package com.matuvent.mineturn.mixin;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntityRenderer.class)
public interface LivingRendererAccess {
    @Invoker("addLayer") boolean mineturn$addLayer(RenderLayer<?,?> layer);
}
