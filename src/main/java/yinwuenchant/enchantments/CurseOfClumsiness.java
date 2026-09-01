package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 笨拙诅咒 —— 削弱武器伤害。
 * 每级 -2 基础、之后每级再 -1：1级-2、2级-3、3级-4 攻击伤害。
 * 移植自 NeoEnchant curse_of_clumsiness（中文名修正了数据包里的错译）。
 */
public class CurseOfClumsiness extends CustomEnchantment {

    public CurseOfClumsiness(YinwuEnchantments plugin) {
        super(plugin, "curse_of_clumsiness", "笨拙诅咒", 3, new Material[] {
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
        });
    }

    @Override
    public Component displayName(int level) {
        return Component.text("笨拙诅咒 " + getRomanNumeral(level));
    }

    @Override
    public boolean isCursed() {
        return true;
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    /** 去重守卫：同一攻击（玩家→目标）100ms 内只应用一次，避免事件重触发/订阅者重复导致二次减伤 */
    private final Map<String, Long> lastApplied = new ConcurrentHashMap<>();

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(EntityDamageByEntityEvent.class, event -> {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.isCancelled()) return;
            if (!(e.getDamager() instanceof Player player)) return;
            if (!(e.getEntity() instanceof LivingEntity target)) return;

            int level = plugin.getEnchantCache().getLevel(player.getUniqueId(), "curse_of_clumsiness");
            if (level <= 0) return;

            // 同一攻击去重（事件可能重触发）
            long now = System.currentTimeMillis();
            String key = player.getUniqueId() + "|" + target.getUniqueId();
            Long last = lastApplied.get(key);
            if (last != null && now - last < 100) return;
            lastApplied.put(key, now);

            double reduction = 1 + level; // 1级-2 / 2级-3 / 3级-4
            // 保底至少 1 点伤害，避免 26.2 低基础伤害下被削到 0
            e.setDamage(Math.max(1, e.getDamage() - reduction));
        });
    }
}
