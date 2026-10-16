# 飞行传送示例（air-teleport）

演示 `mineturn:teleport_offset` 的 **`allow_air`**，并用**函数 AI** 让一只会飞的生物真正调用它。

- **命名空间**：`air_teleport_example`
- **安装**：把本目录压成 zip，或整个放进 `<world>/datapacks/`，然后 `/reload`
- **依赖**：无（只用本模组自带的 `mineturn:ghast_fireball`；不需要任何外部模组）

> **加载与运行语义仍需游戏内验证**：本文只保证通过静态检查与解析器一致性，
> **尚未在真实存档中加载运行**。见文末「验证状态」。

---

## 它覆盖了什么

本示例以 `priority: 0` **覆盖内置的恶魂定义**（内置为 `-100`）。不同资源 ID 注册同一种生物时，优先级相同会报错，不能依靠数据包顺序消除冲突。恶魂原本走模组内置的 `mineturn:ai/ghast/turn`
（火球 + 游荡），装了这个包之后改为：

| 顺序 | 行为 |
| --- | --- |
| 1 | 火球 `mineturn:ghast_fireball` 冷却就绪 → 打火球 |
| 2 | 否则 `air_teleport_example:blink_up` 冷却就绪 → **空中闪现**（+2 东、+2 上） |
| 3 | 都在冷却 → 等待 |

**覆盖范围只有 `minecraft:ghast`**，且只作用于**恶魂实体类型**，不影响玩家物品、其它生物或方块。

### 与其它示例的关系

| 示例 | 是否冲突 |
| --- | --- |
| `teleport-action` | **不冲突**。它用 `teleport_example:blink_east` 映射**木棍物品**；本示例只覆盖恶魂的 AI，不碰物品 |
| `function-ai` / `function-ai-override` | **不冲突**。两者覆盖 `minecraft:zombie`，本示例覆盖恶魂。若另有不同资源 ID 的恶魂定义，则比较 `priority`，同优先级报错 |
| `spatial-boss` | 可能覆盖部分 Boss；本示例只碰恶魂 |

**卸载**：删除 `<world>/datapacks/` 下的本目录（或对应 zip）后 `/reload`。
内置的恶魂 AI 立即恢复，无需改任何配置。

---

## 两个动作

| 动作 ID | 位移 | `allow_air` | 用途 |
| --- | --- | --- | --- |
| `air_teleport_example:blink_up` | +2 东、**+2 上** | **`true`** | 演示无支撑空中落点 |
| `air_teleport_example:blink_ground_only` | +2 东、0 上 | **`false`** | **对照**：证明同一位置在空中会被拒绝 |

两者都是 `self=true`、`consume=0`、`cooldown_av=100`、`action_cost = {main:1, bonus:0}`，
`range=8`（三轴曼哈顿上限）、`require_sight=false`、`allow_water=false`。

`blink_ground_only` **没有被函数 AI 调用**——它是留给手动测试的对照项，
用来验证「同一个飞在空中的恶魂，`allow_air=false` 会被拒绝」。

### 为什么这样选参数

- **位移很小（2 格）**：偏移是固定值，每回合都会重复。位移太大会很快撞到建筑高度上限。
- **`require_sight=false`**：闪现是自身位移，不需要看着目标；也让恶魂在没有敌人时仍能演示。
- **`action_cost = {main:1}`**：一次主要行动，符合普通技能的成本直觉。

---

## 怎么触发

### 自动（函数 AI）

让恶魂和玩家进入战斗即可。每个恶魂回合按上表顺序决策。
恶魂需要**先有目标**才会轮到自己行动，所以要先进入一场战斗。

> `mineturn ai use <action>` 对 `self=true` 的动作**自动以自身为目标**，
> 因此闪现不需要先选中敌人。

### 手动（核对 `allow_air` 的差异）

用命令强制让一个**飞在空中的恶魂**尝试两个动作：

```mcfunction
# 让它尝试无支撑落点：应当成功
execute as @e[type=minecraft:ghast,limit=1] run function air_teleport_example:ghast/turn

# 直接比较两个动作的预检结果（对照项）
execute as @e[type=minecraft:ghast,limit=1] run mineturn ai use air_teleport_example:blink_ground_only
```

`blink_ground_only` 在空中会失败，原因是 `allow_air=false`：

```
请选择有支撑的落点或水中位置。
```

这条命令**只在轮到该生物行动时有效**；不满足时服务端会返回它自己的原因，不会改位置。

---

## `allow_air=true` 的实际边界（不要误解为"获得飞行"）

由 `BattleTeleport` 的落点校验决定：

| 条件 | 要求 |
| --- | --- |
| **谁能用无支撑落点** | **只能是非玩家、未骑乘、且当前移动模式为 `flying` 或 `phasing` 的生物** |
| 玩家 | **永远不行**——玩家即使装了 `allow_air=true` 也必须有支撑 |
| 地面生物 | 不行（移动模式不是 flying/phasing） |
| 骑乘组合 | 不行（`isPassenger()` 为真） |
| 实心方块内 | 仍然拒绝——`phasing` 也检查落点体积 |
| 危险流体（岩浆等） | 仍然拒绝 |
| 越界 / 建筑高度 / 世界边界 | 仍然拒绝 |
| 被参与者占用 | 仍然拒绝 |

**`allow_air` 只放宽"必须有支撑"这一条**，其余校验一律保留。

---

## 验证状态

| 检查 | 状态 |
| --- | --- |
| `python tools/validate_examples.py` | ✅ 通过（见交付说明的真实输出） |
| JSON / mcfunction 与当前解析器一致 | ✅ 静态核对 |
| **在真实存档中加载并观察恶魂闪现** | ⬜ **待验证**——尚未实际加载运行 |
| 恶魂闪现的客户端画面观感 | ⬜ 待客户端实测 |

## 相关文档

- [战斗传送扩展接口](../../docs/TELEPORT_API.md)：`Rules` 完整语义与失败边界
- [物品适配](../../docs/ITEM_ADAPTER_GUIDE.md)：`allow_air` 与旧三参数 `Rules`
- [数据包扩展规范](../../docs/DATAPACK_EXTENSION_SPEC.md)：目录结构与资源 ID 对应
- [数据包覆盖语义](../../docs/DATAPACK_OVERRIDE_SEMANTICS.md)：覆盖、禁用与 `priority`
