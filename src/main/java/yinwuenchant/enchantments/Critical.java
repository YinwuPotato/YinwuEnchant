package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 暴击 —— 概率破甲。
 * 每级 4% 概率触发，破甲 25%：把目标护甲减伤部分的 25% 转化为额外伤害（仅对穿甲目标有效）。
 * 移植自 NeoEnchant critical（armor_effectiveness 破甲）。
 */
public class Critical extends CustomEnchantment {

    public Critical(YinwuEnchantments plugin) {
        super(plugin, "critical", "暴击", 4, new Material[] {
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
        });
    }

    @Override
    public Component displayName(int level) {
        return Component.text("暴击 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    /** 去重守卫：同一攻击 100ms 内只应用一次（事件可能重触发） */
    private final Map<String, Long> lastApplied = new ConcurrentHashMap<>();

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(EntityDamageByEntityEvent.class, event -> {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.isCancelled()) return;
            if (!(e.getDamager() instanceof Player player)) return;
            if (!(e.getEntity() instanceof LivingEntity target)) return;

            int level = plugin.getEnchantCache().getLevel(player.getUniqueId(), "critical");
            if (level <= 0) return;

            long now = System.currentTimeMillis();
            String key = player.getUniqueId() + "|" + target.getUniqueId();
            Long last = lastApplied.get(key);
            if (last != null && now - last < 100) return;
            lastApplied.put(key, now);

            double chancePerLevel = plugin.getConfigManager().getDouble("critical.chance-per-level");
            if (ThreadLocalRandom.current().nextDouble() >= chancePerLevel * level) return;

            double penetration = plugin.getConfigManager().getDouble("critical.armor-penetration");
            double bonus = ArmorPenetration.penetrationBonus(e.getDamage(), penetration, target);
            if (bonus > 0) e.setDamage(e.getDamage() + bonus);
        });
    }
}
