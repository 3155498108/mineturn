package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.platform.FabricAttributes;
import net.minecraft.world.entity.ai.attributes.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AttributeMap.class)
public abstract class FabricAttributeMapMixin {
    @Inject(method="<init>",at=@At("HEAD"))
    private static void mineturn$attributes(AttributeSupplier supplier,CallbackInfo ci){((FabricAttributes)supplier).mineturn$extendAttributes();}
}
