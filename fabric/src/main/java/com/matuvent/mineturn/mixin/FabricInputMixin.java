package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.BattleClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FabricInputMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void mineturn$input(boolean slow,float factor,CallbackInfo ci){var player=Minecraft.getInstance().player;if(player!=null)BattleClient.input(player,(Input)(Object)this);}
}
