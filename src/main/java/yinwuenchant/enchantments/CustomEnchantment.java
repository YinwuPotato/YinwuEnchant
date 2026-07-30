package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;


public abstract class CustomEnchantment {
    protected final YinwuEnchantments plugin;
    protected final String id;
    protected final String displayName;
    protected final int maxLevel;
    protected final Material[] applicableItems;
    protected final NamespacedKey enchantmentKey;

    /** 原版注册的附魔实例，由 EnchantmentManager 在启动时设置 */
    private Enchantment registered;

    public CustomEnchantment(YinwuEnchantments plugin, String id, String displayName, int maxLevel, Material[] applicableItems) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.applicableItems = applicableItems;
        this.enchantmentKey = new NamespacedKey(plugin, "enchantment_" + id.replace("-", "_"));
    }

    /** 由 EnchantmentManager 设置已注册的原版附魔实例 */
    public void setRegistered(Enchantment ench) {
        this.registered = ench;
    }

    public Enchantment getRegistered() {
        return registered;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public int getMaxLevel() { return maxLevel; }
    public Material[] getApplicableItems() { return applicableItems; }

    public boolean canApplyTo(ItemStack item) {
        if (item == null) return false;
        Material material = item.getType();
        for (Material applicable : applicableItems) {
            if (applicable == material) return true;
        }
        return false;
    }

    /**
     * 检测物品是否拥有此附魔。
     * 优先读原版附魔（新格式），回退到 PDC（旧格式兼容）。
     */
    public boolean hasEnchantment(ItemStack item) {
        return getEnchantmentLevel(item) > 0;
    }

    public int getEnchantmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        // 新格式：原版附魔（Paper 1.21.11 ItemMeta 无此方法，改用 ItemStack）
        if (registered != null) {
            int level = item.getEnchantmentLevel(registered);
            if (level > 0) return level;
        }

        // 旧格式：PDC 回退
        Integer pdcLevel = meta.getPersistentDataContainer().get(
            enchantmentKey, PersistentDataType.INTEGER);
        return pdcLevel != null ? pdcLevel : 0;
    }

    public ItemStack applyEnchantment(ItemStack item, int level) {
        if (item == null || level <= 0 || level > maxLevel) return item;
        if (!canApplyTo(item)) return item;
        // 灾厄强化后的物品禁止附魔
        var disasterKey = org.bukkit.NamespacedKey.fromString("yinwuraid:disaster_enhanced");
        if (disasterKey != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(disasterKey, org.bukkit.persistence.PersistentDataType.BYTE)) return item;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (registered != null) {
            // 新格式：原版附魔
            item.addEnchantment(registered, level);
            item.setItemMeta(meta);
        } else {
            // 回退：PDC（极少情况下 registered 为 null）
            meta.getPersistentDataContainer().set(
                enchantmentKey, PersistentDataType.INTEGER, level);
            meta.setEnchantmentGlintOverride(true);
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack removeEnchantment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (registered != null) {
            meta.removeEnchant(registered);
        }
        // 同时清理旧 PDC 格式
        meta.getPersistentDataContainer().remove(enchantmentKey);
        item.setItemMeta(meta);
        return item;
    }

    protected String getRomanNumeral(int number) {
        return switch (number) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III";
            case 4 -> "IV"; case 5 -> "V";
            default -> String.valueOf(number);
        };
    }

    // === 生命周期方法 ===
    public abstract void onEnable();
    public abstract void onDisable();
    public void registerEventSubscribers() {}
    public abstract Component displayName(int level);

    @SuppressWarnings("unused")
    public void onTick() {}
    @SuppressWarnings("unused")
    public void onPlayerMove(org.bukkit.entity.Player player) {}
    @SuppressWarnings("unused")
    public void onPlayerDamage(org.bukkit.entity.Player player, org.bukkit.event.entity.EntityDamageEvent event) {}
    @SuppressWarnings("unused")
    public void onBlockBreak(org.bukkit.entity.Player player, org.bukkit.event.block.BlockBreakEvent event) {}
    @SuppressWarnings("unused")
    public void onPlayerInteract(org.bukkit.entity.Player player, org.bukkit.event.player.PlayerInteractEvent event) {}
    @SuppressWarnings("unused")
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {}

    @SuppressWarnings("unused")
    public void onEquipmentChange(org.bukkit.entity.Player player) {}
}
