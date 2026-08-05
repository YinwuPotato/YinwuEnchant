package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔化 —— 自动熔炼挖掘的方块
 *
 * 效果：
 * - 挖掘方块时，掉落物自动变为熔炼后的产物
 * - 先应用时运（由原版机制处理），再熔炼掉落物
 * - 与精准采集互斥（同时存在时熔炼不生效）
 * - 支持镐、锹、斧、锄
 */
public class Smelt extends CustomEnchantment {

    private final YinwuEnchantments plugin;

    /** 主手带熔化附魔（且无精准采集）的玩家缓存（玩家线程刷新，方块事件线程读取） */
    private final Set<UUID> smeltPlayers = ConcurrentHashMap.newKeySet();
    private ScheduledTask refreshTask;

    // 熔炼映射表：原料 → 熔炼产物
    private static final Map<Material, ItemStack> SMELTING_MAP_INNER = new HashMap<>();

    static {
        // === 粗矿 → 金属锭 ===
        SMELTING_MAP_INNER.put(Material.RAW_IRON, new ItemStack(Material.IRON_INGOT));
        SMELTING_MAP_INNER.put(Material.RAW_GOLD, new ItemStack(Material.GOLD_INGOT));
        SMELTING_MAP_INNER.put(Material.RAW_COPPER, new ItemStack(Material.COPPER_INGOT));

        // === 粗矿块 → 金属块（搭配精准采集挖粗矿块再熔炼）===
        SMELTING_MAP_INNER.put(Material.RAW_IRON_BLOCK, new ItemStack(Material.IRON_BLOCK));
        SMELTING_MAP_INNER.put(Material.RAW_GOLD_BLOCK, new ItemStack(Material.GOLD_BLOCK));
        SMELTING_MAP_INNER.put(Material.RAW_COPPER_BLOCK, new ItemStack(Material.COPPER_BLOCK));

        // === 矿石块 ===
        SMELTING_MAP_INNER.put(Material.IRON_ORE, new ItemStack(Material.IRON_INGOT));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_IRON_ORE, new ItemStack(Material.IRON_INGOT));
        SMELTING_MAP_INNER.put(Material.GOLD_ORE, new ItemStack(Material.GOLD_INGOT));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_GOLD_ORE, new ItemStack(Material.GOLD_INGOT));
        SMELTING_MAP_INNER.put(Material.COPPER_ORE, new ItemStack(Material.COPPER_INGOT));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_COPPER_ORE, new ItemStack(Material.COPPER_INGOT));
        SMELTING_MAP_INNER.put(Material.NETHER_GOLD_ORE, new ItemStack(Material.GOLD_INGOT));
        SMELTING_MAP_INNER.put(Material.NETHER_QUARTZ_ORE, new ItemStack(Material.QUARTZ));
        SMELTING_MAP_INNER.put(Material.ANCIENT_DEBRIS, new ItemStack(Material.NETHERITE_SCRAP));
        // 宝石矿石（通常不精准采集时已直接掉落成品，仍列出以覆盖完整配方）
        SMELTING_MAP_INNER.put(Material.COAL_ORE, new ItemStack(Material.COAL));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_COAL_ORE, new ItemStack(Material.COAL));
        SMELTING_MAP_INNER.put(Material.DIAMOND_ORE, new ItemStack(Material.DIAMOND));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_DIAMOND_ORE, new ItemStack(Material.DIAMOND));
        SMELTING_MAP_INNER.put(Material.EMERALD_ORE, new ItemStack(Material.EMERALD));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_EMERALD_ORE, new ItemStack(Material.EMERALD));
        SMELTING_MAP_INNER.put(Material.LAPIS_ORE, new ItemStack(Material.LAPIS_LAZULI));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_LAPIS_ORE, new ItemStack(Material.LAPIS_LAZULI));
        SMELTING_MAP_INNER.put(Material.REDSTONE_ORE, new ItemStack(Material.REDSTONE));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_REDSTONE_ORE, new ItemStack(Material.REDSTONE));

        // === 深板岩系列 ===
        SMELTING_MAP_INNER.put(Material.COBBLED_DEEPSLATE, new ItemStack(Material.DEEPSLATE));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_BRICKS, new ItemStack(Material.CRACKED_DEEPSLATE_BRICKS));
        SMELTING_MAP_INNER.put(Material.DEEPSLATE_TILES, new ItemStack(Material.CRACKED_DEEPSLATE_TILES));

        // === 石材/建材 ===
        SMELTING_MAP_INNER.put(Material.COBBLESTONE, new ItemStack(Material.STONE));
        SMELTING_MAP_INNER.put(Material.STONE, new ItemStack(Material.SMOOTH_STONE));
        SMELTING_MAP_INNER.put(Material.STONE_BRICKS, new ItemStack(Material.CRACKED_STONE_BRICKS));
        SMELTING_MAP_INNER.put(Material.CHISELED_STONE_BRICKS, new ItemStack(Material.CRACKED_STONE_BRICKS));
        SMELTING_MAP_INNER.put(Material.SANDSTONE, new ItemStack(Material.SMOOTH_SANDSTONE));
        SMELTING_MAP_INNER.put(Material.RED_SANDSTONE, new ItemStack(Material.SMOOTH_RED_SANDSTONE));
        SMELTING_MAP_INNER.put(Material.QUARTZ_BLOCK, new ItemStack(Material.SMOOTH_QUARTZ));

        // === 下界系列 ===
        SMELTING_MAP_INNER.put(Material.NETHERRACK, new ItemStack(Material.NETHER_BRICK));
        SMELTING_MAP_INNER.put(Material.NETHER_BRICKS, new ItemStack(Material.CRACKED_NETHER_BRICKS));
        SMELTING_MAP_INNER.put(Material.BASALT, new ItemStack(Material.SMOOTH_BASALT));
        SMELTING_MAP_INNER.put(Material.POLISHED_BLACKSTONE_BRICKS, new ItemStack(Material.CRACKED_POLISHED_BLACKSTONE_BRICKS));

        // === 黏土/沙子/玻璃 ===
        SMELTING_MAP_INNER.put(Material.CLAY, new ItemStack(Material.TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.CLAY_BALL, new ItemStack(Material.BRICK));
        SMELTING_MAP_INNER.put(Material.SAND, new ItemStack(Material.GLASS));
        SMELTING_MAP_INNER.put(Material.RED_SAND, new ItemStack(Material.GLASS));

        // === 陶瓦 → 釉陶 ===
        SMELTING_MAP_INNER.put(Material.WHITE_TERRACOTTA, new ItemStack(Material.WHITE_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.ORANGE_TERRACOTTA, new ItemStack(Material.ORANGE_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.MAGENTA_TERRACOTTA, new ItemStack(Material.MAGENTA_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.LIGHT_BLUE_TERRACOTTA, new ItemStack(Material.LIGHT_BLUE_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.YELLOW_TERRACOTTA, new ItemStack(Material.YELLOW_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.LIME_TERRACOTTA, new ItemStack(Material.LIME_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.PINK_TERRACOTTA, new ItemStack(Material.PINK_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.GRAY_TERRACOTTA, new ItemStack(Material.GRAY_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.LIGHT_GRAY_TERRACOTTA, new ItemStack(Material.LIGHT_GRAY_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.CYAN_TERRACOTTA, new ItemStack(Material.CYAN_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.PURPLE_TERRACOTTA, new ItemStack(Material.PURPLE_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.BLUE_TERRACOTTA, new ItemStack(Material.BLUE_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.BROWN_TERRACOTTA, new ItemStack(Material.BROWN_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.GREEN_TERRACOTTA, new ItemStack(Material.GREEN_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.RED_TERRACOTTA, new ItemStack(Material.RED_GLAZED_TERRACOTTA));
        SMELTING_MAP_INNER.put(Material.BLACK_TERRACOTTA, new ItemStack(Material.BLACK_GLAZED_TERRACOTTA));

        // === 其他 ===
        SMELTING_MAP_INNER.put(Material.WET_SPONGE, new ItemStack(Material.SPONGE));
        SMELTING_MAP_INNER.put(Material.SEA_PICKLE, new ItemStack(Material.LIME_DYE));
        SMELTING_MAP_INNER.put(Material.CACTUS, new ItemStack(Material.GREEN_DYE));
        SMELTING_MAP_INNER.put(Material.CHORUS_FRUIT, new ItemStack(Material.POPPED_CHORUS_FRUIT));

        // === 食品 ===
        SMELTING_MAP_INNER.put(Material.POTATO, new ItemStack(Material.BAKED_POTATO));
        SMELTING_MAP_INNER.put(Material.KELP, new ItemStack(Material.DRIED_KELP));
        SMELTING_MAP_INNER.put(Material.BEEF, new ItemStack(Material.COOKED_BEEF));
        SMELTING_MAP_INNER.put(Material.PORKCHOP, new ItemStack(Material.COOKED_PORKCHOP));
        SMELTING_MAP_INNER.put(Material.MUTTON, new ItemStack(Material.COOKED_MUTTON));
        SMELTING_MAP_INNER.put(Material.CHICKEN, new ItemStack(Material.COOKED_CHICKEN));
        SMELTING_MAP_INNER.put(Material.COD, new ItemStack(Material.COOKED_COD));
        SMELTING_MAP_INNER.put(Material.SALMON, new ItemStack(Material.COOKED_SALMON));
        SMELTING_MAP_INNER.put(Material.RABBIT, new ItemStack(Material.COOKED_RABBIT));

        // === 木制工具 → 木炭 ===
        SMELTING_MAP_INNER.put(Material.WOODEN_PICKAXE, new ItemStack(Material.CHARCOAL));
        SMELTING_MAP_INNER.put(Material.WOODEN_AXE, new ItemStack(Material.CHARCOAL));
        SMELTING_MAP_INNER.put(Material.WOODEN_SHOVEL, new ItemStack(Material.CHARCOAL));
        SMELTING_MAP_INNER.put(Material.WOODEN_HOE, new ItemStack(Material.CHARCOAL));
        SMELTING_MAP_INNER.put(Material.WOODEN_SWORD, new ItemStack(Material.CHARCOAL));

        // === 原木/木头 → 木炭 ===
        for (Material mat : new Material[]{
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG,
            Material.JUNGLE_LOG, Material.ACACIA_LOG, Material.DARK_OAK_LOG,
            Material.MANGROVE_LOG, Material.CHERRY_LOG, Material.PALE_OAK_LOG,
            Material.STRIPPED_OAK_LOG, Material.STRIPPED_SPRUCE_LOG,
            Material.STRIPPED_BIRCH_LOG, Material.STRIPPED_JUNGLE_LOG,
            Material.STRIPPED_ACACIA_LOG, Material.STRIPPED_DARK_OAK_LOG,
            Material.STRIPPED_MANGROVE_LOG, Material.STRIPPED_CHERRY_LOG, Material.STRIPPED_PALE_OAK_LOG,
            Material.OAK_WOOD, Material.SPRUCE_WOOD, Material.BIRCH_WOOD,
            Material.JUNGLE_WOOD, Material.ACACIA_WOOD, Material.DARK_OAK_WOOD,
            Material.MANGROVE_WOOD, Material.CHERRY_WOOD, Material.PALE_OAK_WOOD,
            Material.STRIPPED_OAK_WOOD, Material.STRIPPED_SPRUCE_WOOD,
            Material.STRIPPED_BIRCH_WOOD, Material.STRIPPED_JUNGLE_WOOD,
            Material.STRIPPED_ACACIA_WOOD, Material.STRIPPED_DARK_OAK_WOOD,
            Material.STRIPPED_MANGROVE_WOOD, Material.STRIPPED_CHERRY_WOOD, Material.STRIPPED_PALE_OAK_WOOD
        }) {
            SMELTING_MAP_INNER.put(mat, new ItemStack(Material.CHARCOAL));
        }
    }

    /** Public immutable smelting map: raw material -> smelted result. */
    public static final Map<Material, ItemStack> SMELTING_MAP = Collections.unmodifiableMap(SMELTING_MAP_INNER);

    public Smelt(YinwuEnchantments plugin) {
        super(plugin, "smelt", "熔化", 1, new Material[]{
            // 镐
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
            Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            // 锹
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
            Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            // 斧
            Material.WOODEN_AXE, Material.STONE_AXE,
            Material.IRON_AXE, Material.GOLDEN_AXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            // 锄
            Material.WOODEN_HOE, Material.STONE_HOE,
            Material.IRON_HOE, Material.GOLDEN_HOE,
            Material.DIAMOND_HOE, Material.NETHERITE_HOE
        });
        this.plugin = plugin;
    }

    @Override
    public org.bukkit.enchantments.Enchantment[] getExclusiveEnchantments() {
        return new org.bukkit.enchantments.Enchantment[] { Enchantment.SILK_TOUCH };
    }

    @Override
    public Component displayName(int level) {
        return Component.text("熔化");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("smelt")) return;
        // 实时刷新主手，避免跨区域读背包（BlockDropItemEvent 在方块 region 触发）
        refreshTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, (task) -> {
                    var tool = player.getInventory().getItemInMainHand();
                    if (tool != null && !tool.getType().isAir()
                            && hasEnchantment(tool)
                            && !tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                        smeltPlayers.add(player.getUniqueId());
                    } else {
                        smeltPlayers.remove(player.getUniqueId());
                    }
                }, null);
            }
        }, 1L, 5L);
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        smeltPlayers.clear();
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            BlockDropItemEvent.class,
            this::onBlockDropItem
        );
    }

    /**
     * 方块掉落事件 —— 自动熔炼掉落物
     * BlockDropItemEvent 在方块被破坏、掉落物生成后触发
     */
    private void onBlockDropItem(BlockDropItemEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        List<Item> items = event.getItems();
        if (items.isEmpty()) return;

        // 只读缓存，不跨区域读主手
        if (!smeltPlayers.contains(player.getUniqueId())) return;

        boolean smelted = false;
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            Item entityItem = it.next();
            ItemStack stack = entityItem.getItemStack();
            if (stack == null || stack.getType().isAir()) continue;

            ItemStack smeltedResult = getSmeltedResult(stack);
            if (smeltedResult != null) {
                it.remove();
                smeltedResult.setAmount(stack.getAmount());
                event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5, 0.5, 0.5), smeltedResult);
                smelted = true;
            }
        }

        if (smelted && plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[熔化] 熔炼了 " + event.getBlock().getType().name());
        }
    }

    /**
     * 查找物品的熔炼产物
     * @param input 输入物品
     * @return 熔炼产物，若无对应配方则返回 null
     */
    private ItemStack getSmeltedResult(ItemStack input) {
        if (input == null) return null;
        ItemStack result = SMELTING_MAP.get(input.getType());
        if (result != null) {
            return result.clone();
        }
        return null;
    }
}
