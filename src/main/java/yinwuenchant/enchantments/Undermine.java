package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Undermine extends CustomEnchantment {
    private final ConfigManager configManager;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    public Undermine(YinwuEnchantments plugin) {
        super(plugin, "undermine", "深层矿工", 5, new Material[] {
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
            Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_AXE, Material.STONE_AXE,
            Material.IRON_AXE, Material.GOLDEN_AXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
            Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override public Component displayName(int level) {
        return Component.text("深层矿工 " + getRomanNumeral(level));
    }

    @Override
    public void onEnable() {}

    @Override
    public void onDisable() { activePlayers.clear(); }

    @Override
    public void onEquipmentChange(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (hasEnchantment(tool)) activePlayers.add(player.getUniqueId());
        else activePlayers.remove(player.getUniqueId());
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(BlockBreakEvent.class,
            event -> onBlockBreak(((BlockBreakEvent) event).getPlayer(), (BlockBreakEvent) event));
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent event) {
        if (!configManager.isEnchantmentEnabled("undermine")) return;
        if (!activePlayers.contains(player.getUniqueId())) return;

        int y = event.getBlock().getY();
        int light = event.getBlock().getLightLevel();
        int yMax = configManager.getInt("undermine.trigger-y-max");
        int lightMax = configManager.getInt("undermine.trigger-light-max");

        if (y <= yMax && light <= lightMax) {
            PotionEffect existing = player.getPotionEffect(PotionEffectType.HASTE);
            if (existing == null || existing.getDuration() <= 40) {
                player.addPotionEffect(new PotionEffect(
                    PotionEffectType.HASTE, 200, 0, true, false, false
                ));
            }
            event.getBlock().getWorld().playSound(
                event.getBlock().getLocation(),
                org.bukkit.Sound.BLOCK_STONE_BREAK, 0.5f, 0.8f);
        }
    }
}
