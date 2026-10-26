package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.TrailsClient;
import net.minecraft.client.KeyboardHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class TrailsSwitchInputMixin {
    @Inject(method="keyPress", at=@At("HEAD"))
    private void mineturn$release(long window, int key, int scan, int action, int modifiers, CallbackInfo ci) {
        if (action == GLFW.GLFW_RELEASE && TrailsClient.SWITCH.matches(key, scan)) TrailsClient.switchReleased();
    }
}
