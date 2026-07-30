package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ShriekerSense extends CustomEnchantment {
    private final ConfigManager configManager;
    private final Set<EntityType> sculkEntities;
    // 存储每个玩家创建的 TextDisplay 实体列表（使用 ConcurrentHashMap 保证线程安全）
    private final Map<UUID, List<TextDisplay>> playerTextDisplays = new ConcurrentHashMap<>();
    // 存储每个玩家的冷却时间（UUID -> 最后使用时间戳，毫秒）
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    public ShriekerSense(YinwuEnchantments plugin) {
        super(plugin, "shrieker_sense", "幽匿探测", 1, new Material[] {
            Material.SPYGLASS
        });
        this.configManager = plugin.getConfigManager();

        // 初始化潜声系列生物列表
        sculkEntities = new HashSet<>();
        sculkEntities.add(EntityType.WARDEN);

        // ✅ 移除构造函数中的事件注册，改为在 onEnable() 中注册
    }

        @Override
    public Component displayName(int level) {
        return Component.text("幽匿探测 " + getRomanNumeral(level));
    }



    @Override
    public void onEnable() {
        boolean enabled = configManager.isEnchantmentEnabled("shrieker_sense");

        // ✅ 调试日志：确认 onEnable 被调用
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[幽匿探测] onEnable() 被调用, enabled=" + enabled);  // ✅ 使用 fine 级别
        }

        if (!enabled) {
            return;
        }

        // ⚠️ 事件订阅注册由 EnchantmentManager.enableAll() 统一调用 registerEventSubscribers()
        // 此处不再重复注册，避免事件处理器被注册两次
    }

    @Override
    public void onDisable() {
        // ✅ 修复：区分 reload 和服务器关闭场景
        // - reload 时：必须手动清理所有 TextDisplay，防止残留
        // - 服务器关闭时：Minecraft 会自动清理，但手动清理也无妨

        if (plugin.getConfigManager().getBoolean("debug")) {
            int totalDisplays = playerTextDisplays.values().stream()
                .mapToInt(List::size)
                .sum();
            plugin.getLogger().fine("[幽匿探测] 开始清理 " + totalDisplays + " 个 TextDisplay");  // ✅ 使用 fine 级别
        }

        // ✅ 遍历并移除所有 TextDisplay 实体
        for (List<TextDisplay> displays : playerTextDisplays.values()) {
            for (TextDisplay display : displays) {
                if (display != null && !display.isDead()) {
                    try {
                        // 在实体所在区域线程执行移除操作（Folia 要求）
                        Location loc = display.getLocation();
                        plugin.getServer().getRegionScheduler().run(plugin, loc, (task) -> {
                            if (!display.isDead()) {
                                display.remove();
                            }
                        });
                    } catch (Exception e) {
                        // 如果实体已经被移除或位置无效，忽略错误
                        if (plugin.getConfigManager().getBoolean("debug")) {
                            plugin.getLogger().fine("[幽匿探测] 清理 TextDisplay 时出错: " + e.getMessage());
                        }
                    }
                }
            }
        }

        // 清空引用列表
        playerTextDisplays.clear();
        playerCooldowns.clear();

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[幽匿探测] TextDisplay 清理完成");  // ✅ 使用 fine 级别
        }
    }

    @Override
    public void registerEventSubscribers() {
        // 注册玩家右键事件（望远镜使用）
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerInteractEvent.class,
            event -> {
                if (!configManager.isEnchantmentEnabled("shrieker_sense")) return;

                org.bukkit.entity.Player player = event.getPlayer();
                ItemStack item = event.getItem();

                if (item != null && item.getType() == Material.SPYGLASS && hasEnchantment(item)) {
                    UUID playerId = player.getUniqueId();

                    // 检查冷却时间
                    long currentTime = System.currentTimeMillis();
                    Long lastUseTime = playerCooldowns.get(playerId);
                    int cooldownSeconds = configManager.getInt("shrieker_sense.cooldown");
                    long cooldownMillis = cooldownSeconds * 1000L;

                    if (lastUseTime != null && (currentTime - lastUseTime) < cooldownMillis) {
                        // 还在冷却中，计算剩余时间
                        long remainingMillis = cooldownMillis - (currentTime - lastUseTime);
                        int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);

                        // 提示玩家冷却中
                        player.sendActionBar("§c幽匿探测冷却中... 还剩 " + remainingSeconds + " 秒");

                        // 播放错误音效
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.5f);

                        return; // 阻止触发
                    }

                    // 更新冷却时间
                    playerCooldowns.put(playerId, currentTime);

                    // 清除该玩家之前的所有 TextDisplay（避免叠加）
                    clearPlayerTextDisplays(player.getUniqueId());

                    int range = configManager.getInt("shrieker_sense.highlight-range");
                    int duration = configManager.getInt("shrieker_sense.highlight-duration");

                    // 高亮附近的潜声生物（实体）- 只添加发光效果
                    int entityCount = 0;
                    for (Entity entity : player.getNearbyEntities(range, range, range)) {
                        if (sculkEntities.contains(entity.getType())) {
                            highlightEntity(entity, duration);
                            entityCount++;
                        }
                    }

                    // 高亮附近的幽匿尖啸体（方块）- 使用 TextDisplay
                    int shriekerCount = highlightSculkShriekers(player, range, duration);

                    // 播放经验球拾取音效
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

                    // 给玩家提示
                    player.sendActionBar("§a幽匿探测已激活！检测到 " + entityCount + " 个监守者, " + shriekerCount + " 个尖啸体");

                    // 调试信息
                    if (configManager.getBoolean("debug")) {
                        plugin.getLogger().fine("ShriekerSense: 范围=" + range + ", 持续时间=" + duration + "刻, 监守者=" + entityCount + ", 尖啸体=" + shriekerCount);  // ✅ 使用 fine 级别
                    }
                }
            }
        );

        // 注册方块破坏事件（清理被破坏的尖啸体对应的 TextDisplay）
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.block.BlockBreakEvent.class,
            event -> {
                Block block = event.getBlock();
                if (block.getType() == Material.SCULK_SHRIEKER) {
                    Location blockLocation = block.getLocation();

                    // 在方块所在区域线程执行清除操作（Folia 要求）
                    plugin.getServer().getRegionScheduler().run(plugin, blockLocation, (task) -> {
                        // 从配置读取偏移量
                        double offsetX = configManager.getDouble("shrieker_sense.text-display-offset.x");
                        double offsetY = configManager.getDouble("shrieker_sense.text-display-offset.y");
                        double offsetZ = configManager.getDouble("shrieker_sense.text-display-offset.z");

                        // 计算 TextDisplay 的实际位置
                        Location expectedDisplayLoc = blockLocation.clone().add(offsetX, offsetY, offsetZ);

                        // 遍历所有玩家的 TextDisplay，查找并移除对应位置的
                        for (Map.Entry<UUID, List<TextDisplay>> entry : playerTextDisplays.entrySet()) {
                            List<TextDisplay> displays = entry.getValue();
                            displays.removeIf(display -> {
                                if (display != null && !display.isDead()) {
                                    Location displayLoc = display.getLocation();
                                    // 检查 TextDisplay 位置是否与预期位置匹配
                                    if (Math.abs(displayLoc.getX() - expectedDisplayLoc.getX()) < 0.1 &&
                                        Math.abs(displayLoc.getY() - expectedDisplayLoc.getY()) < 0.1 &&
                                        Math.abs(displayLoc.getZ() - expectedDisplayLoc.getZ()) < 0.1) {
                                        display.remove();
                                        if (configManager.getBoolean("debug")) {
                                            plugin.getLogger().fine("尖啸体被破坏，已移除对应的 TextDisplay at " + displayLoc);  // ✅ 使用 fine 级别
                                        }
                                        return true;
                                    }
                                }
                                return false;
                            });
                        }
                    });
                }
            }
        );
    }

    /**
     * ✅ 修复：使用标准的 Bukkit 事件监听器签名
     */

    private void highlightEntity(Entity entity, int durationTicks) {
        if (entity instanceof org.bukkit.entity.LivingEntity livingEntity) {
            // Folia R6: 实体可能在其他 Region，在其实体线程施加效果
            entity.getScheduler().run(plugin, task -> {
                livingEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                    org.bukkit.potion.PotionEffectType.GLOWING,
                    durationTicks, 0, true, false, false
                ));
            }, null);
        }
    }

    private int highlightSculkShriekers(org.bukkit.entity.Player player, int range, int durationTicks) {
        Location playerLoc = player.getLocation();
        int count = 0;

        // 从配置读取 TextDisplay 位置偏移（支持 reload）
        double offsetX = configManager.getDouble("shrieker_sense.text-display-offset.x");
        double offsetY = configManager.getDouble("shrieker_sense.text-display-offset.y");
        double offsetZ = configManager.getDouble("shrieker_sense.text-display-offset.z");

        // 遍历范围内的方块（48格范围）
        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    Location checkLoc = playerLoc.clone().add(x, y, z);

                    // 检查区块是否已加载
                    if (!player.getWorld().isChunkLoaded(checkLoc.getBlockX() >> 4, checkLoc.getBlockZ() >> 4)) {
                        continue;
                    }

                    Block block = checkLoc.getBlock();

                    // 检查是否是幽匿尖啸体
                    if (block.getType() == Material.SCULK_SHRIEKER) {
                        // 在尖啸体所在区域线程中创建 TextDisplay（Folia 要求）
                        final Location finalLoc = block.getLocation().clone().add(offsetX, offsetY, offsetZ);
                        plugin.getServer().getRegionScheduler().run(plugin, finalLoc, (task) -> createTextDisplay(finalLoc, durationTicks, player.getUniqueId()));
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private void createTextDisplay(Location location, int durationTicks, UUID playerUUID) {
        try {
            // 在该位置生成 TextDisplay 实体
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class);

            // 设置文本内容（使用 Adventure API 设置颜色）
            textDisplay.text(net.kyori.adventure.text.Component.text("◯", net.kyori.adventure.text.format.TextColor.color(0x00FFFF)));

            // 设置显示属性
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setDefaultBackground(false); // 禁用默认背景
            textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0)); // 设置完全透明背景
            textDisplay.setSeeThrough(true); // 透视显示
            textDisplay.setViewRange(128.0f); // 可见距离

            // 设置亮度（最大亮度）
            textDisplay.setBrightness(new Display.Brightness(15, 15));

            // 设置大小（通过 transformation scale）- 放大到包裹整个方块
            // JOML 是 Paper API 的依赖，用于矩阵变换
            textDisplay.setTransformationMatrix(new org.joml.Matrix4f().scale(16f, 16f, 16f));

            // 将该 TextDisplay 添加到玩家列表中
            playerTextDisplays.computeIfAbsent(playerUUID, k -> new CopyOnWriteArrayList<>()).add(textDisplay);

            // 调试信息
            if (configManager.getBoolean("debug")) {
                plugin.getLogger().fine("TextDisplay 创建成功 at " + location);  // ✅ 使用 fine 级别
            }

            // 在持续时间后移除 TextDisplay（在实体所在区域线程执行）
            plugin.getServer().getRegionScheduler().runDelayed(plugin, location, (task) -> {
                if (!textDisplay.isDead()) {
                    textDisplay.remove();
                    // ✅ 安全地从列表中移除，防止内存泄漏
                    playerTextDisplays.computeIfPresent(playerUUID, (key, list) -> {
                        list.remove(textDisplay);
                        return list.isEmpty() ? null : list;  // 如果列表为空则移除整个条目
                    });
                    if (configManager.getBoolean("debug")) {
                        plugin.getLogger().fine("TextDisplay 已移除");  // ✅ 使用 fine 级别
                    }
                }
            }, durationTicks);
        } catch (Exception e) {
            plugin.getLogger().severe("创建 TextDisplay 失败: " + e.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "详细错误信息", e);
        }
    }

    /**
     * 清除指定玩家的所有 TextDisplay 实体（需要在对应区域线程执行）
     */
    private void clearPlayerTextDisplays(UUID playerUUID) {
        List<TextDisplay> displays = playerTextDisplays.get(playerUUID);
        if (displays != null) {
            // 复制列表以避免并发修改异常
            List<TextDisplay> displaysCopy = new ArrayList<>(displays);
            for (TextDisplay display : displaysCopy) {
                if (display != null && !display.isDead()) {
                    // 在实体所在区域线程执行移除操作
                    Location loc = display.getLocation();
                    plugin.getServer().getRegionScheduler().run(plugin, loc, (task) -> {
                        if (!display.isDead()) {
                            display.remove();
                        }
                    });
                }
            }
            displays.clear();
            if (configManager.getBoolean("debug")) {
                plugin.getLogger().fine("已清除玩家的 TextDisplay");  // ✅ 使用 fine 级别
            }
        }
    }

    /**
     * 公共方法：清理玩家离线时的 TextDisplay（供 EventListener 调用）
     */
    public void cleanupPlayer(UUID playerUUID) {
        clearPlayerTextDisplays(playerUUID);
        playerTextDisplays.remove(playerUUID);
        if (configManager.getBoolean("debug")) {
            plugin.getLogger().fine("玩家 " + playerUUID + " 离线，已清理所有 TextDisplay");  // ✅ 使用 fine 级别
        }
    }
}
