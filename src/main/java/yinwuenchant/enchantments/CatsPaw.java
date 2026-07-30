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

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CatsPaw extends CustomEnchantment {
    private final ConfigManager configManager;

    private double range = 16.0;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
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

        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (UUID uuid : activePlayers) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) { activePlayers.remove(uuid); continue; }
                player.getScheduler().run(plugin, t -> {
                    fleeGoal.fleeFrom(player, plugin, Creeper.class, range, 1.5, 30,
                        Sound.ENTITY_CAT_HISS, Particle.HEART);
                }, null);
            }
        }, 1L, interval);
    }

    @Override
    public void onEquipmentChange(Player player) {
        var boots = player.getInventory().getBoots();
        if (hasEnchantment(boots)) activePlayers.add(player.getUniqueId());
        else activePlayers.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() { fleeGoal.clear(); activePlayers.clear(); }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(CreatureSpawnEvent.class, event -> {
            if (event.getEntity() instanceof Creeper creeper) {
                creeper.getScheduler().runDelayed(plugin, t ->
                    fleeGoal.checkOnSpawn(creeper, plugin, activePlayers, Creeper.class, range, 1.5, 30,
                        Sound.ENTITY_CAT_HISS, Particle.HEART), null, 5L);
            }
        });
        plugin.getEnchantmentManager().subscribeEvent(PlayerQuitEvent.class,
            event -> activePlayers.remove(event.getPlayer().getUniqueId()));
    }
}
