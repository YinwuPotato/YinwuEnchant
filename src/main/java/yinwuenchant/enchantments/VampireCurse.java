package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class VampireCurse extends CustomEnchantment {

    private final YinwuEnchantments plugin;
    private ScheduledTask task;
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

        // 实时检测所有在线玩家头盔，避免缓存失效（右键穿装备不触发 onEquipmentChange）
        task = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, (t2) -> {
                    var helmet = player.getInventory().getHelmet();
                    if (!hasEnchantment(helmet)) return;
                    tickPlayer(player);
                }, null);
            }
        }, 1L, checkInterval);
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
    }

    private void tickPlayer(Player player) {
        if (!player.isOnline()) return;

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
