package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Additive weapon trails and vanilla particles. No world entities, explosions, fire or damage. */
public final class TrailsEffects {
    private static final int MAX_EFFECTS=24, MAX_PARTICLES_PER_TICK=48, MAX_PARTICLES_PER_EFFECT=240;
    private static final ArrayDeque<Effect> ACTIVE=new ArrayDeque<>();
    private static final class Effect {
        final TrailsNetwork.Performance packet; final Vec3 origin, forward; final long start=System.nanoTime();
        final boolean burst, draw; final int ticks, rgb; int emitted;
        Effect(TrailsNetwork.Performance p,LivingEntity e) {
            packet=p;origin=e.position();Vec3 delta=p.aim().subtract(origin).multiply(1,0,1);
            forward=delta.lengthSqr()>1e-6?delta.normalize():new Vec3(0,0,1);
            burst=p.style().startsWith("burst_");draw=p.animation().equals("draw");ticks=draw?p.ticks():burst?28:20;
            rgb=p.red()?0xff3458:switch(style()) { case "axe"->0xffb749;case "bow"->0x7affb0;case "crossbow"->0xff7763;
                case "mace"->0xb98aff;case "trident"->0x58cfff;case "support"->0x75ffb8;default->0x82eaff;};
        }
        String style(){return burst?packet.style().substring(6):packet.style();}
        double age(){return Math.clamp((System.nanoTime()-start)/(ticks*50_000_000.),0,1);}
    }
    public static long renderedEffects, spawnedParticles;
    static int count(){return ACTIVE.size();}
    static void receive(TrailsNetwork.Performance p) {
        var mc=Minecraft.getInstance();
        if (!TrailsPresentationOptions.effects || p.animation().equals("recover") || mc.level==null || !(mc.level.getEntity(p.actor()) instanceof LivingEntity e)) return;
        if(ACTIVE.size()>=MAX_EFFECTS)ACTIVE.removeFirst();ACTIVE.addLast(new Effect(p,e));
    }
    public static void reset(){ACTIVE.clear();}
    static void tick() {
        if(!TrailsClient.active() || !TrailsPresentationOptions.effects){reset();return;}
        long now=System.nanoTime(); ACTIVE.removeIf(f->now-f.start>f.ticks*50_000_000L);
        var level=Minecraft.getInstance().level;if(level==null){reset();return;}
        int budget=MAX_PARTICLES_PER_TICK;
        for(var f:ACTIVE){
            if(budget<=0)break;
            int count=Math.min(budget,Math.min(f.burst?8:3,MAX_PARTICLES_PER_EFFECT-f.emitted));
            for(int i=0;i<count;i++){
                double a=(f.age()*7+i*.75)*Math.PI;
                double radius=f.draw?.5+f.age()*.6:f.burst?1+f.age()*4:.7+f.age()*1.3;
                Vec3 at=f.origin.add(Math.cos(a)*radius,.25+f.age()*(f.draw?1.8:.7),Math.sin(a)*radius);
                var particle=f.packet.red()?ParticleTypes.FLAME:switch(f.style()){
                    case "trident"->i%2==0?ParticleTypes.SPLASH:ParticleTypes.ELECTRIC_SPARK;
                    case "mace"->i==0?ParticleTypes.CLOUD:ParticleTypes.ELECTRIC_SPARK;
                    case "breeze"->f.emitted%8==0?ParticleTypes.GUST:ParticleTypes.CLOUD;
                    case "support"->ParticleTypes.HAPPY_VILLAGER;
                    case "axe"->ParticleTypes.CRIT;
                    default->f.burst?ParticleTypes.END_ROD:ParticleTypes.ENCHANTED_HIT;
                };
                level.addParticle(particle,at.x,at.y,at.z,Math.cos(a)*.025,.025,Math.sin(a)*.025);
                f.emitted++;spawnedParticles++;budget--;
            }
        }
    }
    static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || !TrailsClient.active() || !TrailsPresentationOptions.effects || ACTIVE.isEmpty())return;
        var mc=Minecraft.getInstance();var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(RenderType.lightning());
        Vec3 camera=event.getCamera().getPosition();
        for(var f:ACTIVE){
            double t=f.age(),fade=f.draw?.25+.25*t:Math.sin(Math.PI*t)*.8;
            if(fade<.01)continue;
            Vec3 center=f.origin.subtract(camera),side=new Vec3(f.forward.z,0,-f.forward.x),up=new Vec3(0,1,0);
            if(f.draw){
                ring(out,center.add(0,.08,0),.6+t*.7,.04,f.rgb,(float)fade);
                if(f.burst)ring(out,center.add(0,.09,0),1+t*.8,.06,0xffda89,(float)fade);
            }else switch(f.style()){
                case "sword","axe","melee","charge","iron_golem","ravager"->{
                    Vec3 c=center.add(f.forward.scale(.85)).add(0,1.05,0);
                    double r=f.burst?2+t*5:1.2+t*1.6;
                    Vec3 vertical=f.style().equals("axe")?up:up.scale(.5).add(f.forward.scale(.45));
                    arc(out,c,side,vertical,r,-Math.PI*.85,Math.PI*.55,.22,f.rgb,(float)fade);
                    arc(out,c,side,vertical,r*.98,-Math.PI*.85,Math.PI*.55,.045,0xf4ffff,(float)fade);
                    if(f.burst)arc(out,c,side,vertical.scale(-1),r*.8,-Math.PI*.8,Math.PI*.7,.12,0xffeab4,(float)fade);
                }
                case "mace","breeze","creeper","support","slime","magma_cube"->{
                    Vec3 c=f.style().equals("mace")?f.packet.aim().add(0,-.95,0).subtract(camera):center.add(0,.08,0);
                    double r=(f.burst?7:f.style().equals("mace")?5:3)*t+.2;
                    ring(out,c,r,.12,f.rgb,(float)fade);ring(out,c.add(0,.04,0),r*.8,.045,0xf6f0ff,(float)fade);
                    if(f.burst)ring(out,c.add(0,.08,0),r*.55,.17,0xffdc96,(float)fade);
                }
                case "bow","crossbow","trident"->{
                    Vec3 from=center.add(0,1.25,0),to=f.packet.aim().subtract(camera);
                    beam(out,from,to,.10*(1-t),f.rgb,(float)fade);
                    beam(out,from,to,.025,0xf6ffff,(float)fade);
                    if(f.burst || f.style().equals("crossbow"))for(int lane:new int[]{-1,1})beam(out,from.add(side.scale(lane*.35)),to.add(side.scale(lane*(f.burst?1.4:.7))),.045,f.rgb,(float)fade);
                }
                default->ring(out,center.add(0,.15,0),.4+t*2,.05,f.rgb,(float)fade);
            }
            renderedEffects++;
        }
        buffers.endBatch(RenderType.lightning());
    }
    private static void ring(VertexConsumer out,Vec3 c,double r,double width,int rgb,float alpha){arc(out,c,new Vec3(1,0,0),new Vec3(0,0,1),r,0,Math.PI*2,width,rgb,alpha);}
    private static void arc(VertexConsumer out,Vec3 c,Vec3 x,Vec3 y,double r,double begin,double end,double width,int rgb,float alpha){
        int segments=40;
        for(int i=0;i<segments;i++){
            double a=begin+(end-begin)*i/segments,b=begin+(end-begin)*(i+1)/segments;
            Vec3 da=x.scale(Math.cos(a)).add(y.scale(Math.sin(a))),db=x.scale(Math.cos(b)).add(y.scale(Math.sin(b)));
            quad(out,c.add(da.scale(r-width)),c.add(da.scale(r+width)),c.add(db.scale(r+width)),c.add(db.scale(r-width)),rgb,alpha);
        }
    }
    private static void beam(VertexConsumer out,Vec3 a,Vec3 b,double width,int rgb,float alpha){
        Vec3 axis=b.subtract(a).cross(new Vec3(0,1,0)).normalize().scale(width);
        quad(out,a.add(axis),a.subtract(axis),b.subtract(axis),b.add(axis),rgb,alpha);
        axis=new Vec3(0,width,0);quad(out,a.add(axis),a.subtract(axis),b.subtract(axis),b.add(axis),rgb,alpha);
    }
    private static void quad(VertexConsumer out,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,float alpha){
        for(var p:new Vec3[]{a,b,c,d,d,c,b,a})out.addVertex((float)p.x,(float)p.y,(float)p.z).setColor((rgb>>16)&255,(rgb>>8)&255,rgb&255,(int)(Math.clamp(alpha,0,1)*255));
    }
    private TrailsEffects(){}
}
