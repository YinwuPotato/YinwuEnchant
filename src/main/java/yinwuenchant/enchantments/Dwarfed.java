package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

/**
 * 矮人化 —— 体型缩小 + 削弱（负面诅咒）。
 * 体型 -15%~-50%、攻击伤害 -1~-3、攻击距离 -0.15~-0.55（随级）。
 * 移植自 NeoEnchant dwarfed（curse tag）。
 */
public class Dwarfed extends AbstractLeggingsEnchant {

    private static final NamespacedKey SCALE_KEY  = NamespacedKey.fromString("yinwuenchant:dwarfed_scale");
    private static final NamespacedKey ATTACK_KEY = NamespacedKey.fromString("yinwuenchant:dwarfed_attack");
    private static final NamespacedKey REACH_KEY  = NamespacedKey.fromString("yinwuenchant:dwarfed_reach");

    /** scale 减少比例查找表（1-4级），5级用 fallback -0.15 - 0.15×(级-1) */
    private static final double[] SCALE_LOOKUP = {0.15, 0.25, 0.35, 0.5};

    public Dwarfed(YinwuEnchantments plugin) {
        super(plugin, "dwarfed", "矮人化", 5);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("矮人化 " + getRomanNumeral(level));
    }

    @Override
    public boolean isCursed() {
        return true;
    }

    @Override
    protected void applyModifiers(Player p, int level) {
        double scale = scaleAmount(level);
        double attack = -(1 + 0.5 * (level - 1));
        double reach = -(0.15 + 0.1 * (level - 1));

        AttributeSupport.addModifier(p, Attribute.SCALE, SCALE_KEY, "dwarfed_scale",
            -scale, AttributeModifier.Operation.ADD_SCALAR); // add_multiplied_base
        AttributeSupport.addModifier(p, Attribute.ATTACK_DAMAGE, ATTACK_KEY, "dwarfed_attack",
            attack, AttributeModifier.Operation.ADD_NUMBER);
        AttributeSupport.addModifier(p, Attribute.ENTITY_INTERACTION_RANGE, REACH_KEY, "dwarfed_reach",
            reach, AttributeModifier.Operation.ADD_NUMBER);
    }

    @Override
    protected void removeModifiers(Player p) {
        AttributeSupport.removeModifier(p, Attribute.SCALE, SCALE_KEY);
        AttributeSupport.removeModifier(p, Attribute.ATTACK_DAMAGE, ATTACK_KEY);
        AttributeSupport.removeModifier(p, Attribute.ENTITY_INTERACTION_RANGE, REACH_KEY);
    }

    private static double scaleAmount(int level) {
        if (level >= 1 && level <= SCALE_LOOKUP.length) return SCALE_LOOKUP[level - 1];
        return 0.15 + 0.15 * (level - 1); // fallback（5 级 = 0.75）
    }
}
