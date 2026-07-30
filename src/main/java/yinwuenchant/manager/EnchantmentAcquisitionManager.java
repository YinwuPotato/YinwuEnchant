package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
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
                    : drop.levelMin() + rand.nextInt(drop.levelMax() - drop.levelMin() + 1);

                ItemStack book = createEnchantedBook(id, level);
                if (book != null) {
                    event.getDrops().add(book);
                }
                break;
            }
        }
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

    // ==================== 附魔书创建（原版格式） ====================

    public ItemStack createEnchantedBook(String enchantmentId, int level) {
        CustomEnchantment ench = enchantmentManager.getEnchantment(enchantmentId);
        if (ench == null) return null;
        Enchantment reg = ench.getRegistered();
        if (reg == null) return null;
        if (level < 1 || level > ench.getMaxLevel()) return null;

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        if (meta == null) return null;

        meta.addStoredEnchant(reg, level, true);
        meta.setDisplayName(ench.getMaxLevel() == 1
            ? "§d" + ench.getDisplayName()
            : "§d" + ench.getDisplayName() + " " + getRomanNumeral(level));

        List<String> lore = new ArrayList<>();
        lore.add("§7适用物品: " + getApplicableItemsText(ench));
        lore.add("§7等级: " + level + "/" + ench.getMaxLevel());
        meta.setLore(lore);
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

        // 原版铁砧已自动处理注册过的附魔，我们只需补充书本合并升级逻辑
        if (item1.getType() == Material.ENCHANTED_BOOK && item2.getType() == Material.ENCHANTED_BOOK) {
            handleBookMerge(event, item1, item2);
        }
    }

    /** 两本书合并升级（原版不支持跨 namespace 合并，需要手动处理） */
    private void handleBookMerge(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack book1, ItemStack book2) {
        EnchantmentStorageMeta meta1 = (EnchantmentStorageMeta) book1.getItemMeta();
        EnchantmentStorageMeta meta2 = (EnchantmentStorageMeta) book2.getItemMeta();
        if (meta1 == null || meta2 == null) return;

        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            Enchantment reg = ench.getRegistered();
            if (reg == null) continue;

            int level1 = meta1.getStoredEnchantLevel(reg);
            int level2 = meta2.getStoredEnchantLevel(reg);
            if (level1 > 0 && level2 > 0 && level1 == level2) {
                int newLevel = level1 + 1;
                if (newLevel > ench.getMaxLevel()) continue;
                ItemStack result = createEnchantedBook(ench.getId(), newLevel);
                if (result != null) {
                    event.setResult(result);
                }
                return;
            }
        }
    }

    // ==================== 辅助 ====================

    private String getRomanNumeral(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }

    private String getApplicableItemsText(CustomEnchantment ench) {
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
