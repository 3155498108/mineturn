package com.matuvent.mineturn.platform;

import net.minecraft.world.phys.Vec3;

public record TeleportResult(boolean canceled,Vec3 target,float attackDamage) {
    public boolean isCanceled(){return canceled;}
    public Vec3 getTarget(){return target;}
    public float getAttackDamage(){return attackDamage;}
}
