package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.BattleManager;
import com.matuvent.mineturn.platform.MobSplit;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Slime.class)
public abstract class FabricSlimeMixin {
    @Unique private java.util.List<Mob> mineturn$children;
    @Inject(method="remove",at=@At("HEAD"))
    private void mineturn$start(Entity.RemovalReason reason,CallbackInfo ci){
        mineturn$children=new java.util.ArrayList<>();var parent=(Slime)(Object)this;
        BattleManager.split(parent,new MobSplit(){public boolean isCanceled(){return false;}public java.util.List<? extends Mob> getChildren(){return mineturn$children;}});
    }
    @WrapOperation(method="remove",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean mineturn$children(Level level,Entity entity,Operation<Boolean> original){
        if(BattleManager.temporarySummon((Entity)(Object)this))return false;
        boolean added=original.call(level,entity);if(added && entity instanceof Mob mob)mineturn$children.add(mob);return added;
    }
}
