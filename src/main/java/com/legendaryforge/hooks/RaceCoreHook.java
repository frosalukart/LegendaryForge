package com.legendaryforge.hooks;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Hook RaceCore 2.0.0 via REFLECTION (tanpa compile-dependency pada jar RaceCore).
 * API yang dipakai (hasil inspeksi jar):
 *   RaceCore#data()  -> DataManager
 *   DataManager#raceOf(UUID) -> Race     (Race.id = public String, huruf kecil, mis. "dragonkin")
 */
public final class RaceCoreHook {
    private final Object dataManager;
    private final Method raceOf;
    private final Field idField;

    private RaceCoreHook(Object dm, Method raceOf, Field idField) {
        this.dataManager = dm; this.raceOf = raceOf; this.idField = idField;
    }

    /** @return hook, atau null bila RaceCore tidak ada / API berbeda. */
    public static RaceCoreHook create() {
        try {
            Plugin rc = Bukkit.getPluginManager().getPlugin("RaceCore");
            if (rc == null || !rc.isEnabled()) return null;
            Object dm = rc.getClass().getMethod("data").invoke(rc);
            Method raceOf = dm.getClass().getMethod("raceOf", java.util.UUID.class);
            Field id = Class.forName("com.racecore.Race", true, rc.getClass().getClassLoader()).getField("id");
            return new RaceCoreHook(dm, raceOf, id);
        } catch (Throwable t) {
            Bukkit.getLogger().warning("[LegendaryForge] Hook RaceCore gagal: " + t);
            return null;
        }
    }

    /** ID race pemain (lowercase), "human" bila tidak diketahui. */
    public String raceOf(Player p) {
        try {
            Object race = raceOf.invoke(dataManager, p.getUniqueId());
            return race == null ? "human" : String.valueOf(idField.get(race)).toLowerCase();
        } catch (Throwable t) {
            return "human";
        }
    }
}
