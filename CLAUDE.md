# YinwuEnchant — 自定义附魔插件

## 项目信息
- **技术栈**: Java 21, Maven, Paper API 1.21.8
- **打包**: `mvn clean package` → `target/YinwuEnchant-<version>.jar`
- **前置**: [YinwuPluginLib](https://github.com/YinwuPotato/YinwuPluginLib) 需先 `mvn clean install`
- **Folia 兼容**: 是
- **GitHub**: https://github.com/YinwuPotato/YinwuEnchant

## 功能
- 34 个自定义附魔（21 个原创 + 13 个 NeoEnchant 移植；PDC 存储，非原版注册表）
- 单物品附魔开关 GUI（27 格；诅咒类 5 个锁定不可关）
- 高度可配置，提供开发者 API

## 共享规则
继承自 `YinwuForge/agents.md`（适用于所有 Yinwu 插件）：

### 调度规范（Folia）
- ✅ 使用 `RegionScheduler` / `GlobalRegionScheduler` / `EntityScheduler`
- ❌ 禁止 `Bukkit.getScheduler()`、`runTask`、`runTaskAsynchronously`
- ❌ 初始延迟禁止为 `0L`（必须 ≥ `1L`）

### 代码风格
- 注释极简，无废话
- 仅使用 Paper / Folia API，禁止 NMS
