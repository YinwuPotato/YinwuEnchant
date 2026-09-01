package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 巨人化 —— 体型变大（负面诅咒）。
 * 体型 +25%~+95%、台阶高度 +0.5。个头大更容易被打中、更占空间。
 * 移植自 NeoEnchant oversize（curse tag）。
 */
public class Oversize extends AbstractLeggingsEnchant {

    private static final NamespacedKey SCALE_KEY = NamespacedKey.fromString("yinwuenchant:oversize_scale");
    private static final NamespacedKey STEP_KEY  = NamespacedKey.fromString("yinwuenchant:oversize_step");

    /** scale 增加比例查找表（1-4级） */
    private static final double[] SCALE_LOOKUP = {0.25, 0.35, 0.65, 0.95};

    public Oversize(YinwuEnchantments plugin) {
        super(plugin, "oversize", "巨人化", 4);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("巨人化 " + getRomanNumeral(level));
    }

    @Override
    public boolean isCursed() {
        return true;
    }

    @Override
    protected void applyModifiers(Player p, int level) {
        double scale = SCALE_LOOKUP[Math.min(level, SCALE_LOOKUP.length) - 1];

        AttributeSupport.addModifier(p, Attribute.SCALE, SCALE_KEY, "oversize_scale",
            scale, AttributeModifier.Operation.ADD_SCALAR); // add_multiplied_base
        AttributeSupport.addModifier(p, Attribute.STEP_HEIGHT, STEP_KEY, "oversize_step",
            0.5, AttributeModifier.Operation.ADD_NUMBER);
        // 跳跃高度随体型放大（Jump Boost，无粒子）
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, level - 1, true, false));
    }

    @Override
    protected void removeModifiers(Player p) {
        AttributeSupport.removeModifier(p, Attribute.SCALE, SCALE_KEY);
        AttributeSupport.removeModifier(p, Attribute.STEP_HEIGHT, STEP_KEY);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
