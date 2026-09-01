package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import yinwuenchant.enchantments.CustomEnchantment;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    /**
     * Deeper Dark 数据包注册的原版附魔 key → YinwuEnchant 附魔 id。
     * 数据包附魔在 Folia 上不生效（tick 函数不执行），在铁砧/附魔台拦截并转成 PDC 附魔。
     */
    private static final Map<String, String> DEEPER_DARK_MAP = Map.ofEntries(
        Map.entry("deeper_dark:clearsight", "clearsight"),
        Map.entry("deeper_dark:darkspeed", "darkspeed"),
        Map.entry("deeper_dark:resonate", "resonate"),
        Map.entry("deeper_dark:safefall", "safefall"),
        Map.entry("deeper_dark:shrieker_sense", "shrieker_sense"),
        Map.entry("deeper_dark:sonic_boom", "sonic_boom"),
        Map.entry("deeper_dark:undermine", "undermine")
    );

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

    /**
     * 附魔台模拟 —— 概率性附加自定义附魔（不保证必出）。
     * 全部附魔按稀有度权重挑 1 个，等级按 1/level 递减权重；诅咒默认可出，可配关闭。
     * 物品带「附魔诅咒」则整个附魔取消（含原版附魔）。
     */
    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        if (!plugin.getConfig().getBoolean("enchanting-table.enabled", true)) return;
        ItemStack item = event.getItem();
        if (item == null) return;

        // 附魔诅咒：物品无法再附魔（连同原版一起取消；仅在该附魔启用时生效）
        CustomEnchantment curse = enchantmentManager.getEnchantment("curse_of_enchant");
        if (curse != null && plugin.getConfigManager().isEnchantmentEnabled("curse_of_enchant")
                && curse.hasEnchantment(item)) {
            event.setCancelled(true);
            return;
        }

        // 附魔台 roll 到 deeper_dark 数据包附魔（Folia 上不生效）→ 转成 YinwuEnchant PDC 附魔
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        List<Enchantment> dd = new ArrayList<>();
        for (Enchantment e : toAdd.keySet()) {
            if (DEEPER_DARK_MAP.containsKey(e.getKey().asString())) dd.add(e);
        }
        for (Enchantment e : dd) {
            String id = DEEPER_DARK_MAP.get(e.getKey().asString());
            int lvl = toAdd.remove(e);
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench != null && ench.canApplyTo(item)) ench.applyEnchantment(item, Math.max(1, lvl));
        }

        boolean includeCurses = plugin.getConfig().getBoolean("enchanting-table.include-curses", true);

        List<CustomEnchantment> applicable = new ArrayList<>();
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            if (!includeCurses && ench.isCursed()) continue;
            if (ench.canApplyTo(item) && !ench.hasEnchantment(item)
                && !hasExclusiveConflict(item, ench)) applicable.add(ench);
        }
        if (applicable.isEmpty()) return;

        var rand = ThreadLocalRandom.current();
        double chance = plugin.getConfig().getDouble("enchanting-table.chance", 0.5);
        if (rand.nextDouble() >= chance) return;

        // 按稀有度权重挑 1 个，等级按 1/level 权重（等级越高越难出）
        CustomEnchantment chosen = weightedPick(applicable, rand);
        if (chosen == null) return;
        int level = weightedLevel(chosen, rand);
        chosen.applyEnchantment(item, level);
    }

    /** 按稀有度权重挑一个附魔：诅咒 30、mob-drops chance 推导（0.10→10 / 0.05→5 / 0.02→2）、默认 5 */
    private CustomEnchantment weightedPick(List<CustomEnchantment> applicable, ThreadLocalRandom rand) {
        double total = 0;
        for (CustomEnchantment ench : applicable) total += rarityWeight(ench);
        double r = rand.nextDouble() * total;
        for (CustomEnchantment ench : applicable) {
            r -= rarityWeight(ench);
            if (r <= 0) return ench;
        }
        return applicable.get(applicable.size() - 1);
    }

    private double rarityWeight(CustomEnchantment ench) {
        if (ench.isCursed()) return 30;
        var s = plugin.getConfig().getConfigurationSection("acquisition." + ench.getId());
        if (s != null) {
            List<?> raw = s.getList("mob-drops");
            if (raw != null && !raw.isEmpty() && raw.get(0) instanceof Map<?, ?> m) {
                double c = num(m, "chance", 0.0).doubleValue();
                if (c >= 0.20) return 30;
                if (c >= 0.08) return 10;
                if (c >= 0.03) return 5;
                return 2;
            }
        }
        return 5;
    }

    /** 1/level 递减权重选等级（等级越高越难出） */
    private int weightedLevel(CustomEnchantment ench, ThreadLocalRandom rand) {
        int max = ench.getMaxLevel();
        if (max <= 1) return 1;
        double total = 0;
        for (int l = 1; l <= max; l++) total += 1.0 / l;
        double r = rand.nextDouble() * total;
        for (int l = 1; l <= max; l++) {
            r -= 1.0 / l;
            if (r <= 0) return l;
        }
        return max;
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
        } else if (b2 && !item1.getType().isAir() && !isCustomBook(item1)) {
            // 原版摆法：物品在左(槽0) + 书在右(槽1) 应用附魔（书当祭品）
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

        // deeper_dark 数据包附魔合并（转 PDC，取最高等级）
        for (Map.Entry<Enchantment, Integer> entry : item1.getEnchantments().entrySet()) {
            String id = DEEPER_DARK_MAP.get(entry.getKey().getKey().asString());
            if (id == null) continue;
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench == null) continue;
            int merged = Math.max(entry.getValue(), item2.getEnchantmentLevel(entry.getKey()));
            if (merged > ench.getEnchantmentLevel(item1)) {
                result = ench.applyEnchantment(result, merged);
                changed = true;
            }
        }
        changed |= convertDeeperDarkEnchants(result);        // 清掉结果上残留的原版 deeper_dark

        if (changed) {
            applyRename(event, result);
            event.setResult(result);
            setAnvilCost(event, result);
        }
    }

    /** 把铁砧命名框输入的名字应用到结果物品（命名 + 合并同时生效） */
    @SuppressWarnings("removal") // AnvilInventory.getRenameText() 暂无 1.21.8 替代 API
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
     * 铁砧取走（自定义结果）。
     * - 正常点击：vanilla 读取结果槽交给玩家并消耗输入；成本由 setAnvilCost 设置的 AnvilView
     *   成本自动扣费（vanilla 对 PDC 附魔算出的成本=0，必须在 PrepareAnvilEvent 显式设置）。
     * - shift+点击：接管取走——取消原版点击，手动「给玩家物品 + 消耗输入槽 + 清结果槽 + 扣经验」。
     * Folia：铁砧背包归玩家所有，InventoryClickEvent 在玩家区域线程触发，安全。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvilTake(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event.getRawSlot() != 2) return;                 // 仅铁砧结果槽
        if (!(event.getView().getTopInventory() instanceof org.bukkit.inventory.AnvilInventory inv)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getCurrentItem();
        if (result == null || result.getType().isAir()) return;
        if (!hasCustomEnchantPDC(result)) return;            // 只接管自定义附魔结果

        int cost = customEnchantCost(result);
        if (player.getLevel() < cost) {
            player.sendMessage(org.bukkit.ChatColor.RED + "经验不足，需要 " + cost + " 级经验");
            event.setCancelled(true);
            return;
        }

        if (!event.isShiftClick()) {
            // 正常点击：取走与扣费均由 vanilla 完成（setAnvilCost 已把成本设为 customEnchantCost）
            return;
        }

        // shift+点击：vanilla 拒绝纯 PDC，手动接管取走
        event.setCancelled(true);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(result);
        if (!leftover.isEmpty()) {
            player.sendMessage(org.bukkit.ChatColor.RED + "背包空间不足，无法取走");
            return;                                          // 非堆叠物品 addItem 要么全放要么全拒
        }
        consumeInputs(inv);
        inv.setItem(2, null);
        if (cost > 0) player.setLevel(player.getLevel() - cost);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
    }

    /** 取走时消耗铁砧两个输入槽（各减 1，不足则清空） */
    private void consumeInputs(org.bukkit.inventory.AnvilInventory inv) {
        for (int i = 0; i <= 1; i++) {
            ItemStack input = inv.getItem(i);
            if (input == null || input.getType().isAir()) continue;
            if (input.getAmount() <= 1) inv.setItem(i, null);
            else { input.setAmount(input.getAmount() - 1); inv.setItem(i, input); }
        }
    }

    /** 取附魔书的「存储附魔」（原版书用 StoredEnchants，ItemStack.getEnchantments 对书为空） */
    private Map<Enchantment, Integer> getBookStoredEnchants(ItemStack book) {
        if (book == null || !book.hasItemMeta()) return Map.of();
        ItemMeta meta = book.getItemMeta();
        if (meta instanceof org.bukkit.inventory.meta.EnchantmentStorageMeta esm) {
            return esm.getStoredEnchants();
        }
        return Map.of();
    }

    /** 把物品上残留的 deeper_dark 原版附魔转成 YinwuEnchant PDC 附魔并移除原版 */
    private boolean convertDeeperDarkEnchants(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            String id = DEEPER_DARK_MAP.get(entry.getKey().getKey().asString());
            if (id == null) continue;
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench == null) continue;
            item.removeEnchantment(entry.getKey());
            ench.applyEnchantment(item, Math.max(1, entry.getValue()));
            changed = true;
        }
        return changed;
    }

    /** 原版铁砧等级逻辑：0→level、同等级升 1、已更高跳过（返回 0）；返回应用等级 */
    private int resolveAppliedLevel(CustomEnchantment ench, ItemStack target, int level) {
        int current = ench.getEnchantmentLevel(target);
        if (current == 0) return level;
        if (current == level && level < ench.getMaxLevel()) return level + 1;
        if (current > level) return 0;
        return level;
    }

    /** 结果物品上自定义附魔等级之和 = 铁砧成本（级） */
    private int customEnchantCost(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        int total = 0;
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            Integer lvl = meta.getPersistentDataContainer().get(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER);
            if (lvl != null) total += lvl;
        }
        return total;
    }

    /** 物品是否带任意自定义附魔 PDC（自定义书或已附魔物品） */
    private boolean hasCustomEnchantPDC(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            if (meta.getPersistentDataContainer().has(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER)) return true;
        }
        return false;
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
                    setAnvilCost(event, result);
                }
                return;
            }
        }

        // deeper_dark 数据包附魔书合并升级（两本同等级 → 升 1 级）
        Map<Enchantment, Integer> stored1 = getBookStoredEnchants(book1);
        Map<Enchantment, Integer> stored2 = getBookStoredEnchants(book2);
        for (Map.Entry<Enchantment, Integer> entry : stored1.entrySet()) {
            String id = DEEPER_DARK_MAP.get(entry.getKey().getKey().asString());
            if (id == null) continue;
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench == null) continue;
            Integer level1 = entry.getValue();
            Integer level2 = stored2.get(entry.getKey());
            if (level1 > 0 && level1.equals(level2)) {
                int newLevel = level1 + 1;
                if (newLevel > ench.getMaxLevel()) continue;
                ItemStack result = createEnchantedBook(ench.getId(), newLevel);
                if (result != null) {
                    applyRename(event, result);
                    event.setResult(result);
                    setAnvilCost(event, result);
                }
                return;
            }
        }

        // 两书同一自定义/deeper_dark 附魔且都已在最高级 → 无有效升级，清掉 vanilla 的冗余结果预览
        if (hasSameCustomEnchantAtMax(book1, book2)) {
            event.setResult(null);
        }
    }

    /** 两书是否含同一自定义/deeper_dark 附魔且两书都已在最高级（铁砧无可升级，应清冗余预览） */
    private boolean hasSameCustomEnchantAtMax(ItemStack book1, ItemStack book2) {
        Map<Enchantment, Integer> stored1 = getBookStoredEnchants(book1);
        Map<Enchantment, Integer> stored2 = getBookStoredEnchants(book2);
        for (Map.Entry<Enchantment, Integer> entry : stored1.entrySet()) {
            String id = DEEPER_DARK_MAP.get(entry.getKey().getKey().asString());
            if (id == null) continue;
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench == null) continue;
            if (entry.getValue() < ench.getMaxLevel()) continue;
            Integer other = stored2.get(entry.getKey());
            if (other != null && other.equals(entry.getValue())) return true;
        }
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            int l1 = ench.getEnchantmentLevel(book1);
            if (l1 == 0 || l1 < ench.getMaxLevel()) continue;
            if (ench.getEnchantmentLevel(book2) == l1) return true;
        }
        return false;
    }

    /** 附魔书 + 物品：把附魔应用到物品（铁砧） */
    private void handleBookApply(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack book, ItemStack target) {
        ItemMeta bookMeta = book.getItemMeta();
        if (bookMeta == null) return;

        // 附魔诅咒：目标物品无法被铁砧修改（附魔应用；仅在该附魔启用时生效）
        CustomEnchantment curse = enchantmentManager.getEnchantment("curse_of_enchant");
        if (curse != null && plugin.getConfigManager().isEnchantmentEnabled("curse_of_enchant")
                && curse.hasEnchantment(target)) return;

        // deeper_dark 数据包附魔书（Folia 上数据包附魔不生效）→ 转成 YinwuEnchant PDC 附魔
        for (Map.Entry<Enchantment, Integer> entry : getBookStoredEnchants(book).entrySet()) {
            String id = DEEPER_DARK_MAP.get(entry.getKey().getKey().asString());
            if (id == null) continue;
            CustomEnchantment ench = enchantmentManager.getEnchantment(id);
            if (ench == null || !ench.canApplyTo(target)) continue;
            if (hasExclusiveConflict(target, ench)) continue;

            int applied = resolveAppliedLevel(ench, target, entry.getValue());
            if (applied <= 0) continue;
            ItemStack result = target.clone();
            result = ench.applyEnchantment(result, applied);
            convertDeeperDarkEnchants(result);             // 清掉结果上残留的原版 deeper_dark
            applyRename(event, result);
            event.setResult(result);
            setAnvilCost(event, result);
            return;
        }

        // 原有 PDC 附魔书
        for (CustomEnchantment ench : enchantmentManager.getAllEnchantments().values()) {
            Integer level = bookMeta.getPersistentDataContainer().get(ench.getEnchantmentKey(),
                org.bukkit.persistence.PersistentDataType.INTEGER);
            if (level == null || level <= 0) continue;
            if (!ench.canApplyTo(target)) continue;
            if (hasExclusiveConflict(target, ench)) continue;

            int applied = resolveAppliedLevel(ench, target, level);
            if (applied <= 0) continue;
            ItemStack result = target.clone();
            result = ench.applyEnchantment(result, applied);
            applyRename(event, result);
            event.setResult(result);
            setAnvilCost(event, result);
            return;
        }
    }

    /**
     * 设铁砧成本（显示 + vanilla 取走时扣费）。
     * 1.21 必须走 AnvilView API：AnvilInventory#setRepairCostAmount 已失效（SPIGOT-7853），不显示也不生效。
     * 显示值与 onAnvilTake 手动扣费用的 customEnchantCost 保持一致。
     */
    private void setAnvilCost(org.bukkit.event.inventory.PrepareAnvilEvent event, ItemStack result) {
        if (event.getView() instanceof org.bukkit.inventory.view.AnvilView view) {
            view.setRepairCost(Math.min(39, customEnchantCost(result)));
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
