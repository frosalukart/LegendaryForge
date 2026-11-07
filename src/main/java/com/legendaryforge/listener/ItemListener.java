package com.legendaryforge.listener;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.item.LegendaryItem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Event utama: trigger skill, passive on-hit, on-kill, anti-fall-damage, cleanup. */
public final class ItemListener implements Listener {
    private final LegendaryForge plugin;
    private final Map<UUID, Integer> overcharge = new HashMap<>();

    public ItemListener(LegendaryForge plugin) { this.plugin = plugin; }

    // ---------------- SKILL TRIGGER (klik) ----------------
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        LegendaryItem li = plugin.items().fromStack(p.getInventory().getItemInMainHand());
        if (li == null) return;

        boolean right = e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK;
        boolean left = e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK;

        LegendaryItem.Trigger t = li.trigger();
        boolean match = switch (t) {
            case RIGHT_CLICK       -> right && !p.isSneaking();
            case SHIFT_RIGHT_CLICK -> right && p.isSneaking();
            case LEFT_CLICK        -> left;
        };
        if (!match) return;

        e.setUseItemInHand(Event.Result.DENY);        // jangan menarik busur / memakai item vanilla
        e.setUseInteractedBlock(Event.Result.DENY);
        plugin.executor().cast(p, li);
    }

    // ---------------- ON-HIT (passive + bonus race + syarat) ----------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Player p = e.getDamager() instanceof Player pl ? pl
                : (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null);
        if (p == null || plugin.executor().isCasting(p)) return;

        LegendaryItem li = plugin.items().fromStack(p.getInventory().getItemInMainHand());
        if (li == null) return;

        if (!plugin.executor().canUse(p, li, true)) { e.setCancelled(true); return; }   // syarat tidak terpenuhi

        String race = plugin.hooks().raceOf(p);
        e.setDamage(e.getDamage() * li.raceDamageMultiplier(race));                      // bonus damage race

        if (!(e.getEntity() instanceof LivingEntity target)) return;
        var ps = li.passive();
        if (ps == null) return;

        switch (li.passiveId()) {
            case "OVERCHARGE" -> {                         // 3 hit biasa, hit ke-4 = chain lightning
                int every = ps.getInt("every", 4);
                int n = overcharge.merge(p.getUniqueId(), 1, Integer::sum);
                if (n >= every) {
                    overcharge.put(p.getUniqueId(), 0);
                    plugin.executor().chainLightning(p, target, ps, li.raceDamageMultiplier(race));
                }
            }
            case "MAGMA_BLOOD" -> target.setFireTicks(ps.getInt("burn-ticks", 160));
            default -> { }
        }
    }

    // ---------------- ON-KILL (Soul Harvester) ----------------
    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null) return;
        LegendaryItem li = plugin.items().fromStack(killer.getInventory().getItemInMainHand());
        if (li == null || !li.passiveId().equals("SOUL_HARVESTER") || !plugin.executor().canUse(killer, li, false)) return;
        plugin.executor().soulHarvest(killer, li.passive(), e.getEntity().getLocation());
    }

    // ---------------- FALL DAMAGE (Air Walker) ----------------
    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL || !(e.getEntity() instanceof Player p)) return;
        LegendaryItem li = plugin.items().fromStack(p.getInventory().getItemInMainHand());
        LegendaryItem off = plugin.items().fromStack(p.getInventory().getItemInOffHand());
        if ((li != null && li.passiveId().equals("AIR_WALKER")) || (off != null && off.passiveId().equals("AIR_WALKER")))
            e.setCancelled(true);
    }

    // ---------------- CLEANUP ----------------
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.passives().forget(e.getPlayer());
        overcharge.remove(e.getPlayer().getUniqueId());
    }
}
