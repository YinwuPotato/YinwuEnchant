package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 风暴之箭 —— 箭命中召唤闪电。
 * 视觉闪电（strikeLightningEffect）+ 自控 AoE 伤害：
 * 不烧毁/破坏地形，也不会触发村民变女巫（防熊）。
 * 有冷却防无脑连射/多重射击刷雷。移植自 NeoEnchant storm_arrow。
 */
public class StormArrow extends BowEnchantment {

    /** 玩家 UUID → 上次触发时间（ms），冷却防连射 */
    private final Map<UUID, Long> lastStrike = new ConcurrentHashMap<>();

    public StormArrow(YinwuEnchantments plugin) {
        super(plugin, "storm_arrow", "风暴之箭", 1, new Material[] {
            Material.BOW, Material.CROSSBOW
        });
    }

    @Override
    protected void onArrowHit(ProjectileHitEvent event, int level) {
        // 冷却：连射/多重射击多支箭命中只触发第一支
        if (event.getEntity().getShooter() instanceof Player player) {
            int cooldown = plugin.getConfigManager().getInt("storm_arrow.cooldown");
            long now = System.currentTimeMillis();
            Long last = lastStrike.get(player.getUniqueId());
            if (last != null && now - last < cooldown * 1000L) return;
            lastStrike.put(player.getUniqueId(), now);
        }

        Location loc = event.getEntity().getLocation();
        World world = loc.getWorld();
        if (world == null) return;

        double radius = plugin.getConfigManager().getDouble("storm_arrow.radius");
        double damage = plugin.getConfigManager().getDouble("storm_arrow.damage");

        // 生效范围内撒多道视觉闪电，直观显示伤害范围（无方块破坏/引燃，也不会把村民变女巫）
        int boltCount = Math.max(1, plugin.getConfigManager().getInt("storm_arrow.bolt-count"));
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        world.strikeLightningEffect(loc);
        for (int i = 1; i < boltCount; i++) {
            double dx = rand.nextDouble(-radius, radius);
            double dz = rand.nextDouble(-radius, radius);
            if (dx * dx + dz * dz > radius * radius) continue;   // 保持圆内
            world.strikeLightningEffect(loc.clone().add(dx, 0, dz));
        }
        // 电火花粒子扩散，标记范围边界
        world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 24,
            radius * 0.6, radius * 0.6, radius * 0.6, 0.3);

        // 自控 AoE 伤害（伤害来源=闪电，数值可配）
        DamageSource src = DamageSource.builder(DamageType.LIGHTNING_BOLT).build();
        // Folia：getNearbyEntities 仅命中当前区域实体（半径小，同 SonicBoom 先例）
        for (Entity e : world.getNearbyEntities(loc, radius, radius, radius)) {
            if (e instanceof LivingEntity living && !living.equals(event.getEntity().getShooter())) {
                living.damage(damage, src);
            }
        }
    }

    @Override
    public void onDisable() {
        lastStrike.clear();
        super.onDisable();
    }
}
