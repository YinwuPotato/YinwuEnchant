package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
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
    private final java.util.Map<java.util.UUID, Integer> playerSpeedLevels = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<java.util.UUID> activePlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();

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

        particleTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (java.util.UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) { activePlayers.remove(uuid); continue; }
                player.getScheduler().run(plugin, (task) -> {
                    if (isInDarkness(player)) {
                        // 缓存由 onEquipmentChange 维护，不读PDC
                        int level = playerSpeedLevels.getOrDefault(player.getUniqueId(), 1);
                        spawnParticles(player, level);
                    }
                }, null);
            }
        }, 1L, particleInterval);

        soundTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (java.util.UUID uuid : activePlayers) {
                Player player = Bukkit.getPlayer(uuid);
                if (player == null || !player.isOnline()) { activePlayers.remove(uuid); continue; }
                player.getScheduler().run(plugin, (task) -> {
                    if (isInDarkness(player)) {
                        int level = playerSpeedLevels.getOrDefault(player.getUniqueId(), 1);
                        playSound(player, level);
                    }
                }, null);
            }
        }, 1L, soundInterval);
    }

    @Override
    public void onDisable() {
        if (particleTask != null) particleTask.cancel();
        if (soundTask != null) soundTask.cancel();
        activePlayers.clear();
        playerSpeedLevels.clear();
    }

    @Override
    public void onEquipmentChange(Player player) {
        var boots = player.getInventory().getBoots();
        if (hasEnchantment(boots)) {
            int level = getEnchantmentLevel(boots);
            activePlayers.add(player.getUniqueId());
            playerSpeedLevels.put(player.getUniqueId(), level);
        } else {
            activePlayers.remove(player.getUniqueId());
            playerSpeedLevels.remove(player.getUniqueId());
        }
    }

    public void cleanupPlayerCache(java.util.UUID playerId) {
        activePlayers.remove(playerId);
        playerSpeedLevels.remove(playerId);
    }

    @Override
    public void onPlayerMove(org.bukkit.entity.Player player) {
        if (!configManager.isEnchantmentEnabled("darkspeed")) return;
        if (player.isDead() || !player.isOnline()) return;

        // 只查缓存，不读PDC
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (isInDarkness(player)) {
            int level = playerSpeedLevels.getOrDefault(player.getUniqueId(), 1);
            Integer cachedLevel = playerSpeedLevels.get(player.getUniqueId());
            if (cachedLevel == null || cachedLevel != level) {
                double speedPerLevel = configManager.getDouble("darkspeed.speed-per-level");
                double speedAmount = speedPerLevel * level;
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.SPEED, 100,
                    (int) Math.min(speedAmount * 10, 4),
                    true, false, false
                ));
                playerSpeedLevels.put(player.getUniqueId(), level);
            }
        }
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
