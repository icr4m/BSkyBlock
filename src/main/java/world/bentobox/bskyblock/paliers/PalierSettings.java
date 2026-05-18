package world.bentobox.bskyblock.paliers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import world.bentobox.bskyblock.BSkyBlock;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PalierSettings {

    private final BSkyBlock addon;
    private FileConfiguration config;

    private String guiTitle;
    private int guiSize;
    private String msgNotifyClaimable;
    private String msgNotEnoughLevel;
    private String msgCostMissing;
    private String msgClaimedSuccess;
    private List<PalierConfig> paliers;

    public PalierSettings(BSkyBlock addon) {
        this.addon = addon;
        reload();
    }

    public void reload() {
        File file = new File(addon.getDataFolder(), "paliers.yml");
        if (!file.exists()) {
            addon.saveResource("paliers.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        // Merge defaults from jar — read bytes eagerly to avoid stream closed by addon loader
        try (InputStream is = addon.getResource("paliers.yml")) {
            if (is != null) {
                String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.loadFromString(yaml);
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {}

        guiTitle = color(config.getString("gui.title", "&9&lPaliers"));
        guiSize = config.getInt("gui.size", 27);
        msgNotifyClaimable = config.getString("messages.notify-claimable", "");
        msgNotEnoughLevel = config.getString("messages.not-enough-level", "");
        msgCostMissing = config.getString("messages.cost-missing", "");
        msgClaimedSuccess = config.getString("messages.claimed-success", "");

        paliers = new ArrayList<>();
        List<?> rawList = config.getList("paliers", Collections.emptyList());
        for (Object obj : rawList) {
            if (!(obj instanceof java.util.Map)) continue;
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<String, Object>) obj;

            int id = ((Number) map.getOrDefault("id", 0)).intValue();
            long requiredLevel = ((Number) map.getOrDefault("required-level", 0)).longValue();
            String name = color((String) map.getOrDefault("name", "Palier " + id));
            List<String> lore = colorList(castStringList(map.get("lore")));
            int slot = ((Number) map.getOrDefault("slot", 0)).intValue();
            String icon = (String) map.getOrDefault("icon", "STONE");
            int generatorLevel = ((Number) map.getOrDefault("generator-level", 0)).intValue();
            List<String> commands = castStringList(map.get("commands"));

            List<PalierConfig.ItemCost> cost = new ArrayList<>();
            Object rawCost = map.get("cost");
            if (rawCost instanceof List<?> costList) {
                for (Object costEntry : costList) {
                    if (!(costEntry instanceof java.util.Map)) continue;
                    @SuppressWarnings("unchecked")
                    var costMap = (java.util.Map<String, Object>) costEntry;
                    String type = (String) costMap.getOrDefault("type", "COBBLESTONE");
                    int amount = ((Number) costMap.getOrDefault("amount", 1)).intValue();
                    cost.add(new PalierConfig.ItemCost(type, amount));
                }
            }

            paliers.add(new PalierConfig(id, requiredLevel, name, lore, slot, icon, cost, commands, generatorLevel));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object obj) {
        if (obj instanceof List<?> list) {
            return (List<String>) list;
        }
        return Collections.emptyList();
    }

    private List<String> colorList(List<String> list) {
        List<String> result = new ArrayList<>();
        for (String s : list) result.add(color(s));
        return result;
    }

    private String color(String s) {
        if (s == null) return "";
        return s.replace("&", "§");
    }

    public String getGuiTitle() { return guiTitle; }
    public int getGuiSize() { return guiSize; }
    public String getMsgNotifyClaimable() { return msgNotifyClaimable.replace("&", "§"); }
    public String getMsgNotEnoughLevel() { return msgNotEnoughLevel.replace("&", "§"); }
    public String getMsgCostMissing() { return msgCostMissing.replace("&", "§"); }
    public String getMsgClaimedSuccess() { return msgClaimedSuccess.replace("&", "§"); }
    public List<PalierConfig> getPaliers() { return paliers; }
}
