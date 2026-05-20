package world.bentobox.bskyblock.generator;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import world.bentobox.bskyblock.BSkyBlock;

import java.io.File;
import java.util.*;

/**
 * Reads inventory-costs and purchase-cost from MCG's generatorTemplate.yml.
 * MCG ignores inventory-costs — we parse it ourselves.
 *
 * Expected format per tier:
 *   requirements:
 *     purchase-cost: 25000
 *     inventory-costs:
 *       - type: "skyblock:jade_mineral"
 *         amount: 1
 */
public class GeneratorCostSettings {

    public record TierCost(List<GeneratorItemCost> items, double vaultCost) {}

    private final BSkyBlock addon;
    // keyed by raw template section name, e.g. "generator_level_1"
    private final Map<String, TierCost> costs = new HashMap<>();

    private String msgMissingItems;
    private String msgCostConsumed;

    public GeneratorCostSettings(BSkyBlock addon) {
        this.addon = addon;
        reload();
    }

    public void reload() {
        costs.clear();

        File template = new File(addon.getPlugin().getDataFolder(),
                "addons/MagicCobblestoneGenerator/generatorTemplate.yml");
        if (!template.exists()) {
            addon.log("GeneratorCostSettings: generatorTemplate.yml not found, item costs disabled.");
            return;
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(template);
        ConfigurationSection tiers = cfg.getConfigurationSection("tiers");
        if (tiers == null) return;

        msgMissingItems = color(cfg.getString("messages.inventory-cost-missing",
                "&cIl vous manque : &e%items%&c pour acheter ce générateur."));
        msgCostConsumed = color(cfg.getString("messages.inventory-cost-consumed",
                "&a✔ Items consommés pour le générateur."));

        for (String key : tiers.getKeys(false)) {
            ConfigurationSection tier = tiers.getConfigurationSection(key);
            if (tier == null) continue;

            ConfigurationSection req = tier.getConfigurationSection("requirements");
            if (req == null) continue;

            double vaultCost = req.getDouble("purchase-cost", 0);
            List<GeneratorItemCost> items = parseItemCosts(req);

            if (!items.isEmpty()) {
                costs.put(key, new TierCost(items, vaultCost));
            }
        }
        addon.log("GeneratorCostSettings: loaded item costs for " + costs.size() + " generator tier(s).");
    }

    private List<GeneratorItemCost> parseItemCosts(ConfigurationSection req) {
        List<GeneratorItemCost> result = new ArrayList<>();
        Object raw = req.get("inventory-costs");
        if (raw == null) return result;

        // List form: [{type: "...", amount: N}, ...]
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map)) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) entry;
                String type = (String) map.getOrDefault("type", "");
                int amount = ((Number) map.getOrDefault("amount", 1)).intValue();
                if (!type.isBlank()) result.add(new GeneratorItemCost(type, amount));
            }
        }
        return result;
    }

    /**
     * Finds the TierCost for a generator ID returned by MCG's event.
     * MCG prefixes template keys with the world name, e.g. "bskyblock_generator_level_1".
     */
    public Optional<TierCost> getCost(String generatorId) {
        // Exact match first
        if (costs.containsKey(generatorId)) return Optional.of(costs.get(generatorId));
        // Suffix match: "bskyblock_generator_level_1" ends with "_generator_level_1"
        return costs.entrySet().stream()
                .filter(e -> generatorId.endsWith("_" + e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    private String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }

    public String getMsgMissingItems() { return msgMissingItems; }
    public String getMsgCostConsumed() { return msgCostConsumed; }
    public boolean hasCosts() { return !costs.isEmpty(); }
}
