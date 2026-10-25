package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Short, collision-clamped action framing. Never changes the user's orbit settings or world positions. */
public final class TrailsCinematics {
    private record Shot(TrailsNetwork.Performance packet, long started, ActionAnimations.CameraPose fixed) {}
    private static Shot shot;
    private static ActionAnimations.CameraPose last, returning;
    private static long returnTime, impactTime;
    public static long sampledFrames;
    static void receive(TrailsNetwork.Performance p) {
        if (!TrailsPresentationOptions.camera) return;
        var mc=Minecraft.getInstance();
        if (mc.level==null || !(mc.level.getEntity(p.actor()) instanceof LivingEntity actor)) return;
        boolean continuation=shot!=null && shot.packet.actor()==p.actor() && !p.animation().equals("draw");
        var fixed=continuation ? shot.fixed : framing(actor,p);
        shot = new Shot(p, System.nanoTime(), fixed); last=fixed; returning = null;
    }
    static void impact() { impactTime = System.nanoTime(); }
    static void release() { if (shot != null) { returning = last; returnTime = System.nanoTime(); shot = null; } }
    public static void reset() { shot = null; last = null; returning = null; impactTime = 0; }
    static boolean active() { return shot != null; }
    static boolean bursting() { return shot!=null && shot.packet.style().startsWith("burst_"); }
    public static ActionAnimations.CameraPose cameraPose() {
        if (!TrailsClient.active() || !TrailsPresentationOptions.camera) { reset(); return null; }
        var mc = Minecraft.getInstance(); if (mc.level == null || mc.player == null) { reset(); return null; }
        long now = System.nanoTime();
        if (shot != null && now - shot.started > (shot.packet.ticks() + (shot.packet.animation().equals("recover")?0L:4L)) * 50_000_000L) release();
        if (shot == null) {
            if (returning == null) return null;
            double t = (now - returnTime) / 220_000_000.;
            if (t >= 1) { returning = null; last = null; return null; }
            return last = blend(returning, free(), smooth(t));
        }
        var p = shot.packet;
        if (!(mc.level.getEntity(p.actor()) instanceof LivingEntity actor) || !actor.isAlive()) { release(); return null; }
        last=shot.fixed; sampledFrames++;
        var look = actor.getEyePosition();
        var side = new Vec3(Math.cos(Math.toRadians(last.yaw())),0,Math.sin(Math.toRadians(last.yaw())));
        if (TrailsPresentationOptions.shake && impactTime > 0 && now - impactTime < 140_000_000L) {
            double fade = 1 - (now - impactTime) / 140_000_000.;
            double strength = mc.options.screenEffectScale().get() * .045 * fade;
            var delta = side.scale(Math.sin((now - impactTime) / 9_000_000.) * strength);
            last = new ActionAnimations.CameraPose(clear(last.position().add(delta), look, actor), last.yaw(), last.pitch());
        }
        return last;
    }
    private static ActionAnimations.CameraPose framing(LivingEntity actor,TrailsNetwork.Performance p) {
        var source=actor.getEyePosition().add(0,-.25,0);
        Vec3 forward=p.aim().subtract(source).multiply(1,0,1);
        forward=forward.horizontalDistanceSqr()>1e-6 ? forward.normalize() : new Vec3(0,0,1);
        Vec3 side=new Vec3(forward.z,0,-forward.x);
        boolean ranged=p.style().contains("bow") || p.style().contains("trident") || p.style().equals("breeze");
        double size=Math.min(2.5,Math.max(1,actor.getBbWidth()));
        Vec3 look=source.add(forward.scale(ranged?2:1.1)).add(0,p.style().contains("mace")?.35:0,0);
        // Clamp the subject first, then the fixed camera, so neither endpoint starts inside a wall.
        var lookHit=actor.level().clip(new net.minecraft.world.level.ClipContext(source,look,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,actor));
        if (lookHit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS) look=lookHit.getLocation().add(source.subtract(look).normalize().scale(.15));
        Vec3 wanted=source.add(forward.scale(ranged?-3.8:-2.5)).add(side.scale((ranged?1.8:3.5)*size)).add(0,1.5*size,0);
        return lookAt(clear(wanted,look,actor),look);
    }
    private static double smooth(double t) { t = Math.clamp(t, 0, 1); return t*t*(3-2*t); }
    private static ActionAnimations.CameraPose free() {
        var mc = Minecraft.getInstance(); double yaw = Math.toRadians(BattleClient.yaw), pitch = Math.toRadians(BattleClient.pitch);
        Vec3 focus = TrailsClient.cameraFocus(mc.getTimer().getGameTimeDeltaPartialTick(false));
        Vec3 back = new Vec3(Math.sin(yaw)*Math.cos(pitch), Math.sin(pitch), -Math.cos(yaw)*Math.cos(pitch));
        Vec3 point = focus.add(back.scale(BattleClient.distance));
        if (mc.player != null) point = clear(point, focus, mc.player);
        return new ActionAnimations.CameraPose(point, BattleClient.yaw, BattleClient.pitch);
    }
    private static Vec3 clear(Vec3 position, Vec3 look, LivingEntity source) {
        var hit = source.level().clip(new net.minecraft.world.level.ClipContext(look, position, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, source));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? position : hit.getLocation().add(look.subtract(position).normalize().scale(.25));
    }
    private static ActionAnimations.CameraPose lookAt(Vec3 point, Vec3 look) {
        Vec3 d = look.subtract(point);
        return new ActionAnimations.CameraPose(point, (float)Math.toDegrees(Math.atan2(-d.x,d.z)), (float)-Math.toDegrees(Math.atan2(d.y,d.horizontalDistance())));
    }
    private static ActionAnimations.CameraPose blend(ActionAnimations.CameraPose from, ActionAnimations.CameraPose to, double t) {
        if (from == null) return to;
        return new ActionAnimations.CameraPose(from.position().lerp(to.position(),t), from.yaw()+net.minecraft.util.Mth.wrapDegrees(to.yaw()-from.yaw())*(float)t,
                from.pitch()+(to.pitch()-from.pitch())*(float)t);
    }
    static void hud(GuiGraphics g, int width, int height) {
        if (shot == null || !TrailsPresentationOptions.camera) return;
        var font = Minecraft.getInstance().font; var p = shot.packet;
        g.fill(0,height-38,width,height,0xe610131c);
        g.fill(width/2-132,height-36,width/2-129,height-17,p.red()?0xffed4e62:0xffffd481);
        g.drawCenteredString(font, font.plainSubstrByWidth(p.title(), 250), width/2, height-29, p.red() ? 0xffff8190 : 0xffffdfa0);
    }
    private TrailsCinematics() {}
}
