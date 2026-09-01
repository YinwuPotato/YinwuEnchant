package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 护腿类属性附魔基类（Dwarfed/Oversize 共用）。
 * 周期在玩家上下文读护腿，有附魔则挂属性修改器、脱掉/禁用则移除。
 */
public abstract class AbstractLeggingsEnchant extends CustomEnchantment {

    private static final Material[] LEGGINGS = {
        Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS,
        Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS,
        Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS
    };

    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private ScheduledTask periodicTask;

    protected AbstractLeggingsEnchant(YinwuEnchantments plugin, String id, String displayName, int maxLevel) {
        super(plugin, id, displayName, maxLevel, LEGGINGS);
    }

    /** 装备时应用属性修改器 */
    protected abstract void applyModifiers(Player p, int level);

    /** 脱下/禁用时移除属性修改器 */
    protected abstract void removeModifiers(Player p);

    @Override
    public void onEnable() {
        periodicTask = plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(plugin, t -> refreshStationary(), 1L, 10L);
    }

    @Override
    public void onDisable() {
        if (periodicTask != null && !periodicTask.isCancelled()) periodicTask.cancel();
        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.getScheduler().run(plugin, t -> removeModifiers(p), null);
            }
        }
        activePlayers.clear();
    }

    private void refreshStationary() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.getScheduler().run(plugin, t -> {
                if (p.isOnline() && !p.isDead()) sync(p);
            }, null);
        }
    }

    private void sync(Player p) {
        ItemStack leggings = p.getInventory().getLeggings();
        if (hasEnchantment(leggings)) {
            activePlayers.add(p.getUniqueId());
            applyModifiers(p, getEnchantmentLevel(leggings));
        } else if (activePlayers.remove(p.getUniqueId())) {
            removeModifiers(p);
        }
    }
}
