package com.legendaryforge.ability;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.item.LegendaryItem;
import com.legendaryforge.util.Text;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Semua skill (klik) + util gabungan. Satu method = satu skill; ID di items.yml (skill.id) dipetakan di cast().
 * Menambah skill baru: tulis method baru, tambah 1 baris di switch cast(), lalu pakai ID-nya di items.yml.
 */
public final class AbilityExecutor {
    private final LegendaryForge plugin;
    private final CooldownManager cooldowns;
    /** Pemain yang sedang menjalankan damage skill -> event damage-nya tidak memicu passive lagi. */
    private final Set<UUID> casting = new HashSet<>();

    public AbilityExecutor(LegendaryForge plugin, CooldownManager cooldowns) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
    }

    public boolean isCasting(Player p) { return casting.contains(p.getUniqueId()); }

    // ===================================================================
    //  SYARAT PEMAKAIAN (Race + AuraSkills level)
    // ===================================================================
    public boolean canUse(Player p, LegendaryItem li, boolean notify) {
        if (p.hasPermission("legendaryforge.bypass")) return true;

        // --- RaceCore: pembatasan race ---
        List<String> allowed = li.allowedRaces();
        if (!allowed.isEmpty() && plugin.hooks().hasRaces()) {
            String race = plugin.hooks().raceOf(p);
            boolean humanBypass = plugin.getConfig().getBoolean("racecore.human-bypass", true) && race.equals("human");
            if (!humanBypass && allowed.stream().noneMatch(a -> a.equalsIgnoreCase(race))) {
                if (notify) p.sendActionBar(Text.c(msg("race-denied").replace("{race}", race)
                        .replace("{allowed}", String.join(", ", allowed))));
                return false;
            }
        }
        // --- AuraSkills: syarat level ---
        if (plugin.hooks().hasAura()) {
            for (Map.Entry<String, Integer> e : li.levelRequirements().entrySet()) {
                if (plugin.hooks().aura().level(p, e.getKey()) < e.getValue()) {
                    if (notify) p.sendActionBar(Text.c(msg("level-denied")
                            .replace("{skill}", e.getKey()).replace("{level}", String.valueOf(e.getValue()))));
                    return false;
                }
            }
        }
        return true;
    }

    private String msg(String key) { return plugin.getConfig().getString("messages." + key, key); }

    // ===================================================================
    //  CAST
    // ===================================================================
    public void cast(Player p, LegendaryItem li) {
        if (li.skillId().equals("NONE") || !canUse(p, li, true)) return;

        double rem = cooldowns.remaining(p.getUniqueId(), li.id);
        if (rem > 0) {
            p.sendActionBar(Text.c(msg("cooldown").replace("{time}", String.format("%.1f", rem))));
            snd(p.getLocation(), "block.note_block.bass", 0.6f, 0.6f);
            return;
        }
        double cost = li.manaCost();
        if (cost > 0 && plugin.hooks().hasAura() && plugin.hooks().aura().mana(p) < cost) {
            p.sendActionBar(Text.c(msg("no-mana").replace("{cost}", String.valueOf((int) cost))));
            return;
        }

        String race = plugin.hooks().raceOf(p);
        double dmg = li.raceDamageMultiplier(race);       // bonus race
        double cdm = li.raceCooldownMultiplier(race);
        ConfigurationSection s = li.skill();

        boolean ok = switch (li.skillId()) {
            case "JUDGMENT_RAY"       -> judgmentRay(p, s, dmg);
            case "ABYSSAL_RIFT"       -> abyssalRift(p, s, dmg);
            case "JUDGMENT_OF_THOR"   -> judgmentOfThor(p, s, dmg);
            case "PURGATORY_SLASH"    -> purgatorySlash(p, s, dmg);
            case "TSUNAMI_PRISON"     -> tsunamiPrison(p, s, dmg);
            case "EARTHQUAKE_SHATTER" -> earthquakeShatter(p, s, dmg);
            case "CYCLONE_DASH"       -> cycloneDash(p, s, dmg);
            default -> { plugin.getLogger().warning("Skill tidak dikenal: " + li.skillId()); yield false; }
        };
        if (!ok) return;

        if (cost > 0 && plugin.hooks().hasAura()) plugin.hooks().aura().setMana(p, plugin.hooks().aura().mana(p) - cost);
        cooldowns.set(p.getUniqueId(), li.id, li.cooldown() * cdm);
        if (li.hasRaceBonus(race)) {
            p.sendMessage(Text.c(plugin.getConfig().getString("prefix", "")
                    + msg("race-bonus").replace("{race}", race)));
        }
    }

    // ===================================================================
    //  TIER 1 — SUPREME
    // ===================================================================

    /** Luminance Scepter — pilar cahaya dari langit. */
    private boolean judgmentRay(Player p, ConfigurationSection s, double mul) {
        double range = s.getDouble("range", 28), radius = s.getDouble("radius", 5), dmg = s.getDouble("damage", 18) * mul;
        Location t = aim(p, range);
        World w = t.getWorld();
        castBar(p, "&e☀ &lJudgment Ray", BossBar.Color.YELLOW, 26);
        snd(t, "block.beacon.activate", 2f, 1.6f);
        Particle.DustOptions gold = new Particle.DustOptions(Color.fromRGB(255, 225, 90), 1.8f);

        new BukkitRunnable() {
            int tick = 0;
            List<LivingEntity> hit = List.of();
            @Override public void run() {
                // pilar (charge) + cincin di tanah
                for (double y = 0; y < 32; y += 0.7) w.spawnParticle(Particle.END_ROD, t.getX(), t.getY() + y, t.getZ(), 1, 0.12, 0, 0.12, 0);
                for (int i = 0; i < 24; i++) {
                    double a = Math.PI * 2 * i / 24;
                    w.spawnParticle(Particle.DUST, t.clone().add(Math.cos(a) * radius, 0.1, Math.sin(a) * radius), 1, gold);
                }
                if (tick == 8) {                       // IMPACT: angkat musuh + buta
                    snd(t, "entity.lightning_bolt.thunder", 2f, 1.4f);
                    snd(t, "entity.warden.sonic_boom", 1.5f, 1.2f);
                    w.spawnParticle(Particle.EXPLOSION, t, 3, 1, 0.2, 1, 0);
                    hit = enemies(t, radius, p);
                    for (LivingEntity e : hit) {
                        e.addPotionEffect(new PotionEffect(Text.effectType("levitation"), 14, 4, false, false));
                        e.addPotionEffect(new PotionEffect(Text.effectType("blindness"), 120, 0, false, false));
                    }
                }
                if (tick == 22) {                      // BANTING ke tanah + damage
                    snd(t, "entity.generic.explosion", 1.6f, 0.8f);
                    for (LivingEntity e : hit) {
                        e.removePotionEffect(Text.effectType("levitation"));
                        e.setVelocity(new Vector(0, -2.2, 0));
                        dmg(p, e, dmg);
                        w.spawnParticle(Particle.END_ROD, e.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.15);
                    }
                }
                if (++tick > 24) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Void Scythe — pusaran gravitasi hitam. */
    private boolean abyssalRift(Player p, ConfigurationSection s, double mul) {
        double radius = s.getDouble("radius", 8);
        int dur = s.getInt("duration", 40);
        Location c = aim(p, 10).add(0, 1, 0);
        World w = c.getWorld();
        castBar(p, "&5◉ &lAbyssal Rift", BossBar.Color.PURPLE, dur);
        snd(c, "entity.warden.sonic_boom", 1.5f, 0.5f);
        snd(c, "block.portal.trigger", 1.2f, 0.5f);

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                // spiral gelap
                for (int i = 0; i < 18; i++) {
                    double a = tick * 0.5 + i * (Math.PI * 2 / 18), r = radius * (1 - (tick / (double) dur)) * (0.4 + 0.6 * (i % 3) / 2.0) + 0.5;
                    w.spawnParticle(Particle.SQUID_INK, c.clone().add(Math.cos(a) * r, Math.sin(tick * 0.3 + i) * 0.8, Math.sin(a) * r), 1, 0, 0, 0, 0);
                    w.spawnParticle(Particle.REVERSE_PORTAL, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0.02);
                }
                for (LivingEntity e : enemies(c, radius, p)) {
                    Vector pull = c.toVector().subtract(e.getLocation().toVector());
                    if (pull.lengthSquared() > 0.5) e.setVelocity(pull.normalize().multiply(0.55).setY(0.12));
                    if (tick == 0) {
                        e.addPotionEffect(new PotionEffect(Text.effectType("darkness"), 120, 0, false, false));
                        e.addPotionEffect(new PotionEffect(Text.effectType("wither"), 120, 3, false, true));   // Wither IV
                        e.addPotionEffect(new PotionEffect(Text.effectType("levitation"), 20, 0, false, false));
                    }
                }
                if (tick == dur) {
                    w.spawnParticle(Particle.EXPLOSION, c, 2, 1, 1, 1, 0);
                    snd(c, "entity.generic.explosion", 1.4f, 0.6f);
                    for (LivingEntity e : enemies(c, radius, p)) dmg(p, e, 6 * mul);
                }
                if (++tick > dur) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Stormcaller Blade — dash petir. */
    private boolean judgmentOfThor(Player p, ConfigurationSection s, double mul) {
        double dist = s.getDouble("distance", 14), dmg = s.getDouble("damage", 12) * mul;
        Vector dir = p.getLocation().getDirection();
        dir.setY(dir.getY() * 0.25).normalize();
        int ticks = (int) Math.ceil(dist / 2.0);
        World w = p.getWorld();
        castBar(p, "&b⚡ &lJudgment of Thor", BossBar.Color.BLUE, ticks + 4);
        snd(p.getLocation(), "entity.lightning_bolt.thunder", 1.4f, 1.6f);
        Set<UUID> struck = new HashSet<>();

        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                p.setVelocity(dir.clone().multiply(2.0));
                p.setFallDistance(0);
                Location l = p.getLocation().add(0, 1, 0);
                for (int i = 0; i < 10; i++) {          // jejak petir bercabang
                    Vector j = Vector.getRandom().subtract(new Vector(.5, .5, .5)).multiply(2.2);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, l.clone().add(j), 3, 0.1, 0.1, 0.1, 0.05);
                }
                w.spawnParticle(Particle.END_ROD, l, 6, 0.4, 0.4, 0.4, 0.02);
                for (LivingEntity e : enemies(l, 3, p)) {
                    if (!struck.add(e.getUniqueId())) continue;
                    w.strikeLightningEffect(e.getLocation());            // visual-only (tanpa api/damage vanilla)
                    dmg(p, e, dmg);
                    e.addPotionEffect(new PotionEffect(Text.effectType("slowness"), 50, 4, false, false));
                    e.addPotionEffect(new PotionEffect(Text.effectType("weakness"), 50, 1, false, false));
                }
                if (++t >= ticks) { p.setVelocity(dir.clone().multiply(0.3)); cancel(); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ===================================================================
    //  TIER 2 — MID
    // ===================================================================

    /** Ignis Core Blade — gelombang api sabit. */
    private boolean purgatorySlash(Player p, ConfigurationSection s, double mul) {
        double range = s.getDouble("range", 12), dmg = s.getDouble("damage", 10) * mul;
        int burn = s.getInt("burn-ticks", 200);
        Location origin = p.getEyeLocation();
        Vector dir = origin.getDirection().setY(0).normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        World w = p.getWorld();
        snd(p.getLocation(), "entity.blaze.shoot", 1.5f, 0.7f);
        snd(p.getLocation(), "entity.player.attack.sweep", 1.5f, 0.6f);
        Set<UUID> hit = new HashSet<>();

        new BukkitRunnable() {
            double d = 1.5;
            @Override public void run() {
                for (int step = 0; step < 2; step++) {                // 2 langkah/tick = gelombang cepat
                    Location c = origin.clone().add(dir.clone().multiply(d)).subtract(0, 0.6, 0);
                    for (int i = -8; i <= 8; i++) {                    // lengkung sabit
                        double a = Math.toRadians(i * 9);
                        Location pt = c.clone()
                                .add(right.clone().multiply(Math.sin(a) * 3.2))
                                .add(dir.clone().multiply(-(1 - Math.cos(a)) * 2.4));
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, pt, 2, 0.05, 0.15, 0.05, 0.01);
                        if (i % 2 == 0) w.spawnParticle(Particle.FLAME, pt, 1, 0.05, 0.1, 0.05, 0.01);
                    }
                    for (LivingEntity e : enemies(c.clone().add(0, 1, 0), 3.4, p)) {
                        if (!hit.add(e.getUniqueId())) continue;
                        dmg(p, e, dmg);
                        e.setFireTicks(burn);
                        w.spawnParticle(Particle.SOUL_FIRE_FLAME, e.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.05);
                    }
                    d += 0.9;
                }
                if (d >= range) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Leviathan's Trident — bola air raksasa. */
    private boolean tsunamiPrison(Player p, ConfigurationSection s, double mul) {
        double radius = s.getDouble("radius", 6), range = s.getDouble("range", 20);
        int dur = s.getInt("duration", 100);
        Location c = aim(p, range).add(0, 2, 0);
        World w = c.getWorld();
        castBar(p, "&9≈ &lTsunami Prison", BossBar.Color.BLUE, dur);
        snd(c, "item.trident.thunder", 1.5f, 0.8f);
        snd(c, "entity.dolphin.splash", 1.5f, 0.6f);
        Random rnd = new Random();

        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                for (int i = 0; i < 45; i++) {                          // cangkang bola
                    double th = rnd.nextDouble() * Math.PI * 2, ph = Math.acos(2 * rnd.nextDouble() - 1);
                    Location pt = c.clone().add(radius * Math.sin(ph) * Math.cos(th), radius * Math.cos(ph), radius * Math.sin(ph) * Math.sin(th));
                    w.spawnParticle(i % 3 == 0 ? Particle.SPLASH : Particle.BUBBLE_POP, pt, 1, 0, 0, 0, 0);
                }
                for (LivingEntity e : enemies(c, radius, p)) {
                    e.addPotionEffect(new PotionEffect(Text.effectType("slowness"), 12, 6, false, false));   // Slowness VII
                    Vector out = e.getLocation().toVector().subtract(c.toVector());
                    if (out.length() > radius - 1) e.setVelocity(out.normalize().multiply(-0.4));          // tak bisa keluar
                }
                if (tick % 20 == 0) {                                   // heal rekan setim tiap 1 detik
                    for (Player a : w.getPlayers()) {
                        if (a.getLocation().distanceSquared(c) <= radius * radius && (a.equals(p) || isAlly(p, a))) {
                            double max = a.getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health"))).getValue();
                            a.setHealth(Math.min(max, a.getHealth() + 2.0));
                            w.spawnParticle(Particle.HEART, a.getLocation().add(0, 2, 0), 2, 0.3, 0.2, 0.3, 0);
                        }
                    }
                }
                if (++tick > dur) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    /** Terra Breaker — hantaman gempa. */
    private boolean earthquakeShatter(Player p, ConfigurationSection s, double mul) {
        double radius = s.getDouble("radius", 7), dmg = s.getDouble("damage", 12) * mul;
        Location c = p.getLocation();
        World w = c.getWorld();
        Material under = c.clone().subtract(0, 1, 0).getBlock().getType();
        if (under.isAir()) under = Material.STONE;
        var data = under.createBlockData();
        snd(c, "entity.generic.explosion", 1.8f, 0.5f);
        snd(c, "block.stone.break", 2f, 0.5f);
        castBar(p, "&6⛰ &lEarthquake Shatter", BossBar.Color.YELLOW, 12);

        for (LivingEntity e : enemies(c, radius, p)) {
            dmg(p, e, dmg);
            e.setVelocity(new Vector(0, 1.15, 0));
            e.addPotionEffect(new PotionEffect(Text.effectType("levitation"), 20, 1, false, false));
            e.addPotionEffect(new PotionEffect(Text.effectType("slowness"), 60, 2, false, false));
        }
        new BukkitRunnable() {                                           // gelombang retakan meluas
            int t = 0;
            @Override public void run() {
                double r = (t + 1) * radius / 10.0;
                for (int i = 0; i < 40; i++) {
                    double a = Math.PI * 2 * i / 40;
                    Location pt = c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r);
                    w.spawnParticle(Particle.BLOCK, pt, 6, 0.2, 0.4, 0.2, 0.1, data);
                    if (i % 4 == 0) w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, pt, 1, 0, 0.1, 0, 0.02);
                }
                if (++t >= 10) cancel();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ===================================================================
    //  TIER 3 — BASE
    // ===================================================================

    /** Zephyr Tempest Bow — ledakan angin / rocket jump. */
    private boolean cycloneDash(Player p, ConfigurationSection s, double mul) {
        double range = s.getDouble("range", 8), kb = s.getDouble("knockback", 2.4), launch = s.getDouble("launch", 1.7);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        boolean down = eye.getPitch() > 55;

        snd(p.getLocation(), "entity.wind_charge.wind_burst", 1.8f, 1.0f);
        if (down) {                                                      // ROCKET JUMP
            Location feet = p.getLocation();
            p.setVelocity(new Vector(dir.getX() * 0.35, launch, dir.getZ() * 0.35));
            p.setFallDistance(0);
            for (int i = 0; i < 40; i++) {
                double a = Math.PI * 2 * i / 40;
                w.spawnParticle(Particle.CLOUD, feet.clone().add(Math.cos(a) * 1.2, 0.1, Math.sin(a) * 1.2), 0, Math.cos(a), 0.1, Math.sin(a), 0.35);
            }
            for (LivingEntity e : enemies(feet, 4, p)) {
                Vector out = e.getLocation().toVector().subtract(feet.toVector()).setY(0.3);
                if (out.lengthSquared() > 0) e.setVelocity(out.normalize().multiply(1.4).setY(0.5));
            }
        } else {                                                         // TIUP MUNDUR (kerucut)
            for (double d = 1; d <= range; d += 0.8) {
                Location pt = eye.clone().add(dir.clone().multiply(d));
                w.spawnParticle(Particle.CLOUD, pt, 4, 0.2 + d * 0.06, 0.2 + d * 0.06, 0.2 + d * 0.06, 0.03);
                w.spawnParticle(Particle.POOF, pt, 1, 0.2, 0.2, 0.2, 0.02);
            }
            for (LivingEntity e : enemies(eye.clone().add(dir.clone().multiply(range / 2)), range, p)) {
                Vector to = e.getEyeLocation().toVector().subtract(eye.toVector());
                if (to.lengthSquared() == 0 || to.normalize().dot(dir) < 0.6) continue;
                e.setVelocity(dir.clone().multiply(kb).setY(0.45));
                dmg(p, e, 4 * mul);
            }
        }
        return true;
    }

    // ===================================================================
    //  PASSIVE yang butuh dipanggil dari listener
    // ===================================================================

    /** Overcharge: Chain Lightning dari target pertama ke musuh terdekat. */
    public void chainLightning(Player p, LivingEntity first, ConfigurationSection pass, double mul) {
        int n = pass.getInt("chain-targets", 3);
        double rad = pass.getDouble("chain-radius", 6), dmg = pass.getDouble("chain-damage", 8) * mul;
        World w = first.getWorld();
        snd(first.getLocation(), "entity.lightning_bolt.thunder", 1.2f, 1.8f);
        w.strikeLightningEffect(first.getLocation());
        Set<UUID> seen = new HashSet<>(List.of(first.getUniqueId()));
        LivingEntity from = first;
        for (int i = 0; i < n; i++) {
            LivingEntity next = null; double best = Double.MAX_VALUE;
            for (LivingEntity e : enemies(from.getLocation(), rad, p)) {
                if (seen.contains(e.getUniqueId())) continue;
                double d = e.getLocation().distanceSquared(from.getLocation());
                if (d < best) { best = d; next = e; }
            }
            if (next == null) break;
            arc(from.getEyeLocation(), next.getEyeLocation());
            dmg(p, next, dmg);
            next.addPotionEffect(new PotionEffect(Text.effectType("slowness"), 30, 1, false, false));
            seen.add(next.getUniqueId());
            from = next;
        }
    }

    /** Soul Harvester: dipanggil saat pemilik membunuh. */
    public void soulHarvest(Player p, ConfigurationSection pass, Location at) {
        int dur = pass.getInt("duration", 200);
        p.addPotionEffect(new PotionEffect(Text.effectType("absorption"), dur, pass.getInt("absorption-level", 2), false, true));
        p.addPotionEffect(new PotionEffect(Text.effectType("strength"), dur, pass.getInt("strength-level", 4), false, true)); // Strength V
        World w = p.getWorld();
        snd(p.getLocation(), "entity.wither.ambient", 0.8f, 1.6f);
        for (int i = 0; i < 25; i++) {
            Location from = at.clone().add(0, 1, 0);
            Vector v = p.getEyeLocation().toVector().subtract(from.toVector()).multiply(0.08 * (i % 5 + 1));
            w.spawnParticle(Particle.SOUL, from, 0, v.getX(), v.getY(), v.getZ(), 1);
        }
        p.sendActionBar(Text.c("&5☠ &dSoul Harvested! &7Absorption + Strength V"));
    }

    // ===================================================================
    //  UTIL
    // ===================================================================

    /** Damage skill (tidak memicu passive on-hit). */
    public void dmg(Player p, LivingEntity t, double amount) {
        casting.add(p.getUniqueId());
        try { t.damage(amount, p); } finally { casting.remove(p.getUniqueId()); }
    }

    public List<LivingEntity> enemies(Location c, double r, Player caster) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : c.getWorld().getNearbyLivingEntities(c, r)) {
            if (e.equals(caster) || e.isDead() || e instanceof ArmorStand || isAlly(caster, e)) continue;
            if (e instanceof Player pl && (pl.getGameMode() == GameMode.SPECTATOR || pl.getGameMode() == GameMode.CREATIVE)) continue;
            out.add(e);
        }
        return out;
    }

    /** Ally = pemain di scoreboard team yang sama. Ganti di sini bila memakai plugin Party/Clan. */
    public boolean isAlly(Player caster, LivingEntity other) {
        if (!(other instanceof Player o)) return false;
        Team t = caster.getScoreboard().getEntryTeam(caster.getName());
        Team u = o.getScoreboard().getEntryTeam(o.getName());
        return t != null && t.equals(u);
    }

    /** Titik bidik pemain (block / entity / jarak maksimum). */
    private Location aim(Player p, double range) {
        Location eye = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, 0.4,
                e -> !e.equals(p) && e instanceof LivingEntity);
        return r != null ? r.getHitPosition().toLocation(p.getWorld())
                         : eye.clone().add(eye.getDirection().multiply(range));
    }

    private void arc(Location a, Location b) {
        Vector v = b.toVector().subtract(a.toVector());
        double len = v.length();
        v.normalize();
        for (double d = 0; d < len; d += 0.35) {
            Vector jitter = Vector.getRandom().subtract(new Vector(.5, .5, .5)).multiply(0.35);
            a.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, a.clone().add(v.clone().multiply(d)).add(jitter), 1, 0, 0, 0, 0);
        }
    }

    private void snd(Location l, String key, float vol, float pitch) {
        l.getWorld().playSound(l, key, SoundCategory.PLAYERS, vol, pitch);
    }

    /** Bossbar "cast bar" yang menyusut selama skill aktif. */
    private void castBar(Player p, String title, BossBar.Color color, int ticks) {
        if (!plugin.getConfig().getBoolean("display.bossbar-cast", true)) return;
        BossBar bar = BossBar.bossBar(Text.c(title), 1f, color, BossBar.Overlay.NOTCHED_10);
        p.showBossBar(bar);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || t >= ticks) { p.hideBossBar(bar); cancel(); return; }
                bar.progress(Math.max(0f, 1f - t / (float) ticks));
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
