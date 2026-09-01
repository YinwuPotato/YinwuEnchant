package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 生命汲取 —— 攻击命中给自己再生。
 * 再生等级固定 II（amplifier 1），时长 2.5 + 3.5×(级-1) 秒，随级增长。
 * 移植自 NeoEnchant life_steal。
 */
public class LifeSteal extends CustomEnchantment {

    public LifeSteal(YinwuEnchantments plugin) {
        super(plugin, "life_steal", "生命汲取", 3, new Material[] {
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
        });
    }

    @Override
    public Component displayName(int level) {
        return Component.text("生命汲取 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(EntityDamageByEntityEvent.class, event -> {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.isCancelled()) return;
            if (!(e.getDamager() instanceof Player player)) return;
            if (!(e.getEntity() instanceof LivingEntity)) return;

            int level = plugin.getEnchantCache().getLevel(player.getUniqueId(), "life_steal");
            if (level <= 0) return;

            // 目标区域线程不能给攻击者上药水 → 调度回玩家区域
            int ticks = (int) Math.round((2.5 + 3.5 * (level - 1)) * 20);
            player.getScheduler().run(plugin, task -> {
                if (player.isOnline() && !player.isDead()) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, ticks, 1));
                }
            }, null);
        });
    }
}
