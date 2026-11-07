package com.legendaryforge.item;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Representasi 1 section di items.yml. Data mentah disimpan di {@link #cfg};
 * getter di bawah hanya jalan pintas supaya kode ability tetap bersih.
 */
public final class LegendaryItem {
    public enum Tier { SUPREME, MID, BASE }
    public enum Trigger { RIGHT_CLICK, SHIFT_RIGHT_CLICK, LEFT_CLICK }

    public final String id;
    public final ConfigurationSection cfg;
    public final Tier tier;
    public final Material material;
    public final int modelData;
    public final String displayName;

    public LegendaryItem(String id, ConfigurationSection cfg) {
        this.id = id;
        this.cfg = cfg;
        this.tier = Tier.valueOf(cfg.getString("tier", "BASE").toUpperCase());
        Material m = Material.matchMaterial(cfg.getString("material", "STICK"));
        this.material = m == null ? Material.STICK : m;
        this.modelData = cfg.getInt("model-data", 0);
        this.displayName = cfg.getString("display-name", id);
    }

    // ---- passive ----
    public String passiveId() { return cfg.getString("passive.id", "NONE").toUpperCase(); }
    public ConfigurationSection passive() { return cfg.getConfigurationSection("passive"); }
    public List<String> passiveEffects() { return cfg.getStringList("passive.effects"); }

    // ---- skill ----
    public String skillId() { return cfg.getString("skill.id", "NONE").toUpperCase(); }
    public String skillName() { return cfg.getString("skill.name", skillId()); }
    public ConfigurationSection skill() { return cfg.getConfigurationSection("skill"); }
    public Trigger trigger() {
        try { return Trigger.valueOf(cfg.getString("skill.trigger", "RIGHT_CLICK").toUpperCase()); }
        catch (IllegalArgumentException e) { return Trigger.RIGHT_CLICK; }
    }
    public double cooldown() { return cfg.getDouble("skill.cooldown", 10); }
    public double manaCost() { return cfg.getDouble("skill.mana-cost", 0); }

    // ---- requirements ----
    public List<String> allowedRaces() { return cfg.getStringList("requirements.races"); }
    public Map<String, Integer> levelRequirements() {
        Map<String, Integer> out = new LinkedHashMap<>();
        ConfigurationSection s = cfg.getConfigurationSection("requirements.auraskills");
        if (s != null) for (String k : s.getKeys(false)) out.put(k.toLowerCase(), s.getInt(k));
        return out;
    }

    // ---- race bonus ----
    public double raceDamageMultiplier(String race) { return cfg.getDouble("race-bonus." + race + ".damage-multiplier", 1.0); }
    public double raceCooldownMultiplier(String race) { return cfg.getDouble("race-bonus." + race + ".cooldown-multiplier", 1.0); }
    public boolean hasRaceBonus(String race) { return cfg.isConfigurationSection("race-bonus." + race); }

    // ---- AuraSkills ----
    public Map<String, Double> statBonus() { return numMap("stat-bonus"); }
    public Map<String, Double> xpBoost() { return numMap("xp-boost"); }

    private Map<String, Double> numMap(String path) {
        Map<String, Double> out = new LinkedHashMap<>();
        ConfigurationSection s = cfg.getConfigurationSection(path);
        if (s != null) for (String k : s.getKeys(false)) out.put(k.toLowerCase(), s.getDouble(k));
        return out;
    }
}
