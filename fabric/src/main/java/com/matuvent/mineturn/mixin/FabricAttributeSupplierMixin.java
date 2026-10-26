package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.platform.FabricAttributes;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.*;
import org.spongepowered.asm.mixin.*;

@Mixin(AttributeSupplier.class)
public abstract class FabricAttributeSupplierMixin implements FabricAttributes {
    @Shadow @Final @Mutable private java.util.Map<Holder<Attribute>,AttributeInstance> instances;
    @Unique private boolean mineturn$extended;
    @Override public synchronized void mineturn$extendAttributes(){
        if(mineturn$extended)return;
        var merged=new java.util.HashMap<>(instances);
        for(var attribute:java.util.List.of(MineTurn.AGILITY,MineTurn.MAIN_ACTIONS,MineTurn.BONUS_ACTIONS))merged.computeIfAbsent(attribute,key->new AttributeInstance(key,value->{throw new UnsupportedOperationException("Default attribute cannot change");}));
        instances=java.util.Map.copyOf(merged);mineturn$extended=true;
    }
}
