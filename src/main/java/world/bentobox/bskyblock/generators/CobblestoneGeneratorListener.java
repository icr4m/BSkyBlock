package world.bentobox.bskyblock.generators;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFormEvent;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class CobblestoneGeneratorListener implements Listener {

    private static final List<Material> GENERATOR_OUTPUTS = List.of(
            Material.COBBLESTONE, Material.STONE, Material.BASALT
    );

    private final BSkyBlock addon;
    private final boolean itemsAdderEnabled;
    private final boolean mcgPresent;
    private final Random random = new Random();

    // Standalone mode (no MCG): palier generatorLevel -> weighted entries
    private final Map<Integer, List<WeightedEntry>> levelWeights = new HashMap<>();

    // MCG bridge mode: MCG tier uniqueId -> ItemsAdder weighted entries
    private final Map<String, List<WeightedEntry>> mcgTierIABlocks = new HashMap<>();
    // MCG bridge mode: MCG tier uniqueId -> total weight (including IA blocks, from template)
    private final Map<String, Integer> mcgTierTotalWeights = new HashMap<>();

    private boolean enabled = true;

    public CobblestoneGeneratorListener(BSkyBlock addon) {
        this.addon = addon;
        this.itemsAdderEnabled = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        this.mcgPresent = isMCGPresent();
        loadConfig();
        if (mcgPresent) {
            loadMCGTemplate();
        }
    }

    private static boolean isMCGPresent() {
        try {
            Class.forName("world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public void loadConfig() {
        levelWeights.clear();
        File file = new File(addon.getDataFolder(), "generator.yml");
        if (!file.exists()) {
            addon.saveResource("generator.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        try (InputStream is = addon.getResource("generator.yml")) {
            if (is != null) {
                String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.loadFromString(yaml);
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {}

        enabled = config.getBoolean("generator.enabled", true);

        ConfigurationSection levelsSection = config.getConfigurationSection("generator.levels");
        if (levelsSection == null) return;

        for (String levelKey : levelsSection.getKeys(false)) {
            int level;
            try { level = Integer.parseInt(levelKey); } catch (NumberFormatException e) { continue; }
            ConfigurationSection levelSection = levelsSection.getConfigurationSection(levelKey);
            if (levelSection == null) continue;

            List<WeightedEntry> entries = new ArrayList<>();
            for (String key : levelSection.getKeys(false)) {
                int weight = levelSection.getInt(key, 0);
                if (weight > 0) entries.add(new WeightedEntry(key, weight));
            }
            levelWeights.put(level, entries);
        }
    }

    private void loadMCGTemplate() {
        mcgTierIABlocks.clear();
        mcgTierTotalWeights.clear();

        File templateFile = new File(addon.getPlugin().getDataFolder(),
                "addons/MagicCobblestoneGenerator/generatorTemplate.yml");
        if (!templateFile.exists()) return;

        YamlConfiguration template = YamlConfiguration.loadConfiguration(templateFile);
        ConfigurationSection tiers = template.getConfigurationSection("tiers");
        if (tiers == null) return;

        String gamemodePrefx = addon.getDescription().getName().toLowerCase() + "_";

        for (String tierKey : tiers.getKeys(false)) {
            ConfigurationSection blocks = tiers.getConfigurationSection(tierKey + ".blocks");
            if (blocks == null) continue;

            List<WeightedEntry> iaEntries = new ArrayList<>();
            int totalWeight = 0;

            for (String blockKey : blocks.getKeys(false)) {
                int weight = blocks.getInt(blockKey, 0);
                if (weight <= 0) continue;
                totalWeight += weight;
                // ItemsAdder custom blocks use namespace:id format
                if (blockKey.contains(":")) {
                    iaEntries.add(new WeightedEntry(blockKey, weight));
                }
            }

            if (!iaEntries.isEmpty() && totalWeight > 0) {
                String uniqueId = gamemodePrefx + tierKey;
                mcgTierIABlocks.put(uniqueId, iaEntries);
                mcgTierTotalWeights.put(uniqueId, totalWeight);
            }
        }
    }

    // Run at LOWEST so we see the original block type before MCG (NORMAL) modifies it.
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockForm(BlockFormEvent event) {
        if (!enabled) return;
        if (!GENERATOR_OUTPUTS.contains(event.getNewState().getType())) return;

        Location loc = event.getNewState().getLocation();
        if (!addon.inWorld(loc)) return;

        Island island = getIslandAt(loc);
        if (island == null) return;

        if (mcgPresent && itemsAdderEnabled && !mcgTierIABlocks.isEmpty()) {
            handleMCGBridge(island, loc);
        } else if (!mcgPresent) {
            handleStandalone(event, island);
        }
        // MCG present but no IA blocks configured → let MCG handle everything unmodified.
    }

    /**
     * When MCG is installed: intercept at LOWEST priority to place ItemsAdder blocks
     * that MCG cannot handle natively. MCG (NORMAL priority) then handles the
     * remaining vanilla block probability unmodified.
     */
    private void handleMCGBridge(Island island, Location loc) {
        String tierUniqueId = getActiveMCGTier(island, loc);
        if (tierUniqueId == null) return;

        List<WeightedEntry> iaEntries = mcgTierIABlocks.get(tierUniqueId);
        if (iaEntries == null || iaEntries.isEmpty()) return;

        Integer totalWeight = mcgTierTotalWeights.get(tierUniqueId);
        if (totalWeight == null || totalWeight <= 0) return;

        int iaWeight = iaEntries.stream().mapToInt(WeightedEntry::weight).sum();

        // Roll against the FULL template weight (including IA blocks) so the
        // final probability distribution matches the template exactly.
        if (random.nextInt(totalWeight) >= iaWeight) return;

        String picked = pickWeighted(iaEntries);
        if (picked == null) return;

        // Let the event continue so a vanilla block forms as a placeholder
        // (prevents a 1-tick gap where lava could flow in), then replace it.
        Location finalLoc = loc.clone();
        Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
            try {
                Class.forName("dev.lone.itemsadder.api.CustomBlock")
                        .getMethod("place", String.class, Location.class)
                        .invoke(null, picked, finalLoc);
            } catch (Exception ignored) {}
        });
    }

    private String getActiveMCGTier(Island island, Location loc) {
        try {
            Object mcgAddon = addon.getPlugin().getAddonsManager()
                    .getAddonByName("MagicCobblestoneGenerator").orElse(null);
            if (mcgAddon == null) return null;

            Object manager = mcgAddon.getClass().getMethod("getAddonManager").invoke(mcgAddon);

            Class<?> genTypeClass = Class.forName(
                    "world.bentobox.magiccobblestonegenerator.database.objects.GeneratorTierObject$GeneratorType");
            @SuppressWarnings("unchecked")
            Object genType = Enum.valueOf((Class<Enum>) genTypeClass, "COBBLESTONE_OR_STONE");

            Object tier = manager.getClass()
                    .getMethod("getGeneratorTier", Island.class, Location.class, genTypeClass)
                    .invoke(manager, island, loc, genType);
            if (tier == null) return null;

            return (String) tier.getClass().getMethod("getUniqueId").invoke(tier);
        } catch (Exception e) {
            return null;
        }
    }

    private void handleStandalone(BlockFormEvent event, Island island) {
        int generatorLevel = addon.getPalierDataManager().getGeneratorLevel(island.getUniqueId());
        List<WeightedEntry> entries = getEntriesForLevel(generatorLevel);
        if (entries == null || entries.isEmpty()) return;

        String picked = pickWeighted(entries);
        if (picked == null) return;

        if (itemsAdderEnabled && picked.contains(":")) {
            Location finalLoc = event.getNewState().getLocation();
            Bukkit.getScheduler().runTask(addon.getPlugin(), () -> {
                try {
                    Class.forName("dev.lone.itemsadder.api.CustomBlock")
                            .getMethod("place", String.class, Location.class)
                            .invoke(null, picked, finalLoc);
                } catch (Exception ignored) {}
            });
        } else {
            Material mat = Material.matchMaterial(picked);
            if (mat != null) event.getNewState().setType(mat);
        }
    }

    private Island getIslandAt(Location loc) {
        try {
            return addon.getPlugin().getIslands().getIslandAt(loc).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<WeightedEntry> getEntriesForLevel(int level) {
        if (levelWeights.containsKey(level)) return levelWeights.get(level);
        int best = -1;
        for (int l : levelWeights.keySet()) {
            if (l <= level && l > best) best = l;
        }
        return best >= 0 ? levelWeights.get(best) : null;
    }

    private String pickWeighted(List<WeightedEntry> entries) {
        int total = entries.stream().mapToInt(e -> e.weight).sum();
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        int cumulative = 0;
        for (WeightedEntry entry : entries) {
            cumulative += entry.weight;
            if (roll < cumulative) return entry.key;
        }
        return entries.get(entries.size() - 1).key;
    }

    private record WeightedEntry(String key, int weight) {}
}
