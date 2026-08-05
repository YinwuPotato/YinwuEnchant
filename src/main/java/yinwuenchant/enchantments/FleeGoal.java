package yinwuenchant.enchantments;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用怪物恐吓工具，对标原版 FleeEntityGoal。
 * 玩家主动检测附近怪物 → 切换怪物区域 → 设置逃离路径。
 * 所有调度在各自的区域线程执行。
 * 对主动攻击型怪物（骷髅等）持续刷新逃跑路径，防止被其 AI 覆盖。
 */
public class FleeGoal {

    private final Set<UUID> fleeing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ScheduledTask> fleeingTasks = new ConcurrentHashMap<>();

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
        for (var entity : playerLoc.getWorld().getNearbyEntities(
                playerLoc, range, range, range,
                e -> mobType.isInstance(e) && e.isValid())) {
            @SuppressWarnings("unchecked")
            T mob = (T) entity;
            // 已在逃跑中的怪物跳过（其 fleeOnce 递归会持续维护逃跑状态）
            if (!fleeing.add(mob.getUniqueId())) continue;
            fleeOnce(mob, playerLoc, player, plugin, mobType, range, fleeSpeed, fleeTicks, sound, particle);
        }
    }

    /** 执行一次逃跑，并在 fleeTicks 后若玩家仍在范围内则继续逃跑 */
    private <T extends Mob> void fleeOnce(T mob, Location fleeFrom, Player player, Plugin plugin,
                                          Class<T> mobType, double range, double fleeSpeed,
                                          int fleeTicks, Sound sound, Particle particle) {
        mob.getScheduler().run(plugin, task -> {
            if (!mob.isValid()) { cleanup(mob); return; }
            mob.setTarget(null);

            // 取消苦力怕引线，防止引线状态下无视逃跑直接爆炸（0 = 不点燃）
            if (mob instanceof Creeper creeper) {
                creeper.setFuseTicks(0);
            }

            if (sound != null) {
                mob.getWorld().playSound(mob.getLocation(), sound, 0.8f, 1.0f);
            }
            if (particle != null) {
                mob.getWorld().spawnParticle(particle,
                    mob.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0.05);
            }

            // 持续刷新逃跑路径（主动攻击型怪物 AI 会覆盖 moveTo，需不断拉回）
            startFleeMove(mob, plugin, fleeFrom, fleeSpeed);

            // fleeTicks 后重新检测：玩家仍在范围内则继续逃，否则结束
            mob.getScheduler().runDelayed(plugin, t -> {
                if (!mob.isValid()) { cleanup(mob); return; }
                Location mobLoc = mob.getLocation().clone(); // mob 线程读取快照
                // 切到玩家线程读取玩家位置并判断距离（Folia 跨区域安全）
                player.getScheduler().run(plugin, t2 -> {
                    if (!player.isOnline()) { cleanup(mob); return; }
                    Location pLoc = player.getLocation();
                    if (pLoc.distanceSquared(mobLoc) <= range * range) {
                        fleeOnce(mob, pLoc, player, plugin, mobType,
                            range, fleeSpeed, fleeTicks, sound, particle);
                    } else {
                        cleanup(mob);
                    }
                }, null);
            }, null, fleeTicks);
        }, null);
    }

    /** 持续刷新怪物的逃跑移动，直到逃跑结束 */
    private void startFleeMove(Mob mob, Plugin plugin, Location fleeFrom, double fleeSpeed) {
        ScheduledTask old = fleeingTasks.remove(mob.getUniqueId());
        if (old != null && !old.isCancelled()) old.cancel();

        ScheduledTask task = mob.getScheduler().runAtFixedRate(plugin, t -> {
            if (!mob.isValid()) { t.cancel(); fleeingTasks.remove(mob.getUniqueId()); return; }
            mob.setTarget(null);
            Vector dir = mob.getLocation().toVector().subtract(fleeFrom.toVector());
            if (dir.lengthSquared() < 0.01) return;
            dir = dir.normalize();
            if (mob instanceof Phantom phantom) {
                // 幻翼是飞行怪物，moveTo 路径寻路无效，直接给飞行速度驱赶
                phantom.setVelocity(dir.multiply(fleeSpeed * 1.5));
            } else {
                Location away = mob.getLocation().add(dir.multiply(8));
                mob.getPathfinder().moveTo(away, fleeSpeed);
            }
        }, null, 1L, 5L);
        fleeingTasks.put(mob.getUniqueId(), task);
    }

    private void cleanup(Mob mob) {
        ScheduledTask task = fleeingTasks.remove(mob.getUniqueId());
        if (task != null && !task.isCancelled()) task.cancel();
        fleeing.remove(mob.getUniqueId());
    }

    public void clear() {
        for (ScheduledTask task : fleeingTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        fleeingTasks.clear();
        fleeing.clear();
    }

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
