package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.BattleManager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.ConduitBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(ConduitBlockEntity.class)
public abstract class ConduitAuraBoundaryMixin {
    @Redirect(method="applyEffects",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/player/Player;addEffect(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private static boolean mineturn$refresh(Player player,MobEffectInstance effect){return !BattleManager.locked(player)&&player.addEffect(effect);}
}
