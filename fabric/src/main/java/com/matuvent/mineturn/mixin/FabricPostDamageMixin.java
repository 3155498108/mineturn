package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.BattleManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

/** Both classes implement this method independently in vanilla 1.21.1. */
@Mixin({LivingEntity.class,Player.class})
public abstract class FabricPostDamageMixin {
    @WrapMethod(method="actuallyHurt")
    private void mineturn$postDamage(DamageSource source,float amount,Operation<Void> original){
        var entity=(LivingEntity)(Object)this;float health=entity.getHealth();
        original.call(source,amount);
        float lost=Math.max(0,health-entity.getHealth());
        if(lost>0)BattleManager.damaged(entity,source,lost);
    }
}
