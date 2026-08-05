package yinwuenchant;

import yinwuenchant.api.EnchantAPIImpl;
import yinwuenchant.manager.CommandHandler;
import yinwuenchant.manager.ConfigManager;
import yinwuenchant.manager.EnchantmentAcquisitionManager;
import yinwuenchant.manager.EnchantmentManager;
import yinwuenchant.manager.EventListener;
import net.yinwu.lib.api.EnchantAPI;
import net.yinwu.lib.plugin.YinwuPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

public final class YinwuEnchantments extends YinwuPlugin {
    private ConfigManager configManager;
    private EnchantmentManager enchantmentManager;
    private EnchantmentAcquisitionManager acquisitionManager;
    private CommandHandler commandHandler;
    private EventListener eventListener;

    @Override
    public String name() {
        return "YinwuEnchant";
    }

    @Override
    public void enable() {
        // 初始化管理器
        configManager = new ConfigManager(this);
        enchantmentManager = new EnchantmentManager(this, configManager);
        acquisitionManager = new EnchantmentAcquisitionManager(this, enchantmentManager);
        commandHandler = new CommandHandler(this, enchantmentManager, acquisitionManager, configManager);
        eventListener = new EventListener(this, enchantmentManager);

        // 注册命令（Bukkit 插件用 getCommand）
        getCommand("ye").setExecutor(commandHandler);
        getCommand("ye").setTabCompleter(commandHandler);

        // 启用所有附魔
        enchantmentManager.enableAll();

        // 注册 EnchantAPI 服务（供其他 Yinwu 插件调用）
        Bukkit.getServicesManager().register(EnchantAPI.class,
            new EnchantAPIImpl(this, enchantmentManager, acquisitionManager),
            this, ServicePriority.Normal);

        // 只在调试模式下输出详细信息
        if (configManager.getBoolean("debug")) {
            getLogger().info("已加载 " + enchantmentManager.getAllEnchantments().size() + " 个附魔。");
            getLogger().info("EnchantAPI 已注册");
        }
    }

    @Override
    public void disable() {
        if (enchantmentManager != null) {
            enchantmentManager.disableAll();
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public EnchantmentManager getEnchantmentManager() { return enchantmentManager; }
    public EnchantmentAcquisitionManager getAcquisitionManager() { return acquisitionManager; }
}
