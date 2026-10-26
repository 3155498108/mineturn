package com.matuvent.mineturn.battle;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class CompanionTokenItem extends Item {
    public CompanionTokenItem() { super(new Properties().stacksTo(1)); }
    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        var pos = context.getClickedPos();
        if (!context.getLevel().getBlockState(pos).getCollisionShape(context.getLevel(), pos).isEmpty()) pos = pos.relative(context.getClickedFace());
        return TrailsCompanions.release(player, context.getHand(), Vec3.atBottomCenterOf(pos)) != null
                ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.mineturn.companion_token.release").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.mineturn.companion_token.recall").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.mineturn.companion_token.owner").withStyle(ChatFormatting.DARK_GREEN));
    }
    @Override public boolean isFoil(ItemStack stack) { return true; }
}
