package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 护佑 —— 触发不死图腾后传送回绑定点
 *
 * 效果：
 * - 玩家触发不死图腾时，延迟 1 秒后传送至绑定的床/重生锚/世界出生点
 * - 优先使用床位置 → 重生锚位置 → 世界出生点
 * - 传送时伴随粒子与音效
 */
public class Bless extends CustomEnchantment {

    private final YinwuEnchantments plugin;

    public Bless(YinwuEnchantments plugin) {
        super(plugin, "bless", "护佑", 1, new Material[]{
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE
        });
        this.plugin = plugin;
    }

    @Override
    public Component displayName(int level) {
        return Component.text("护佑");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("bless")) {
            return;
        }
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[护佑] 已启用");
        }
    }

    @Override
    public void onDisable() {
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[护佑] 已禁用");
        }
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            EntityResurrectEvent.class,
            this::onResurrect
        );
    }

    private void onResurrect(EntityResurrectEvent event) {
        if (event.isCancelled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // 检查胸甲是否有护佑附魔
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType().isAir() || !hasEnchantment(chest)) return;

        // 延迟 20 tick（1秒）后传送，让不死图腾的动画和效果先播放
        player.getScheduler().runDelayed(plugin, (task) -> {
            if (!player.isOnline()) return;

            Location target = findSpawnLocation(player);
            if (target == null) {
                plugin.getLogger().warning("[护佑] 无法找到 " + player.getName() + " 的传送点");
                return;
            }

            player.teleportAsync(target).thenAccept(success -> {
                if (success) {
                    // 粒子与音效
                    player.getWorld().playSound(target, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.2f);
                    player.getWorld().spawnParticle(Particle.REVERSE_PORTAL,
                        target.getX(), target.getY() + 1, target.getZ(),
                        50, 0.5, 1.0, 0.5, 0.1);
                    player.getWorld().spawnParticle(Particle.END_ROD,
                        target.getX(), target.getY() + 1, target.getZ(),
                        30, 0.5, 0.5, 0.5, 0.05);
                    player.sendMessage("§d✦ §5护佑 §d已将你传送回重生点！");
                }
            });

            if (plugin.getConfigManager().getBoolean("debug")) {
                plugin.getLogger().fine("[护佑] §a" + player.getName() + " 触发不死图腾，已传送至 " + formatLocation(target));
            }
        }, null, 20L);
    }

    /**
     * 查找玩家的重生点
     * 优先级：床 → 重生锚 → 世界出生点
     */
    private Location findSpawnLocation(Player player) {
        // 1. 床位置（含重生锚）
        Location bed = player.getBedSpawnLocation();
        if (bed != null) return bed.clone();

        // 2. 世界出生点
        return player.getWorld().getSpawnLocation().clone();
    }

    private String formatLocation(Location loc) {
        return String.format("(%.1f, %.1f, %.1f, %s)",
            loc.getX(), loc.getY(), loc.getZ(),
            loc.getWorld() != null ? loc.getWorld().getName() : "?");
    }
}
