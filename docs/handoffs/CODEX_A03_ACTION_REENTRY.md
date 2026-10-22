# Codex A03 第三批：效果回调重入玩家行动

2026-10-11。

## 修复

拥有额外主要行动次数时，原版物品事件或扩展效果回调可能在外层行动尚未结束前再次请求行动。BattleSession.requireTurn 现在先检查 activeEffect，在 runEffect 结算期间拒绝玩家的新行动，返回“当前行动正在结算，请等待完成。”。

保护沿用玩家行动的公共校验入口，不修改内部 runEffect 上下文栈，也不禁止合法的效果组合及 BattleAccess 效果操作。外层效果完成后恢复上下文，玩家仍可使用剩余行动。

## 验证

新增 itemCallbackCannotReenterPlayerActionsWithExtraActions，给予玩家三次主要行动和一次次要行动，在牛奶移除药水效果的原版事件回调中：

- 通过 BattleManager.request 尝试 use、sprint、end、move，并通过 castGranted 尝试扩展行动。
- 核对回调只执行一次，外层饮用正常返还空桶，主要行动仅减少一次，次要行动和移动预算不变。
- 核对当前行动者、AV 不变，revision 仅增加一次，没有产生运动或射击状态。
- 回调结束后再次饮用可以正常扣费，执行授权不泄漏。

359 项必需 GameTest 全部通过，构建成功；日志 build/paid-action-reentry.log。协议 22 不变，未进行客户端实测。

## 后续边界

本批覆盖 runEffect 内通过玩家行动入口的重入。预检回调、其他付费步骤中的事件、直接库存修改和中途主动离场仍需后续审查，不宣称任意第三方回调具有事务原子性。
