package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.enchantments.ShriekerSense;
import yinwuenchant.gui.EnchantmentGUI;
import yinwuenchant.gui.EnchantmentToggleGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class EventListener implements Listener {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private final EnchantmentToggleGUI toggleGui;
    private final EnchantmentGUI gui;

    public EventListener(YinwuEnchantments plugin, EnchantmentManager enchantmentManager,
                         EnchantmentToggleGUI toggleGui, EnchantmentGUI gui) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
        this.toggleGui = toggleGui;
        this.gui = gui;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onBlockDropItem(BlockDropItemEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerItemDamage(PlayerItemDamageEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onEntityResurrect(EntityResurrectEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        enchantmentManager.dispatchEvent(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 先 dispatch 给事件订阅者（CatsPaw、Nasus、StepUp、SonicBoom 等）
        enchantmentManager.dispatchEvent(event);

        // 玩家离线时清理 ShriekerSense 的 TextDisplay
        CustomEnchantment shriekerSense = enchantmentManager.getEnchantment("shrieker_sense");
        if (shriekerSense instanceof ShriekerSense) {
            ((ShriekerSense) shriekerSense).cleanupPlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        // 一级目录（三页）：左下角45=上一页、正中49=附魔开关、右下角53=下一页
        if (EnchantmentGUI.matches(title)) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            int page = EnchantmentGUI.currentPage(title);
            if (slot == 45) {
                int target = Math.max(1, page - 1);
                player.getScheduler().run(plugin, (t) -> gui.openPage(player, target), null);
            } else if (slot == 53) {
                int target = Math.min(3, page + 1);
                player.getScheduler().run(plugin, (t) -> gui.openPage(player, target), null);
            } else if (slot == 49) {
                player.getScheduler().run(plugin, (t) -> toggleGui.open(player), null);
            }
            return;
        }
        // 二级附魔开关
        if (EnchantmentToggleGUI.matches(title)) {
            toggleGui.handleClick(player, event);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        if (EnchantmentToggleGUI.matches(event.getView().getTitle())) {
            toggleGui.handleDrag(event);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (EnchantmentToggleGUI.matches(event.getView().getTitle())) {
            toggleGui.handleClose(player);
        }
    }
}
