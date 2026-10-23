# 交接：D14 可安装的飞行传送数据包示例

日期：2026-10-16 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

对应 第二批任务 D13–D17（原任务文件未随上游仓库提供） 的 **D14**，P1。

**状态：完成**（仅新增 `examples/air-teleport/`，未改基础数据包与 `build.gradle`）。

---

## 已做

新增目录 `examples/air-teleport/`，可独立安装：

```
examples/air-teleport/
├─ pack.mcmeta
├─ README.md
└─ data/
   ├─ air_teleport_example/
   │  ├─ function/
   │  │  ├─ load.mcfunction                 # 声明计分板 mineturn.ai
   │  │  └─ ghast/{init,turn,cleanup}.mcfunction
   │  └─ mineturn/
   │     ├─ actions/blink_up.json           # allow_air=true  ← 主演示
   │     ├─ actions/blink_ground_only.json  # allow_air=false ← 对照
   │     └─ mobs/ghast.json                 # 覆盖恶魂，绑定函数 AI
   └─ minecraft/tags/function/load.json
```

### 关键设计

| 项 | 选择 | 理由 |
| --- | --- | --- |
| 命名空间 | `air_teleport_example` | 独立，不与 `teleport_example` / `mineturn_example` 冲突 |
| 演示生物 | **`minecraft:ghast`** | 模组注册为 `movement_mode: flying`，满足 `allow_air` 的 `airborne` 条件；体积大、在空中、容易观察 |
| 覆盖方式 | `priority: -100` + `entity` 精确匹配 | 与内置恶魂同优先级，按数据包优先级整文件覆盖 |
| 调用入口 | `on_turn` → `air_teleport_example:ghast/turn` | **真正调用了动作**，不是只写一个没有入口的动作定义 |
| 位移 | `dx:2, dy:2, dz:0` | 偏移是固定值、每回合重复；位移太大会很快撞到建筑高度上限 |
| 对照动作 | `blink_ground_only`（`allow_air=false`） | 证明同一空中的恶魂会被拒绝 |

### `turn.mcfunction` 的决策顺序

```
1. 火球就绪 → 打火球（沿用内置 mineturn:ghast_fireball）
2. 否则闪现就绪 → mineturn ai use air_teleport_example:blink_up
3. 都在冷却 → mineturn ai wait
```

**`mineturn ai use <action>` 对 `self=true` 的动作自动以自身为目标**（见 `FunctionAi.use`
与 `validateUse` 中的 `action.self() ? frame.self() : frame.target()`），
所以闪现**不需要先选中敌人**，恶魂没有目标时也能执行。

### 覆盖与卸载（README 已写明）

- **只覆盖 `minecraft:ghast`**；不碰玩家物品、其它生物或方块。
- 与 `teleport-action` **不冲突**（那个映射木棍物品，本示例只改恶魂 AI）。
- 与 `function-ai` / `function-ai-override` 不重叠（那两个覆盖僵尸）。
- **卸载**：删除 `<world>/datapacks/` 下的本目录或 zip 后 `/reload`，内置恶魂 AI 立即恢复。

---

## 演示的边界（README 中成表）

由 `BattleTeleport` 的落点校验决定，`allow_air=true` **只放宽"必须有支撑"这一条**：

| 条件 | 行为 |
| --- | --- |
| **谁能用无支撑落点** | 仅**非玩家、未骑乘、移动模式为 `flying` 或 `phasing`** 的生物 |
| 玩家 | **永远不行**，即使写成 `allow_air=true` |
| 地面生物 | 不行（移动模式不匹配） |
| 骑乘组合 | 不行（`isPassenger()` 为真） |
| 实心方块内 | 仍然拒绝（`phasing` 也检查体积） |
| 岩浆等危险流体 | 仍然拒绝 |
| 越界 / 建筑高度 / 世界边界 / 被占用 | 仍然拒绝 |

`allow_air=false` 时空中落点的失败文本已从源码核实：

```
请选择有支撑的落点或水中位置。
```

---

## 未做

| 项 | 说明 |
| --- | --- |
| 修改基础数据包 | 未改 `src/main/resources` 下任何文件 |
| 修改 `build.gradle` | 未加打包任务；示例可直接手动压 zip（任务说明允许） |
| 新增物品 | 未映射任何玩家物品 |
| 外部模组依赖 | 无 |
| **真实存档加载运行** | **未执行**——见下 |

---

## 真实验证命令与结果

| 命令 | 结果 |
| --- | --- |
| `python tools/validate_examples.py` | ✅ `OK: all example checks passed`，**示例包数 12 → 13** |
| 全部 JSON 用标准库解析 | ✅ 4 个 JSON 全部可解析 |
| 4 个 `.mcfunction` 换行 | ✅ **CR 字节 = 0**（全 LF，满足 `.gitattributes` 与数据包要求） |
| `python tools/workspace_context.py --check` | ✅ 通过（见整体交付说明） |

**未运行 Gradle / GameTest**：D14 为新增数据包示例，按任务说明「未实际加载运行要写待验证」，
且当前不宜与 Codex 并发运行 Gradle。

**待整合验证**（请在你的整合批次中确认）：

1. `mineturn:teleport_offset` 在 `allow_air=true` 下对恶魂**实际成功位移**；
2. `blink_ground_only` 对空中的恶魂**实际返回**「请选择有支撑的落点或水中位置。」；
3. 恶魂被本示例覆盖后仍能正常打火球（即覆盖没有破坏内置行为）。

---

## 发现的问题

**无新增缺陷。** 一处值得你确认的设计点：

`BattleTeleport` 的 `airborne` 判定要求 `battle.movementMode(player)` **恰好等于** `"flying"` 或 `"phasing"`。
本示例的恶魂通过 `movement_mode: "flying"` 满足。

但**骑乘飞行的生物**（例如骑乘以飞行模式注册的坐骑）会因为 `isPassenger()` 被排除——
如果将来要支持"飞行坐骑整体传送"，这里需要另外的规则。目前按"不支持"记录，
未在示例中演示。
