package com.legendaryforge.ability;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.item.LegendaryItem;
import com.legendaryforge.util.Text;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Loop tiap 2 tick:
 *  - menampilkan ACTIONBAR (nama skill + progres cooldown / SIAP)
 *  - tiap 10 tick: menerapkan passive "saat dipegang" (efek konstan, aura, stat AuraSkills)
 */
public final class PassiveTask extends BukkitRunnable {
    private final LegendaryForge plugin;
    private final Map<UUID, String> lastHeld = new HashMap<>();
    private final NamespacedKey titanKey;
    private int counter = 0;

    public PassiveTask(LegendaryForge plugin) {
        this.plugin = plugin;
        this.titanKey = new NamespacedKey(plugin, "titan_stance_kb");
    }

    @Override public void run() {
        boolean passiveTick = (counter++ % 5 == 0);
        for (Player p : Bukkit.getOnlinePlayers()) {
            LegendaryItem li = plugin.items().fromStack(p.getInventory().getItemInMainHand());
            handleHeldChange(p, li);
            if (li == null) continue;

            boolean usable = plugin.executor().canUse(p, li, false);
            if (plugin.getConfig().getBoolean("display.actionbar", true)) actionBar(p, li, usable);
            if (passiveTick && usable) applyPassive(p, li);
        }
    }

    // ------------------------------------------------------------------
    private void actionBar(Player p, LegendaryItem li, boolean usable) {
        if (!usable) { p.sendActionBar(Text.c("&c✖ Kamu belum memenuhi syarat item ini")); return; }
        if (li.skillId().equals("NONE")) return;
        double rem = plugin.cooldowns().remaining(p.getUniqueId(), li.id);
        double prog = plugin.cooldowns().progress(p.getUniqueId(), li.id);
        String state = rem <= 0 ? "&a&lSIAP" : "&c" + String.format("%.1fs", rem);
        String mana = (li.manaCost() > 0 && plugin.hooks().hasAura())
                ? "  &b✦ " + (int) plugin.hooks().aura().mana(p) + "&8/&b" + (int) li.manaCost() : "";
        p.sendActionBar(Text.c("&d" + li.skillName().split(" \\(")[0] + " " + Text.bar(prog, 14) + " " + state + mana));
    }

    /** Saat ganti item di tangan: bersihkan stat/atribut item lama, pasang punya item baru. */
    private void handleHeldChange(Player p, LegendaryItem now) {
        String nowId = now == null ? null : now.id;
        String old = lastHeld.get(p.getUniqueId());
        if (java.util.Objects.equals(old, nowId)) return;

        if (old != null) {
            LegendaryItem prev = plugin.items().get(old);
            if (prev != null) unequip(p, prev);
        }
        if (nowId == null) lastHeld.remove(p.getUniqueId()); else lastHeld.put(p.getUniqueId(), nowId);
        if (now != null && plugin.executor().canUse(p, now, false)) equip(p, now);
    }

    private void equip(Player p, LegendaryItem li) {
        if (plugin.hooks().hasAura()) plugin.hooks().aura().applyStats(p, li.id, li.statBonus());   // Stats AuraSkills
        if (li.passiveId().equals("TITAN_STANCE")) {                                                // kebal knockback
            AttributeInstance inst = p.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("knockback_resistance")));
            if (inst != null && inst.getModifier(titanKey) == null)
                inst.addTransientModifier(new AttributeModifier(titanKey, 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }

    public void unequip(Player p, LegendaryItem li) {
        if (plugin.hooks().hasAura()) plugin.hooks().aura().clearStats(p, li.id, li.statBonus());
        if (li.passiveId().equals("TITAN_STANCE")) {
            AttributeInstance inst = p.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("knockback_resistance")));
            if (inst != null) inst.removeModifier(titanKey);
        }
    }

    public void forget(Player p) {
        String old = lastHeld.remove(p.getUniqueId());
        if (old != null) { LegendaryItem prev = plugin.items().get(old); if (prev != null) unequip(p, prev); }
    }

    // ------------------------------------------------------------------
    private void applyPassive(Player p, LegendaryItem li) {
        // efek konstan sederhana dari "passive.effects"
        for (String spec : li.passiveEffects()) {
            PotionEffect e = Text.parseEffect(spec, 60);
            if (e != null) p.addPotionEffect(e);
        }
        ConfigurationSection ps = li.passive();
        if (ps == null) return;
        World w = p.getWorld();

        switch (li.passiveId()) {
            case "DIVINE_GRACE" -> {      // Luminance: musuh Glowing (tembus dinding), rekan regen
                double r = ps.getDouble("radius", 14);
                PotionEffectType glow = Text.effectType("glowing"), regen = Text.effectType("regeneration");
                for (LivingEntity e : w.getNearbyLivingEntities(p.getLocation(), r)) {
                    if (e.equals(p)) continue;
                    boolean friend = e instanceof Player && plugin.executor().isAlly(p, e);
                    if (friend) e.addPotionEffect(new PotionEffect(regen, 60, 1, true, true, true));
                    else if (e instanceof Monster || e instanceof Player)
                        e.addPotionEffect(new PotionEffect(glow, 60, 0, true, false, false));
                }
                p.addPotionEffect(new PotionEffect(regen, 60, 0, true, false, true));
                w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2.2, 0), 2, 0.3, 0.1, 0.3, 0.01);
            }
            case "MAGMA_BLOOD" ->
                w.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
            case "TIDAL_FLOW" -> {        // Leviathan: aktif dekat air / hujan
                if (nearWater(p) || raining(p)) {
                    for (String spec : ps.getStringList("effects-active")) {
                        PotionEffect e = Text.parseEffect(spec, 60);
                        if (e != null) p.addPotionEffect(e);
                    }
                    w.spawnParticle(Particle.BUBBLE_POP, p.getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0);
                }
            }
            case "AIR_WALKER" ->
                w.spawnParticle(Particle.CLOUD, p.getLocation(), 1, 0.2, 0, 0.2, 0.01);
            default -> { }
        }
    }

    private boolean raining(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation();
        return w.hasStorm() && w.getHighestBlockYAt(l) <= l.getBlockY();
    }

    private boolean nearWater(Player p) {
        if (p.isInWater()) return true;
        Location l = p.getLocation();
        for (int x = -2; x <= 2; x++) for (int y = -1; y <= 2; y++) for (int z = -2; z <= 2; z++)
            if (l.clone().add(x, y, z).getBlock().getType() == Material.WATER) return true;
        return false;
    }
}
