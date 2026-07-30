package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PhantomProtection extends CustomEnchantment {
    private final ConfigManager configManager;

    private final Set<UUID> protectionCache = ConcurrentHashMap.newKeySet();
    private double scanRange = 48.0;

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

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            for (UUID uuid : Set.copyOf(protectionCache)) {
                Player player = plugin.getServer().getPlayer(uuid);
                if (player == null || !player.isOnline()) { protectionCache.remove(uuid); continue; }
                player.getScheduler().run(plugin, t -> {
                    var flee = new FleeGoal();
                    flee.fleeFrom(player, plugin, Phantom.class, scanRange, 1.5, 40, null, null);
                }, null);
            }
        }, 1L, interval);
    }

    @Override
    public void onEquipmentChange(Player player) {
        var chest = player.getInventory().getChestplate();
        if (hasEnchantment(chest)) protectionCache.add(player.getUniqueId());
        else protectionCache.remove(player.getUniqueId());
    }

    @Override
    public void onDisable() { protectionCache.clear(); }

    @Override
    public void registerEventSubscribers() {}
}
