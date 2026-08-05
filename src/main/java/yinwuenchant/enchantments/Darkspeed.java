package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public class Darkspeed extends CustomEnchantment {
    private final ConfigManager configManager;
    private ScheduledTask particleTask;
    private ScheduledTask soundTask;

    public Darkspeed(YinwuEnchantments plugin) {
        super(plugin, "darkspeed", "黑暗行者", 3, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("黑暗行者 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("darkspeed")) return;

        int particleInterval = configManager.getInt("darkspeed.particle-interval");
        int soundInterval = configManager.getInt("darkspeed.sound-interval");

        // 实时检测所有在线玩家的靴子，避免缓存失效（右键穿装备不触发 onEquipmentChange）
        particleTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, (task) -> {
                    var boots = player.getInventory().getBoots();
                    if (!hasEnchantment(boots)) return;
                    int level = getEnchantmentLevel(boots);
                    if (isInDarkness(player)) {
                        applySpeed(player, level);
                        spawnParticles(player, level);
                    }
                }, null);
            }
        }, 1L, particleInterval);

        soundTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, (task) -> {
                    var boots = player.getInventory().getBoots();
                    if (!hasEnchantment(boots)) return;
                    int level = getEnchantmentLevel(boots);
                    if (isInDarkness(player)) playSound(player, level);
                }, null);
            }
        }, 1L, soundInterval);
    }

    @Override
    public void onDisable() {
        if (particleTask != null) particleTask.cancel();
        if (soundTask != null) soundTask.cancel();
    }

    private void applySpeed(Player player, int level) {
        PotionEffect existing = player.getPotionEffect(PotionEffectType.SPEED);
        if (existing != null && existing.getDuration() > 40) return;
        double speedPerLevel = configManager.getDouble("darkspeed.speed-per-level");
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.SPEED, 100,
            (int) Math.min(speedPerLevel * level * 10, 4),
            true, false, false
        ));
    }

    private boolean isInDarkness(Player player) {
        int darkLightLevel = configManager.getInt("darkspeed.dark-light-level");
        return player.getLocation().getBlock().getLightLevel() <= darkLightLevel;
    }

    private void spawnParticles(Player player, int level) {
        Location loc = player.getLocation();
        String particleTypeStr = configManager.getString("darkspeed.particle-type");
        Particle particle;
        try { particle = Particle.valueOf(particleTypeStr); } catch (IllegalArgumentException e) { particle = Particle.SOUL_FIRE_FLAME; }
        player.getWorld().spawnParticle(particle, loc.clone().add(0, 0.5, 0), level * 3, 0.5, 0.2, 0.5, 0.1);
    }

    private void playSound(Player player, int level) {
        org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.fromString("minecraft:" + configManager.getString("darkspeed.sound-type").toLowerCase().replace("_", "-"));
        Sound sound = key != null ? org.bukkit.Registry.SOUNDS.get(key) : null;
        if (sound == null) sound = Sound.BLOCK_SOUL_SAND_STEP;
        player.playSound(player.getLocation(), sound, 0.5f * level, 0.8f + level * 0.1f);
    }
}
