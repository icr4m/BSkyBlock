package world.bentobox.bskyblock.paliers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import world.bentobox.bskyblock.BSkyBlock;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class PalierDataManager {

    private final BSkyBlock addon;
    private final File dataFile;
    private FileConfiguration data;

    public PalierDataManager(BSkyBlock addon) {
        this.addon = addon;
        this.dataFile = new File(addon.getDataFolder(), "palier-data.yml");
        load();
    }

    public void load() {
        data = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void save() {
        try {
            data.save(dataFile);
        } catch (IOException e) {
            addon.getLogger().log(Level.SEVERE, "Could not save palier-data.yml", e);
        }
    }

    private String islandPath(String islandId) {
        return "islands." + islandId;
    }

    public Set<Integer> getClaimedPaliers(String islandId) {
        var list = data.getIntegerList(islandPath(islandId) + ".claimed-paliers");
        return new HashSet<>(list);
    }

    public boolean isClaimed(String islandId, int palierId) {
        return getClaimedPaliers(islandId).contains(palierId);
    }

    public void claimPalier(String islandId, int palierId) {
        Set<Integer> claimed = getClaimedPaliers(islandId);
        claimed.add(palierId);
        data.set(islandPath(islandId) + ".claimed-paliers", claimed.stream().toList());
        save();
    }

    public int getGeneratorLevel(String islandId) {
        return data.getInt(islandPath(islandId) + ".generator-level", 0);
    }

    public void setGeneratorLevel(String islandId, int level) {
        data.set(islandPath(islandId) + ".generator-level", level);
        save();
    }
}
