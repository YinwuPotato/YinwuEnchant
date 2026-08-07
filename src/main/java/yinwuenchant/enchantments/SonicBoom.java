package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SonicBoom extends CustomEnchantment {
    private int chargeTicks = 60;

    private final Map<UUID, Integer> chargeTick = new ConcurrentHashMap<>();
    private final Map<UUID, ScheduledTask> chargeTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private Set<String> immuneMobs = Collections.singleton("VILLAGER");

    private double damage;
    private double range;

    public SonicBoom(YinwuEnchantments plugin) {
        super(plugin, "sonic_boom", "音波爆裂", 1, new Material[] {
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        });
    }

    @Override
    public Component displayName(int level) {
        return Component.text("音波爆裂 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {
        var cfg = plugin.getConfig().getConfigurationSection("enchantments.sonic_boom");
        if (cfg == null) return;
        List<String> mobs = cfg.getStringList("immune-mobs");
        if (!mobs.isEmpty()) {
            Set<String> set = new HashSet<>();
            for (String mob : mobs) set.add(mob.toUpperCase());
            immuneMobs = set;
        }
        damage = cfg.getDouble("damage", 8);
        range = cfg.getDouble("range", 24);
        chargeTicks = cfg.getInt("charge-ticks", 60);
    }

    @Override
    public void onDisable() {
        for (ScheduledTask task : chargeTasks.values()) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        chargeTasks.clear();
        chargeTick.clear();
        cooldowns.clear();
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(PlayerToggleSneakEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!hasEnchantment(player.getInventory().getChestplate())) return;

            if (event.isSneaking()) {
                startCharging(player);
            } else {
                stopCharging(player);
            }
        });

        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerQuitEvent.class,
            event -> {
                Player player = event.getPlayer();
                UUID uuid = player.getUniqueId();
                ScheduledTask task = chargeTasks.remove(uuid);
                if (task != null && !task.isCancelled()) task.cancel();
                chargeTick.remove(uuid);
                cooldowns.remove(uuid);
            }
        );
    }

    private void startCharging(Player player) {
        UUID uuid = player.getUniqueId();

        // 冷却检查
        Long cd = cooldowns.get(uuid);
        if (cd != null && System.currentTimeMillis() < cd) {
            long left = (cd - System.currentTimeMillis()) / 1000;
            player.sendActionBar(Component.text("§c音波爆裂冷却中… " + left + "s"));
            return;
        }

        chargeTick.put(uuid, 0);

        // 每 tick 更新蓄力进度
        ScheduledTask task = player.getScheduler().runAtFixedRate(plugin, (t) -> {
            int tick = chargeTick.getOrDefault(uuid, 0);
            if (tick >= chargeTicks) {
                // 蓄满后提示但不取消任务，等玩家站起来
                player.sendActionBar(Component.text("§d■ 音波爆裂 §7┃ §a████████████████████ §7100% §6✓ 可释放"));
                return;
            }
            tick++;
            chargeTick.put(uuid, tick);
            player.sendActionBar(buildBar(tick));
        }, null, 1L, 1L);

        chargeTasks.put(uuid, task);
    }

    private void stopCharging(Player player) {
        UUID uuid = player.getUniqueId();
        ScheduledTask task = chargeTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();

        Integer tick = chargeTick.remove(uuid);
        if (tick == null) return;

        // 未蓄满 → 无声取消
        if (tick < chargeTicks) return;

        // 蓄满 → 发射
        int cd = plugin.getConfig().getInt("enchantments.sonic_boom.cooldown", 10);
        cooldowns.put(uuid, System.currentTimeMillis() + cd * 1000L);
        player.sendActionBar(Component.empty());

        fireSonicBoom(player);
    }

    private void fireSonicBoom(Player player) {
        Location start = player.getEyeLocation();
        var dir = start.getDirection().normalize();

        player.getWorld().playSound(start, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);

        // 粒子轨迹
        for (double d = 0; d < range; d += 0.5) {
            Location point = start.clone().add(dir.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
        }

        // 伤害判定：以玩家视线为轴、半径 1.2、长度 range 的圆柱体（原版监守者音波为圆柱判定，范围更大）
        CustomEnchantment resonate = plugin.getEnchantmentManager().getEnchantment("resonate");
        Set<UUID> damaged = new HashSet<>();
        double radius = 1.2;
        // Folia 注意：getNearbyEntities 仅在当前区域线程内生效，跨区域实体不会被命中（范围受限，不崩溃）
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(start, range, 3.0, range);
        for (Entity entity : nearby) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            if (!damaged.add(entity.getUniqueId())) continue;
            if (immuneMobs.contains(entity.getType().name())) continue;

            // 圆柱体判定：实体到射线轴线的垂直距离 ≤ 半径，且在射程内
            Location entLoc = entity.getLocation().clone().subtract(start.toVector());
            double along = entLoc.getX() * dir.getX() + entLoc.getY() * dir.getY() + entLoc.getZ() * dir.getZ();
            if (along < 1 || along > range) continue;
            Vector perpendicular = entLoc.toVector().subtract(dir.clone().multiply(along));
            if (perpendicular.length() > radius) continue;

            // 在目标实体线程检查共振盾牌反弹
            entity.getScheduler().run(plugin, t -> {
                boolean reflected = false;
                if (living instanceof Player victim && victim.isBlocking()) {
                    ItemStack shield = victim.getActiveItem();
                    if (shield != null && shield.getType() == Material.SHIELD
                            && resonate != null && resonate.hasEnchantment(shield)) {
                        reflected = true;
                        // 反弹回攻击者
                        player.getScheduler().run(plugin, t2 -> {
                            player.damage(damage, DamageSource.builder(DamageType.SONIC_BOOM).build());
                            Vector kb = dir.clone().setY(0).normalize().multiply(-7);
                            kb.setY(0.5);
                            player.setVelocity(kb);
                            player.getWorld().playSound(player.getLocation(),
                                Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.5f);
                        }, null);
                    }
                }
                if (!reflected) {
                    living.damage(damage, DamageSource.builder(DamageType.SONIC_BOOM).build());
                    Vector kb = dir.clone().setY(0).normalize().multiply(7);
                    kb.setY(0.5);
                    living.setVelocity(kb);
                }
            }, null);
        }
    }

    private Component buildBar(int tick) {
        int pct = tick * 100 / chargeTicks;
        int filled = tick * 20 / chargeTicks;
        int empty = 20 - filled;
        var color = pct < 50 ? "§a" : pct < 80 ? "§e" : "§c";
        return Component.text(String.format("§d■ 音波爆裂 §7┃ %s%s§7%s §f%d%%",
            color, "█".repeat(filled), "░".repeat(empty), pct));
    }
}
