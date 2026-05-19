package world.bentobox.bskyblock.donate.gui;

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
import world.bentobox.bskyblock.donate.DonateItemConfig;
import world.bentobox.bskyblock.donate.DonateManager;
import world.bentobox.bskyblock.donate.DonateSettings;

import java.util.ArrayList;
import java.util.List;

public class DonateGUI implements Listener {

    private final BSkyBlock addon;
    private final Island island;
    private final Player player;
    private final DonateManager donateManager;
    private final DonateSettings settings;
    private Inventory inventory;

    public DonateGUI(BSkyBlock addon, Island island, Player player) {
        this.addon = addon;
        this.island = island;
        this.player = player;
        this.donateManager = addon.getDonateManager();
        this.settings = addon.getDonateSettings();
    }

    public void open() {
        inventory = Bukkit.createInventory(null, settings.getGuiSize(), settings.getGuiTitle());
        populate();
        player.openInventory(inventory);
        addon.getPlugin().getServer().getPluginManager().registerEvents(this, addon.getPlugin());
    }

    private void populate() {
        inventory.clear();
        for (DonateItemConfig item : settings.getItems()) {
            inventory.setItem(item.getSlot(), buildItem(item));
        }
    }

    private ItemStack buildItem(DonateItemConfig cfg) {
        ItemStack display = resolveIcon(cfg);
        ItemMeta meta = display.getItemMeta();
        if (meta == null) return display;

        meta.setDisplayName(cfg.getDisplayName());

        List<String> lore = new ArrayList<>(cfg.getLore());
        if (!lore.isEmpty()) lore.add("");

        lore.add("§7Quantité : §e" + cfg.getAmount());
        lore.add("§6+ " + cfg.getHonour() + " honneur");
        lore.add("");

        if (donateManager.hasItems(player, cfg)) {
            lore.add("§a▶ Cliquer pour donner");
        } else {
            lore.add("§c✗ Inventaire insuffisant");
        }

        meta.setLore(lore);
        display.setItemMeta(meta);
        return display;
    }

    private ItemStack resolveIcon(DonateItemConfig cfg) {
        String icon = cfg.getIcon();

        // Try ItemsAdder custom stack first (covers both type and icon fields)
        if (icon.contains(":")) {
            ItemStack custom = donateManager.getItemsAdderStack(icon);
            if (custom != null) return custom.clone();
            // Fall through to vanilla fallback
            return new ItemStack(Material.CHEST);
        }

        Material mat = Material.matchMaterial(icon);
        if (mat != null) return new ItemStack(mat);

        // Ultimate fallback
        return new ItemStack(Material.CHEST);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player clicker)) return;
        if (!clicker.equals(player)) return;

        int slot = event.getSlot();
        settings.getItems().stream()
                .filter(i -> i.getSlot() == slot)
                .findFirst()
                .ifPresent(item -> {
                    donateManager.donate(island, item, clicker);
                    populate(); // refresh to update availability indicators
                });
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        HandlerList.unregisterAll(this);
    }
}
