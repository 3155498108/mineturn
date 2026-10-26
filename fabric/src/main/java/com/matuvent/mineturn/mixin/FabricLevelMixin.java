package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.BattleManager;
import com.matuvent.mineturn.api.CombatChanneling;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(ServerLevel.class)
public abstract class FabricLevelMixin {
    @Inject(method="tickNonPassenger",at=@At("HEAD"),cancellable=true)
    private void mineturn$freeze(Entity entity,CallbackInfo ci){if(BattleManager.freezeTick(entity))ci.cancel();}
    @Inject(method="tickPassenger",at=@At("HEAD"),cancellable=true)
    private void mineturn$freezePassenger(Entity vehicle,Entity passenger,CallbackInfo ci){if(BattleManager.freezeTick(passenger))ci.cancel();}
    @Inject(method="addEntity",at=@At("HEAD"),cancellable=true)
    private void mineturn$lightning(Entity entity,CallbackInfoReturnable<Boolean> cir){if(CombatChanneling.capture(entity))cir.setReturnValue(false);}
}
