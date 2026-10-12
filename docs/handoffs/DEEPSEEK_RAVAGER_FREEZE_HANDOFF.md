# 交接：劫掠兽被永久冻结（`MINETURN_STUN` 判定）

更新：2026-10-11 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

> ## ✅ 已修复（2026-10-12，Codex）
>
> 修复记录见 [劫掠兽客户端冻结修复](CODEX_RAVAGER_FREEZE_FIX.md)。**用户已确认有效。**
>
> **本文的修法建议（把 `>= 0` 改为 `> 0`）是错的，请勿采纳。** Codex 的纠正理由：
>
> `-1` / `0` / 正数**都有语义**，不是可以合并的同一状态：
>
> | 值 | 含义 |
> | --- | --- |
> | `-1` | **原版接管**（战斗未接管该计时） |
> | `0` | 战斗接管，但**当前计时已结束** |
> | 正数 | 还有剩余计时 |
>
> getter 返回 `0` 是**必要行为**——否则会读到客户端实体事件留下的原版旧计时。
>
> **我定位对的部分**：`aiStep` 入口被取消导致显示停住，这个机制方向是对的。
> **我判断错的部分**：真正的问题是**取消整个 `aiStep` 使父类的插值无法推进**
> （父类 `LivingEntity.aiStep` 负责位置/身体旋转/头部旋转插值），
> **而不是**比较运算符本身写错。
>
> 实际修复：客户端在战斗接管期间先同步原版眩晕/吼叫字段，再执行父类 `aiStep`，
> 保留攻击动画实时时钟；继续跳过劫掠兽自身的原版计时、吼叫伤害与破坏树叶。
>
> **教训**：`-1` / `0` / 正数这种哨兵值设计，不能在只看到一处比较时就断定是笔误。
> 我应该在建议里先确认哨兵值语义，而不是直接给出「改成 `> 0`」。

**来源**：用户真实客户端实测。**本文为历史定位记录**，结论以修复记录为准。

## 用户现象

| 观察 | 说明 |
| --- | --- |
| **动作频繁抽搐** | 动画在跑但反复重置 |
| **不移动** | 位置不变 |
| **仍能正常攻击** | 攻击结算正常，**只有显示/位移冻结** |
| 用户原话 | 「很像**动作被冻结**了，而他还能正常攻击我，只是显示上不移动」 |

## 我当时定位到的链路（机制正确，结论待修正）

### 1. 冻结判定

`mixin/RavagerBattleMixin.java`：

```java
@Inject(method="aiStep",at=@At("HEAD"),cancellable=true)
private void mineturn$freeze(CallbackInfo ci){
    var self=(Ravager)(Object)this;
    if(self.getEntityData().get(MINETURN_STUN) >= 0){
        if(self.level().isClientSide && attackTick>0)attackTick--;
        ci.cancel();
    }
}
```

### 2. 眩晕结束时写入 `0`

`battle/BattleSpecies.java` 的 `tick`：`stun` 归零时调 `mineturn$timers(stun=0, roar=20)`。

### 3. `mineturn$timers` 原样写入同步数据

```java
@Override public void mineturn$timers(int stun,int roar){
    var self=(Ravager)(Object)this;stunnedTick=Math.max(0,stun);roarTick=Math.max(0,roar);
    self.getEntityData().set(MINETURN_STUN,stun);   // 写入 0
    self.getEntityData().set(MINETURN_ROAR,roar);
}
```

### 4. 只有战斗结束才恢复 `-1`

`BattleSpecies.clear`：`((RavagerBattleAccess)r).mineturn$timers(-1,-1);`

**当时的推断**：`0 >= 0` 恒真 → 永久冻结。
**实际**：`0` 是"战斗接管但计时结束"的合法状态，**取消 `aiStep` 才是问题**（阻断父类插值）。

## 触发条件（仍然成立）

`battle/BattleManager.java`：

```java
if(battle!=null && ravager.getRandom().nextDouble()<0.5)BattleSpecies.stun(battle,ravager);
```

**用盾牌格挡劫掠兽有 50% 概率触发眩晕**（玩家最自然的应对方式）。

## 我无法自行验证的原因

我写过探针想观察真实战斗里的劫掠兽回合，结果**劫掠兽和铁傀儡都不动**——说明探针没能驱动 AI
（`BattleSession` 包私有，`turnTicks`/`budget`/`actor` 在 `test` 包设不了）。**该探针已删除**。

## 我这边同期的改动（与本问题无关）

技能镜头（`client/` 下）：机位不再随体型缩放、固定瞄准点距离、释放镜头做遮挡夹取。
**不涉及** `mixin` 或移动路径，可独立审阅。

## 相关文件

| 文件 | 关系 |
| --- | --- |
| [CODEX_RAVAGER_FREEZE_FIX.md](CODEX_RAVAGER_FREEZE_FIX.md) | **实际修复与正确语义** |
| `mixin/RavagerBattleMixin.java` | 冻结判定与同步字段 |
| `battle/BattleSpecies.java` | 眩晕/吼叫 AV 推进，以及唯一的 `-1` 恢复处 |
| `battle/BattleManager.java` | 盾挡 50% 触发眩晕 |
| `battle/BattleSession.java` | `busy()` 跳过整个回合 |

