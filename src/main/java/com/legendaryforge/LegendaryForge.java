package com.legendaryforge;

import com.legendaryforge.ability.AbilityExecutor;
import com.legendaryforge.ability.CooldownManager;
import com.legendaryforge.ability.PassiveTask;
import com.legendaryforge.gui.LegendaryMenu;
import com.legendaryforge.hooks.HookManager;
import com.legendaryforge.item.ItemManager;
import com.legendaryforge.listener.ItemListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Main class: menyusun semua komponen. */
public final class LegendaryForge extends JavaPlugin {
    private ItemManager items;
    private CooldownManager cooldowns;
    private HookManager hooks;
    private AbilityExecutor executor;
    private PassiveTask passives;
    private LegendaryMenu menu;
    private BukkitTask passiveTask;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!new java.io.File(getDataFolder(), "items.yml").exists()) saveResource("items.yml", false);

        items = new ItemManager(this);
        cooldowns = new CooldownManager();
        hooks = new HookManager(this);
        executor = new AbilityExecutor(this, cooldowns);
        menu = new LegendaryMenu(this);

        items.load();
        hooks.init();

        Bukkit.getPluginManager().registerEvents(new ItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(menu, this);

        LegendaryCommand cmd = new LegendaryCommand(this);
        getCommand("legendary").setExecutor(cmd);
        getCommand("legendary").setTabCompleter(cmd);

        startPassives();
        getLogger().info("LegendaryForge aktif — " + items.all().size() + " item.");
    }

    @Override public void onDisable() {
        if (passiveTask != null) passiveTask.cancel();
        if (passives != null) Bukkit.getOnlinePlayers().forEach(passives::forget);   // lepas stat/atribut
    }

    private void startPassives() {
        if (passiveTask != null) passiveTask.cancel();
        passives = new PassiveTask(this);
        passiveTask = passives.runTaskTimer(this, 20L, 2L);
    }

    public void reloadAll() {
        reloadConfig();
        items.load();
    }

    public boolean floodgateAvailable() { return Bukkit.getPluginManager().isPluginEnabled("floodgate"); }

    public ItemManager items() { return items; }
    public CooldownManager cooldowns() { return cooldowns; }
    public HookManager hooks() { return hooks; }
    public AbilityExecutor executor() { return executor; }
    public PassiveTask passives() { return passives; }
    public LegendaryMenu menu() { return menu; }
}
