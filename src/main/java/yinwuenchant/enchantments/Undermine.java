package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.BlockBreakEvent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Undermine extends CustomEnchantment {
    private final ConfigManager configManager;

    /** 主手带深层矿工的玩家缓存（玩家线程刷新，方块事件线程读取） */
    private final Map<UUID, Integer> playerLevels = new ConcurrentHashMap<>();
    private ScheduledTask refreshTask;

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
    public void onEnable() {
        // 实时刷新主手，避免缓存失效（换主手工具不触发 onEquipmentChange）
        refreshTask = plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, (t) -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.getScheduler().run(plugin, (task) -> {
                    var tool = player.getInventory().getItemInMainHand();
                    if (hasEnchantment(tool)) {
                        playerLevels.put(player.getUniqueId(), getEnchantmentLevel(tool));
                    } else {
                        playerLevels.remove(player.getUniqueId());
                    }
                }, null);
            }
        }, 1L, 5L);
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) refreshTask.cancel();
        playerLevels.clear();
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(BlockBreakEvent.class,
            event -> onBlockBreak(((BlockBreakEvent) event).getPlayer(), (BlockBreakEvent) event));
    }

    @Override
    public void onBlockBreak(Player player, BlockBreakEvent event) {
        if (!configManager.isEnchantmentEnabled("undermine")) return;
        // 只读缓存，不跨区域读主手（BlockBreakEvent 在方块 region 触发）
        Integer level = playerLevels.get(player.getUniqueId());
        if (level == null) return;

        // 忽视亮度，只按深度触发
        int y = event.getBlock().getY();
        int yMax = configManager.getInt("undermine.trigger-y-max");
        if (y > yMax) return;

        // 急迫随等级缩放（amp=level+2，5级=Haste VIII 配效率5下界合金镐秒破深板岩）
        int amplifier = level + 2;

        // Folia：玩家可能在相邻 region，药水效果调度到玩家线程
        player.getScheduler().run(plugin, (task) -> {
            if (player.isOnline() && !player.isDead()) {
                PotionEffect existing = player.getPotionEffect(PotionEffectType.HASTE);
                if (existing == null || existing.getDuration() <= 40 || existing.getAmplifier() < amplifier) {
                    player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE, 200, amplifier, true, false, false
                    ));
                }
            }
        }, null);
        event.getBlock().getWorld().playSound(
            event.getBlock().getLocation(),
            org.bukkit.Sound.BLOCK_STONE_BREAK, 0.5f, 0.8f);
    }
}
