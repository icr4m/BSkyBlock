package world.bentobox.bskyblock.generator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import world.bentobox.bentobox.api.addons.Addon;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bentobox.hooks.VaultHook;
import world.bentobox.bskyblock.BSkyBlock;
import world.bentobox.bskyblock.generator.GeneratorCostSettings.TierCost;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Intercepts MCG's GeneratorBuyEvent (not cancellable) to enforce inventory-costs.
 * If items are missing: refunds Vault cost and removes the tier from purchased/unlocked sets.
 * If items are present: consumes them.
 */
public class GeneratorCostListener {

    private GeneratorCostListener() {}

    @SuppressWarnings("unchecked")
    public static void register(BSkyBlock addon, GeneratorCostSettings settings) {
        if (!settings.hasCosts()) {
            addon.log("GeneratorCostListener: no inventory costs configured, listener skipped.");
            return;
        }
        boolean itemsAdderPresent = Bukkit.getPluginManager().getPlugin("ItemsAdder") != null;

        try {
            Class<? extends Event> eventClass = (Class<? extends Event>)
                    Class.forName("world.bentobox.magiccobblestonegenerator.events.GeneratorBuyEvent");

            Listener dummy = new Listener() {};

            Bukkit.getPluginManager().registerEvent(
                    eventClass, dummy, EventPriority.MONITOR,
                    (l, event) -> {
                        if (!eventClass.isInstance(event)) return;
                        try {
                            UUID playerUUID = (UUID) event.getClass().getMethod("getTargetPlayer").invoke(event);
                            String generatorId = (String) event.getClass().getMethod("getGeneratorID").invoke(event);
                            String islandUUID = (String) event.getClass().getMethod("getIslandUUID").invoke(event);

                            if (playerUUID == null || islandUUID == null) return;

                            Optional<TierCost> costOpt = settings.getCost(generatorId);
                            if (costOpt.isEmpty()) return;

                            TierCost tierCost = costOpt.get();
                            Player player = Bukkit.getPlayer(playerUUID);
                            if (player == null) return;

                            List<String> missing = checkItems(player, tierCost.items(), itemsAdderPresent);

                            if (!missing.isEmpty()) {
                                undoPurchase(addon, player, islandUUID, generatorId, tierCost.vaultCost());
                                String msg = settings.getMsgMissingItems()
                                        .replace("%items%", String.join(", ", missing));
                                player.sendMessage(msg);
                            } else {
                                consumeItems(player, tierCost.items(), itemsAdderPresent);
                                if (!settings.getMsgCostConsumed().isBlank()) {
                                    player.sendMessage(settings.getMsgCostConsumed());
                                }
                            }
                        } catch (Exception e) {
                            addon.logError("GeneratorCostListener exception: "
                                    + e.getClass().getSimpleName() + ": " + e.getMessage());
                        }
                    },
                    addon.getPlugin(),
                    false
            );
            addon.log("MagicCobblestoneGenerator detected - generator inventory costs enabled.");
        } catch (ClassNotFoundException e) {
            addon.log("MagicCobblestoneGenerator not found - generator inventory costs disabled.");
        }
    }

    @SuppressWarnings("unchecked")
    private static void undoPurchase(BSkyBlock addon, Player player, String islandUUID,
                                     String generatorId, double vaultCost) {
        try {
            Class<?> addonClass = Class.forName("world.bentobox.magiccobblestonegenerator.StoneGeneratorAddon");
            Addon mcgAddon = (Addon) addonClass.getMethod("getInstance").invoke(null);
            Object manager = addonClass.getMethod("getAddonManager").invoke(mcgAddon);

            Optional<Island> islandOpt = addon.getIslands().getIslandById(islandUUID);
            if (islandOpt.isEmpty()) return;
            Island island = islandOpt.get();

            Object dataObj = manager.getClass()
                    .getMethod("getGeneratorData", Island.class)
                    .invoke(manager, island);
            if (dataObj == null) return;

            Set<String> purchased = (Set<String>) dataObj.getClass()
                    .getMethod("getPurchasedTiers").invoke(dataObj);
            Set<String> unlocked = (Set<String>) dataObj.getClass()
                    .getMethod("getUnlockedTiers").invoke(dataObj);
            purchased.remove(generatorId);
            unlocked.remove(generatorId);

            manager.getClass()
                    .getMethod("saveGeneratorData", dataObj.getClass())
                    .invoke(manager, dataObj);

            refundVault(addon, player, vaultCost);

        } catch (Exception e) {
            addon.logError("GeneratorCostListener.undoPurchase failed: "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // --- item helpers ---

    private static List<String> checkItems(Player player, List<GeneratorItemCost> costs, boolean iaPresent) {
        List<String> missing = new ArrayList<>();
        for (GeneratorItemCost cost : costs) {
            if (cost.isCustomItem()) {
                if (countItemsAdder(player, cost.type(), iaPresent) < cost.amount()) {
                    missing.add(cost.amount() + "x " + cost.type());
                }
            } else {
                Material mat = Material.matchMaterial(cost.type());
                if (mat == null || countVanilla(player, mat) < cost.amount()) {
                    missing.add(cost.amount() + "x " + cost.type());
                }
            }
        }
        return missing;
    }

    private static void consumeItems(Player player, List<GeneratorItemCost> costs, boolean iaPresent) {
        for (GeneratorItemCost cost : costs) {
            if (cost.isCustomItem()) {
                removeItemsAdder(player, cost.type(), cost.amount(), iaPresent);
            } else {
                Material mat = Material.matchMaterial(cost.type());
                if (mat != null) removeVanilla(player, mat, cost.amount());
            }
        }
    }

    private static int countVanilla(Player player, Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) count += stack.getAmount();
        }
        return count;
    }

    private static void removeVanilla(Player player, Material mat, int amount) {
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

    private static int countItemsAdder(Player player, String namespacedId, boolean iaPresent) {
        if (!iaPresent) return 0;
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

    private static void removeItemsAdder(Player player, String namespacedId, int amount, boolean iaPresent) {
        if (!iaPresent) return;
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

    // --- Vault refund ---

    private static void refundVault(BSkyBlock addon, Player player, double amount) {
        if (amount <= 0) return;
        Optional<VaultHook> vaultOpt = addon.getPlugin().getVault();
        if (vaultOpt.isEmpty()) return;
        vaultOpt.get().getEconomy().depositPlayer(player, amount);
    }
}
