package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Clearsight extends CustomEnchantment {
    private final ConfigManager configManager;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private ScheduledTask task;

    public Clearsight(YinwuEnchantments plugin) {
        super(plugin, "clearsight", "明目", 1, new Material[] {
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET, Material.CARVED_PUMPKIN,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("明目 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("clearsight")) return;

        int interval = configManager.getInt("clearsight.check-interval");
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) { activePlayers.remove(uuid); continue; }
                player.getScheduler().run(plugin, (task2) -> {
                    if (player.hasPotionEffect(PotionEffectType.DARKNESS)) {
                        player.removePotionEffect(PotionEffectType.DARKNESS);
                    }
                    if (player.hasPotionEffect(PotionEffectType.BLINDNESS)) {
                        player.removePotionEffect(PotionEffectType.BLINDNESS);
                    }
                }, null);
            }
        }, 1L, interval);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        activePlayers.clear();
    }

    @Override
    public void onEquipmentChange(Player player) {
        var helmet = player.getInventory().getHelmet();
        if (hasEnchantment(helmet)) {
            activePlayers.add(player.getUniqueId());
        } else {
            activePlayers.remove(player.getUniqueId());
        }
    }
}
