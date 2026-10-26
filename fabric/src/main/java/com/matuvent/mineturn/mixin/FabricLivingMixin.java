package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(LivingEntity.class)
public abstract class FabricLivingMixin {
    @WrapMethod(method="hurt")
    private boolean mineturn$damage(DamageSource source,float amount,Operation<Boolean> original){
        var entity=(LivingEntity)(Object)this;
        amount=TrailsCombat.guardDamage(entity,amount);
        if(BattleManager.cancelIncoming(entity,source,amount))return false;
        try{return original.call(source,amount);}finally{BattleManager.finishShieldHit(entity);}
    }
    @Inject(method="hurtCurrentlyUsedShield",at=@At("HEAD"))
    private void mineturn$shield(float amount,CallbackInfo ci){BattleManager.shieldBlocked((LivingEntity)(Object)this,true,amount);}
    @Inject(method="tickEffects",at=@At("HEAD"),cancellable=true)
    private void mineturn$effects(CallbackInfo ci){var e=(Entity)(Object)this;if((BattleManager.locked(e)||BattleManager.clientStatusLocked.test(e))&&!BattleStatus.running())ci.cancel();}
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void mineturn$travel(CallbackInfo ci){var e=(LivingEntity)(Object)this;if(BattleManager.locked(e)){e.walkAnimation.update(0,.4f);ci.cancel();}}
    @Inject(method="knockback",at=@At("HEAD"),cancellable=true)
    private void mineturn$knockback(double force,double x,double z,CallbackInfo ci){var e=(Entity)(Object)this;if(BattleManager.locked(e)||BattleManager.ridingCarrier(e))ci.cancel();}
    @Inject(method="addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",at=@At("HEAD"),cancellable=true)
    private void mineturn$externalEffect(MobEffectInstance effect,Entity source,CallbackInfoReturnable<Boolean> cir){var e=(LivingEntity)(Object)this;if(!e.level().isClientSide && !BattleManager.authorized() && source!=null && !BattleManager.allowsRealtimeEffect(source,e))cir.setReturnValue(false);}
    @Inject(method="forceAddEffect",at=@At("HEAD"),cancellable=true)
    private void mineturn$forceEffect(MobEffectInstance effect,Entity source,CallbackInfo ci){var e=(LivingEntity)(Object)this;if(!e.level().isClientSide && !BattleManager.authorized() && source!=null && !BattleManager.allowsRealtimeEffect(source,e))ci.cancel();}
    @Inject(method="dropAllDeathLoot",at=@At("HEAD"),cancellable=true)
    private void mineturn$drops(CallbackInfo ci){if(BattleManager.temporarySummon((Entity)(Object)this))ci.cancel();}
    @Inject(method="dropExperience",at=@At("HEAD"),cancellable=true)
    private void mineturn$experience(CallbackInfo ci){if(BattleManager.temporarySummon((Entity)(Object)this))ci.cancel();}
}
