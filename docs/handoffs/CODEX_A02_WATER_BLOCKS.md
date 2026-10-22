# Codex A02 第五批：水瓶方块灭火

2026-10-09。只处理原版水瓶方块灭火来源，不增加全战场地形保护。

- `BattleManager.allowsRealtimeSource` 从已有 `allowsRealtimeEffect` 提取来源链判断，实体效果仍额外检查目标是否锁定。
- `PotionBoundaryMixin` 在原版 `dowseFire` 入口阻止被战斗锁定来源的水瓶修改方块；不取消整个投掷物命中流程。
- 无来源/场外来源可灭火，离战后恢复；世界方块 tick 不冻结。战斗内水瓶物品动作仍沿用现有执行器。
- `ContactGameTests` 新增火方块、蜡烛、营火来源隔离/离战恢复回归，以及熄灭后的营火不再造成 AV 伤害验证。

首次 `build/water-block-boundaries.log` 虽显示 BUILD SUCCESSFUL，但测试世界被其他进程占用、服务器启动失败，不能计为测试通过。
改用 `build/water-boundary.init.gradle` 将测试工作目录及测试数据包复制目标同时切换到 `build/water-boundary-gametest`，没有修改 build.gradle、删除锁或停止其他进程。复现命令：

```powershell
$env:JAVA_HOME='D:/jdk'
$env:GRADLE_USER_HOME='C:/Users/晓风残月/.gradle'
./gradlew.bat --init-script build/water-boundary.init.gradle --no-configuration-cache runGameTestServer build --offline
```

该 init 脚本属于 build 临时产物，不纳入版本管理。若已清理 build，脚本内容为：

```groovy
allprojects {
    afterEvaluate {
        neoForge.runs.gameTestServer.gameDirectory = file('build/water-boundary-gametest')
        tasks.named('prepareFunctionAiTests', Copy) {
            into file('build/water-boundary-gametest/world/datapacks/mineturn-function-tests')
        }
    }
}
```

未提交或推送其他工作；协议 22 未改。客户端仍需实测。

## 验证结果与 D06 交接

`build/water-block-isolated.log`：336 GAME TESTS COMPLETE，3 required tests failed。本批两项测试通过；333 项通过，不能报告完整回归成功。

三项失败来自本次运行期间正在新增/修改的 `test/PathfindingBenchmarkGameTests.java`，没有修改该文件：

- narrowCorridor：cost 2.2，samples 62，断言要求约 22。
- stairsAndSlopes：cost 2.2，samples 62，断言要求约 22。
- flatGroundBaseline：cost 2.0，samples 56，断言要求约 20。

请 D06 不要假定采样点数等于费用 / 0.1。TerrainPath.traceFrom 每个小段经过 corner 和 point，零长度段也 Math.max(1, ...) 产生样本；上下台阶和落差还产生不等于水平计费长度的竖直采样。采样数是输出路线密度，不是寻路访问节点数。建议断言实际端点/碰撞/费用，采样数先作为测量数据；不要为满足基准断言修改生产计费规则。另外 landings 可为空，不能无条件 getLast()。

初次标准 build 已完成打包，但全套回归如上有 D06 失败；不得把存在 JAR 等同于完整验收成功。默认运行配置未改，临时目录可以用于后续验证。
