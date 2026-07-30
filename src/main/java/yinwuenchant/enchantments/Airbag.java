package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 气囊 —— 减少鞘翅飞行时撞击墙壁的伤害
 *
 * 效果：
 * - 装备鞘翅飞行时撞击墙壁，气囊自动触发缓冲
 * - 每级减免一定比例的撞墙伤害
 * - 触发后有短暂冷却，冷却期间仍可减免部分伤害
 */
public class Airbag extends CustomEnchantment {

    private final YinwuEnchantments plugin;

    // 玩家冷却记录（上次全额触发时间）
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    // 配置
    private int cooldownTicks;
    private double reductionPerLevel;

    public Airbag(YinwuEnchantments plugin) {
        super(plugin, "airbag", "气囊", 3, new Material[]{
            Material.ELYTRA
        });
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        cooldownTicks = plugin.getConfigManager().getRawConfig().getInt("enchantments.airbag.cooldown-ticks", 100);
        reductionPerLevel = plugin.getConfigManager().getRawConfig().getDouble("enchantments.airbag.reduction-per-level", 0.4);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("气囊");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("airbag")) {
            return;
        }
        loadConfig();
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[气囊] 已启用（每级减免: " + (int)(reductionPerLevel * 100) + "%，冷却: " + cooldownTicks + " tick）");
        }
    }

    @Override
    public void onDisable() {
        cooldowns.clear();
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[气囊] 已禁用");
        }
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            EntityDamageEvent.class,
            this::onEntityDamage
        );
    }

    private void onEntityDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.FLY_INTO_WALL) return;
        if (!(event.getEntity() instanceof Player player)) return;

        // 检查鞘翅是否有气囊附魔
        ItemStack chest = player.getInventory().getChestplate();
        if (chest == null || chest.getType() != Material.ELYTRA || !hasEnchantment(chest)) return;

        int level = getEnchantmentLevel(chest);
        if (level <= 0) return;

        UUID uid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // 计算减免比例
        double reduction = level * reductionPerLevel;
        if (reduction > 0.95) reduction = 0.95; // 保留至少 5% 伤害防止完全免疫

        // 冷却期间减免比例减半
        Long lastTrigger = cooldowns.get(uid);
        if (lastTrigger != null && (now - lastTrigger) < cooldownTicks * 50L) {
            reduction *= 0.5;
        } else {
            // 全额触发时记录冷却
            cooldowns.put(uid, now);
        }

        double originalDamage = event.getDamage();
        double reducedDamage = originalDamage * (1.0 - reduction);
        event.setDamage(Math.max(reducedDamage, 0.5)); // 至少保留 0.5 伤害

        // 粒子与音效
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, loc.getX(), loc.getY(), loc.getZ(),
            20, 0.6, 0.4, 0.6, 0.03);
        player.getWorld().playSound(loc, Sound.BLOCK_WOOL_PLACE, 0.8f, 0.8f);

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[气囊] §a减免 " + (int)(reduction * 100) + "% 撞墙伤害（原始: "
                + String.format("%.1f", originalDamage) + " → 实际: "
                + String.format("%.1f", event.getDamage()) + "）");
        }
    }
}
