package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Render-only anticipation, lunge, mace lift and impact recoil; server positions stay authoritative. */
@Mixin(LivingEntityRenderer.class)
public abstract class TrailsRootAnimationMixin {
    @Inject(method="setupRotations",at=@At("TAIL"))
    private void mineturn$actionRoot(LivingEntity e,PoseStack stack,float bob,float yaw,float partial,float scale,CallbackInfo ci) {
        var frame=TrailsPerformances.frame(e);
        if (frame!=null) {
            var pose=frame.pose();
            var offset=e.getLookAngle().multiply(1,0,1).normalize().scale(pose.advance()).add(0,pose.lift(),0);
            if(e.level().noCollision(e,e.getBoundingBox().move(offset))) stack.translate(0,pose.lift(),-pose.advance());
            stack.mulPose(Axis.XP.rotation(pose.lean()));
        }
        {
            float reaction=TrailsImpacts.reaction(e.getId());
            if (reaction>0) { stack.translate(0,0,.09*reaction); stack.mulPose(Axis.XP.rotation(-.08f*reaction)); }
        }
    }
}
