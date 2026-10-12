# Codex A02 第二批：动画下落水体重检

日期：2026-10-08。

修改 BattleSession 的 Movement.falling、tickMotion 和 finishMotion：下落距离使用 double 累加实际下降段，入水清零，落地时转为 float 交原版 fall。没有修改动画协议、演出机位或 DeepSeek 的 D 系列文件。

ContactGameTests 新增 changedWaterRecomputesAnimatedFallDistance，使用真实 TerrainPath 规划及 tickMotion 执行，分别检查干燥、规划后放水、规划后撤水的七格下落；同时确认动画不额外改变 AV/移动预算。

验证日志：build/dynamic-water-fall.log。实际完成结果以日志中的 required tests passed 为准；Gradle 的成功提示本身不保证测试服务器成功启动。

范围：只修正逐步动画的实际下落距离，旧同步提交入口与其他 A02 环境策略另行审计。当前修改未提交或推送；请勿批量暂存其他人的未提交工作。

最终验证：318 项服务器必需 GameTest 全部通过，构建成功。
