package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.breeze.Breeze;

/** Temporary vanilla model states, kept separate from authoritative damage and AI. */
public final class TrailsPerformances {
    private static final Map<Integer, PoseState> ACTIVE = new HashMap<>();
    private static long sequence = -1;
    public static long renderedPoses, visualWeaponFrames;
    public record Frame(String style, String phase, double progress, com.matuvent.mineturn.battle.TrailsChoreography.Pose pose) {}
    public static Frame frame(LivingEntity e) {
        var state=ACTIVE.get(e.getId()); if (state==null || state.entity!=e) return null;
        double progress=1-(state.expires-System.nanoTime())/(state.packet.ticks()*50_000_000.);
        if (progress<0 || progress>1) return null;
        return new Frame(state.packet.style(),state.packet.animation(),progress,
                com.matuvent.mineturn.battle.TrailsChoreography.pose(state.packet.style(),state.packet.animation(),progress));
    }
    public static net.minecraft.world.item.ItemStack visualItem(LivingEntity e,net.minecraft.world.item.ItemStack original,HumanoidArm arm) {
        var frame=frame(e);var mc=Minecraft.getInstance();
        if(frame==null || !frame.style().startsWith("burst_") || arm!=e.getMainArm())return original;
        if(e!=mc.player){var state=ACTIVE.get(e.getId());if(state==null || state.packet.weapon().isBlank())return original;
            var id=net.minecraft.resources.ResourceLocation.tryParse(state.packet.weapon());if(id==null)return original;
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);visualWeaponFrames++;return new net.minecraft.world.item.ItemStack(item);}
        for(int i=0;i<36;i++) {
            var candidate=mc.player.getInventory().getItem(i);
            if(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(candidate.getItem()).toString().equals(TrailsClient.view.primary())) {
                visualWeaponFrames++;return candidate;
            }
        }
        return original;
    }
    private record PoseState(LivingEntity entity, Pose original, boolean aggressive, float pitch, boolean using, InteractionHand hand, int remaining, float walk, boolean crossbow, boolean ghast, boolean blaze, int beam, int swell,
                             Runnable nativeRestore, TrailsNetwork.Performance packet, long expires) {
        void restore() {
            nativeRestore.run();
            entity.setPose(original); entity.setXRot(pitch); entity.walkAnimation.setSpeed(walk); entity.stopUsingItem();
            var access = (com.matuvent.mineturn.mixin.StatusAccess)entity;
            access.mineturn$useFlag(1, false);
            if (using) { entity.startUsingItem(hand); access.mineturn$useFlag(1, true); access.mineturn$useFlag(2, hand == InteractionHand.OFF_HAND); access.mineturn$useRemaining(remaining); }
            if (entity instanceof Mob mob) mob.setAggressive(aggressive);
            if (entity instanceof Pillager p) p.setChargingCrossbow(crossbow);
            if (entity instanceof Ghast g) g.setCharging(ghast);
            if (entity instanceof Blaze b) ((com.matuvent.mineturn.mixin.BlazeBattleAccess)b).mineturn$charged(blaze);
            if (entity instanceof Guardian g) ((com.matuvent.mineturn.api.GuardianBeamAccess)g).mineturn$beam(beam, -1);
            if (entity instanceof Creeper c) c.setSwellDir(swell);
            if (entity instanceof Breeze b) { b.shoot.stop(); b.inhale.stop(); }
        }
    }
    static boolean bursting() {
        long now=System.nanoTime();return ACTIVE.values().stream().anyMatch(state->state.packet.style().startsWith("burst_") && now<state.expires);
    }
    public static void reset() { ACTIVE.values().forEach(PoseState::restore); ACTIVE.clear(); sequence = -1; }
    public static boolean red(LivingEntity e) { var s = ACTIVE.get(e.getId()); var threat=TrailsWarnings.threat(e); return !TrailsWarnings.gold(e) && (threat!=null && !threat.golden() || s != null && s.entity == e && s.packet.red() && System.nanoTime() < s.expires); }
    static int count() { return ACTIVE.size(); }
    static void receive(TrailsNetwork.Performance p) {
        var mc = Minecraft.getInstance();
        if (!TrailsClient.active() || !TrailsClient.view.battle().equals(p.battle()) || p.sequence() <= sequence
                || p.ticks() < 1 || p.ticks() > 100 || mc.level == null) return;
        sequence = p.sequence();
        if (!(mc.level.getEntity(p.actor()) instanceof LivingEntity e)) return;
        var old = ACTIVE.remove(p.actor());
        AnimationState sonic=null;
        if(e instanceof net.minecraft.world.entity.monster.warden.Warden w && old!=null && old.entity==e && old.packet.style().equals(p.style()) && p.style().contains("sonic")){
            sonic=w.sonicBoomAnimationState;w.sonicBoomAnimationState=new AnimationState();
        }
        if (old != null) old.restore();
        ACTIVE.put(p.actor(), new PoseState(e, e.getPose(), e instanceof Mob m && m.isAggressive(), e.getXRot(), e.isUsingItem(), e.getUsedItemHand(), e.getUseItemRemainingTicks(), e.walkAnimation.speed(),
                e instanceof Pillager v && v.isChargingCrossbow(), e instanceof Ghast g && g.isCharging(), e instanceof Blaze blaze && blaze.isOnFire(),
                e instanceof Guardian g && g.getActiveAttackTarget() != null ? g.getActiveAttackTarget().getId() : 0,
                e instanceof Creeper c ? c.getSwellDir() : -1, TrailsSpeciesAnimations.begin(e,p), p,
                System.nanoTime() + p.ticks() * 50_000_000L));
        if(sonic!=null && sonic.isStarted() && e instanceof net.minecraft.world.entity.monster.warden.Warden w)w.sonicBoomAnimationState=sonic;
        TrailsCinematics.receive(p); TrailsEffects.receive(p);
        if (p.animation().equals("strike") || p.animation().equals("support")) {
            e.swing(InteractionHand.MAIN_HAND);
            if (e instanceof IronGolem || e instanceof Ravager) e.handleEntityEvent((byte)4);
            if (e instanceof Sheep && p.animation().equals("support")) e.handleEntityEvent((byte)10);
            if (e instanceof Slime slime) slime.targetSquish = -0.5f;
            if (e instanceof Wolf && p.animation().equals("support")) e.handleEntityEvent((byte)8);
        }
        pose(ACTIVE.get(e.getId()));
    }
    private static void pose(PoseState state) {
        var e = state.entity; var p = state.packet;
        TrailsSpeciesAnimations.pose(e,p);
        boolean draw = p.animation().equals("draw");
        var delta = p.aim().subtract(e.position());
        if (delta.horizontalDistanceSqr() > 1e-6) {
            float yaw = (float)Math.toDegrees(Math.atan2(-delta.x, delta.z));
            e.setYRot(yaw); e.yBodyRot = yaw; e.setYHeadRot(yaw);
        }
        if (p.animation().equals("recover")) return;
        if (e instanceof Mob mob) mob.setAggressive(true);
        if (draw && (e.getMainHandItem().getItem() instanceof net.minecraft.world.item.BowItem
                || e.getMainHandItem().getItem() instanceof net.minecraft.world.item.CrossbowItem
                || e.getMainHandItem().getItem() instanceof net.minecraft.world.item.TridentItem)) {
            if (!e.isUsingItem()) {
                e.startUsingItem(InteractionHand.MAIN_HAND);
                var access = (com.matuvent.mineturn.mixin.StatusAccess)e;
                access.mineturn$useFlag(1, true); access.mineturn$useFlag(2, false);
            }
        }
        if (e instanceof Pillager pillager) pillager.setChargingCrossbow(draw);
        if (e instanceof Ghast ghast) ghast.setCharging(draw);
        if (e instanceof Creeper creeper) creeper.setSwellDir(draw ? 1 : -1);
        if (e instanceof Blaze blaze) ((com.matuvent.mineturn.mixin.BlazeBattleAccess)blaze).mineturn$charged(draw);
        if (draw && e instanceof Guardian guardian) {
            var target = Minecraft.getInstance().level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(28),
                    candidate -> candidate != e && candidate.position().distanceToSqr(p.aim()) < 1).stream().findFirst();
            target.ifPresent(t -> ((com.matuvent.mineturn.api.GuardianBeamAccess)guardian).mineturn$beam(t.getId(), 30));
        }
        if (e instanceof Breeze breeze) {
            e.setPose(draw ? Pose.INHALING : Pose.SHOOTING);
            if (draw) breeze.inhale.startIfStopped(e.tickCount); else breeze.shoot.startIfStopped(e.tickCount);
        }
        if (e instanceof Chicken c) { c.oFlap = c.flap; c.flap += 1.2f; c.oFlapSpeed = c.flapSpeed; c.flapSpeed = 1; }
        if (e instanceof Pig || e instanceof Cow || e instanceof Cat || e instanceof Wolf || e instanceof Spider) {
            e.walkAnimation.setSpeed(.7f);
            if (p.animation().equals("support")) e.setXRot(25);
        }
    }
    static void tick() {
        if (!TrailsClient.active()) { if (!ACTIVE.isEmpty()) reset(); return; }
        var mc = Minecraft.getInstance(); long now = System.nanoTime();
        var iterator = ACTIVE.values().iterator();
        while (iterator.hasNext()) {
            var state = iterator.next();
            if (mc.level == null || state.entity.level() != mc.level || !state.entity.isAlive() || now >= state.expires) {
                state.restore(); iterator.remove();
            } else pose(state);
        }
    }
    private TrailsPerformances() {}
}
