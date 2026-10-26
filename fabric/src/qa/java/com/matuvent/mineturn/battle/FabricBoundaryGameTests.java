package com.matuvent.mineturn.battle;

import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;

/** Native Fabric hooks, beyond the shared AT algorithm tests. */
public final class FabricBoundaryGameTests {
    @GameTest(template="mineturn:empty")
    public static void realtimeDamageEffectsAndKnockbackStayOutsideBattle(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();
            var z=f.mob(EntityType.ZOMBIE,8,5);f.start(false);float hp=f.p.getHealth();
            h.assertTrue(!f.p.hurt(f.p.damageSources().generic(),4) && f.p.getHealth()==hp,"Real-time damage crossed turn boundary");
            var source=EntityType.AREA_EFFECT_CLOUD.create(h.getLevel());
            h.assertTrue(!f.p.addEffect(new MobEffectInstance(MobEffects.POISON,100),source),"External effect entered battle");
            f.p.forceAddEffect(new MobEffectInstance(MobEffects.WITHER,100),source);
            h.assertTrue(!f.p.hasEffect(MobEffects.WITHER),"forceAddEffect bypassed boundary");
            var velocity=f.p.getDeltaMovement();f.p.knockback(1,1,0);h.assertTrue(f.p.getDeltaMovement().equals(velocity),"Native knockback moved locked player");
            f.b.close("restore");f.p.invulnerableTime=0;hp=f.p.getHealth();
            h.assertTrue(f.p.hurt(f.p.damageSources().generic(),4)&&f.p.getHealth()<hp,"Exit failed to restore native damage");
        }h.succeed();
    }
    @GameTest(template="mineturn:empty",timeoutTicks=80)
    public static void nativeLevelTickFreezesMobThenResumes(GameTestHelper h){
        var f=new TrailsGameTests.Fixture(h);var z=f.mob(EntityType.ZOMBIE,8,5);
        z.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,100));f.start(false);
        int ticks=z.tickCount,duration=z.getEffect(MobEffects.MOVEMENT_SPEED).getDuration();
        h.runAfterDelay(5,()->{
            try{
                h.assertTrue(z.tickCount==ticks&&z.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()==duration,"Waiting advanced native mob clocks");f.b.close("resume");
                h.runAfterDelay(4,()->{try{h.assertTrue(z.tickCount>ticks&&z.getEffect(MobEffects.MOVEMENT_SPEED).getDuration()<duration,"Exit did not resume native ticking");h.succeed();}finally{f.close();}});
            }catch(RuntimeException|Error error){f.close();throw error;}
        });
    }
    @GameTest(template="mineturn:empty")
    public static void logicalBreathingHonorsConduitAndBubbleColumns(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            f.mob(EntityType.ZOMBIE,8,5);var eye=net.minecraft.core.BlockPos.containing(f.p.getX(),f.p.getEyeY(),f.p.getZ());
            h.getLevel().setBlock(eye,Blocks.WATER.defaultBlockState(),3);h.getLevel().setBlock(f.p.blockPosition(),Blocks.WATER.defaultBlockState(),3);
            f.p.setAirSupply(50);f.p.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER,100));f.start(false);
            BattleStatus.tick(f.b.member(f.p));h.assertTrue(f.p.getAirSupply()==50,"Conduit power failed to prevent oxygen consumption");
            BattleManager.authorized(()->f.p.removeEffect(MobEffects.CONDUIT_POWER));BattleStatus.tick(f.b.member(f.p));h.assertTrue(f.p.getAirSupply()==49,"AV breath did not consume oxygen");
            h.getLevel().setBlock(eye,Blocks.BUBBLE_COLUMN.defaultBlockState(),3);BattleStatus.tick(f.b.member(f.p));h.assertTrue(f.p.getAirSupply()>49,"Bubble column failed to restore oxygen");
        }h.succeed();
    }
    @GameTest(template="mineturn:empty")
    public static void nativeSlimeSpawningJoinsChildrenOnce(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var slime=f.mob(EntityType.SLIME,8,5);slime.setSize(2,true);f.start(false);
            slime.setHealth(0);slime.remove(Entity.RemovalReason.KILLED);
            var children=f.b.members.values().stream().map(m->m.entity).filter(e->e instanceof Slime && e!=slime).toList();
            h.assertTrue(children.size()>=2&&children.size()<=4&&children.stream().allMatch(BattleManager::locked),"Native slime children lost battle membership");
            BattleManager.finishSplit(slime);h.assertTrue(f.b.members.values().stream().filter(m->m.entity instanceof Slime&&m.entity!=slime).count()==children.size(),"Children admitted twice");
        }h.succeed();
    }
    @GameTest(template="mineturn:empty")
    public static void nativeExplosionDoesNotPushLockedPlayers(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();
            f.mob(EntityType.ZOMBIE,8,5);f.start(false);float health=f.p.getHealth();f.p.setDeltaMovement(Vec3.ZERO);
            var e=h.getLevel().explode(null,f.p.getX()+1,f.p.getY(),f.p.getZ(),2,Level.ExplosionInteraction.NONE);
            h.assertTrue(f.p.getHealth()==health&&f.p.getDeltaMovement().equals(Vec3.ZERO)&&!e.getHitPlayers().containsKey(f.p),"Explosion damage or network impulse crossed battle boundary");
        }h.succeed();
    }
    @GameTest(template="mineturn:empty")
    public static void savedTemporarySummonsAreDiscardedOnLoad(GameTestHelper h){
        var mob=EntityType.PIG.create(h.getLevel());mob.addTag("mineturn:temporary_summon");var tag=mob.saveWithoutId(new CompoundTag());
        var restored=EntityType.PIG.create(h.getLevel());restored.load(tag);h.assertTrue(restored.isRemoved(),"Orphan summon survived native entity load");h.succeed();
    }
}
