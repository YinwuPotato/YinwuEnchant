# YinwuEnchant — Yinwu附魔
# YinwuEnchant — Custom Enchantments

**最新版本：v1.2.4** | [下载 Release](https://github.com/qumingjam/YinwuEnchant/releases/tag/v1.2.4)

21 个自定义附魔，基于 PDC（PersistentDataContainer）存储，全部 Folia 线程安全。

> ⚡ 完全兼容 Folia，正确处理跨区域调度；附魔以 PDC 存储于物品，非原版注册表注册。

---

## 附魔列表

| 附魔 | 效果 | 适用 | 来源 |
|------|------|------|------|
| 明目 | 无视黑暗效果 | 头盔 | 幽匿维度 |
| 黑暗行者 | 黑暗区域加速（3级） | 靴子 | 幽匿维度 |
| 外骨骼 | 减免摔落伤害（3级） | 护腿 | 幽匿维度 |
| 音波爆裂 | 蹲下蓄力发射音波 | 胸甲 | 幽匿维度 |
| 深层矿工 | 地下挖掘加速（5级） | 工具 | 幽匿维度 |
| 共振 | 盾牌反弹伤害 | 盾牌 | 幽匿维度 |
| 幽匿探测 | 望远镜高亮监守者 | 望远镜 | 幽匿维度 |
| 猫爪 | 恐吓苦力怕 | 靴子 | 击杀猪灵 |
| 狗头 | 恐吓骷髅 | 头盔 | 击杀潜影贝 |
| 幻影 | 驱离幻翼 | 胸甲/鞘翅 | 击杀幻翼 |
| 切肉大师 | 额外肉类掉落（3级），联动抢夺+火焰附加→熟肉 | 剑 | 击杀掠夺者 |
| 丰收 | 右键收获作物+自动补种 | 锄头 | 钓鱼 |
| 熔化 | 自动熔炼方块（100+配方） | 工具 | 未知 |
| 拾翠 | 草丛掉落绿宝石 | 锄头 | 未知 |
| 灵魂绑定 | 死亡保留物品 | 全装备 | 击杀幻术师(10%) |
| 熔岩行者 | 熔岩行走，离开后还原 | 靴子 | 下界探索 |
| 马蹄 | 提高步高（2级） | 靴子 | 未知 |
| 气囊 | 减少鞘翅撞墙伤害（3级） | 鞘翅 | 未知 |
| 护佑 | 不死图腾→传送重生点 | 胸甲 | 未知 |
| 吸血鬼诅咒 | [负面] 白天燃烧，夜晚回复 | 头盔 | 诅咒 |
| 失眠 | [负面] 无法入睡，夜晚力量/夜视，唱片机可安抚 | 头盔 | 诅咒 |

---

## 附魔独立开关（v1.2.4 新增）

玩家可在游戏内对**单个物品上的每个自定义附魔**独立开启/关闭：

1. `/ye gui` 打开附魔目录 → 右下角点「附魔开关」（或直接 `/ye toggle`）
2. 把物品放入二级界面左侧格子（支持 shift 点击整组放入）
3. 右侧列出该物品的全部自定义附魔，点击开关图标切换

**禁用效果：**
- 该附魔效果立即失效（如关闭切肉大师后击杀不再额外掉肉）
- 物品 Lore 对应行变为**删除线灰字 + 「(已禁用)」**
- 该物品全部自定义附魔禁用后不再发光
- 开关状态写入物品 PDC，取出界面后永久生效；不影响物品原生属性与其他附魔

---

## 原版互斥

通过 `getExclusiveEnchantments()` 自定义互斥关系，在附魔台/铁砧应用时检查跳过：

- 熔化 / 丰收 / 拾翠 ↔ **精准采集**（Silk Touch）
- 熔岩行者 ↔ **冰霜行者**（Frost Walker）

---

## 获取方式

- **怪物掉落** — 特定怪物按稀有度概率掉落对应附魔书（常见 0.10 / 稀有 0.05 / 极稀有 0.02 / 负面 0.30）
- **钓鱼** — 部分附魔可通过钓鱼获得
- **附魔台** — 附魔时概率附加随机自定义附魔
- **铁砧合并** — 两本同等级附魔书可合并升级
- **铁砧应用** — 附魔书应用到物品

---

## 架构

```
YinwuEnchant
├── YinwuEnchantments       # 主类（extends YinwuPlugin）
├── enchantments/           # 21 个附魔实现
├── gui/
│   ├── EnchantmentGUI           # /ye gui 附魔目录 GUI
│   └── EnchantmentToggleGUI     # 二级界面：单物品附魔独立开关
├── manager/
│   ├── EnchantmentManager       # 附魔管理器 + 事件订阅者模式
│   ├── EnchantmentAcquisition   # 获取系统（掉落/钓鱼/附魔台/铁砧）
│   ├── EnchantmentLore          # Lore 显示（含禁用态删除线）
│   ├── ConfigManager            # 配置管理
│   └── CommandHandler           # /ye 命令
└── api/                    # EnchantAPIImpl（ServicesManager）
```

---

## 命令

| 命令 | 说明 | 权限 |
|------|------|------|
| `/ye gui` | 打开附魔目录 GUI | `yinwu.enchant.use` |
| `/ye toggle` | 打开单物品附魔开关界面 | `yinwu.enchant.use` |
| `/ye give <玩家> <附魔> [等级]` | 给予附魔物品 | `yinwu.enchant.admin` |
| `/ye givebook <玩家> <附魔> [等级]` | 给予附魔书 | `yinwu.enchant.admin` |
| `/ye reload` | 重载配置 | `yinwu.enchant.admin` |

---

## 构建

```bash
git clone https://github.com/qumingjam/YinwuEnchant.git
cd YinwuEnchant
mvn clean package
```

产出：`target/YinwuEnchant-1.2.4.jar`

---

## 依赖

- **[YinwuPluginLib](https://github.com/qumingjam/YinwuPluginLib)**（必需）
- **Paper API 1.21+**（provided）

---

## 链接

- 仓库：[github.com/qumingjam/YinwuEnchant](https://github.com/qumingjam/YinwuEnchant)
- 关联：[YinwuForge](https://github.com/qumingjam/YinwuForge) | [YinwuRaid](https://github.com/qumingjam/YinwuRaid)
- 作者：Qumingjam
