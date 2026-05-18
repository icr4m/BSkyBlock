package world.bentobox.bskyblock.paliers.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import world.bentobox.bentobox.database.objects.Island;
import world.bentobox.bskyblock.BSkyBlock;
import world.bentobox.bskyblock.paliers.PalierConfig;
import world.bentobox.bskyblock.paliers.PalierManager;
import world.bentobox.bskyblock.paliers.PalierSettings;

import java.util.ArrayList;
import java.util.List;

public class PaliersGUI implements Listener {

    private final BSkyBlock addon;
    private final Island island;
    private final Player player;
    private final PalierManager palierManager;
    private final PalierSettings settings;
    private Inventory inventory;

    public PaliersGUI(BSkyBlock addon, Island island, Player player) {
        this.addon = addon;
        this.island = island;
        this.player = player;
        this.palierManager = addon.getPalierManager();
        this.settings = addon.getPalierSettings();
    }

    public void open() {
        inventory = Bukkit.createInventory(null, settings.getGuiSize(), settings.getGuiTitle());
        populate();
        player.openInventory(inventory);
        addon.getPlugin().getServer().getPluginManager().registerEvents(this, addon.getPlugin());
    }

    private void populate() {
        inventory.clear();
        for (PalierConfig palier : settings.getPaliers()) {
            PalierManager.PalierStatus status = palierManager.getStatus(island, palier);
            ItemStack item = buildItem(palier, status);
            inventory.setItem(palier.getSlot(), item);
        }
    }

    private ItemStack buildItem(PalierConfig palier, PalierManager.PalierStatus status) {
        Material glass = switch (status) {
            case CLAIMED -> Material.GREEN_STAINED_GLASS_PANE;
            case CLAIMABLE -> Material.PURPLE_STAINED_GLASS_PANE;
            case LOCKED -> Material.RED_STAINED_GLASS_PANE;
        };

        ItemStack item = new ItemStack(glass);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(palier.getName());

        List<String> lore = new ArrayList<>(palier.getLore());
        lore.add("");
        lore.add(buildStatusLore(palier, status));

        if (!palier.getCost().isEmpty()) {
            lore.add("§7Coût :");
            for (PalierConfig.ItemCost cost : palier.getCost()) {
                lore.add("  §e" + cost.amount() + "x §f" + cost.type());
            }
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private String buildStatusLore(PalierConfig palier, PalierManager.PalierStatus status) {
        return switch (status) {
            case CLAIMED -> "§2✔ Déjà réclamé";
            case CLAIMABLE -> "§5▶ Cliquez pour réclamer !";
            case LOCKED -> "§c🔒 Level requis : §4" + palier.getRequiredLevel();
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;

        int slot = event.getSlot();
        settings.getPaliers().stream()
                .filter(p -> p.getSlot() == slot)
                .findFirst()
                .ifPresent(palier -> {
                    boolean success = palierManager.claim(island, palier, clicker);
                    if (success) {
                        populate(); // refresh GUI
                    }
                });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }
}
