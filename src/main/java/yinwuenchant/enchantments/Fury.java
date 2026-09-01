package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 狂怒 —— 牺牲护甲换增伤 + 破甲（取舍型）。
 * 装备任一盔甲含狂怒时：
 * - +攻击伤害（add_multiplied_total，0.075/级）
 * - -护甲（add_multiplied_total，0.10/级）
 * - 攻击时按 (0.045 + 0.035×(级-1)) 比例穿透目标护甲减伤
 * 移植自 NeoEnchant fury。
 */
public class Fury extends CustomEnchantment {

    private static final NamespacedKey ATTACK_KEY = NamespacedKey.fromString("yinwuenchant:fury_attack");
    private static final NamespacedKey ARMOR_KEY  = NamespacedKey.fromString("yinwuenchant:fury_armor");

    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    /** 破甲去重守卫：同一攻击 100ms 内只应用一次 */
    private final Map<String, Long> lastApplied = new ConcurrentHashMap<>();
    private ScheduledTask periodicTask;

    public Fury(YinwuEnchantments plugin) {
        super(plugin, "fury", "狂怒", 3, new Material[] {
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET,
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.CHAINMAIL_LEGGINGS,
            Material.IRON_LEGGINGS, Material.GOLDEN_LEGGINGS,
            Material.DIAMOND_LEGGINGS, Material.NETHERITE_LEGGINGS,
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS
        });
    }

    @Override
    public Component displayName(int level) {
        return Component.text("狂怒 " + getRomanNumeral(level));
    }

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

    @Override
    public void registerEventSubscribers() {
        // 攻击破甲
        plugin.getEnchantmentManager().subscribeEvent(EntityDamageByEntityEvent.class, event -> {
            EntityDamageByEntityEvent e = (EntityDamageByEntityEvent) event;
            if (e.isCancelled()) return;
            if (!(e.getDamager() instanceof Player player)) return;
            if (!(e.getEntity() instanceof LivingEntity target)) return;

            int level = plugin.getEnchantCache().getLevel(player.getUniqueId(), "fury");
            if (level <= 0) return;

            long now = System.currentTimeMillis();
            String key = player.getUniqueId() + "|" + target.getUniqueId();
            Long last = lastApplied.get(key);
            if (last != null && now - last < 100) return;
            lastApplied.put(key, now);

            double penetration = 0.045 + 0.035 * (level - 1);
            double bonus = ArmorPenetration.penetrationBonus(e.getDamage(), penetration, target);
            if (bonus > 0) e.setDamage(e.getDamage() + bonus);
        });
    }

    private void refreshStationary() {
        for (Player p : plugin.getServer().getOnlinePlayers()) {
            p.getScheduler().run(plugin, t -> {
                if (p.isOnline() && !p.isDead()) sync(p);
            }, null);
        }
    }

    private void sync(Player p) {
        int level = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            level = Math.max(level, getEnchantmentLevel(piece));
        }
        if (level > 0) {
            activePlayers.add(p.getUniqueId());
            applyModifiers(p, level);
        } else if (activePlayers.remove(p.getUniqueId())) {
            removeModifiers(p);
        }
    }

    private void applyModifiers(Player p, int level) {
        // 固定值加成（ADD_NUMBER，描述与实际一致）
        double attack = 5.0;
        double armor = -3.0;
        AttributeSupport.addModifier(p, Attribute.ATTACK_DAMAGE, ATTACK_KEY, "fury_attack",
            attack, AttributeModifier.Operation.ADD_NUMBER);
        AttributeSupport.addModifier(p, Attribute.ARMOR, ARMOR_KEY, "fury_armor",
            armor, AttributeModifier.Operation.ADD_NUMBER);
    }

    private void removeModifiers(Player p) {
        AttributeSupport.removeModifier(p, Attribute.ATTACK_DAMAGE, ATTACK_KEY);
        AttributeSupport.removeModifier(p, Attribute.ARMOR, ARMOR_KEY);
    }
}
