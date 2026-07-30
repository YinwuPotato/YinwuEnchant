package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.enchantments.Darkspeed;
import yinwuenchant.enchantments.ShriekerSense;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class EventListener implements Listener {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;

    public EventListener(YinwuEnchantments plugin, EnchantmentManager enchantmentManager) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
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
    public void onEntityTarget(EntityTargetEvent event) {
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
    public void onPlayerItemBreak(PlayerItemBreakEvent event) {
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
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 先 dispatch 给事件订阅者（CatsPaw、Nasus、LavaWalker、StepUp 等）
        enchantmentManager.dispatchEvent(event);

        // 玩家离线时清理 ShriekerSense 的 TextDisplay
        CustomEnchantment shriekerSense = enchantmentManager.getEnchantment("shrieker_sense");
        if (shriekerSense instanceof ShriekerSense) {
            ((ShriekerSense) shriekerSense).cleanupPlayer(event.getPlayer().getUniqueId());
        }

        // 玩家离线时清理 Darkspeed 的速度缓存（防止内存泄漏）
        CustomEnchantment darkspeed = enchantmentManager.getEnchantment("darkspeed");
        if (darkspeed instanceof Darkspeed) {
            ((Darkspeed) darkspeed).cleanupPlayerCache(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        enchantmentManager.refreshAllCaches(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // GUI 锁定
        if (event.getView().getTitle().contains("Yinwu附魔列表")) {
            event.setCancelled(true);
            return;
        }
        // 装备变更时刷新所有附魔缓存（遍历所有装备槽读一次 PDC）
        if (event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
            // 延迟 1 tick 确保物品已生效（Folia 最小延迟）
            player.getScheduler().runDelayed(plugin, t -> {
                if (player.isOnline()) enchantmentManager.refreshAllCaches(player);
            }, null, 1L);
        }
    }
}
