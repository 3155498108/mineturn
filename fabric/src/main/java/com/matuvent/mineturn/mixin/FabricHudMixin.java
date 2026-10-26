package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.*;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Gui.class)
public abstract class FabricHudMixin {
    @WrapMethod(method="render")
    private void mineturn$hud(GuiGraphics graphics,DeltaTracker ticks,Operation<Void> original){if(!BattleClient.suppressHud())original.call(graphics,ticks);TrailsClient.hud(graphics);}
}
