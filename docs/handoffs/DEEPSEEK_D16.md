# 交接：D16 原版转换资格独立回归

日期：2026-10-16 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

对应 [第二批任务 D13–D17](CODEX_TO_DEEPSEEK_D13_D17.md) 的 **D16**，P1。

**状态：新增测试源码完成；编译与运行待你整合验证。**
未改 `BattleConversions`、混入、访问器或全局数据包状态。

---

## 已有覆盖（先查过，据此只补缺口）

`ConversionGameTests`（14 个用例）**已经覆盖** D16 点名的大部分场景：

| D16 点名的场景 | 已有覆盖 |
| --- | --- |
| **转换途中切换 NoAI 后暂停与恢复** | ✅ `conversionWaitFreezesAndExitResumesNativeClock`（含 `setNoAi(true)` 冻结 + 恢复推进 + 离战恢复原版计时） |
| **非空猪灵私有背包保持原实体和原物品** | ✅ `piglinPrivateInventoryDelaysConversionWithoutLoss` |
| **合法清空背包后继续转换** | ✅ 同上（`removeItemNoUpdate(0)` 后重试成功） |
| **临时召唤物排除** | ✅ `conversionEntryKeepsNativeCountdownAndExcludesSummons` |
| 免疫条件切换与取消事件计时语义 | ✅ `netherConversionImmunityResetsAndCancellationUpdatesTimer`、`conversionCancellationKeepsOriginalEquipmentAndTimer` |

**核对方式**：逐个读 `ConversionGameTests` 的用例体，而非只看用例名。

### 因此本批只补真正的缺口

`BattleConversions.tick` 的第一行守卫是：

```java
if(!(member.entity instanceof Mob mob) || mob.isNoAi() || mob.isPassenger() || mob.isVehicle()
        || BattleSummons.temporary(mob) || !present(b,member))return;
```

其中 `isPassenger()`（自身是乘客）与 `isVehicle()`（载着别人）**没有任何测试覆盖**，
而它们正是"骑乘或临时召唤物排除"里**未被验证的那一半**。

| # | 新增缺口 | 为何是缺口 |
| --- | --- | --- |
| 1 | **载着乘客**的转换生物不被转换 | `isVehicle()` 守卫无覆盖 |
| 2 | **自身是乘客**（坐在载具上）的转换生物不被转换 | `isPassenger()` 守卫无覆盖 |
| 3 | 解除持有后**同一实体**转换，且计时**未被重置** | 既有测试证明"暂停"，未证明"暂停期间累积的计时存活" |
| 4 | 临时召唤窗口结束后排除**确实解除** | 防止守卫永不失效（排除写死） |

---

## 新增文件

`src/main/java/com/matuvent/mineturn/battle/ConversionEligibilityRegressionGameTests.java`（4 个用例）

| 用例 | 断言要点 |
| --- | --- |
| `mobCarryingAPassengerDoesNotConvert` | 计时已到期（`< 0`）但载着乘客 → **原实体存活、类型不变、仍在战斗中、乘客仍挂在原载体上** |
| `mobRidingAVehicleDoesNotConvert` | 计时已到期但自身是乘客 → 原实体存活、类型不变 |
| `timerSurvivesThePassengerHold` | 载客时跳过的 tick **不推进也不重置**计时（保持 300）；解除后**下一 tick 立即转换**为僵尸猪灵 |
| `exclusionLiftsOnceSummonWindowEnds` | 打上 `BattleSummons.TAG` 时不转换；**移除标签后**下一 tick 正常转换为溺尸 |

### 设计说明

- **不重复 `piglinPrivateInventory` 的用例**：既有测试已覆盖"非空背包 → 保持原实体与原物品 →
  清空后继续"。本批**没有**再写一遍，也没有实现掉包事务。
- **`mobCarryingAPassengerDoesNotConvert` 的额外价值**：它同时验证了"乘客没有被留在被移除的载体上"。
  这正是守卫存在的理由——若守卫失效，乘客会挂到一个已移除实体上。
- **计时语义**：`timerSurvivesThePassengerHold` 特意把计时设为 300（差一 tick 触发），
  载客 tick 后仍为 300，解除后一 tick 即转换。这能同时抓住"跳过时误推进"和"跳过时误重置"两种错误。
- **只用已有接口**：`ZombieConversionAccess` / `PiglinConversionAccess` 的 `mineturn$` 方法、
  `BattleSummons.TAG`、`BattleStatus.tick`，均为包内可访问。**未用反射改私有字段。**

---

## 真实验证命令与结果

| 命令 | 结果 |
| --- | --- |
| 括号平衡静态核对 | ✅ `{`=23 `}`=23；`(`=159 `)`=159 |
| `@GameTest` 用例数 | ✅ 4 个 |
| 换行/编码 | ✅ `CRLF=0`、纯 LF 222 行、无 BOM、无尾随空格 |
| `BattleSummons.TAG` 名称核对 | ✅ **我最初写的 `TEMPORARY_TAG` 不存在**，已按源码改为 `TAG` |
| `python tools/workspace_context.py --check` | ✅ 通过 |
| **`gradlew compileJava` / `runGameTestServer`** | ⬜ **未运行**——按任务要求交你统一编译 |

---

## 发现的问题

**无新增缺陷。** 两点**行为说明**，供你在文档层面确认：

1. **`isVehicle()` 排除的后果**：一个载着乘客的僵尸即使水浸计时到期也不会转换。
   这是合理的（避免乘客挂在被移除的实体上），但**没有任何文档写出该行为**。
   `docs/` 里我未找到转换专题；若你认为需要，建议在 `AV_STATUSES.md` 或新专题里补一句。

2. **`present(b, member)` 守卫**：本批未覆盖。若它包含"必须已加载/在战场内"语义，
   那是另一个可测边界，但 D16 未点名，我**没有扩写**。

**一处命名提醒**：`BattleSummons.TAG` 的值为 `"mineturn:temporary_summon"`，
但它是 `static final String` 而非 `TagKey`。数据包无法用实体标签表达"临时召唤物"——
只能由 `BattleSummons` 在召唤时 `addTag`。若将来要让数据包声明，需要另一套机制。
