# A05：转换时间轴基础（2026-10-12）

新增 `Timeline.replace(previous, replacement)`，原位替换参与者身份：

- 保留剩余行动距离、当前 AV 与并列顺序，不生成新一圈进度。
- 新实体敏捷仅影响剩余距离所需时间，遵循既有 10000 路程模型；不按原敏捷冻结后续等待时间。
- 拒绝将一个参与者覆盖为已经存在的另一个参与者；未知来源返回 false，同一身份返回 true 且不变化。

GameTest 覆盖转换前已有部分进度、并列先后、预测和真实出手一致、新敏捷不改变其他成员已跑进度、非法合并无副作用，以及已经就绪的参与者保留出手机会。

这只是 A05 的底层基础，没有启用僵尸/尸壳/骷髅转换。原版计时、生成取消、装备唯一交接、成员/仇恨/预算/锁及技能引用清理仍必须完成，不能据此把 A05 标为已完成。原版源码调查见 `CODEX_A05_RESEARCH_NOT_IMPLEMENTED.md`。

验证：389 项必需 GameTest 全部通过，构建成功，`build/conversion-timeline-foundation.log`；协议 22 未变。`git -c core.whitespace=cr-at-eol diff --check` 通过。
