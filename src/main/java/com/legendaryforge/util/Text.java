package com.legendaryforge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Helper kecil: warna &-code, lookup registry (aman lintas versi), efek potion. */
public final class Text {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private Text() {}

    /** "&6Halo" -> Component tanpa italic default (cocok untuk name/lore item). */
    public static Component c(String s) {
        return LEGACY.deserialize(s == null ? "" : s).decoration(TextDecoration.ITALIC, false);
    }

    /** Teks polos tanpa warna (untuk tombol Form Bedrock). */
    public static String plain(String s) {
        return PlainTextComponentSerializer.plainText().serialize(c(s));
    }

    public static PotionEffectType effectType(String name) {
        return Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(name.toLowerCase()));
    }

    public static Enchantment enchant(String name) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name.toLowerCase()));
    }

    /** Parse "speed:1" (level 0-based) -> PotionEffect (partikel disembunyikan). */
    public static PotionEffect parseEffect(String spec, int durationTicks) {
        String[] p = spec.split(":");
        PotionEffectType t = effectType(p[0]);
        if (t == null) return null;
        int amp = p.length > 1 ? Integer.parseInt(p[1]) : 0;
        return new PotionEffect(t, durationTicks, amp, true, false, true);
    }

    public static String bar(double fraction, int len) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * len);
        return "&a" + "▮".repeat(filled) + "&8" + "▯".repeat(len - filled);
    }
}
