package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.platform.RuntimePlatform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** UUID ownership, CP and primary weapons persist through the vanilla entity save path. */
@Mixin(Entity.class)
public abstract class FabricEntityDataMixin implements RuntimePlatform.PersistentEntity {
    @Unique private CompoundTag mineturn$state=new CompoundTag();
    @Override public CompoundTag mineturn$data(){return mineturn$state;}
    @Inject(method="saveWithoutId",at=@At("RETURN"))
    private void mineturn$save(CompoundTag tag,CallbackInfoReturnable<CompoundTag> ci){if(!mineturn$state.isEmpty())tag.put("mineturn:state",mineturn$state.copy());}
    @Inject(method="load",at=@At("HEAD"))
    private void mineturn$load(CompoundTag tag,CallbackInfo ci){mineturn$state=tag.getCompound("mineturn:state").copy();}
    @Inject(method="load",at=@At("TAIL"))
    private void mineturn$orphan(CompoundTag tag,CallbackInfo ci){var entity=(Entity)(Object)this;if(!entity.level().isClientSide && com.matuvent.mineturn.battle.BattleManager.temporarySummon(entity))entity.discard();}
}
