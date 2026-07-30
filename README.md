# YinwuEnchant

**最新版本：v1.2.0** | [下载 Release](https://github.com/qumingjam/YinwuEnchant/releases/tag/v1.2.0)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21+-brightgreen.svg)](https://minecraft.net)
[![Folia](https://img.shields.io/badge/Folia-✓-blue.svg)](https://papermc.io/software/folia)
[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> 为 Minecraft 服务器添加 12 个独特的自定义附魔，完美兼容 Folia 区域化线程架构

---

## ✨ 特性

- **🚀 Folia 原生支持** - 完全兼容 Folia 的区域化线程调度，避免传统 Bukkit 调度器问题
- **⚔️ 12 个独特附魔** - 涵盖战斗、探索、农业、防御等多个方面
- **🎮 平衡设计** - 每个附魔都经过精心设计，不会破坏游戏平衡
- **⚙️ 高度可配置** - 几乎所有参数都可在配置文件中调整
- **📦 开发者 API** - 提供简单的 API 供其他插件调用

---

## 📋 附魔列表

### 防御类

| 附魔 | 适用物品 | 等级 | 效果 |
|------|---------|------|------|
| **明目** (clearsight) | 头盔 | I | 免疫黑暗和失明效果 |
| **外骨骼** (safefall) | 护腿 | I-III | 减少摔落伤害，增加安全掉落高度 |
| **幻影守护者** (phantom) | 胸甲、鞘翅 | I | 防止幻翼攻击 |
| **共振** (resonate) | 盾牌 | I | 格挡时反弹部分伤害给攻击者 |

### 战斗类

| 附魔 | 适用物品 | 等级 | 效果 |
|------|---------|------|------|
| **猫爪** (cats_paw) | 靴子 | I-III | 周期性恐吓周围苦力怕 |
| **狗头** (nasus) | 头盔 | I-III | 周期性恐吓周围骷髅类怪物 |
| **切肉大师** (master_of_beef_slicing) | 剑 | I-III | 击杀动物时额外掉落肉类 |

### 探索类

| 附魔 | 适用物品 | 等级 | 效果 |
|------|---------|------|------|
| **黑暗行者** (darkspeed) | 靴子 | I-III | 在黑暗区域获得速度加成 |
| **幽匿探测** (shrieker_sense) | 望远镜 | I | 探测周围的监守者和尖啸体 |
| **音波爆裂** (sonic_boom) | 胸甲 | I | 熄灭附近的灵魂营火和灵魂火 |

### 工具类

| 附魔 | 适用物品 | 等级 | 效果 |
|------|---------|------|------|
| **深层矿工** (undermine) | 镐、锹、斧 | I-V | 在地下低光照环境获得挖掘速度加成 |
| **丰收** (harvest) | 锄头 | I | 右键收获 5×5×5 范围的成熟作物 |

---

## 📥 安装

### 要求

- **服务端**: Paper 1.21+ 或 Folia 1.21+
- **Java**: Java 21 或更高版本

### 步骤

1. 从 [Releases](https://github.com/qumingjam/YinwuEnchant/releases) 下载最新版本的插件
2. 将 `YinwuEnchant-1.1.1.jar` 放入服务器的 `plugins/` 文件夹
3. 重启服务器或加载插件
4. 编辑 `plugins/YinwuEnchant/config.yml` 进行自定义配置
5. 使用 `/ye reload` 重载配置

---

## 🎮 使用方法

### 获取附魔书

附魔书可通过以下方式获得：

| 附魔 | 获取方式 | 掉落概率 |
|------|---------|---------|
| 猫爪 | 击杀猪灵 | 2% |
| 狗头 | 击杀潜影贝 | 1.5% |
| 切肉大师 | 击杀掠夺者/卫道士/唤魔者 | 3% |
| 幻影守护者 | 击杀幻翼 | 5% |
| 丰收 | 钓鱼 | 2% |
| 其他 | 幽匿维度探索 | - |

### 管理员命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/ye` | `yinwuenchant.use` | 显示帮助信息 |
| `/ye list` | `yinwuenchant.use` | 打开附魔列表 GUI |
| `/ye give <玩家> <附魔ID> [等级]` | `yinwuenchant.admin` | 给予玩家带附魔的物品 |
| `/ye givebook <玩家> <附魔ID> [等级]` | `yinwuenchant.admin` | 给予玩家附魔书 |
| `/ye reload` | `yinwuenchant.admin` | 重载配置文件 |

### 附魔 ID 列表

```
clearsight, darkspeed, resonate, safefall, shrieker_sense,
sonic_boom, undermine, cats_paw, nasus, master_of_beef_slicing,
phantom, harvest
```

---

## ⚙️ 配置

配置文件位于 `plugins/YinwuEnchant/config.yml`，包含：

- 每个附魔的启用/禁用开关
- 检测范围、冷却时间、效果强度等参数
- 附魔获取概率设置
- 调试模式开关

### 示例配置

```yaml
enchantments:
  cats_paw:
    enabled: true
    range-level-1: 5
    range-level-2: 7
    range-level-3: 9
    check-interval: 60
  
  harvest:
    enabled: true
    range: 2  # 5x5x5 范围
    cooldown: 3  # 秒
```

---

## 🔌 开发者 API

其他插件可以通过 API 生成自定义附魔书：

```java
// 获取插件实例
Plugin yinwuPlugin = Bukkit.getPluginManager().getPlugin("YinwuEnchant");
if (yinwuPlugin instanceof YinwuEnchantments) {
    YinwuEnchantments yinwu = (YinwuEnchantments) yinwuPlugin;
    EnchantmentAcquisitionManager api = yinwu.getAcquisitionManager();
    
    // 创建附魔书
    ItemStack book = api.createEnchantedBook("cats_paw", 3);  // 猫爪 III
    
    // 获取所有附魔ID
    String[] ids = api.getAvailableEnchantmentIds();
    
    // 获取附魔最大等级
    int maxLevel = api.getMaxLevel("cats_paw");  // 返回: 3
}
```

---

## 🛠️ 技术细节

### Folia 兼容性

本插件完全针对 Folia 区域化线程架构设计：

- ✅ 使用 `RegionScheduler` 和 `EntityScheduler` 进行线程调度
- ✅ 所有跨区域操作都通过正确的调度器执行
- ✅ 使用 `ConcurrentHashMap` 保证线程安全
- ✅ 禁止直接使用 `Bukkit.getScheduler()`

### 性能优化

- 事件订阅者模式，只处理需要的事件
- 配置缓存，避免频繁读取文件
- 智能冷却系统，防止频繁触发
- 调试日志使用 `FINE` 级别，生产环境无额外开销

---

## 📜 许可证

本项目采用 [MIT 许可证](LICENSE) 开源。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

---

## 📧 联系

如有问题或建议，欢迎通过 GitHub Issues 联系。

---

<p align="center">Made with ❤️ by qumingjam</p>

---

## 📋 更新日志 (v1.2.0)

- `Harvest.java`/`MasterOfBeefSlicing.java`/`EnchantmentAcquisitionManager.java`：`new Random()` 替换为 `ThreadLocalRandom.current()`
- 8 个附魔类补充缺失的 `@Override` 注解
- `EnchantmentManager.eventSubscribers`：`HashMap` → `ConcurrentHashMap`
- `EnchantmentAcquisitionManager`：新增 `NamespacedKey` 缓存 `getKey()`，避免每 tick 重建
- `onDisable()` 新增 `Bukkit.getGlobalRegionScheduler().cancelTasks(this)` 和 `HandlerList.unregisterAll(this)`
- 附魔类移除冗余 `isOnline()` 检查（`getScheduler().run()` 已处理）
