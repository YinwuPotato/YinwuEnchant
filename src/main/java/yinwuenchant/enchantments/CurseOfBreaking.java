package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 脆弱诅咒 —— 耐久损耗加快。
 * 每级 15% 概率额外 +1 耐久损耗。PlayerItemDamageEvent 在玩家上下文触发，
 * 直接读物品 PDC 即可（无需跨区域缓存）。
 * 移植自 NeoEnchant curse_of_breaking。
 */
public class CurseOfBreaking extends CustomEnchantment {

    private static final Material[] DURABILITY_ITEMS = {
        Material.WOODEN_SWORD, Material.STONE_SWORD,
        Material.IRON_SWORD, Material.GOLDEN_SWORD,
        Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
        Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
        Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
        Material.WOODEN_AXE, Material.STONE_AXE,
        Material.IRON_AXE, Material.GOLDEN_AXE,
        Material.DIAMOND_AXE, Material.NETHERITE_AXE,
        Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
        Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
        Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
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

    public CurseOfBreaking(YinwuEnchantments plugin) {
        super(plugin, "curse_of_breaking", "脆弱诅咒", 5, DURABILITY_ITEMS);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("脆弱诅咒 " + getRomanNumeral(level));
    }

    @Override
    public boolean isCursed() {
        return true;
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void registerEventSubscribers() {
        // 手持工具/武器：耐久损耗时每级 15% 概率额外 +1
        plugin.getEnchantmentManager().subscribeEvent(PlayerItemDamageEvent.class, event -> {
            PlayerItemDamageEvent e = (PlayerItemDamageEvent) event;
            if (e.isCancelled()) return;
            int level = getEnchantmentLevel(e.getItem());
            if (level <= 0) return;
            if (ThreadLocalRandom.current().nextDouble() < 0.15 * level) {
                e.setDamage(e.getDamage() + 1);
            }
        });

        // 盔甲：受击时额外磨损（EntityDamageEvent 在玩家区域，getArmorContents 返回副本需写回）
        plugin.getEnchantmentManager().subscribeEvent(EntityDamageEvent.class, event -> {
            EntityDamageEvent e = (EntityDamageEvent) event;
            if (e.isCancelled()) return;
            if (!(e.getEntity() instanceof Player player)) return;
            if (player.isDead()) return;

            ItemStack[] armor = player.getInventory().getArmorContents();
            boolean changed = false;
            for (ItemStack piece : armor) {
                if (piece == null) continue;
                int level = getEnchantmentLevel(piece);
                if (level <= 0) continue;
                if (ThreadLocalRandom.current().nextDouble() < 0.15 * level) {
                    if (piece.getItemMeta() instanceof Damageable dmg) {
                        dmg.setDamage(Math.min(dmg.getDamage() + 1, piece.getType().getMaxDurability()));
                        piece.setItemMeta(dmg);
                        changed = true;
                    }
                }
            }
            if (changed) player.getInventory().setArmorContents(armor);
        });
    }
}
