package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.TrailsPerformances;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Layers authored weapon poses over vanilla models, restoring the shared model before its next use. */
@Mixin(HumanoidModel.class)
public abstract class TrailsHumanoidAnimationMixin {
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;
    @Unique private float[] mineturn$saved;
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("HEAD"))
    private void mineturn$restore(LivingEntity entity,float a,float b,float c,float d,float e,CallbackInfo ci) {
        if (mineturn$saved==null) return;
        int i=0; for (var part : new ModelPart[]{body,rightArm,leftArm}) { part.xRot=mineturn$saved[i++]; part.yRot=mineturn$saved[i++]; part.zRot=mineturn$saved[i++]; }
        mineturn$saved=null;
    }
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void mineturn$weaponPose(LivingEntity entity,float a,float b,float c,float d,float e,CallbackInfo ci) {
        var frame=TrailsPerformances.frame(entity); if (frame==null) return;
        mineturn$saved=new float[9]; int i=0;
        for (var part : new ModelPart[]{body,rightArm,leftArm}) { mineturn$saved[i++]=part.xRot; mineturn$saved[i++]=part.yRot; mineturn$saved[i++]=part.zRot; }
        var pose=frame.pose(); body.yRot+=pose.twist(); body.xRot+=pose.lean();
        if (!pose.arms()) return;
        var main=entity.getMainArm()==HumanoidArm.LEFT ? leftArm : rightArm;
        var off=main==rightArm ? leftArm : rightArm;
        main.xRot=pose.arm(); main.zRot=pose.armSide()*(main==rightArm?1:-1); main.yRot=-pose.twist();
        if (frame.style().contains("mace") || frame.style().contains("axe") || frame.style().equals("support")) {
            off.xRot=pose.arm()*.85f; off.zRot=-main.zRot; off.yRot=.12f;
        }
        TrailsPerformances.renderedPoses++;
    }
}
