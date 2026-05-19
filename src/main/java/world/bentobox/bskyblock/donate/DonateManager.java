package world.bentobox.bskyblock.donate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;

public class DonateManager {

    private final BSkyBlock addon;
    private final HonourDataManager honourData;
    private final DonateSettings settings;
    private final boolean itemsAdderPresent;

    public DonateManager(BSkyBlock addon, HonourDataManager honourData, DonateSettings settings) {
        this.addon = addon;
        this.honourData = honourData;
        this.settings = settings;
        this.itemsAdderPresent = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;
    }

    public long getHonour(Island island) {
        return honourData.getHonour(island.getUniqueId());
    }

    /**
     * Attempts a donation. Returns true on success, false on failure (message sent to player).
     */
    public boolean donate(Island island, DonateItemConfig item, Player player) {
        if (!hasItems(player, item)) {
            String msg = settings.getMsgNotEnoughItems()
                    .replace("%amount%", String.valueOf(item.getAmount()))
                    .replace("%item%", item.getDisplayName());
            player.sendMessage(msg);
            return false;
        }

        removeItems(player, item);
        long newTotal = honourData.getHonour(island.getUniqueId()) + item.getHonour();
        honourData.addHonour(island.getUniqueId(), item.getHonour());

        String msg = settings.getMsgDonated()
                .replace("%amount%", String.valueOf(item.getAmount()))
                .replace("%item%", item.getDisplayName())
                .replace("%honour%", String.valueOf(item.getHonour()))
                .replace("%total%", String.valueOf(newTotal));
        player.sendMessage(msg);
        return true;
    }

    public boolean hasItems(Player player, DonateItemConfig item) {
        if (item.isCustomItem()) {
            return countItemsAdder(player, item.getType()) >= item.getAmount();
        }
        Material mat = Material.matchMaterial(item.getType());
        return mat != null && countVanilla(player, mat) >= item.getAmount();
    }

    private void removeItems(Player player, DonateItemConfig item) {
        if (item.isCustomItem()) {
            removeItemsAdder(player, item.getType(), item.getAmount());
        } else {
            Material mat = Material.matchMaterial(item.getType());
            if (mat != null) removeVanilla(player, mat, item.getAmount());
        }
    }

    private int countVanilla(Player player, Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) count += stack.getAmount();
        }
        return count;
    }

    private void removeVanilla(Player player, Material mat, int amount) {
        int toRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            if (stack.getAmount() <= toRemove) {
                toRemove -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - toRemove);
                toRemove = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    private int countItemsAdder(Player player, String namespacedId) {
        if (!itemsAdderPresent) return 0;
        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            int count = 0;
            for (ItemStack stack : player.getInventory().getContents()) {
                if (stack == null) continue;
                Object cs = csClass.getMethod("byItemStack", ItemStack.class).invoke(null, stack);
                if (cs == null) continue;
                String id = (String) csClass.getMethod("getNamespacedID").invoke(cs);
                if (namespacedId.equals(id)) count += stack.getAmount();
            }
            return count;
        } catch (Exception e) {
            return 0;
        }
    }

    private void removeItemsAdder(Player player, String namespacedId, int amount) {
        if (!itemsAdderPresent) return;
        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            int toRemove = amount;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && toRemove > 0; i++) {
                ItemStack stack = contents[i];
                if (stack == null) continue;
                Object cs = csClass.getMethod("byItemStack", ItemStack.class).invoke(null, stack);
                if (cs == null) continue;
                String id = (String) csClass.getMethod("getNamespacedID").invoke(cs);
                if (!namespacedId.equals(id)) continue;
                if (stack.getAmount() <= toRemove) {
                    toRemove -= stack.getAmount();
                    contents[i] = null;
                } else {
                    stack.setAmount(stack.getAmount() - toRemove);
                    toRemove = 0;
                }
            }
            player.getInventory().setContents(contents);
        } catch (Exception ignored) {}
    }

    /**
     * Attempts to resolve an ItemsAdder item stack for GUI display.
     * Returns null if ItemsAdder is absent or the item doesn't exist.
     */
    public ItemStack getItemsAdderStack(String namespacedId) {
        if (!itemsAdderPresent) return null;
        try {
            Class<?> csClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object cs = csClass.getMethod("getInstance", String.class).invoke(null, namespacedId);
            if (cs == null) return null;
            return (ItemStack) csClass.getMethod("getItemStack").invoke(cs);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isItemsAdderPresent() { return itemsAdderPresent; }
}
