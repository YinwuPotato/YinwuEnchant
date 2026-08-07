package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

public class ShriekerSense extends CustomEnchantment {
    /** TextDisplay 标记键：用于启动时清扫上次禁用残留的提示实体 */
    private static final NamespacedKey DISPLAY_MARKER =
        new NamespacedKey("yinwuenchant", "shrieker_display");

    private final ConfigManager configManager;
    private final Set<EntityType> sculkEntities;
    // 存储每个玩家创建的 TextDisplay 实体列表（使用 ConcurrentHashMap 保证线程安全）
    private final Map<UUID, List<TextDisplay>> playerTextDisplays = new ConcurrentHashMap<>();
    // TextDisplay 位置 → 实体（供方块破坏时按位置直接查，避免跨区域遍历）
    private final Map<Location, TextDisplay> textDisplayByLocation = new ConcurrentHashMap<>();
    // 存储每个玩家的冷却时间（UUID -> 最后使用时间戳，毫秒）
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();
    // 被高亮的实体 UUID（用于清理发光效果，避免残留）
    private final Set<UUID> highlightedEntities = ConcurrentHashMap.newKeySet();

    public ShriekerSense(YinwuEnchantments plugin) {
        super(plugin, "shrieker_sense", "幽匿探测", 1, new Material[] {
            Material.SPYGLASS
        });
        this.configManager = plugin.getConfigManager();

        // 初始化潜声系列生物列表
        sculkEntities = new HashSet<>();
        sculkEntities.add(EntityType.WARDEN);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("幽匿探测 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {
        boolean enabled = configManager.isEnchantmentEnabled("shrieker_sense");

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[幽匿探测] onEnable() 被调用, enabled=" + enabled);
        }

        if (!enabled) {
            return;
        }

        // 启动清扫：移除上次禁用时残留的标记 TextDisplay（Folia：逐 chunk 在其 region 线程执行）
        sweepResidualDisplays();
    }

    /** 清扫世界内残留的幽匿探测提示实体（带 PDC 标记的 TextDisplay）。尽力而为，失败不阻断启用。 */
    private void sweepResidualDisplays() {
        try {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    final int cx = chunk.getX();
                    final int cz = chunk.getZ();
                    plugin.getServer().getRegionScheduler().run(plugin, world, cx, cz, (task) -> {
                        try {
                            for (Entity e : chunk.getEntities()) {
                                if (e.getType() == EntityType.TEXT_DISPLAY
                                    && e.getPersistentDataContainer().has(DISPLAY_MARKER)) {
                                    e.remove();
                                }
                            }
                        } catch (Exception ignored) {
                            // 单块清扫失败不影响其他块
                        }
                    });
                }
            }
        } catch (Exception e) {
            // Folia 下世界/区块枚举可能受限，清扫降级为跳过
            if (plugin.getConfigManager().getBoolean("debug")) {
                plugin.getLogger().fine("[幽匿探测] 启动清扫跳过: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisable() {
        // 先清除被高亮实体的发光效果，避免 reload 后监守者仍发光
        clearGlowEffects();

        if (plugin.getConfigManager().getBoolean("debug")) {
            int totalDisplays = playerTextDisplays.values().stream()
                .mapToInt(List::size)
                .sum();
            plugin.getLogger().fine("[幽匿探测] 开始清理 " + totalDisplays + " 个 TextDisplay");
        }

        // 遍历并移除所有 TextDisplay 实体（位置表提供所属 region，避免跨区域读）
        for (Map.Entry<Location, TextDisplay> entry : textDisplayByLocation.entrySet()) {
            final Location loc = entry.getKey();
            final TextDisplay display = entry.getValue();
            try {
                plugin.getServer().getRegionScheduler().run(plugin, loc, (task) -> {
                    if (!display.isDead()) {
                        display.remove();
                    }
                });
            } catch (Exception e) {
                if (plugin.getConfigManager().getBoolean("debug")) {
                    plugin.getLogger().fine("[幽匿探测] 清理 TextDisplay 时出错: " + e.getMessage());
                }
            }
        }

        playerTextDisplays.clear();
        textDisplayByLocation.clear();
        playerCooldowns.clear();
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
                        long remainingMillis = cooldownMillis - (currentTime - lastUseTime);
                        int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);

                        player.sendActionBar("§c幽匿探测冷却中... 还剩 " + remainingSeconds + " 秒");
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 0.5f);
                        return;
                    }

                    // 更新冷却时间
                    playerCooldowns.put(playerId, currentTime);

                    // 清除该玩家之前的所有 TextDisplay（避免叠加）
                    clearPlayerTextDisplays(player.getUniqueId());

                    int range = configManager.getInt("shrieker_sense.highlight-range");
                    int duration = configManager.getInt("shrieker_sense.highlight-duration");

                    // 高亮附近的潜声生物（实体）- 只添加发光效果
                    // Folia 注意：getNearbyEntities 仅返回当前区域实体，跨区域生物不会被高亮（范围受限，不崩溃）
                    int entityCount = 0;
                    for (Entity entity : player.getNearbyEntities(range, range, range)) {
                        if (sculkEntities.contains(entity.getType())) {
                            highlightEntity(entity, duration);
                            entityCount++;
                        }
                    }

                    // 高亮附近的幽匿尖啸体（方块）- 按 chunk 分组调度到所属 region 扫描
                    AtomicInteger shriekerCount = new AtomicInteger();
                    highlightSculkShriekers(player, range, duration, shriekerCount);

                    // 延迟三拍统计后再回玩家线程发提示
                    final int finalEntityCount = entityCount;
                    player.getScheduler().runDelayed(plugin, (t) -> {
                        if (!player.isOnline()) return;
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        player.sendActionBar("§a幽匿探测已激活！检测到 " + finalEntityCount + " 个监守者, " + shriekerCount.get() + " 个尖啸体");
                        if (configManager.getBoolean("debug")) {
                            plugin.getLogger().fine("ShriekerSense: 范围=" + range + ", 持续时间=" + duration + "刻, 监守者=" + finalEntityCount + ", 尖啸体=" + shriekerCount.get());
                        }
                    }, null, 3L);
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
                        double offsetX = configManager.getDouble("shrieker_sense.text-display-offset.x");
                        double offsetY = configManager.getDouble("shrieker_sense.text-display-offset.y");
                        double offsetZ = configManager.getDouble("shrieker_sense.text-display-offset.z");

                        // 按位置直接查表移除，避免跨区域遍历所有玩家的 TextDisplay
                        Location expectedDisplayLoc = blockLocation.clone().add(offsetX, offsetY, offsetZ);
                        TextDisplay display = textDisplayByLocation.remove(expectedDisplayLoc);
                        if (display != null && !display.isDead()) {
                            display.remove();
                            for (Map.Entry<UUID, List<TextDisplay>> entry : playerTextDisplays.entrySet()) {
                                List<TextDisplay> list = entry.getValue();
                                if (list.remove(display) && list.isEmpty()) {
                                    playerTextDisplays.remove(entry.getKey(), list);
                                }
                            }
                            if (configManager.getBoolean("debug")) {
                                plugin.getLogger().fine("尖啸体被破坏，已移除对应的 TextDisplay at " + expectedDisplayLoc);
                            }
                        }
                    });
                }
            }
        );
    }

    private void highlightEntity(Entity entity, int durationTicks) {
        if (entity instanceof org.bukkit.entity.LivingEntity livingEntity) {
            // 追踪被高亮实体，供清理发光效果
            highlightedEntities.add(entity.getUniqueId());
            // Folia R6: 实体可能在其他 Region，在其实体线程施加效果
            entity.getScheduler().run(plugin, task -> {
                if (!livingEntity.isDead()) {
                    livingEntity.addPotionEffect(new org.bukkit.potion.PotionEffect(
                        org.bukkit.potion.PotionEffectType.GLOWING,
                        durationTicks, 0, true, false, false
                    ));
                }
            }, null);
        }
    }

    /** 清除所有被高亮实体的发光效果（下次探测/reload 时调用，Folia：逐实体线程移除） */
    private void clearGlowEffects() {
        for (UUID uuid : highlightedEntities) {
            Entity e = Bukkit.getEntity(uuid);
            if (e instanceof org.bukkit.entity.LivingEntity living) {
                living.getScheduler().run(plugin, task -> {
                    if (!living.isDead()) {
                        living.removePotionEffect(org.bukkit.potion.PotionEffectType.GLOWING);
                    }
                }, null);
            }
        }
        highlightedEntities.clear();
    }

    private void highlightSculkShriekers(org.bukkit.entity.Player player, int range, int durationTicks, AtomicInteger count) {
        var world = player.getWorld();
        Location playerLoc = player.getLocation();
        int px = playerLoc.getBlockX();
        int py = playerLoc.getBlockY();
        int pz = playerLoc.getBlockZ();
        int minY = Math.max(py - range, world.getMinHeight());
        int maxY = Math.min(py + range, world.getMaxHeight() - 1);

        // 从配置读取 TextDisplay 位置偏移（支持 reload）
        double offsetX = configManager.getDouble("shrieker_sense.text-display-offset.x");
        double offsetY = configManager.getDouble("shrieker_sense.text-display-offset.y");
        double offsetZ = configManager.getDouble("shrieker_sense.text-display-offset.z");

        // 按 chunk 分组调度到所属 region 扫描，避免玩家线程跨区域读方块
        for (int cx = (px - range) >> 4; cx <= (px + range) >> 4; cx++) {
            for (int cz = (pz - range) >> 4; cz <= (pz + range) >> 4; cz++) {
                final int fcX = cx;
                final int fcZ = cz;
                plugin.getServer().getRegionScheduler().run(plugin, world, fcX, fcZ, (task) -> {
                    int startX = Math.max(fcX << 4, px - range);
                    int endX = Math.min((fcX << 4) + 15, px + range);
                    int startZ = Math.max(fcZ << 4, pz - range);
                    int endZ = Math.min((fcZ << 4) + 15, pz + range);
                    for (int x = startX; x <= endX; x++) {
                        for (int y = minY; y <= maxY; y++) {
                            for (int z = startZ; z <= endZ; z++) {
                                Block block = world.getBlockAt(x, y, z);
                                if (block.getType() == Material.SCULK_SHRIEKER) {
                                    Location loc = block.getLocation().add(offsetX, offsetY, offsetZ);
                                    createTextDisplay(loc, durationTicks, player.getUniqueId());
                                    count.incrementAndGet();
                                }
                            }
                        }
                    }
                });
            }
        }
    }

    private void createTextDisplay(Location location, int durationTicks, UUID playerUUID) {
        try {
            // 在该位置生成 TextDisplay 实体（已在方块所属 region 线程）
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class);

            // 设置文本内容（使用 Adventure API 设置颜色）
            textDisplay.text(net.kyori.adventure.text.Component.text("◯", net.kyori.adventure.text.format.TextColor.color(0x00FFFF)));

            // 设置显示属性
            textDisplay.setBillboard(Display.Billboard.CENTER);
            textDisplay.setDefaultBackground(false);
            textDisplay.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
            textDisplay.setSeeThrough(true);
            textDisplay.setViewRange(128.0f);

            // 设置亮度（最大亮度）
            textDisplay.setBrightness(new Display.Brightness(15, 15));

            // 设置大小（通过 transformation scale）- 放大到包裹整个方块
            textDisplay.setTransformationMatrix(new org.joml.Matrix4f().scale(16f, 16f, 16f));

            // 打 PDC 标记：供启动清扫识别残留实体
            textDisplay.getPersistentDataContainer().set(DISPLAY_MARKER, PersistentDataType.BYTE, (byte) 1);

            // 将该 TextDisplay 添加到玩家列表和位置表中
            playerTextDisplays.computeIfAbsent(playerUUID, k -> new CopyOnWriteArrayList<>()).add(textDisplay);
            textDisplayByLocation.put(location, textDisplay);

            if (configManager.getBoolean("debug")) {
                plugin.getLogger().fine("TextDisplay 创建成功 at " + location);
            }

            // 在持续时间后移除 TextDisplay（在实体所在区域线程执行）
            plugin.getServer().getRegionScheduler().runDelayed(plugin, location, (task) -> {
                if (!textDisplay.isDead()) {
                    textDisplay.remove();
                    playerTextDisplays.computeIfPresent(playerUUID, (key, list) -> {
                        list.remove(textDisplay);
                        return list.isEmpty() ? null : list;
                    });
                    textDisplayByLocation.remove(location);
                    if (configManager.getBoolean("debug")) {
                        plugin.getLogger().fine("TextDisplay 已移除");
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
        // 重新探测前先清除旧高亮发光
        clearGlowEffects();
        List<TextDisplay> displays = playerTextDisplays.get(playerUUID);
        if (displays == null || displays.isEmpty()) return;

        // 复制列表以避免并发修改异常
        List<TextDisplay> displaysCopy = new ArrayList<>(displays);
        // 通过位置表找到各 display 的所属 region，避免跨区域读 getLocation()
        List<Location> toRemove = new ArrayList<>();
        for (Map.Entry<Location, TextDisplay> entry : textDisplayByLocation.entrySet()) {
            final Map.Entry<Location, TextDisplay> e = entry;
            if (displaysCopy.contains(e.getValue())) {
                toRemove.add(e.getKey());
                plugin.getServer().getRegionScheduler().run(plugin, e.getKey(), (task) -> {
                    if (!e.getValue().isDead()) {
                        e.getValue().remove();
                    }
                });
            }
        }
        for (Location loc : toRemove) {
            textDisplayByLocation.remove(loc);
        }
        displays.clear();
        if (configManager.getBoolean("debug")) {
            plugin.getLogger().fine("已清除玩家的 TextDisplay");
        }
    }

    /**
     * 公共方法：清理玩家离线时的 TextDisplay（供 EventListener 调用）
     */
    public void cleanupPlayer(UUID playerUUID) {
        clearPlayerTextDisplays(playerUUID);
        playerTextDisplays.remove(playerUUID);
        if (configManager.getBoolean("debug")) {
            plugin.getLogger().fine("玩家 " + playerUUID + " 离线，已清理所有 TextDisplay");
        }
    }
}
