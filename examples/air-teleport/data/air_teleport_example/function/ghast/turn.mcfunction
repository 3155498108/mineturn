# 恶魂的回合：优先原版火球，否则用 allow_air 的空中闪现脱离/换位。
#
# 关键点：`mineturn ai use <action>` 对 self=true 的动作自动以自身为目标，
# 不需要先选中敌人。所以闪现即使没有目标也能用。
#
# @s 就是当前行动的生物。

# 1) 冷却就绪时先打火球（沿用模组内置行动）。
execute store result score @s mineturn.ai run mineturn ai query ready mineturn:ghast_fireball
execute if score @s mineturn.ai matches 1 run return run mineturn ai use mineturn:ghast_fireball

# 2) 否则尝试空中闪现。allow_air=true 让 flying 生物可以落到无支撑的空中位置。
execute store result score @s mineturn.ai run mineturn ai query ready air_teleport_example:blink_up
execute if score @s mineturn.ai matches 1 run return run mineturn ai use air_teleport_example:blink_up

# 3) 都在冷却：等待。
return run mineturn ai wait
