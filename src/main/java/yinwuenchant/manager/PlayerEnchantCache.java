package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家主手/护甲附魔等级缓存（Folia 安全）。
 *
 * EntityDamageByEntityEvent 在受害者区域线程触发，改伤害必须同步；
 * 攻击者的背包属于玩家区域，不能跨区域读取。因此周期性在玩家上下文
 * 读主手（critical/life_steal/poison_aspect/curse_of_clumsiness）与
 * 护甲（fury），缓存到线程安全 Map，事件处理器同步查缓存即可。
 */
public final class PlayerEnchantCache {

    private static final String[] HAND_IDS = {"critical", "life_steal", "poison_aspect", "curse_of_clumsiness"};
    private static final String FURY_ID = "fury";

    private final YinwuEnchantments plugin;
    private final Map<UUID, Map<String, Integer>> levels = new ConcurrentHashMap<>();
    private ScheduledTask refreshTask;

    public PlayerEnchantCache(YinwuEnchantments plugin) {
        this.plugin = plugin;
    }

    public void start() {
        refreshTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, t -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                p.getScheduler().run(plugin, task -> {
                    if (!p.isOnline()) return;
                    levels.put(p.getUniqueId(), snapshot(p));
                }, null);
            }
            // 清理已离线玩家（reload 后不依赖事件订阅，防泄漏）
            for (UUID uuid : levels.keySet()) {
                if (Bukkit.getPlayer(uuid) == null) levels.remove(uuid);
            }
        }, 1L, 5L);
    }

    private Map<String, Integer> snapshot(Player p) {
        Map<String, Integer> m = new HashMap<>();
        EnchantmentManager em = plugin.getEnchantmentManager();

        ItemStack hand = p.getInventory().getItemInMainHand();
        for (String id : HAND_IDS) {
            CustomEnchantment ench = em.getEnchantment(id);
            if (ench != null && ench.hasEnchantment(hand)) m.put(id, ench.getEnchantmentLevel(hand));
        }

        CustomEnchantment fury = em.getEnchantment(FURY_ID);
        if (fury != null) {
            int f = 0;
            for (ItemStack piece : p.getInventory().getArmorContents()) {
                f = Math.max(f, fury.getEnchantmentLevel(piece));
            }
            if (f > 0) m.put(FURY_ID, f);
        }
        return m;
    }

    /** 查询玩家某附魔当前等级（0 = 无） */
    public int getLevel(UUID uuid, String id) {
        return levels.getOrDefault(uuid, Map.of()).getOrDefault(id, 0);
    }

    public void stop() {
        if (refreshTask != null && !refreshTask.isCancelled()) refreshTask.cancel();
        refreshTask = null;
        levels.clear();
    }
}
