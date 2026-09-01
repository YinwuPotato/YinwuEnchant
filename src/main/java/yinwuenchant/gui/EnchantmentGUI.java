package yinwuenchant.gui;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.manager.ConfigManager;
import yinwuenchant.manager.EnchantmentManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ItemFlag;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 附魔目录 GUI（三页，精确分类）。
 * 每页附魔集中在前 4 行（每行一个部位类别），第 5 行占位，第 6 行导航。
 * 导航：左下角(45)=上一页、正中(49)=附魔开关、右下角(53)=下一页。
 * 第 1 页：护甲（头盔/胸甲鞘翅/护腿/靴子）
 * 第 2 页：武器/工具/杂项
 * 第 3 页：诅咒
 */
public class EnchantmentGUI {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private final ConfigManager configManager;

    /** 每玩家图标循环任务（重开/关闭时取消，防累积） */
    private final Map<UUID, List<io.papermc.paper.threadedregions.scheduler.ScheduledTask>> cycleTasks =
        new ConcurrentHashMap<>();

    private static final String TITLE_BASE = ChatColor.GOLD + "Yinwu附魔列表";
    private static final String TITLE_PAGE1 = TITLE_BASE + " §8(1/3)";
    private static final String TITLE_PAGE2 = TITLE_BASE + " §8(2/3)";
    private static final String TITLE_PAGE3 = TITLE_BASE + " §8(3/3)";
    private static final int SIZE = 54;
    private static final int PREV_SLOT = 45;   // 左下角
    private static final int TOGGLE_SLOT = 49; // 正中
    private static final int NEXT_SLOT = 53;   // 右下角

    /** 第 1 页：护甲（12 个，第 2~5 行按部位分类，第 1 行占位） */
    private static final Map<String, Integer> SLOTS_PAGE1 = Map.ofEntries(
        // 第 2 行：头盔
        Map.entry("clearsight", 10), Map.entry("nasus", 11),
        // 第 3 行：胸甲/鞘翅
        Map.entry("sonic_boom", 19), Map.entry("bless", 20), Map.entry("phantom", 21),
        Map.entry("airbag", 22), Map.entry("fury", 23),
        // 第 4 行：护腿
        Map.entry("safefall", 28),
        // 第 5 行：靴子
        Map.entry("darkspeed", 37), Map.entry("cats_paw", 38),
        Map.entry("lava_walker", 39), Map.entry("step_up", 40)
    );

    /** 第 2 页：武器/工具/盾牌杂项（15 个，第 2~5 行，剑与弓弩分开） */
    private static final Map<String, Integer> SLOTS_PAGE2 = Map.ofEntries(
        // 第 2 行：近战武器（剑）
        Map.entry("master_of_beef_slicing", 10), Map.entry("critical", 11),
        Map.entry("life_steal", 12), Map.entry("poison_aspect", 13),
        // 第 3 行：远程武器（弓/弩）
        Map.entry("echo_shot", 19), Map.entry("storm_arrow", 20),
        Map.entry("explosive_arrow", 21),
        // 第 4 行：工具
        Map.entry("undermine", 28), Map.entry("harvest", 29),
        Map.entry("smelt", 30), Map.entry("emerald_till", 31),
        Map.entry("shrieker_sense", 32), Map.entry("vein_miner", 33),
        // 第 5 行：盾牌/杂项
        Map.entry("resonate", 37), Map.entry("soulbound", 38)
    );

    /** 第 3 页：诅咒（7 个，第 2~5 行） */
    private static final Map<String, Integer> SLOTS_PAGE3 = Map.ofEntries(
        Map.entry("vampire_curse", 10), Map.entry("insomnia", 11),
        Map.entry("dwarfed", 19), Map.entry("oversize", 20),
        Map.entry("curse_of_clumsiness", 28),
        Map.entry("curse_of_breaking", 37), Map.entry("curse_of_enchant", 38)
    );

    /** 每页类别标题（行起始格位 → 文本，第 1 行留空为占位） */
    private static final Map<Integer, String> HEADERS_PAGE1 = Map.of(
        9, "头盔附魔", 18, "胸甲/鞘翅附魔", 27, "护腿附魔", 36, "靴子附魔"
    );
    private static final Map<Integer, String> HEADERS_PAGE2 = Map.of(
        9, "近战武器", 18, "远程武器（弓/弩）", 27, "工具", 36, "盾牌/杂项"
    );
    private static final Map<Integer, String> HEADERS_PAGE3 = Map.of(
        9, "头盔诅咒", 18, "护腿诅咒", 27, "武器诅咒", 36, "工具诅咒"
    );

    /** 行起始格位 → 标题玻璃板颜色（第 2~5 行） */
    private static final Map<Integer, Material> HEADER_COLORS = Map.of(
        9, Material.BLUE_STAINED_GLASS_PANE,
        18, Material.RED_STAINED_GLASS_PANE,
        27, Material.GREEN_STAINED_GLASS_PANE,
        36, Material.YELLOW_STAINED_GLASS_PANE
    );

    public EnchantmentGUI(YinwuEnchantments plugin, EnchantmentManager em, ConfigManager cm) {
        this.plugin = plugin; this.enchantmentManager = em; this.configManager = cm;
    }

    /** 打开第 1 页（护甲） */
    public void open(Player player) {
        openPage(player, 1);
    }

    public void openPage(Player player, int page) {
        cancelCycles(player.getUniqueId());
        Map<String, Integer> slots = switch (page) {
            case 2 -> SLOTS_PAGE2;
            case 3 -> SLOTS_PAGE3;
            default -> SLOTS_PAGE1;
        };
        Map<Integer, String> headers = switch (page) {
            case 2 -> HEADERS_PAGE2;
            case 3 -> HEADERS_PAGE3;
            default -> HEADERS_PAGE1;
        };
        String title = switch (page) {
            case 2 -> TITLE_PAGE2;
            case 3 -> TITLE_PAGE3;
            default -> TITLE_PAGE1;
        };

        Inventory inv = org.bukkit.Bukkit.createInventory(null, SIZE, title);

        // 全背景占位符
        ItemStack placeholder = placeholderItem();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, placeholder);

        // 类别标题行
        headers.forEach((slot, name) -> inv.setItem(slot, headerItem(slot, name)));

        // 附魔图标
        for (Map.Entry<String, Integer> e : slots.entrySet()) {
            CustomEnchantment ench = enchantmentManager.getEnchantment(e.getKey());
            if (ench == null) continue;
            ItemStack item = buildItem(e.getKey(), ench);
            if (item != null) inv.setItem(e.getValue(), item);
        }

        // 导航行：左下角上一页 / 正中附魔开关 / 右下角下一页
        inv.setItem(PREV_SLOT, prevPageItem());
        inv.setItem(TOGGLE_SLOT, toggleEntryItem());
        inv.setItem(NEXT_SLOT, nextPageItem());

        player.openInventory(inv);
        startCycles(player, slots.keySet().toArray(new String[0]));
    }

    /** 从标题解析当前页码（1/2/3） */
    public static int currentPage(String title) {
        if (title == null) return 1;
        int i = title.indexOf('(');
        if (i >= 0) {
            int j = title.indexOf('/', i);
            if (j > i) {
                try { return Integer.parseInt(title.substring(i + 1, j).trim()); } catch (NumberFormatException ignored) {}
            }
        }
        return 1;
    }

    /** 背景占位符 */
    private ItemStack placeholderItem() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.setDisplayName(" "); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack headerItem(int slot, String name) {
        ItemStack item = new ItemStack(HEADER_COLORS.getOrDefault(slot, Material.LIGHT_BLUE_STAINED_GLASS_PANE));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "✦ " + name);
            meta.setLore(List.of(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 左下角：上一页 */
    private ItemStack prevPageItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "◂ 上一页");
            meta.setLore(List.of(ChatColor.GRAY + "返回上一页附魔"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 右下角：下一页 */
    private ItemStack nextPageItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "下一页 ▸");
            meta.setLore(List.of(ChatColor.GRAY + "查看下一页附魔"));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** 正中：附魔开关入口 */
    private ItemStack toggleEntryItem() {
        ItemStack item = new ItemStack(Material.LEVER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.YELLOW + "附魔开关");
            meta.setLore(List.of(ChatColor.GRAY + "点击管理物品上的自定义附魔"));
            item.setItemMeta(meta);
        }
        return item;
    }

    public static boolean matches(String title) {
        return title != null && title.startsWith(TITLE_BASE);
    }

    private ItemStack buildItem(String id, CustomEnchantment ench) {
        Material mat = displayMaterial(id);
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        boolean en = configManager.isEnchantmentEnabled(id);
        ChatColor c = en ? ChatColor.LIGHT_PURPLE : ChatColor.RED;
        meta.setDisplayName(c + chineseName(id));
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.setLore(buildLore(id, ench));
        item.setItemMeta(meta);
        return item;
    }

    private List<String> buildLore(String id, CustomEnchantment ench) {
        List<String> l = new ArrayList<>();
        l.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
        // lore per enchantment
        switch (id) {
            case "airbag" -> { l.add(lore("适用物品: 鞘翅")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 减少飞行撞墙伤害")); l.add(lore("触发方式: 自动")); }
            case "bless" -> { l.add(lore("适用物品: 胸甲")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 触发不死图腾后传送回重生点")); }
            case "clearsight" -> { l.add(lore("适用物品: 头盔")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 无视黑暗Buff")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "darkspeed" -> { l.add(lore("适用物品: 鞋子")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 黑暗区域增加移动速度")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "resonate" -> { l.add(lore("适用物品: 盾牌")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 反弹所格挡的攻击")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "safefall" -> { l.add(lore("适用物品: 护腿")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 减免摔落伤害")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "shrieker_sense" -> { l.add(lore("适用物品: 望远镜")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 探测周围的尖啸体与监守者")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "sonic_boom" -> { l.add(lore("适用物品: 胸甲")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 蹲下蓄力发射音波")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "undermine" -> { l.add(lore("适用物品: 镐/锹/斧")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 海平面以下挖掘加速")); l.add(lore("附魔来源: 幽匿维度探索")); }
            case "cats_paw" -> { l.add(lore("适用物品: 鞋子")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 周期性恐吓苦力怕")); l.add(lore("附魔来源: 击杀猪灵")); }
            case "nasus" -> { l.add(lore("适用物品: 头盔")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 周期性恐吓骷髅类怪物")); l.add(lore("附魔来源: 击杀潜影贝")); }
            case "master_of_beef_slicing" -> { l.add(lore("适用物品: 剑")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 攻击生物额外掉落肉类")); l.add(lore("附魔来源: 击杀掠夺者/卫道士")); }
            case "phantom" -> { l.add(lore("适用物品: 胸甲/鞘翅")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 防止幻翼攻击")); l.add(lore("附魔来源: 击杀幻翼")); }
            case "harvest" -> { l.add(lore("适用物品: 锄头")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 右键收获成熟作物")); l.add(lore("附魔来源: 钓鱼")); }
            case "insomnia" -> { l.add(lore("适用物品: 头盔")); l.add(ChatColor.RED + "负面附魔"); l.add(lore("附魔效果: 无法入睡，积累未睡眠天数")); }
            case "soulbound" -> { l.add(lore("适用物品: 武器/工具/盔甲/盾牌/鞘翅")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 死亡时保留附魔物品")); l.add(lore("附魔来源: 击杀幻术师")); }
            case "smelt" -> { l.add(lore("适用物品: 镐/锹/斧/锄")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 自动熔炼挖掘的方块")); l.add(lore("兼容附魔: 时运")); }
            case "lava_walker" -> { l.add(lore("适用物品: 靴子")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 将熔岩临时替换为岩浆块")); }
            case "emerald_till" -> { l.add(lore("适用物品: 锄头")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 破坏草丛概率掉落绿宝石")); }
            case "step_up" -> { l.add(lore("适用物品: 靴子")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 提高步高，走上1格高方块")); }
            case "vampire_curse" -> { l.add(lore("适用物品: 头盔")); l.add(ChatColor.RED + "⚠ 负面附魔"); l.add(lore("附魔效果: 白天燃烧，夜晚回复+抗性")); }
            // ==== NeoEnchant 移植 ====
            case "vein_miner" -> { l.add(lore("适用物品: 镐")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 非潜行挖掘时连锁挖同矿")); l.add(lore("兼容附魔: 时运")); }
            case "critical" -> { l.add(lore("适用物品: 剑")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 概率破甲25%")); }
            case "life_steal" -> { l.add(lore("适用物品: 剑")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 攻击命中回复生命")); }
            case "fury" -> { l.add(lore("适用物品: 盔甲")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 减护甲换增伤+破甲")); }
            case "poison_aspect" -> { l.add(lore("适用物品: 剑")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 攻击使目标中毒")); }
            case "echo_shot" -> { l.add(lore("适用物品: 弓/弩")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 箭命中产生音爆AOE")); }
            case "storm_arrow" -> { l.add(lore("适用物品: 弓/弩")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 箭命中召唤闪电")); }
            case "explosive_arrow" -> { l.add(lore("适用物品: 弓/弩")); l.add(lore("最高等级: " + ench.getMaxLevel())); l.add(lore("附魔效果: 箭命中产生爆炸")); }
            case "curse_of_breaking" -> { l.add(lore("适用物品: 耐久类")); l.add(ChatColor.RED + "⚠ 负面附魔"); l.add(lore("附魔效果: 耐久损耗加快")); }
            case "curse_of_enchant" -> { l.add(lore("适用物品: 耐久类")); l.add(ChatColor.RED + "⚠ 负面附魔"); l.add(lore("附魔效果: 无法再附魔/铁砧修改")); }
            case "curse_of_clumsiness" -> { l.add(lore("适用物品: 剑")); l.add(ChatColor.RED + "⚠ 负面附魔"); l.add(lore("附魔效果: 削弱武器伤害")); }
            case "dwarfed" -> { l.add(lore("适用物品: 护腿")); l.add(ChatColor.RED + "⚠ 负面附魔"); l.add(lore("附魔效果: 体型缩小+攻击削弱")); }
            case "oversize" -> { l.add(lore("适用物品: 护腿")); l.add(ChatColor.RED + "⚠ 负面附魔"); l.add(lore("附魔效果: 体型变大")); }
            default -> {
                l.add(lore("ID: " + ench.getId()));
                l.add(lore("最大等级: " + ench.getMaxLevel()));
            }
        }
        l.add(ChatColor.DARK_GRAY + "ID: " + id);
        l.add(configManager.isEnchantmentEnabled(id) ? ChatColor.GREEN + "状态: 已启用" : ChatColor.RED + "状态: 已禁用");
        l.add(ChatColor.GRAY + "━━━━━━━━━━━━━━━━━━━━");
        return l;
    }

    private void startCycles(Player player, String[] ids) {
        for (String id : ids) {
            Integer slot = slotOf(id);
            if (slot == null) continue;
            startCycle(player, slot, id);
        }
    }

    private static Integer slotOf(String id) {
        Integer s = SLOTS_PAGE1.get(id);
        if (s != null) return s;
        s = SLOTS_PAGE2.get(id);
        if (s != null) return s;
        return SLOTS_PAGE3.get(id);
    }

    private void startCycle(Player player, int slot, String id) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(id);
        if (ench == null) return;
        Material[] items = ench.getApplicableItems();
        if (items == null || items.length == 0) return;

        AtomicInteger idx = new AtomicInteger(ThreadLocalRandom.current().nextInt(items.length));
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
            player.getScheduler().runAtFixedRate(plugin, (task) -> {
            if (!player.isOnline() || !matches(player.getOpenInventory().getTitle())) {
                task.cancel();
                cycleTasks.computeIfPresent(player.getUniqueId(), (k, list) -> {
                    list.remove(task);
                    return list.isEmpty() ? null : list;
                });
                return;
            }
            int i = idx.getAndIncrement() % items.length;
            ItemStack ni = new ItemStack(items[i]);
            ItemMeta m = ni.getItemMeta();
            if (m == null) return;
            boolean en = configManager.isEnchantmentEnabled(id);
            m.setDisplayName((en ? ChatColor.GREEN : ChatColor.RED) + chineseName(id));
            m.setLore(buildLore(id, ench));
            m.addEnchant(Enchantment.UNBREAKING, 1, true);
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            ni.setItemMeta(m);
            player.getOpenInventory().setItem(slot, ni);
        }, null, 1L, 20L);
        cycleTasks.computeIfAbsent(player.getUniqueId(), k -> new CopyOnWriteArrayList<>()).add(scheduled);
    }

    /** 取消指定玩家的所有图标循环任务 */
    public void cancelCycles(UUID uuid) {
        List<io.papermc.paper.threadedregions.scheduler.ScheduledTask> tasks = cycleTasks.remove(uuid);
        if (tasks != null) {
            for (io.papermc.paper.threadedregions.scheduler.ScheduledTask t : tasks) {
                if (t != null && !t.isCancelled()) t.cancel();
            }
        }
    }

    private static Material displayMaterial(String id) {
        return switch (id) {
            case "clearsight" -> Material.CARVED_PUMPKIN; case "darkspeed" -> Material.DIAMOND_BOOTS;
            case "resonate" -> Material.SHIELD; case "safefall" -> Material.DIAMOND_LEGGINGS;
            case "shrieker_sense" -> Material.SPYGLASS; case "sonic_boom" -> Material.DIAMOND_CHESTPLATE;
            case "undermine" -> Material.IRON_PICKAXE; case "cats_paw" -> Material.CREEPER_HEAD;
            case "nasus" -> Material.SKELETON_SKULL; case "master_of_beef_slicing" -> Material.COOKED_BEEF;
            case "phantom" -> Material.PHANTOM_MEMBRANE; case "harvest" -> Material.GOLDEN_HOE;
            case "soulbound" -> Material.TOTEM_OF_UNDYING; case "airbag" -> Material.ELYTRA;
            case "bless" -> Material.TOTEM_OF_UNDYING; case "vampire_curse" -> Material.ZOMBIE_HEAD;
            case "insomnia" -> Material.CLOCK; case "smelt" -> Material.FURNACE;
            case "lava_walker" -> Material.MAGMA_BLOCK; case "emerald_till" -> Material.DIAMOND_HOE;
            case "step_up" -> Material.IRON_BOOTS;
            // NeoEnchant 移植
            case "vein_miner" -> Material.DIAMOND_PICKAXE; case "critical" -> Material.DIAMOND_SWORD;
            case "life_steal" -> Material.NETHERITE_SWORD; case "fury" -> Material.NETHERITE_CHESTPLATE;
            case "poison_aspect" -> Material.POISONOUS_POTATO; case "echo_shot" -> Material.BOW;
            case "storm_arrow" -> Material.LIGHTNING_ROD; case "explosive_arrow" -> Material.TNT;
            case "curse_of_breaking" -> Material.CHIPPED_ANVIL; case "curse_of_enchant" -> Material.ENCHANTED_BOOK;
            case "curse_of_clumsiness" -> Material.WOODEN_SWORD; case "dwarfed" -> Material.RABBIT_FOOT;
            case "oversize" -> Material.ANVIL; default -> Material.PAPER;
        };
    }

    static String chineseName(String id) {
        return switch (id) {
            case "clearsight" -> "明目"; case "darkspeed" -> "黑暗行者";
            case "resonate" -> "共振"; case "safefall" -> "外骨骼";
            case "shrieker_sense" -> "幽匿探测"; case "sonic_boom" -> "音波爆裂";
            case "undermine" -> "深层矿工"; case "cats_paw" -> "猫爪";
            case "nasus" -> "狗头"; case "master_of_beef_slicing" -> "切肉大师";
            case "phantom" -> "幻影"; case "harvest" -> "丰收";
            case "airbag" -> "气囊"; case "bless" -> "护佑";
            case "vampire_curse" -> "吸血鬼诅咒"; case "insomnia" -> "失眠";
            case "soulbound" -> "灵魂绑定"; case "smelt" -> "熔化";
            case "lava_walker" -> "熔岩行者"; case "emerald_till" -> "拾翠";
            case "step_up" -> "马蹄";
            // NeoEnchant 移植
            case "vein_miner" -> "连锁挖矿"; case "critical" -> "暴击";
            case "life_steal" -> "生命汲取"; case "fury" -> "狂怒";
            case "poison_aspect" -> "毒素"; case "echo_shot" -> "回声射击";
            case "storm_arrow" -> "风暴之箭"; case "explosive_arrow" -> "爆炸之箭";
            case "curse_of_breaking" -> "脆弱诅咒"; case "curse_of_enchant" -> "附魔诅咒";
            case "curse_of_clumsiness" -> "笨拙诅咒"; case "dwarfed" -> "矮人化";
            case "oversize" -> "巨人化"; default -> id;
        };
    }

    private static String lore(String s) { return ChatColor.YELLOW + s.replaceFirst(": ", ": " + ChatColor.WHITE); }
}
