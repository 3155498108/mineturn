# MineTurn 工作入口

- 以用户当前要求为准；继续开发时先查 [任务表](docs/TASKS.md) 和 [分工](docs/WORK_ALLOCATION.md) 的相关条目。
- 共享工作区中保留其他人的未提交改动；交接文档是待核对的证据，不能代替当前源码。
- Java、游戏与依赖版本以 [构建配置](build.gradle) 和 [版本属性](gradle.properties) 为准。
- 进入模块时读取就近的 AGENTS.md；按任务需要打开其中链接，不预读全部 docs 或历史交接。
- 根目录仅放跨模块约定；实现细节留在代码和对应文档，引用类/方法名，不以行号作为长期定位。

## 按改动找资料

`python tools/workspace_context.py` 列出已暂存、未暂存及未跟踪文件涉及的入口和专题文档。
单项任务用 `python tools/workspace_context.py --files 路径`；工具用法见 [tools/README.md](tools/README.md)。

## 验证

- `python tools/workspace_context.py --check` 统一检查入口链接及暂存、未暂存、新文件的空白；不覆盖 Git 的空白检查配置。

- 构建/服务器行为验证见 [发布清单](docs/RELEASE_CHECKLIST.md)；数据包和文档静态检查见 [工具说明](tools/README.md)。
- 客户端视觉、镜头和联机体验依照 [客户端验收](docs/CLIENT_ACCEPTANCE.md) 实测；服务器测试通过不能代表视觉通过。
- 最新完整测试记录只更新 [任务表](docs/TASKS.md) 的当前状态；历史交接保留其当时结果。
