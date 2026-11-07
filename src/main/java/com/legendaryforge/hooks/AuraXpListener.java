package com.legendaryforge.hooks;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.item.LegendaryItem;
import dev.aurelium.auraskills.api.event.skill.XpGainEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Skill XP Boost: persen bonus XP (xp-boost di items.yml) saat item legendaris dipegang. */
public final class AuraXpListener implements Listener {
    private final LegendaryForge plugin;
    public AuraXpListener(LegendaryForge plugin) { this.plugin = plugin; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onXp(XpGainEvent e) {
        LegendaryItem li = plugin.items().fromStack(e.getPlayer().getInventory().getItemInMainHand());
        if (li == null) return;
        Double pct = li.xpBoost().get(e.getSkill().getId().getKey().toLowerCase());
        if (pct != null && pct > 0) e.setAmount(e.getAmount() * (1.0 + pct / 100.0));
    }
}
