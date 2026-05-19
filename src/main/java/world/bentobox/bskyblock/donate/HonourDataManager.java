package world.bentobox.bskyblock.donate;

import world.bentobox.bentobox.database.Database;
import world.bentobox.bskyblock.BSkyBlock;

import java.util.HashMap;
import java.util.Map;

public class HonourDataManager {

    private final Database<IslandHonour> database;
    private final Map<String, IslandHonour> cache = new HashMap<>();

    public HonourDataManager(BSkyBlock addon) {
        this.database = new Database<>(addon, IslandHonour.class);
        load();
    }

    private void load() {
        database.loadObjects().forEach(obj -> cache.put(obj.getUniqueId(), obj));
    }

    public long getHonour(String islandId) {
        return cache.computeIfAbsent(islandId, IslandHonour::new).getHonour();
    }

    public void addHonour(String islandId, long amount) {
        IslandHonour entry = cache.computeIfAbsent(islandId, IslandHonour::new);
        entry.addHonour(amount);
        database.saveObjectAsync(entry);
    }
}
