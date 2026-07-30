package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class EmeraldTill extends CustomEnchantment {
    private final ConfigManager configManager;

    public EmeraldTill(YinwuEnchantments plugin) {
        super(plugin, "emerald_till", "拾翠", 1, new Material[] {
            Material.WOODEN_HOE, Material.STONE_HOE,
            Material.IRON_HOE, Material.GOLDEN_HOE,
            Material.DIAMOND_HOE, Material.NETHERITE_HOE
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("拾翠");
    }

    @Override
    public void onEnable() {
        // 事件驱动，无需额外初始化
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            BlockDropItemEvent.class,
            event -> {
                BlockDropItemEvent e = (BlockDropItemEvent) event;
                handleDrop(e);
            }
        );
    }

    @Override
    public void onDisable() {}

    private void handleDrop(BlockDropItemEvent event) {
        if (event.isCancelled()) return;
        if (!configManager.isEnchantmentEnabled("emerald_till")) return;
        if (!isTargetPlant(event.getBlock().getType())) return;

        Player player = event.getPlayer();

        player.getScheduler().run(plugin, task -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item == null || item.getType() == Material.AIR) return;
            if (!isHoe(item.getType())) return;
            if (!hasEnchantment(item)) return;

            int fortuneLevel = item.getEnchantmentLevel(Enchantment.FORTUNE);
            var rand = ThreadLocalRandom.current();

            var cfg = plugin.getConfig();
            double baseChance = cfg.getDouble("enchantments.emerald_till.emerald-drop-chance", 0.05);
            double fortuneBonus = cfg.getDouble("enchantments.emerald_till.fortune-chance-bonus", 0.03) * fortuneLevel;
            int fortuneAmountBonus = cfg.getInt("enchantments.emerald_till.fortune-amount-bonus", 1);

            if (rand.nextDouble() < baseChance + fortuneBonus) {
                final int amount = 1 + (fortuneLevel > 0 && fortuneAmountBonus > 0
                    ? rand.nextInt(fortuneLevel * fortuneAmountBonus + 1) : 0);

                // 切换回方块区域线程生成掉落物
                plugin.getServer().getRegionScheduler().run(plugin, event.getBlock().getLocation(), t2 -> {
                    event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation().add(0.5, 0.5, 0.5),
                        new ItemStack(Material.EMERALD, amount));
                });

                // 在当前玩家区域线程消耗耐久
                ItemMeta meta = item.getItemMeta();
                if (meta instanceof Damageable dmg) {
                    int maxDurability = item.getType().getMaxDurability();
                    if (dmg.getDamage() < maxDurability - 1) {
                        dmg.setDamage(dmg.getDamage() + 1);
                        item.setItemMeta((ItemMeta) dmg);
                    }
                }
            }
        }, null);
    }

    private boolean isTargetPlant(Material type) {
        return type == Material.SHORT_GRASS
            || type == Material.TALL_GRASS
            || type == Material.DEAD_BUSH
            || type == Material.FERN
            || type == Material.LARGE_FERN;
    }

    private boolean isHoe(Material type) {
        return type.name().endsWith("_HOE");
    }
}
