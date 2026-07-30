package yinwuenchant.enchantments;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.manager.ConfigManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public class MasterOfBeefSlicing extends CustomEnchantment {
    private final ConfigManager configManager;

    public MasterOfBeefSlicing(YinwuEnchantments plugin) {
        super(plugin, "master_of_beef_slicing", "切肉大师", 3, new Material[] {
            Material.WOODEN_SWORD, Material.STONE_SWORD,
            Material.IRON_SWORD, Material.GOLDEN_SWORD,
            Material.DIAMOND_SWORD, Material.NETHERITE_SWORD
        });
        this.configManager = plugin.getConfigManager();
    }

    @Override
    public Component displayName(int level) {
        return Component.text("切肉大师 " + getRomanNumeral(level));
    }



    @Override
    public void onEnable() {
        // 事件处理在 onEntityDamageByEntity 中
    }

    /**
     * ✅ 注册事件订阅者（支持 reload）
     */
    @Override
    public void registerEventSubscribers() {
        plugin.getEnchantmentManager().subscribeEvent(
            org.bukkit.event.entity.EntityDamageByEntityEvent.class,
            event -> onEntityDamageByEntity((org.bukkit.event.entity.EntityDamageByEntityEvent) event)
        );
    }

    @Override
    public void onDisable() {
        // 无需清理
    }

    @Override
    public void onEntityDamageByEntity(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!configManager.isEnchantmentEnabled("master_of_beef_slicing")) return;

        if (!(event.getDamager() instanceof Player player)) return;
        if (event.isCancelled()) return;

        // ⚠️ EntityDamageByEntityEvent 在目标实体区域线程触发
        // 玩家可能位于不同区域（远程攻击），在此先捕获需要的目标信息
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        final Location targetLocation = target.getLocation().clone();
        final EntityType targetType = target.getType();
        final boolean isMooshroom = target instanceof MushroomCow;
        final MushroomCow.Variant mooshroomVariant = isMooshroom ? ((MushroomCow) target).getVariant() : null;

        // 第一步：切换到玩家区域线程读取背包
        player.getScheduler().run(plugin, task -> {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (!hasEnchantment(weapon)) return;

            int level = getEnchantmentLevel(weapon);
            if (level <= 0) return;

            int dropAmount = getDropAmount(level);
            boolean hasFireAspect = weapon.hasItemMeta() &&
                                   weapon.getItemMeta().hasEnchant(Enchantment.FIRE_ASPECT);
            if (weapon.hasItemMeta() && weapon.getItemMeta().hasEnchant(Enchantment.LOOTING)) {
                dropAmount += weapon.getItemMeta().getEnchantLevel(Enchantment.LOOTING);
            }

            final int finalDrop = dropAmount;
            // 第二步：切换到目标区域线程生成掉落物
            plugin.getServer().getRegionScheduler().run(plugin, targetLocation, task2 -> {
                spawnDropsAt(targetLocation, targetType, isMooshroom, mooshroomVariant,
                            hasFireAspect, finalDrop);
            });
        }, null);
    }

    /**
     * 在目标位置生成掉落物（必须在目标区域线程调用）
     */
    private void spawnDropsAt(Location location, EntityType targetType, boolean isMooshroom,
                              MushroomCow.Variant mooshroomVariant, boolean hasFireAspect, int dropAmount) {
        World world = location.getWorld();
        if (world == null) return;

        if (isMooshroom) {
            spawnMooshroomDrops(location, world, mooshroomVariant, hasFireAspect, dropAmount);
            return;
        }

        ItemStack meatItem = getMeatDrop(targetType, hasFireAspect);
        if (meatItem == null) return;

        meatItem.setAmount(dropAmount);
        world.dropItemNaturally(location.add(0, 0.5, 0), meatItem);
    }

    /**
     * 处理蘑菇牛的特殊掉落
     */
    private void spawnMooshroomDrops(Location location, World world, MushroomCow.Variant variant,
                                     boolean hasFireAspect, int dropAmount) {
        Random random = ThreadLocalRandom.current();
        Location dropLoc = location.add(0, 0.5, 0);

        if (hasFireAspect) {
            if (random.nextDouble() < 0.7) {
                ItemStack mushroom = new ItemStack(
                    variant == MushroomCow.Variant.RED ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM,
                    dropAmount);
                world.dropItemNaturally(dropLoc, mushroom);
                world.dropItemNaturally(dropLoc, new ItemStack(Material.COOKED_BEEF, dropAmount));
            } else {
                world.dropItemNaturally(dropLoc, new ItemStack(Material.MUSHROOM_STEW, dropAmount));
                world.dropItemNaturally(dropLoc, new ItemStack(Material.COOKED_BEEF, dropAmount));
            }
        } else {
            world.dropItemNaturally(dropLoc, new ItemStack(
                variant == MushroomCow.Variant.RED ? Material.RED_MUSHROOM : Material.BROWN_MUSHROOM,
                dropAmount));
        }
    }

    /**
     * 根据等级获取掉落数量（随机范围）
     */
    private int getDropAmount(int level) {
        int min, max;
        min = switch (level) {
            case 1 -> configManager.getInt("master_of_beef_slicing.drop-amount-level-1-min");
            case 2 -> configManager.getInt("master_of_beef_slicing.drop-amount-level-2-min");
            case 3 -> configManager.getInt("master_of_beef_slicing.drop-amount-level-3-min");
            default -> 0;
        };

        max = switch (level) {
            case 1 -> configManager.getInt("master_of_beef_slicing.drop-amount-level-1-max");
            case 2 -> configManager.getInt("master_of_beef_slicing.drop-amount-level-2-max");
            case 3 -> configManager.getInt("master_of_beef_slicing.drop-amount-level-3-max");
            default -> 1;
        };

        // 确保 min <= max
        if (min > max) {
            int temp = min;
            min = max;
            max = temp;
        }

        // 在 min 和 max 之间随机（包括边界）
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    /**
     * 检查是否是僵尸类生物
     */
    private boolean isZombieType(EntityType type) {
        return type == EntityType.ZOMBIE ||
               type == EntityType.ZOMBIE_VILLAGER ||
               type == EntityType.HUSK ||
               type == EntityType.DROWNED ||
               type == EntityType.ZOGLIN ||
               type == EntityType.ZOMBIFIED_PIGLIN ||
               type == EntityType.ZOMBIE_HORSE;
    }

    /**
     * 根据生物类型和火焰附加获取对应的肉类
     */
    private ItemStack getMeatDrop(EntityType type, boolean hasFireAspect) {
        Random random = ThreadLocalRandom.current();

        // 牛（蘑菇牛已在外部处理）
        if (type == EntityType.COW) {
            return new ItemStack(hasFireAspect ? Material.COOKED_BEEF : Material.BEEF);
        }

        // 猪
        if (type == EntityType.PIG) {
            return new ItemStack(hasFireAspect ? Material.COOKED_PORKCHOP : Material.PORKCHOP);
        }

        // 鸡
        if (type == EntityType.CHICKEN) {
            return new ItemStack(hasFireAspect ? Material.COOKED_CHICKEN : Material.CHICKEN);
        }

        // 羊
        if (type == EntityType.SHEEP) {
            return new ItemStack(hasFireAspect ? Material.COOKED_MUTTON : Material.MUTTON);
        }

        // 兔子
        if (type == EntityType.RABBIT) {
            if (hasFireAspect) {
                return new ItemStack(random.nextDouble() < 0.7 ? Material.COOKED_RABBIT : Material.RABBIT_STEW);
            }
            return new ItemStack(Material.RABBIT);
        }

        // 僵尸类生物
        if (isZombieType(type)) {
            return new ItemStack(Material.ROTTEN_FLESH);
        }

        return null;
    }
}
