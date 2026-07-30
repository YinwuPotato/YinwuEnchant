package yinwuenchant;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry.EnchantmentCost;
import io.papermc.paper.registry.event.RegistryComposeEvent;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemType;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public class YinwuEnchantBootstrap implements PluginBootstrap {

    private static final String NS = "yinwuenchant";

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(
            RegistryEvents.ENCHANTMENT.compose(),
            this::onEnchantmentRegistry
        );
    }

    private void onEnchantmentRegistry(RegistryComposeEvent<org.bukkit.enchantments.Enchantment, EnchantmentRegistryEntry.Builder> event) {
        var registry = event.registry();
        java.util.List<EquipmentSlotGroup> L = java.util.Collections.emptyList();

        reg(registry, "clearsight",           "明目",       1, 10, helmets(),         EquipmentSlotGroup.HEAD,  10, 5, 20, 5, L, null);
        reg(registry, "darkspeed",            "黑暗行者",   3, 10, boots(),           EquipmentSlotGroup.FEET,  10, 5, 25, 8, L, null);
        reg(registry, "resonate",             "共振",       1, 5,  of(ItemType.SHIELD), EquipmentSlotGroup.HAND, 15, 5, 25, 5, L, null);
        reg(registry, "safefall",             "外骨骼",     3, 10, leggings(),        EquipmentSlotGroup.LEGS, 10, 5, 25, 8, L, null);
        reg(registry, "shrieker_sense",       "幽匿探测",   1, 5,  of(ItemType.SPYGLASS), EquipmentSlotGroup.HAND, 15, 5, 25, 5, L, null);
        reg(registry, "sonic_boom",           "音波爆裂",   1, 10, chestplates(),     EquipmentSlotGroup.CHEST, 15, 5, 30, 5, L, null);
        reg(registry, "undermine",            "深层矿工",   5, 10, tools(),           EquipmentSlotGroup.MAINHAND, 10, 5, 30, 8, L, null);
        reg(registry, "cats_paw",             "猫爪",       1, 8,  boots(),           EquipmentSlotGroup.FEET,  20, 5, 35, 5, L, null);
        reg(registry, "nasus",               "狗头",       1, 8,  helmets(),         EquipmentSlotGroup.HEAD, 20, 5, 35, 5, L, null);
        reg(registry, "master_of_beef_slicing","切肉大师", 3, 8,  swords(),          EquipmentSlotGroup.MAINHAND, 20, 5, 35, 8, L, null);
        reg(registry, "phantom",              "幻影",       1, 8,  chestplates(),     EquipmentSlotGroup.CHEST, 20, 5, 35, 5, L, null);
        reg(registry, "harvest",              "丰收",       1, 8,  hoes(),            EquipmentSlotGroup.MAINHAND, 20, 5, 35, 5, L, silkTouch());
        reg(registry, "soulbound",           "灵魂绑定",   1, 3,  allEnchantable(),  EquipmentSlotGroup.HEAD, 30, 10, 50, 10, L, vanishingCurse());
        reg(registry, "smelt",               "熔化",       1, 8,  tools(),           EquipmentSlotGroup.MAINHAND, 20, 5, 35, 5, L, silkTouch());
        reg(registry, "airbag",              "气囊",       3, 5,  of(ItemType.ELYTRA), EquipmentSlotGroup.CHEST, 15, 5, 30, 8, L, null);
        reg(registry, "bless",               "护佑",       1, 5,  chestplates(),     EquipmentSlotGroup.CHEST, 25, 5, 40, 5, L, null);
        reg(registry, "vampire_curse",        "吸血鬼诅咒", 1, 2,  helmets(),         EquipmentSlotGroup.HEAD, 25, 10, 45, 10, L, null);
        reg(registry, "insomnia",            "失眠",       1, 2,  helmets(),         EquipmentSlotGroup.HEAD, 25, 10, 45, 10, L, null);
        reg(registry, "lava_walker",          "熔岩行者",   2, 8,  boots(),           EquipmentSlotGroup.FEET, 20, 5, 35, 8, L, lavaWalkerExcl());
        reg(registry, "emerald_till",         "拾翠",       1, 8,  hoes(),            EquipmentSlotGroup.MAINHAND, 20, 5, 35, 5, L, silkTouch());
        reg(registry, "step_up",             "马蹄",       2, 8,  boots(),           EquipmentSlotGroup.FEET, 20, 5, 35, 8, L, null);
    }

    // ====== 注册辅助 ======

    private void reg(
        io.papermc.paper.registry.event.WritableRegistry<org.bukkit.enchantments.Enchantment, EnchantmentRegistryEntry.Builder> registry,
        String id, String displayName, int maxLevel, int weight,
        RegistryKeySet<org.bukkit.inventory.ItemType> supportedItems,
        EquipmentSlotGroup primarySlot,
        int minBase, int minAdd, int maxBase, int maxAdd,
        List<EquipmentSlotGroup> extraSlots,
        RegistryKeySet<org.bukkit.enchantments.Enchantment> exclusive
    ) {
        var key = TypedKey.create(RegistryKey.ENCHANTMENT, key(id));
        if (exclusive != null) {
            registry.register(key, builder -> builder
                .description(Component.text(displayName))
                .supportedItems(supportedItems)
                .maxLevel(maxLevel)
                .weight(weight)
                .minimumCost(EnchantmentCost.of(minBase, minAdd))
                .maximumCost(EnchantmentCost.of(maxBase, maxAdd))
                .anvilCost(1)
                .activeSlots(combine(primarySlot, extraSlots))
                .exclusiveWith(exclusive)
            );
        } else {
            registry.register(key, builder -> builder
                .description(Component.text(displayName))
                .supportedItems(supportedItems)
                .maxLevel(maxLevel)
                .weight(weight)
                .minimumCost(EnchantmentCost.of(minBase, minAdd))
                .maximumCost(EnchantmentCost.of(maxBase, maxAdd))
                .anvilCost(1)
                .activeSlots(combine(primarySlot, extraSlots))
            );
        }
    }

    private RegistryKeySet<Enchantment> silkTouch() {
        return RegistrySet.keySet(RegistryKey.ENCHANTMENT,
            List.of(TypedKey.create(RegistryKey.ENCHANTMENT, new NamespacedKey("minecraft", "silk_touch"))));
    }
    private RegistryKeySet<Enchantment> vanishingCurse() {
        return RegistrySet.keySet(RegistryKey.ENCHANTMENT,
            List.of(TypedKey.create(RegistryKey.ENCHANTMENT, new NamespacedKey("minecraft", "vanishing_curse"))));
    }
    private RegistryKeySet<Enchantment> lavaWalkerExcl() {
        return RegistrySet.keySet(RegistryKey.ENCHANTMENT,
            List.of(
                TypedKey.create(RegistryKey.ENCHANTMENT, new NamespacedKey("minecraft", "depth_strider")),
                TypedKey.create(RegistryKey.ENCHANTMENT, new NamespacedKey("minecraft", "frost_walker"))
            ));
    }

    // ====== 物品集合 ======

    @SafeVarargs
    private static <T> List<T> of(T... items) { return List.of(items); }

    private RegistryKeySet<org.bukkit.inventory.ItemType> of(ItemType... items) {
        return RegistrySet.keySetFromValues(RegistryKey.ITEM, List.of(items));
    }

    private RegistryKeySet<ItemType> helmets() {
        return of(ItemType.LEATHER_HELMET, ItemType.CHAINMAIL_HELMET, ItemType.IRON_HELMET, ItemType.GOLDEN_HELMET, ItemType.DIAMOND_HELMET, ItemType.NETHERITE_HELMET, ItemType.TURTLE_HELMET, ItemType.CARVED_PUMPKIN);
    }

    private RegistryKeySet<ItemType> chestplates() {
        return of(ItemType.LEATHER_CHESTPLATE, ItemType.CHAINMAIL_CHESTPLATE, ItemType.IRON_CHESTPLATE, ItemType.GOLDEN_CHESTPLATE, ItemType.DIAMOND_CHESTPLATE, ItemType.NETHERITE_CHESTPLATE, ItemType.ELYTRA);
    }

    private RegistryKeySet<ItemType> leggings() {
        return of(ItemType.LEATHER_LEGGINGS, ItemType.CHAINMAIL_LEGGINGS, ItemType.IRON_LEGGINGS, ItemType.GOLDEN_LEGGINGS, ItemType.DIAMOND_LEGGINGS, ItemType.NETHERITE_LEGGINGS);
    }

    private RegistryKeySet<ItemType> boots() {
        return of(ItemType.LEATHER_BOOTS, ItemType.CHAINMAIL_BOOTS, ItemType.IRON_BOOTS, ItemType.GOLDEN_BOOTS, ItemType.DIAMOND_BOOTS, ItemType.NETHERITE_BOOTS);
    }

    private RegistryKeySet<ItemType> swords() {
        return of(ItemType.WOODEN_SWORD, ItemType.STONE_SWORD, ItemType.IRON_SWORD, ItemType.GOLDEN_SWORD, ItemType.DIAMOND_SWORD, ItemType.NETHERITE_SWORD);
    }

    private RegistryKeySet<ItemType> tools() {
        return of(ItemType.WOODEN_PICKAXE, ItemType.STONE_PICKAXE, ItemType.IRON_PICKAXE, ItemType.GOLDEN_PICKAXE, ItemType.DIAMOND_PICKAXE, ItemType.NETHERITE_PICKAXE, ItemType.WOODEN_AXE, ItemType.STONE_AXE, ItemType.IRON_AXE, ItemType.GOLDEN_AXE, ItemType.DIAMOND_AXE, ItemType.NETHERITE_AXE, ItemType.WOODEN_SHOVEL, ItemType.STONE_SHOVEL, ItemType.IRON_SHOVEL, ItemType.GOLDEN_SHOVEL, ItemType.DIAMOND_SHOVEL, ItemType.NETHERITE_SHOVEL, ItemType.WOODEN_HOE, ItemType.STONE_HOE, ItemType.IRON_HOE, ItemType.GOLDEN_HOE, ItemType.DIAMOND_HOE, ItemType.NETHERITE_HOE);
    }

    private RegistryKeySet<ItemType> hoes() {
        return of(ItemType.WOODEN_HOE, ItemType.STONE_HOE, ItemType.IRON_HOE, ItemType.GOLDEN_HOE, ItemType.DIAMOND_HOE, ItemType.NETHERITE_HOE);
    }

    private RegistryKeySet<ItemType> allEnchantable() {
        return of(ItemType.LEATHER_HELMET, ItemType.LEATHER_CHESTPLATE, ItemType.LEATHER_LEGGINGS, ItemType.LEATHER_BOOTS, ItemType.CHAINMAIL_HELMET, ItemType.CHAINMAIL_CHESTPLATE, ItemType.CHAINMAIL_LEGGINGS, ItemType.CHAINMAIL_BOOTS, ItemType.IRON_HELMET, ItemType.IRON_CHESTPLATE, ItemType.IRON_LEGGINGS, ItemType.IRON_BOOTS, ItemType.GOLDEN_HELMET, ItemType.GOLDEN_CHESTPLATE, ItemType.GOLDEN_LEGGINGS, ItemType.GOLDEN_BOOTS, ItemType.DIAMOND_HELMET, ItemType.DIAMOND_CHESTPLATE, ItemType.DIAMOND_LEGGINGS, ItemType.DIAMOND_BOOTS, ItemType.NETHERITE_HELMET, ItemType.NETHERITE_CHESTPLATE, ItemType.NETHERITE_LEGGINGS, ItemType.NETHERITE_BOOTS, ItemType.WOODEN_SWORD, ItemType.STONE_SWORD, ItemType.IRON_SWORD, ItemType.GOLDEN_SWORD, ItemType.DIAMOND_SWORD, ItemType.NETHERITE_SWORD, ItemType.WOODEN_AXE, ItemType.STONE_AXE, ItemType.IRON_AXE, ItemType.GOLDEN_AXE, ItemType.DIAMOND_AXE, ItemType.NETHERITE_AXE, ItemType.WOODEN_PICKAXE, ItemType.STONE_PICKAXE, ItemType.IRON_PICKAXE, ItemType.GOLDEN_PICKAXE, ItemType.DIAMOND_PICKAXE, ItemType.NETHERITE_PICKAXE, ItemType.WOODEN_SHOVEL, ItemType.STONE_SHOVEL, ItemType.IRON_SHOVEL, ItemType.GOLDEN_SHOVEL, ItemType.DIAMOND_SHOVEL, ItemType.NETHERITE_SHOVEL, ItemType.WOODEN_HOE, ItemType.STONE_HOE, ItemType.IRON_HOE, ItemType.GOLDEN_HOE, ItemType.DIAMOND_HOE, ItemType.NETHERITE_HOE, ItemType.BOW, ItemType.CROSSBOW, ItemType.TRIDENT, ItemType.MACE, ItemType.SHIELD, ItemType.ELYTRA, ItemType.FISHING_ROD);
    }

    // ====== 辅助 ======

    private static NamespacedKey key(String id) { return new NamespacedKey(NS, id); }

    private static List<EquipmentSlotGroup> combine(EquipmentSlotGroup primary, List<EquipmentSlotGroup> extra) {
        var list = new java.util.ArrayList<>(extra);
        list.addFirst(primary);
        return list;
    }
}
