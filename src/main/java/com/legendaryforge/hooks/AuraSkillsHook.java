package com.legendaryforge.hooks;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.skill.Skill;
import dev.aurelium.auraskills.api.stat.Stat;
import dev.aurelium.auraskills.api.stat.StatModifier;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * Hook AuraSkills 2.x. Class ini HANYA dimuat bila AuraSkills terpasang
 * (HookManager memanggil create() di dalam try/catch), jadi aman tanpa AuraSkills.
 */
public final class AuraSkillsHook {
    private final AuraSkillsApi api = AuraSkillsApi.get();

    private SkillsUser user(Player p) { return api.getUser(p.getUniqueId()); }

    public int level(Player p, String skillName) {
        Skill s = api.getGlobalRegistry().getSkill(NamespacedId.fromDefault(skillName));
        return s == null ? 0 : user(p).getSkillLevel(s);
    }

    /** Beri Stat bonus (strength, toughness, wisdom=mana, ...) saat item dipegang. */
    public void applyStats(Player p, String itemId, Map<String, Double> stats) {
        SkillsUser u = user(p);
        stats.forEach((name, val) -> {
            Stat st = api.getGlobalRegistry().getStat(NamespacedId.fromDefault(name));
            if (st != null) u.addStatModifier(new StatModifier("lf_" + itemId + "_" + name, st, val));
        });
    }

    public void clearStats(Player p, String itemId, Map<String, Double> stats) {
        SkillsUser u = user(p);
        stats.keySet().forEach(name -> u.removeStatModifier("lf_" + itemId + "_" + name));
    }

    public double mana(Player p) { return user(p).getMana(); }
    public void setMana(Player p, double v) { user(p).setMana(v); }
}
