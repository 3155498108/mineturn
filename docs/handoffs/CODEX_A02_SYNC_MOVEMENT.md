# Codex A02 第三批：同步移动执行

2026-10-08。本批只修改 BattleSession 中移动执行相关代码、ContactGameTests 及对应文档，不修改 DeepSeek 的演出/D 系列文件。

- 从 tickMotion 提取 clearMovementSample，动画与同步提交共用；不复制两份几何校验。
- commitMovement 逐样本重检，累计实际下落，现场结算落地；不再走完全路径后回放落地点。
- 接触回调后检查存活、成员与会话状态，阻止失效执行器继续推进。
- 水体变化测试同时跑动画/同步入口；新增同步提交遇到新墙的回归。

验证日志：build/synchronous-route-boundaries.log。完成情况以实际 GameTest 汇总为准。未进行提交或推送，请避免批量暂存其他工作。

最终验证：319 项必需 GameTest 全部通过，构建成功。
