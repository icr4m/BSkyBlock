package world.bentobox.bskyblock.donate;

import java.util.List;

public class DonateItemConfig {

    private final String type;
    private final String displayName;
    private final String icon;
    private final List<String> lore;
    private final long honour;
    private final int amount;
    private final int slot;

    public DonateItemConfig(String type, String displayName, String icon,
            List<String> lore, long honour, int amount, int slot) {
        this.type = type;
        this.displayName = displayName;
        this.icon = icon;
        this.lore = lore;
        this.honour = honour;
        this.amount = amount;
        this.slot = slot;
    }

    public String getType()        { return type; }
    public String getDisplayName() { return displayName; }
    public String getIcon()        { return icon; }
    public List<String> getLore()  { return lore; }
    public long getHonour()        { return honour; }
    public int getAmount()         { return amount; }
    public int getSlot()           { return slot; }

    public boolean isCustomItem()  { return type.contains(":"); }
}
