package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.ActionAnimations;
import com.matuvent.mineturn.client.BattleClient;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class FabricCameraMixin {
    @Shadow private boolean detached;
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow protected abstract void move(float zoom, float dy, float dx);
    @Shadow private float getMaxZoom(float distance) { throw new AssertionError(); }
    @Shadow public abstract Vec3 getPosition();
    @Shadow public abstract float getYRot();
    @Shadow public abstract float getXRot();

    @Inject(method = "setup", at = @At("RETURN"))
    private void mineturn$orbit(net.minecraft.world.level.BlockGetter level, net.minecraft.world.entity.Entity entity,
                               boolean thirdPerson, boolean reverse, float partial, CallbackInfo ci) {
        if (!BattleClient.active()) return;
        detached = true;
        // Hand the live orbit camera to the performance player so a cut can push in from here.
        ActionAnimations.noteFreeCamera(new ActionAnimations.CameraPose(getPosition(), getYRot(), getXRot()));
        var pose = com.matuvent.mineturn.client.TrailsClient.active() ? com.matuvent.mineturn.client.TrailsCinematics.cameraPose() : ActionAnimations.cameraPose();
        if (pose != null) {
            // Fixed cut: absolute world position plus a locked look direction.
            setPosition(pose.position());
            setRotation(pose.yaw(), pose.pitch());
            return;
        }
        setRotation(BattleClient.yaw, BattleClient.pitch);
        setPosition(com.matuvent.mineturn.client.TrailsClient.active() ? com.matuvent.mineturn.client.TrailsClient.cameraFocus(partial) : BattleClient.focus);
        move(-Math.max(0, getMaxZoom((float) BattleClient.distance) - 0.15f), 0, 0);
    }
}
