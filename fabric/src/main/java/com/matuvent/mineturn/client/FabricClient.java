package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.platform.FabricPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.*;
import net.fabricmc.fabric.api.resource.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.concurrent.*;

public final class FabricClient implements ClientModInitializer {
    @Override public void onInitializeClient(){
        new BattleClient();FabricClientPayloads.initialize();
        KeyBindingHelper.registerKeyBinding(TrailsClient.SWITCH);KeyBindingHelper.registerKeyBinding(TrailsClient.BURST);
        KeyBindingHelper.registerKeyBinding(TrailsClient.PRIMARY);KeyBindingHelper.registerKeyBinding(TrailsClient.CONFIG);
        EntityRendererRegistry.register(MineTurn.BATTLE_BULLET.get(),BattleBulletRenderer::new);
        EntityRendererRegistry.register(MineTurn.BATTLE_DEVICE.get(),BattleDeviceRenderer::new);
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new IdentifiableResourceReloadListener(){
            private final ActionAnimationData animations=new ActionAnimationData();
            public ResourceLocation getFabricId(){return ResourceLocation.fromNamespaceAndPath(MineTurn.MODID,"animations");}
            public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier,ResourceManager resources,ProfilerFiller preparation,ProfilerFiller application,Executor background,Executor game){return animations.reload(barrier,resources,preparation,application,background,game);}
        });
        ClientTickEvents.END_CLIENT_TICK.register(client->{ProjectileAnimations.tick();TrailsEffects.tick();TrailsPerformances.tick();TrailsClient.tick();BattleClient.tick();});
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context->render(context,WorldRenderFrame.Stage.AFTER_TRANSLUCENT_BLOCKS));
        WorldRenderEvents.LAST.register(context->render(context,WorldRenderFrame.Stage.AFTER_PARTICLES));
    }
    private static void render(WorldRenderContext context,WorldRenderFrame.Stage stage){
        var frame=new WorldRenderFrame(stage,context.camera(),context.positionMatrix(),context.projectionMatrix(),context.tickCounter());
        ProjectileAnimations.render(frame);BattleClient.world(frame);TrailsWarnings.render(frame);TrailsImpacts.render(frame);TrailsEffects.render(frame);TrailsClient.world(frame);
    }
}
