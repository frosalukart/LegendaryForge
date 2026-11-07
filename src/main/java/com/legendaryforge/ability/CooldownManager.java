package com.legendaryforge.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Cooldown per (pemain, item). Waktu dalam ms. */
public final class CooldownManager {
    private final Map<UUID, Map<String, long[]>> map = new HashMap<>(); // {expiry, total}

    public void set(UUID u, String itemId, double seconds) {
        long total = (long) (seconds * 1000);
        map.computeIfAbsent(u, k -> new HashMap<>()).put(itemId, new long[]{System.currentTimeMillis() + total, total});
    }

    /** Sisa detik (0 bila siap). */
    public double remaining(UUID u, String itemId) {
        Map<String, long[]> m = map.get(u);
        if (m == null) return 0;
        long[] v = m.get(itemId);
        if (v == null) return 0;
        long left = v[0] - System.currentTimeMillis();
        return left <= 0 ? 0 : left / 1000.0;
    }

    /** Fraksi progres 0..1 (1 = siap). */
    public double progress(UUID u, String itemId) {
        Map<String, long[]> m = map.get(u);
        if (m == null || m.get(itemId) == null) return 1;
        long[] v = m.get(itemId);
        if (v[1] <= 0) return 1;
        return 1.0 - Math.min(1.0, Math.max(0, (v[0] - System.currentTimeMillis()) / (double) v[1]));
    }

    public void clear(UUID u) { map.remove(u); }
}
