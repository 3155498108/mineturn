package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.compat.PartialTick;
import com.matuvent.mineturn.platform.ForgePayloads;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only registrations; gameplay and render implementations remain shared. */
@Mod.EventBusSubscriber(modid = MineTurn.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClient {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            new BattleClient();
            ForgePayloads.setClientPlayer(() -> Minecraft.getInstance().player);
            // The isolated smoke run must continue when another desktop window has focus.
            if (Boolean.getBoolean("mineturn.encounterSmoke")) Minecraft.getInstance().options.pauseOnLostFocus = false;
        });
    }
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) {
        event.register(TrailsClient.SWITCH); event.register(TrailsClient.BURST);
        event.register(TrailsClient.PRIMARY); event.register(TrailsClient.CONFIG);
    }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MineTurn.BATTLE_BULLET.get(), BattleBulletRenderer::new);
        event.registerEntityRenderer(MineTurn.BATTLE_DEVICE.get(), BattleDeviceRenderer::new);
    }
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event) { event.registerReloadListener(new ActionAnimationData()); }

    @Mod.EventBusSubscriber(modid = MineTurn.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class GameEvents {
        @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            ProjectileAnimations.tick(); TrailsEffects.tick(); TrailsPerformances.tick(); TrailsClient.tick(); BattleClient.tick();
        }
        @SubscribeEvent public static void render(RenderLevelStageEvent event) {
            WorldRenderFrame.Stage stage;
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) stage = WorldRenderFrame.Stage.AFTER_TRANSLUCENT_BLOCKS;
            else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) stage = WorldRenderFrame.Stage.AFTER_PARTICLES;
            else return;
            var frame = new WorldRenderFrame(stage, event.getCamera(), event.getPoseStack().last().pose(), event.getProjectionMatrix(), new PartialTick(event.getPartialTick()));
            ProjectileAnimations.render(frame); BattleClient.world(frame); TrailsWarnings.render(frame); TrailsImpacts.render(frame); TrailsEffects.render(frame); TrailsClient.world(frame);
        }
    }
}
