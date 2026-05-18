package world.bentobox.bskyblock.paliers;

import java.util.List;

public class PalierConfig {

    private final int id;
    private final long requiredLevel;
    private final String name;
    private final List<String> lore;
    private final int slot;
    private final String icon;
    private final List<ItemCost> cost;
    private final List<String> commands;
    private final int generatorLevel;

    public PalierConfig(int id, long requiredLevel, String name, List<String> lore,
            int slot, String icon, List<ItemCost> cost, List<String> commands, int generatorLevel) {
        this.id = id;
        this.requiredLevel = requiredLevel;
        this.name = name;
        this.lore = lore;
        this.slot = slot;
        this.icon = icon;
        this.cost = cost;
        this.commands = commands;
        this.generatorLevel = generatorLevel;
    }

    public int getId() { return id; }
    public long getRequiredLevel() { return requiredLevel; }
    public String getName() { return name; }
    public List<String> getLore() { return lore; }
    public int getSlot() { return slot; }
    public String getIcon() { return icon; }
    public List<ItemCost> getCost() { return cost; }
    public List<String> getCommands() { return commands; }
    public int getGeneratorLevel() { return generatorLevel; }

    public record ItemCost(String type, int amount) {}
}
