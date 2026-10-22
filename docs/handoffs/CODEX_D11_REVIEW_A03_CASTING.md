# D11 复核与 A03 扩展施放异常清理

2026-10-11。

## DeepSeek 交付

已读取 D11 提交 a978f22 与 RELEASE_CHECKLIST.md。保留其带日期的历史实测记录，不把 350 项旧记录改成伪造的新结果。修正以下问题：

- PowerShell 示例使用 .\gradlew.bat，不使用 Bash 的反斜杠续行。
- 专服按 NeoForge 官方说明使用生成的 run.bat/run.sh 与 user_jvm_args.txt，移除假定存在 server.jar 的启动命令；未代为安装生产服务器或接受 EULA。
- 安装说明不再声称所有命令均已本地实测。
- 原版挥手和部分使用姿态已存在，完整专用姿态尚未完成。
- 忠诚返回任务和战斗 AV 队列为内存态，回滚说明改为持久化的实体、物品、方块变化。

D12 依赖 A16–A20 稳定，本轮不解除依赖，也未向其他对话派发消息。

## A03 修复

请求处理入口已有运行时异常安全关闭，但公共 CombatCasting.cast 所调用的 castGranted 只捕获可预期的参数/状态拒绝。此前 runEffect 已付费异常被包装后，会直接抛给适配器，留下原战斗。

castGranted 新增 RuntimeException 捕获，记录原异常、安全关闭战斗并返回明确失败。不会退款或撤销原生副作用。普通预检错误仍返回拒绝，不关闭战斗。本批不解决资源提供者 trySpend 的任意部分支付异常语义。

grantedCastingFailureReleasesBattleAndDoesNotRetry 覆盖三种已付费异常、部分伤害保留、行动已扣、AV 不变、双方锁/运动/射击清理、授权恢复、重复施放拒绝，以及普通预检拒绝不消耗。

## 验证

- 362 项必需 GameTest 全部通过，构建成功：build/granted-casting-failure.log。
- tools/validate_examples.py：13 个包、56 个 JSON 通过；提示项仍保留。
- 协议 22 不变；未修改客户端代码，未进行客户端实测。
