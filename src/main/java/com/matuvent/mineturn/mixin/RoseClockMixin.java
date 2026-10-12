package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.BattleManager;
import com.matuvent.mineturn.battle.BattleStatus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WitherRoseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WitherRoseBlock.class)
public abstract class RoseClockMixin {
    @Inject(method="entityInside",at=@At("HEAD"),cancellable=true)
    private void mineturn$clock(BlockState state,Level level,BlockPos pos,Entity entity,CallbackInfo ci){
        if(BattleManager.locked(entity) && !BattleStatus.running())ci.cancel();
    }
}
