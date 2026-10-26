package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(Mob.class)
public abstract class TrailsCompanionMixin {
    @Shadow @Final protected GoalSelector goalSelector;
    @Inject(method="<init>", at=@At("TAIL"))
    private void mineturn$followGoal(CallbackInfo ci) {
        goalSelector.addGoal(5, new CompanionFollowGoal((Mob)(Object)this));
    }
    @Inject(method="interact", at=@At("HEAD"), cancellable=true)
    private void mineturn$recall(Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        var mob = (Mob)(Object)this;
        if (user.isShiftKeyDown() && user.getItemInHand(hand).isEmpty() && user instanceof ServerPlayer player
                && player.getUUID().equals(TrailsParties.owner(mob))) {
            ci.setReturnValue(TrailsCompanions.capture(player, mob) ? InteractionResult.CONSUME : InteractionResult.FAIL);
        }
    }
}
