# 开发工具

## 按改动选择工作资料（`workspace_context.py`）

```powershell
python tools/workspace_context.py
python tools/workspace_context.py --files src/main/java/com/matuvent/mineturn/mixin/RavagerBattleMixin.java
python tools/workspace_context.py --base HEAD
python tools/workspace_context.py --check
python -m unittest discover -s tools -p "test_*.py"
```

默认合并 Git 已暂存、未暂存和未跟踪文件；`--base` 另外包含相对指定版本的差异。
`--files` 用于只关注本次任务，路径以仓库根目录为基准。支持中文、空格及 Windows 路径分隔符。

工具输出祖先目录的 AGENTS.md，以及 [context_rules.json](context_rules.json) 匹配的专题文档；
不会把文档内容全部塞入输出，不自动执行其中命令。规则中的 `*` 可以跨目录匹配。
`--check` 检查规则的目标文件和 Git 可见 AGENTS.md 的本地链接，并执行原样的
`git diff --check` 和 `git diff --cached --check`；任一失败均返回非零退出码。
未跟踪的 UTF-8 文本另查行末空格和制表符，逐行报告。
通过 `git check-attr` 读取声明；对全部已跟踪及未跟踪的 `eol=lf` 文本检查 CRLF，每文件只报一次。
跳过二进制和符号链接；不把允许 CRLF 或未声明 LF 的文件当成换行违规。
不验证链接锚点或内容语义；资料推荐模式不执行空白检查。
工具本身只读，不修改 Git 或自动启动构建；失败返回非零退出码。无需第三方依赖。

工作方式：根入口只保留通用约定，局部约定放模块入口，行为说明继续维护在专题文档。
增删文件时更新引用和规则，避免复制历史测试数字、绝对机器路径和易过期的源码行号。

## 数据包示例静态检查（`tools/validate_examples.py`）

在仓库根目录运行：

```bash
python tools/validate_examples.py
```

退出码 `0` 表示全部通过；`1` 表示至少有一处问题，并在末尾逐条列出。
**只用 Python 标准库**，无第三方依赖，不需要启动游戏或 Gradle。
文档链接扫描跳过 `build`、`dist` 等产物目录，避免把历史试玩包里的文档副本当作当前源码；当前文档与数据包仍逐项检查。

## 成品 JAR 结构核验（`tools/verify_release_jar.py`）

对**已经构建好的**模组 JAR 做只读结构核验：

```bash
python tools/verify_release_jar.py build/libs/mineturn-1.0.0.jar
```

退出码 `0` 表示结构通过，`1` 表示有失败项（逐条列出原因），`2` 表示路径不可读。
**只用 Python 标准库**；默认**只读**——不构建、不启动游戏、不解压到工作区、不修改或删除产物、不联网。
路径支持空格与中文。

### 它检查什么

| 检查 | 说明 |
| --- | --- |
| ZIP 完整性与重复条目 | `testzip()` 加条目重名统计 |
| 模组元数据 | `META-INF/neoforge.mods.toml` 存在，且声明 `modId`、`version`、`license` |
| Mixin 配置 | 元数据里 `[[mixins]] config` 指向的配置**确实在包内** |
| Mixin 类 | 配置的 `package` + `mixins` / `client` / `server` 列表**逐一解析为 `.class` 条目** |
| 必要资源 | `assets/mineturn/lang/{en_us,zh_cn}.json`、`animation_camera.json`、`data/mineturn/` 下有内容 |
| JSON 语法 | 包内**每个** `.json` 成员都能解析 |

模组版本等**从元数据读取**，工具里不写死版本号或测试数。

### 它刻意**不**检查什么

- **不模拟游戏的资源优先级**，不判断数据包覆盖谁
- **不把合法的外部资源引用判错**（示例包允许引用基础包，JAR 同理）
- **不证明游戏行为或发布就绪**——通过只代表**包结构**成立

### 单元测试

```bash
python -m unittest discover -s tools -p "test_*.py"
```

`tools/test_verify_release_jar.py` 用**临时合成 JAR** 覆盖：文件缺失、ZIP 损坏、重复条目、
缺 Mixin class（含 client 列表与包名不匹配）、坏 JSON、缺必要资源、元数据带行尾注释、
带空格与中文的路径。

## 它检查什么

| # | 检查项 |
| --- | --- |
| 1 | 每个 `examples/<名字>/` 都有合法的 `pack.mcmeta`：JSON 可解析、`pack.pack_format == 48`、`description` 非空 |
| 2 | 每个示例都有 `data/<命名空间>/…` 结构，且命名空间名符合资源位置字符集 |
| 3 | 该目录树下所有 `.json` 都能解析（UTF-8、无 BOM、语法正确） |
| 4 | `actions/` 与 `grants/` 下的文件名推导出的 id 是合法资源位置 |
| 5 | `build.gradle` 里引用的 `examples/<名字>` 目录都真实存在 |
| 6 | 文档中的**本地文件链接**指向存在的文件（跳过 `http(s)`、`mailto:`、纯锚点） |
| 7 | 缺少 `README.md` 的示例会在 notes 中提示（不算失败） |

## 它刻意**不**检查什么

示例是**部分数据包**，设计上会引用基础模组（`src/main/resources/data/mineturn/`）或其它数据包里的
动作、物品和函数。因此：

- **悬空引用不是错误**。脚本不要求每个示例自包含。
- 不校验动作 schema 的字段语义（那由游戏内的加载期校验负责，见 `CombatData`）。
- 不校验原版函数（`.mcfunction`）的命令是否合法。
- 不校验数值平衡或行为正确性。

## 已知局限

- 只做语法与结构检查，**不能替代** `gradlew runGameTestServer` 的行为验证。
- 链接检查是**文本级**的：不做大小写不敏感匹配，也不检查锚点（`#section`）是否真的存在。
- 依赖 `pack_format == 48`（Minecraft 1.21.1）。升级游戏版本时需同步改脚本里的常量。

## 排除目录

以下目录不属于交付内容，扫描时会跳过，避免第三方依赖里的 README 产生大量假报错：

`.git`、`.gradle`、`.idea`、`.github`、`build`、`run`、`run-gametest`、`outputs`、`node_modules`、
`repo`、`__pycache__`

## 实测结果（2026-10-08）

```
example packs checked : 12
json files parsed     : 54
notes (7): ...
OK: all example checks passed
```

反向验证（确认脚本真的能抓错，验证后已全部还原）：

| 注入的错误 | 是否被抓到 |
| --- | --- |
| `pack.mcmeta` 被写坏（BOM / 语法错误） | ✅ 报 invalid JSON |
| action JSON 被截断 | ✅ 报 invalid JSON 并给出文件路径 |
| 文档中的链接指向不存在的文件 | ✅ 报 link target does not exist |

## notes 里两个待办（不阻塞）

1. `examples/teleport-action` 未被 `build.gradle` 引用 —— 它是可手动放入的数据包，属正常；
   如需打包成 ZIP，在 `build.gradle` 里加一个 Zip 任务即可。
2. 6 个示例没有 `README.md`（`batch-ai`、`function-ai`、`function-ai-override`、`granted-laser`、
   `spatial-boss`、`teleport-action`）—— 建议补一段简短说明，但不是错误。
