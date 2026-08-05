package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StepUp extends CustomEnchantment {
    private final ConfigManager configManager;

    /** 穿着马蹄靴子的玩家缓存 */
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();

    private static final double DEFAULT_STEP = 0.6;
    private double stepHeightL1 = 1.0;
    private double stepHeightL2 = 1.25;
    private ScheduledTask periodicTask;

    public StepUp(YinwuEnchantments plugin) {
        super(plugin, "step_up", "马蹄", 2, new Material[] {
            Material.LEATHER_BOOTS, Material.CHAINMAIL_BOOTS,
            Material.IRON_BOOTS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_BOOTS, Material.NETHERITE_BOOTS,
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("马蹄 " + getRomanNumeral(level));
    }

    private void loadConfig() {
        var cfg = plugin.getConfig();
        stepHeightL1 = cfg.getDouble("enchantments.step_up.step-height-level-1", 1.0);
        stepHeightL2 = cfg.getDouble("enchantments.step_up.step-height-level-2", 1.25);
    }

    @Override
    public void onEnable() {
        loadConfig();
        periodicTask = plugin.getServer().getGlobalRegionScheduler()
            .runAtFixedRate(plugin, (t) -> refreshStationary(), 1L, 20L);
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerMoveEvent.class,
            event -> {
                PlayerMoveEvent e = (PlayerMoveEvent) event;
                handleMove(e);
            }
        );

        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.player.PlayerQuitEvent.class,
            event -> activePlayers.remove(event.getPlayer().getUniqueId())
        );
    }

    @Override
    public void onDisable() {
        if (periodicTask != null && !periodicTask.isCancelled()) {
            periodicTask.cancel();
        }
        for (UUID uuid : activePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.getScheduler().run(plugin, t -> resetStepHeight(player), null);
            }
        }
        activePlayers.clear();
    }

    private void refreshStationary() {
        // 实时读所有在线玩家靴子，站定时保底维持步高
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.getScheduler().run(plugin, task -> {
                if (player.isDead()) return;
                syncBoots(player);
            }, null);
        }
    }

    private void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isEnchantmentEnabled("step_up")) return;
        if (player.isDead()) return;
        syncBoots(player);
    }

    private void syncBoots(Player player) {
        var boots = player.getInventory().getBoots();
        if (hasEnchantment(boots)) {
            activePlayers.add(player.getUniqueId());
            applyStepHeight(player, getEnchantmentLevel(boots));
        } else if (activePlayers.remove(player.getUniqueId())) {
            resetStepHeight(player);
        }
    }

    private void applyStepHeight(Player player, int level) {
        double target = level == 1 ? stepHeightL1 : stepHeightL2;
        var attr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (attr != null && attr.getBaseValue() != target) {
            attr.setBaseValue(target);
        }
    }

    private void resetStepHeight(Player player) {
        if (player == null || !player.isOnline()) return;
        var attr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_STEP);
        }
    }
}
