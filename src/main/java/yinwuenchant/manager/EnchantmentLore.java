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

    /** 从 ItemMeta 的 PDC 重建所有自定义附魔的 Lore 行（禁用附魔显示删除线+已禁用标记） */
    public static void rebuild(ItemMeta meta, Collection<CustomEnchantment> all) {
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> isEnchantLine(line, all));
        for (CustomEnchantment ench : all) {
            Integer level = meta.getPersistentDataContainer()
                .get(ench.getEnchantmentKey(), PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                boolean disabled = meta.getPersistentDataContainer()
                    .has(ench.getDisabledKey(), PersistentDataType.BYTE);
                lore.add(0, formatLine(ench, level, disabled));
            }
        }
        meta.setLore(lore);
    }

    /** 生成附魔显示行 */
    public static String formatLine(CustomEnchantment ench, int level) {
        return formatLine(ench, level, false);
    }

    /** 生成附魔显示行（含禁用态：删除线 + 置灰 + 已禁用标记） */
    public static String formatLine(CustomEnchantment ench, int level, boolean disabled) {
        String levelSuffix = ench.getMaxLevel() > 1 ? " " + roman(level) : "";
        if (disabled) {
            return "§8§m" + ench.getDisplayName() + levelSuffix + "§r §7(已禁用)";
        }
        return COLOR + ench.getDisplayName() + levelSuffix;
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
