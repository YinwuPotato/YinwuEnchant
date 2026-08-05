package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 失眠 —— 无法睡觉，但可设置复活点，且会积累未睡眠天数生成幻翼
 *
 * 效果：
 * - 可以通过床设置复活点
 * - 但无法入睡（进入床的动画被取消）
 * - 不计入跳过夜晚的睡眠人数
 * - 积累未睡眠天数，时间超过阈值会生成幻翼
 */
public class Insomnia extends CustomEnchantment {

    private final YinwuEnchantments plugin;

    public Insomnia(YinwuEnchantments plugin) {
        super(plugin, "insomnia", "失眠", 1, new Material[]{
            Material.LEATHER_HELMET, Material.CHAINMAIL_HELMET,
            Material.IRON_HELMET, Material.GOLDEN_HELMET,
            Material.DIAMOND_HELMET, Material.NETHERITE_HELMET,
            Material.TURTLE_HELMET
        });
        this.plugin = plugin;
    }

    @Override
    public Component displayName(int level) {
        return Component.text("失眠");
    }

    @Override
    public void onEnable() {
        if (!plugin.getConfigManager().isEnchantmentEnabled("insomnia")) {
            return;
        }
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[失眠] 已启用");
        }
    }

    @Override
    public void onDisable() {
        if (plugin.getConfigManager().getBoolean("debug")) {
            plugin.getLogger().fine("[失眠] 已禁用");
        }
    }

    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            PlayerBedEnterEvent.class,
            this::onBedEnter
        );
        startNightBuffTask();
    }

    /**
     * 玩家尝试进入床时：
     * - 若附近有播放中的唱片机（音乐安抚失眠），允许入睡
     * - 否则夜晚（试图入睡）取消进入，但保留复活点设置
     * - 白天（仅设置复活点）允许正常操作
     */
    private void onBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();

        // 检查头盔是否有失眠附魔
        ItemStack helmet = player.getInventory().getHelmet();
        if (helmet == null || helmet.getType().isAir() || !hasEnchantment(helmet)) return;

        // 只在玩家试图入睡时干预（夜晚/雷暴时进入床）
        if (event.getBedEnterResult() == PlayerBedEnterEvent.BedEnterResult.OK) {
            // 能听到播放中的唱片机 → 可以入睡
            if (isJukeboxPlayingNearby(player)) {
                return;
            }

            // 取消入睡，但允许设置复活点
            event.setCancelled(true);

            // 手动设置复活点为床的位置
            Block bed = event.getBed();
            if (bed != null && Tag.BEDS.isTagged(bed.getType())) {
                player.setBedSpawnLocation(bed.getLocation(), true);
                player.sendMessage("§c§l失眠 §7- 你无法入睡，但已设置重生点");
            }
        }
        // 其他情况（阻挡、太远等）不干预
    }

    /** 检测玩家附近（16格）是否有播放中的唱片机 */
    private boolean isJukeboxPlayingNearby(Player player) {
        Location loc = player.getLocation();
        World w = loc.getWorld();
        if (w == null) return false;
        int cx = loc.getBlockX(), cy = loc.getBlockY(), cz = loc.getBlockZ();
        int horizontal = 16;
        for (int x = cx - horizontal; x <= cx + horizontal; x++) {
            for (int z = cz - horizontal; z <= cz + horizontal; z++) {
                for (int y = cy - 4; y <= cy + 4; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    if (b.getType() == Material.JUKEBOX) {
                        try {
                            org.bukkit.block.Jukebox jb = (org.bukkit.block.Jukebox) b.getState();
                            if (jb.isPlaying()) return true;
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
        return false;
    }

    /** 夜晚给戴失眠头盔的玩家力量+夜视（代价换收益） */
    private void startNightBuffTask() {
        Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, (task) -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.getScheduler().run(plugin, (t) -> {
                    if (!player.isOnline()) return;
                    ItemStack helmet = player.getInventory().getHelmet();
                    if (helmet == null || helmet.getType().isAir() || !hasEnchantment(helmet)) return;
                    long time = player.getWorld().getTime();
                    if (time >= 12500 && time <= 23500) {
                        // 300 tick(15s)：刷新间隔 2s 后剩余仍 >10s，避开夜视客户端闪烁警示(<10s)
                        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 300, 0));
                        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 300, 0));
                    }
                }, null);
            }
        }, 1L, 40L);
    }
}
