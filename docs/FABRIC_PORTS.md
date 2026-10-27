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

输出位于 `fabric/build/ports/<游戏版本>/libs/`。1.20.1 的模组字节码目标为 Java 17，其余为 Java 21。开发运行目录与正式 `run/`、其他版本的运行目录分开。

`tools/fabric_ports.py prepare` 合并既有共同源码和 Fabric 入口，再由 Git 严格应用 `fabric/ports/<版本>.patch`。补丁上下文不匹配会失败，不静默丢弃改动。`capture` 用于开发时将生成目录里的变更保存为补丁，生成的 Java 不提交。`resources` 只调整该版本生成目录中的数据包路径、原版不存在的条目和物品模型。

开工前检索了 [Stonecutter 官方多加载器模板](https://github.com/stonecutter-versioning/stonecutter-template-multiloader) 和实际多版本项目；当前继续使用已有 Fabric Loom / 共同源码结构，以 Git 的补丁工具处理版本差异。接口调整参照 [Fabric 1.21.6–1.21.8](https://fabricmc.net/2025/06/15/1216.html)、[Fabric 1.21.9–1.21.10](https://fabricmc.net/2025/09/23/1219.html) 和目标版本原版映射/字节码。

## 版本边界

- 1.20.1 没有原版重锤、风弹、旋风人、沼骸、犰狳或狼铠；安装包不伪造这些原版内容。使用旧物品 NBT 和原生 Fabric 网络传输，保留个人友军收纳、速度 AT 和 R 按键周期。
- 1.20.1 的内置 AI 宏/返回语句由有命令数和递归深度上限的兼容执行器处理；原版 `/function` 不被替换。
- 1.21.8 起分别适配新的实体渲染状态、二维 GUI 矩阵、物品模型定义、实体存储接口、伤害与盾牌回调。1.21.10 还需新的输入事件与渲染提交入口。
- 每个 JAR 只声明它实际构建的 Minecraft 版本。客户端与服务器须安装同一游戏版本、同一适配包和对应的 Fabric API。

## 验证记录

当前结果统一记录在[任务表](TASKS.md)顶部；编译成功、服务器启动、实际客户端流程分开记录。原 1.21.1 的测试数字不自动沿用到其他版本。
