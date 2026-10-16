# 交接：D13 近期骑乘/飞行传送文档与客户端验收补齐

日期：2026-10-16 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

对应 [第二批任务 D13–D17](CODEX_TO_DEEPSEEK_D13_D17.md) 的 **D13**，P1。

**状态：完成**（仅文档，无生产代码改动）。

---

## 已做

### 1. `docs/MOUNTED_COMBAT.md`（主要修正）

原文标题为「骑乘战斗：单人陆地载体」，并写着「本组没有新增冲锋、跳马或下马行动」、
「主动下马/重新上马的战斗行动……留待后续」——**与当前实现不符**。

按 `BattleMountActions`、`BattleRiding`、`BattleManager.hasMountAction` 和实际授予文件重写：

| 补充内容 | 依据 |
| --- | --- |
| 标题改为「单人受控坐骑」 | 已支持水中上下马与整体传送 |
| **区分"入战已有坐骑"与"战斗中主动上马"** | 两者的资格判定时机不同 |
| 主动上下马是**条件授予**而非物品动作 | `grants/mount.json`、`grants/dismount.json` |
| 成本 `main 0 / bonus 1`、**可配置** | `actions/mount.json` 的 `parameters.action_cost` |
| **不补充移动距离** | `cast` 中 `budget.limitMovement(...)` |
| 周身约束（需先撤退） | `error` 首条判定 |
| 安全下马：曼哈顿 ≤ 2、纵向 −1..1、扫掠与视线 | `landing` / `safeLanding` |
| 落点回调后**重检** | `cast` 中的二次 `safeLanding` |
| 猪＝胡萝卜钓竿、炽足兽＝诡异菌钓竿、**骆驼明确排除** | `equipped` |
| **上马后切换掉控制物品仍保持载体** | `BattleRiding.vehicle` 回退查 `RIDERS` |
| 取消事件不消耗行动、回调期间归属变化即中止 | `cast` 的 try/catch 与归属校验 |
| 修订记录（保留 2026-10-04 的历史条目） | 文档约定：不静默重写历史结果 |

### 2. `docs/CLIENT_ACCEPTANCE.md`（骑乘组 G7–G23）

原 G 组只有 6 项基础移动/伤害。按 D13 要求补 17 项**可执行手测**，全部初始为 `未测`：

- **按钮出现/消失**：G7 合格坐骑出现「上马」、G8 不合格不出现、G9 上下马互斥
- **控制物品**：G10 猪（胡萝卜钓竿 ✓ / 诡异菌钓竿 ✗）、G11 炽足兽（相反）
- **成本与额度**：G12 上马消耗次要行动且**不刷新移动距离**
- **切换物品**：G13 上马后换剑仍保持骑乘
- **陆地/水中下马**：G14、G15
- **无可用落点**：G16 拒绝且**不消耗行动**
- **周身控制**：G17 拒绝并提示先撤退
- **镜头**：G18 上下马瞬间不穿墙、不抽搐
- **骑乘结算**：G19 尖牙、G20 吼叫只对骑手结算一次
- **队列**：G21 坐骑不作为独立行动者、G22 原版转换前后队列显示
- **退出**：G23 恢复原版控制、无残留锁

### 3. `docs/ITEM_ADAPTER_GUIDE.md`（补 `allow_air` 指引）

原指南只在执行器表里提了 `ender_pearl / chorus_fruit`，**未提通用传送的 `allow_air`**。
新增小节说明 `Rules(range, requireSight, allowWater, allowAir)` 的四个参数与默认值，
并明确 `allow_air=true` 的**实际边界**：

- 只有**已注册 `flying`/`phasing` 且未骑乘的非玩家生物**可用无支撑落点
- **不给玩家或地面生物飞行能力**
- 即使 `phasing` 也不能落进实心方块
- 旧三参数构造器**保留**，等价 `allowAir=false`

---

## 未做 / 未改

| 项 | 说明 |
| --- | --- |
| `docs/TELEPORT_API.md` | **核对后未发现需修正的错误**，故未改动。`allow_air` 边界在 §46 已写明，三参数 `Rules` 与 `allowAir=false` 在 §39 已写明 |
| 任何生产代码 | D13 限定专题文档 |
| 客户端实测结果 | 全部 G 组新增项保持 `未测`，**未虚构任何通过** |
| 历史验证结果 | `COMBAT_FEEDBACK_2026_10_04` 等历史条目原样保留 |

---

## 已有覆盖证据（避免重复劳动）

写文档时核对了现有测试，D15 应据此只补缺口：

| 已覆盖 | 位置 |
| --- | --- |
| 上下马授予保持预算与单一行动者 | `ContactGameTests.grantedMountAndDismountKeepBudgetAndSingleActor` |
| 预检要求装备/空间/先撤退 | `ContactGameTests.mountPreflightRequiresEquipmentClearanceAndDisengage` |
| 骑乘事件取消不扣行动 | `ContactGameTests.mountEventCancellationDoesNotConsumeActions` |
| 回调不可重入、离场释放锁 | `ContactGameTests.mountCallbacksCannotReenterAndDepartureReleasesLocks` |
| 水中下马要求水且重检 | `ContactGameTests.aquaticDismountRequiresWaterAndRechecksDrain` |
| 原版回调后重检地面 | `ContactGameTests.dismountRechecksFloorAfterNativeCallback` |
| 骑乘整体传送与落点重检 | `ContactGameTests.mountedTeleportChecksWholeBodyAndRevalidatesLanding` |
| 骑乘伤害只结算骑手一次 | `ContactGameTests.fangsHitMountedRiderOnceThroughCarrierBody` |

---

## 真实验证命令与结果

均在本工作区实跑：

| 命令 | 结果 |
| --- | --- |
| `python tools/validate_examples.py` | ✅ `OK: all example checks passed` |
| `python tools/workspace_context.py --check` | ✅ 退出码 **0** |
| `git diff --check`（原样） | ✅ 通过 |
| 三个改动文件的换行与编码 | ✅ 全部 `i/lf w/lf`、无 BOM、无尾随空格 |

**未运行 Gradle / runGameTestServer**：D13 仅文档，按任务说明「仅文档无需启动服务器」，
且当前不宜与 Codex 并发运行 Gradle。

---

## 发现的问题

**无新增缺陷。** 核对过程中确认了几处与旧文档不符的实现，均已在文档中更正，不是代码缺陷：

1. 旧文档称"没有主动下马行动"——实际已实现且有测试。
2. 旧文档称"只有陆地"——实际水中下马已支持（`aquaticDismountRequiresWaterAndRechecksDrain`）。
3. 旧标题"单人陆地载体"未反映整体传送。

**一处值得你确认**：`BattleMountActions.equipped` 对 `Camel` 直接返回 `false`（主动上马排除骆驼），
但骆驼本身支持骑乘。这是刻意的范围限制还是待办？我在文档里按"明确排除"记录，
若属待实现，建议在 `MOUNTED_COMBAT.md` 的「尚未完成」中另列一行。
