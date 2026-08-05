package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;

public class PhantomProtection extends CustomEnchantment {
    private final ConfigManager configManager;

    private double scanRange = 48.0;
    private final FleeGoal fleeGoal = new FleeGoal();

    public PhantomProtection(YinwuEnchantments plugin) {
        super(plugin, "phantom", "幻影", 1, new Material[] {
            Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_CHESTPLATE,
            Material.IRON_CHESTPLATE, Material.GOLDEN_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.NETHERITE_CHESTPLATE,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override public Component displayName(int level) { return Component.text("幻影"); }

    @Override
    public void onEnable() {
        if (!configManager.isEnchantmentEnabled("phantom")) return;
        scanRange = plugin.getConfig().getDouble("enchantments.phantom.scan-range", 48.0);
        int interval = plugin.getConfig().getInt("enchantments.phantom.scan-interval", 40);

        // 周期检测：实时读取玩家胸甲/鞘翅，有幻影附魔则驱赶周围幻翼
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, t -> {
                    var chest = player.getInventory().getChestplate();
                    if (hasEnchantment(chest)) {
                        fleeGoal.fleeFrom(player, plugin, Phantom.class, scanRange, 1.5, 40, null, null);
                    }
                }, null);
            }
        }, 1L, interval);
    }

    @Override
    public void onDisable() { fleeGoal.clear(); }

    @Override
    public void registerEventSubscribers() {}
}
