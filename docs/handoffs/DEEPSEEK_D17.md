# 交接：D17 只读成品 JAR 核验工具

日期：2026-10-16 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

对应 第二批任务 D13–D17（原任务文件未随上游仓库提供） 的 **D17**，P1。

**状态：完成**，工具与单元测试均已实跑，并核对了真实成品 JAR。

---

## 新增文件与入口

| 文件 | 说明 |
| --- | --- |
| `tools/verify_release_jar.py` | 只读核验工具，仅标准库 |
| `tools/test_verify_release_jar.py` | 16 项单元测试，用临时合成 JAR |
| `tools/README.md` | 新增「成品 JAR 结构核验」一节（含检查项、不检查项、测试命令） |
| `docs/RELEASE_CHECKLIST.md` | §7「自动化（可脚本验证）」新增一条 |

用法：

```bash
python tools/verify_release_jar.py build/libs/mineturn-1.0.0.jar
```

退出码 `0` 通过 / `1` 有失败项 / `2` 路径不可读。

---

## 只读边界（任务硬要求）

| 要求 | 实现 |
| --- | --- |
| 显式接收 JAR 路径 | 位置参数，必填 |
| 默认只读 | 仅 `Path.read_bytes()` + `ZipFile` 读；**不写任何文件** |
| 不构建 / 不启动游戏 | 无 Gradle、无进程调用 |
| 不解压到工作区 | 全程用 `io.BytesIO` 内存读取 |
| 不修改/删除产物 | 无写操作 |
| 不联网 | 无网络调用 |
| 仅标准库 | `argparse`/`hashlib`/`io`/`json`/`re`/`sys`/`zipfile`/`pathlib` |

**路径支持空格与中文**，有专门测试。

---

## 检查项（先核对真实 JAR，未凭空要求）

核对 `build/libs/mineturn-1.0.0.jar` 的实际结构后确定：

| 检查 | 依据 |
| --- | --- |
| ZIP 完整性 + **重复条目** | `testzip()`，条目重名统计 |
| 元数据存在且声明 `modId`/`version`/`license` | 实包为 `META-INF/neoforge.mods.toml` |
| `[[mixins]] config` 指向的配置**在包内** | 实包声明 `mineturn.mixins.json` |
| Mixin 类逐一解析为 `.class` | 实包配置含 `package`、`mixins` 与 **`client`** 两个列表 |
| 必要资源 | 实包含 `assets/mineturn/lang/{en_us,zh_cn}.json`、`assets/mineturn/mineturn/animation_camera.json`、`data/mineturn/` |
| 包内**每个** `.json` 可解析 | 实包 125 个 JSON 成员 |

**版本从元数据读取**，工具里不写死版本号或测试数。

### 刻意不检查（已在 README 写明）

- 不模拟游戏资源优先级、不判断数据包覆盖关系
- 不把合法的外部资源引用判错
- **通过只代表包结构成立，不代表游戏行为或发布就绪**

---

## 真实验证命令与结果

### 1. 单元测试

```bash
python -m unittest discover -s tools -p "test_*.py"
```

| 结果 | |
| --- | --- |
| `tools/test_verify_release_jar.py` | ✅ **16 项通过** |
| `tools` 全部测试 | ✅ **24 项通过**（含既有 `test_workspace_context.py` 8 项） |

覆盖：文件缺失、**ZIP 损坏**、**重复条目**、**缺 Mixin class**（含 client 列表与包名不匹配）、
**坏 JSON**、缺必要资源、**元数据带行尾注释**、带空格与中文的路径、`size`/`sha256` 记录。

### 2. 真实成品 JAR

```
verify_release_jar: build/libs/mineturn-1.0.0.jar
  size: 1004087 bytes
  sha256: c8cfd7bf18250273fa79b2631f052d67e4b99bb5cf3fab0d17e93c97fc9f07fb
  mod id: mineturn  version: 1.0.0  license: LGPL-2.1-only
  mixin classes resolved: 49
  data resources under data/mineturn/: 148
  JSON members parsed: 125
RESULT: OK (structure only; this does not prove game behaviour)
```

退出码 **0**。

---

## 工具开发中实际抓到的一个 bug（已修）

**首轮对真实 JAR 运行时报**：

```
FAIL META-INF/neoforge.mods.toml: no [[mods]] block found
FAIL metadata declares mod id None, expected 'mineturn'
```

**但元数据里明明有 `[[mods]]`**。原因是**模板写的是 `[[mods]] #mandatory`**——
我的表头正则要求整行匹配，行尾注释导致它不匹配。

**这是我的解析器缺陷，不是 JAR 的问题。** 已修：新增 `_strip_comment()`，
在**引号外**剥离行尾 `#` 注释（避免误伤引号内的 `#`），表头与键值行都先剥离注释。

修正后真实 JAR 通过。**并已加回归测试** `test_metadata_with_annotated_headers_parses`，
用带注释的表头与含 `#`、`[[mods]]` 字样的多行描述验证解析。

> 值得注意：`neoforge.mods.toml` 是**模板生成**的，注释很多且带行尾注释。
> 任何基于逐行正则的读取都必须先处理注释——这是本工具第一次真实运行才暴露的。

### 关于 TOML 解析

本机 Python 是 **3.10.2**，**没有 `tomllib`**（3.11+ 才有）。
任务要求只用标准库，因此工具内含一个**最小 TOML 读取器**，只取
`key = value` 标量与 `[[table]]` 块，注释中已说明**它不是通用 TOML 解析器**。
若将来 CI 用 3.11+，可换成 `tomllib` 而无需改调用方。

---

## 未做

| 项 | 说明 |
| --- | --- |
| 构建或重新打包 | 任务要求不构建；工具只读现有产物 |
| 修改版本号 / 许可证 | 未改 |
| 模拟游戏资源优先级 | 明确不检查，已在 README 说明 |
| 校验示例 ZIP | 属 `validate_examples.py` 的范围，未重复 |

---

## 发现的问题

**无新增产品缺陷。** 两点观察：

1. **`build/libs/mineturn-1.0.0.jar` 是当前唯一产物**，大小 1004087 字节。
   若你后续重新构建，SHA-256 会变，属正常。

2. **元数据里 `loaderVersion="[1,)"`**（模板默认），而 `neoforge.mods.toml` 注释说
   "This is currently 2"。模板原文如此，**不是本次改动引入**，也未在核验范围内；
   仅作为观察记录，未擅自修改（任务禁止改版本相关字段）。
