# Codex A02 首批：动态支撑检查

2026-10-08。已读取 DeepSeek 的 A12 和 D01–D03 交接；A12 已完成，不重复编辑演出代码。

本批改动：

- BattleSession.tickMotion：在纯地面模式及水陆模式的陆地段，对水平采样、计划落地点和终点检查支撑；抬升台阶和即将下落的过渡段保留。
- TerrainPath.supportedAt：纯位置查询，检查已加载区块内的整体脚下支撑，保留受控坐骑和水面行走条件。
- ContactGameTests：新增地板移除停止旧路线，以及等待时支撑移除后原版落地/不重复伤害回归。
- COMBAT_BOUNDARIES.md：记录 A02 第一批范围。

不改变网络协议，不修改 DeepSeek 的 D 系列测试；BattleSession 只增加支撑判断，没有改动作演出逻辑。

验证注意：首次启动发生 BattleStatus$Frame 缺失，随后重试发生测试世界文件锁占用；第二次 Gradle 虽报 BUILD SUCCESSFUL，Minecraft 实际未启动，不能算测试通过。待世界锁释放后另跑 build/ground-support-verified.log，以其实际测试汇总为准。不要删除 session.lock 或终止其他模型的测试进程。

A02 尚未全部完成：方块修改的全局策略和其他地形/液体边界仍需后续处理。当前允许外部地形变化，只取消失效的旧地面路线，移动已付费用不返还。

新回归曾发现检查仅覆盖 ground、漏过玩家实际使用的 amphibious 模式；已补齐水陆模式的干燥段。测试按世界坐标移除前方支撑，避免测试局部坐标与实际移动方向不一致。

最终验证：317 项服务器必需 GameTest 全部通过，BUILD SUCCESSFUL，见 build/ground-support-verified.log。此数量包含当前工作区其他模型已新增的测试。本批不提交或推送代码。
