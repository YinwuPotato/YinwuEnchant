package yinwuenchant.manager;

import yinwuenchant.enchantments.CustomEnchantment;
import org.bukkit.ChatColor;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 自定义附魔 Lore 显示管理。
 * 附魔行格式：§7<附魔名> [等级]，多个附魔叠加时按注册顺序排在最上方。
 */
public final class EnchantmentLore {

    private static final String COLOR = "§7";

    private EnchantmentLore() {}

    /** 从 ItemMeta 的 PDC 重建所有自定义附魔的 Lore 行 */
    public static void rebuild(ItemMeta meta, Collection<CustomEnchantment> all) {
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> isEnchantLine(line, all));
        for (CustomEnchantment ench : all) {
            Integer level = meta.getPersistentDataContainer()
                .get(ench.getEnchantmentKey(), PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                lore.add(0, formatLine(ench, level));
            }
        }
        meta.setLore(lore);
    }

    /** 生成附魔显示行 */
    public static String formatLine(CustomEnchantment ench, int level) {
        return COLOR + ench.getDisplayName()
            + (ench.getMaxLevel() > 1 ? " " + roman(level) : "");
    }

    /** 判断某行是否为自定义附魔行 */
    private static boolean isEnchantLine(String line, Collection<CustomEnchantment> all) {
        String stripped = ChatColor.stripColor(line);
        if (stripped == null) return false;
        for (CustomEnchantment ench : all) {
            String name = ench.getDisplayName();
            if (stripped.equals(name)) return true;
            if (stripped.startsWith(name + " ")) return true;
        }
        return false;
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
