# 示例数据包

先读对应示例的 README；格式见 [扩展规范](../docs/DATAPACK_EXTENSION_SPEC.md)，覆盖行为见 [覆盖语义](../docs/DATAPACK_OVERRIDE_SEMANTICS.md)。

示例允许引用基础模组或其他数据包，不要求每包自包含。修改后运行 `python tools/validate_examples.py`；加载和运行语义仍需 GameTest。打包任务见 [build.gradle](../build.gradle)。
