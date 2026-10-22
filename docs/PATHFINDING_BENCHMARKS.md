# 寻路场景基准与回归检查

2026-10-09，D06 交付后的 Codex 复核。代码：`src/main/java/com/matuvent/mineturn/test/PathfindingBenchmarkGameTests.java`。

## 覆盖范围

这组测试主要测量直线路径采样和碰撞检查，不是完整绕障搜索器的访问节点基准。不能根据返回的采样点数推断搜索节点数、整体寻路复杂度或多人服务器性能。

| 场景 | 实际入口与检查 |
| --- | --- |
| 平地 2/4/8 格 | TerrainPath.traceFrom，端点费用、采样输出规模 |
| 四级台阶 | 地面上升/下降，下降水平费用为 4 |
| 一格走廊 | 直走完成，向侧墙移动被截断 |
| 正面墙体 | 地面路线不得穿墙 |
| 密闭水池 | AquaticPath.trace 与 SpatialPath.trace(swimming=true)，真实水下身体、顶盖和侧墙，检查截断与保持浸水 |
| 飞行越墙 | SpatialPath.trace：原高度被挡；上升 5、横移 8、下降 5 后抵达墙后，三维曼哈顿总费用 18 |
| 四个实体 | 分别生成独立路线，记录真实总采样数与总耗时；不代表战斗调度或网络负载基准 |

计时采用预热后五次最小值，仅记录，不设固定毫秒阈值。输出采样数量与算法内部访问数量不是同一个指标。

## 对 D06 初稿的复核修正

- 起点从结构边缘移到中间，避免请求方向撞上测试结构围栏；沿用 D06 的围栏距离检查。
- 原先把“采样数 = 移动费用 / 0.1”和“每条路线都有落地点”当作断言，已由 D06 删除。落地点只记录下落；垂直段不按水平费用计算，分段浮点取整也会改变样本数量。
- 原“飞行”测试调用 TerrainPath，设置 noGravity 不会改变该入口的地面语义。已改为真实 SpatialPath 分段越墙，不能再把两次地面计算相等视作飞行通过。
- 原涉水用例不等于密闭水下用例。已改为有顶盖的封闭水池，调用玩家游泳和严格水下入口；不根据不足的场景断言引擎允许穿墙或走水面。
- 原四人汇总行复用了单人的 Result，显示的不是总采样数。现单独输出真实 totalSamples。
- onGround 是可设置的状态，先 setOnGround(true) 再读不能验证地板。现检查身体下移少量后的实际碰撞。
- GameTestHelper 的相对坐标访问应使用同一坐标基准；不能把调试时坐标混用归结为 setBlock/getBlockState 存在不同高度语义。

## A10 本批优化

TerrainPath 不再把零长度拐点写入输出采样，但保留原有 control 检查顺序。水平曼哈顿费用、目标位置、碰撞、台阶与落地记录不变。减少的是移动执行时无位置变化的重复检查及放置操作，不据此声称整个搜索器已完成性能优化。

新增回归检查正向、斜向路线没有相邻重复位置，连续样本间隔不超过 0.1 格，费用、端点、控制拒绝和预览无副作用保持不变。台阶、动态地形和动画执行由完整回归继续覆盖。

## 运行与剩余工作

运行 `./gradlew.bat runGameTestServer build --offline`。共享测试世界被占用时可使用独立目录，方法见 [水瓶批次交接](handoffs/CODEX_A02_WATER_BLOCKS.md)。最新验证以 TASKS.md 和实际测试汇总为准。

D06 原交付报告 336/336 通过；这只是该次代码的历史结果，不能替代本次复核后的运行。原表中的飞行、四人总数不具备所声称的意义，不沿用作性能依据。

复核后完整验证：**337/337 通过，构建成功**，日志 `build/d06-review-a10.log`。密闭水下两种入口均在 4.2 格处被墙截断；真实飞行三段总费用 18，地面路线受墙阻挡仅能前进 3.2 格。

A10 已在 2026-10-10 增加 GroundRoutes / SpatialRoutes 的单次查询访问量和队列统计，以及地面邻居预算提前剪枝；见 [搜索统计交接](handoffs/CODEX_A10_SEARCH_METRICS.md)。344 项完整回归通过，日志 `build/search-budget-metrics.log`。多玩家预览请求负载、路径历史内存开销及水陆切换仍待后续处理。

同日第三批将 SpatialRoutes 改为共享父节点历史，保留有状态控制的完整顺序回放。新增绕墙回归记录新增路段 840 个样本、避免前缀引用复制 2100 次；该数据不等于堆内存字节。最终 349 项完整回归通过，日志 `build/spatial-parent-history-verified.log`，见 [空间历史交接](handoffs/CODEX_A10_SPATIAL_HISTORY.md)。地面历史和多玩家负载仍待优化。

第四批完成 GroundRoutes 的共享父节点历史，同时保留落地记录和追击预算裁剪。先下落再绕墙的场景记录新增样本 644、避免前缀引用复制 934 次。350 项完整回归通过，日志 `build/ground-parent-history.log`，见 [地面历史交接](handoffs/CODEX_A10_GROUND_HISTORY.md)。后续继续处理多人预览负载与水陆切换。

第五批修复过期预览占用当前请求名额，并在单次请求内复用已校验的激流路线。351 项完整回归通过，日志 `build/preview-load-boundaries.log`，见 [预览请求交接](handoffs/CODEX_A10_PREVIEW_QUOTAS.md)。真实多人持续负载和客户端延迟体验尚未验收。

第六批补齐 aquatic 追击的地面台阶回退与地面路线水中预算停步，新增一格上岸及入水前缀的实际执行回归。353 项完整回归通过，日志 `build/shoreline-pursuit-verified.log`，见 [水陆追击交接](handoffs/CODEX_A10_SHORELINE_PURSUIT.md)。严格水生模式不新增地面回退。

第七批完成四名模拟玩家的混合预览请求回归：40 个模拟战斗 tick 中发送 3840 次请求，得到 320 次限流后的回包，验证身份、行动权限、地形刷新及资源不变。354 项完整回归通过，日志 `build/multiplayer-preview-workload.log`，见 [多人预览交接](handoffs/CODEX_A10_MULTIPLAYER_PREVIEW.md)。该测试不计真实耗时，不是网络吞吐或 TPS 基准；真实联机延迟体验仍待验收。
