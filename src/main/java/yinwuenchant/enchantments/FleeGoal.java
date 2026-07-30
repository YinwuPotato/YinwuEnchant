package yinwuenchant.enchantments;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用怪物恐吓工具，对标原版 FleeEntityGoal。
 * 玩家主动检测附近怪物 → 切换怪物区域 → 设置逃离路径。
 * 所有调度在各自的区域线程执行。
 */
public class FleeGoal {

    private final Set<UUID> fleeing = ConcurrentHashMap.newKeySet();

    /**
     * 让玩家恐吓周围的指定类型怪物。
     *
     * @param player     玩家
     * @param plugin     插件实例
     * @param mobType    怪物 Class（Creeper.class / AbstractSkeleton.class / Phantom.class）
     * @param range      检测范围（格）
     * @param fleeSpeed  逃跑速度（推荐 1.5）
     * @param fleeTicks  逃跑持续时间（tick）
     * @param sound      恐吓音效（null=不播放）
     * @param particle   恐吓粒子（null=不生成）
     * @param <T>        怪物类型
     */
    public <T extends Mob> void fleeFrom(Player player, Plugin plugin,
                                          Class<T> mobType, double range, double fleeSpeed,
                                          int fleeTicks, Sound sound, Particle particle) {
        Location playerLoc = player.getLocation();
        // 在玩家区域查找附近怪物
        for (var entity : playerLoc.getWorld().getNearbyEntities(
                playerLoc, range, range, range,
                e -> mobType.isInstance(e) && e.isValid())) {
            @SuppressWarnings("unchecked")
            T mob = (T) entity;
            if (!fleeing.add(mob.getUniqueId())) continue;

            Location fleeFrom = playerLoc.clone();
            // 切换到怪物区域执行 AI
            mob.getScheduler().run(plugin, task -> {
                mob.setTarget(null);

                Vector dir = mob.getLocation().toVector().subtract(fleeFrom.toVector());
                if (dir.lengthSquared() < 0.01) return;

                Location away = mob.getLocation().add(dir.normalize().multiply(16));
                mob.getPathfinder().moveTo(away, fleeSpeed);

                if (sound != null) {
                    mob.getWorld().playSound(mob.getLocation(), sound, 0.8f, 1.0f);
                }
                if (particle != null) {
                    mob.getWorld().spawnParticle(particle,
                        mob.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.05);
                }

                mob.getScheduler().runDelayed(plugin, t -> fleeing.remove(mob.getUniqueId()), null, fleeTicks);
            }, null);
        }
    }

    public void clear() { fleeing.clear(); }

    /** 检查新生成的怪物附近是否有已激活的玩家（用于 CreatureSpawnEvent 回调） */
    public <T extends Mob> void checkOnSpawn(T mob, Plugin plugin, Set<UUID> activePlayers,
                                               Class<T> mobType, double range, double fleeSpeed,
                                               int fleeTicks, Sound sound, Particle particle) {
        if (activePlayers.isEmpty()) return;
        for (UUID uuid : activePlayers) {
            Player player = mob.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()
                && player.getLocation().distanceSquared(mob.getLocation()) <= range * range) {
                player.getScheduler().run(plugin, t -> {
                    fleeFrom(player, plugin, mobType, range, fleeSpeed, fleeTicks, sound, particle);
                }, null);
                break;
            }
        }
    }
}
