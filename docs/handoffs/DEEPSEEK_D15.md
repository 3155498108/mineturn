# 交接：D15 主动坐骑资格独立回归

日期：2026-10-16 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

对应 [第二批任务 D13–D17](CODEX_TO_DEEPSEEK_D13_D17.md) 的 **D15**，P1。

**状态：新增测试源码完成；编译与运行待你整合验证**（见文末）。
未改任何核心类或已有测试巨类。

---

## 已有覆盖（先查过，据此只补缺口）

现有上马测试**全部使用 `EntityType.HORSE`**：

| 已覆盖 | 位置 |
| --- | --- |
| 上下马授予保持预算与单一行动者 | `ContactGameTests.grantedMountAndDismountKeepBudgetAndSingleActor` |
| 预检要求装备/空间/先撤退 | `ContactGameTests.mountPreflightRequiresEquipmentClearanceAndDisengage` |
| 骑乘事件取消不扣行动 | `ContactGameTests.mountEventCancellationDoesNotConsumeActions` |
| 回调不可重入、离场释放锁 | `ContactGameTests.mountCallbacksCannotReenterAndDepartureReleasesLocks` |
| 水中下马要求水且重检 | `ContactGameTests.aquaticDismountRequiresWaterAndRechecksDrain` |
| 原版回调后重检地面 | `ContactGameTests.dismountRechecksFloorAfterNativeCallback` |
| 骑乘整体传送 | `ContactGameTests.mountedTeleport*` |
| 上下马保持朝向/座位 | `BattleExtensionGameTests.mountedHeadingFollowsRouteAndSeatUpdate` |

**核对方式**：`Select-String 'EntityType\.(HORSE|PIG|STRIDER|CAMEL|DONKEY|MULE|LLAMA)'`
在 `ContactGameTests` 与 `BattleExtensionGameTests` 中**只命中 HORSE**。

### 因此本批新增的缺口（未与既有重复）

| # | 缺口 | 为何是缺口 |
| --- | --- | --- |
| 1 | 马：未驯服 / 无鞍 / **幼体**三种拒绝 | 既有测试只断言"合格马可用"，未断言拒绝路径 |
| 2 | **猪**：鞍 + 胡萝卜钓竿，且**主手与副手**都要能用 | 猪**完全没有覆盖** |
| 3 | **炽足兽**：诡异菌钓竿，且**不能**接受胡萝卜钓竿 | 炽足兽**完全没有覆盖** |
| 4 | **骆驼被明确排除** | `equipped` 对 Camel 直接返回 false，无覆盖 |
| 5 | 多候选时按**距离**择优、与生成顺序无关 | 无覆盖 |
| 6 | **已被占用**的坐骑不被选中 | 无覆盖 |
| 7 | **上马后切换掉控制物品仍保持载体** | 无覆盖（`BattleRiding.vehicle` 的注释声明了该行为） |
| 8 | 拒绝时不消耗行动/移动、不改变原骑乘关系 | 部分覆盖（既有测的是"事件取消"，不是"预检拒绝"） |
| 9 | 锁定在骑乘期间持有、战斗结束后释放 | 无覆盖 |

---

## 新增文件

`src/main/java/com/matuvent/mineturn/battle/MountEligibilityRegressionGameTests.java`（10 个用例）

放在 `battle` 包，以便使用包私有入口（任务允许）。**未新增任何生产 API、未用反射改私有字段。**

| 用例 | 断言要点 |
| --- | --- |
| `horseRequiresTamingSaddleAndAdulthood` | 未驯服/无鞍/幼体分别被拒；合格马可用 |
| `pigRequiresSaddleAndCarrotOnAStick` | 无鞍拒绝；有鞍无钓竿拒绝；**错误钓竿**拒绝；主手可用；**副手也可用**；幼体拒绝 |
| `striderRequiresSaddleAndWarpedFungus` | 胡萝卜钓竿拒绝；诡异菌钓竿可用；幼体拒绝 |
| `camelIsExcludedFromActiveBoarding` | 骆驼即使驯服+装鞍也**不被主动上马选中** |
| `nearestEligibleMountIsChosen` | 两匹合格马（先放远的、后放近的）中选**更近**的那匹 |
| `occupiedMountIsSkipped` | 更近的马被他人骑乘时，选**空闲**的那匹 |
| `steeringItemMattersOnlyForBoarding` | 骑马成功后再切成铁剑，**仍保持载体** |
| `refusedBoardingKeepsResourcesAndExistingMount` | 拒绝时主要/次要行动与移动预算**全部不变**，且未上任何东西 |
| `engagedPlayerCannotBoardWithoutRetreating` | 周身控制生效时拒绝且提示含「撤退」；**未生效时断言合格马可用**（避免依赖实时几何产生假绿） |
| `lockIsHeldWhileRidingAndReleasedAfterwards` | 骑乘期间 `BattleManager.locked` 为真；**战斗关闭后为假** |

### 设计说明

- **`steeringItemMattersOnlyForBoarding`** 直接验证了 `BattleRiding.vehicle` 里那句注释
  `Swapping away from a steering item in battle must not discard the carrier`——
  这正是文档 D13 里写进 `MOUNTED_COMBAT.md` 的行为，现在有测试钉住。
- **`engagedPlayerCannotBoardWithoutRetreating`** 有意写成双分支：
  `engaged` 依赖实时包围盒几何，若环境不满足则断言"合格马仍可用"，
  保证测试在任何情况下都检验同一段代码，而不是变成假绿。
- **静态注册表清理**：`BattleRiding.RIDERS` 是静态 `ConcurrentHashMap`。
  已确认 `BattleSession.close()` 会走 `BattleRiding.release` 与 `forget`，
  因此 `Fixture.close()` 不会把锁泄漏给其它测试。

---

## 真实验证命令与结果

| 命令 | 结果 |
| --- | --- |
| 括号平衡静态核对（去字符串与注释后计数） | ✅ `{`=30 `}`=30；`(`=263 `)`=263 |
| `@GameTest` 用例数 | ✅ 10 个 |
| 换行/编码 | ✅ `CRLF=0`、纯 LF 356 行、无 BOM、无尾随空格 |
| API 签名逐一核对 | ✅ 见下表 |
| `python tools/workspace_context.py --check` | ✅ 通过 |
| **`gradlew compileJava` / `runGameTestServer`** | ⬜ **未运行**——见下 |

### API 签名核对（本批用到的包私有入口）

| 调用 | 签名 |
| --- | --- |
| `BattleMountActions.nearest` | `static Mob nearest(BattleSession, ServerPlayer, double)` |
| `BattleMountActions.cast` | `static void cast(BattleSession, ServerPlayer, String, CombatData.Action)` |
| `BattleMountActions.error` | `static String error(BattleSession, ServerPlayer, CombatData.Action)` |
| `BattleRiding.vehicle` | `static LivingEntity vehicle(LivingEntity)` |
| `BattleManager.locked` | `public static boolean locked(Entity)` |
| `BattleSession.engaged` | `boolean engaged(LivingEntity)` |
| `BattleSession.budget` | `TurnBudget budget;`（包内字段） |
| `TurnBudget.setActions` | `public void setActions(int, int)` |

---

## 未运行 Gradle 的原因

任务说明要求「不与 Codex 同时运行 Gradle 或写同一个测试世界；先交 Python/数据包静态结果及新增测试源码，
由 Codex 统一编译和 runGameTestServer/build」。

**因此本文件仅交付源码，并明确标注：编译与运行结果待整合验证。**
若编译失败，最可能的位置是 `AbstractHorse.equipSaddle(ItemStack, Player)` 的第二个参数
（我用 `null`，与原版及既有测试一致）。

---

## 发现的问题

**无新增缺陷。** 一处**行为观察**供你确认：

`BattleMountActions.equipped` 对 `Camel` 直接返回 `false`，属于**显式排除主动上马**。
我在 D13 的文档里按"明确排除"记录，但**未在你的既有测试中找到对该行为的断言**，
也不确定它是刻意范围限制还是待办。**D15 现在用 `camelIsExcludedFromActiveBoarding` 把当前行为钉住了**：

- 若这是**刻意**的，测试即为有效回归；
- 若将来要**支持**骆驼主动上马，这条测试会失败，正好提醒同步更新文档与 `MOUNTED_COMBAT.md` 的范围。

请确认该断言方向是否正确；若属待实现，我会把 D13 文档里的骆驼从"明确排除"移到「尚未完成」。
