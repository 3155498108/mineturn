package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import com.matuvent.mineturn.battle.TrailsArea;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Critical telegraphs follow authoritative roster state, never a short particle timer or an effects toggle. */
public final class TrailsWarnings {
    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("mineturn","textures/effect/charge.png");
    public static long goldenModels, dangerFrames;
    public static TrailsNetwork.Threat threat(LivingEntity entity) {
        if(!TrailsClient.active() || !entity.isAlive())return null;
        return TrailsClient.fighters.stream().filter(f->f.id()==entity.getId()).map(TrailsNetwork.Fighter::threat).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }
    public static boolean gold(LivingEntity entity) {
        var threat=threat(entity);var frame=TrailsPerformances.frame(entity);
        return threat!=null && threat.golden() || frame!=null && frame.style().startsWith("boss_");
    }
    public static RenderType glow(float time){return RenderType.energySwirl(TEXTURE,time*.007f,time*.004f);}
    public static int color(float time){return ((int)(135+45*Math.sin(time*.15))<<24)|0xffd23c;}
    @SuppressWarnings({"rawtypes","unchecked"})
    static void layers(EntityRenderersEvent.AddLayers event) {
        for(var type:event.getEntityTypes())if(event.getRenderer(type) instanceof LivingEntityRenderer renderer)renderer.addLayer(new GoldLayer(renderer));
    }
    private static final class GoldLayer<T extends LivingEntity,M extends EntityModel<T>> extends RenderLayer<T,M> {
        GoldLayer(RenderLayerParent<T,M> parent){super(parent);}
        @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,T entity,float limb,float amount,float partial,float age,float yaw,float pitch) {
            if(!gold(entity))return;
            poses.pushPose();poses.scale(1.015f,1.015f,1.015f);
            getParentModel().renderToBuffer(poses,buffers.getBuffer(glow(age)),LightTexture.FULL_BRIGHT,OverlayTexture.NO_OVERLAY,color(age));
            poses.popPose();goldenModels++;
        }
    }
    static void render(RenderLevelStageEvent event) {
        if(!TrailsClient.active() || event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();var poses=new PoseStack();poses.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.lines());
        for(var fighter:TrailsClient.fighters){
            var warning=fighter.threat();if(warning==null || !(mc.level.getEntity(fighter.id()) instanceof LivingEntity entity) || !entity.isAlive())continue;
            var area=warning.area();var origin=warning.origin();var aim=warning.aim();
            float green=warning.golden()?.8f:.12f;double pulse=.7+.2*Math.sin(System.nanoTime()/180_000_000.);
            Vec3 forward=aim.horizontalDistanceSqr()>1e-8?aim.multiply(1,0,1).normalize():new Vec3(0,0,1),side=new Vec3(forward.z,0,-forward.x);
            for(int i=0;i<100;i++){
                double t=i/100.;Vec3 point;
                if(area.kind()==TrailsArea.Kind.RECTANGLE){
                    double d=t*4;double along=d<1?d:d<2?1:d<3?3-d:0;double across=d<1?-1:d<2?(d-1)*2-1:d<3?1:1-(d-3)*2;
                    point=origin.add(forward.scale(along*area.radius())).add(side.scale(across*area.width()/2));
                }else if(area.kind()==TrailsArea.Kind.SECTOR){
                    double half=Math.toRadians(area.width()/2),a=(t-.5)*2*half;
                    point=origin.add(forward.scale(Math.cos(a)*area.radius())).add(side.scale(Math.sin(a)*area.radius()));
                    if(i%2==0)for(int edge:new int[]{-1,1})mark(poses,out,origin.add(forward.scale(Math.cos(half)*area.radius()*t)).add(side.scale(edge*Math.sin(half)*area.radius()*t)),green,.65f);
                }else {double angle=t*Math.PI*2;point=origin.add(area.kind()==TrailsArea.Kind.DISK?aim:Vec3.ZERO).add(Math.cos(angle)*Math.max(.7,area.radius()),0,Math.sin(angle)*Math.max(.7,area.radius()));}
                mark(poses,out,point,green,(float)pulse);
            }
            // Rising halo remains visible on flying bosses and when decorative effects are disabled.
            double radius=Math.max(.65,entity.getBbWidth()*.6),time=System.nanoTime()/1_000_000_000.;
            for(int i=0;i<40;i++){
                double angle=i*Math.PI/20+time;var p=entity.position().add(Math.cos(angle)*radius,.15+((i%5)/5.+time*.45)%1*Math.min(5,entity.getBbHeight()),Math.sin(angle)*radius);
                LevelRenderer.renderLineBox(poses,out,new AABB(p.x-.035,p.y-.12,p.z-.035,p.x+.035,p.y+.12,p.z+.035),1,green,.12f,.9f);
            }
            dangerFrames++;
        }
        buffers.endBatch(RenderType.lines());
    }
    private static void mark(PoseStack poses,com.mojang.blaze3d.vertex.VertexConsumer out,Vec3 p,float green,float alpha){
        LevelRenderer.renderLineBox(poses,out,new AABB(p.x-.045,p.y+.06,p.z-.045,p.x+.045,p.y+.12,p.z+.045),1,green,.1f,alpha);
    }
    private TrailsWarnings(){}
}
