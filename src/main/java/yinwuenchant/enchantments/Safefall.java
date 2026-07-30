package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageEvent;

public class Safefall extends CustomEnchantment {
    private final ConfigManager configManager;

    public Safefall(YinwuEnchantments plugin) {
        super(plugin, "safefall", "外骨骼", 3, new Material[] {
            Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS,
            Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS,
            Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
        });
        this.configManager = plugin.getConfigManager();
    }

        @Override
    public Component displayName(int level) {
        return Component.text("外骨骼 " + getRomanNumeral(level));
    }



    @Override
    public void onEnable() {
        // 不需要特殊处理
    }

    /**
     * ✅ 注册事件订阅者（支持 reload）
     */
    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.entity.EntityDamageEvent.class,
            event -> {
                org.bukkit.event.entity.EntityDamageEvent damageEvent = (org.bukkit.event.entity.EntityDamageEvent) event;
                if (damageEvent.getEntity() instanceof org.bukkit.entity.Player) {
                    onPlayerDamage((org.bukkit.entity.Player) damageEvent.getEntity(), damageEvent);
                }
            }
        );
    }

    @Override
    public void onDisable() {
        // 不需要特殊处理
    }

    @Override
    public void onPlayerDamage(org.bukkit.entity.Player player, org.bukkit.event.entity.EntityDamageEvent event) {
        if (!configManager.isEnchantmentEnabled("safefall")) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (hasEnchantment(player.getInventory().getLeggings())) {
                int level = getEnchantmentLevel(player.getInventory().getLeggings());
                if (level > 0) {
                    double damageReductionPerLevel = configManager.getDouble("safefall.damage-reduction-per-level");
                    double reduction = damageReductionPerLevel * level;

                    // 计算减少后的伤害
                    double newDamage = event.getDamage() * (1.0 - reduction);
                    event.setDamage(Math.max(0, newDamage));

                    // 增加安全掉落高度
                    int safeFallHeightPerLevel = configManager.getInt("safefall.safe-fall-height-per-level");
                    int safeFallHeight = safeFallHeightPerLevel * level;

                    // 如果掉落高度小于安全高度，完全免疫伤害
                    if (event.getDamage() > 0 && player.getFallDistance() <= safeFallHeight) {
                        event.setCancelled(true);
                        player.setFallDistance(0);
                    }
                }
            }
        }
    }
}
