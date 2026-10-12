# 开发工具

用法集中在 [README.md](README.md)。现有工具只用 Python 标准库，默认只读，不自动运行游戏、不提交、不发布。

更改 workspace_context 时运行 `python -m unittest discover -s tools -p "test_*.py"` 及 `python tools/workspace_context.py --check`；保持带空格/中文路径、暂存和未跟踪文件可用。
