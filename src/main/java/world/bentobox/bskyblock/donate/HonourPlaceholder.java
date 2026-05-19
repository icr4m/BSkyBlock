package world.bentobox.bskyblock.donate;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;

public class HonourPlaceholder extends PlaceholderExpansion {

    private final BSkyBlock addon;

    public HonourPlaceholder(BSkyBlock addon) {
        this.addon = addon;
    }

    @Override
    public String getIdentifier() { return "bskyblock"; }

    @Override
    public String getAuthor() { return "icr4m"; }

    @Override
    public String getVersion() { return addon.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    /**
     * Supported placeholders:
     *   %bskyblock_honour%          — island honour of the player
     *   %bskyblock_honour_formatted% — same, formatted with thousand separators
     */
    @Override
    public String onRequest(OfflinePlayer player, String params) {
        if (player == null) return "";

        if (params.equals("honour") || params.equals("honour_formatted")) {
            Island island = addon.getIslands().getIsland(addon.getOverWorld(), player.getUniqueId());
            if (island == null) return "0";

            long honour = addon.getDonateManager().getHonour(island);
            if (params.equals("honour_formatted")) {
                return String.format("%,d", honour);
            }
            return String.valueOf(honour);
        }

        return null;
    }
}
