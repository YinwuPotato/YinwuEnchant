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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Nasus extends CustomEnchantment {
    private final ConfigManager configManager;

    private double range = 16.0;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
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
            for (UUID uuid : activePlayers) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) { activePlayers.remove(uuid); continue; }
                player.getScheduler().run(plugin, t -> {
                    fleeGoal.fleeFrom(player, plugin, AbstractSkeleton.class, range, 1.5, 30,
                        Sound.ENTITY_WOLF_GROWL, Particle.HEART);
                }, null);
            }
        }, 1L, interval);
    }

    @Override
    public void onEquipmentChange(Player player) {
        var helmet = player.getInventory().getHelmet();
        if (hasEnchantment(helmet)) activePlayers.add(player.getUniqueId());
        else activePlayers.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() { fleeGoal.clear(); activePlayers.clear(); }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(CreatureSpawnEvent.class, event -> {
            if (event.getEntity() instanceof AbstractSkeleton skeleton) {
                skeleton.getScheduler().runDelayed(plugin, t ->
                    fleeGoal.checkOnSpawn(skeleton, plugin, activePlayers, AbstractSkeleton.class, range, 1.5, 30,
                        Sound.ENTITY_WOLF_GROWL, Particle.HEART), null, 5L);
            }
        });
        plugin.getEnchantmentManager().subscribeEvent(PlayerQuitEvent.class,
            event -> activePlayers.remove(event.getPlayer().getUniqueId()));
    }
}
