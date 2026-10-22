# Codex A10 第七批：多玩家预览请求回归

2026-10-11 收尾；测试于 2026-10-10 执行。本批新增测试，不修改生产行为或协议。

## 用例

`PreviewLoadGameTests.mixedPreviewBurstHasBoundedIndependentRepliesAndFreshTerrain` 创建同场四名模拟 ServerPlayer。测试监听器模拟已协商 Preview 通道，捕获真实请求处理器发送的 Preview 数据包；没有跳过 BattleManager.request 的权限、版本或限流校验。

手动推进 40 个战斗 tick，每个 tick 轮换当前行动者。每名成员发送 12 组“过期物品预览 + 当前请求”，当前第一包为移动预览、重复包为物品预览。共 3840 次请求，应只得到 320 次回包。用例核对：

- 每个成员每 tick 当前/过期各一次回包，互不抢额度；跨预览类型的重复请求也受限。
- 回包保留战斗、revision、请求 ID、落点；非当前行动者收到拒绝。
- 第 20 个模拟 tick 在第一名玩家目标处放置两格高障碍，第 30 个移除；revision 不变也必须重新读取地形，随后恢复可达。
- 预览不改变玩家位置、移动/主要行动预算、AV、revision，不启动运动或射击。
- 所有假玩家、会话和 EmbeddedChannel 在 finally 中清理。

这些检查只覆盖同步服务器入口。tick 和行动者由测试直接安排，不代表 40 个真实时间 tick 内的吞吐，不测真实网络排队、丢包、客户端展示、TPS 或 FPS，也不宣称实现多人并行回合。

## 验证与后续

`build/multiplayer-preview-workload.log`：**All 354 required tests passed**，BUILD SUCCESSFUL。日志确认 3840 requests / 320 replies / 4 members / 40 simulated combat ticks。

协议 22 不变；未提交/推送。客户端仍需真实联机测试：多玩家同时点选时只有当前行动者得到可达路线；延迟条件下更换落点不应展示旧回包；场外放置/移除障碍后下次预览应更新。现有客户端每 4 tick 最多发一次、静止落点约 20 tick 重查，不要求障碍变化立即推送。
