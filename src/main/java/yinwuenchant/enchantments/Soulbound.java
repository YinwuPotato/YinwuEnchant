package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灵魂绑定 —— 死亡时保留附魔物品，不掉落不丢失
 *
 * 效果：
 * - 玩家死亡时，带有此附魔的物品不会掉落，会留在背包中
 * - 支持所有可附魔物品（武器、工具、盔甲、盾牌等）
 */
public class Soulbound extends CustomEnchantment {

    private final YinwuEnchantments plugin;

    /** 死亡时暂存被保护物品，重生时归还（玩家UUID → 物品列表） */
    private final Map<UUID, List<ItemStack>> pendingRestores = new ConcurrentHashMap<>();

    public Soulbound(YinwuEnchantments plugin) {
        super(plugin, "soulbound", "灵魂绑定", 1, new Material[]{
            Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
            Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS,
            Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
            Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS,
            Material.IRON_HELMET, Material.IRON_CHESTPLATE,
            Material.IRON_LEGGINGS, Material.IRON_BOOTS,
            Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
            Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS,
            Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
            Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS,
            Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
            Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS,
            Material.TURTLE_HELMET,
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE,
            Material.IRON_AXE, Material.GOLDEN_AXE,
            Material.DIAMOND_AXE, Material.NETHERITE_AXE,
            Material.WOODEN_PICKAXE, Material.STONE_PICKAXE,
            Material.IRON_PICKAXE, Material.GOLDEN_PICKAXE,
            Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE,
            Material.WOODEN_SHOVEL, Material.STONE_SHOVEL,
            Material.IRON_SHOVEL, Material.GOLDEN_SHOVEL,
            Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL,
            Material.WOODEN_HOE, Material.STONE_HOE,
            Material.IRON_HOE, Material.GOLDEN_HOE,
            Material.DIAMOND_HOE, Material.NETHERITE_HOE,
            Material.BOW, Material.CROSSBOW,
            Material.TRIDENT, Material.MACE,
            Material.SHIELD, Material.ELYTRA,
            Material.FISHING_ROD
        });
        this.plugin = plugin;
    }

    @Override
    public Component displayName(int level) {
        return Component.text("灵魂绑定");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("soulbound")) {
            return;
        }

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[灵魂绑定] 已启用");
        }
    }

    @Override
    public void onDisable() {
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[灵魂绑定] 已禁用");
        }
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerDeathEvent.class,
            this::onPlayerDeath
        );

        // 重生归还 + 登入兜底（死亡后离线/重进场景）
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerRespawnEvent.class,
            event -> restoreItems(((PlayerRespawnEvent) event).getPlayer())
        );
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerJoinEvent.class,
            event -> restoreItems(((PlayerJoinEvent) event).getPlayer())
        );
    }

    /**
     * 玩家死亡时保护灵魂绑定的物品
     * 从掉落物中移除带附魔的物品，保留在玩家背包中
     */
    private void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> protectedItems = new ArrayList<>();

        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[灵魂绑定] §e检查玩家 " + player.getName() + " 的死亡掉落物");
        }

        // 遍历掉落物，找出带灵魂绑定的物品
        List<ItemStack> drops = event.getDrops();
        for (ListIterator<ItemStack> it = drops.listIterator(); it.hasNext();) {
            ItemStack item = it.next();
            if (item != null && !item.getType().isAir() && hasEnchantment(item)) {
                protectedItems.add(item.clone());
                it.remove();

                if (plugin.getConfigManager().getBoolean("debug")) {
                    plugin.getLogger().fine("[灵魂绑定] §a保护物品: " + item.getType().name());
                }
            }
        }

        // 如果成功保护了物品，暂存并在重生时归还
        if (!protectedItems.isEmpty()) {
            pendingRestores.put(player.getUniqueId(), protectedItems);

            // 主路径：死亡清空背包后延迟几刻直接把物品放回背包（不依赖重生事件）
            // 玩家离线时任务不执行，由登入兜底 restoreItems 归还
            player.getScheduler().runDelayed(plugin, (task) -> {
                if (player.isOnline()) {
                    List<ItemStack> items = pendingRestores.remove(player.getUniqueId());
                    if (items != null && !items.isEmpty()) {
                        giveBack(player, items);
                    }
                }
            }, null, 5L);

            player.sendMessage("§d✦ §5灵魂绑定 §d保护了 §f" + protectedItems.size()
                + " §d件物品，重生后归还！");
        }
    }

    /**
     * 归还被保护的物品（重生 / 登入兜底，幂等）
     */
    private void restoreItems(Player player) {
        List<ItemStack> items = pendingRestores.remove(player.getUniqueId());
        if (items == null || items.isEmpty()) {
            return;
        }
        giveBack(player, items);
    }

    private void giveBack(Player player, List<ItemStack> items) {
        for (ItemStack item : items) {
            // 背包已满的部分掉落在重生点
            player.getInventory().addItem(item).values().forEach(drop ->
                player.getWorld().dropItemNaturally(player.getLocation(), drop));
        }
        player.sendMessage("§d✦ §5灵魂绑定 §d已归还 §f" + items.size() + " §d件物品！");
    }
}
