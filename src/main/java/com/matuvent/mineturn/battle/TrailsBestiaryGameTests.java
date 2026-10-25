package com.matuvent.mineturn.battle;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("trails")
@PrefixGameTestTemplate(false)
public final class TrailsBestiaryGameTests {
    @GameTest(template="empty")
    public static void everyVanillaMobHasExplicitBasicSkillAndValidAssets(GameTestHelper h) {
        var actual=new TreeSet<String>();
        for(var type:BuiltInRegistries.ENTITY_TYPE){
            var id=BuiltInRegistries.ENTITY_TYPE.getKey(type);if(!id.getNamespace().equals("minecraft"))continue;
            var entity=type.create(h.getLevel());if(entity instanceof Mob)actual.add(id.toString());
        }
        var configured=new TreeSet<String>();
        for(var profile:TrailsBestiary.all()){
            configured.add(profile.id());var moves=new ArrayList<>(List.of(profile.basic(),profile.skill()));if(profile.ultimate()!=null)moves.add(profile.ultimate());
            for(var move:moves){
                h.assertTrue(!move.name().isBlank() && !move.animation().isBlank() && Set.of("target","self","circle","cone","line").contains(move.shape()),"Invalid move "+profile.id());
                h.assertTrue(move.power()>=0 && move.area().validAim(net.minecraft.world.phys.Vec3.ZERO),"Invalid geometry "+profile.id());
                if(!move.buff().isBlank())h.assertTrue(BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.withDefaultNamespace(move.buff())),"Unknown buff "+profile.id());
                if(!move.projectile().isBlank())h.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.withDefaultNamespace(move.projectile())),"Unknown projectile "+profile.id());
            }
        }
        h.assertTrue(actual.equals(configured),"Vanilla Mob coverage mismatch: actual="+actual+" configured="+configured);
        h.assertTrue(configured.size()==82,"Expected 82 Mob types on 1.21.1");h.succeed();
    }
    private static <T extends Mob> void golden(GameTestHelper h,EntityType<T> type) {
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();
            var boss=f.mob(type,5,10);if(boss instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither)wither.setInvulnerableTicks(0);
            f.start(false);var c=f.b.trails;f.b.actor=boss;c.onTurn();c.turns.put(boss.getUUID(),3);c.enemyAction();
            var plan=c.plans.get(boss.getUUID());h.assertTrue(plan!=null && plan.warning().golden(),"Boss did not prepare gold S: "+type);
            h.assertTrue(c.pending==null && !c.busy(),"Forecast immediately attacked or locked input");
            f.b.actor=f.p;c.onTurn();for(int i=0;i<650;i++)c.tick();
            h.assertTrue(c.plans.get(boss.getUUID())==plan,"Gold warning expired during player thinking");
            float hp=f.p.getHealth();f.b.actor=boss;c.onTurn();c.enemyAction();
            h.assertTrue(!c.plans.containsKey(boss.getUUID()) && c.pending!=null && f.p.getHealth()==hp,"S did not consume forecast and stage windup");
            TrailsGameTests.resolve(c);h.assertTrue(f.p.getHealth()<hp,"Boss S never landed: "+type);
            f.b.close("exit");h.assertTrue(c.plans.isEmpty() && c.charging.isEmpty(),"Exit retained gold warning");
        }
        h.succeed();
    }
    @GameTest(template="empty") public static void witherGoldPersistsUntilOwnTurnAndLands(GameTestHelper h){golden(h,EntityType.WITHER);}
    @GameTest(template="empty") public static void dragonGoldPersistsUntilOwnTurnAndLands(GameTestHelper h){golden(h,EntityType.ENDER_DRAGON);}
    @GameTest(template="empty") public static void wardenGoldPersistsUntilOwnTurnAndLands(GameTestHelper h){golden(h,EntityType.WARDEN);}
    @GameTest(template="empty")
    public static void lockedSonicLaneCanBeDodgedButDoesNotCareAboutWall(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();var w=f.mob(EntityType.WARDEN,5,11);f.start(false);var c=f.b.trails;
            f.b.actor=w;c.onTurn();TrailsBestiaryActions.prepare(c,w,f.p,TrailsBestiary.get(w).ultimate(),true);
            var warning=c.plans.get(w.getUUID()).warning();f.b.place(f.p,f.p.position().add(4,0,0));float hp=f.p.getHealth();
            f.b.actor=w;c.onTurn();c.enemyAction();TrailsGameTests.resolve(c);h.assertTrue(f.p.getHealth()==hp,"Sonic retargeted after gold forecast");
            f.b.place(f.p,new net.minecraft.world.phys.Vec3(warning.origin().x,warning.origin().y,warning.origin().z-5));
            for(int y=1;y<5;y++)h.setBlock(5,y,8,Blocks.STONE);
            f.b.actor=w;c.onTurn();TrailsBestiaryActions.cast(c,w,f.p,TrailsBestiary.get(w).skill(),f.p.position().subtract(w.position()),false,w.position());TrailsGameTests.resolve(c);
            h.assertTrue(f.p.getHealth()<hp,"Native sonic did not cross cover");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void deadBossLosesForecastAndCannotCast(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var w=f.mob(EntityType.WITHER,5,10);w.setInvulnerableTicks(0);f.mob(EntityType.ZOMBIE,12,12);f.start(false);var c=f.b.trails;
            f.b.actor=w;c.onTurn();TrailsBestiaryActions.prepare(c,w,f.p,TrailsBestiary.get(w).ultimate(),true);w.setHealth(0);
            f.b.actor=f.p;c.onTurn();c.tick();h.assertTrue(!c.plans.containsKey(w.getUUID()) && c.pending==null,"Dead boss retained S");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void allayBatAndFishAreControllableAndSelfBuffStaysSelf(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            f.mob(EntityType.ZOMBIE,12,12);var a=f.mob(EntityType.ALLAY,6,6);var bat=f.mob(EntityType.BAT,6,7);var fish=f.mob(EntityType.COD,6,8);var cat=f.mob(EntityType.CAT,5,5);f.start(false);var c=f.b.trails;
            h.assertTrue(c.allied(a) && c.allied(bat) && c.allied(fish),"Non-animal peaceful creatures lost allied control");
            f.b.actor=cat;c.onTurn();c.companionSkill(-1);TrailsGameTests.resolve(c);
            h.assertTrue(!cat.getActiveEffects().isEmpty() && f.p.getActiveEffects().isEmpty(),"Self buff leaked to nearby friend");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void vexPhasesThroughWallWithoutEndingInsideIt(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();var vex=f.mob(EntityType.VEX,5,11);f.start(false);var c=f.b.trails;
            for(int y=1;y<5;y++)for(int x=3;x<9;x++)h.setBlock(x,y,8,Blocks.STONE);
            f.b.actor=vex;c.onTurn();c.turns.put(vex.getUUID(),3);float hp=f.p.getHealth();c.enemyAction();TrailsGameTests.resolve(c);
            h.assertTrue(vex.getZ()<h.absolutePos(new BlockPos(5,1,8)).getZ() && f.p.getHealth()<hp && vex.level().noCollision(vex),"Vex phase path or damage failed");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void playerCanDamageDragonThroughNativeHeadPath(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var dragon=f.mob(EntityType.ENDER_DRAGON,5,8);f.start(false);float hp=dragon.getHealth();f.b.trails.hit(f.p,dragon,12);
            h.assertTrue(dragon.getHealth()<hp,"Native dragon hurt path rejected player damage");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void defeatedDragonLeavesBattleForNativeDeathFlight(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var dragon=f.mob(EntityType.ENDER_DRAGON,5,8);f.start(false);dragon.getPhaseManager().setPhase(net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.HOLDING_PATTERN);dragon.setHealth(1);var c=f.b.trails;c.hit(f.p,dragon,100);c.tick();
            h.assertTrue(f.b.closed && !BattleManager.locked(dragon) && dragon.getPhaseManager().getCurrentPhase().getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING,"Dragon stayed frozen: closed="+f.b.closed+" locked="+BattleManager.locked(dragon)+" hp="+dragon.getHealth()+" phase="+dragon.getPhaseManager().getCurrentPhase().getPhase());
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void speciesAttacksHonorStrengthAndWeakness(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();var zombie=f.mob(EntityType.ZOMBIE,5,7);f.start(false);var c=f.b.trails;
            var basic=TrailsBestiary.get(zombie).basic();float hp=f.p.getHealth();
            TrailsBestiaryActions.apply(c,zombie,f.p,basic,net.minecraft.world.phys.Vec3.ZERO,zombie.position());float first=hp-f.p.getHealth();
            TrailsCombat.effect(zombie,net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,200,0);hp=f.p.getHealth();
            TrailsBestiaryActions.apply(c,zombie,f.p,basic,net.minecraft.world.phys.Vec3.ZERO,zombie.position());h.assertTrue(hp-f.p.getHealth()>first,"Species strength buff did not change damage");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void creeperWarningStaysRedRatherThanBorrowingBossS(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            var creeper=f.mob(EntityType.CREEPER,5,8);f.start(false);var c=f.b.trails;f.b.actor=creeper;c.onTurn();c.enemyAction();
            h.assertTrue(c.plans.containsKey(creeper.getUUID()) && !c.plans.get(creeper.getUUID()).warning().golden(),"Creeper warning was golden");
            f.b.actor=creeper;c.onTurn();c.enemyAction();h.assertTrue(c.pending.red() && !c.performances.get(creeper.getUUID())[0].startsWith("boss_"),"Ordinary charge became boss S");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void ordinarySupportMatchesPreviewHeightButAerialDragonCanReachGround(GameTestHelper h){
        try(var f=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++)f.p.tick();var sheep=f.mob(EntityType.SHEEP,5,6);var dragon=f.mob(EntityType.ENDER_DRAGON,5,10);f.start(false);var c=f.b.trails;
            f.b.place(f.p,f.p.position().add(0,4,0));f.p.setHealth(10);
            var heal=TrailsBestiary.get(sheep).skill();TrailsBestiaryActions.apply(c,sheep,null,heal,net.minecraft.world.phys.Vec3.ZERO,sheep.position());
            h.assertTrue(f.p.getHealth()==10,"Ground support exceeded the preview's 3-block vertical reach");
            f.b.place(dragon,dragon.position().add(0,8,0));float hp=f.p.getHealth();
            var attack=TrailsBestiary.get(dragon).basic();
            TrailsBestiaryActions.apply(c,dragon,f.p,attack,attack.aim(dragon,f.p),dragon.position());h.assertTrue(f.p.getHealth()<hp,"Aerial dragon could not reach its marked ground area");
        }h.succeed();
    }
    private TrailsBestiaryGameTests(){}
}
