package world.bentobox.bskyblock.generators;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
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
    private final Random random = new Random();

    // generatorLevel -> list of (materialKey, weight) pairs
    private final Map<Integer, List<WeightedEntry>> levelWeights = new HashMap<>();
    private boolean enabled = true;

    public CobblestoneGeneratorListener(BSkyBlock addon) {
        this.addon = addon;
        this.itemsAdderEnabled = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
        // Note: Level is a BentoBox addon, checked via AddonsManager, not PluginManager
        loadConfig();
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

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!enabled) return;
        if (!GENERATOR_OUTPUTS.contains(event.getNewState().getType())) return;

        Location loc = event.getNewState().getLocation();
        if (!addon.inWorld(loc)) return;

        Island island = getIslandAt(loc);
        if (island == null) return;

        int generatorLevel = addon.getPalierDataManager().getGeneratorLevel(island.getUniqueId());
        List<WeightedEntry> entries = getEntriesForLevel(generatorLevel);
        if (entries == null || entries.isEmpty()) return;

        String picked = pickWeighted(entries);
        if (picked == null) return;

        if (itemsAdderEnabled && picked.contains(":")) {
            Location finalLoc = event.getNewState().getLocation();
            // Place on next tick so the block position is committed
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
        // Exact match first, then fall back to highest available level below
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
