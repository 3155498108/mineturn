package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.TrailsPerformances;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

/** A primary-weapon S-Break renders its weapon even if food is selected; inventory stays unchanged. */
@Mixin(ItemInHandLayer.class)
public abstract class TrailsHeldItemMixin {
    @ModifyVariable(method="renderArmWithItem",at=@At("HEAD"),argsOnly=true,ordinal=0)
    private ItemStack mineturn$burstWeapon(ItemStack current,LivingEntity e,ItemStack original,ItemDisplayContext context,HumanoidArm arm,PoseStack pose,MultiBufferSource buffers,int light) {
        return TrailsPerformances.visualItem(e,current,arm);
    }
}
