package com.legendaryforge.gui;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.item.LegendaryItem;
import com.legendaryforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Map;

/**
 * Menu katalog item legendaris.
 *  - Java    : Chest GUI (3 baris, 1 baris per tier).
 *  - Bedrock : Floodgate+Cumulus SimpleForm native (bila Floodgate ada).
 *              Chest GUI juga tetap jalan di Bedrock karena Geyser mengubahnya jadi container.
 * Klik item = admin menerima item; pemain biasa hanya melihat detail.
 */
public final class LegendaryMenu implements Listener {
    private final LegendaryForge plugin;

    public LegendaryMenu(LegendaryForge plugin) { this.plugin = plugin; }

    private static final class Holder implements InventoryHolder {
        final Map<Integer, String> slotToId = new java.util.HashMap<>();
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        if (plugin.floodgateAvailable() && BedrockMenu.isBedrock(p)) { new BedrockMenu(plugin, this).open(p); return; }
        openChest(p);
    }

    // ------------------------------ Java ------------------------------
    private void openChest(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 27, Text.c("&8✦ &6Legendary Armory &8✦"));
        h.inv = inv;
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta(); pm.displayName(Text.c(" ")); pane.setItemMeta(pm);
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);

        int[] col = new int[LegendaryItem.Tier.values().length];
        for (LegendaryItem li : plugin.items().all()) {
            int row = li.tier.ordinal();
            int slot = row * 9 + 1 + col[row]++;
            if (slot >= row * 9 + 8) continue;
            inv.setItem(slot, plugin.items().build(li));
            h.slotToId.put(slot, li.id);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String id = h.slotToId.get(e.getRawSlot());
        if (id != null) give(p, id);
    }

    /** Dipakai juga oleh BedrockMenu. */
    void give(Player p, String id) {
        if (!p.hasPermission("legendaryforge.admin")) {
            p.sendMessage(Text.c("&7Hanya preview. Item legendaris didapat dari event/boss."));
            return;
        }
        LegendaryItem li = plugin.items().get(id);
        if (li != null) p.getInventory().addItem(plugin.items().build(li));
    }

}
