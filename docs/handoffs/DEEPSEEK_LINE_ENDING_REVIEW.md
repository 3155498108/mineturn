# 交接：换行符规范化矛盾与 `--check` 的报告方式

日期：2026-10-12 ｜ 作者：**DeepSeek** ｜ 收件人：**Codex**

**来源**：用户要求复核两个此前提出的问题。**复核结果修正了我自己的结论**，两个问题里只有一个仍然成立，
且根因在仓库级，不是某一批改动。

**本文只做定位与建议，未修改任何文件。** 涉及你的在途文件（`tools/workspace_context.py`、
`README.md`、`TASKS.md`、4 份 `CODEX_*.md`、9 个 `AGENTS.md`），需授权后才能动。

---

## 结论速览

| 原问题 | 复核结果 |
| --- | --- |
| ① `git diff --check` 不通过（26 个文件尾随空格） | **已解决**，现在通过 |
| ② `--check` 不查空白（我称"工具缺口"） | **我说错了**。你已补上，且 `AGENTS.md` 已写明 |
| ③ 换行符：`--check` 报 145 行 | **报告是真的**，但**根因是全仓库 207 个已跟踪文件的 CRLF 与 `.gitattributes` 矛盾** |

**我此前对 ② 的表述有误**：我把它当成"文档承诺了但工具没做"。实际是你已经做了，问题在于
**报告方式**与**仓库长期状态**，不是功能缺失。

---

## 一、已解决：尾随空格

`git diff --check` 现在无输出。此前报告的 26 个文件（含 `README.md`、`TASKS.md`、
`BattleSession.java` 等）已清理。

---

## 二、`--check` 的报告方式（两个可改进点）

`tools/workspace_context.py` → `check_whitespace`：

```python
for number, line in enumerate(lines, 1):
    if line.endswith((' ', '\t', '\r')):
        errors.append(f'{name}:{number}: trailing whitespace or CRLF in new text file')
```

### 问题 A：消息把两种不同的问题混为一谈

`\r` 是 **CRLF 行末**，与"尾随空格"是两类问题，却共用一条消息。读者无法从输出判断
到底是"某行多打了空格"还是"整个文件是 CRLF"。

**实测**：`docs/handoffs/CODEX_A01_CONTACT_HANDOFF.md` 是 **26 行纯 CRLF、零尾随空格**，
却报了 26 条同名错误。

### 问题 B：逐行报 + 不参考 `.gitattributes`

- **换行风格是文件级属性**，逐行报会把一个整文件问题放大成上百行噪音。
  当前总计 **13 个文件 / 145 行**，其中真正的"单行尾随空格"会被淹没。
- 实现对**所有**未跟踪文本硬编码检查 CRLF，没有用 `git check-attr` 判断该文件是否真的声明了 `eol=lf`。

**建议**（不改变检查严格度，只改报告）：

| 项 | 建议 |
| --- | --- |
| 换行 | **每文件报一次**，消息区分开，例如 `CRLF line endings (declared eol=lf)` |
| 尾随空格 | 保持逐行报，消息里不要再带 CRLF |
| 判定依据 | 用 `git check-attr text eol -- <file>`，只对声明了 `eol=lf` 的文件报 CRLF |

---

## 三、根因：仓库级的换行符矛盾（**需要用户决策**）

这不是本次新增的问题。用 `git ls-files --eol` 得到的实测数据：

| 指标 | 数量 |
| --- | --- |
| 索引中是 CRLF（`i/crlf`） | **215** |
| 其中 `.gitattributes` 声明 `eol=lf` | **207** |
| 索引 CRLF **且** 工作区 CRLF（完全未规范化） | **191** |
| 索引 LF 且工作区 LF（已正确） | 227 |

`.gitattributes` 现有声明：

```
*.java  text eol=lf
*.md    text eol=lf
*.json  text eol=lf
*.py    text eol=lf
```

**所以工具报 CRLF 是正确的**——违反的是明确声明过的约定。但它只检查**未跟踪文件**，
而已跟踪的 207 个文件同样违反，工具不报。

结果是一个长期矛盾：

> **新文件必须 LF，而仓库里 207 个已跟踪文件是 CRLF。**

### 三个方案（取舍需要用户拍板）

| 方案 | 做法 | 代价 |
| --- | --- | --- |
| **A** | `git add --renormalize .` 一次性规范化为 LF | 产生**触及 207 个文件的大 diff**；必须在**无并行改动**时执行，否则冲突面很大 |
| **B** | 只转当前的 13 个未跟踪文件；容忍历史矛盾 | 消除新增违规，但矛盾留存，后续每批新文件都要注意 |
| **C** | 放宽 `.gitattributes`，承认 Windows 现实 | 与"跨平台一致"初衷相反，**不推荐** |

**我不替你决定。** 如果需要，我可以只做 B（把 13 个未跟踪文件转为 LF），
A 和工具改动由你执行——A 在我这边做风险太高（你还有大量未提交改动）。

### 当前违规文件（全部未跟踪，均为你的在途文件）

```
docs/handoffs/CODEX_A01_CONTACT_HANDOFF.md
docs/handoffs/CODEX_A02_DYNAMIC_WATER.md
docs/handoffs/CODEX_A02_GROUND_SUPPORT.md
docs/handoffs/CODEX_A02_SYNC_MOVEMENT.md
examples/AGENTS.md
src/main/java/com/matuvent/mineturn/api/AGENTS.md
src/main/java/com/matuvent/mineturn/battle/AGENTS.md
src/main/java/com/matuvent/mineturn/client/AGENTS.md
src/main/java/com/matuvent/mineturn/data/AGENTS.md
src/main/java/com/matuvent/mineturn/mixin/AGENTS.md
src/main/java/com/matuvent/mineturn/network/AGENTS.md
src/main/java/com/matuvent/mineturn/test/AGENTS.md
tools/AGENTS.md
```

**注意**：其中 9 个是你本批新建的 `AGENTS.md`，正是这批工作区调整的一部分——
它们自己是 CRLF，与它们推广的约定相反，建议优先转 LF。

---

## 四、我这边核对过的（无问题）

用 `git ls-files --eol` 逐一确认，我交付的文件全部 `i/lf w/lf`：

- `docs/RELEASE_CHECKLIST.md`
- `docs/ACTION_CAMERA_CONFIG.md`
- `docs/PATHFINDING_BENCHMARKS.md`
- `docs/DATAPACK_OVERRIDE_SEMANTICS.md`
- `docs/CLIENT_ACCEPTANCE.md`
- `docs/TRIDENT_RECOVERY_ACCEPTANCE.md`
- `docs/handoffs/DEEPSEEK_*.md`
- `tools/validate_examples.py`、`tools/README.md`
- `client/ActionAnimations.java`、`client/ActionAnimationData.java`、`animation_camera.json`

**我没有引入过 CRLF，也没有留下尾随空格。**

---

## 五、复核时的验证记录

均在本工作区实跑：

| 检查 | 结果 |
| --- | --- |
| `gradlew compileJava` | 通过 |
| `gradlew runGameTestServer` | **389 项全部通过** |
| `python tools/workspace_context.py --check` | 退出码 1，报 13 个未跟踪文件的 CRLF |
| `python tools/validate_examples.py` | 13 包 / 56 JSON 通过 |
| `python -m unittest discover -s tools` | 4 项通过 |
| `git diff --check` | 通过 |
| `docs/TASKS.md` 状态表 | 写着 **389 项**，与实测一致 |

---

## 六、附：上一批劫掠兽冻结交接的更正（已完成）

我此前的 `DEEPSEEK_RAVAGER_FREEZE_HANDOFF.md` 建议把 `>= 0` 改成 `> 0`，**该建议是错的**，
你的修复记录已说明 `-1`/`0`/正数是三种不同哨兵语义。我已在那个文件顶部加了醒目标注，
指向 [CODEX_RAVAGER_FREEZE_FIX.md](CODEX_RAVAGER_FREEZE_FIX.md)，说明「本文修法建议请勿采纳」，
并记录了教训：**哨兵值设计不能只看一处比较就断定是笔误**。

## 相关文件

| 文件 | 关系 |
| --- | --- |
| `tools/workspace_context.py` | `check_whitespace` 的报告方式 |
| `.gitattributes` | 声明 `eol=lf`，与现状矛盾 |
| `AGENTS.md`、`docs/AGENTS.md` | 已把 `--check` 列为验证步骤（与你实现一致） |
| `docs/TASKS.md` | 当前状态入口，389 项与实测一致 |
