package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.event.entity.ProjectileHitEvent;

/**
 * 爆炸之箭 —— 箭命中产生爆炸，特效为 TNT 白闪爆 + 橙火爆发 + 火柱/灰烟混合 + 白色冲击波环。
 * 威力 2.0 + 0.5×(级-1)；break-blocks / set-fire 默认关闭防炸毁建筑（config 可配）。
 * 视觉特效随等级 1→4 逐渐增大。移植自 NeoEnchant explosive_arrow。
 */
public class ExplosiveArrow extends BowEnchantment {

    public ExplosiveArrow(YinwuEnchantments plugin) {
        super(plugin, "explosive_arrow", "爆炸之箭", 4, new Material[] {
            Material.BOW, Material.CROSSBOW
        });
    }

    @Override
    protected void onArrowHit(ProjectileHitEvent event, int level) {
        Location loc = event.getEntity().getLocation();
        double basePower = plugin.getConfigManager().getDouble("explosive_arrow.power");
        float power = (float) (basePower + 0.5 * (level - 1));
        boolean setFire = plugin.getConfigManager().getBoolean("explosive_arrow.set-fire");
        boolean breakBlocks = plugin.getConfigManager().getBoolean("explosive_arrow.break-blocks");

        loc.getWorld().createExplosion(loc, power, setFire, breakBlocks);
        playExplosionEffect(loc, level);
    }

    /**
     * 爆炸视觉特效：爆闪 + 上升火柱 + 环形冲击波扩散 + 浓烟收尾。
     * 物理爆炸音效由 createExplosion 自带，这里补一声清脆爆鸣。
     * Folia：命中在射弹区域线程，spawnParticle 安全（同 SonicBoom 先例）；
     * 动画用 RegionScheduler 固定速率调度在落点区域线程。
     */
    private void playExplosionEffect(Location loc, int level) {
        World world = loc.getWorld();
        if (world == null) return;

        double scale = 1.0 + (level - 1) * 0.25;       // 等级 1→4：1.0→1.75
        int burst = 16 + level * 4;                    // 火焰爆闪 20→32
        double ringMax = 1.6 + 1.0 * level;            // 冲击波最大半径 2.6→5.6
        int ticks = 7;                                 // 火柱/冲击波动画帧数

        // ===== TNT 风格白闪爆（混合） =====
        world.spawnParticle(Particle.EXPLOSION, loc, 3, 0, 0, 0, 0);
        world.spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.2, 0), burst,
            0.9 * scale, 0.7 * scale, 0.9 * scale, 0.05);
        // ===== 原橙火爆闪（保留） =====
        world.spawnParticle(Particle.FLAME, loc.clone().add(0, 0.2, 0), burst / 2,
            0.9 * scale, 0.7 * scale, 0.9 * scale, 0.12);
        world.spawnParticle(Particle.LARGE_SMOKE, loc.clone().add(0, 0.1, 0),
            8 + level * 2, 0.7 * scale, 0.4 * scale, 0.7 * scale, 0.02);
        world.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.1f);

        double baseX = loc.getX(), baseY = loc.getY(), baseZ = loc.getZ();
        int chunkX = loc.getBlockX() >> 4, chunkZ = loc.getBlockZ() >> 4;
        int[] tick = {0};
        plugin.getServer().getRegionScheduler().runAtFixedRate(plugin, world,
            chunkX, chunkZ, task -> {
                int t = tick[0]++;
                if (t >= ticks) {
                    // 浓烟收尾（TNT 灰烟更浓）
                    world.spawnParticle(Particle.LARGE_SMOKE, baseX, baseY + 0.5, baseZ,
                        16 + level * 3, 1.1 * scale, 0.4, 1.1 * scale, 0.05);
                    task.cancel();
                    return;
                }
                // 火柱 + 灰烟混合上升
                double y = baseY + 0.4 + t * 0.55;
                world.spawnParticle(Particle.FLAME, baseX, y, baseZ, 4, 0.35, 0.2, 0.35, 0.08);
                world.spawnParticle(Particle.LARGE_SMOKE, baseX, y, baseZ, 3, 0.35, 0.2, 0.35, 0.05);
                world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, baseX, y + 0.3, baseZ,
                    2, 0.2, 0.1, 0.2, 0.02);
                // 冲击波环：白色烟环（TNT 风格）逐帧扩大，微向上飘
                double r = ringMax * (t + 1.0) / ticks;
                int count = Math.max(10, (int) (r * 7));
                double ringY = baseY + 0.3;
                for (int i = 0; i < count; i++) {
                    double angle = 2 * Math.PI * i / count;
                    world.spawnParticle(Particle.CLOUD,
                        baseX + r * Math.cos(angle), ringY, baseZ + r * Math.sin(angle),
                        1, 0, 0.08, 0, 0.02);
                }
            }, 1L, 1L);
    }
}
