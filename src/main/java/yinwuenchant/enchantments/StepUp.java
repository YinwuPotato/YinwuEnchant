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
    public void onEquipmentChange(Player player) {
        var boots = player.getInventory().getBoots();
        if (hasEnchantment(boots)) {
            activePlayers.add(player.getUniqueId());
            int level = getEnchantmentLevel(boots);
            applyStepHeight(player, level);
        } else if (activePlayers.remove(player.getUniqueId())) {
            resetStepHeight(player.getUniqueId(), player);
        }
    }

    @Override
    public void onDisable() {
        if (periodicTask != null && !periodicTask.isCancelled()) {
            periodicTask.cancel();
        }
        for (UUID uuid : activePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                UUID id = uuid;
                player.getScheduler().run(plugin, t -> resetStepHeight(id, player), null);
            }
        }
        activePlayers.clear();
    }

    private void refreshStationary() {
        for (UUID uuid : activePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                activePlayers.remove(uuid);
                continue;
            }
            player.getScheduler().run(plugin, task -> {
                if (player.isDead()) {
                    resetStepHeight(uuid, player);
                    activePlayers.remove(uuid);
                    return;
                }
                // 缓存由 onEquipmentChange 维护，保底维持步高
                applyStepHeight(player, 1);
            }, null);
        }
    }

    private void handleMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!configManager.isEnchantmentEnabled("step_up")) return;
        if (player.isDead()) return;

        // 只查缓存，不读PDC
        if (!activePlayers.contains(player.getUniqueId())) return;
        // 步高已在 onEquipmentChange 时设置，无需重复
    }

    private void applyStepHeight(Player player, int level) {
        double target = level == 1 ? stepHeightL1 : stepHeightL2;
        var attr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (attr != null && attr.getBaseValue() != target) {
            attr.setBaseValue(target);
        }
    }

    private void resetStepHeight(UUID uuid, Player player) {
        if (player == null || !player.isOnline()) return;
        var attr = player.getAttribute(Attribute.STEP_HEIGHT);
        if (attr != null) {
            attr.setBaseValue(DEFAULT_STEP);
        }
    }

    public void cleanup() {
        if (periodicTask != null && !periodicTask.isCancelled()) {
            periodicTask.cancel();
        }
        for (UUID uuid : activePlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                UUID id = uuid;
                player.getScheduler().run(plugin, t -> resetStepHeight(id, player), null);
            }
        }
        activePlayers.clear();
    }
}
