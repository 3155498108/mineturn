package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.TrailsParties;
import com.matuvent.mineturn.battle.TrailsCompanions;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NameTagItem.class)
public abstract class TrailsNameTagMixin {
    @Inject(method="interactLivingEntity",at=@At("HEAD"),cancellable=true)
    private void mineturn$checkOwner(ItemStack stack,Player user,LivingEntity target,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){
        if(user instanceof ServerPlayer player && stack.has(DataComponents.CUSTOM_NAME) && target.isAlive() && !TrailsParties.canName(player,target)){
            player.displayClientMessage(Component.literal("战斗中不能改名，也不能接管其他玩家的生物。"),true);ci.setReturnValue(InteractionResult.FAIL);
        }
        if (user instanceof ServerPlayer player && stack.has(DataComponents.CUSTOM_NAME) && TrailsCompanions.friendly(player, target)) {
            if (!TrailsCompanions.canStore(player, target) || !TrailsCompanions.hasSpace(player, stack)) {
                player.displayClientMessage(Component.literal("请先腾出背包格子，并解除友军的骑乘、乘客和拴绳后再收纳。"), true);
                ci.setReturnValue(InteractionResult.FAIL);
            }
        }
    }
    @Inject(method="interactLivingEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;setCustomName(Lnet/minecraft/network/chat/Component;)V",shift=At.Shift.AFTER))
    private void mineturn$register(ItemStack stack,Player user,LivingEntity target,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){
        if(user instanceof ServerPlayer player)TrailsParties.named(player,target);
    }
    @Inject(method="interactLivingEntity", at=@At("RETURN"))
    private void mineturn$store(ItemStack stack, Player user, LivingEntity target, InteractionHand hand, CallbackInfoReturnable<InteractionResult> ci) {
        if (ci.getReturnValue().consumesAction() && user instanceof ServerPlayer player && target instanceof Mob mob)
            TrailsCompanions.capture(player, mob);
    }
}
