package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Bounded floating post-mitigation numbers and cosmetic hit reactions, never gameplay knockback. */
public final class TrailsImpacts {
    private record Number(TrailsNetwork.Impact packet, long started) {}
    private static final ArrayDeque<Number> NUMBERS = new ArrayDeque<>();
    private static long sequence = -1;
    public static long received, renderedNumbers;
    static void receive(TrailsNetwork.Impact p) {
        if (!TrailsClient.active() || !TrailsClient.view.battle().equals(p.battle()) || p.sequence() <= sequence || !Float.isFinite(p.amount()) || p.amount() < 0) return;
        sequence = p.sequence(); received++; TrailsCinematics.impact();
        if (NUMBERS.size() >= 96) NUMBERS.removeFirst(); NUMBERS.addLast(new Number(p,System.nanoTime()));
    }
    public static void reset() { NUMBERS.clear(); sequence = -1; }
    public static float reaction(int target) {
        long now=System.nanoTime(); float strength=0;
        for (var n : NUMBERS) if (n.packet.target()==target && n.packet.amount()>0 && now-n.started<230_000_000L)
            strength=Math.max(strength,(float)Math.sin(Math.PI*(now-n.started)/230_000_000.));
        return strength;
    }
    static void render(RenderLevelStageEvent e) {
        if (e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (!TrailsClient.active()) { reset(); return; }
        long now=System.nanoTime(); while (!NUMBERS.isEmpty() && now-NUMBERS.peekFirst().started>950_000_000L) NUMBERS.removeFirst();
        if (!TrailsPresentationOptions.numbers || NUMBERS.isEmpty()) return;
        var mc=Minecraft.getInstance(); var camera=e.getCamera().getPosition(); var buffers=mc.renderBuffers().bufferSource();
        int lane=0;
        for (var n : NUMBERS) {
            double t=(now-n.started)/950_000_000.; var point=n.packet.position().add((lane++%3-1)*.12,.65*t,0).subtract(camera);
            var pose=new com.mojang.blaze3d.vertex.PoseStack(); pose.translate(point.x,point.y,point.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation()); pose.scale(-.027f,-.027f,.027f);
            String text=n.packet.amount()<=.001 ? "BLOCK" : String.format(java.util.Locale.ROOT,"%.1f",n.packet.amount());
            int color=n.packet.amount()>=12 ? 0xffffd77b : 0xfff8f4ec;
            renderedNumbers++;
            mc.font.drawInBatch(text,-mc.font.width(text)/2f,0,color,true,pose.last().pose(),buffers,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);
        }
        buffers.endBatch();
    }
    private TrailsImpacts() {}
}
