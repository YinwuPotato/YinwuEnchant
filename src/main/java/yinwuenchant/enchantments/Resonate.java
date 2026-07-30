package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Resonate extends CustomEnchantment {
    private final ConfigManager configManager;

    public Resonate(YinwuEnchantments plugin) {
        super(plugin, "resonate", "共振", 1, new Material[] {
            Material.SHIELD
        });
        this.configManager = plugin.getConfigManager();
    }

        @Override
    public Component displayName(int level) {
        return Component.text("共振 " + getRomanNumeral(level));
    }



    @Override
    public void onEnable() {
    }

    /**
     * ✅ 注册事件订阅者（支持 reload）
     */
    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.entity.EntityDamageByEntityEvent.class,
            event -> onEntityDamageByEntity((org.bukkit.event.entity.EntityDamageByEntityEvent) event)
        );
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!configManager.isEnchantmentEnabled("resonate")) return;

        Entity damaged = event.getEntity();
        Entity damager = event.getDamager();

        // 只处理玩家被攻击的情况
        if (!(damaged instanceof Player player)) return;

        // 检查是否在使用盾牌（通过检查主手或副手物品）
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();

        boolean hasShieldInHand = false;
        int level = 0;

        if (mainHand.getType() == Material.SHIELD && hasEnchantment(mainHand)) {
            hasShieldInHand = true;
            level = getEnchantmentLevel(mainHand);
        } else if (offHand.getType() == Material.SHIELD && hasEnchantment(offHand)) {
            hasShieldInHand = true;
            level = getEnchantmentLevel(offHand);
        }

        if (!hasShieldInHand || level == 0) return;

        // 检查玩家是否正在格挡（有伤害吸收效果）
        if (player.isBlocking()) {
            double damageReflectPerLevel = configManager.getDouble("resonate.damage-reflect-per-level");
            double reflectedDamage = event.getDamage() * damageReflectPerLevel * level;

            // 反弹伤害给攻击者
            if (damager instanceof LivingEntity livingDamager) {
                final double finalReflectedDamage = reflectedDamage;

                // ⚠️ EntityDamageByEntityEvent 在受击者（玩家）区域线程触发
                // 攻击者可能位于不同区域，必须在攻击者所在区域线程执行伤害操作
                livingDamager.getScheduler().run(plugin, task -> {
                    livingDamager.damage(finalReflectedDamage, player);
                }, null);

                // 播放音效（受击者区域，安全）
                String soundTypeStr = configManager.getString("resonate.sound-type");
                Sound sound;
                try {
                    // 使用 NamespacedKey 从 Registry 获取声音
                    org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString("minecraft:" + soundTypeStr.toLowerCase().replace("_", "-"));
                    sound = org.bukkit.Registry.SOUNDS.get(key);
                    if (sound == null) {
                        // 如果找不到，静默使用默认音效
                        sound = Sound.ENTITY_IRON_GOLEM_HURT;
                    }
                } catch (Exception e) {
                    // 出错时静默使用默认音效
                    sound = Sound.ENTITY_IRON_GOLEM_HURT;
                }

                player.getWorld().playSound(
                    player.getLocation(),
                    sound,
                    1.0f,
                    1.0f
                );

                // 减少原伤害（盾牌格挡效果）
                event.setDamage(event.getDamage() * 0.33); // 盾牌格挡减少67%伤害
            }
        }
    }
}
