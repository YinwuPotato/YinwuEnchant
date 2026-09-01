# YinwuEnchant — Yinwu附魔
# YinwuEnchant — Custom Enchantments

**最新版本：v1.2.5** | [下载 Release](https://github.com/qumingjam/YinwuEnchant/releases/tag/v1.2.5)

34 个自定义附魔，基于 PDC（PersistentDataContainer）存储，全部 Folia 线程安全。

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
| 灵魂绑定 | 死亡保留物品 | 全装备 | 击杀幻术师(2%) |
| 熔岩行者 | 熔岩行走，离开后还原 | 靴子 | 下界探索 |
| 马蹄 | 提高步高（2级） | 靴子 | 未知 |
| 气囊 | 减少鞘翅撞墙伤害（3级） | 鞘翅 | 未知 |
| 护佑 | 不死图腾→传送重生点 | 胸甲 | 未知 |
| 吸血鬼诅咒 | [负面] 白天燃烧，夜晚回复 | 头盔 | 诅咒 |
| 失眠 | [负面] 无法入睡，夜晚力量/夜视，唱片机可安抚 | 头盔 | 诅咒 |

### NeoEnchant 移植附魔（v1.2.5 新增 13 个）

| 附魔 | 效果 | 适用 | 来源 |
|------|------|------|------|
| 连锁挖矿 | 非潜行挖同矿脉，联动时运 | 镐 | 击杀铁傀儡 |
| 暴击 | 概率破甲 25% | 剑 | 击杀卫道士 |
| 生命汲取 | 攻击命中回复生命 | 剑 | 击杀女巫 |
| 狂怒 | 攻击 +5 / 护甲 -3 | 盔甲 | 击杀猪灵蛮兵 |
| 毒素 | 攻击使目标中毒 | 剑 | 击杀洞穴蜘蛛 |
| 回声射击 | 箭命中音爆 AOE | 弓/弩 | 击杀监守者 |
| 风暴之箭 | 箭命中召唤闪电（视觉+AoE+冷却） | 弓/弩 | 击杀溺尸 |
| 爆炸之箭 | 箭命中产生爆炸（火焰+TNT 混合特效） | 弓/弩 | 击杀苦力怕 |
| 脆弱诅咒 | [负面] 耐久损耗加快 | 耐久类 | 附魔台 |
| 附魔诅咒 | [负面] 无法再附魔/铁砧修改 | 耐久类 | 附魔台 |
| 笨拙诅咒 | [负面] 削弱武器伤害 | 剑 | 附魔台 |
| 矮人化 | [负面] 体型缩小+攻击削弱 | 护腿 | 附魔台 |
| 巨人化 | [负面] 体型变大 | 护腿 | 附魔台 |

---

## Deeper Dark 附魔拦截（v1.2.5 新增）

Deeper Dark 数据包产出的原版附魔书（`deeper_dark:*`，行为靠数据包函数）在 Folia 上**不生效**。YinwuEnchant 在铁砧/附魔台自动拦截，将 `deeper_dark:clearsight / darkspeed / resonate / safefall / shrieker_sense / sonic_boom / undermine` 转换成对应的 YinwuEnchant 附魔，使其真正生效。

---

## 附魔独立开关（v1.2.4 新增，v1.2.5 优化）

玩家可在游戏内对**单个物品上的每个自定义附魔**独立开启/关闭（27 格单格按钮布局）：

1. `/ye gui` 打开附魔目录 → 右下角点「附魔开关」（或直接 `/ye toggle`）
2. 把物品放入二级界面左侧格子（支持 shift 点击整组放入）
3. 右侧列出该物品的全部自定义附魔，点击按钮切换

**禁用效果：**
- 该附魔效果立即失效（如关闭切肉大师后击杀不再额外掉肉）
- 物品 Lore 对应行变为**删除线灰字 + 「(已禁用)」**
- 该物品全部自定义附魔禁用后不再发光
- 开关状态写入物品 PDC，取出界面后永久生效；不影响物品原生属性与其他附魔
- **诅咒类附魔（5 个）无法关闭**：显示锁定图标，点击无效（`setDisabled` 层也拒绝）

---

## 原版互斥

通过 `getExclusiveEnchantments()` 自定义互斥关系，在附魔台/铁砧应用时检查跳过：

- 熔化 / 丰收 ↔ **精准采集**（Silk Touch）
- 熔岩行者 ↔ **冰霜行者**（Frost Walker）

---

## 获取方式

- **怪物掉落** — 特定怪物按稀有度概率掉落对应附魔书（常见 0.10 / 稀有 0.05 / 极稀有 0.02 / 负面 0.30）
- **钓鱼** — 部分附魔可通过钓鱼获得
- **附魔台** — 附魔时按稀有度加权附加随机自定义附魔（可配概率/诅咒开关）
- **铁砧合并** — 两本同等级附魔书可合并升级
- **铁砧应用** — 附魔书应用到物品；v1.2.5 起**显示附魔花费**、支持 **shift+点击** 批量取走、满级重复合并不再显示冗余预览

---

## 架构

```
YinwuEnchant
├── YinwuEnchantments       # 主类（extends YinwuPlugin）
├── enchantments/           # 34 个附魔实现
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

产出：`target/YinwuEnchant-1.2.5.jar`

---

## 依赖

- **[YinwuPluginLib](https://github.com/qumingjam/YinwuPluginLib)**（必需）
- **Paper API 1.21+**（provided）

---

## 链接

- 仓库：[github.com/qumingjam/YinwuEnchant](https://github.com/qumingjam/YinwuEnchant)
- 关联：[YinwuForge](https://github.com/qumingjam/YinwuForge) | [YinwuRaid](https://github.com/qumingjam/YinwuRaid)
- 作者：Qumingjam
