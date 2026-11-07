package com.legendaryforge.hooks;

import com.legendaryforge.LegendaryForge;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Fasad semua integrasi. Kode lain cukup memanggil method di sini (aman bila plugin target tidak ada). */
public final class HookManager {
    private final LegendaryForge plugin;
    private RaceCoreHook race;
    private AuraSkillsHook aura;

    public HookManager(LegendaryForge plugin) { this.plugin = plugin; }

    public void init() {
        if (plugin.getConfig().getBoolean("racecore.enabled", true)) {
            race = RaceCoreHook.create();
            plugin.getLogger().info("RaceCore hook: " + (race != null ? "AKTIF" : "tidak ditemukan"));
        }
        if (plugin.getConfig().getBoolean("auraskills.enabled", true)
                && Bukkit.getPluginManager().isPluginEnabled("AuraSkills")) {
            try {
                aura = new AuraSkillsHook();
                Bukkit.getPluginManager().registerEvents(new AuraXpListener(plugin), plugin);
                plugin.getLogger().info("AuraSkills hook: AKTIF");
            } catch (Throwable t) {
                plugin.getLogger().warning("AuraSkills hook gagal (versi API beda?): " + t);
                aura = null;
            }
        }
    }

    public boolean hasRaces() { return race != null; }
    public boolean hasAura() { return aura != null; }
    public AuraSkillsHook aura() { return aura; }

    /** Race pemain; "human" bila RaceCore tidak ada. */
    public String raceOf(Player p) { return race == null ? "human" : race.raceOf(p); }
}
