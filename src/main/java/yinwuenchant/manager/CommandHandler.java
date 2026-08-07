package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.gui.EnchantmentGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandHandler implements TabExecutor {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private final EnchantmentAcquisitionManager acquisitionManager;
    private final ConfigManager configManager;
    private final EnchantmentGUI gui;

    public CommandHandler(YinwuEnchantments plugin, EnchantmentManager em, EnchantmentAcquisitionManager am, ConfigManager cm) {
        this.plugin = plugin;
        this.enchantmentManager = em;
        this.acquisitionManager = am;
        this.configManager = cm;
        this.gui = new EnchantmentGUI(plugin, em, cm);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { sendHelp(sender); return true; }
        return switch (args[0].toLowerCase()) {
            case "give" -> adminCheck(sender) ? handleGiveCommand(sender, args) : true;
            case "givebook" -> adminCheck(sender) ? handleGiveBookCommand(sender, args) : true;
            case "reload" -> adminCheck(sender) ? handleReloadCommand(sender, args) : true;
            case "list" -> handleListCommand(sender);
            default -> { sendHelp(sender); yield true; }
        };
    }

    private boolean adminCheck(CommandSender s) {
        if (!s.hasPermission("yinwuenchant.admin")) {
            s.sendMessage(ChatColor.RED + "未知的子命令。请使用 /ye list 查看附魔列表。");
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> cmds = new ArrayList<>();
            if (sender.hasPermission("yinwuenchant.admin")) { cmds.add("give"); cmds.add("givebook"); cmds.add("reload"); }
            if (sender.hasPermission("yinwuenchant.use")) cmds.add("list");
            StringUtil.copyPartialMatches(args[0], cmds, completions);
        } else if (args.length == 2 && (args[0].equals("give") || args[0].equals("givebook"))) {
            for (Player p : Bukkit.getOnlinePlayers())
                if (StringUtil.startsWithIgnoreCase(p.getName(), args[1])) completions.add(p.getName());
        } else if (args.length == 3 && (args[0].equals("give") || args[0].equals("givebook"))) {
            StringUtil.copyPartialMatches(args[2], Arrays.asList(enchantmentManager.getEnchantmentIds()), completions);
        } else if (args.length == 4 && (args[0].equals("give") || args[0].equals("givebook"))) {
            int max = enchantmentManager.getMaxLevel(args[2]);
            if (max > 1) for (int i = 1; i <= max; i++)
                if (StringUtil.startsWithIgnoreCase(String.valueOf(i), args[3])) completions.add(String.valueOf(i));
        }
        return completions;
    }

    private boolean handleGiveCommand(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "用法: /ye give <玩家> <附魔> [等级]"); return true; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "玩家未找到: " + args[1]); return true; }
        String id = args[2].toLowerCase();
        CustomEnchantment ench = enchantmentManager.getEnchantment(id);
        if (ench == null) { sender.sendMessage(ChatColor.RED + "未知附魔: " + args[2]); return true; }
        int level = 1;
        if (args.length >= 4) {
            try { level = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                sender.sendMessage(ChatColor.RED + "无效等级: " + args[3]); return true; }
        }
        if (level < 1 || level > ench.getMaxLevel()) { sender.sendMessage(ChatColor.RED + "等级需在1-" + ench.getMaxLevel() + "之间"); return true; }
        // Folia R6：背包读取/校验/写入都必须在目标玩家实体线程执行
        int fl = level;
        target.getScheduler().run(plugin, (t) -> {
            if (!target.isOnline()) { sender.sendMessage(ChatColor.RED + "目标已下线"); return; }
            ItemStack item = target.getInventory().getItemInMainHand();
            if (item.getType() == Material.AIR) { sender.sendMessage(ChatColor.RED + "目标手中无物品"); return; }
            if (!ench.canApplyTo(item)) { sender.sendMessage(ChatColor.RED + "此附魔无法应用于该物品"); return; }
            target.getInventory().setItemInMainHand(ench.applyEnchantment(item, fl));
            String lv = ench.getMaxLevel() == 1 ? "" : " " + fl;
            sender.sendMessage(ChatColor.GREEN + "已将 " + ench.getDisplayName() + lv + " 应用于 " + target.getName());
            target.sendMessage(ChatColor.GREEN + "你的物品获得了 " + ench.getDisplayName() + lv);
        }, null);
        return true;
    }

    private boolean handleGiveBookCommand(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(ChatColor.RED + "用法: /ye givebook <玩家> <附魔> [等级]"); return true; }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "玩家未找到: " + args[1]); return true; }
        if (!target.isOnline()) { sender.sendMessage(ChatColor.RED + "目标已下线"); return true; }
        String id = args[2].toLowerCase();
        CustomEnchantment ench = enchantmentManager.getEnchantment(id);
        if (ench == null) { sender.sendMessage(ChatColor.RED + "未知附魔: " + args[2]); return true; }
        int level = 1;
        if (args.length >= 4) {
            try { level = Integer.parseInt(args[3]);
                if (level < 1 || level > ench.getMaxLevel()) { sender.sendMessage(ChatColor.RED + "等级需在1-" + ench.getMaxLevel()); return true; }
            } catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "无效等级"); return true; }
        }
        int fl = level;
        ItemStack book = acquisitionManager.createEnchantedBook(id, fl);
        if (book == null) { sender.sendMessage(ChatColor.RED + "创建附魔书失败"); return true; }
        target.getScheduler().run(plugin, (t) -> {
            target.getInventory().addItem(book);
            String lv = ench.getMaxLevel() == 1 ? "" : " " + fl;
            sender.sendMessage(ChatColor.GREEN + "已给予 " + target.getName() + " " + ench.getDisplayName() + lv + " 附魔书");
        }, null);
        return true;
    }

    private boolean handleReloadCommand(CommandSender sender, String[] args) {
        enchantmentManager.disableAll();
        configManager.reload();
        acquisitionManager.reload();
        enchantmentManager.enableAll();
        sender.sendMessage(ChatColor.GREEN + "配置已重载");
        return true;
    }

    private boolean handleListCommand(CommandSender sender) {
        if (!sender.hasPermission("yinwuenchant.use")) { sender.sendMessage(ChatColor.RED + "无权限"); return true; }
        if (!(sender instanceof Player p)) { sender.sendMessage(ChatColor.RED + "仅玩家可用"); return true; }
        gui.open(p);
        return true;
    }

    private void sendHelp(CommandSender s) {
        if (!s.hasPermission("yinwuenchant.use")) { s.sendMessage(ChatColor.RED + "无权限"); return; }
        s.sendMessage(ChatColor.GOLD + "=== YinwuEnchantments ===");
        s.sendMessage(ChatColor.YELLOW + "/ye list" + ChatColor.WHITE + " - 列出附魔");
        if (s.hasPermission("yinwuenchant.admin")) {
            s.sendMessage(ChatColor.YELLOW + "/ye give <玩家> <附魔> [等级]" + ChatColor.WHITE + " - 给予附魔物品");
            s.sendMessage(ChatColor.YELLOW + "/ye givebook <玩家> <附魔> [等级]" + ChatColor.WHITE + " - 给予附魔书");
            s.sendMessage(ChatColor.YELLOW + "/ye reload" + ChatColor.WHITE + " - 重载配置");
        }
    }
}
