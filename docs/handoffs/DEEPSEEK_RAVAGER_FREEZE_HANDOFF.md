# 交接：劫掠兽被永久冻结（`MINETURN_STUN >= 0` 判定错误）

更新：2026-10-11 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

**来源**：用户真实客户端实测。**已定位到根因，未修复**——改动落在你的范围（`mixin`）。

## 用户现象

| 观察 | 说明 |
| --- | --- |
| **动作频繁抽搐** | 动画在跑但反复重置 |
| **不移动** | 位置不变 |
| **仍能正常攻击** | 攻击结算正常，**只有显示/位移冻结** |
| 用户原话 | 「很像**动作被冻结**了，而他还能正常攻击我，只是显示上不移动」 |

## 根因（完整链路，逐行可核）

### 1. 冻结判定用了 `>= 0`

`mixin/RavagerBattleMixin.java:34-42`：

```java
@Inject(method="aiStep",at=@At("HEAD"),cancellable=true)
private void mineturn$freeze(CallbackInfo ci){
    var self=(Ravager)(Object)this;
    if(self.getEntityData().get(MINETURN_STUN) >= 0){   // ← 0 也命中
        if(self.level().isClientSide && attackTick>0)attackTick--;
        ci.cancel();                                     // ← 冻结整个 aiStep
    }
}
```

### 2. 眩晕结束时写入的正是 `0`

`battle/BattleSpecies.java`：

```java
static void tick(BattleSession battle,BattleSession.Member member){
    if(member.entity instanceof Ravager r){
        int stun=r.getStunnedTick(),roar=r.getRoarTick();
        if(stun>0){
            stun--;
            if(stun==0){roar=20;r.playSound(SoundEvents.RAVAGER_ROAR,1,1);}
        }else if(roar>0){
            roar--;
            if(roar==10)roar(battle,r);
        }
        ((RavagerBattleAccess)r).mineturn$timers(stun,roar);   // ← stun 此时为 0
    }
    ...
}
```

### 3. `mineturn$timers` 会把这个 `0` 同步到实体数据

`mixin/RavagerBattleMixin.java:21-24`：

```java
@Override public void mineturn$timers(int stun,int roar){
    var self=(Ravager)(Object)this;stunnedTick=Math.max(0,stun);roarTick=Math.max(0,roar);
    self.getEntityData().set(MINETURN_STUN,stun);   // ← 写入 0（不是 -1）
    self.getEntityData().set(MINETURN_ROAR,roar);
}
```

### 4. 只有战斗结束才恢复 `-1`

`BattleSpecies.clear`：

```java
if(entity instanceof Ravager r)((RavagerBattleAccess)r).mineturn$timers(-1,-1);
```

**只在 `clear()`（离场/重载/战斗结束）时置 -1。**

### 结论

**劫掠兽一旦被眩晕一次，`MINETURN_STUN` 就停在 `0`，
`0 >= 0` 恒真 → `aiStep` 被永久取消 → 原版 AI、朝向与动画冻结，直到本场战斗结束。**

- **攻击仍正常**：伤害走战斗结算路径，与 `aiStep` 无关
- **显示抽搐**：战斗系统的移动在推进，但原版实体更新被取消，两者不同步
- **不移动**：`aiStep` 被取消

### 同一处还有第二处相同的 off-by-one（第 31 行）

```java
@Inject(method="getStunnedTick",at=@At("HEAD"),cancellable=true)
private void mineturn$stun(CallbackInfoReturnable<Integer> cir){
    int n=((Ravager)(Object)this).getEntityData().get(MINETURN_STUN);
    if(n>=0)cir.setReturnValue(n);      // ← 同样把 0 当成有效值
}
```

对比 `BattleSpecies.busy` 用的是**严格大于零**：

```java
static boolean busy(LivingEntity entity){
    return entity instanceof Ravager r && (r.getStunnedTick()>0 || r.getRoarTick()>0) || ...
}
```

**同一个概念（"是否处于眩晕/吼叫中"）在两处用了不同的判定**：
`busy()` 是 `> 0`，`mineturn$freeze`/`mineturn$stun` 是 `>= 0`。这就是分叉的根源。

## 触发条件（很容易撞上）

`battle/BattleManager.java:105`：

```java
if(battle!=null && ravager.getRandom().nextDouble()<0.5)BattleSpecies.stun(battle,ravager);
```

**用盾牌格挡劫掠兽有 50% 概率触发眩晕**（这也是玩家最自然的应对方式）。

## 建议修法（三选一，请你定）

| 方案 | 改动 | 说明 |
| --- | --- | --- |
| **A（最小）** | 判定改为 `> 0`，并在 `BattleSpecies.tick` 里让 `roar>0` 时**不要**把 stun 写 0 | 需同时确认眩晕期间仍要冻结 |
| **B（对齐语义）** | 明确 `-1 = 未激活`、`>=0 = 激活`，则 `mineturn$timers` 在 `stun==0 && roar==0` 时应写 `-1` | 语义最清晰 |
| **C** | `mineturn$freeze` 改为检查 `stun>0 || roar>0` | 与 `BattleSpecies.busy` 的语义一致 |

**推荐 C**：它与 `BattleSpecies.busy(entity)` 已有的语义（`getStunnedTick()>0 || getRoarTick()>0`）完全一致，
让"冻结"和"跳过回合"用同一个条件，避免两处再次分叉。

## 我无法自行验证的原因

我写过探针想观察真实战斗里的劫掠兽回合，结果**劫掠兽和铁傀儡都不动**——说明探针没能驱动 AI
（`BattleSession` 包私有，`turnTicks`/`budget`/`actor` 在 `test` 包设不了）。**该探针已删除**，没留伪造结论。

**若要回归测试**，建议放在 `battle` 包内，仿 `BattleExtensionGameTests:1538` 的
`ravagerStunRoarAvAndCleanup`，追加一条断言：

```java
// 眩晕+吼叫走完之后必须解冻
for(int i=0;i<40+20;i++)BattleSpecies.tick(battle,battle.member(ravager));
h.assertTrue(ravager.getEntityData().get(MINETURN_STUN) < 0 或 == -1, "Ravager stayed frozen after roar");
```

需要在 mixin 里加一个 `@Invoker` 或从 `RavagerBattleAccess` 暴露读取方法。

## 我这边同期的改动（与本问题无关）

技能镜头（`client/` 下）：机位不再随体型缩放、固定瞄准点距离、释放镜头做遮挡夹取。
**不涉及** `mixin` 或移动路径，可独立审阅。

## 相关文件

| 文件 | 行 | 关系 |
| --- | --- | --- |
| `mixin/RavagerBattleMixin.java` | **34-42** | **冻结判定（根因）** |
| `mixin/RavagerBattleMixin.java` | 21-24 | 写同步数据 |
| `battle/BattleSpecies.java` | `tick` | 眩晕归零写 0 |
| `battle/BattleSpecies.java` | `clear` | 唯一恢复 -1 处 |
| `battle/BattleManager.java` | 105 | 50% 格挡触发眩晕 |
| `battle/BattleSession.java` | 1467 | `busy()` 跳过整个回合 |
