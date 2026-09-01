package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 回声射击 —— 箭命中产生音爆 AOE。
 * 半径 0.5+0.5×级，伤害 6+2×(级-1)（音波伤害），对落点周围除射手外的实体造成伤害。
 * 范围伤害沿用 SonicBoom 的 Folia 模式：getNearbyEntities + 目标实体调度器。
 * 移植自 NeoEnchant echo_shot。
 */
public class EchoShot extends BowEnchantment {

    public EchoShot(YinwuEnchantments plugin) {
        super(plugin, "echo_shot", "回声射击", 2, new Material[] {
            Material.BOW, Material.CROSSBOW
        });
    }

    @Override
    protected void onArrowHit(ProjectileHitEvent event, int level) {
        Location loc = event.getEntity().getLocation();
        if (!(event.getEntity().getShooter() instanceof Player shooter)) return;

        double radius = 1.0 + 1.0 * level; // 2026-08-31 用户要求：范围比原版放大 1 倍
        double damage = plugin.getConfigManager().getDouble("echo_shot.damage")
            + 2 * (level - 1);

        loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 5, 0.5, 0.5, 0.5, 0);

        // 落点 AOE 音波伤害（Folia：目标实体调度器）
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(shooter)) continue;
            entity.getScheduler().run(plugin, t -> {
                if (living.isValid() && !living.isDead()) {
                    living.damage(damage, DamageSource.builder(DamageType.SONIC_BOOM).build());
                }
            }, null);
        }
    }
}
