package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.*;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Explosion.class)
public abstract class FabricExplosionMixin {
    @Inject(method="explode",at=@At("TAIL"))
    private void mineturn$playerImpulse(CallbackInfo ci){var explosion=(Explosion)(Object)this;var source=explosion.getIndirectSourceEntity();explosion.getHitPlayers().keySet().removeIf(player->BattleManager.locked(player)||source!=null&&BattleManager.locked(source));}
    @WrapOperation(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private java.util.List<Entity> mineturn$targets(Level level,Entity source,AABB area,Operation<java.util.List<Entity>> original){var targets=new java.util.ArrayList<>(original.call(level,source,area));BattleExplosion.filter((Explosion)(Object)this,targets);return targets;}
    @WrapOperation(method="explode",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void mineturn$push(Entity entity,Vec3 velocity,Operation<Void> original){var source=((Explosion)(Object)this).getIndirectSourceEntity();if(!BattleManager.locked(entity) && (source==null || !BattleManager.locked(source)))original.call(entity,velocity);}
}
