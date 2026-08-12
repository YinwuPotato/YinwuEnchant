package yinwuenchant.gui;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.manager.ConfigManager;
import yinwuenchant.manager.EnchantmentManager;
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

public class EnchantmentGUI {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private final ConfigManager configManager;

    /** 每玩家图标循环任务（重开/关闭时取消，防累积） */
    private final Map<UUID, List<io.papermc.paper.threadedregions.scheduler.ScheduledTask>> cycleTasks =
        new ConcurrentHashMap<>();

    private static final String TITLE = ChatColor.GOLD + "Yinwu附魔列表";
    private static final int SIZE = 54;

    private static final Map<String, Integer> SLOTS = Map.ofEntries(
        // 第一行：头盔（标题格 0）
        Map.entry("clearsight", 1), Map.entry("nasus", 2),
        Map.entry("insomnia", 3), Map.entry("vampire_curse", 4),
        // 第二行：胸甲/鞘翅（标题格 9）
        Map.entry("sonic_boom", 10), Map.entry("bless", 11),
        Map.entry("phantom", 12), Map.entry("airbag", 13),
        // 第三行：护腿（标题格 18）
        Map.entry("safefall", 19),
        // 第四行：靴子（标题格 27）
        Map.entry("darkspeed", 28), Map.entry("cats_paw", 29),
        Map.entry("lava_walker", 30), Map.entry("step_up", 31),
        // 第五行：武器（标题格 36）
        Map.entry("master_of_beef_slicing", 37), Map.entry("resonate", 38),
        // 第六行：工具（标题格 45）
        Map.entry("undermine", 46), Map.entry("harvest", 47),
        Map.entry("smelt", 48), Map.entry("emerald_till", 49),
        Map.entry("shrieker_sense", 50), Map.entry("soulbound", 51)
    );

    /** 每行类别标题格位 */
    private static final Map<Integer, String> HEADERS = Map.of(
        0, "头盔附魔", 9, "胸甲/鞘翅附魔", 18, "护腿附魔",
        27, "靴子附魔", 36, "武器附魔", 45, "工具附魔"
    );

    /** 每行标题玻璃板颜色（各不相同） */
    private static final Map<Integer, Material> HEADER_COLORS = Map.of(
        0, Material.BLUE_STAINED_GLASS_PANE,
        9, Material.RED_STAINED_GLASS_PANE,
        18, Material.GREEN_STAINED_GLASS_PANE,
        27, Material.YELLOW_STAINED_GLASS_PANE,
        36, Material.ORANGE_STAINED_GLASS_PANE,
        45, Material.PURPLE_STAINED_GLASS_PANE
    );

    public EnchantmentGUI(YinwuEnchantments plugin, EnchantmentManager em, ConfigManager cm) {
        this.plugin = plugin; this.enchantmentManager = em; this.configManager = cm;
    }

    public void open(Player player) {
        // 取消该玩家上一次打开的循环任务（防累积）
        cancelCycles(player.getUniqueId());
        Inventory inv = org.bukkit.Bukkit.createInventory(null, SIZE, TITLE);
        String[] ids = enchantmentManager.getEnchantmentIds();

        // 玻璃板底色
        ItemStack filler = fillerItem();
        for (int i = 0; i < SIZE; i++) inv.setItem(i, filler);

        // 类别标题行
        HEADERS.forEach((slot, name) -> inv.setItem(slot, headerItem(slot, name)));

        // 附魔图标
        for (String id : ids) {
            Integer slot = SLOTS.get(id);
            if (slot == null) continue;
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench == null) continue;
            ItemStack item = buildItem(id, ench);
            if (item != null) inv.setItem(slot, item);
        }

        // 二级界面入口（slot 53）
        inv.setItem(53, toggleEntryItem());

        player.openInventory(inv);
        startCycles(player, ids);
    }

    private ItemStack fillerItem() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        if (fm != null) { fm.setDisplayName(" "); filler.setItemMeta(fm); }
        return filler;
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

    public static boolean matches(String title) { return TITLE.equals(title); }

    /** 二级界面入口按钮（点击打开单物品附魔开关，由 EventListener 处理） */
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
            Integer slot = SLOTS.get(id);
            if (slot == null) continue;
            startCycle(player, slot, id);
        }
    }

    private void startCycle(Player player, int slot, String id) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(id);
        if (ench == null) return;
        Material[] items = ench.getApplicableItems();
        if (items == null || items.length == 0) return;

        AtomicInteger idx = new AtomicInteger(ThreadLocalRandom.current().nextInt(items.length));
        io.papermc.paper.threadedregions.scheduler.ScheduledTask scheduled =
            player.getScheduler().runAtFixedRate(plugin, (task) -> {
            if (!player.isOnline() || !TITLE.equals(player.getOpenInventory().getTitle())) {
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
            case "step_up" -> Material.IRON_BOOTS; default -> Material.PAPER;
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
            case "step_up" -> "马蹄"; default -> id;
        };
    }

    static String materialChineseName(Material mat) {
        return switch (mat) {
            case LEATHER_HELMET -> "皮革头盔"; case CHAINMAIL_HELMET -> "锁链头盔";
            case IRON_HELMET -> "铁头盔"; case GOLDEN_HELMET -> "金头盔";
            case DIAMOND_HELMET -> "钻石头盔"; case NETHERITE_HELMET -> "下界合金头盔";
            case TURTLE_HELMET -> "海龟壳";
            case LEATHER_CHESTPLATE -> "皮革胸甲"; case CHAINMAIL_CHESTPLATE -> "锁链胸甲";
            case IRON_CHESTPLATE -> "铁胸甲"; case GOLDEN_CHESTPLATE -> "金胸甲";
            case DIAMOND_CHESTPLATE -> "钻石胸甲"; case NETHERITE_CHESTPLATE -> "下界合金胸甲";
            case LEATHER_LEGGINGS -> "皮革护腿"; case CHAINMAIL_LEGGINGS -> "锁链护腿";
            case IRON_LEGGINGS -> "铁护腿"; case GOLDEN_LEGGINGS -> "金护腿";
            case DIAMOND_LEGGINGS -> "钻石护腿"; case NETHERITE_LEGGINGS -> "下界合金护腿";
            case LEATHER_BOOTS -> "皮革靴子"; case CHAINMAIL_BOOTS -> "锁链靴子";
            case IRON_BOOTS -> "铁靴子"; case GOLDEN_BOOTS -> "金靴子";
            case DIAMOND_BOOTS -> "钻石靴子"; case NETHERITE_BOOTS -> "下界合金靴子";
            case WOODEN_PICKAXE -> "木镐"; case STONE_PICKAXE -> "石镐";
            case IRON_PICKAXE -> "铁镐"; case GOLDEN_PICKAXE -> "金镐";
            case DIAMOND_PICKAXE -> "钻石镐"; case NETHERITE_PICKAXE -> "下界合金镐";
            case WOODEN_AXE -> "木斧"; case STONE_AXE -> "石斧";
            case IRON_AXE -> "铁斧"; case GOLDEN_AXE -> "金斧";
            case DIAMOND_AXE -> "钻石斧"; case NETHERITE_AXE -> "下界合金斧";
            case WOODEN_SHOVEL -> "木锹"; case STONE_SHOVEL -> "石锹";
            case IRON_SHOVEL -> "铁锹"; case GOLDEN_SHOVEL -> "金锹";
            case DIAMOND_SHOVEL -> "钻石锹"; case NETHERITE_SHOVEL -> "下界合金锹";
            case SHIELD -> "盾牌";
            default -> mat.name().toLowerCase().replace("_", " ");
        };
    }

    private static String lore(String s) { return ChatColor.YELLOW + s.replaceFirst(": ", ": " + ChatColor.WHITE); }
}
