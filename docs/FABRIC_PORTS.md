# Fabric 多版本适配

目标为 Minecraft **1.20.1、1.21、1.21.8、1.21.10**；用户所称 1.21.0 的正式版本号为 1.21。原 1.21.1 Fabric / NeoForge 工程保留。

## 构建与源码

构建使用 Java 21、Python 3 和 Git。在仓库根运行（PowerShell 中带点的属性值须整体加引号）：

```powershell
./gradlew.bat -p fabric '-PmcPort=1.20.1' build
./gradlew.bat -p fabric '-PmcPort=1.21' build
./gradlew.bat -p fabric '-PmcPort=1.21.8' build
./gradlew.bat -p fabric '-PmcPort=1.21.10' build
```

已固定的运行依赖如下。四个版本使用 Fabric Loader **0.18.4**，并安装对应版本的 Fabric API：

| Minecraft | Java 运行环境 | Fabric API |
| --- | --- | --- |
| 1.20.1 | 17 或 21 | 0.92.12+1.20.1 |
| 1.21（1.21.0） | 21 | 0.102.0+1.21 |
| 1.21.8 | 21 | 0.136.1+1.21.8 |
| 1.21.10 | 21 | 0.138.4+1.21.10 |

输出位于 `fabric/build/ports/<游戏版本>/libs/`。1.20.1 的模组字节码目标为 Java 17，其余为 Java 21。开发运行目录与正式 `run/`、其他版本的运行目录分开。

`tools/fabric_ports.py prepare` 合并既有共同源码和 Fabric 入口，再由 Git 严格应用 `fabric/ports/<版本>.patch`。补丁上下文不匹配会失败，不静默丢弃改动。`capture` 用于开发时将生成目录里的变更保存为补丁，生成的 Java 不提交。`resources` 只调整该版本生成目录中的数据包路径、原版不存在的条目和物品模型。

开工前检索了 [Stonecutter 官方多加载器模板](https://github.com/stonecutter-versioning/stonecutter-template-multiloader) 和实际多版本项目；当前继续使用已有 Fabric Loom / 共同源码结构，以 Git 的补丁工具处理版本差异。接口调整参照 [Fabric 1.21.6–1.21.8](https://fabricmc.net/2025/06/15/1216.html)、[Fabric 1.21.9–1.21.10](https://fabricmc.net/2025/09/23/1219.html) 和目标版本原版映射/字节码。

## 版本边界

- 1.20.1 没有原版重锤、风弹、旋风人、沼骸、犰狳或狼铠；安装包不伪造这些原版内容。使用旧物品 NBT 和原生 Fabric 网络传输，保留个人友军收纳、速度 AT 和 R 按键周期。
- 1.20.1 的内置 AI 宏/返回语句由有命令数和递归深度上限的兼容执行器处理；原版 `/function` 不被替换。
- 1.21.8 起分别适配新的实体渲染状态、二维 GUI 矩阵、物品模型定义、实体存储接口、伤害与盾牌回调。1.21.10 使用新的按键/鼠标事件、模型提交队列、按键分类和资源重载入口。
- 每个 JAR 只声明它实际构建的 Minecraft 版本。客户端与服务器须安装同一游戏版本、同一适配包和对应的 Fabric API。

## 安装与验证记录

发行包为 [trails.11 Fabric 多版本预发行](https://github.com/3155498108/mineturn/releases/tag/v1.0.0-trails.11)。下载对应版本的 JAR 与上表 Fabric API，放入 Fabric 实例的 `mods` 文件夹；同一实例只保留一个 MineTurn JAR。四个端口分别通过构建、服务端启动/重载、真实客户端友军与 R 周期、完整入战流程；1.21 另通过 86 项服务器测试，两个新版另验证 Boss 金光和 S 技。1.21.10 的武器动作和旧存档收纳数据升级也已实测。

多人没有对每个端口重新跑双客户端验收，第三方模组兼容不在本轮范围。升级前备份存档，不能向低版本降级世界。

当前结果统一记录在[任务表](TASKS.md)顶部；编译成功、服务器启动、实际客户端流程分开记录。原 1.21.1 的测试数字不自动沿用到其他版本。
