package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class CatsPaw extends CustomEnchantment {
    private final ConfigManager configManager;

    private double range = 16.0;
    private final FleeGoal fleeGoal = new FleeGoal();

    public CatsPaw(YinwuEnchantments plugin) {
        super(plugin, "cats_paw", "猫爪", 1, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override public Component displayName(int level) { return Component.text("猫爪"); }

    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("cats_paw")) return;
        range = plugin.getConfig().getDouble("enchantments.cats_paw.range", 16.0);
        int interval = plugin.getConfig().getInt("enchantments.cats_paw.check-interval", 60);

        // 周期检测：实时读取每个在线玩家盔甲栏靴子，有猫爪则恐吓周围苦力怕
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> {
                    var boots = player.getInventory().getBoots();
                    if (hasEnchantment(boots)) {
                        fleeGoal.fleeFrom(player, plugin, Creeper.class, range, 1.5, 30,
                            Sound.ENTITY_CAT_HISS, Particle.HEART);
                    }
                }, null);
            }
        }, 1L, interval);
    }

    @Override
    public void onDisable() { fleeGoal.clear(); }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(CreatureSpawnEvent.class, event -> {
            if (event.getEntity() instanceof Creeper creeper) {
                // 生成时实时检测附近有猫爪靴子的玩家
                creeper.getScheduler().runDelayed(plugin, t -> {
                    for (Player player : creeper.getWorld().getPlayers()) {
                        player.getScheduler().run(plugin, t2 -> {
                            var boots = player.getInventory().getBoots();
                            if (hasEnchantment(boots)
                                && player.getLocation().distanceSquared(creeper.getLocation()) <= range * range) {
                                fleeGoal.fleeFrom(player, plugin, Creeper.class, range, 1.5, 30,
                                    Sound.ENTITY_CAT_HISS, Particle.HEART);
                            }
                        }, null);
                    }
                }, null, 5L);
            }
        });
        plugin.getEnchantmentManager().subscribeEvent(PlayerQuitEvent.class,
            event -> fleeGoal.clear());
    }
}
