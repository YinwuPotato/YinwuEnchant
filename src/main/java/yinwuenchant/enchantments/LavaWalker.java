package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LavaWalker extends CustomEnchantment {
    private final ConfigManager configManager;

    /** 记录被转换的方块：位置 → 原始方块数据 */
    private final Map<Location, BlockData> convertedBlocks = new ConcurrentHashMap<>();

    /** 穿着熔岩行者靴子的玩家缓存 */
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    /** 等级缓存（避免PDC读取） */
    private final Map<UUID, Integer> playerLevels = new ConcurrentHashMap<>();

    /** 防火效果冷却（玩家UUID → 上次生效的游戏刻） */
    private final Map<UUID, Long> lastFireTick = new ConcurrentHashMap<>();

    // 从 config 加载的数值
    private int fireCooldownTicks = 100;
    private int fireDurationTicks = 120;
    private int revertDelayTicks = 100;

    private ScheduledTask periodicTask;

    public LavaWalker(YinwuEnchantments plugin) {
        super(plugin, "lava_walker", "熔岩行者", 2, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("熔岩行者 " + getRomanNumeral(level));
    }

    private void loadConfig() {
        var cfg = plugin.getConfig();
        fireCooldownTicks = cfg.getInt("enchantments.lava_walker.fire-cooldown-ticks", 100);
        fireDurationTicks = cfg.getInt("enchantments.lava_walker.fire-duration-ticks", 120);
        revertDelayTicks = cfg.getInt("enchantments.lava_walker.revert-delay-ticks", 100);
    }

    @Override
    public void onEnable() {
        loadConfig();
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerMoveEvent.class,
            event -> {
                PlayerMoveEvent e = (PlayerMoveEvent) event;
                handleMove(e);
            }
        );

        plugin.getEnchantmentManager().subscribeEvent(
            BlockBreakEvent.class,
            event -> {
                BlockBreakEvent e = (BlockBreakEvent) event;
                handleBlockBreak(e);
            }
        );

        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerQuitEvent.class,
            event -> activePlayers.remove(event.getPlayer().getUniqueId())
        );
    }

    @Override
    public void onEquipmentChange(Player player) {
        var boots = player.getInventory().getBoots();
        if (hasEnchantment(boots)) {
            activePlayers.add(player.getUniqueId());
            playerLevels.put(player.getUniqueId(), getEnchantmentLevel(boots));
        } else {
            activePlayers.remove(player.getUniqueId());
            playerLevels.remove(player.getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        if (periodicTask != null && !periodicTask.isCancelled()) {
            periodicTask.cancel();
        }
        for (Map.Entry<Location, BlockData> entry : convertedBlocks.entrySet()) {
            Location loc = entry.getKey();
            BlockData originalData = entry.getValue();
            try {
                plugin.getServer().getRegionScheduler().run(plugin, loc, t -> {
                    Block block = loc.getBlock();
                    if (block.getType() == Material.MAGMA_BLOCK) {
                        block.setBlockData(originalData, false);
                    }
                });
            } catch (Exception ignored) {
                // 关闭期间可能无法获取区域线程所有权，忽略
            }
        }
        convertedBlocks.clear();
        lastFireTick.clear();
    }

    private void refreshStationary() {
    }

    private void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isEnchantmentEnabled("lava_walker")) return;
        if (player.isDead() || !player.isOnline()) return;
        if (!activePlayers.contains(player.getUniqueId())) return;

        int level = playerLevels.getOrDefault(player.getUniqueId(), 1);
        int range = level + 1;

        boolean moved = event.getFrom().getBlockX() != event.getTo().getBlockX()
                     || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
        if (!moved) return;

        Block center = player.getLocation().getBlock();
        // Frost Walker 模式：以脚底为中心扫圆形一圈，只查 y=0 层
        for (int x = -range; x <= range; x++) {
            for (int z = -range; z <= range; z++) {
                if (x * x + z * z > range * range) continue;
                Block block = center.getRelative(x, 0, z);
                if (!isLava(block.getType())) continue;
                if (block.getRelative(0, 1, 0).getType() != Material.AIR) continue;

                Location loc = block.getLocation();
                if (convertedBlocks.containsKey(loc)) continue;

                convertedBlocks.put(loc, block.getBlockData().clone());
                block.setType(Material.MAGMA_BLOCK, false);
                scheduleRevert(loc);
            }
        }

        applyFireResist(player);
    }

    private void applyFireResist(Player player) {
        long tick = Bukkit.getCurrentTick();
        Long last = lastFireTick.get(player.getUniqueId());
        if (last == null || tick - last >= fireCooldownTicks) {
            lastFireTick.put(player.getUniqueId(), tick);
            player.addPotionEffect(new PotionEffect(
                PotionEffectType.FIRE_RESISTANCE, fireDurationTicks, 0,
                true, false, true
            ));
        }
    }

    private void handleBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.MAGMA_BLOCK) return;

        Location loc = block.getLocation();
        BlockData original = convertedBlocks.remove(loc);
        if (original == null) return;

        event.setDropItems(false);
        event.setExpToDrop(0);
        block.setBlockData(original, false);
    }

    private boolean isLava(Material material) {
        return material == Material.LAVA;
    }

    private void scheduleRevert(Location loc) {
        plugin.getServer().getRegionScheduler().runDelayed(plugin, loc, (task) -> {
            BlockData original = convertedBlocks.remove(loc);
            if (original != null) {
                Block block = loc.getBlock();
                if (block.getType() == Material.MAGMA_BLOCK) {
                    block.setBlockData(original, false);
                    block.getWorld().spawnParticle(
                        Particle.LAVA,
                        block.getLocation().add(0.5, 1, 0.5),
                        3, 0.3, 0.1, 0.3, 0.01
                    );
                }
            }
        }, revertDelayTicks);
    }

    public void cleanup() {
        if (periodicTask != null && !periodicTask.isCancelled()) {
            periodicTask.cancel();
        }
        for (Map.Entry<Location, BlockData> entry : convertedBlocks.entrySet()) {
            Block block = entry.getKey().getBlock();
            if (block.getType() == Material.MAGMA_BLOCK) {
                try {
                    block.setBlockData(entry.getValue(), false);
                } catch (Exception ignored) {}
            }
        }
        convertedBlocks.clear();
    }
}
