package yinwuenchant.enchantments;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;

/**
 * 护甲减伤近似计算（Critical 破甲 / Fury 护甲穿透共用）。
 * 用简化公式 armor/(armor+8) 估算护甲减伤比例（上限 0.8），
 * 破甲伤害 = 当前伤害 × 破甲比例 × 目标护甲减伤比例。
 */
public final class ArmorPenetration {

    private ArmorPenetration() {}

    /** 目标护甲减伤比例（0~0.8）；无护甲属性返回 0 */
    public static double armorReductionFraction(LivingEntity target) {
        if (target == null) return 0;
        var attr = target.getAttribute(Attribute.ARMOR);
        if (attr == null) return 0;
        double armor = attr.getValue();
        if (armor <= 0) return 0;
        return Math.min(0.8, armor / (armor + 8));
    }

    /** 破甲带来的额外伤害 */
    public static double penetrationBonus(double damage, double penetrationFraction, LivingEntity target) {
        return damage * penetrationFraction * armorReductionFraction(target);
    }
}
