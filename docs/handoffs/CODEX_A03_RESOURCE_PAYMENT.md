# A03 第七批：扩展资源支付回调边界

2026-10-11。

## 行为

扩展行动的 trySpend 发生在 runEffect 之前，原效果执行保护未覆盖此阶段。新增独立 payingResource 标记，由 requireTurn 拒绝支付期间重入玩家行动，finally 恢复标记。

资源提供者抛出的运行时异常统一包装成执行故障，避免 IllegalArgumentException / IllegalStateException 被误认为普通预检拒绝。既有请求/扩展施放入口会安全关闭战斗。支付返回后检查战斗、行动者、双方成员身份、存活、维度、预算对象、revision 和待执行运动/射击状态；状态变化时不再扣主要行动或创建后续效果。

正常返回 false 仍遵循资源接口原有约定：资源提供者必须保证没有修改资源，模组拒绝行动且保持战斗继续。支付成功后才扣主要行动；支付抛错或离场时不尝试回滚提供者已经修改的外部资源，主要行动此时尚未扣除。

本批不保证恶意提供者遵守原子支付约定，不自动回滚未知外部状态；纯预检 check/validate 的副作用与其他原版后置回调仍待审查。

## 回归

resourcePaymentRejectsReentryAndStopsAfterFailureOrDeparture 覆盖成功（同时尝试递归施放与结束回合）、返回 false、两种常见异常、支付期间离场五种情况，核对调用次数、效果次数、资源与行动费用、AV、控制锁、授权恢复、保护解除以及失败后重复施放拒绝。

363 项必需 GameTest 全部通过，构建成功。验证日志：build/resource-payment-boundaries.log。协议 22 未变；未修改客户端。

另按用户明确说明，在 WORK_ALLOCATION.md 记录 DeepSeek D01–D11 全部已交付；与复核通过、客户端验收状态分开，D12 仍等待依赖。
