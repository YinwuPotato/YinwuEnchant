package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LavaWalker extends CustomEnchantment {
    private final ConfigManager configManager;

    /** 记录被转换的方块：位置 → 原始方块数据 */
    private final Map<Location, BlockData> convertedBlocks = new ConcurrentHashMap<>();

    /** 被转换方块最近被熔岩行者玩家触达的游戏刻（保持存活用） */
    private final Map<Location, Long> blockLastActive = new ConcurrentHashMap<>();

    /** 防火效果冷却（玩家UUID → 上次生效的游戏刻） */
    private final Map<UUID, Long> lastFireTick = new ConcurrentHashMap<>();

    // 从 config 加载的数值
    private int fireCooldownTicks = 100;
    private int fireDurationTicks = 120;
    private int revertDelayTicks = 100;

    private io.papermc.paper.threadedregions.scheduler.ScheduledTask keepAliveTask;

    public LavaWalker(YinwuEnchantments plugin) {
        super(plugin, "lava_walker", "熔岩行者", 2, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public org.bukkit.enchantments.Enchantment[] getExclusiveEnchantments() {
        return new org.bukkit.enchantments.Enchantment[] { Enchantment.FROST_WALKER };
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
        // 周期触达玩家附近的已转换方块，模拟冰霜行者：站在上面持续冻结不还原
        keepAliveTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, (task) -> {
                    var boots = player.getInventory().getBoots();
                    if (!hasEnchantment(boots)) return;
                    int range = getEnchantmentLevel(boots) + 1;
                    Location feet = player.getLocation().getBlock().getLocation();
                    long tick = Bukkit.getCurrentTick();
                    for (Location loc : convertedBlocks.keySet()) {
                        if (loc.getWorld() != feet.getWorld()) continue;
                        if (Math.abs(loc.getBlockX() - feet.getBlockX()) <= range
                            && Math.abs(loc.getBlockZ() - feet.getBlockZ()) <= range) {
                            blockLastActive.put(loc, tick);
                        }
                    }
                }, null);
            }
        }, 1L, 10L);
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
    }

    @Override
    public void onDisable() {
        if (keepAliveTask != null) keepAliveTask.cancel();
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
        blockLastActive.clear();
        lastFireTick.clear();
    }

    private void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isEnchantmentEnabled("lava_walker")) return;
        if (player.isDead() || !player.isOnline()) return;

        // 实时读靴子，避免缓存失效（右键穿装备不触发 onEquipmentChange）
        var boots = player.getInventory().getBoots();
        if (!hasEnchantment(boots)) return;
        int level = getEnchantmentLevel(boots);
        int range = level + 1;

        boolean moved = event.getFrom().getBlockX() != event.getTo().getBlockX()
                     || event.getFrom().getBlockZ() != event.getTo().getBlockZ();
        if (!moved) return;

        Location center = player.getLocation().getBlock().getLocation();
        // Frost Walker 模式：扫脚下一层（行走面）+ 脚底层，以脚底为中心圆形一圈
        // 只有空气在上的熔岩表面层会被转换 → 站在边上就能预转换前方熔岩
        // Folia：方块检查与转换调度到所属 region 线程
        for (int dy = -1; dy <= 0; dy++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    if (x * x + z * z > range * range) continue;
                    Location loc = center.clone().add(x, dy, z);
                    plugin.getServer().getRegionScheduler().run(plugin, loc, (task) -> {
                        Block block = loc.getBlock();
                        if (!isLava(block.getType())) return;
                        if (block.getRelative(0, 1, 0).getType() != Material.AIR) return;
                        if (convertedBlocks.containsKey(loc)) return;

                        convertedBlocks.put(loc, block.getBlockData().clone());
                        blockLastActive.put(loc, (long) Bukkit.getCurrentTick());
                        block.setType(Material.MAGMA_BLOCK, false);
                        scheduleRevert(loc);
                    });
                }
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
            BlockData original = convertedBlocks.get(loc);
            if (original == null) return;

            // 玩家仍在附近（保持存活触达过）→ 延期还原，模拟冰霜行者持续冻结
            long tick = Bukkit.getCurrentTick();
            Long last = blockLastActive.get(loc);
            if (last != null && tick - last < revertDelayTicks) {
                scheduleRevert(loc);
                return;
            }

            convertedBlocks.remove(loc);
            blockLastActive.remove(loc);
            Block block = loc.getBlock();
            if (block.getType() == Material.MAGMA_BLOCK) {
                block.setBlockData(original, false);
                block.getWorld().spawnParticle(
                    Particle.LAVA,
                    block.getLocation().add(0.5, 1, 0.5),
                    3, 0.3, 0.1, 0.3, 0.01
                );
            }
        }, revertDelayTicks);
    }
}
