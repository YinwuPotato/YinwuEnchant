package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 附魔诅咒 —— 物品无法再附魔/被铁砧修改。
 * 本类无独立事件订阅者；拦截逻辑在 EnchantmentAcquisitionManager：
 * - 附魔台 EnchantItemEvent：物品带本诅咒则整个取消（含原版附魔）
 * - 铁砧 PrepareAnvilEvent：目标物品带本诅咒则拒绝书贴物品/合并
 * 移植自 NeoEnchant curse_of_enchant。
 */
public class CurseOfEnchant extends CustomEnchantment {

    private static final Material[] DURABILITY_ITEMS = {
        // 剑
        Material.WOODEN_SWORD, Material.STONE_SWORD,
        Material.IRON_SWORD, Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        // 镐
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
        Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
        Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
        // 斧
        Material.WOODEN_AXE, Material.STONE_AXE,
        Material.IRON_AXE, Material.GOLDEN_AXE,
        Material.DIAMOND_AXE, Material.NETHERITE_AXE,
        // 锹
        Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
        Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
        Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
        // 锄
        Material.WOODEN_HOE, Material.STONE_HOE,
        Material.IRON_HOE, Material.GOLDEN_HOE,
        Material.DIAMOND_HOE, Material.NETHERITE_HOE,
        // 盔甲
        Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
        Material.IRON_HELMET, Material.GOLDEN_HELMET,
        Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
        Material.TURTLE_HELMET,
        Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
        Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
        Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS,
        Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS,
        Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
        Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
        Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
        Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        // 其他耐久物品
        Material.SHIELD, Material.ELYTRA, Material.BOW, Material.CROSSBOW,
        Material.FISHING_ROD, Material.TRIDENT, Material.MACE,
        Material.FLINT_AND_STEEL, Material.SHEARS
    };

    public CurseOfEnchant(YinwuEnchantments plugin) {
        super(plugin, "curse_of_enchant", "附魔诅咒", 1, DURABILITY_ITEMS);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("附魔诅咒");
    }

    @Override
    public boolean isCursed() {
        return true;
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}
}
