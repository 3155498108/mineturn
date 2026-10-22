# Minecraft 可切换 AT 战斗模组：初步可行性调研

调研日期：2026-10-22。阶段：资料与部分源码核对，未搭建或运行 Minecraft 模组。

## 结论与范围

在 Minecraft Java 版中，以客户端和服务端共同安装模组的方式，实现「原版即时战斗 → 按键切入回合战斗 → 退出后恢复即时战斗」具有明确的技术可行性。按 SPD 决定行动频率、显示未来行动顺序、按招式设置行动后延迟都能实现。

这不是现有候选项目已经完整满足用户需求的结论；多人、第三方怪物、投射物与环境交互需要专项实现和实机验证。用户尚未确认 Java/基岩版、版本、加载器和玩法，暂不建立具体模组工程或锁定依赖。

## 官方参考：空之轨迹 the 1st

- 快速战斗可通过战术切换进入指令战斗；眩晕敌人后切入可获得先制收益。
- 指令战斗显示 AT 行动顺序及 AT 奖励，并有移动范围。
- 招式存在不同的硬直；官方举例防御与使用道具的硬直为普通攻击的一半。
- 退却可返回快速战斗；敌方先制时，敌我合计行动五次前不能退却。因此「任意时刻无条件双向切换」应作为我们独立确认的规则。

来源：
- https://www.falcom.co.jp/manual/sora/page/05/01.html
- https://www.falcom.co.jp/manual/sora/page/06/01.html
- https://www.falcom.co.jp/manual/sora/page/06/02.html

## GitHub 现有实现

### Matuvent/mineturn

地址：https://github.com/Matuvent/mineturn

README 标明 Minecraft 1.21.1 / NeoForge 21.1.251 / Java 21，目前为可工作的原型。具备按敏捷驱动的 AV 时间轴、未来行动预测、战术移动、多人共同时间轴及数据包战斗定义。

已阅读 Timeline.java、BattleParticipation.java、BattleManager.java：时间轴计算剩余行动距离除以敏捷，并推进到下一事件；服务端通过战斗成员状态限制实时伤害和怪物 tick。说明核心机制有可供核对的实现，不只是玩法描述。

差异：当前以攻击触发战斗；尚不能视为已提供所需的自由按键双向切换。README 明确指出重连战斗恢复、完整地形寻路、地形隔离和客户端自动化验证等仍有边界。其测试数量和效果为作者声明，本次没有执行。

许可证文件需在实际复用前按固定提交再次核对；README 标注 LGPL-2.1-only。本次没有复制项目源码。

源码入口：
- https://github.com/Matuvent/mineturn/blob/main/src/main/java/com/matuvent/mineturn/battle/Timeline.java
- https://github.com/Matuvent/mineturn/blob/main/src/main/java/com/matuvent/mineturn/battle/BattleManager.java

### Stephen-Seo/TurnBasedMinecraftMod

地址：https://github.com/Stephen-Seo/TurnBasedMinecraftMod

README 提供玩家与怪物、玩家之间的回合战斗，支持原版弓箭及配置第三方怪物；区分 Forge / NeoForge 分支，可参考伤害接管、战斗成员管理和退出处理。

注意：本次读取的 neoforge 分支 gradle.properties 已指向 Minecraft 26.2，不能直接拿它当 1.21.1 工程。README 列出 MIT 许可证。本次未构建运行，也未确认其行动调度适合轨迹式 AT。

## 初步实现建议（待用户确认）

- Java 模组使用 JDK、Gradle 和目标加载器的模板，编译为 jar 安装。1.21.1 NeoForge 官方文档要求 JDK 21。
- 按键与 UI 在客户端；参战范围、行动合法性、伤害和时间轴由服务端决定。单人游戏也保留相同逻辑边界。
- 建立单场战斗的参与者集合，仅接管参战实体；不使用暂停整个服务器的办法。
- 建议采用事件推进的 AT：玩家选择指令时不倒计时，确认行动后推进逻辑时间。相同行动成本下，SPD 高的单位行动频率更高。普通攻击、魔法蓄力和强力技能可有不同延迟；这是一种拟议模型，并非已证实的原作精确公式。
- 若没有现成整合包限制，可优先评估 1.21.1 + NeoForge + Java 21，以便利用已找到的候选实现；这不是对最新版本的判断，也未锁定方案。

官方开发资料：
- https://docs.neoforged.net/docs/1.21.1/gettingstarted/
- https://docs.neoforged.net/docs/1.21.1/networking/

## 关键难点与必须验证的行为

1. 切入和退出：保留 HP、状态和位置；处理动画或投射物进行中的切换，避免双重结算。
2. 环境时间：箭矢、苦力怕引爆、燃烧、中毒、摔落、溺水，以及战斗外攻击者的处理。
3. 地形：高度差、墙壁、洞穴、水下、飞行目标；独立战场可降低部分难度，但改变体验。
4. 多人：共同时间轴、旁观和中途加入、断线及死亡、跨维度退出；不能让一个玩家暂停整个世界。
5. 兼容性：第三方 Boss、战斗模组和武器技能需要逐项适配，不能承诺通用兼容。

## 下一步要确认

- 游戏版本、Java/基岩版、现有加载器和必须兼容的模组。
- 单人优先或多人联机优先。
- 同键自由退出，或退却/冷却/敌先制限制；仅回合间切换还是行动途中也可请求。
- 原地战场或独立竞技场；第一人称或第三人称战术镜头。
- 初版保留原版装备与攻击，或同时加入战技、魔法、CP/EP、队友等系统。

## 本次工作状态

原目录为空且不是 Git 仓库；已在该目录初始化 Git。本文件是调研记录，不是可运行模组，不包含已经通过游戏验证的承诺。
