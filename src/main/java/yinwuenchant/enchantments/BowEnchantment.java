package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弓类附魔基类（EchoShot/StormArrow/ExplosiveArrow 共用）。
 *
 * Folia 安全：ProjectileLaunchEvent 在射手的玩家区域触发，此时读取弓并缓存
 * 「箭 UUID → 等级」；ProjectileHitEvent 在射弹区域触发，查缓存应用落点效果。
 * 命中即移除缓存；未命中的箭由定时任务按存活时间（箭头最久约 60s）清理。
 */
public abstract class BowEnchantment extends CustomEnchantment {

    private record Cached(int level, long time) {}

    private final Map<UUID, Cached> arrowLevels = new ConcurrentHashMap<>();
    private ScheduledTask cleanupTask;

    protected BowEnchantment(YinwuEnchantments plugin, String id, String displayName, int maxLevel, Material[] items) {
        super(plugin, id, displayName, maxLevel, items);
    }

    @Override
    public void onEnable() {
        cleanupTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            long now = System.currentTimeMillis();
            arrowLevels.entrySet().removeIf(e -> now - e.getValue().time() > 130_000L);
        }, 1L, 100L);
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(ProjectileLaunchEvent.class, event -> {
            ProjectileLaunchEvent e = (ProjectileLaunchEvent) event;
            if (!(e.getEntity() instanceof Arrow arrow)) return;
            if (!(arrow.getShooter() instanceof Player player)) return;

            ItemStack bow = player.getInventory().getItemInMainHand();
            if (!isRangedWeapon(bow)) bow = player.getInventory().getItemInOffHand();
            if (!isRangedWeapon(bow)) return;
            if (!hasEnchantment(bow)) return;

            arrowLevels.put(arrow.getUniqueId(),
                new Cached(getEnchantmentLevel(bow), System.currentTimeMillis()));
        });

        plugin.getEnchantmentManager().subscribeEvent(ProjectileHitEvent.class, event -> {
            ProjectileHitEvent e = (ProjectileHitEvent) event;
            Cached cached = arrowLevels.remove(e.getEntity().getUniqueId());
            if (cached == null || cached.level() <= 0) return;
            onArrowHit(e, cached.level());
        });
    }

    @Override
    public void onDisable() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) cleanupTask.cancel();
        arrowLevels.clear();
    }

    /** 是否为弓/弩（本附魔适用武器） */
    private static boolean isRangedWeapon(ItemStack item) {
        return item != null && (item.getType() == Material.BOW || item.getType() == Material.CROSSBOW);
    }

    /** 箭命中（射弹区域线程），level 为弓上本附魔等级 */
    protected abstract void onArrowHit(ProjectileHitEvent event, int level);

    @Override
    public Component displayName(int level) {
        return Component.text(getDisplayName() + " " + getRomanNumeral(level));
    }
}
