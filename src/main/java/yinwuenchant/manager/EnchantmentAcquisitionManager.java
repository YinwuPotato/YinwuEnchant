package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class EnchantmentAcquisitionManager implements Listener {
    private final YinwuEnchantments plugin;
    private final EnchantmentManager enchantmentManager;
    private Map<String, EnchantAcq> acquisitionMap = Map.of();

    private record MobDrop(String entity, double chance, int levelMin, int levelMax) {}
    private record FishingAcq(double chance, int levelMin, int levelMax) {}
    private record EnchantAcq(boolean enabled, List<MobDrop> mobDrops, FishingAcq fishing) {}

    public EnchantmentAcquisitionManager(YinwuEnchantments plugin, EnchantmentManager enchantmentManager) {
        this.plugin = plugin;
        this.enchantmentManager = enchantmentManager;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        Map<String, EnchantAcq> map = new HashMap<>();
        ConfigurationSection acq = plugin.getConfig().getConfigurationSection("acquisition");
        if (acq != null) {
            for (String id : acq.getKeys(false)) {
                ConfigurationSection s = acq.getConfigurationSection(id);
                if (s == null || !s.getBoolean("enabled", true)) continue;

                List<MobDrop> mobDrops = new ArrayList<>();
                List<?> raw = s.getList("mob-drops");
                if (raw != null) {
                    for (Object o : raw) {
                        if (!(o instanceof Map<?, ?> m)) continue;
                        mobDrops.add(new MobDrop(
                            str(m, "entity"),
                            num(m, "chance", 0.0).doubleValue(),
                            num(m, "level-min", 1).intValue(),
                            num(m, "level-max", 1).intValue()
                        ));
                    }
                }

                FishingAcq fishing = null;
                ConfigurationSection fs = s.getConfigurationSection("fishing");
                if (fs != null) {
                    fishing = new FishingAcq(
                        fs.getDouble("chance", 0),
                        fs.getInt("level-min", 1),
                        fs.getInt("level-max", 1)
                    );
                }

                map.put(id, new EnchantAcq(true, mobDrops, fishing));
            }
        }
        acquisitionMap = map;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        EntityType type = event.getEntityType();
        var rand = ThreadLocalRandom.current();

        for (Map.Entry<String, EnchantAcq> entry : acquisitionMap.entrySet()) {
            String id = entry.getKey();
            EnchantAcq acq = entry.getValue();
            if (acq.mobDrops().isEmpty()) continue;

            for (MobDrop drop : acq.mobDrops()) {
                EntityType target;
                try { target = EntityType.valueOf(drop.entity().toUpperCase()); } catch (IllegalArgumentException e) { continue; }
                if (type != target) continue;
                if (rand.nextDouble() >= drop.chance()) continue;

                int level = drop.levelMin() == drop.levelMax()
                    ? drop.levelMin()
                    : weightedRandomLevel(drop.levelMin(), drop.levelMax(), rand);

                ItemStack book = createEnchantedBook(id, level);
                if (book != null) {
                    event.getDrops().add(book);
                }
                break;
            }
        }
    }

    /** 按 1/level 递减权重选等级（等级越高掉落率越低） */
    private int weightedRandomLevel(int min, int max, ThreadLocalRandom rand) {
        double total = 0;
        for (int l = min; l <= max; l++) total += 1.0 / l;
        double r = rand.nextDouble() * total;
        for (int l = min; l <= max; l++) {
            r -= 1.0 / l;
            if (r <= 0) return l;
        }
        return max;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        var rand = ThreadLocalRandom.current();

        for (Map.Entry<String, EnchantAcq> entry : acquisitionMap.entrySet()) {
            String id = entry.getKey();
            FishingAcq f = entry.getValue().fishing();
            if (f == null) continue;
            if (rand.nextDouble() >= f.chance()) continue;

            int level = f.levelMin() == f.levelMax()
                ? f.levelMin()
                : f.levelMin() + rand.nextInt(f.levelMax() - f.levelMin() + 1);

            ItemStack book = createEnchantedBook(id, level);
            if (book == null) continue;

            Player player = event.getPlayer();
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(book);
                player.sendMessage("§d✨ 你钓到了一个神秘的附魔书！");
            } else {
                event.setCancelled(true);
                player.getWorld().dropItemNaturally(player.getLocation(), book);
                player.sendMessage("§d✨ 你钓到了一个神秘的附魔书！（已掉落在地上）");
            }
            break;
        }
    }

    // ==================== 附魔台模拟 ====================

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        if (!plugin.getConfig().getBoolean("enchanting-table.enabled", true)) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        List<CustomEnchantment> applicable = new ArrayList<>();
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            if (ench.canApplyTo(item) && !ench.hasEnchantment(item)
                && !hasExclusiveConflict(item, ench)) applicable.add(ench);
        }
        if (applicable.isEmpty()) return;

        var rand = ThreadLocalRandom.current();
        double chance = plugin.getConfig().getDouble("enchanting-table.chance", 0.5);
        if (rand.nextDouble() >= chance) return;

        int countMin = plugin.getConfig().getInt("enchanting-table.count-min", 1);
        int countMax = plugin.getConfig().getInt("enchanting-table.count-max", 1);
        int count = countMin == countMax ? countMin
            : countMin + rand.nextInt(countMax - countMin + 1);

        Collections.shuffle(applicable);
        for (int i = 0; i < count && i < applicable.size(); i++) {
            CustomEnchantment ench = applicable.get(i);
            int level = 1 + rand.nextInt(ench.getMaxLevel());
            ench.applyEnchantment(item, level);
        }
    }

    // ==================== 附魔书创建（原版格式） ====================

    public ItemStack createEnchantedBook(String enchantmentId, int level) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantmentId);
        if (ench == null) return null;
        if (level < 1 || level > ench.getMaxLevel()) return null;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        if (meta == null) return null;

        // PDC 存储附魔（本服务端不支持 bootstrap 原版注册）
        meta.getPersistentDataContainer().set(ench.getEnchantmentKey(),
            org.bukkit.persistence.PersistentDataType.INTEGER, level);
        meta.setDisplayName(ench.getMaxLevel() == 1
            ? "§d" + ench.getDisplayName()
            : "§d" + ench.getDisplayName() + " " + EnchantmentLore.roman(level));

        List<String> lore = new ArrayList<>();
        lore.add("§7适用物品: " + getApplicableItemsText(ench));
        lore.add("§7等级: " + level + "/" + ench.getMaxLevel());
        meta.setLore(lore);
        // 发光
        meta.setEnchantmentGlintOverride(true);
        book.setItemMeta(meta);
        return book;
    }

    public String[] getAvailableEnchantmentIds() { return enchantmentManager.getEnchantmentIds(); }
    public int getMaxLevel(String enchantmentId) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantmentId);
        return ench != null ? ench.getMaxLevel() : 0;
    }

    // ==================== 铁砧（原版已自动处理，保留自定义 lore 补充） ====================

    @EventHandler
    public void onPrepareAnvil(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        var inv = event.getInventory();
        ItemStack[] items = inv.getContents();
        if (items.length < 2) return;

        ItemStack item1 = items[0];
        ItemStack item2 = items[1];
        if (item1 == null || item2 == null) return;

        boolean b1 = item1.getType() == Material.ENCHANTED_BOOK;
        boolean b2 = item2.getType() == Material.ENCHANTED_BOOK;

        if (b1 && b2) {
            // 两本书：合并升级
            handleBookMerge(event, item1, item2);
        } else if (b1 && !item2.getType().isAir() && !isCustomBook(item2)) {
            // 书 + 物品：应用附魔
            handleBookApply(event, item1, item2);
        } else if (b2 && !item1.getType().isAir() && !isCustomBook(item1)) {
            // 物品 + 书：应用附魔
            handleBookApply(event, item2, item1);
        } else if (!b1 && !b2 && item1.getType() == item2.getType() && !item1.getType().isAir()) {
            // 两个同类型物品：合并自定义附魔（取最高等级）
            handleItemMerge(event, item1, item2);
        }
    }

    /** 两个同类型物品合并：合并双方的自定义附魔（各取最高等级，模拟原版铁砧） */
    private void handleItemMerge(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack item1, ItemStack item2) {
        ItemStack result = item1.clone();
        boolean changed = false;

        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            int level1 = ench.getEnchantmentLevel(item1);
            int level2 = ench.getEnchantmentLevel(item2);
            int merged = Math.max(level1, level2);
            if (merged > level1) {
                result = ench.applyEnchantment(result, merged);
                changed = true;
            }
        }

        if (changed) {
            applyRename(event, result);
            event.setResult(result);
            forceConsumeSacrifice(event);
            event.getInventory().setRepairCost(Math.min(39, event.getInventory().getRepairCost() + 1));
        }
    }

    /** 把铁砧命名框输入的名字应用到结果物品（命名 + 合并同时生效） */
    private void applyRename(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack result) {
        String rename = event.getInventory().getRenameText();
        if (rename == null || rename.isEmpty()) return;

        // 原版会在放入物品时自动把第一格原名填入命名框；
        // 若与原名相同说明玩家没改名，跳过以免覆盖合并后的新名
        ItemStack slot0 = event.getInventory().getItem(0);
        if (slot0 != null && slot0.hasItemMeta() && slot0.getItemMeta().hasDisplayName()) {
            String orig = org.bukkit.ChatColor.stripColor(slot0.getItemMeta().getDisplayName());
            if (rename.equals(orig)) return;
        }

        ItemMeta meta = result.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(rename);
        result.setItemMeta(meta);
    }

    /**
     * 强制消耗 slot1（第二件物品/书）。
     * 原版 createResult() 对"满耐久 + 无原版附魔"的物品判定为仅重命名（onlyRenaming=true），
     * 导致 onTake 不消耗 slot1。这里用反射把 repairItemCountCost 置 1，
     * 使 onTake 走"消耗 slot1"分支。
     */
    private void forceConsumeSacrifice(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        try {
            Object view = event.getView();
            java.lang.reflect.Method getHandle = view.getClass().getMethod("getHandle");
            Object anvilMenu = getHandle.invoke(view);
            java.lang.reflect.Field field = anvilMenu.getClass().getField("repairItemCountCost");
            field.setInt(anvilMenu, 1);
        } catch (Exception ignored) {
            // 反射失败则退回原版行为
        }
    }

    /** 检查目标物品是否已含与指定附魔互斥的原版附魔 */
    private boolean hasExclusiveConflict(ItemStack item, CustomEnchantment ench) {
        org.bukkit.enchantments.Enchantment[] exclusives = ench.getExclusiveEnchantments();
        if (exclusives.length == 0) return false;
        for (org.bukkit.enchantments.Enchantment e : exclusives) {
            if (item.containsEnchantment(e)) return true;
        }
        return false;
    }

    private boolean isCustomBook(ItemStack item) {
        // 只有附魔书材质才算书，避免把已附魔的自定义物品误判为书
        if (item.getType() != Material.ENCHANTED_BOOK) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            if (meta.getPersistentDataContainer().has(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER)) return true;
        }
        return false;
    }

    /** 两本同等级附魔书合并升级（两本1级 → 1本2级） */
    private void handleBookMerge(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack book1, ItemStack book2) {
        ItemMeta meta1 = book1.getItemMeta();
        ItemMeta meta2 = book2.getItemMeta();
        if (meta1 == null || meta2 == null) return;

        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            Integer level1 = meta1.getPersistentDataContainer().get(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER);
            Integer level2 = meta2.getPersistentDataContainer().get(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER);
            if (level1 != null && level1 > 0 && level1.equals(level2)) {
                int newLevel = level1 + 1;
                if (newLevel > ench.getMaxLevel()) continue;
                ItemStack result = createEnchantedBook(ench.getId(), newLevel);
                if (result != null) {
                    applyRename(event, result);
                    event.setResult(result);
                    forceConsumeSacrifice(event);
                    event.getInventory().setRepairCost(Math.min(39, event.getInventory().getRepairCost() + 1));
                }
                return;
            }
        }
    }

    /** 附魔书 + 物品：把附魔应用到物品（铁砧） */
    private void handleBookApply(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack book, ItemStack target) {
        ItemMeta bookMeta = book.getItemMeta();
        if (bookMeta == null) return;

        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            Integer level = bookMeta.getPersistentDataContainer().get(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER);
            if (level == null || level <= 0) continue;
            if (!ench.canApplyTo(target)) continue;
            if (hasExclusiveConflict(target, ench)) continue;

            // 原版铁砧逻辑：已有更高等级则跳过，同等级则升级
            int current = ench.getEnchantmentLevel(target);
            int applied;
            if (current == 0) applied = level;
            else if (current == level && level < ench.getMaxLevel()) applied = level + 1;
            else if (current > level) continue;
            else applied = level;

            ItemStack result = target.clone();
            result = ench.applyEnchantment(result, applied);
            applyRename(event, result);
            event.setResult(result);
            forceConsumeSacrifice(event);
            event.getInventory().setRepairCost(Math.min(39, event.getInventory().getRepairCost() + 1));
            return;
        }
    }

    // ==================== 辅助 ====================

    private String getRomanNumeral(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    private String getApplicableItemsText(CustomEnchantment ench) {
        // 灵魂绑定覆盖全物品，精简显示
        if ("soulbound".equals(ench.getId())) return "盔甲、武器、工具";
        Material[] items = ench.getApplicableItems();
        if (items == null || items.length == 0) return "未知";
        java.util.Set<String> cats = new java.util.HashSet<>();
        for (Material m : items) {
            String n = m.name();
            if (n.contains("HELMET")) cats.add("头盔");
            else if (n.contains("CHESTPLATE")) cats.add("胸甲");
            else if (n.contains("LEGGINGS")) cats.add("护腿");
            else if (n.contains("BOOTS")) cats.add("靴子");
            else if (n.contains("SWORD")) cats.add("剑");
            else if (n.contains("PICKAXE")) cats.add("镐");
            else if (n.contains("AXE")) cats.add("斧");
            else if (n.contains("SHOVEL")) cats.add("锹");
            else if (n.contains("HOE")) cats.add("锄");
            else if (n.contains("SHIELD")) cats.add("盾牌");
            else if (n.contains("SPYGLASS")) cats.add("望远镜");
            else if (n.contains("ELYTRA")) cats.add("鞘翅");
        }
        return String.join("、", cats);
    }

    @SuppressWarnings("unchecked")
    private static String str(Map<?, ?> m, String key) { Object v = m.get(key); return v != null ? v.toString() : ""; }
    @SuppressWarnings("unchecked")
    private static Number num(Map<?, ?> m, String key, Number def) { Object v = m.get(key); return v instanceof Number n ? n : def; }
}
