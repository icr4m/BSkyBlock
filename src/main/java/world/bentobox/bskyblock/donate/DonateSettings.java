package world.bentobox.bskyblock.donate;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import world.bentobox.bskyblock.BSkyBlock;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DonateSettings {

    private final BSkyBlock addon;
    private FileConfiguration config;

    private String guiTitle;
    private int guiSize;
    private String msgDonated;
    private String msgNotEnoughItems;
    private String msgHonourDisplay;
    private String msgNoIsland;

    private List<DonateItemConfig> items;

    public DonateSettings(BSkyBlock addon) {
        this.addon = addon;
        reload();
    }

    public void reload() {
        File file = new File(addon.getDataFolder(), "donate.yml");
        if (!file.exists()) {
            addon.saveResource("donate.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);
        try (InputStream is = addon.getResource("donate.yml")) {
            if (is != null) {
                String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                YamlConfiguration defaults = new YamlConfiguration();
                defaults.loadFromString(yaml);
                config.setDefaults(defaults);
            }
        } catch (Exception ignored) {}

        guiTitle          = color(config.getString("gui.title", "&6&lDonation"));
        guiSize           = config.getInt("gui.size", 54);
        msgDonated        = config.getString("messages.donated", "");
        msgNotEnoughItems = config.getString("messages.not-enough-items", "");
        msgHonourDisplay  = config.getString("messages.honour-display", "");
        msgNoIsland       = config.getString("messages.no-island", "");

        items = new ArrayList<>();
        List<?> rawList = config.getList("items", Collections.emptyList());
        for (Object obj : rawList) {
            if (!(obj instanceof java.util.Map)) continue;
            @SuppressWarnings("unchecked")
            var map = (java.util.Map<String, Object>) obj;

            String type        = (String) map.getOrDefault("type", "COBBLESTONE");
            String displayName = color((String) map.getOrDefault("display-name", type));
            String icon        = (String) map.getOrDefault("icon", type);
            List<String> lore  = colorList(castStringList(map.get("lore")));
            long honour        = ((Number) map.getOrDefault("honour", 1)).longValue();
            int amount         = ((Number) map.getOrDefault("amount", 1)).intValue();
            int slot           = ((Number) map.getOrDefault("slot", 0)).intValue();

            items.add(new DonateItemConfig(type, displayName, icon, lore, honour, amount, slot));
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> castStringList(Object obj) {
        if (obj instanceof List<?> list) return (List<String>) list;
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

    public String getGuiTitle()          { return guiTitle; }
    public int getGuiSize()              { return guiSize; }
    public String getMsgDonated()        { return msgDonated.replace("&", "§"); }
    public String getMsgNotEnoughItems() { return msgNotEnoughItems.replace("&", "§"); }
    public String getMsgHonourDisplay()  { return msgHonourDisplay.replace("&", "§"); }
    public String getMsgNoIsland()       { return msgNoIsland.replace("&", "§"); }
    public List<DonateItemConfig> getItems() { return items; }
}
