package yinwuenchant.enchantments;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;

/**
 * 玩家实体 AttributeModifier 增删工具（Fury/Dwarfed/Oversize 共用）。
 * 用固定 NamespacedKey 保证可幂等移除，EquipmentSlotGroup.ANY 表示作用于玩家整体。
 */
public final class AttributeSupport {

    private AttributeSupport() {}

    /** 添加修饰器（已存在则跳过，避免重复叠加） */
    public static void addModifier(Player player, Attribute attribute, NamespacedKey key, String name,
                                   double amount, AttributeModifier.Operation op) {
        if (player == null || player.isDead()) return;
        var attr = player.getAttribute(attribute);
        if (attr == null) return;
        if (attr.getModifier(key) != null) return;
        attr.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    /** 移除修饰器（不存在则忽略） */
    public static void removeModifier(Player player, Attribute attribute, NamespacedKey key) {
        if (player == null) return;
        var attr = player.getAttribute(attribute);
        if (attr == null) return;
        attr.removeModifier(key);
    }
}
