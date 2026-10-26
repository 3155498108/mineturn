package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.data.CombatData;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ReloadableServerResources.class)
public abstract class FabricReloadResourcesMixin {
    @Inject(method="listeners",at=@At("RETURN"),cancellable=true)
    private void mineturn$data(CallbackInfoReturnable<java.util.List<PreparableReloadListener>> cir){
        var listeners=new java.util.ArrayList<>(cir.getReturnValue());listeners.add(new CombatData((ReloadableServerResources)(Object)this));cir.setReturnValue(java.util.List.copyOf(listeners));
    }
}
