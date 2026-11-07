package com.legendaryforge.gui;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.item.LegendaryItem;
import com.legendaryforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

import java.util.ArrayList;
import java.util.List;

/**
 * Form NATIVE Bedrock (Floodgate + Cumulus). Dipisah dari LegendaryMenu supaya class Floodgate
 * hanya dimuat bila Floodgate benar-benar terpasang.
 */
final class BedrockMenu {
    private final LegendaryForge plugin;
    private final LegendaryMenu menu;

    BedrockMenu(LegendaryForge plugin, LegendaryMenu menu) { this.plugin = plugin; this.menu = menu; }

    // ----------------------------- Bedrock ----------------------------
    static boolean isBedrock(Player p) {
        return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
    }

    void open(Player p) {
        List<LegendaryItem> list = new ArrayList<>(plugin.items().all());
        SimpleForm.Builder form = SimpleForm.builder()
                .title("✦ Legendary Armory")
                .content("Pilih item untuk melihat detail" + (p.hasPermission("legendaryforge.admin") ? " / mengambilnya." : "."));
        for (LegendaryItem li : list) {
            form.button(Text.plain(li.displayName) + "\n" + li.tier + " - " + Text.plain(li.cfg.getString("element", "")));
        }
        form.validResultHandler(r -> {
            LegendaryItem li = list.get(r.clickedButtonId());
            Bukkit.getScheduler().runTask(plugin, () -> detail(p, li));
        });
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), form);
    }

    /** Halaman detail (Form ke-2) dengan tombol Ambil untuk admin. */
    private void detail(Player p, LegendaryItem li) {
        boolean admin = p.hasPermission("legendaryforge.admin");
        SimpleForm.Builder f = SimpleForm.builder()
                .title(Text.plain(li.displayName))
                .content(Text.plain(li.cfg.getString("nickname", "")) + "\n\n"
                        + "Pasif: " + Text.plain(li.cfg.getString("passive.name", "-")) + "\n"
                        + Text.plain(li.cfg.getString("passive.description", "")) + "\n\n"
                        + "Skill: " + Text.plain(li.skillName()) + "\n"
                        + Text.plain(li.cfg.getString("skill.description", "")) + "\n\n"
                        + "Cooldown: " + li.cooldown() + "s");
        if (admin) f.button("Ambil item");
        f.button("Kembali");
        f.validResultHandler(r -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (admin && r.clickedButtonId() == 0) menu.give(p, li.id); else open(p);
        }));
        FloodgateApi.getInstance().sendForm(p.getUniqueId(), f);
    }
}
