package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.client.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;

/** NeoForge registrations; all actual UI and rendering remain in the shared client package. */
@Mod(value=MineTurn.MODID,dist=Dist.CLIENT)
public final class NeoForgeClient {
    public NeoForgeClient(IEventBus bus){
        new BattleClient();
        bus.addListener((RegisterKeyMappingsEvent e)->{e.register(TrailsClient.SWITCH);e.register(TrailsClient.BURST);e.register(TrailsClient.PRIMARY);e.register(TrailsClient.CONFIG);});
        net.neoforged.fml.ModList.get().getModContainerById(MineTurn.MODID).orElseThrow().registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,(container,parent)->new TrailsConfigScreen(parent));
        bus.addListener((EntityRenderersEvent.RegisterRenderers e)->{e.registerEntityRenderer(MineTurn.BATTLE_BULLET.get(),BattleBulletRenderer::new);e.registerEntityRenderer(MineTurn.BATTLE_DEVICE.get(),BattleDeviceRenderer::new);});
        bus.addListener((RegisterClientReloadListenersEvent e)->e.registerReloadListener(new ActionAnimationData()));
        bus.addListener((EntityRenderersEvent.AddLayers e)->TrailsWarnings.layers(e.getEntityTypes().stream().map(e::getRenderer).toList()));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->{ProjectileAnimations.tick();TrailsEffects.tick();TrailsPerformances.tick();TrailsClient.tick();BattleClient.tick();});
        NeoForge.EVENT_BUS.addListener((MovementInputUpdateEvent e)->BattleClient.input(e.getEntity(),e.getInput()));
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Pre e)->{if(BattleClient.suppressHud())e.setCanceled(true);});
        NeoForge.EVENT_BUS.addListener((RenderGuiEvent.Post e)->TrailsClient.hud(e.getGuiGraphics()));
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e)->{
            WorldRenderFrame.Stage stage;
            if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)stage=WorldRenderFrame.Stage.AFTER_TRANSLUCENT_BLOCKS;
            else if(e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES)stage=WorldRenderFrame.Stage.AFTER_PARTICLES;
            else return;
            var frame=new WorldRenderFrame(stage,e.getCamera(),e.getModelViewMatrix(),e.getProjectionMatrix(),e.getPartialTick());
            ProjectileAnimations.render(frame);BattleClient.world(frame);TrailsWarnings.render(frame);TrailsImpacts.render(frame);TrailsEffects.render(frame);TrailsClient.world(frame);
        });
        if(!System.getProperty("mineturn.multiplayerSmoke","").isEmpty())NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e)->com.matuvent.mineturn.battle.TrailsMultiplayerScenario.tick(e.getServer()));
    }
}
