package com.legendaryforge;

import com.legendaryforge.item.LegendaryItem;
import com.legendaryforge.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** /legendary <menu|give <player> <id> [jumlah]|list|reload> */
public final class LegendaryCommand implements CommandExecutor, TabCompleter {
    private final LegendaryForge plugin;
    public LegendaryCommand(LegendaryForge plugin) { this.plugin = plugin; }

    private String prefix() { return plugin.getConfig().getString("prefix", ""); }
    private void say(CommandSender s, String m) { s.sendMessage(Text.c(prefix() + m)); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase();
        switch (sub) {
            case "menu" -> {
                if (s instanceof Player p) plugin.menu().open(p); else say(s, "&cHanya pemain.");
            }
            case "list" -> plugin.items().all().forEach(i -> say(s, "&7- &f" + i.id + " &8(" + i.tier + ")"));
            case "reload" -> {
                if (!s.hasPermission("legendaryforge.admin")) { say(s, "&cTidak ada izin."); return true; }
                plugin.reloadAll();
                say(s, plugin.getConfig().getString("messages.reloaded", "Reloaded")
                        .replace("{count}", String.valueOf(plugin.items().all().size())));
            }
            case "give" -> {
                if (!s.hasPermission("legendaryforge.admin")) { say(s, "&cTidak ada izin."); return true; }
                if (a.length < 3) { say(s, "&cPakai: /legendary give <player> <id> [jumlah]"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                LegendaryItem li = plugin.items().get(a[2]);
                if (t == null) { say(s, "&cPemain tidak online."); return true; }
                if (li == null) { say(s, "&cItem tidak ada. /legendary list"); return true; }
                int amt = a.length > 3 ? Math.max(1, Math.min(64, Integer.parseInt(a[3]))) : 1;
                ItemStack st = plugin.items().build(li);
                st.setAmount(amt);
                t.getInventory().addItem(st);
                say(s, plugin.getConfig().getString("messages.given", "")
                        .replace("{item}", Text.plain(li.displayName)).replace("{player}", t.getName()));
            }
            default -> say(s, "&7/legendary &f<menu|list|give|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(List.of("menu", "list", "give", "reload"));
        else if (a.length == 2 && a[0].equalsIgnoreCase("give")) Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        else if (a.length == 3 && a[0].equalsIgnoreCase("give")) out.addAll(plugin.items().ids());
        String last = a[a.length - 1].toLowerCase();
        out.removeIf(x -> !x.toLowerCase().startsWith(last));
        return out;
    }
}
