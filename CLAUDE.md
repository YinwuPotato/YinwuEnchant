# YinwuEnchant — 自定义附魔插件

## 项目信息
- **技术栈**: Java 21, Maven, Paper API 1.21.4
- **打包**: `mvn clean package` → `target/YinwuEnchant-<version>.jar`
- **Folia 兼容**: 是
- **GitHub**: https://github.com/YinwuPotato/YinwuEnchant

## 功能
- 12 个独特的自定义附魔（战斗、探索、农业、防御等）
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
