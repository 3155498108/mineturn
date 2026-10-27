# 中国版 Java 经典版移植

## 官方依据与目标

- [开发者 API 文档](https://mc.163.com/dev/apidocs.html?target=browser)中的 ModAPI / ModSDK 配合基岩版附加包和 Python 使用；本任务目标是用户指定的中国版 **Java 经典版**，不把基岩版 SDK 接口当作 Java 模组接口。
- [官方开发者常见问题](https://github.com/MCNeteaseDevs/netease-bedrock-wiki/blob/main/mcguide/50-%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98%E7%AD%94%E7%96%91/86-%E5%BC%80%E5%8F%91%E8%80%85%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98%E6%B1%87%E6%80%BB.md)列出的 Java 1.20.1 对应 **Forge 47.3.0**。先针对这一明确基线开发；开发者平台列出的其他游戏版本不代表本端口已经支持。
- [官方开发工作台说明](https://github.com/MCNeteaseDevs/netease-bedrock-wiki/blob/main/mcguide/12-%E5%85%A5%E9%97%A8%E6%95%99%E7%A8%8B/20-MC%20Studio%E4%BD%BF%E7%94%A8%E8%AF%B4%E6%98%8E.md)允许导入 Java 作品进行测试，但不能在工作台内编辑 Java 代码。
- [官方包体说明](https://github.com/MCNeteaseDevs/netease-bedrock-wiki/blob/main/mconline/10-addon%E6%95%99%E7%A8%8B/%E7%AC%AC18%E7%AB%A0%EF%BC%9A%E6%89%93%E5%8C%85%E5%AF%BC%E5%87%BA%E4%BD%A0%E7%9A%84%E4%BD%9C%E5%93%81/%E8%AF%BE%E7%A8%8B01.%E5%8C%85%E4%BD%93%E7%9A%84%E7%BB%93%E6%9E%84%E5%92%8C%E5%AF%BC%E5%85%A5.md)要求 Java 模组使用 JAR；网页手动上传时将 JAR 放入文件夹，再将文件夹压成 7z。不同游戏版本对应不同 JAR 时，不能把多个 JAR 混在一个组件中假定客户端会自行选择。

## 实施边界

`forge/` 是独立的 Forge 1.20.1 构建；`tools/forge_port.py` 严格应用已有 `fabric/ports/1.20.1.patch` 来生成共同源码，Forge 专用注册、网络和客户端入口放在 `forge/src/`。生成源码和运行目录均为构建中间产物，不修改原版 `run/` 存档。1.20.1 不含重锤、风弹、旋风人等后续原版内容。

MixinExtras 的 Forge 依赖及配置参考其[官方安装说明](https://github.com/LlamaLad7/MixinExtras#setup)；必须验证最终发行 JAR 真正包含运行时依赖和混淆后的 refmap，不能只凭源码编译通过。

## 验收与发布

1. 构建 Forge 47.3.0 JAR，检查打包、依赖、Mixin 和专服启动；客户端和服务端使用同一协议。
2. 用 MC Studio 导入该 JAR，选择 Java 版和 1.20.1，实际打开独立测试世界，检查入战圈、R 进入/退出、AT 顺序、攻击/战技、移动、镜头、界面和友军。
3. 单独验证联机和重启存档；服务端日志与国际版开发客户端验证不能替代中国版工作台和正式客户端验证。
4. 在平台如实说明上游 [Matuvent/mineturn](https://github.com/Matuvent/mineturn) 与 LGPL-2.1-only 许可，保留本项目的 `LICENSE`、`NOTICE` 和对应源码。开发者平台对非原创作品要求提交商业授权证明，最终是否接受开源许可证证明以平台审核为准。
5. 完成相应验证后，再上传匹配版本的 JAR 或 7z；更新记录以 [任务表](TASKS.md)为准。

目前本机 Forge 构建、开发客户端战斗和友军重启流程、候选 JAR 专服启动与重载已通过；具体记录见[任务表](TASKS.md)。网易工作台、中国版客户端、多人验证及平台提交尚未完成，候选包不等于国服验收通过。
