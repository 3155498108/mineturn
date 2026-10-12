# 给 DeepSeek：工作区入口与资料选择工具

日期：2026-10-12。用户要求同步本次根据视频《提示词美学：勿写提示词》实施的工作区调整。
本文已写入共享工作区；未通过消息渠道送达，未收到 DeepSeek 回执。

## 接着工作前

1. 阅读根目录 [AGENTS.md](../../AGENTS.md)，再读取所修改模块内的 AGENTS.md。
2. 当前任务与分工仍以 [TASKS.md](../TASKS.md)、[WORK_ALLOCATION.md](../WORK_ALLOCATION.md) 为入口。本次没有新增分工，也没有调整 A/D 系列负责人。
3. 工作区已有双方未提交改动，保留它们；交接结论需要和当前源码核对。

## 本次变化

- 根 AGENTS.md 仅保留通用约定和资料入口。
- `src/main/java/com/matuvent/mineturn/` 下的 battle、client、data、api、mixin、network、test 各有局部 AGENTS.md；docs、examples、tools 也有各自入口。
- 模块入口链接到现有专题文档，不复制整套机制说明。长期定位采用类名、方法名与文件链接，避免只写源码行号。
- 中英文 README 已将过期的测试总数替换为任务表链接。历史交接中的测试数字保留为历史结果。
- 新增 [workspace_context.py](../../tools/workspace_context.py) 和 [context_rules.json](../../tools/context_rules.json)：根据改动选择资料，并校验入口链接。

## 使用方法

在仓库根目录运行；Python 标准库即可，无第三方依赖：

```powershell
# 当前全部改动，包括暂存、未暂存和未跟踪文件
python tools/workspace_context.py

# 共享工作区改动很多时，只查看本次负责的文件
python tools/workspace_context.py --files src/main/java/com/matuvent/mineturn/client/BattleClient.java

# 检查工作入口与规则引用
python tools/workspace_context.py --check
```

工具只输出资料路径，不自动读取全部正文、运行其中命令或启动构建。具体参数与测试命令见 [工具说明](../../tools/README.md)。
相关约定放到对应模块，通用入口保持简短；已有行为文档继续原处维护，不再建立重复副本。

## 验证与边界

本批仅调整文档和开发工具，没有修改游戏逻辑。4 项工具测试、入口链接检查、13 个示例包 / 56 个 JSON 的静态检查通过，`git diff --check` 通过。没有为这批文档修改重新构建模组。

另：用户已确认上一批劫掠兽冻结修复有效。实现与 AV 哨兵值说明见 [修复记录](CODEX_RAVAGER_FREEZE_FIX.md)，无需再按原交接建议将 `>= 0` 改为 `> 0`。
