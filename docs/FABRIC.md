# Fabric 与 NeoForge

其他 Minecraft 版本的 Fabric 适配、Java 与 API 依赖见 [FABRIC_PORTS.md](FABRIC_PORTS.md)；下表保留 1.21.1 / trails.10 的安装组合。

`trails.10` 使用共同的 AT 规则、数据包、网络消息和客户端界面，加载器分别提供注册、事件和原版注入。Fabric 不是通过兼容层运行 NeoForge JAR。

## 安装

| 环境 | 版本 | 安装文件 |
| --- | --- | --- |
| Minecraft Java | 1.21.1 | Java 21 |
| Fabric | Loader 0.16.14，Fabric API 0.116.6+1.21.1 为验证组合 | `mineturn-fabric-1.0.0-trails.10.jar`，另装 Fabric API |
| NeoForge | 21.1.251 | `mineturn-1.0.0-trails.10.jar` |

从 [GitHub Releases](https://github.com/3155498108/mineturn/releases) 选择对应加载器的 JAR，放入实例的 `mods` 文件夹。Fabric API 的官方下载在 [Modrinth](https://modrinth.com/mod/fabric-api)。同一实例只安装一个 MineTurn JAR；多人服务器与客户端使用同一加载器、相同版本及依赖。

默认 R 显示 20 格入战圈并切换，V 抢 S，B 设主武器，O 设置；玩家回合可按 E 换装备。详细操作见 [试玩说明](PLAYTEST.md)，多人权限见 [多人规则](TRAILS_MULTIPLAYER.md)。

已有 NeoForge 世界切换加载器前备份。Fabric 实体数据写入独立的 `mineturn:state`，本轮没有实现 NeoForge 附加实体数据的跨加载器迁移；不承诺旧命名牌归属/CP 自动转移。没有验证跨加载器联机或任意第三方模组兼容。

## 构建与原生适配

根构建保留 NeoForge；`fabric/` 使用官方 Mojang 映射与 Fabric Loom，共用根源代码和资源。分别排除加载器入口、平台实现、NeoForge 事件及有不同原版签名的 Mixin。`RuntimePlatform`、`PayloadRegistrar`、`WorldRenderFrame` 和 `MobSplit` 是共用代码的边界。

Fabric 使用原生 Payload API、生命周期和渲染回调；没有 Fabric 事件的伤害/AI 冻结、分裂、爆炸与掉落入口使用 Mixin。客户端接收器位于独立类，专用服务器不加载 LocalPlayer。呼吸保留原版潮涌能量、水下呼吸与气泡柱条件。

在仓库根目录设置 `JAVA_HOME` 到 JDK 21，然后运行：

```powershell
.\gradlew.bat build
.\gradlew.bat -p fabric build
.\gradlew.bat runGameTestServer
.\gradlew.bat runGameTestServer -PlegacyTests -PtestNamespaces=mineturn
.\gradlew.bat -p fabric -PfabricQa runGameTest
```

Fabric QA 从现有四个 `Trails*GameTests` 源文件生成加载器无关注册，保留原断言；额外原生边界测试位于 `fabric/src/qa/java`。生成内容只用于 QA，发行元数据不声明测试入口，JAR 排除测试类。正常构建通过官方 Maven 获取依赖；本机网络受限时复用的缓存经过官方清单哈希校验，不把本机缓存路径写进发行构建。

## 本轮验证

当前完整结果只更新 [任务表](TASKS.md)。服务器测试与真实客户端分别执行，不能用编译通过代替画面验收。Fabric 已验证共同 AT 测试、原生冻结/恢复、伤害/状态隔离、呼吸、分裂、爆炸推动与临时召唤清理；单人、Boss 演出、联机记录详见任务表。

82 种技能配置不代表每种生物每帧动画已验收；原版 Boss 完整阶段、复杂公网延迟、长期平衡和大队伍性能仍有原型限制。

## 投稿

trails.10 的两个 JAR 和源码已发布在 [GitHub](https://github.com/3155498108/mineturn/releases/tag/v1.0.0-trails.10)；本轮 Modrinth 连接中断，尚未同步。

此前 Modrinth 沿用 [同一项目](https://modrinth.com/mod/mineturn-at-prototype)，已上传 [Fabric trails.9](https://modrinth.com/mod/mineturn-at-prototype/version/xhh6YSAm) 和 [NeoForge trails.9](https://modrinth.com/mod/mineturn-at-prototype/version/FMfWa6lk)，前者标注 Fabric API 必需依赖。项目如显示 Under review，表示已经提交，尚未审核通过。

MC 百科申请为 `MineTurn AT Prototype`，审核编号 `808408`，可在登录账号的 [个人审核列表](https://www.mcmod.cn/verify.html?selfonly=1&order=createtime) 查看；不会重复创建申请。
