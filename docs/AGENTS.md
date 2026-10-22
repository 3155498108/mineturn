# 文档维护

- [TASKS.md](TASKS.md) 是当前进度入口；[WORK_ALLOCATION.md](WORK_ALLOCATION.md) 是分工入口。
- 专题文档记录行为契约；handoffs 是历史证据。出现矛盾时核对源码及用户最新决定，记录纠正，不静默重写他人的结论。
- 新增机制先找现有专题，避免复制多个版本的说明；引用类/方法名与相对文件链接。
- 测试通过、实现完成、客户端实测通过分别记录。文档改动运行 `python tools/validate_examples.py` 和 `python tools/workspace_context.py --check`（含原样 Git 空白检查）。
