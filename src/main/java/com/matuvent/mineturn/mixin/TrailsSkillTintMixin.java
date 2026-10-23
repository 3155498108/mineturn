package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.TrailsPerformances;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla red overlay without modifying hurtTime, health or animation timing. */
@Mixin(LivingEntityRenderer.class)
public abstract class TrailsSkillTintMixin {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void mineturn$skillTint(LivingEntity entity, float white, CallbackInfoReturnable<Integer> result) {
        if (TrailsPerformances.red(entity)) result.setReturnValue(OverlayTexture.pack(OverlayTexture.u(0), OverlayTexture.v(true)));
    }
}
