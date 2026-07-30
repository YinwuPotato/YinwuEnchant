package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VampireCurse extends CustomEnchantment {

    private final YinwuEnchantments plugin;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private int checkInterval = 40;

    public VampireCurse(YinwuEnchantments plugin) {
        super(plugin, "vampire_curse", "吸血鬼诅咒", 1, new Material[]{
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET
        });
        this.plugin = plugin;
    }

    @Override
    public Component displayName(int level) {
        return Component.text("吸血鬼诅咒");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("vampire_curse")) return;
        checkInterval = plugin.getConfig().getInt("enchantments.vampire_curse.check-interval", 40);

        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) { activePlayers.remove(uuid); continue; }
                player.getScheduler().run(plugin, (t) -> tickPlayer(player), null);
            }
        }, 1L, checkInterval);
    }

    @Override
    public void onDisable() {
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

    private void tickPlayer(Player player) {
        if (!player.isOnline()) return;
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (player.getWorld().getEnvironment() != World.Environment.NORMAL) return;

        long time = player.getWorld().getTime();

        if (isDay(time)) {
            if (isExposedToSun(player)) {
                player.setFireTicks(Math.max(player.getFireTicks(), 100));
            }
        } else {
            if (!player.hasPotionEffect(PotionEffectType.REGENERATION)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    600, 0, false, false, true));
            }
            if (!player.hasPotionEffect(PotionEffectType.RESISTANCE)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    600, 0, false, false, true));
            }
        }
    }

    private boolean isDay(long time) {
        return time >= 1000 && time < 13000;
    }

    private boolean isExposedToSun(Player player) {
        return player.getWorld().getHighestBlockYAt(player.getLocation()) <= player.getEyeLocation().getBlockY()
            && !player.isInWaterOrRain()
            && player.getWorld().isClearWeather();
    }
}
