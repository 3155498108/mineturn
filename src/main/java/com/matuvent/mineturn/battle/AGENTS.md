# 服务器战斗

入口：BattleManager → BattleSession；时间轴看 Timeline，状态看 BattleStatus，地形路径看 TerrainPath / AquaticPath。修改回调时核对预检、扣费、执行和退出期间的成员归属；普通攻击不附加击退，技能位移走专用接口。回归测试也有放在本包的 *GameTests。

按修改内容读取：

- [入场、成员与离场](../../../../../../../docs/BATTLE_LIFECYCLE.md)
- [回调授权与战场隔离](../../../../../../../docs/COMBAT_BOUNDARIES.md)
- [行动资源](../../../../../../../docs/TURN_RESOURCES.md)
- [移动与位移](../../../../../../../docs/MOBILITY_AND_SKILLS.md)
