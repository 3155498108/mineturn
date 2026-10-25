package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** World-anchored hit numbers projected after the scene, then drawn above the battle UI. */
public final class TrailsImpacts {
    private record Number(TrailsNetwork.Impact packet,long started) {}
    private static final ArrayDeque<Number> NUMBERS=new ArrayDeque<>();
    private static long sequence=-1;
    private static Matrix4f worldView,projection;
    private static Vec3 camera;
    private static net.minecraft.client.multiplayer.ClientLevel lingerWorld;
    public static long received,renderedNumbers,whiteNumbers,redCriticalNumbers;
    public static TrailsNetwork.Impact lastReceived;
    public static String format(float value){return value==Math.rint(value)?String.format(Locale.ROOT,"%.0f",value):String.format(Locale.ROOT,value<.1?"%.2f":"%.1f",value);}
    public static int color(boolean critical){return critical?0xffff3030:0xffffffff;}
    public static float size(boolean critical){return critical?1.84f:1.15f;}
    static void receive(TrailsNetwork.Impact p){
        if(!TrailsClient.active() || !TrailsClient.view.battle().equals(p.battle()) || p.sequence()<=sequence || !Float.isFinite(p.amount()) || p.amount()<0)return;
        sequence=p.sequence();received++;lastReceived=p;TrailsCinematics.impact();
        if(NUMBERS.size()>=96)NUMBERS.removeFirst();NUMBERS.addLast(new Number(p,System.nanoTime()));
    }
    public static void reset(){NUMBERS.clear();sequence=-1;lastReceived=null;lingerWorld=null;worldView=null;projection=null;camera=null;}
    /** Keep only the already delivered final hit through natural battle completion; manual exit still clears. */
    static Runnable afterClose(String reason){
        if(!reason.equals("战斗结束。") || NUMBERS.isEmpty())return ()->{};
        var saved=List.copyOf(NUMBERS);var packet=lastReceived;var level=Minecraft.getInstance().level;
        return ()->{NUMBERS.addAll(saved);lastReceived=packet;lingerWorld=level;};
    }
    public static float reaction(int target){
        long now=System.nanoTime();float strength=0;
        for(var n:NUMBERS)if(n.packet.target()==target && n.packet.amount()>0 && now-n.started<230_000_000L)
            strength=Math.max(strength,(float)Math.sin(Math.PI*(now-n.started)/230_000_000.));
        return strength;
    }
    static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        long now=System.nanoTime();while(!NUMBERS.isEmpty() && now-NUMBERS.peekFirst().started>1_200_000_000L)NUMBERS.removeFirst();
        var level=Minecraft.getInstance().level;
        if(!TrailsClient.active() && (lingerWorld==null || lingerWorld!=level || NUMBERS.isEmpty())){reset();return;}
        worldView=new Matrix4f(event.getModelViewMatrix());projection=new Matrix4f(event.getProjectionMatrix());camera=event.getCamera().getPosition();
    }
    static void hud(GuiGraphics g){
        var mc=Minecraft.getInstance();
        if(!TrailsPresentationOptions.numbers || worldView==null || projection==null || camera==null || (!TrailsClient.active() && (lingerWorld!=mc.level || mc.screen!=null)))return;
        int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();long now=System.nanoTime();
        for(var n:NUMBERS){
            double t=(now-n.started)/1_200_000_000.;if(t<0 || t>.99)continue;
            Vec3 point=n.packet.position().subtract(camera);
            var clip=new Vector4f((float)point.x,(float)point.y,(float)point.z,1);worldView.transform(clip);projection.transform(clip);
            if(clip.w<=.01f || Math.abs(clip.x/clip.w)>1.15f || Math.abs(clip.y/clip.w)>1.3f)continue;
            boolean critical=n.packet.critical() && n.packet.amount()>.001;
            float x=(clip.x/clip.w+1)*w/2+(n.packet.sequence()%5-2)*5;
            float y=(float)Math.max(58,Math.clamp((1-clip.y/clip.w)*h/2,85,h-60)-28*t-5*Math.sin(Math.PI*t));
            float scale=size(critical)*(float)(1+.25*Math.sin(Math.PI*Math.min(1,t/.22)));
            int rgba=((int)(255*Math.min(1,(1-t)*4))<<24)|(color(critical)&0xffffff);
            String text=n.packet.amount()<=.001?"BLOCK":format(n.packet.amount());
            if(critical)text="暴击 "+text;
            g.pose().pushPose();g.pose().translate(x,y,500);g.pose().scale(scale,scale,1);
            g.drawCenteredString(mc.font,text,0,0,rgba);g.pose().popPose();
            renderedNumbers++;if(critical)redCriticalNumbers++;else whiteNumbers++;
        }
    }
    private TrailsImpacts(){}
}
