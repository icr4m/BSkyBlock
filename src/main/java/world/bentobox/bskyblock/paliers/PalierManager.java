package world.bentobox.bskyblock.paliers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PalierManager {

    public enum PalierStatus { LOCKED, CLAIMABLE, CLAIMED }

    private final BSkyBlock addon;
    private final PalierDataManager data;
    private final PalierSettings settings;
    // When Level addon is absent, island levels default to 0 (all paliers locked).
    private final boolean levelAddonPresent;
    private final boolean itemsAdderPresent;

    public PalierManager(BSkyBlock addon, PalierDataManager data, PalierSettings settings) {
        this.addon = addon;
        this.data = data;
        this.settings = settings;
        this.levelAddonPresent = addon.getPlugin().getAddonsManager().getAddonByName("Level").isPresent();
        this.itemsAdderPresent = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public PalierStatus getStatus(Island island, PalierConfig palier) {
        String islandId = island.getUniqueId();
        if (data.isClaimed(islandId, palier.getId())) return PalierStatus.CLAIMED;
        long level = getIslandLevel(island);
        if (level >= palier.getRequiredLevel()) return PalierStatus.CLAIMABLE;
        return PalierStatus.LOCKED;
    }

    /**
     * Attempts to claim a palier. Returns true on success, false with a message to the player on failure.
     */
    public boolean claim(Island island, PalierConfig palier, Player player) {
        String islandId = island.getUniqueId();

        if (data.isClaimed(islandId, palier.getId())) return false;

        long level = getIslandLevel(island);
        if (level < palier.getRequiredLevel()) {
            String msg = settings.getMsgNotEnoughLevel()
                    .replace("%required%", String.valueOf(palier.getRequiredLevel()));
            player.sendMessage(msg);
            return false;
        }

        // Check and consume cost
        List<String> missing = checkCost(palier, player);
        if (!missing.isEmpty()) {
            String msg = settings.getMsgCostMissing()
                    .replace("%items%", String.join(", ", missing));
            player.sendMessage(msg);
            return false;
        }
        consumeCost(palier, player);

        // Mark claimed
        data.claimPalier(islandId, palier.getId());

        // Apply generator level upgrade (never downgrade)
        if (palier.getGeneratorLevel() > 0 && palier.getGeneratorLevel() > data.getGeneratorLevel(islandId)) {
            data.setGeneratorLevel(islandId, palier.getGeneratorLevel());
        }

        // Run reward commands
        UUID ownerUUID = island.getOwner();
        Player owner = ownerUUID != null ? Bukkit.getPlayer(ownerUUID) : null;
        String ownerName = owner != null ? owner.getName() : (ownerUUID != null ? ownerUUID.toString() : player.getName());
        for (String cmd : palier.getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", ownerName));
        }

        player.sendMessage(settings.getMsgClaimedSuccess().replace("%name%", palier.getName()));
        return true;
    }

    public void notifyClaimable(Island island) {
        String msg = settings.getMsgNotifyClaimable();
        if (msg.isBlank()) return;
        island.getMemberSet().stream()
                .map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline())
                .forEach(p -> p.sendMessage(msg));
    }

    // --- cost helpers ---

    private List<String> checkCost(PalierConfig palier, Player player) {
        List<String> missing = new ArrayList<>();
        for (PalierConfig.ItemCost cost : palier.getCost()) {
            if (cost.type().contains(":")) {
                // ItemsAdder custom item
                if (!hasItemsAdderItem(player, cost.type(), cost.amount())) {
                    missing.add(cost.amount() + "x " + cost.type());
                }
            } else {
                Material mat = Material.matchMaterial(cost.type());
                if (mat == null || !hasVanillaItem(player, mat, cost.amount())) {
                    missing.add(cost.amount() + "x " + cost.type());
                }
            }
        }
        return missing;
    }

    private void consumeCost(PalierConfig palier, Player player) {
        for (PalierConfig.ItemCost cost : palier.getCost()) {
            if (cost.type().contains(":")) {
                removeItemsAdderItem(player, cost.type(), cost.amount());
            } else {
                Material mat = Material.matchMaterial(cost.type());
                if (mat != null) removeVanillaItem(player, mat, cost.amount());
            }
        }
    }

    private boolean hasVanillaItem(Player player, Material mat, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == mat) count += item.getAmount();
        }
        return count >= amount;
    }

    private void removeVanillaItem(Player player, Material mat, int amount) {
        int toRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() != mat) continue;
            if (item.getAmount() <= toRemove) {
                toRemove -= item.getAmount();
                contents[i] = null;
            } else {
                item.setAmount(item.getAmount() - toRemove);
                toRemove = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    private boolean hasItemsAdderItem(Player player, String namespacedId, int amount) {
        if (!itemsAdderPresent) return false;
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            int count = 0;
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null) continue;
                Object cs = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
                if (cs == null) continue;
                String id = (String) customStackClass.getMethod("getNamespacedID").invoke(cs);
                if (namespacedId.equals(id)) count += item.getAmount();
            }
            return count >= amount;
        } catch (Exception e) {
            return false;
        }
    }

    private void removeItemsAdderItem(Player player, String namespacedId, int amount) {
        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            int toRemove = amount;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && toRemove > 0; i++) {
                ItemStack item = contents[i];
                if (item == null) continue;
                Object cs = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
                if (cs == null) continue;
                String id = (String) customStackClass.getMethod("getNamespacedID").invoke(cs);
                if (!namespacedId.equals(id)) continue;
                if (item.getAmount() <= toRemove) {
                    toRemove -= item.getAmount();
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - toRemove);
                    toRemove = 0;
                }
            }
            player.getInventory().setContents(contents);
        } catch (Exception ignored) {}
    }

    // --- Level addon integration ---

    public long getIslandLevel(Island island) {
        if (!levelAddonPresent) return 0;
        UUID owner = island.getOwner();
        if (owner == null) return 0;
        try {
            var levelOpt = addon.getPlugin().getAddonsManager().getAddonByName("Level");
            if (levelOpt.isEmpty()) return 0;
            Object levelAddon = levelOpt.get();
            return (long) levelAddon.getClass()
                    .getMethod("getIslandLevel", org.bukkit.World.class, UUID.class)
                    .invoke(levelAddon, addon.getOverWorld(), owner);
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean isClaimed(Island island, int palierId) {
        return data.isClaimed(island.getUniqueId(), palierId);
    }

    public boolean isLevelAddonPresent() { return levelAddonPresent; }
}
