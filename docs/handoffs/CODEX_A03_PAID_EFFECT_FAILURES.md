# Codex A03 第二批：区分预检拒绝与已付费效果异常

2026-10-11。

## 修复

BattleManager.request 将 IllegalArgumentException / IllegalStateException 视为可向玩家解释的操作拒绝；但原版物品回调也可能在已经支付行动、进入 runEffect 后抛出相同异常。此前这种部分执行失败可能只显示错误而继续战斗。

runEffect 现在将执行回调中的这两类异常包装为带原始 cause 的 RuntimeException，交给既有安全关闭流程。普通执行前校验仍沿用原有拒绝逻辑，不关闭战斗。activeEffect 仍在 finally 恢复，执行授权 ThreadLocal 的既有 finally 也会清理。动作演出移到成功返回后发送，不在失败的 finally 中播放。

不自动返还已扣行动，也不尝试撤销第三方已经产生的生命/库存/世界副作用。物品是否已消耗以原版 finishUsingItem 实际执行到哪一步为准；不能承诺异常事务整体原子回滚。

## 验证

真实牛奶移除效果回调分别抛出 IllegalArgumentException 和 IllegalStateException，同时改变选中槽位。通过请求入口核对：

- 无效槽位预检不会扣费、消费或关闭战斗。
- 正确槽位进入付费执行后，回调异常关闭战斗并释放双方控制锁。
- 选中槽位恢复；本用例在原版消费前抛错，牛奶保留且不凭空产生空桶，但主要行动不退回。
- AV 不推进，执行授权不泄漏；重复原请求不再调用回调或消费物品。

日志 `build/paid-effect-failures.log` 包含故意注入的异常日志，是否通过以 GameTest 汇总为准。协议 22 不变，未提交/推送。

后续 A03 仍有直接改库存、重入动作、中途主动离场和其他付费步骤事件边界；本批只处理 runEffect 中执行回调的异常分类。

最终验证：358 项必需 GameTest 全部通过，构建成功。
