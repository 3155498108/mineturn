package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.client.TrailsWarnings;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public abstract class FabricRendererLayersMixin {
    @Shadow private java.util.Map<EntityType<?>,EntityRenderer<?>> renderers;
    @Shadow private java.util.Map<PlayerSkin.Model,EntityRenderer<? extends Player>> playerRenderers;
    @Inject(method="onResourceManagerReload",at=@At("TAIL"))
    private void mineturn$layers(ResourceManager resources,CallbackInfo ci){TrailsWarnings.layers(renderers.values());TrailsWarnings.layers(playerRenderers.values());}
}
