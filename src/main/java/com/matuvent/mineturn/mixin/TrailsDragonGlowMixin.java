package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.TrailsWarnings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dragon has a special renderer rather than LivingEntityRenderer; reuse its already posed native model. */
@Mixin(EnderDragonRenderer.class)
public abstract class TrailsDragonGlowMixin {
    @Shadow @Final private EnderDragonRenderer.DragonModel model;
    @Inject(method="render(Lnet/minecraft/world/entity/boss/enderdragon/EnderDragon;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",ordinal=1))
    private void mineturn$gold(EnderDragon dragon,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light,CallbackInfo ci){
        if(!TrailsWarnings.gold(dragon))return;
        model.renderToBuffer(poses,buffers.getBuffer(TrailsWarnings.glow(dragon.tickCount+partial)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,TrailsWarnings.color(dragon.tickCount+partial));
        TrailsWarnings.goldenModels++;
    }
}
