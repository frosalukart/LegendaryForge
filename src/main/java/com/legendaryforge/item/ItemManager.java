package com.legendaryforge.item;

import com.legendaryforge.LegendaryForge;
import com.legendaryforge.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.*;

/** Memuat items.yml & membangun ItemStack. ID item disimpan di PersistentDataContainer (anti-rename/anvil). */
public final class ItemManager {
    private final LegendaryForge plugin;
    private final Map<String, LegendaryItem> items = new LinkedHashMap<>();
    private final NamespacedKey keyId;

    public ItemManager(LegendaryForge plugin) {
        this.plugin = plugin;
        this.keyId = new NamespacedKey(plugin, "legendary_id");
    }

    public void load() {
        items.clear();
        File f = new File(plugin.getDataFolder(), "items.yml");
        if (!f.exists()) plugin.saveResource("items.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (String id : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(id);
            if (s == null) continue;
            try {
                items.put(id.toLowerCase(), new LegendaryItem(id.toLowerCase(), s));
            } catch (Exception e) {
                plugin.getLogger().warning("Gagal memuat item '" + id + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Memuat " + items.size() + " legendary item.");
    }

    public Collection<LegendaryItem> all() { return items.values(); }
    public LegendaryItem get(String id) { return id == null ? null : items.get(id.toLowerCase()); }
    public Set<String> ids() { return items.keySet(); }

    /** Item legendaris dari ItemStack, atau null. */
    public LegendaryItem fromStack(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return null;
        String id = stack.getItemMeta().getPersistentDataContainer().get(keyId, PersistentDataType.STRING);
        return get(id);
    }

    public ItemStack build(LegendaryItem li) {
        ItemStack stack = new ItemStack(li.material);
        ItemMeta meta = stack.getItemMeta();
        ConfigurationSection c = li.cfg;

        meta.displayName(Text.c(li.displayName));
        meta.setUnbreakable(c.getBoolean("unbreakable", true));
        if (li.modelData > 0) meta.setCustomModelData(li.modelData);   // dipakai resource pack & Geyser
        meta.getPersistentDataContainer().set(keyId, PersistentDataType.STRING, li.id);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);

        ConfigurationSection en = c.getConfigurationSection("enchants");
        if (en != null) for (String k : en.getKeys(false)) {
            Enchantment e = Text.enchant(k);
            if (e != null) meta.addEnchant(e, en.getInt(k), true);
        }

        // attack_damage di config = TOTAL damage yang diinginkan -> kurangi 1 (damage dasar tangan kosong).
        ConfigurationSection at = c.getConfigurationSection("attributes");
        if (at != null) for (String k : at.getKeys(false)) {
            Attribute attr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(k.toLowerCase()));
            if (attr == null) { plugin.getLogger().warning(li.id + ": attribute tidak dikenal '" + k + "'"); continue; }
            double v = at.getDouble(k);
            if (k.equalsIgnoreCase("attack_damage")) v -= 1.0;
            meta.addAttributeModifier(attr, new AttributeModifier(
                    new NamespacedKey(plugin, li.id + "_" + k.toLowerCase()),
                    v, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }

        meta.lore(buildLore(li));
        stack.setItemMeta(meta);
        return stack;
    }

    private List<Component> buildLore(LegendaryItem li) {
        ConfigurationSection c = li.cfg;
        List<Component> lore = new ArrayList<>();
        String tierColor = switch (li.tier) { case SUPREME -> "&6&l"; case MID -> "&b&l"; case BASE -> "&a&l"; };
        lore.add(Text.c(tierColor + "✦ " + li.tier + " ELEMENT ✦ &8| " + c.getString("element", "")));
        String nick = c.getString("nickname");
        if (nick != null) lore.add(Text.c(nick));
        lore.add(Text.c(""));
        for (String l : c.getStringList("lore")) lore.add(Text.c(l));
        lore.add(Text.c(""));
        if (!li.passiveId().equals("NONE")) {
            lore.add(Text.c("&e◆ Pasif: &f" + c.getString("passive.name", li.passiveId())));
            lore.add(Text.c("  &7" + c.getString("passive.description", "")));
        }
        if (!li.skillId().equals("NONE")) {
            String trig = switch (li.trigger()) {
                case RIGHT_CLICK -> "Klik Kanan";
                case SHIFT_RIGHT_CLICK -> "Shift + Klik Kanan";
                case LEFT_CLICK -> "Klik Kiri";
            };
            lore.add(Text.c("&d◆ Skill (" + trig + "): &f" + li.skillName()));
            lore.add(Text.c("  &7" + c.getString("skill.description", "")));
            lore.add(Text.c("  &8Cooldown: &7" + li.cooldown() + "s  &8Mana: &b" + (int) li.manaCost()));
        }
        lore.add(Text.c(""));
        li.levelRequirements().forEach((s, lv) -> lore.add(Text.c("&c✖ Butuh &f" + s + " &clevel &f" + lv)));
        if (!li.allowedRaces().isEmpty()) lore.add(Text.c("&c✖ Race: &f" + String.join(", ", li.allowedRaces())));
        return lore;
    }
}
