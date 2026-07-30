package yinwuenchant.api;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import yinwuenchant.manager.EnchantmentAcquisitionManager;
import yinwuenchant.manager.EnchantmentManager;
import net.yinwu.lib.api.EnchantAPI;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class EnchantAPIImpl implements EnchantAPI {

    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private final EnchantmentAcquisitionManager acquisitionManager;

    public EnchantAPIImpl(YinwuEnchantments plugin, EnchantmentManager enchantmentManager,
                          EnchantmentAcquisitionManager acquisitionManager) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
        this.acquisitionManager = acquisitionManager;
    }

    @Override
    public String apiVersion() { return "1.0.0"; }

    @Override
    public List<String> getEnchantmentIds() {
        return List.of(enchantmentManager.getEnchantmentIds());
    }

    @Override
    public String getDisplayName(String enchantId) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantId);
        return ench != null ? ench.getDisplayName() : enchantId;
    }

    @Override
    public int getMaxLevel(String enchantId) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantId);
        return ench != null ? ench.getMaxLevel() : 0;
    }

    @Override
    public ItemStack createEnchantedBook(String enchantId, int level) {
        return acquisitionManager.createEnchantedBook(enchantId, level);
    }

    @Override
    public ItemStack applyEnchantment(ItemStack item, String enchantId, int level) {
        if (item == null || item.getType().isAir()) return item;
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantId);
        if (ench == null) return item;
        return ench.applyEnchantment(item, level);
    }

    @Override
    public boolean hasEnchantment(ItemStack item, String enchantId) {
        if (item == null || !item.hasItemMeta()) return false;
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantId);
        if (ench == null) return false;
        return ench.hasEnchantment(item);
    }

    @Override
    public int getEnchantmentLevel(ItemStack item, String enchantId) {
        if (item == null || !item.hasItemMeta()) return 0;
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantId);
        if (ench == null) return 0;
        return ench.getEnchantmentLevel(item);
    }

    @Override
    public List<EnchantmentInstance> getEnchantments(ItemStack item) {
        List<EnchantmentInstance> result = new ArrayList<>();
        if (item == null || !item.hasItemMeta()) return result;

        for (var entry : enchantmentManager.getAllEnchantments().entrySet()) {
            int level = entry.getValue().getEnchantmentLevel(item);
            if (level > 0) {
                result.add(new EnchantmentInstance(entry.getKey(), entry.getValue().getDisplayName(), level));
            }
        }
        return result;
    }
}
