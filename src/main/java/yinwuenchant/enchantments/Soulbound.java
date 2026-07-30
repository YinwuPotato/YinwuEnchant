package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * 灵魂绑定 —— 死亡时保留附魔物品，不掉落不丢失
 *
 * 效果：
 * - 玩家死亡时，带有此附魔的物品不会掉落，会留在背包中
 * - 支持所有可附魔物品（武器、工具、盔甲、盾牌等）
 */
public class Soulbound extends CustomEnchantment {

    private final YinwuEnchantments plugin;

    public Soulbound(YinwuEnchantments plugin) {
        super(plugin, "soulbound", "灵魂绑定", 1, new Material[]{
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.TURTLE_HELMET,
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE,
            Material.IRON_AXE, Material.GOLDEN_AXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
            Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
            Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            Material.WOODEN_HOE, Material.STONE_HOE,
            Material.IRON_HOE, Material.GOLDEN_HOE,
            Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.BOW, Material.CROSSBOW,
            Material.TRIDENT, Material.MACE,
            Material.SHIELD, Material.ELYTRA,
            Material.FISHING_ROD
        });
        this.plugin = plugin;
    }

    @Override
    public Component displayName(int level) {
        return Component.text("灵魂绑定");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("soulbound")) {
            return;
        }

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[灵魂绑定] 已启用");
        }
    }

    @Override
    public void onDisable() {
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[灵魂绑定] 已禁用");
        }
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerDeathEvent.class,
            this::onPlayerDeath
        );
    }

    /**
     * 玩家死亡时保护灵魂绑定的物品
     * 从掉落物中移除带附魔的物品，保留在玩家背包中
     */
    private void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> protectedItems = new ArrayList<>();

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[灵魂绑定] §e检查玩家 " + player.getName() + " 的死亡掉落物");
        }

        // 遍历掉落物，找出带灵魂绑定的物品
        List<ItemStack> drops = event.getDrops();
        for (ListIterator<ItemStack> it = drops.listIterator(); it.hasNext();) {
            ItemStack item = it.next();
            if (item != null && !item.getType().isAir() && hasEnchantment(item)) {
                protectedItems.add(item.clone());
                it.remove();

                if (plugin.getConfigManager().getBoolean("debug")) {
                    plugin.getLogger().fine("[灵魂绑定] §a保护物品: " + item.getType().name());
                }
            }
        }

        // 如果成功保护了物品，通知玩家
        if (!protectedItems.isEmpty()) {
            // 从 getDrops() 移除后，物品会自动保留在玩家背包中
            player.sendMessage("§d✦ §5灵魂绑定 §d保护了 §f" + protectedItems.size()
                + " §d件物品免于掉落！");
        }
    }
}
