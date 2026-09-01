package yinwuenchant.manager;

import yinwuenchant.YinwuEnchantments;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ConfigManager {
    private final YinwuEnchantments plugin;
    private FileConfiguration config;

    // 配置缓存（使用 ConcurrentHashMap 确保线程安全）
    private final Map<String, Boolean> enchantmentEnabled = new ConcurrentHashMap<>();
    private final Map<String, Object> settings = new ConcurrentHashMap<>();

    public ConfigManager(YinwuEnchantments plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // 附魔 lore 颜色（视觉仿原版附魔行：正常附魔蓝、诅咒附魔红）
        EnchantmentLore.setColors(
            config.getString("lore-color", "&9"),
            config.getString("curse-lore-color", "&c")
        );

        // 清空缓存
        enchantmentEnabled.clear();
        settings.clear();

        // 加载附魔启用状态
        enchantmentEnabled.put("clearsight", config.getBoolean("enchantments.clearsight.enabled", true));
        enchantmentEnabled.put("darkspeed", config.getBoolean("enchantments.darkspeed.enabled", true));
        enchantmentEnabled.put("resonate", config.getBoolean("enchantments.resonate.enabled", true));
        enchantmentEnabled.put("safefall", config.getBoolean("enchantments.safefall.enabled", true));
        enchantmentEnabled.put("shrieker_sense", config.getBoolean("enchantments.shrieker_sense.enabled", true));
        enchantmentEnabled.put("sonic_boom", config.getBoolean("enchantments.sonic_boom.enabled", true));
        enchantmentEnabled.put("undermine", config.getBoolean("enchantments.undermine.enabled", true));

        // 加载各种设置
        // Clearsight
        settings.put("clearsight.check-interval", config.getInt("enchantments.clearsight.check-interval", 20));

        // Darkspeed
        settings.put("darkspeed.dark-light-level", config.getInt("enchantments.darkspeed.dark-light-level", 7));
        settings.put("darkspeed.speed-per-level", config.getDouble("enchantments.darkspeed.speed-per-level", 0.1));
        settings.put("darkspeed.particle-interval", config.getInt("enchantments.darkspeed.particle-interval", 10));
        settings.put("darkspeed.sound-interval", config.getInt("enchantments.darkspeed.sound-interval", 40));
        settings.put("darkspeed.particle-type", config.getString("enchantments.darkspeed.particle-type", "SOUL_FIRE_FLAME"));
        settings.put("darkspeed.sound-type", config.getString("enchantments.darkspeed.sound-type", "BLOCK_SOUL_SAND_STEP"));

        // Resonate
        settings.put("resonate.damage-reflect-per-level", config.getDouble("enchantments.resonate.damage-reflect-per-level", 0.2));
        settings.put("resonate.sound-type", config.getString("enchantments.resonate.sound-type", "ENTITY_IRON_GOLEM_HURT"));

        // Safefall
        settings.put("safefall.damage-reduction-per-level", config.getDouble("enchantments.safefall.damage-reduction-per-level", 0.3));
        settings.put("safefall.safe-fall-height-per-level", config.getInt("enchantments.safefall.safe-fall-height-per-level", 2));

        // Shrieker Sense
        settings.put("shrieker_sense.highlight-range", config.getInt("enchantments.shrieker_sense.highlight-range", 48));
        settings.put("shrieker_sense.highlight-color", config.getString("enchantments.shrieker_sense.highlight-color", "#00FF00"));
        settings.put("shrieker_sense.highlight-duration", config.getInt("enchantments.shrieker_sense.highlight-duration", 1200));
        settings.put("shrieker_sense.text-display-offset.x", config.getDouble("enchantments.shrieker_sense.text-display-offset.x", 0.5));
        settings.put("shrieker_sense.text-display-offset.y", config.getDouble("enchantments.shrieker_sense.text-display-offset.y", 0.5));
        settings.put("shrieker_sense.text-display-offset.z", config.getDouble("enchantments.shrieker_sense.text-display-offset.z", 0.5));
        settings.put("shrieker_sense.cooldown", config.getInt("enchantments.shrieker_sense.cooldown", 10));

        // Sonic Boom（已自加载 config，此处仅保留 enabled）
        enchantmentEnabled.put("sonic_boom", config.getBoolean("enchantments.sonic_boom.enabled", true));

        // Undermine
        settings.put("undermine.max-efficiency-multiplier", config.getDouble("enchantments.undermine.max-efficiency-multiplier", 2.0));
        settings.put("undermine.efficiency-per-level", config.getDouble("enchantments.undermine.efficiency-per-level", 0.2));
        settings.put("undermine.trigger-y-max", config.getInt("enchantments.undermine.trigger-y-max", 60));
        settings.put("undermine.trigger-light-max", config.getInt("enchantments.undermine.trigger-light-max", 7));
        settings.put("undermine.check-interval", config.getInt("enchantments.undermine.check-interval", 10));

        // CatsPaw（猫爪）
        enchantmentEnabled.put("cats_paw", config.getBoolean("enchantments.cats_paw.enabled", true));

        // Nasus（狗头）
        enchantmentEnabled.put("nasus", config.getBoolean("enchantments.nasus.enabled", true));

        // MasterOfBeefSlicing（切肉大师）
        enchantmentEnabled.put("master_of_beef_slicing", config.getBoolean("enchantments.master_of_beef_slicing.enabled", true));
        settings.put("master_of_beef_slicing.drop-amount-level-1-min", config.getInt("enchantments.master_of_beef_slicing.drop-amount-level-1-min", 0));
        settings.put("master_of_beef_slicing.drop-amount-level-1-max", config.getInt("enchantments.master_of_beef_slicing.drop-amount-level-1-max", 2));
        settings.put("master_of_beef_slicing.drop-amount-level-2-min", config.getInt("enchantments.master_of_beef_slicing.drop-amount-level-2-min", 1));
        settings.put("master_of_beef_slicing.drop-amount-level-2-max", config.getInt("enchantments.master_of_beef_slicing.drop-amount-level-2-max", 4));
        settings.put("master_of_beef_slicing.drop-amount-level-3-min", config.getInt("enchantments.master_of_beef_slicing.drop-amount-level-3-min", 2));
        settings.put("master_of_beef_slicing.drop-amount-level-3-max", config.getInt("enchantments.master_of_beef_slicing.drop-amount-level-3-max", 6));

        // Phantom（幻影守护者）
        enchantmentEnabled.put("phantom", config.getBoolean("enchantments.phantom.enabled", true));

        // Harvest（丰收）
        enchantmentEnabled.put("harvest", config.getBoolean("enchantments.harvest.enabled", true));
        settings.put("harvest.range", config.getInt("enchantments.harvest.range", 2));

        // EmeraldTill（绿宝石耕耘）
        enchantmentEnabled.put("emerald_till", config.getBoolean("enchantments.emerald_till.enabled", true));
        // Airbag（安全气囊）
        enchantmentEnabled.put("airbag", config.getBoolean("enchantments.airbag.enabled", true));
        // Bless（祝福）
        enchantmentEnabled.put("bless", config.getBoolean("enchantments.bless.enabled", true));
        // VampireCurse（吸血鬼诅咒）
        enchantmentEnabled.put("vampire_curse", config.getBoolean("enchantments.vampire_curse.enabled", true));
        // Insomnia（失眠）
        enchantmentEnabled.put("insomnia", config.getBoolean("enchantments.insomnia.enabled", true));
        // Soulbound（灵魂绑定）
        enchantmentEnabled.put("soulbound", config.getBoolean("enchantments.soulbound.enabled", true));
        // Smelt（熔炼）
        enchantmentEnabled.put("smelt", config.getBoolean("enchantments.smelt.enabled", true));
        // StepUp（马蹄）
        enchantmentEnabled.put("step_up", config.getBoolean("enchantments.step_up.enabled", true));
        // LavaWalker（熔岩行者）
        enchantmentEnabled.put("lava_walker", config.getBoolean("enchantments.lava_walker.enabled", true));

        // ==== NeoEnchant 移植附魔（13 个，正向 8 + 诅咒 5）====
        enchantmentEnabled.put("vein_miner", config.getBoolean("enchantments.vein_miner.enabled", true));
        enchantmentEnabled.put("critical", config.getBoolean("enchantments.critical.enabled", true));
        enchantmentEnabled.put("life_steal", config.getBoolean("enchantments.life_steal.enabled", true));
        enchantmentEnabled.put("fury", config.getBoolean("enchantments.fury.enabled", true));
        enchantmentEnabled.put("poison_aspect", config.getBoolean("enchantments.poison_aspect.enabled", true));
        enchantmentEnabled.put("echo_shot", config.getBoolean("enchantments.echo_shot.enabled", true));
        enchantmentEnabled.put("storm_arrow", config.getBoolean("enchantments.storm_arrow.enabled", true));
        enchantmentEnabled.put("explosive_arrow", config.getBoolean("enchantments.explosive_arrow.enabled", true));
        enchantmentEnabled.put("curse_of_breaking", config.getBoolean("enchantments.curse_of_breaking.enabled", true));
        enchantmentEnabled.put("curse_of_enchant", config.getBoolean("enchantments.curse_of_enchant.enabled", true));
        enchantmentEnabled.put("curse_of_clumsiness", config.getBoolean("enchantments.curse_of_clumsiness.enabled", true));
        enchantmentEnabled.put("dwarfed", config.getBoolean("enchantments.dwarfed.enabled", true));
        enchantmentEnabled.put("oversize", config.getBoolean("enchantments.oversize.enabled", true));

        // NeoEnchant 移植附魔的具体参数（不缓存则 getInt/getBoolean 兜底值会错）
        settings.put("vein_miner.max-blocks", config.getInt("enchantments.vein_miner.max-blocks", 32));
        settings.put("critical.chance-per-level", config.getDouble("enchantments.critical.chance-per-level", 0.04));
        settings.put("critical.armor-penetration", config.getDouble("enchantments.critical.armor-penetration", 0.25));
        settings.put("echo_shot.damage", config.getDouble("enchantments.echo_shot.damage", 6.0));
        settings.put("storm_arrow.radius", config.getDouble("enchantments.storm_arrow.radius", 3.0));
        settings.put("storm_arrow.damage", config.getDouble("enchantments.storm_arrow.damage", 5.0));
        settings.put("storm_arrow.cooldown", config.getInt("enchantments.storm_arrow.cooldown", 3));
        settings.put("storm_arrow.bolt-count", config.getInt("enchantments.storm_arrow.bolt-count", 4));
        settings.put("explosive_arrow.power", config.getDouble("enchantments.explosive_arrow.power", 2.0));
        settings.put("explosive_arrow.break-blocks", config.getBoolean("enchantments.explosive_arrow.break-blocks", false));
        settings.put("explosive_arrow.set-fire", config.getBoolean("enchantments.explosive_arrow.set-fire", false));

        // 调试模式
        settings.put("debug", config.getBoolean("debug", false));

        // ✅ 验证配置合法性
        validateConfig();
    }

    /**
     * 验证配置值的合法性
     */
    private void validateConfig() {
        // 光照等级验证 (0-15)
        int darkLightLevel = config.getInt("enchantments.darkspeed.dark-light-level", 7);
        if (darkLightLevel < 0 || darkLightLevel > 15) {
            plugin.getLogger().warning("darkspeed.dark-light-level 超出范围 (0-15)，使用默认值 7");
            settings.put("darkspeed.dark-light-level", 7);
        }

        // 比例验证 (0.0-1.0)
        double damageReduction = config.getDouble("enchantments.safefall.damage-reduction-per-level", 0.3);
        if (damageReduction < 0.0 || damageReduction > 1.0) {
            plugin.getLogger().warning("safefall.damage-reduction-per-level 超出范围 (0.0-1.0)，使用默认值 0.3");
            settings.put("safefall.damage-reduction-per-level", 0.3);
        }

        double damageReflect = config.getDouble("enchantments.resonate.damage-reflect-per-level", 0.2);
        if (damageReflect < 0.0 || damageReflect > 1.0) {
            plugin.getLogger().warning("resonate.damage-reflect-per-level 超出范围 (0.0-1.0)，使用默认值 0.2");
            settings.put("resonate.damage-reflect-per-level", 0.2);
        }

        // 挖掘速度倍率验证 (> 0)
        double efficiencyMultiplier = config.getDouble("enchantments.undermine.max-efficiency-multiplier", 2.0);
        if (efficiencyMultiplier <= 0) {
            plugin.getLogger().warning("undermine.max-efficiency-multiplier 必须大于 0，使用默认值 2.0");
            settings.put("undermine.max-efficiency-multiplier", 2.0);
        }

        // Y坐标验证
        int triggerYMax = config.getInt("enchantments.undermine.trigger-y-max", 60);
        if (triggerYMax < -64 || triggerYMax > 320) {
            plugin.getLogger().warning("undermine.trigger-y-max 超出合理范围 (-64-320)，使用默认值 60");
            settings.put("undermine.trigger-y-max", 60);
        }
    }

    public boolean isEnchantmentEnabled(String enchantment) {
        return enchantmentEnabled.getOrDefault(enchantment, true);
    }

    public int getInt(String key) {
        Object value = settings.get(key);
        if (value == null) {
            // 如果配置不存在，尝试从原始配置中读取
            return config.getInt(key, 10); // 默认10刻
        }
        return (Integer) value;
    }

    public double getDouble(String key) {
        Object value = settings.get(key);
        if (value == null) {
            // 如果配置不存在，尝试从原始配置中读取
            return config.getDouble(key, 1.0); // 默认1.0
        }
        return (Double) value;
    }

    public String getString(String key) {
        Object value = settings.get(key);
        if (value == null) {
            // 如果配置不存在，尝试从原始配置中读取
            return config.getString(key, "");
        }
        return (String) value;
    }

    public boolean getBoolean(String key) {
        Object value = settings.get(key);
        if (value == null) {
            // 如果配置不存在，尝试从原始配置中读取
            return config.getBoolean(key, true);
        }
        return (Boolean) value;
    }

    public long getLong(String key) {
        Object value = settings.get(key);
        if (value == null) {
            // 如果配置不存在，尝试从原始配置中读取
            return config.getLong(key, 2000L); // 默认2000毫秒
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return (Long) value;
    }

    public FileConfiguration getRawConfig() {
        return config;
    }

    public void reload() {
        loadConfig();
    }
}
