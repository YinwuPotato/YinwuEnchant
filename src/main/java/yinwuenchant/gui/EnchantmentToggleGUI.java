package yinwuenchant.gui;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.manager.EnchantmentLore;
import yinwuenchant.manager.EnchantmentManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 二级界面：单物品自定义附魔独立开关（27 格优化版）。
 * slot 0 放入物品，右侧每个附魔占 1 格按钮（名称+状态），点击切换启停；
 * 诅咒类附魔显示锁定图标（BARRIER）不可关闭。
 * 对物品的修改直接写回输入格内的 ItemStack，关闭界面时归还玩家。
 */
public class EnchantmentToggleGUI {
    private static final String TITLE = ChatColor.GOLD + "附魔开关";
    private static final int SIZE = 27;
    private static final int INPUT_SLOT = 0;
    private static final int INSTRUCT_SLOT = SIZE - 1;

    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;

    /** 玩家 → 会话（当前打开的实例 + 按钮槽位映射） */
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    private static final class Session {
        final Inventory inv;
        /** 开关按钮槽位 → 附魔（诅咒不在其中，点击无效） */
        final Map<Integer, CustomEnchantment> toggleButtons = new HashMap<>();
        Session(Inventory inv) { this.inv = inv; }
    }

    public EnchantmentToggleGUI(YinwuEnchantments plugin, EnchantmentManager em) {
        this.plugin = plugin;
        this.enchantmentManager = em;
    }

    public static boolean matches(String title) { return title != null && title.contains("附魔开关"); }

    /** 打开二级界面（会先清理旧会话并归还旧物品） */
    public void open(Player player) {
        handleClose(player);
        Inventory inv = Bukkit.createInventory(null, SIZE, TITLE);
        sessions.put(player.getUniqueId(), new Session(inv));
        refreshEntries(sessions.get(player.getUniqueId()));
        player.openInventory(inv);
    }

    /** 点击处理（由 EventListener 在标题匹配时调用；运行于玩家线程） */
    public void handleClick(Player player, InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();
        Session session = sessions.get(player.getUniqueId());

        // shift 点击：手动把底部背包整组移入输入格（防止物品卡入顶部填充格）
        if (event.isShiftClick()) {
            event.setCancelled(true);
            if (session == null || rawSlot < SIZE) return;
            ItemStack bottom = event.getView().getBottomInventory().getItem(event.getSlot());
            if (bottom == null || bottom.getType() == Material.AIR) return;
            ItemStack input = session.inv.getItem(INPUT_SLOT);
            if (input != null && input.getType() != Material.AIR) {
                player.sendMessage(ChatColor.YELLOW + "请先取出输入格中的物品");
                return;
            }
            event.getView().getBottomInventory().setItem(event.getSlot(), null);
            session.inv.setItem(INPUT_SLOT, bottom);
            refreshEntries(session);
            return;
        }

        // 输入格：放行，事件后重扫（Folia 初始延迟 ≥1L）
        if (rawSlot == INPUT_SLOT) {
            scheduleRefresh(player);
            return;
        }
        // 底部玩家背包：普通点击放行
        if (rawSlot >= SIZE) return;

        // 顶部其他槽位：一律取消
        event.setCancelled(true);
        if (session == null) return;

        CustomEnchantment ench = session.toggleButtons.get(rawSlot);
        if (ench == null) return;   // 诅咒/填充格：点击无效

        ItemStack item = session.inv.getItem(INPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) return;
        boolean currentlyDisabled = ench.isDisabled(item);
        boolean wantDisabled = !currentlyDisabled;
        session.inv.setItem(INPUT_SLOT, ench.setDisabled(item, wantDisabled));
        refreshEntries(session);
    }

    /** 拖拽处理：禁止拖入顶部除输入格外区域 */
    public void handleDrag(InventoryDragEvent event) {
        boolean touchInput = false;
        for (int slot : event.getRawSlots()) {
            if (slot >= 1 && slot < SIZE) {
                event.setCancelled(true);
                return;
            }
            if (slot == INPUT_SLOT) touchInput = true;
        }
        if (touchInput && event.getWhoClicked() instanceof Player p) {
            scheduleRefresh(p);
        }
    }

    /** 关闭处理：归还输入格物品并清会话 */
    public void handleClose(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        ItemStack item = session.inv.getItem(INPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) return;
        session.inv.setItem(INPUT_SLOT, null);
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), rest);
        }
    }

    /** 事件后 1 tick 重扫条目（等物品移动完成后刷新） */
    private void scheduleRefresh(Player player) {
        player.getScheduler().runDelayed(plugin, (t) -> {
            if (!player.isOnline()) return;
            Session session = sessions.get(player.getUniqueId());
            if (session == null) return;
            if (!matches(player.getOpenInventory().getTitle())) return;
            refreshEntries(session);
        }, null, 1L);
    }

    /** 重建右侧条目区（每个附魔 1 格按钮，诅咒锁定不可点） */
    private void refreshEntries(Session session) {
        Inventory inv = session.inv;
        session.toggleButtons.clear();
        for (int i = 1; i < SIZE; i++) inv.setItem(i, filler());
        inv.setItem(INSTRUCT_SLOT, instructions());

        ItemStack item = inv.getItem(INPUT_SLOT);
        if (item == null || item.getType() == Material.AIR) {
            inv.setItem(4, emptyHint());
            return;
        }
        List<CustomEnchantment> present = new ArrayList<>();
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            if (ench.getRawEnchantmentLevel(item) > 0) present.add(ench);
        }
        if (present.isEmpty()) {
            inv.setItem(4, noEnchantHint());
            return;
        }
        int slot = 1;
        for (CustomEnchantment ench : present) {
            if (slot >= INSTRUCT_SLOT) break;   // 保留最后格给说明
            boolean disabled = ench.isDisabled(item);
            inv.setItem(slot, enchantButton(ench, ench.getRawEnchantmentLevel(item), disabled));
            if (!ench.isCursed()) {
                session.toggleButtons.put(slot, ench);
            }
            slot++;
        }
    }

    /** 附魔条目按钮（单格）：正常附魔可开关，诅咒显示锁定不可关闭 */
    private static ItemStack enchantButton(CustomEnchantment ench, int level, boolean disabled) {
        String base = EnchantmentGUI.chineseName(ench.getId());
        String levelSuffix = ench.getMaxLevel() > 1 ? " " + EnchantmentLore.roman(level) : "";
        ItemStack button;
        ItemMeta meta;
        if (ench.isCursed()) {
            button = new ItemStack(Material.BARRIER);
            meta = button.getItemMeta();
            if (meta == null) return button;
            meta.setDisplayName(ChatColor.RED + base + levelSuffix);
            meta.setLore(List.of(ChatColor.RED + "诅咒附魔，无法关闭"));
            button.setItemMeta(meta);
            return button;
        }
        button = new ItemStack(disabled ? Material.BOOK : Material.ENCHANTED_BOOK);
        meta = button.getItemMeta();
        if (meta == null) return button;
        meta.setDisplayName(disabled
            ? ChatColor.RED + base + levelSuffix + ChatColor.GRAY + "（已关闭）"
            : ChatColor.GREEN + base + levelSuffix);
        meta.setLore(List.of(
            disabled ? ChatColor.RED + "已关闭 · 点击开启" : ChatColor.GREEN + "已开启 · 点击关闭",
            ChatColor.DARK_GRAY + "ID: " + ench.getId()));
        button.setItemMeta(meta);
        return button;
    }

    private static ItemStack filler() {
        ItemStack f = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = f.getItemMeta();
        if (m != null) { m.setDisplayName(" "); f.setItemMeta(m); }
        return f;
    }

    private static ItemStack emptyHint() {
        ItemStack h = new ItemStack(Material.PAPER);
        ItemMeta m = h.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.YELLOW + "放入物品");
            m.setLore(List.of(
                ChatColor.GRAY + "将物品放入左侧第一格",
                ChatColor.GRAY + "自动检索其自定义附魔"));
            h.setItemMeta(m);
        }
        return h;
    }

    private static ItemStack noEnchantHint() {
        ItemStack h = new ItemStack(Material.PAPER);
        ItemMeta m = h.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.GRAY + "该物品没有自定义附魔");
            h.setItemMeta(m);
        }
        return h;
    }

    private static ItemStack instructions() {
        ItemStack s = new ItemStack(Material.OAK_SIGN);
        ItemMeta m = s.getItemMeta();
        if (m != null) {
            m.setDisplayName(ChatColor.AQUA + "操作说明");
            m.setLore(List.of(
                ChatColor.GRAY + "放入物品自动检索附魔",
                ChatColor.GRAY + "点击附魔按钮切换启停",
                ChatColor.RED + "诅咒附魔不可关闭",
                ChatColor.GRAY + "关闭界面后物品自动归还"));
            s.setItemMeta(m);
        }
        return s;
    }
}
