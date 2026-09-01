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
 * 附魔行格式：<颜色><附魔名> [等级]，多个附魔叠加时按注册顺序排在最上方。
 * 颜色可配：正常附魔默认原版附魔蓝（视觉仿原版附魔行），诅咒附魔默认红色（仿原版诅咒红字）。
 */
public final class EnchantmentLore {

    private static final String DEFAULT_COLOR = "§9";
    private static final String DEFAULT_CURSE_COLOR = "§c";

    /** 正常附魔行颜色（config lore-color） */
    private static String color = DEFAULT_COLOR;
    /** 诅咒附魔行颜色（config curse-lore-color） */
    private static String curseColor = DEFAULT_CURSE_COLOR;

    private EnchantmentLore() {}

    /** 由 ConfigManager 加载时设置（§x 或 &x 均可） */
    public static void setColors(String normal, String curse) {
        color = normalize(normal, DEFAULT_COLOR);
        curseColor = normalize(curse, DEFAULT_CURSE_COLOR);
    }

    private static String normalize(String s, String def) {
        if (s == null || s.isBlank()) return def;
        String t = s.trim();
        if (t.startsWith("&")) t = "§" + t.substring(1);
        return t;
    }

    /** 附魔效果说明行的前缀（兼作移除标记） */
    private static final String EFFECT_PREFIX = "§7· ";

    /** 从 ItemMeta 的 PDC 重建所有自定义附魔的 Lore 行（禁用附魔显示删除线+已禁用标记），附魔名下方带效果说明行 */
    public static void rebuild(ItemMeta meta, Collection<CustomEnchantment> all) {
        List<String> lore = meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> isEnchantLine(line, all) || isEffectLine(line));
        for (CustomEnchantment ench : all) {
            Integer level = meta.getPersistentDataContainer()
                .get(ench.getEnchantmentKey(), PersistentDataType.INTEGER);
            if (level != null && level > 0) {
                boolean disabled = meta.getPersistentDataContainer()
                    .has(ench.getDisabledKey(), PersistentDataType.BYTE);
                lore.add(0, formatLine(ench, level, disabled));
                if (!disabled) {
                    List<String> effects = effectLines(ench, level);
                    for (int i = effects.size() - 1; i >= 0; i--) {
                        lore.add(1, effects.get(i));
                    }
                }
            }
        }
        meta.setLore(lore);
    }

    /** 判断某行是否为自定义附魔的效果说明行 */
    private static boolean isEffectLine(String line) {
        return line != null && line.startsWith(EFFECT_PREFIX);
    }

    /** 附魔效果说明行（数值随等级计算，仿原版「在主手时」样式） */
    public static List<String> effectLines(CustomEnchantment ench, int level) {
        List<String> lines = new ArrayList<>();
        switch (ench.getId()) {
            case "curse_of_clumsiness" -> lines.add(EFFECT_PREFIX + "在主手时：减少 §9" + (1 + level) + "§7 点伤害");
            case "poison_aspect" -> lines.add(EFFECT_PREFIX + "攻击造成 §9" + (2 * level + 1) + "§7 秒中毒");
            case "critical" -> lines.add(EFFECT_PREFIX + "攻击有 §9" + (4 * level) + "%§7 概率暴击（破甲25%）");
            case "life_steal" -> lines.add(EFFECT_PREFIX + "攻击命中回复生命（再生 §9" + fmt(2.5 + 3.5 * (level - 1)) + "§7 秒）");
            case "fury" -> {
                lines.add(EFFECT_PREFIX + "穿戴时：攻击伤害 +§9" + 5 + "§7");
                lines.add(EFFECT_PREFIX + "穿戴时：护甲 -§9" + 3 + "§7");
            }
            case "vein_miner" -> lines.add(EFFECT_PREFIX + "非潜行挖矿时连锁挖矿脉");
            case "echo_shot" -> lines.add(EFFECT_PREFIX + "箭命中音爆（§9" + (6 + 2 * (level - 1)) + "§7 点伤害）");
            case "storm_arrow" -> lines.add(EFFECT_PREFIX + "箭命中召唤闪电");
            case "explosive_arrow" -> lines.add(EFFECT_PREFIX + "箭命中爆炸（威力§9" + fmt(2 + 0.5 * (level - 1)) + "§7）");
            case "curse_of_breaking" -> lines.add(EFFECT_PREFIX + "耐久损耗加快（§9" + (15 * level) + "%§7 概率+1）");
            case "curse_of_enchant" -> lines.add(EFFECT_PREFIX + "无法再附魔或修改");
            case "dwarfed" -> lines.add(EFFECT_PREFIX + "体型缩小、攻击伤害 -§9" + fmt(1 + 0.5 * (level - 1)) + "§7");
            case "oversize" -> lines.add(EFFECT_PREFIX + "体型变大、跳跃更高、台阶 +§9" + fmt(0.5) + "§7");
            // ==== 原版 21 个附魔说明行 ====
            case "clearsight" -> lines.add(EFFECT_PREFIX + "无视黑暗效果");
            case "darkspeed" -> lines.add(EFFECT_PREFIX + "黑暗区域移动速度 +§9" + (10 * level) + "%§7");
            case "resonate" -> lines.add(EFFECT_PREFIX + "反弹所格挡的攻击");
            case "safefall" -> lines.add(EFFECT_PREFIX + "减免摔落伤害 §9" + (30 * level) + "%§7");
            case "shrieker_sense" -> lines.add(EFFECT_PREFIX + "探测周围尖啸体/监守者（§9" + 48 + "§7 格）");
            case "sonic_boom" -> lines.add(EFFECT_PREFIX + "蹲下蓄力发射音波");
            case "undermine" -> lines.add(EFFECT_PREFIX + "海平面以下挖掘效率 +§9" + (20 * level) + "%§7");
            case "cats_paw" -> lines.add(EFFECT_PREFIX + "周期性恐吓苦力怕");
            case "nasus" -> lines.add(EFFECT_PREFIX + "周期性恐吓骷髅类怪物");
            case "master_of_beef_slicing" -> lines.add(EFFECT_PREFIX + "击杀生物额外掉落肉类");
            case "phantom" -> lines.add(EFFECT_PREFIX + "防止幻翼攻击");
            case "harvest" -> lines.add(EFFECT_PREFIX + "右键收获成熟作物（§9" + 2 + "§7 格）");
            case "emerald_till" -> lines.add(EFFECT_PREFIX + "破坏草丛概率掉落绿宝石");
            case "airbag" -> lines.add(EFFECT_PREFIX + "减少飞行撞墙伤害");
            case "bless" -> lines.add(EFFECT_PREFIX + "死亡后传送回重生点");
            case "vampire_curse" -> lines.add(EFFECT_PREFIX + "白天燃烧、夜晚回复生命");
            case "insomnia" -> lines.add(EFFECT_PREFIX + "夜晚无法入睡，积累未睡眠天数");
            case "soulbound" -> lines.add(EFFECT_PREFIX + "死亡时保留此物品");
            case "smelt" -> lines.add(EFFECT_PREFIX + "自动熔炼挖掘的方块");
            case "step_up" -> lines.add(EFFECT_PREFIX + "可走上 §9" + 1 + "§7 格高方块");
            case "lava_walker" -> lines.add(EFFECT_PREFIX + "熔岩变为可行走的岩浆块");
            default -> {}
        }
        return lines;
    }

    private static String fmt(double d) {
        if (d == Math.floor(d)) return String.valueOf((long) d);
        return String.valueOf(Math.round(d * 10) / 10.0);
    }

    private static String fmtPct(double d) {
        double pct = d * 100;
        if (pct == Math.floor(pct)) return String.valueOf((long) pct);
        return String.valueOf(Math.round(pct * 10) / 10.0);
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
        return (ench.isCursed() ? curseColor : color) + ench.getDisplayName() + levelSuffix;
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
