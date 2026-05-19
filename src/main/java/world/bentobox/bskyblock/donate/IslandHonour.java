package world.bentobox.bskyblock.donate;

import world.bentobox.bentobox.database.objects.DataObject;
import world.bentobox.bentobox.database.objects.Table;

@Table(name = "IslandHonour")
public class IslandHonour implements DataObject {

    private String uniqueId = "";
    private long honour = 0L;

    public IslandHonour() {}

    public IslandHonour(String islandId) {
        this.uniqueId = islandId;
    }

    @Override
    public String getUniqueId() { return uniqueId; }

    @Override
    public void setUniqueId(String uniqueId) { this.uniqueId = uniqueId; }

    public long getHonour() { return honour; }

    public void setHonour(long honour) { this.honour = honour; }

    public void addHonour(long amount) { this.honour += amount; }
}
