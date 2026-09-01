package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 毒素 —— 攻击使目标中毒。
 * 中毒等级 = 级-1（1级=中毒I），时长 (2×级+1) 秒。亡灵免疫毒（原版机制）。
 * 移植自 NeoEnchant poison_aspect。
 */
public class PoisonAspect extends CustomEnchantment {

    public PoisonAspect(YinwuEnchantments plugin) {
        super(plugin, "poison_aspect", "毒素", 3, new Material[] {
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
        });
    }

    @Override
    public Component displayName(int level) {
        return Component.text("毒素 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() {}

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(EntityDamageByEntityEvent.class, event -> {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.isCancelled()) return;
            if (!(e.getDamager() instanceof Player player)) return;
            if (!(e.getEntity() instanceof LivingEntity target)) return;

            int level = plugin.getEnchantCache().getLevel(player.getUniqueId(), "poison_aspect");
            if (level <= 0) return;

            int ticks = (2 * level + 1) * 20;
            int amplifier = Math.max(0, level - 1);
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, ticks, amplifier));
        });
    }
}
