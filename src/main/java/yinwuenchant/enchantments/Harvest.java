package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 丰收附魔 - Harvest Enchantment
 * 效果：锄头右键可以收获以目标作物为中心5x5x5的所有成熟作物
 * - 直接在作物位置掉落成熟掉落物品
 * - 自动重新补种对应的作物种子
 * - 支持时运效果
 * - 与精准采集互斥
 * - 冷却时间3秒
 */
public class Harvest extends CustomEnchantment {
    private final ConfigManager configManager;

    // 玩家冷却时间记录 <玩家UUID, 最后使用时间戳>
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    private long cooldownTicks = 60; // 3秒

    // 收获范围（从配置读取）
    private int harvestRange = 2; // 默认值

    public Harvest(YinwuEnchantments plugin) {
        super(plugin, "harvest", "丰收", 1, new Material[] {
            Material.WOODEN_HOE,
            Material.STONE_HOE,
            Material.IRON_HOE,
            Material.GOLDEN_HOE,
            Material.DIAMOND_HOE,
            Material.NETHERITE_HOE
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("丰收");
    }

    @Override
    public void onEnable() {
        boolean enabled = configManager.isEnchantmentEnabled("harvest");

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[丰收] onEnable() 被调用, enabled=" + enabled);  // ✅ 使用 fine 级别
        }

        if (!enabled) {
            return;
        }

        // ✅ 从配置缓存中读取收获范围（性能优化）
        harvestRange = configManager.getInt("harvest.range");
        cooldownTicks = plugin.getConfig().getInt("enchantments.harvest.cooldown-ticks", 60);

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[丰收] 收获范围: " + harvestRange + " (实际范围: " + (harvestRange * 2 + 1) + "x" + (harvestRange * 2 + 1) + "x" + (harvestRange * 2 + 1) + ")");  // ✅ 使用 fine 级别
        }

        // ⚠️ 事件订阅注册由 EnchantmentManager.enableAll() 统一调用 registerEventSubscribers()
        // 此处不再重复注册，避免事件处理器被注册两次
    }

    @Override
    public void onDisable() {
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[丰收] 已禁用，清理 " + playerCooldowns.size() + " 个玩家冷却记录");  // ✅ 使用 fine 级别
        }
        playerCooldowns.clear();
    }

    @Override
    public void registerEventSubscribers() {
        // 注册玩家右键事件
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerInteractEvent.class,
            event -> {
                if (!configManager.isEnchantmentEnabled("harvest")) return;

                // 只处理右键空气或方块
                if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK &&
                    event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR) {
                    return;
                }

                Player player = event.getPlayer();
                ItemStack item = event.getItem();

                if (item == null || item.getType() == Material.AIR) {
                    return;
                }

                // 检查是否持有带有丰收附魔的锄头
                if (!hasEnchantment(item)) {
                    return;
                }

                // ✅ 检查是否与精准采集互斥
                if (item.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0) {
                    return;
                }

                // 检查冷却时间
                if (isOnCooldown(player)) {
                    if (plugin.getConfigManager().getBoolean("debug")) {
                        plugin.getLogger().fine("[丰收调试] 玩家 " + player.getName() + " 正在冷却中，跳过触发");  // ✅ 使用 fine 级别
                    }
                    return;
                }

                // 获取目标方块
                Block targetBlock = event.getClickedBlock();
                if (targetBlock == null) {
                    return;
                }

                // 检查目标方块是否是可收获的作物
                if (!isHarvestableCrop(targetBlock)) {
                    return;
                }

                // 执行收获逻辑
                harvestCrops(player, targetBlock);

                // 设置冷却时间
                setCooldown(player);
            }
        );
    }

    /**
     * 检查是否在冷却中
     */
    private boolean isOnCooldown(Player player) {
        Long lastUse = playerCooldowns.get(player.getUniqueId());
        if (lastUse == null) return false;

        long currentTime = System.currentTimeMillis();
        return (currentTime - lastUse) < cooldownTicks * 50L;
    }

    /**
     * 设置冷却时间
     */
    private void setCooldown(Player player) {
        playerCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * 检查方块是否是可收获的作物
     */
    private boolean isHarvestableCrop(Block block) {
        BlockData blockData = block.getBlockData();

        // 检查是否是 Ageable（有年龄属性的作物）
        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }

        // 检查是否完全成熟
        return ageable.getAge() >= ageable.getMaximumAge();
    }

    /**
     * 收获作物（可配置范围）
     */
    private void harvestCrops(Player player, Block centerBlock) {
        World world = centerBlock.getWorld();
        Location center = centerBlock.getLocation();

        int harvestedCount = 0;

        // ✅ 使用配置中的范围值遍历
        for (int x = -harvestRange; x <= harvestRange; x++) {
            for (int y = -harvestRange; y <= harvestRange; y++) {
                for (int z = -harvestRange; z <= harvestRange; z++) {
                    Block block = world.getBlockAt(
                        center.getBlockX() + x,
                        center.getBlockY() + y,
                        center.getBlockZ() + z
                    );

                    if (isHarvestableCrop(block)) {
                        // 收获单个作物
                        if (harvestSingleCrop(player, block)) {
                            harvestedCount++;
                        }
                    }
                }
            }
        }

        // 播放音效（村民高兴的声音）
        if (harvestedCount > 0) {
            world.playSound(center, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);

            if (plugin.getConfigManager().getBoolean("debug")) {
                plugin.getLogger().fine("[丰收] 玩家 " + player.getName() + " 收获了 " + harvestedCount + " 个作物");  // ✅ 使用 fine 级别

                // ✅ 额外调试：统计土豆总数
                int fortuneLevel = getFortuneLevel(player);
                plugin.getLogger().fine("[丰收调试] 总计收获: " + harvestedCount + " 格 | 时运等级: " + fortuneLevel);  // ✅ 使用 fine 级别
            }
        }
    }

    /**
     * 收获单个作物
     */
    private boolean harvestSingleCrop(Player player, Block cropBlock) {
        Material cropType = cropBlock.getType();
        BlockData blockData = cropBlock.getBlockData();

        if (!(blockData instanceof Ageable ageable)) {
            return false;
        }

        // ✅ 获取时运等级（用于调试）
        int fortuneLevel = getFortuneLevel(player);

        // 获取作物的掉落物品
        List<ItemStack> drops = getCropDrops(cropType, player);

        // ✅ 调试日志：记录每次收获的详细信息
        if (plugin.getConfigManager().getBoolean("debug") && cropType == Material.POTATOES) {
            int totalPotatoes = drops.stream()
                .filter(item -> item.getType() == Material.POTATO)
                .mapToInt(ItemStack::getAmount)
                .sum();
            plugin.getLogger().fine("[丰收调试] 收获土豆 | 时运等级: " + fortuneLevel + " | 掉落数量: " + totalPotatoes);  // ✅ 使用 fine 级别
        }

        // 在作物位置掉落物品
        for (ItemStack drop : drops) {
            if (drop != null && !drop.getType().isAir()) {
                cropBlock.getWorld().dropItemNaturally(cropBlock.getLocation().add(0.5, 0.5, 0.5), drop);
            }
        }

        // ✅ 浆果丛不需要重新补种，其他作物需要重置年龄
        if (cropType != Material.SWEET_BERRY_BUSH) {
            ageable.setAge(0);
            cropBlock.setBlockData(ageable);
        }

        return true;
    }

    /**
     * 获取作物掉落物品（支持时运）
     */
    private List<ItemStack> getCropDrops(Material cropType, Player player) {
        List<ItemStack> drops = new ArrayList<>();

        // 获取工具上的时运等级
        int fortuneLevel = getFortuneLevel(player);

        // 根据不同作物类型计算掉落
        switch (cropType) {
            case WHEAT:
                // 小麦：掉落小麦 + 种子（时运影响种子数量）
                drops.add(new ItemStack(Material.WHEAT, 1));
                int wheatSeeds = 1 + (fortuneLevel > 0 ? ThreadLocalRandom.current().nextInt(fortuneLevel + 1) : 0);
                drops.add(new ItemStack(Material.WHEAT_SEEDS, wheatSeeds));
                break;

            case CARROTS:
                // 胡萝卜：掉落胡萝卜（时运增加数量）
                int carrots = 1 + (fortuneLevel > 0 ? ThreadLocalRandom.current().nextInt(fortuneLevel + 2) : 0);
                drops.add(new ItemStack(Material.CARROT, carrots));
                break;

            case POTATOES:
                // ✅ 土豆：匹配 Minecraft Java 版原版掉落机制
                // 根据原版数据：无时运平均 3.71，时运 III 平均 5.43


                // 基础掉落 2-5 个（均匀分布）
                int potatoes = 2 + ThreadLocalRandom.current().nextInt(4);

                // 时运效果：每级时运有概率额外增加掉落
                // 时运 I: +0-1 (50%概率), 时运 II: +0-2, 时运 III: +0-3
                if (fortuneLevel > 0) {
                    potatoes += ThreadLocalRandom.current().nextInt(fortuneLevel + 1);
                }

                drops.add(new ItemStack(Material.POTATO, potatoes));

                // 2% 概率掉落毒土豆
                if (ThreadLocalRandom.current().nextDouble() < 0.02) {
                    drops.add(new ItemStack(Material.POISONOUS_POTATO, 1));
                }
                break;

            case BEETROOTS:
                // 甜菜根：掉落甜菜根（时运增加数量）+ 1个种子
                int beetroots = 1 + (fortuneLevel > 0 ? ThreadLocalRandom.current().nextInt(fortuneLevel + 2) : 0);
                drops.add(new ItemStack(Material.BEETROOT, beetroots));
                drops.add(new ItemStack(Material.BEETROOT_SEEDS, 1));
                break;

            case NETHER_WART:
                // 下界疣：掉落下界疣（时运增加数量）
                int netherWarts = 1 + (fortuneLevel > 0 ? ThreadLocalRandom.current().nextInt(fortuneLevel + 3) : 0);
                drops.add(new ItemStack(Material.NETHER_WART, netherWarts));
                break;

            case SWEET_BERRY_BUSH:
                // 甜浆果丛：掉落甜浆果（时运增加数量），不重新种植
                int sweetBerries = 1 + (fortuneLevel > 0 ? ThreadLocalRandom.current().nextInt(fortuneLevel + 3) : 0);
                drops.add(new ItemStack(Material.SWEET_BERRIES, sweetBerries));
                break;

            default:
                // 其他作物：默认掉落1个
                drops.add(new ItemStack(cropType, 1));
                break;
        }

        return drops;
    }

    /**
     * 获取玩家工具上的时运等级
     */
    private int getFortuneLevel(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return 0;
        }

        // 获取原版时运附魔等级
        return item.getEnchantmentLevel(Enchantment.FORTUNE);
    }
}
