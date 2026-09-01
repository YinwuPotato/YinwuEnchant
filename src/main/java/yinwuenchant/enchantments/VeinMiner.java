package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 连锁挖矿 —— 非潜行挖矿时连锁挖矿脉。
 * 以被挖方块为中心 BFS 遍历 26 邻域（含斜角）的相邻矿石（支持混合矿脉），最多 max-blocks（默认 32）块。
 *
 * Folia：BlockBreakEvent 在方块区域触发，不能直接读玩家背包 → 用 Smelt 式
 * 周期缓存玩家主手镐快照（含附魔），事件里用快照 breakNaturally（时运生效），
 * 再调度回玩家上下文扣实际工具耐久。与精准采集可共存（采到矿石方块）。
 * 注：连锁块不产生经验球（Bukkit Block 无 getExpDrop）。
 */
public class VeinMiner extends CustomEnchantment {

    private static final Material[] PICKAXES = {
        Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
        Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
        Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE
    };

    private static final Set<Material> ORES = Set.of(
        Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
        Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
        Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
        Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
        Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
        Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
        Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
        Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
        Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
        Material.ANCIENT_DEBRIS
    );

    /** 玩家 → 主手镐快照（含时运/附魔，Folia 跨区域安全） */
    private final Map<UUID, ItemStack> veinTools = new ConcurrentHashMap<>();
    private ScheduledTask refreshTask;

    /** 矿石 → 基础经验（连锁块补经验，原版 breakNaturally 不给 XP） */
    private static final Map<Material, Integer> ORE_XP = new HashMap<>();
    static {
        ORE_XP.put(Material.COAL_ORE, 1); ORE_XP.put(Material.DEEPSLATE_COAL_ORE, 1);
        ORE_XP.put(Material.DIAMOND_ORE, 5); ORE_XP.put(Material.DEEPSLATE_DIAMOND_ORE, 5);
        ORE_XP.put(Material.EMERALD_ORE, 5); ORE_XP.put(Material.DEEPSLATE_EMERALD_ORE, 5);
        ORE_XP.put(Material.LAPIS_ORE, 3); ORE_XP.put(Material.DEEPSLATE_LAPIS_ORE, 3);
        ORE_XP.put(Material.REDSTONE_ORE, 3); ORE_XP.put(Material.DEEPSLATE_REDSTONE_ORE, 3);
        ORE_XP.put(Material.NETHER_QUARTZ_ORE, 3);
        ORE_XP.put(Material.GOLD_ORE, 1); ORE_XP.put(Material.DEEPSLATE_GOLD_ORE, 1);
        ORE_XP.put(Material.NETHER_GOLD_ORE, 1);
    }

    public VeinMiner(YinwuEnchantments plugin) {
        super(plugin, "vein_miner", "连锁挖矿", 1, PICKAXES);
    }

    @Override
    public Component displayName(int level) {
        return Component.text("连锁挖矿");
    }

    // 2026-08-31: 与精准采集不互斥 —— 镐带精准采集时连锁挖矿用其效果（采到矿石方块）。
    // 精准采集 vs 时运 的互斥由原版处理，无需插件干预。

    @Override
    public void onEnable() {
        refreshTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.getScheduler().run(plugin, task -> {
                    if (!p.isOnline()) return;
                    ItemStack tool = p.getInventory().getItemInMainHand();
                    if (hasEnchantment(tool)) veinTools.put(p.getUniqueId(), tool.clone());
                    else veinTools.remove(p.getUniqueId());
                }, null);
            }
            // 清理已离线玩家（reload 安全）
            for (UUID uuid : veinTools.keySet()) {
                if (Bukkit.getPlayer(uuid) == null) veinTools.remove(uuid);
            }
        }, 1L, 5L);
    }

    @Override
    public void onDisable() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        veinTools.clear();
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(BlockBreakEvent.class, event -> {
            BlockBreakEvent e = (BlockBreakEvent) event;
            if (e.isCancelled()) return;
            Player player = e.getPlayer();
            // NeoEnchant：潜行时普通挖掘
            if (player.isSneaking()) return;

            Block origin = e.getBlock();
            if (!ORES.contains(origin.getType())) return;

            ItemStack tool = veinTools.get(player.getUniqueId());
            if (tool == null) return;

            int maxBlocks = plugin.getConfigManager().getInt("vein_miner.max-blocks");

            // 收集矿石（26 邻域：含斜角/棱/顶点；支持混合矿脉——相邻任意矿石都连锁）
            Set<Block> chain = new HashSet<>();
            ArrayDeque<Block> queue = new ArrayDeque<>();
            queue.add(origin);
            chain.add(origin);
            while (!queue.isEmpty() && chain.size() <= maxBlocks) {
                Block cur = queue.poll();
                for (Block n : neighbors26(cur)) {
                    if (chain.size() >= maxBlocks) break;
                    if (ORES.contains(n.getType()) && chain.add(n)) queue.add(n);
                }
            }
            chain.remove(origin); // 原方块由原版事件处理

            if (chain.isEmpty()) return;

            // 逐块 breakNaturally（方块区域线程，用快照的时运/附魔）
            for (Block b : chain) {
                b.breakNaturally(tool);
                // 补经验（原版 breakNaturally 不给矿石经验）
                Integer base = ORE_XP.get(b.getType());
                if (base != null && base > 0) {
                    int xp = base + ThreadLocalRandom.current().nextInt(base + 1);
                    b.getWorld().spawn(b.getLocation().add(0.5, 0.5, 0.5),
                        ExperienceOrb.class, orb -> orb.setExperience(xp));
                }
            }

            // 工具耐久扣减 → 调度回玩家上下文（方块区域不能改玩家背包）
            final int count = chain.size();
            player.getScheduler().run(plugin, task -> {
                if (!player.isOnline()) return;
                ItemStack held = player.getInventory().getItemInMainHand();
                if (!hasEnchantment(held)) return;
                if (held.getItemMeta() instanceof Damageable dmg) {
                    dmg.setDamage(Math.min(dmg.getDamage() + count, held.getType().getMaxDurability()));
                    held.setItemMeta(dmg);
                }
            }, null);
        });
    }

    /** 26 邻域（3×3×3 减去自身）：含面邻、棱邻、角邻（斜角） */
    private static Block[] neighbors26(Block b) {
        List<Block> list = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    list.add(b.getRelative(dx, dy, dz));
                }
            }
        }
        return list.toArray(new Block[0]);
    }
}
