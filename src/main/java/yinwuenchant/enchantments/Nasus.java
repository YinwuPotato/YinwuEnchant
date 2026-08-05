package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractSkeleton;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class Nasus extends CustomEnchantment {
    private final ConfigManager configManager;

    private double range = 16.0;
    private final FleeGoal fleeGoal = new FleeGoal();

    public Nasus(YinwuEnchantments plugin) {
        super(plugin, "nasus", "狗头", 1, new Material[] {
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override public Component displayName(int level) { return Component.text("狗头"); }

    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("nasus")) return;
        range = plugin.getConfig().getDouble("enchantments.nasus.range", 16.0);
        int interval = plugin.getConfig().getInt("enchantments.nasus.check-interval", 60);

        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> {
                    var helmet = player.getInventory().getHelmet();
                    if (hasEnchantment(helmet)) {
                        fleeGoal.fleeFrom(player, plugin, AbstractSkeleton.class, range, 1.5, 30,
                            Sound.ENTITY_WOLF_GROWL, Particle.HEART);
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
            if (event.getEntity() instanceof AbstractSkeleton skeleton) {
                // 生成时实时检测附近戴狗头头盔的玩家
                skeleton.getScheduler().runDelayed(plugin, t -> {
                    for (Player player : skeleton.getWorld().getPlayers()) {
                        player.getScheduler().run(plugin, t2 -> {
                            var helmet = player.getInventory().getHelmet();
                            if (hasEnchantment(helmet)
                                && player.getLocation().distanceSquared(skeleton.getLocation()) <= range * range) {
                                fleeGoal.fleeFrom(player, plugin, AbstractSkeleton.class, range, 1.5, 30,
                                    Sound.ENTITY_WOLF_GROWL, Particle.HEART);
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
