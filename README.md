# YinwuEnchant — Yinwu附魔
# YinwuEnchant — Custom Enchantments

**最新版本：v1.2.1** | [下载 Release](https://github.com/qumingjam/YinwuEnchant/releases/tag/v1.2.1)

21 custom enchantments registered via Paper RegistryComposeEvent.

通过 Paper 原生 Registry 注册的 21 个自定义附魔，全部 Folia 线程安全。

> ⚡ 全部附魔 Folia 兼容，正确处理跨区域调度。

---

## Enchantment List | 附魔列表

| 附魔 | 效果 | 适用 | 来源 |
|------|------|------|------|
| 明目 | 无视黑暗效果 | 头盔 | 幽匿维度 |
| 黑暗行者 | 黑暗区域加速（3级） | 靴子 | 幽匿维度 |
| 外骨骼 | 减免摔落伤害（3级） | 护腿 | 幽匿维度 |
| 音波爆裂 | 蓄力发射音波 | 胸甲 | 幽匿维度 |
| 深层矿工 | 地下挖掘加速（5级） | 工具 | 幽匿维度 |
| 共振 | 盾牌反弹伤害 | 盾牌 | 幽匿维度 |
| 幽匿探测 | 望远镜高亮监守者 | 望远镜 | 幽匿维度 |
| 猫爪 | 恐吓苦力怕 | 靴子 | 击杀猪灵 |
| 狗头 | 恐吓骷髅 | 头盔 | 击杀潜影贝 |
| 幻影 | 驱离幻翼 | 胸甲/鞘翅 | 击杀幻翼 |
| 切肉大师 | 额外肉类掉落（3级），联动抢夺+火焰附加→熟肉 | 剑 | 击杀掠夺者 |
| 丰收 | 右键收获作物+自动补种 | 锄头 | 钓鱼 |
| 熔化 | 自动熔炼方块（100+配方） | 工具 | 未知 |
| 灵魂绑定 | 死亡保留物品 | 全装备 | 击杀幻术师(10%) |
| 熔岩行者 | 熔岩行走，5秒恢复 | 靴子 | 下界探索 |
| 拾翠 | 草丛掉落绿宝石 | 锄头 | 未知 |
| 马蹄 | 提高步高（2级） | 靴子 | 未知 |
| 气囊 | 减少鞘翅撞墙伤害（3级） | 鞘翅 | 未知 |
| 护佑 | 不死图腾→传送重生点 | 胸甲 | 未知 |
| 吸血鬼诅咒 | [负面] 白天燃烧，夜晚回复 | 头盔 | 诅咒 |
| 失眠 | [负面] 无法入睡 | 头盔 | 诅咒 |

---

## Mutual Exclusion | 原版互斥

通过 `exclusiveWith()` 由 Bukkit 原生系统处理：

- 熔化 / 丰收 / 拾翠 ↔ **精准采集**（Silk Touch）
- 熔岩行者 ↔ **深海探索者**（Depth Strider）/ **冰霜行者**（Frost Walker）
- 灵魂绑定 ↔ **消失诅咒**（Curse of Vanishing）

---

## Acquisition | 获取方式

- **怪物掉落** — 特定怪物概率掉落对应附魔书
- **钓鱼** — 部分附魔可通过钓鱼获得
- **铁砧合并** — 两本同级附魔书可合并升级

---

## Architecture | 架构

```
YinwuEnchant
├── YinwuEnchantBootstrap   # PluginBootstrap，RegistryComposeEvent 注册
├── YinwuEnchantments       # 主类
├── enchantments/           # 21 个附魔实现
├── gui/                    # /ye list 附魔列表 GUI
├── manager/
│   ├── EnchantmentManager       # 附魔管理器 + 事件订阅者模式
│   ├── EnchantmentAcquisition   # 获取系统（掉落/钓鱼/铁砧）
│   ├── ConfigManager            # 配置管理
│   └── CommandHandler           # /ye 命令
└── api/                    # EnchantAPIImpl（ServicesManager）
```

---

## Commands | 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/ye list` | 打开附魔列表GUI | `yinwuenchant.use` |
| `/ye give <玩家> <附魔> [等级]` | 给予附魔物品 | `yinwuenchant.admin` |
| `/ye givebook <玩家> <附魔> [等级]` | 给予附魔书 | `yinwuenchant.admin` |
| `/ye reload` | 重载配置 | `yinwuenchant.admin` |

---

## Build | 构建

```bash
git clone https://github.com/qumingjam/YinwuEnchant.git
cd YinwuEnchant
mvn clean package
```

产出：`target/YinwuEnchant-1.2.1.jar`

---

## Dependencies | 依赖

- **[YinwuPluginLib](https://github.com/qumingjam/YinwuPluginLib)**（必需）
- **Paper API 1.21+**（provided）

---

## Links | 链接

- 仓库：[github.com/qumingjam/YinwuEnchant](https://github.com/qumingjam/YinwuEnchant)
- 关联：[YinwuForge](https://github.com/qumingjam/YinwuForge) | [YinwuRaid](https://github.com/qumingjam/YinwuRaid)
- 作者：Qumingjam
