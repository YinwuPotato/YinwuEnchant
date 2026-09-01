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
    /** 物品级禁用标记：disabled_<id> = BYTE 1 */
    protected final NamespacedKey disabledKey;

    /** 原版注册的附魔实例，由 EnchantmentManager 在启动时设置 */
    private Enchantment registered;

    public CustomEnchantment(YinwuEnchantments plugin, String id, String displayName, int maxLevel, Material[] applicableItems) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.applicableItems = applicableItems;
        this.enchantmentKey = new NamespacedKey(plugin, "enchantment_" + id.replace("-", "_"));
        this.disabledKey = new NamespacedKey(plugin, "disabled_" + id.replace("-", "_"));
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
    public NamespacedKey getEnchantmentKey() { return enchantmentKey; }

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
     * 以 PDC 为准（本服务端不支持 bootstrap 原版注册）。
     */
    public boolean hasEnchantment(ItemStack item) {
        return getEnchantmentLevel(item) > 0;
    }

    public int getEnchantmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        // 物品级禁用：附魔被关闭时视为无此附魔，效果层立即失效
        if (meta.getPersistentDataContainer().has(disabledKey, PersistentDataType.BYTE)) return 0;

        // PDC 为准
        Integer pdcLevel = meta.getPersistentDataContainer().get(
            enchantmentKey, PersistentDataType.INTEGER);
        if (pdcLevel != null && pdcLevel > 0) return pdcLevel;

        // 兼容旧原版注册（在支持 bootstrap 的服务器上）
        if (registered != null) {
            int level = item.getEnchantmentLevel(registered);
            if (level > 0) return level;
        }
        return 0;
    }

    public NamespacedKey getDisabledKey() { return disabledKey; }

    /**
     * 检测此附魔在物品上是否被禁用。
     * 禁用不影响 PDC level 与其他附魔，仅让本附魔效果失效。
     */
    public boolean isDisabled(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(disabledKey, PersistentDataType.BYTE);
    }

    /**
     * 直接读 PDC 等级，忽略禁用态（供 GUI 显示原始等级用）。
     */
    public int getRawEnchantmentLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;
        Integer pdcLevel = meta.getPersistentDataContainer().get(
            enchantmentKey, PersistentDataType.INTEGER);
        return pdcLevel != null && pdcLevel > 0 ? pdcLevel : 0;
    }

    /**
     * 设置此附魔在物品上的启用/禁用状态。
     * 仅精确作用于本附魔（PDC disabled_<id>），不动物品原生属性/其他附魔。
     * 变更后重建 Lore 并重算发光：全部禁用时去发光（恢复默认发光规则，原版附魔仍发光）。
     */
    public ItemStack setDisabled(ItemStack item, boolean disabled) {
        if (item == null || !item.hasItemMeta()) return item;
        if (isCursed() && disabled) return item;             // 诅咒无法关闭
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        var pdc = meta.getPersistentDataContainer();
        if (disabled) {
            pdc.set(disabledKey, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc.remove(disabledKey);
        }
        item.setItemMeta(meta);

        var all = plugin.getEnchantmentManager().getAllEnchantments().values();
        boolean anyEnabled = false;
        for (CustomEnchantment ench : all) {
            if (ench.getRawEnchantmentLevel(item) > 0 && !ench.isDisabled(item)) {
                anyEnabled = true;
                break;
            }
        }
        ItemMeta meta2 = item.getItemMeta();
        if (meta2 != null) {
            meta2.setEnchantmentGlintOverride(anyEnabled);
            yinwuenchant.manager.EnchantmentLore.rebuild(meta2, all);
            item.setItemMeta(meta2);
        }
        return item;
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

        // PDC 存储 + 发光 + Lore 显示
        meta.getPersistentDataContainer().remove(disabledKey); // 新附魔默认启用
        meta.getPersistentDataContainer().set(enchantmentKey, PersistentDataType.INTEGER, level);
        meta.setEnchantmentGlintOverride(true);
        yinwuenchant.manager.EnchantmentLore.rebuild(meta,
            plugin.getEnchantmentManager().getAllEnchantments().values());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack removeEnchantment(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (registered != null) {
            meta.removeEnchant(registered);
        }
        meta.getPersistentDataContainer().remove(enchantmentKey);
        yinwuenchant.manager.EnchantmentLore.rebuild(meta,
            plugin.getEnchantmentManager().getAllEnchantments().values());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * 与本附魔互斥的原版附魔（默认无）。
     * 子类覆盖以定义互斥关系，附魔台/铁砧应用时会跳过冲突项。
     */
    public org.bukkit.enchantments.Enchantment[] getExclusiveEnchantments() {
        return new org.bukkit.enchantments.Enchantment[0];
    }

    /** 是否为负面/诅咒附魔（默认 false）。诅咒附魔 lore 用红色、获取走负面高概率。 */
    public boolean isCursed() {
        return false;
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
    public void onPlayerDamage(org.bukkit.entity.Player player, org.bukkit.event.entity.EntityDamageEvent event) {}
    @SuppressWarnings("unused")
    public void onBlockBreak(org.bukkit.entity.Player player, org.bukkit.event.block.BlockBreakEvent event) {}
    @SuppressWarnings("unused")
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {}
}
