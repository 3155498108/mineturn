package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.data.CombatData;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("mineturn")
@PrefixGameTestTemplate(false)
public final class ContactGameTests {
    @GameTest(template="empty")
    public static void lowFieldsAndTrapsResolveMountedGroupOnce(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            var mount=h.spawnWithNoFreeWill(EntityType.HORSE,new BlockPos(2,1,2));mount.setPos(start);mount.setTamed(true);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            try{
                battle.place(f.mob,start.add(5,0,0));var riderStart=f.player.position();var targets=new java.util.ArrayList<net.minecraft.world.entity.LivingEntity>();
                h.assertTrue(riderStart.y>start.y+.5,"Fixture rider overlaps low field");
                var action=CombatData.current.actions().get("mineturn:mob_melee");
                var pulse=BattleFields.create(battle,f.mob,new com.matuvent.mineturn.api.CombatFields.Request(start,1,.5,5,100),action,net.minecraft.world.item.ItemStack.EMPTY,c->targets.add(c.target()));
                h.assertTrue(pulse.accepted(),"Low field failed to spawn: "+pulse.reason());
                battle.clock.advance(5,battle::agility);BattleFields.advance(battle);BattleFields.advance(battle);
                h.assertTrue(targets.equals(java.util.List.of(f.player)),"Low pulse missed carrier or applied to both members of group");
                var trap=BattleFields.create(battle,f.mob,new com.matuvent.mineturn.api.CombatFields.Request(start.add(2,0,0),.6,.5,5,100,true,2,0),action,net.minecraft.world.item.ItemStack.EMPTY,c->targets.add(c.target()));
                h.assertTrue(trap.accepted(),"Low trap failed to spawn: "+trap.reason());
                battle.place(f.player,riderStart.add(2,0,0));
                h.assertTrue(targets.equals(java.util.List.of(f.player,f.player)) && battle.fields.get(trap.id()).charges==1,"Low mounted arrival missed trap or consumed multiple charges");
                battle.place(f.player,f.player.position());h.assertTrue(targets.size()==2,"Stationary mounted group retriggered arrival");
            }finally{battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void lowPotionCloudUsesRiderAsOnlyNativeVictim(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            var mount=h.spawnWithNoFreeWill(EntityType.HORSE,new BlockPos(2,1,2));mount.setPos(start);mount.setTamed(true);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            var potion=new net.minecraft.world.item.alchemy.PotionContents(java.util.Optional.empty(),java.util.Optional.empty(),java.util.List.of(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,200)));
            var cloud=new BattleCloud(battle,f.mob,start,potion);
            try{
                cloud.setWaitTime(0);cloud.setRadiusOnUse(0);h.getLevel().addFreshEntity(cloud);
                h.assertTrue(cloud.participants(java.util.List.of(mount,f.player,mount)).equals(java.util.List.of(f.player)),"Cloud did not normalize and deduplicate mounted target");
                for(int i=0;i<5;i++)cloud.advance();
                h.assertTrue(f.player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN) && !mount.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),"Native low cloud missed rider or affected carrier separately");
                battle.close("test");h.assertTrue(cloud.participants(java.util.List.of(mount,f.player)).isEmpty(),"Closed battle retained cloud victims");
            }finally{cloud.discard();battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void riptideAttackCallbackCannotReenterOrHitDepartedTarget(GameTestHelper h){
        for(int mode=0;mode<5;mode++)try(var f=new Fixture(h)){
            int scenario=mode;var start=f.player.position();f.battle.place(f.mob,start.add(3,0,0));
            h.getLevel().setBlock(f.player.blockPosition(),Blocks.WATER.defaultBlockState(),2);
            var weapon=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
            weapon.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.RIPTIDE),3);
            f.player.getInventory().setItem(4,weapon);f.battle.budget=new TurnBudget(6);f.battle.budget.setActions(2,1);var paid=f.battle.budget;
            float hp=f.mob.getHealth();var calls=new java.util.concurrent.atomic.AtomicInteger();var blocked=new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.AttackEntityEvent> listener=event->{
                if(event.getEntity()!=f.player)return;calls.incrementAndGet();
                for(String operation:new String[]{"end","sprint","move"})try{f.battle.action(f.player,operation,0,"",-1,1,0);}
                catch(IllegalArgumentException expected){blocked.incrementAndGet();}
                if(scenario==1)f.battle.leave(f.mob,"riptide target departure");
                if(scenario==2)f.battle.leave(f.player,"riptide source departure");
                if(scenario==3)f.battle.place(f.mob,start.add(6,0,0));
                if(scenario==4)throw new IllegalArgumentException("injected paid riptide failure");
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try{BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"potion_ground",4,"mineturn:riptide",-1,start.add(5,0,0),0));}
            finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
            h.assertTrue(calls.get()==1 && blocked.get()==3,"Riptide native attack callback allowed recursive actions");
            h.assertTrue(f.mob.getHealth()==(mode==0?hp-8:hp),"Riptide hit a departed/relocated target or lost ordinary hit");
            h.assertTrue(weapon.getDamageValue()==1 && paid.mainActions()==1 && paid.remaining()==6 && !BattleManager.authorized(),"Riptide repeated/refunded payment or leaked authority");
            if(mode==4)h.assertTrue(f.battle.closed && !BattleManager.locked(f.player),"Paid riptide failure was treated as an unpaid validation rejection");
            if(!f.battle.closed)f.battle.requireTurn(f.player);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void mountedTeleportChecksWholeBodyAndRevalidatesLanding(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            var mount=h.spawnWithNoFreeWill(EntityType.HORSE,new BlockPos(2,1,2));mount.setPos(start);mount.setTamed(true);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            try{
                var rules=new com.matuvent.mineturn.api.CombatTeleport.Rules(8,false,false);var riderStart=f.player.position();var point=riderStart.add(3,0,0);
                var preview=BattleTeleport.preview(battle,f.player,point,rules);
                h.assertTrue(preview.success() && mount.position().equals(start) && f.player.position().equals(riderStart),"Mounted preview rejected legal landing or moved group");
                var obstacle=BlockPos.containing(point.add(0,f.player.getBbHeight()-.1,0));h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);
                h.assertTrue(!BattleTeleport.apply(battle,f.player,point,rules).success() && mount.position().equals(start),"Teleport ignored rider-only head obstacle introduced after preview");
                h.getLevel().setBlock(obstacle,Blocks.AIR.defaultBlockState(),2);
                var enemyStart=f.mob.position();battle.place(f.mob,start.add(3,0,0));
                h.assertTrue(!BattleTeleport.preview(battle,f.player,point,rules).success(),"Mounted teleport ignored occupied carrier landing");battle.place(f.mob,enemyStart);
                float hp=mount.getHealth();mount.fallDistance=9;f.player.fallDistance=9;double av=battle.clock.time(),movement=battle.budget.remaining();
                h.assertTrue(BattleTeleport.apply(battle,f.player,point,rules).success(),"Legal group teleport failed");
                h.assertTrue(mount.position().distanceToSqr(start.add(3,0,0))<1e-8 && f.player.position().distanceToSqr(point)<1e-8 && f.player.getVehicle()==mount,"Group teleport split rider/carrier or used rider feet as carrier feet");
                h.assertTrue(mount.fallDistance==0 && f.player.fallDistance==0 && mount.getHealth()==hp && battle.clock.time()==av && battle.budget.remaining()==movement && battle.budget.canAct(),"Group teleport caused fall damage or spent additional budget/AV");
            }finally{battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void mountedTeleportWaterPermissionAndUnsupportedLanding(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            for(int x=2;x<=4;x++)for(int z=-1;z<=1;z++){
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-2,z)),Blocks.STONE.defaultBlockState(),2);
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-1,z)),Blocks.WATER.defaultBlockState(),2);
            }
            var mount=h.spawnWithNoFreeWill(EntityType.HORSE,new BlockPos(2,1,2));mount.setPos(start);mount.setTamed(true);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            try{
                var dry=new com.matuvent.mineturn.api.CombatTeleport.Rules(8,false,false);var wet=new com.matuvent.mineturn.api.CombatTeleport.Rules(8,false,true);
                var point=f.player.position().add(3,-1,0);
                h.assertTrue(!BattleTeleport.preview(battle,f.player,point,dry).success(),"Dry teleport allowed immersed carrier beneath dry rider");
                h.assertTrue(!BattleTeleport.preview(battle,f.player,point.add(0,3,0),wet).success(),"Group teleport enabled unsupported hovering");
                h.assertTrue(BattleTeleport.apply(battle,f.player,point,wet).success() && mount.position().distanceToSqr(start.add(3,-1,0))<1e-8,"Water-enabled teleport failed to carry mount into water");
                battle.stabilizePlayers();h.assertTrue(mount.position().distanceToSqr(start.add(3,-1,0))<1e-8,"Mounted teleport landing sank to bottom");
                h.assertTrue(!BattleTeleport.preview(battle,mount,start,wet).success(),"Carrier bypassed participant ownership");
            }finally{battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void pearlLandingEventCannotReenterOrReplacePaidStack(GameTestHelper h){
        for(int mode=0;mode<5;mode++)try(var f=new Fixture(h)){
            int scenario=mode;var start=f.player.position();var destination=start.add(2,0,0);var paid=f.battle.budget;
            var pearls=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL,2);f.player.getInventory().setItem(4,pearls);
            var calls=new java.util.concurrent.atomic.AtomicInteger();var blocked=new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityTeleportEvent.EnderPearl> listener=event->{
                if(event.getEntity()!=f.player)return;calls.incrementAndGet();
                for(String operation:new String[]{"end","sprint","move","pearl"})try{
                    if(operation.equals("pearl"))f.battle.usePotionPoint(f.player,4,"mineturn:ender_pearl",destination);
                    else f.battle.action(f.player,operation,0,"",-1,1,0);
                }catch(IllegalArgumentException expected){blocked.incrementAndGet();}
                if(scenario==1)f.player.getInventory().setItem(4,pearls.copy());
                if(scenario==2)event.setCanceled(true);
                if(scenario==3)f.battle.leave(f.player,"pearl event departure");
                if(scenario==4)f.player.setPos(start.add(0,0,1));
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try{BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"potion_ground",4,"mineturn:ender_pearl",-1,destination,0));}
            finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
            h.assertTrue(calls.get()==1 && blocked.get()==4 && !BattleManager.authorized(),"Pearl event recursively dispatched an action or leaked authority");
            if(mode==0)h.assertTrue(pearls.getCount()==1 && !paid.canAct() && f.player.position().distanceToSqr(destination)<1e-8,"Valid pearl did not consume/teleport exactly once");
            else h.assertTrue(pearls.getCount()==2 && f.player.getInventory().getItem(4).getCount()==2 && paid.canAct() && f.player.position().distanceToSqr(destination)>1,"Rejected pearl spent or teleported after callback mutation");
            if(!f.battle.closed){f.battle.requireTurn(f.player);h.assertTrue(f.battle.shot==null && f.battle.motion==null,"Pearl callback started another operation");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void postDamageDepartureStopsDurabilityAndPreservesDepartedVelocity(GameTestHelper h){
        for(boolean sourceLeaves:new boolean[]{false,true})try(var f=new Fixture(h)){
            var extra=EntityType.PILLAGER.create(h.getLevel());extra.setPos(f.mob.position().add(0,0,2));h.getLevel().addFreshEntity(extra);f.battle.add(extra);
            try{
                f.battle.place(f.mob,f.player.position().add(1,0,0));
                var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);f.player.getInventory().setItem(4,sword);
                float hp=f.mob.getHealth();var speed=new net.minecraft.world.phys.Vec3(.4,.2,0);var calls=new java.util.concurrent.atomic.AtomicInteger();var paidBudget=f.battle.budget;
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post> listener=event->{
                    if(event.getEntity()!=f.mob)return;
                    calls.incrementAndGet();f.battle.leave(sourceLeaves?f.player:f.mob,"post damage departure");f.mob.setDeltaMovement(speed);
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                try{BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"use",4,"mineturn:melee",f.mob.getId(),f.mob.position(),0));}
                finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
                h.assertTrue(calls.get()==1 && f.mob.getHealth()<hp && sword.getDamageValue()==0,"Post-hit departure refunded damage or continued durability callback");
                // Native hurt may apply knockback after Post; the adapter must not erase that departed state.
                h.assertTrue(f.mob.getDeltaMovement().lengthSqr()>0 && !paidBudget.canAct() && !BattleManager.authorized(),"Adapter erased departed velocity, refunded action or leaked authority: sourceLeaves="+sourceLeaves+", velocity="+f.mob.getDeltaMovement()+", paid="+!paidBudget.canAct());
            }finally{extra.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void lethalMeleeStillPaysNativeDurability(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.place(f.mob,f.player.position().add(1,0,0));f.mob.setHealth(1);
            var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);f.player.getInventory().setItem(4,sword);
            BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"use",4,"mineturn:melee",f.mob.getId(),f.mob.position(),0));
            h.assertTrue(!f.mob.isAlive() && sword.getDamageValue()==1 && !BattleManager.authorized(),"Lethal hit was mistaken for departed-target callback and skipped native durability");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void mountedSwimmingUsesCarrierFeetAndIndependentBreathing(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            for(int x=1;x<=6;x++)for(int z=-1;z<=1;z++){
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-4,z)),Blocks.STONE.defaultBlockState(),2);
                for(int y=-3;y<=-1;y++)h.getLevel().setBlock(BlockPos.containing(start.add(x,y,z)),Blocks.WATER.defaultBlockState(),2);
            }
            var mount=h.spawnWithNoFreeWill(EntityType.HORSE,new BlockPos(2,1,2));mount.setPos(start);mount.setTamed(true);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            try{
                battle.budget=new TurnBudget(20);double av=battle.clock.time();var water=start.add(3,-1,0);
                battle.beginMoveTo(f.player,start.add(3,-1.0/9,0));
                for(int tick=0;tick<200 && battle.motion!=null;tick++)battle.tickMotion();
                h.assertTrue(battle.motion==null && mount.position().distanceToSqr(water)<1e-6 && battle.movementMode(f.player).equals("aquatic"),"Mount did not enter water or incorrectly walked on its surface");
                h.assertTrue(AquaticPath.feet(f.player,f.player.position()).distanceToSqr(mount.position())<1e-8 && AquaticPath.immersed(f.player,f.player.position()),"Mounted water test did not use carrier feet");
                battle.stabilizePlayers();h.assertTrue(mount.position().distanceToSqr(water)<1e-6,"Gravity sank immersed carrier through water");
                battle.commitMovement(f.player,battle.validateDestination(f.player,water.add(0,-1,0)));
                h.assertTrue(mount.position().distanceToSqr(water.add(0,-1,0))<1e-6,"Mounted vertical swimming failed");
                BattleManager.authorized(()->mount.setAirSupply(5));
                BattleStatus.tick(battle.member(f.player).mountState);BattleStatus.tick(battle.member(f.player));
                h.assertTrue(mount.getAirSupply()<5 && f.player.getAirSupply()==f.player.getMaxAirSupply(),"Carrier and rider breathing were not evaluated at their own eyes");
                battle.commitMovement(f.player,battle.validateDestination(f.player,water));
                battle.commitMovement(f.player,battle.validateDestination(f.player,start));
                h.assertTrue(mount.position().distanceToSqr(start)<1e-6 && battle.movementMode(f.player).equals("ground") && f.player.getVehicle()==mount,"Swimming carrier could not return to bank");
                h.assertTrue(Math.abs(battle.budget.remaining()-12)<1e-6 && battle.clock.time()==av && !battle.members.containsKey(mount.getUUID()),"Mounted swim reset budget, changed Manhattan cost/AV or added initiative actor");
            }finally{battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void mountedAquaticExecutionRechecksRiderHeadroom(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            for(int x=-1;x<=5;x++)for(int z=-1;z<=1;z++){
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-2,z)),Blocks.STONE.defaultBlockState(),2);
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-1,z)),Blocks.WATER.defaultBlockState(),2);
            }
            var mount=h.spawnWithNoFreeWill(EntityType.HORSE,new BlockPos(2,1,2));mount.setPos(start.add(0,-1,0));mount.setTamed(true);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            try{
                battle.budget=new TurnBudget(6);var destination=mount.position().add(3,0,0);var route=battle.validateDestination(f.player,destination);
                var obstacle=BlockPos.containing(destination.add(0,f.player.getY()-mount.getY()+f.player.getBbHeight()-.1,0));
                h.assertTrue(obstacle.getY()>=mount.getBoundingBox().maxY,"Fixture obstacle was not above carrier");
                h.getLevel().setBlock(obstacle,Blocks.STONE.defaultBlockState(),2);
                battle.commitMovement(f.player,route);
                h.assertTrue(mount.getX()<destination.x-.3 && !f.player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(obstacle)),"Mounted water execution ignored newly blocked rider headroom");
                h.assertTrue(f.player.getVehicle()==mount && Math.abs(battle.budget.remaining()-3)<1e-6,"Blocked mounted swim split rider or refunded committed route");
                h.assertTrue(!AquaticPath.canStep(f.player,f.player.position(),f.player.position().add(0,4,0)),"Mounted aquatic path allowed flight into open air");
            }finally{battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void riddenStriderUsesNativeLavaSurfaceAndReturnsToBank(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("mount fixture");var start=f.player.position();
            for(int x=1;x<=5;x++)for(int z=-1;z<=1;z++){
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-2,z)),Blocks.STONE.defaultBlockState(),2);
                h.getLevel().setBlock(BlockPos.containing(start.add(x,-1,z)),Blocks.LAVA.defaultBlockState(),2);
            }
            var mount=h.spawnWithNoFreeWill(EntityType.STRIDER,new BlockPos(2,1,2));mount.setPos(start);
            mount.equipSaddle(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE),null);
            f.player.getInventory().setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WARPED_FUNGUS_ON_A_STICK));
            f.player.startRiding(mount,true);mount.positionRider(f.player);
            var battle=new BattleSession(f.player,f.mob,CombatData.current,f.player);
            try{
                h.assertTrue(BattleRiding.vehicle(f.player)==mount,"Fixture did not establish controllable strider");
                battle.budget=new TurnBudget(12);var destination=start.add(3,-.5,0);
                var route=battle.validateDestination(f.player,destination);
                for(int i=1;i<route.samples().size();i++)h.assertTrue(route.samples().get(i).distanceToSqr(route.samples().get(i-1))>1e-12,"Route contained a stationary floating-point corner");
                h.assertTrue(mount.position().equals(start) && Math.abs(route.cost()-3)<1e-6,"Preview moved mount or charged vertical lava entry");
                double av=battle.clock.time();battle.beginMoveTo(f.player,destination);
                var movement=battle.motion;
                for(int tick=0;tick<200 && battle.motion!=null;tick++)battle.tickMotion();
                h.assertTrue(mount.position().distanceToSqr(destination)<1e-6,"Lava route stopped at sample "+movement.sample+": "+movement.route.samples().subList(Math.max(0,movement.sample-1),Math.min(movement.route.samples().size(),movement.sample+4))+", seat="+BattleRiding.seat(f.player));
                h.assertTrue(battle.motion==null && mount.position().distanceToSqr(destination)<1e-6 && TerrainPath.supportedAt(f.player,f.player.position()),"Strider could not finish on vanilla half-block lava support: mount="+mount.position()+", destination="+destination+", supported="+TerrainPath.supportedAt(f.player,f.player.position()));
                h.assertTrue(f.player.getVehicle()==mount && !battle.members.containsKey(mount.getUUID()) && battle.clock.time()==av,"Riding/initiative/AV changed while crossing lava");
                var back=battle.validateDestination(f.player,start);battle.commitMovement(f.player,back);
                h.assertTrue(mount.position().distanceToSqr(start)<1e-6 && Math.abs(battle.budget.remaining()-6)<1e-6,"Strider could not climb back to bank or spent wrong distance");
            }finally{battle.close("test");f.player.stopRiding();mount.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void nativeLavaSupportDoesNotEnableOrdinaryMobsOrSubmergedTravel(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();var lava=BlockPos.containing(start.add(0,-1,0));
            h.getLevel().setBlock(lava,Blocks.LAVA.defaultBlockState(),2);
            var strider=h.spawnWithNoFreeWill(EntityType.STRIDER,new BlockPos(8,1,7));strider.setPos(start.add(0,-.5,0));
            try{
                h.assertTrue(TerrainPath.clearGroundSample(strider,strider.position()) && TerrainPath.supportedAt(strider,strider.position()),"Native strider surface not supported");
                h.assertTrue(!TerrainPath.clearGroundSample(f.mob,strider.position()) && !TerrainPath.supportedAt(f.mob,strider.position()),"Ordinary mob gained lava walking");
                h.assertTrue(!TerrainPath.clearGroundSample(strider,strider.position().add(0,-.1,0)),"Strider could walk below lava support");
                h.getLevel().setBlock(lava.above(),Blocks.LAVA.defaultBlockState(),2);
                h.assertTrue(!TerrainPath.clearGroundSample(strider,strider.position()),"Strider could walk through a submerged lava layer");
            }finally{strider.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void tridentSpawnCallbackCannotReenterOrCommitAfterOwnershipChange(GameTestHelper h){
        for(int kind=0;kind<5;kind++)try(var f=new Fixture(h)){
            int mode=kind;var weapon=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
            f.player.getInventory().setItem(4,weapon);f.battle.budget.setActions(3,1);
            var originalBudget=f.battle.budget;var captured=new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.item.ItemEntity>();
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.EntityJoinLevelEvent> listener=event->{
                if(!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity drop) || !drop.getItem().is(net.minecraft.world.item.Items.TRIDENT))return;
                captured.set(drop);calls.incrementAndGet();
                if(mode==0){
                    try{f.battle.use(f.player,4,"mineturn:throw_trident",f.mob.getId());throw new AssertionError("Spawn callback recursively threw trident");}
                    catch(IllegalArgumentException expected){h.assertTrue(expected.getMessage().contains("正在结算"),"Wrong launch reentry refusal");}
                }
                if(mode==1)f.player.getInventory().setItem(4,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                if(mode==2)f.battle.leave(f.player,"spawn departure");
                if(mode==3)event.setCanceled(true);
                if(mode==4)throw new IllegalArgumentException("injected spawn failure");
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try{
                BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"use",4,"mineturn:throw_trident",f.mob.getId(),f.mob.position(),0));
            }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
            try{
                h.assertTrue(calls.get()==1 && captured.get()!=null,"Spawn callback did not run once");
                h.assertTrue(originalBudget.mainActions()==(mode==0?2:3) && weapon.getCount()==(mode==0?0:1),"Failed launch consumed weapon/action or recursion double spent");
                h.assertTrue(f.battle.closed==(mode==1 || mode==2 || mode==4) && !BattleManager.authorized(),"Failure cleanup or safe cancellation incorrect");
                h.assertTrue(captured.get().isAlive()==(mode==0) && f.battle.recoverableTridents.size()==(mode==0?1:0),"Failed launch retained duplicate drop or recovery entry");
                if(mode==1)h.assertTrue(f.player.getInventory().getItem(4).is(net.minecraft.world.item.Items.DIAMOND),"Launch overwrote callback replacement");
                if(mode==3)f.battle.requireTurn(f.player);
            }finally{if(captured.get()!=null)captured.get().discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void stackedTridentComponentLaunchTransfersOnlyOne(GameTestHelper h){
        try(var f=new Fixture(h)){
            var weapon=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT,2);
            weapon.remove(net.minecraft.core.component.DataComponents.MAX_DAMAGE);
            weapon.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE,16);
            f.player.getInventory().setItem(4,weapon);
            BattleTridents.launch(f.battle,f.player,weapon,f.player.position().add(1,0,0),4,100);
            var drop=f.battle.recoverableTridents.values().iterator().next().drop();
            try{h.assertTrue(weapon.getCount()==1 && drop.getItem().getCount()==1,"Stackable component copied entire trident stack");}
            finally{drop.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void manualTridentRecoveryTransfersOriginalAndCancelsLoyalty(GameTestHelper h){
        for(boolean loyal:new boolean[]{false,true})try(var f=new Fixture(h)){
            var weapon=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
            weapon.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("real recoverable"));
            if(loyal)weapon.enchant(h.getLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT).getHolderOrThrow(net.minecraft.world.item.enchantment.Enchantments.LOYALTY),1);
            f.player.getInventory().setItem(4,weapon);
            BattleTridents.launch(f.battle,f.player,weapon,f.player.position().add(1,0,0),4,100);
            var drop=f.battle.recoverableTridents.values().iterator().next().drop();var original=drop.getItem();
            h.assertTrue(weapon.isEmpty() && original.getDamageValue()==1,"Launch did not commit actual item and durability");
            var offer=f.battle.offers(f.player).stream().filter(o->o.id().equals("mineturn:recover_trident")).findFirst().orElseThrow();
            h.assertTrue(offer.unavailable().isEmpty(),"Nearby recovery offer unavailable");
            double av=f.battle.clock.time();var result=BattleManager.castGranted(f.player,"mineturn:recover_trident",f.player.getId());
            h.assertTrue(result.accepted() && f.player.getInventory().getItem(4)==original && original.getHoverName().getString().equals("real recoverable"),"Recovery copied item, lost components or ignored original slot");
            h.assertTrue(!drop.isAlive() && drop.getItem().isEmpty() && f.battle.recoverableTridents.isEmpty() && f.battle.returningTridents.isEmpty(),"Recovery left source item or loyalty return ticket");
            h.assertTrue(!f.battle.budget.canAct() && f.battle.clock.time()==av,"Recovery did not cost one action or advanced AV");
            f.battle.budget.setActions(1,1);
            h.assertTrue(!BattleManager.castGranted(f.player,"mineturn:recover_trident",f.player.getId()).accepted() && f.battle.budget.canAct(),"Repeated recovery copied item or charged failed action");
            BattleTridents.advance(f.battle);h.assertTrue(f.player.getInventory().getItem(4)==original && original.getCount()==1,"Loyalty duplicated manually recovered item");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void manualRecoveryRejectsFullBlockedDestroyedOrDistantDrops(GameTestHelper h){
        for(int kind=0;kind<4;kind++)try(var f=new Fixture(h)){
            var weapon=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
            f.player.getInventory().setItem(4,weapon);var origin=f.player.position();
            BattleTridents.launch(f.battle,f.player,weapon,origin.add(2,0,0),4,100);
            var drop=f.battle.recoverableTridents.values().iterator().next().drop();
            try{
                if(kind==0)for(int slot=0;slot<36;slot++)f.player.getInventory().setItem(slot,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE,64));
                if(kind==1)for(int y=0;y<3;y++)h.getLevel().setBlock(BlockPos.containing(origin.add(1,y,0)),Blocks.STONE.defaultBlockState(),2);
                if(kind==2)drop.discard();
                if(kind==3)drop.setPos(origin.add(7,0,0));
                var result=BattleManager.castGranted(f.player,"mineturn:recover_trident",f.player.getId());
                h.assertTrue(!result.accepted() && f.battle.budget.canAct() && !f.battle.closed,"Invalid recovery paid or closed battle");
                if(kind==0){
                    h.assertTrue(drop.isAlive() && drop.getItem().is(net.minecraft.world.item.Items.TRIDENT),"Full inventory destroyed source");
                    f.player.getInventory().setItem(12,net.minecraft.world.item.ItemStack.EMPTY);
                    h.assertTrue(BattleManager.castGranted(f.player,"mineturn:recover_trident",f.player.getId()).accepted() && f.player.getInventory().getItem(12).is(net.minecraft.world.item.Items.TRIDENT),"Recovery did not use free backpack slot");
                }
            }finally{drop.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void recoveryRejectsUnregisteredForeignAndPostBattleDrops(GameTestHelper h){
        try(var f=new Fixture(h);var other=new Fixture(h)){
            var weapon=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TRIDENT);
            f.player.getInventory().setItem(4,weapon);
            BattleTridents.launch(f.battle,f.player,weapon,f.player.position().add(1,0,0),4,100);
            var pending=f.battle.recoverableTridents.values().iterator().next();var drop=pending.drop();
            try{
                other.battle.recoverableTridents.put(drop.getUUID(),pending);other.battle.place(other.player,f.player.position());
                h.assertTrue(BattleTridents.nearest(other.battle,other.player,2.5)==null,"Another owner could recover trident");
                f.battle.recoverableTridents.clear();
                h.assertTrue(!BattleManager.hasRecoverableTrident(f.player),"Nearby unregistered drop counted as thrown weapon");
                f.battle.recoverableTridents.put(drop.getUUID(),pending);f.battle.close("test exit");
                h.assertTrue(drop.isAlive() && f.battle.recoverableTridents.isEmpty() && !BattleManager.hasRecoverableTrident(f.player),"Exit deleted non-loyalty drop or retained recovery action");
            }finally{drop.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void itemPreflightProtectsIdentityCountAndComponents(GameTestHelper h){
        for(int kind=0;kind<4;kind++)try(var f=new Fixture(h)){
            f.battle.close("replace definitions");int mode=kind;
            var id=net.minecraft.resources.ResourceLocation.parse("mineturn_test:item_check_"+UUID.randomUUID().toString().replace("-",""));
            var during=new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});var executed=new java.util.concurrent.atomic.AtomicInteger();
            com.matuvent.mineturn.api.CombatEffects.register(id,new com.matuvent.mineturn.api.CombatEffects.Effect(){
                public String validate(com.matuvent.mineturn.api.CombatEffects.Context context){during.get().run();return null;}
                public void execute(com.matuvent.mineturn.api.CombatEffects.Context context){executed.incrementAndGet();}
            });
            var data=CombatData.current;var actions=new HashMap<>(data.actions());
            actions.put(id.toString(),new CombatData.Action("test",id.toString(),0,32,false,0,0,new com.google.gson.JsonObject()));
            var battle=new BattleSession(f.player,f.mob,new CombatData.Snapshot(actions,data.mobs(),data.items(),data.sources()),f.player);
            try{
                var stack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK,3);
                stack.set(com.matuvent.mineturn.MineTurn.COMBAT.get(),new com.matuvent.mineturn.data.CombatItem(true,java.util.List.of(id.toString()),32));
                f.player.getInventory().setItem(4,stack);battle.budget.setActions(3,1);
                during.set(()->{
                    try{battle.action(f.player,"end",0,"",-1,0,0);throw new AssertionError("Preflight allowed end turn");}
                    catch(IllegalArgumentException expected){h.assertTrue(expected.getMessage().contains("正在结算"),"Wrong reentry rejection");}
                    if(mode==1)f.player.getInventory().setItem(4,stack.copy());
                    if(mode==2)stack.shrink(1);
                    if(mode==3)stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed"));
                });
                boolean rejected=false;
                try{battle.use(f.player,4,id.toString(),f.mob.getId());}
                catch(IllegalArgumentException expected){rejected=true;h.assertTrue(expected.getMessage().contains("物品已变化"),"Wrong item mutation rejection");}
                h.assertTrue(rejected==(mode!=0) && executed.get()==(mode==0?1:0) && battle.budget.mainActions()==(mode==0?2:3),"Changed item executed or charged action");
                battle.requireTurn(f.player);h.assertTrue(!battle.closed,"Preflight closed healthy battle");
            }finally{battle.close("test");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void rangedSettlementValidationCannotReenterOrUseDepartedTarget(GameTestHelper h){
        for(int kind=0;kind<3;kind++)try(var f=new Fixture(h)){
            f.battle.close("replace definitions");int mode=kind;
            var id=net.minecraft.resources.ResourceLocation.parse("mineturn_test:ranged_check_"+UUID.randomUUID().toString().replace("-",""));
            var during=new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});var executed=new java.util.concurrent.atomic.AtomicInteger();
            com.matuvent.mineturn.api.CombatEffects.register(id,new com.matuvent.mineturn.api.CombatEffects.Effect(){
                public String validate(com.matuvent.mineturn.api.CombatEffects.Context context){during.get().run();return null;}
                public void execute(com.matuvent.mineturn.api.CombatEffects.Context context){executed.incrementAndGet();}
            });
            var data=CombatData.current;var actions=new HashMap<>(data.actions());
            actions.put(id.toString(),new CombatData.Action("test",id.toString(),0,32,false,0,0,new com.google.gson.JsonObject(),new CombatData.Ranged(1000,.2,.1,.1,"minecraft:arrow",0)));
            var grant=new com.matuvent.mineturn.data.GrantedAction(id.toString(),"test","minecraft:stick",0,true,Map.of(),java.util.List.of(),java.util.List.of());
            var battle=new BattleSession(f.player,f.mob,new CombatData.Snapshot(actions,data.mobs(),data.items(),data.sources(),Map.of(id.toString(),grant)),f.player);
            try{
                battle.budget.setActions(3,1);battle.useGrant(f.player,id.toString(),f.mob.getId());var budget=battle.budget;
                during.set(()->{
                    var result=BattleManager.castGranted(f.player,id.toString(),f.mob.getId());
                    h.assertTrue(!result.accepted() && result.reason().contains("正在结算"),"Settlement validation allowed nested shot");
                    if(mode==1)f.mob.setPos(f.mob.position().add(1,0,0));
                    if(mode==2)battle.leave(f.mob,"settlement departure");
                });
                battle.finishShot(true);
                h.assertTrue(executed.get()==(mode==0?1:0) && budget.mainActions()==2 && battle.shot==null,"Settlement executed stale effect, refunded action or recreated shot");
                battle.finishShot(true);h.assertTrue(executed.get()==(mode==0?1:0),"Consumed shot settled twice");
                if(mode<2)battle.requireTurn(f.player);
                else h.assertTrue(battle.closed && !BattleManager.locked(f.player) && !BattleManager.locked(f.mob),"Departure left lock after settlement");
            }finally{battle.close("test");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void offerConditionsCannotRecursivelyCastOrBuildOffers(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.close("replace definitions");
            var id=net.minecraft.resources.ResourceLocation.parse("mineturn_test:offer_check_"+UUID.randomUUID().toString().replace("-",""));
            var during=new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});var checks=new java.util.concurrent.atomic.AtomicInteger();
            com.matuvent.mineturn.api.CombatConditions.register(id,player->{checks.incrementAndGet();during.get().run();return true;});
            var data=CombatData.current;
            var grant=new com.matuvent.mineturn.data.GrantedAction("mineturn:melee","test","minecraft:stick",0,true,Map.of(),java.util.List.of(),java.util.List.of(id.toString()));
            var battle=new BattleSession(f.player,f.mob,new CombatData.Snapshot(data.actions(),data.mobs(),data.items(),data.sources(),Map.of(id.toString(),grant)),f.player);
            try{
                battle.place(f.mob,f.player.position().add(1,0,0));long revision=battle.revision;float hp=f.mob.getHealth();
                during.set(()->{
                    h.assertTrue(battle.offers(f.player).isEmpty(),"Offer condition recursively built offers");
                    var result=BattleManager.castGranted(f.player,id.toString(),f.mob.getId());
                    h.assertTrue(!result.accepted() && result.reason().contains("正在结算"),"Offer condition executed grant");
                });
                var offers=battle.offers(f.player);
                h.assertTrue(offers.size()==1 && checks.get()==2 && f.mob.getHealth()==hp && battle.budget.canAct() && battle.revision==revision,"Offer query had combat side effects or duplicate checks");
                battle.requireTurn(f.player);
            }finally{battle.close("test");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void grantValidationCannotReenterOrSpendAfterStateChange(GameTestHelper h){
        for(int kind=0;kind<4;kind++)try(var f=new Fixture(h)){
            f.battle.close("replace definitions");int mode=kind;
            var id=net.minecraft.resources.ResourceLocation.parse("mineturn_test:preflight_"+UUID.randomUUID().toString().replace("-",""));
            var checks=new java.util.concurrent.atomic.AtomicInteger();var effects=new java.util.concurrent.atomic.AtomicInteger();
            var during=new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
            com.matuvent.mineturn.api.CombatEffects.register(id,new com.matuvent.mineturn.api.CombatEffects.Effect(){
                public String validate(com.matuvent.mineturn.api.CombatEffects.Context context){checks.incrementAndGet();during.get().run();return mode==1?"injected refusal":null;}
                public void execute(com.matuvent.mineturn.api.CombatEffects.Context context){effects.incrementAndGet();}
            });
            var data=CombatData.current;var actions=new HashMap<>(data.actions());
            actions.put(id.toString(),new CombatData.Action("test",id.toString(),0,32,false,0,0,new com.google.gson.JsonObject()));
            var grant=new com.matuvent.mineturn.data.GrantedAction(id.toString(),"test","minecraft:stick",0,true,Map.of(),java.util.List.of(),java.util.List.of(),new com.matuvent.mineturn.api.CombatResources.Cost("mineturn:experience_levels",1));
            var battle=new BattleSession(f.player,f.mob,new CombatData.Snapshot(actions,data.mobs(),data.items(),data.sources(),Map.of(id.toString(),grant)),f.player);
            try{
                f.player.experienceLevel=5;battle.budget.setActions(3,1);var budget=battle.budget;double av=battle.clock.time();
                during.set(()->{
                    var nested=BattleManager.castGranted(f.player,id.toString(),f.mob.getId());
                    h.assertTrue(!nested.accepted() && nested.reason().contains("正在结算"),"Validation recursively cast action");
                    if(mode==2)f.mob.setPos(f.mob.position().add(1,0,0));
                    if(mode==3)battle.leave(f.player,"preflight departure");
                });
                var result=BattleManager.castGranted(f.player,id.toString(),f.mob.getId());
                h.assertTrue(result.accepted()==(mode==0) && effects.get()==(mode==0?1:0),"Stale or rejected preflight still executed");
                h.assertTrue(f.player.experienceLevel==(mode==0?4:5) && budget.mainActions()==(mode==0?2:3) && budget.bonusActions()==1 && battle.clock.time()==av,"Rejected preflight spent resources/actions or advanced AV");
                h.assertTrue(battle.closed==(mode==3) && !BattleManager.authorized(),"Preflight refusal closed healthy battle or leaked authority");
                if(mode!=3){battle.requireTurn(f.player);h.assertTrue(BattleManager.locked(f.player),"Guard did not restore or player lost membership");}
                else h.assertTrue(!BattleManager.locked(f.player) && !BattleManager.locked(f.mob),"Departure retained locks");
                h.assertTrue(checks.get()>=1,"Preflight callback did not execute");
            }finally{battle.close("test");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void resourcePaymentRejectsReentryAndStopsAfterFailureOrDeparture(GameTestHelper h){
        for(int kind=0;kind<5;kind++)try(var f=new Fixture(h)){
            f.battle.close("replace definitions");int mode=kind;
            var id=net.minecraft.resources.ResourceLocation.parse("mineturn_test:payment_"+UUID.randomUUID().toString().replace("-",""));
            var calls=new java.util.concurrent.atomic.AtomicInteger();var effects=new java.util.concurrent.atomic.AtomicInteger();
            var during=new java.util.concurrent.atomic.AtomicReference<Runnable>(() -> {});
            com.matuvent.mineturn.api.CombatResources.register(id,new com.matuvent.mineturn.api.CombatResources.Resource(){
                public String check(ServerPlayer player,double amount){return null;}
                public boolean trySpend(ServerPlayer player,double amount){
                    calls.incrementAndGet();if(mode==1)return false;
                    player.experienceLevel--;during.get().run();
                    if(mode==2)throw new IllegalArgumentException("injected payment failure");
                    if(mode==3)throw new IllegalStateException("injected payment failure");
                    return true;
                }
            });
            com.matuvent.mineturn.api.CombatEffects.register(id,context->effects.incrementAndGet());
            var data=CombatData.current;var actions=new HashMap<>(data.actions());
            actions.put(id.toString(),new CombatData.Action("test",id.toString(),0,32,false,0,0,new com.google.gson.JsonObject()));
            var grant=new com.matuvent.mineturn.data.GrantedAction(id.toString(),"test","minecraft:stick",0,true,Map.of(),java.util.List.of(),java.util.List.of(),new com.matuvent.mineturn.api.CombatResources.Cost(id.toString(),1));
            var battle=new BattleSession(f.player,f.mob,new CombatData.Snapshot(actions,data.mobs(),data.items(),data.sources(),Map.of(id.toString(),grant)),f.player);
            try{
                f.player.experienceLevel=5;battle.budget.setActions(3,1);var budget=battle.budget;double av=battle.clock.time();
                during.set(()->{
                    if(mode==4){battle.leave(f.player,"payment departure");return;}
                    var nested=BattleManager.castGranted(f.player,id.toString(),f.mob.getId());
                    h.assertTrue(!nested.accepted() && nested.reason().contains("正在结算"),"Payment allowed recursive casting");
                    BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(battle.id,battle.revision,"end",0,"",f.mob.getId(),f.player.position(),0));
                });
                var result=BattleManager.castGranted(f.player,id.toString(),f.mob.getId());
                h.assertTrue(result.accepted()==(mode==0) && calls.get()==1 && effects.get()==(mode==0?1:0),"Payment executed twice or effect continued after failure");
                h.assertTrue(f.player.experienceLevel==(mode==1?5:4) && budget.mainActions()==(mode==0?2:3) && budget.bonusActions()==1 && battle.clock.time()==av,"Payment resources, action budget or AV incorrect");
                h.assertTrue(battle.closed==(mode>=2) && BattleManager.locked(f.player)==(mode<2) && BattleManager.locked(f.mob)==(mode<2) && !BattleManager.authorized(),"Payment cleanup retained lock or authority");
                if(mode<2){battle.requireTurn(f.player);h.assertTrue(battle.actor==f.player,"Payment reentry advanced turn");}
                else h.assertTrue(!BattleManager.castGranted(f.player,id.toString(),f.mob.getId()).accepted() && calls.get()==1,"Failed payment retried after closure");
            }finally{battle.close("test");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void grantedCastingFailureReleasesBattleAndDoesNotRetry(GameTestHelper h){
        for(int kind=0;kind<3;kind++)try(var f=new Fixture(h)){
            f.battle.close("replace test definitions");
            int failure=kind;var calls=new java.util.concurrent.atomic.AtomicInteger();
            var id=net.minecraft.resources.ResourceLocation.parse("mineturn_test:cast_failure_"+UUID.randomUUID().toString().replace("-",""));
            com.matuvent.mineturn.api.CombatEffects.register(id,context->{
                calls.incrementAndGet();context.battle().hurt(context.target(),2);
                if(failure==0)throw new IllegalArgumentException("injected paid grant failure");
                if(failure==1)throw new IllegalStateException("injected paid grant failure");
                throw new RuntimeException("injected paid grant failure");
            });
            var data=CombatData.current;var actions=new HashMap<>(data.actions());
            actions.put(id.toString(),new CombatData.Action("test",id.toString(),0,32,false,0,0,new com.google.gson.JsonObject()));
            var grant=new com.matuvent.mineturn.data.GrantedAction(id.toString(),"test","minecraft:stick",0,true,Map.of(),java.util.List.of(),java.util.List.of());
            var battle=new BattleSession(f.player,f.mob,new CombatData.Snapshot(actions,data.mobs(),data.items(),data.sources(),Map.of(id.toString(),grant)),f.player);
            try{
                var invalid=BattleManager.castGranted(f.player,"mineturn_test:absent",f.mob.getId());
                h.assertTrue(!invalid.accepted() && !battle.closed && battle.budget.canAct() && calls.get()==0,"Grant preflight failure consumed or closed battle");
                float hp=f.mob.getHealth();double av=battle.clock.time();
                var result=com.matuvent.mineturn.api.CombatCasting.cast(f.player,id,f.mob.getId());
                h.assertTrue(!result.accepted() && !result.reason().isBlank() && calls.get()==1,"Paid casting failure escaped or reported success");
                h.assertTrue(battle.closed && !BattleManager.locked(f.player) && !BattleManager.locked(f.mob) && battle.motion==null && battle.shot==null,"Casting failure retained battle locks/state");
                h.assertTrue(!battle.budget.canAct() && battle.clock.time()==av && f.mob.getHealth()<hp && !BattleManager.authorized(),"Failure refunded action, rewound damage or leaked authority");
                h.assertTrue(!com.matuvent.mineturn.api.CombatCasting.cast(f.player,id,f.mob.getId()).accepted() && calls.get()==1,"Closed casting failure could be retried");
            }finally{battle.close("test");}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void nativeAttackCallbackDepartureStopsDamage(GameTestHelper h){
        for(boolean sourceLeaves:new boolean[]{true,false})try(var f=new Fixture(h)){
            var extra=EntityType.PILLAGER.create(h.getLevel());
            extra.setPos(f.mob.position().add(0,0,2));h.getLevel().addFreshEntity(extra);f.battle.add(extra);
            try{
                f.battle.place(f.mob,f.player.position().add(1,0,0));
                var sword=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
                f.player.getInventory().setItem(4,sword);f.player.getInventory().selected=0;
                float hp=f.mob.getHealth();double av=f.battle.clock.time();var budget=f.battle.budget;
                long sequence=f.battle.actionSequence;f.player.swinging=false;
                var calls=new java.util.concurrent.atomic.AtomicInteger();
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.player.AttackEntityEvent> listener=event->{
                    if(event.getEntity()!=f.player)return;
                    calls.incrementAndGet();f.battle.leave(sourceLeaves?f.player:f.mob,"test native attack departure");
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                try{
                    BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"use",4,"mineturn:melee",f.mob.getId(),f.mob.position(),0));
                }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
                h.assertTrue(calls.get()==1 && f.mob.getHealth()==hp && sword.getDamageValue()==0,"Native attack continued after participant departure");
                h.assertTrue(!budget.canAct() && f.battle.clock.time()==av && f.player.getInventory().selected==0 && !BattleManager.authorized(),"Departure refunded action, advanced AV or leaked selected slot/authority");
                h.assertTrue(!BattleManager.locked(sourceLeaves?f.player:f.mob),"Departed participant retained battle lock");
                if(!sourceLeaves)h.assertTrue(!f.battle.closed && BattleManager.locked(f.player) && BattleManager.locked(extra),"Target departure unnecessarily closed remaining battle");
                h.assertTrue(f.battle.motion==null && f.battle.shot==null,"Departure left pending action state");
                h.assertTrue(!f.player.swinging && f.battle.actionSequence==sequence,"Departed action still emitted swing or action performance");
            }finally{extra.discard();}
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void drinkRemainderFollowsMovedStackWithoutOverwritingCallbackReplacement(GameTestHelper h){
        for(boolean moved:new boolean[]{true,false})try(var f=new Fixture(h)){
            var milk=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET);
            var replacement=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3);
            f.player.getInventory().setItem(4,milk);f.player.getInventory().selected=0;
            f.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,200));
            double av=f.battle.clock.time();var calls=new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove> listener=event->{
                if(event.getEntity()!=f.player)return;
                calls.incrementAndGet();f.player.getInventory().setItem(4,replacement);
                if(moved)f.player.getInventory().setItem(12,milk);
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try{
                BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"use",4,"mineturn:drink",f.player.getId(),f.player.position(),0));
            }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
            h.assertTrue(calls.get()==1 && f.player.getInventory().getItem(4)==replacement && replacement.getCount()==3,"Drink overwrote callback replacement");
            int buckets=0;
            for(int slot=0;slot<f.player.getInventory().getContainerSize();slot++){
                var stack=f.player.getInventory().getItem(slot);
                if(stack.is(net.minecraft.world.item.Items.BUCKET))buckets+=stack.getCount();
            }
            h.assertTrue(buckets==(moved?1:0) && (!moved || f.player.getInventory().getItem(12).is(net.minecraft.world.item.Items.BUCKET)),"Remainder did not follow original stack or was manufactured after ownership loss");
            h.assertTrue(f.battle.closed==!moved && BattleManager.locked(f.player)==moved && BattleManager.locked(f.mob)==moved,"Ownership loss did not safely close battle");
            h.assertTrue(milk.isEmpty() && !f.battle.budget.canAct() && f.battle.clock.time()==av && f.player.getInventory().selected==0 && !BattleManager.authorized(),"Consumption, action cost or cleanup incorrect");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void itemCallbackCannotReenterPlayerActionsWithExtraActions(GameTestHelper h){
        try(var f=new Fixture(h)){
            f.battle.budget.setActions(3,1);
            f.player.getInventory().setItem(4,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET));
            f.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,200));
            double av=f.battle.clock.time(),distance=f.battle.budget.remaining();long revision=f.battle.revision;
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove> listener=event->{
                if(event.getEntity()!=f.player || calls.incrementAndGet()!=1)return;
                for(var operation:java.util.List.of("use","sprint","end","move"))
                    BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,operation,4,"mineturn:drink",f.player.getId(),f.player.position().add(1,0,0),0));
                var cast=BattleManager.castGranted(f.player,"mineturn_test:missing",f.player.getId());
                h.assertTrue(!cast.accepted() && cast.reason().contains("正在结算"),"Granted casting did not reject reentry before grant lookup");
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try{
                BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,revision,"use",4,"mineturn:drink",f.player.getId(),f.player.position(),0));
            }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
            h.assertTrue(calls.get()==1 && !f.battle.closed && f.battle.actor==f.player,"Nested action ran or ended outer turn");
            h.assertTrue(f.battle.budget.mainActions()==2 && f.battle.budget.bonusActions()==1 && f.battle.budget.remaining()==distance,"Nested action spent extra resources");
            h.assertTrue(f.battle.clock.time()==av && f.battle.revision==revision+1 && f.battle.motion==null && f.battle.shot==null,"Nested action advanced battle state");
            h.assertTrue(f.player.getInventory().getItem(4).is(net.minecraft.world.item.Items.BUCKET),"Outer drink failed to finish normally");
            f.player.getInventory().setItem(4,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET));
            f.battle.member(f.player).cooldowns.clear();f.battle.use(f.player,4,"mineturn:drink",f.player.getId());
            h.assertTrue(f.battle.budget.mainActions()==1 && !BattleManager.authorized(),"Reentry guard remained locked after outer effect returned");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void paidDrinkCallbackFailureClosesBattleAndRestoresSelection(GameTestHelper h){
        for(int kind=0;kind<2;kind++)try(var f=new Fixture(h)){
            var milk=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET);
            f.player.getInventory().setItem(4,milk);f.player.getInventory().selected=0;
            f.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,200));
            long revision=f.battle.revision;double av=f.battle.clock.time();
            BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,revision,"use",9,"mineturn:drink",f.player.getId(),f.player.position(),0));
            h.assertTrue(!f.battle.closed && f.battle.budget.canAct() && milk.getCount()==1,"Preflight rejection incorrectly closed battle or consumed resources");
            int failureKind=kind;var calls=new java.util.concurrent.atomic.AtomicInteger();
            java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove> listener=event->{
                if(event.getEntity()==f.player){
                    calls.incrementAndGet();f.player.getInventory().selected=2;
                    if(failureKind==0)throw new IllegalArgumentException("injected paid drink failure");
                    throw new IllegalStateException("injected paid drink failure");
                }
            };
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
            try{
                var request=new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,f.battle.revision,"use",4,"mineturn:drink",f.player.getId(),f.player.position(),0);
                BattleManager.request(f.player,request);
                h.assertTrue(f.battle.closed && !BattleManager.locked(f.player) && !BattleManager.locked(f.mob),"Paid callback failure was treated as a recoverable preflight rejection");
                h.assertTrue(f.player.getInventory().selected==0 && f.player.getInventory().getItem(4)==milk && milk.getCount()==1,"Failed native callback lost selection or manufactured a remainder");
                h.assertTrue(!f.battle.budget.canAct() && f.battle.clock.time()==av && !BattleManager.authorized(),"Failure refunded paid action, advanced AV or leaked execution authority");
                BattleManager.request(f.player,request);
                h.assertTrue(calls.get()==1 && milk.getCount()==1,"Repeated request executed after failure cleanup");
            }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
        }
        h.succeed();
    }
    @GameTest(template="empty")
    public static void milkCallbacksCannotRedirectRemainderAndCancellationKeepsNativeCost(GameTestHelper h){
        try(var f=new Fixture(h)){
            for(boolean canceled:new boolean[]{false,true}){
                var milk=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET);
                var protectedStack=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,3);
                f.player.getInventory().setItem(4,milk);f.player.getInventory().setItem(2,protectedStack);f.player.getInventory().selected=0;
                var effect=net.minecraft.world.effect.MobEffects.POISON;
                f.player.addEffect(new net.minecraft.world.effect.MobEffectInstance(effect,200));
                var calls=new java.util.concurrent.atomic.AtomicInteger();
                java.util.function.Consumer<net.neoforged.neoforge.event.entity.living.MobEffectEvent.Remove> listener=event->{
                    if(event.getEntity()==f.player && event.getEffect().equals(effect)){
                        calls.incrementAndGet();f.player.getInventory().selected=2;
                        if(canceled)event.setCanceled(true);
                    }
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                try{
                    f.battle.budget=new TurnBudget(4);f.battle.member(f.player).cooldowns.clear();
                    double av=f.battle.clock.time();f.battle.use(f.player,4,"mineturn:drink",f.player.getId());
                    h.assertTrue(calls.get()==1,"Milk did not invoke native removal callback exactly once");
                    h.assertTrue(f.player.getInventory().getItem(2)==protectedStack && protectedStack.getCount()==3,"Callback selection change overwrote unrelated stack");
                    h.assertTrue(f.player.getInventory().getItem(4).is(net.minecraft.world.item.Items.BUCKET) && f.player.getInventory().getItem(4).getCount()==1 && milk.isEmpty(),"Milk remainder did not replace exactly one used item in its original slot");
                    h.assertTrue(f.player.getInventory().selected==0 && f.player.hasEffect(effect)==canceled,"Selection restore or removal cancellation ignored");
                    h.assertTrue(!f.battle.budget.canAct() && f.battle.clock.time()==av,"Canceled removal refunded action or advanced AV");
                }finally{net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
                f.player.removeEffect(effect);
            }
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void shorePursuitPrefixCanEndInWaterWithoutSolidFloor(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();
            for(int x=-5;x<=-1;x++)for(int z=-2;z<=2;z++)for(int y=-2;y<=0;y++)
                h.getLevel().setBlock(BlockPos.containing(start.add(x,y,z)),Blocks.WATER.defaultBlockState(),2);
            var route=GroundRoutes.pursue(f.mob,start.add(-6,0,0),2,()->p->true);
            h.assertTrue(route!=null && route.cost()>1.9 && route.cost()<=2.000001,"Water prefix stopped on shore despite affordable swimming");
            h.assertTrue(AquaticPath.immersed(h.getLevel(),route.destination()) && !TerrainPath.supportedAt(f.mob,route.destination()),"Fixture failed to end in unsupported water");
            double av=f.battle.clock.time();float hp=f.mob.getHealth();
            f.battle.budget=new TurnBudget(2);f.battle.commitMovement(f.mob,route);
            h.assertTrue(f.mob.position().distanceToSqr(route.destination())<1e-8 && f.mob.getHealth()==hp && f.battle.clock.time()==av,"Water prefix execution changed destination, fall damage or AV");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void swimmingGroundMobPursuesUpOneBlockBank(GameTestHelper h){
        try(var f=new Fixture(h,true)){
            var start=f.mob.position();
            for(int x=-2;x<=1;x++)for(int z=-3;z<=3;z++)for(int y=-2;y<=0;y++)
                h.getLevel().setBlock(BlockPos.containing(start.add(x,y,z)),Blocks.WATER.defaultBlockState(),2);
            for(int x=-6;x<=-3;x++)for(int z=-3;z<=3;z++)
                h.getLevel().setBlock(BlockPos.containing(start.add(x,0,z)),Blocks.STONE.defaultBlockState(),2);
            f.battle.place(f.player,start.add(-5,1,0));f.battle.budget=new TurnBudget(8);
            h.assertTrue(f.battle.movementMode(f.mob).equals("aquatic"),"Ground mob did not switch into aquatic mode");
            var route=f.battle.pursuitRoute(f.mob,f.player);
            h.assertTrue(route!=null && route.destination().y>start.y+0.9 && route.cost()<=8,"Swimming ground mob could not plan one-block bank ascent");
            double av=f.battle.clock.time();float hp=f.mob.getHealth();
            f.battle.commitMovement(f.mob,route);
            h.assertTrue(f.mob.position().distanceToSqr(route.destination())<1e-8 && f.battle.movementMode(f.mob).equals("ground"),"Mob did not finish ascent and switch back to ground movement");
            h.assertTrue(f.mob.getHealth()==hp && f.battle.clock.time()==av,"Bank ascent damaged mob or advanced AV");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void stalePreviewDoesNotStarveCurrentRequestOrOtherMember(GameTestHelper h){
        try(var f=new Fixture(h)){
            var member=f.battle.member(f.player);var other=f.battle.member(f.mob);
            var destination=f.player.position().add(1,0,0);
            long revision=f.battle.revision;double av=f.battle.clock.time(),budget=f.battle.budget.remaining();
            BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,revision-1,"potion_preview",0,"",-1,destination,1));
            h.assertTrue(member.stalePreviewTick==f.battle.ticks && member.previewTick!=f.battle.ticks,"Stale request consumed current preview quota");
            BattleManager.request(f.player,new com.matuvent.mineturn.network.BattleNetwork.Request(f.battle.id,revision,"preview",0,"",-1,destination,2));
            h.assertTrue(member.previewTick==f.battle.ticks,"Current request was suppressed after stale preview");
            h.assertTrue(!BattleManager.acceptPreview(member,f.battle.ticks,true) && !BattleManager.acceptPreview(member,f.battle.ticks,false),"Same-tick duplicate bypassed quota");
            h.assertTrue(BattleManager.acceptPreview(other,f.battle.ticks,true),"One member exhausted another member's quota");
            f.battle.ticks++;
            h.assertTrue(BattleManager.acceptPreview(member,f.battle.ticks,true) && BattleManager.acceptPreview(member,f.battle.ticks,false),"Quota did not refresh next tick");
            h.assertTrue(f.battle.clock.time()==av && f.battle.budget.remaining()==budget && f.battle.revision==revision && f.battle.motion==null,"Preview spent resources, advanced time or started movement");
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void groundParentHistoryKeepsDropsAndAffordablePrefix(GameTestHelper h){
        try(var f=new Fixture(h)){
            var base=f.mob.position();
            h.getLevel().setBlock(BlockPos.containing(base),Blocks.STONE.defaultBlockState(),2);
            f.battle.place(f.mob,base.add(0,1,0));
            var start=f.mob.position();var goal=base.add(-4,0,0);
            for(int y=0;y<4;y++)for(int z=-1;z<=1;z++)
                h.getLevel().setBlock(BlockPos.containing(base.add(-2,y,z)),Blocks.STONE.defaultBlockState(),2);
            java.util.function.Supplier<java.util.function.Predicate<net.minecraft.world.phys.Vec3>> controls=()->new java.util.function.Predicate<>(){
                net.minecraft.world.phys.Vec3 previous=start;
                public boolean test(net.minecraft.world.phys.Vec3 point){
                    boolean continuous=point.distanceTo(previous)<=0.100001;previous=point;return continuous;
                }
            };
            var stats=new RouteSearchStats();
            var route=GroundRoutes.find(f.mob,goal,10,controls,stats);
            h.assertTrue(route!=null && route.destination().distanceToSqr(goal)<0.0025,"Ground parent replay lost wall detour");
            h.assertTrue(!route.landings().isEmpty() && stats.avoidedPrefixCopies>0,"Ground history omitted drop or kept copying all prefixes");
            var previous=start;double cost=0;int landing=0;
            for(var point:route.samples()){
                h.assertTrue(point.distanceTo(previous)<=0.100001,"Ground history introduced a gap");
                cost+=MovementDistance.ground(point.subtract(previous));previous=point;
                if(landing<route.landings().size() && point.distanceToSqr(route.landings().get(landing).position())<1e-8)landing++;
            }
            h.assertTrue(landing==route.landings().size() && Math.abs(cost-route.cost())<1e-6 && previous.distanceToSqr(goal)<0.0025,"Ground reconstruction changed landing order, cost or endpoint");
            var prefix=GroundRoutes.pursue(f.mob,goal,2,controls);
            h.assertTrue(prefix!=null && prefix.cost()<=2.000001 && TerrainPath.supportedAt(f.mob,prefix.destination()),"Pursuit prefix lost budget or support");
            h.assertTrue(!prefix.landings().isEmpty() && prefix.landings().stream().allMatch(drop->prefix.samples().stream().anyMatch(p->p.distanceToSqr(drop.position())<1e-8)),"Pursuit prefix lost its completed fall or included future landings");
            h.assertTrue(f.mob.position().equals(start),"Ground history query moved entity");
            com.matuvent.mineturn.MineTurn.LOGGER.info("Ground parent history: {}",stats);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void spatialHistoryReplaysInOrderAndMaterializesCompleteRoute(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();var goal=start.add(-4,0,0);
            for(int y=0;y<3;y++)for(int z=-3;z<=3;z++)
                h.getLevel().setBlock(BlockPos.containing(start.add(-2,y,z)),Blocks.STONE.defaultBlockState(),2);
            var stats=new RouteSearchStats();
            var route=SpatialRoutes.find(f.mob,goal,12,"flying",()->new java.util.function.Predicate<net.minecraft.world.phys.Vec3>(){
                net.minecraft.world.phys.Vec3 previous=start;
                public boolean test(net.minecraft.world.phys.Vec3 point){
                    boolean continuous=point.distanceTo(previous)<=0.100001;
                    previous=point;return continuous;
                }
            },stats);
            h.assertTrue(route!=null && route.destination().distanceToSqr(goal)<1e-8 && route.cost()>4,"Parent history broke stateful continuous control replay");
            var previous=start;double cost=0;
            for(var point:route.samples()){
                h.assertTrue(point.distanceTo(previous)<=0.100001 && SpatialPath.canStep(f.mob,previous,point,false),"Materialized route has missing prefix or invalid segment");
                cost+=MovementDistance.between(previous,point);previous=point;
            }
            h.assertTrue(previous.distanceToSqr(goal)<1e-8 && Math.abs(cost-route.cost())<1e-6,"Materialized path lost endpoint or cost");
            h.assertTrue(stats.storedSamples>0 && stats.avoidedPrefixCopies>stats.storedSamples,"Branching search did not reduce prefix storage");
            h.assertTrue(f.mob.position().equals(start),"History reconstruction moved the entity");
            com.matuvent.mineturn.MineTurn.LOGGER.info("Spatial parent history: {}",stats);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void groundSearchMeasuresDetourAndPrunesUnpayableEdges(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();var goal=start.add(-4,0,0);
            for(int y=0;y<3;y++)for(int z=-1;z<=1;z++)
                h.getLevel().setBlock(BlockPos.containing(start.add(-2,y,z)),Blocks.STONE.defaultBlockState(),2);
            var stats=new RouteSearchStats();
            var route=GroundRoutes.find(f.mob,goal,10,()->p->true,stats);
            h.assertTrue(route!=null && route.destination().distanceToSqr(goal)<0.0025 && route.cost()>4 && route.cost()<=10,"Ground search lost affordable wall detour");
            h.assertTrue(stats.budgetPruned>0 && stats.expanded>1 && stats.edges>0 && stats.replayedSamples>0,"Search diagnostics did not measure real branching/history");
            h.assertTrue(stats.polled<=512 && stats.expanded<=stats.polled && stats.peakOpen<=stats.queued,"Ground diagnostics violated search bound");
            var denied=new RouteSearchStats();
            h.assertTrue(GroundRoutes.find(f.mob,goal,4,()->p->true,denied)==null && denied.budgetPruned>0,"Budget pruning accepted an unaffordable detour");
            h.assertTrue(f.mob.position().equals(start),"Search moved entity during preview");
            com.matuvent.mineturn.MineTurn.LOGGER.info("Ground detour search: {}; insufficient budget: {}",stats,denied);
        }h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void spatialSearchHasMeasuredHardLimitAndFreshCounters(GameTestHelper h){
        try(var f=new Fixture(h)){
            var pos=h.absolutePos(new BlockPos(24,8,24));
            f.mob.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
            var start=f.mob.position();var goal=start.add(6,0,0);var stats=new RouteSearchStats();
            var route=SpatialRoutes.find(f.mob,goal,40,"flying",()->p->p.distanceToSqr(goal)>=1,stats);
            h.assertTrue(route==null && stats.capped && stats.polled==512,"Unreachable spatial search escaped its hard limit");
            h.assertTrue(stats.expanded<=stats.polled && stats.peakOpen<=stats.queued && stats.edges>0,"Spatial search counters invalid");
            var clear=new RouteSearchStats();
            var direct=SpatialRoutes.find(f.mob,goal,6,"flying",()->p->true,clear);
            h.assertTrue(direct!=null && direct.cost()==6 && clear.polled==1 && !clear.capped,"Counters or failure leaked between previews");
            h.assertTrue(f.mob.position().equals(start),"Spatial preview moved entity");
            com.matuvent.mineturn.MineTurn.LOGGER.info("Unreachable spatial search: {}; direct query: {}",stats,clear);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void groundSamplesOmitStationaryCornersWithoutChangingCost(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();
            for(var offset:java.util.List.of(new net.minecraft.world.phys.Vec3(2,0,0),new net.minecraft.world.phys.Vec3(-2,0,1))){
                var route=TerrainPath.trace(f.mob,offset,p->true);
                h.assertTrue(route.destination().distanceToSqr(start.add(offset))<1e-8,"Sampling optimization changed destination");
                h.assertTrue(Math.abs(route.cost()-Math.abs(offset.x)-Math.abs(offset.z))<1e-6,"Sampling optimization changed Manhattan cost");
                var previous=start;
                for(var point:route.samples()){
                    h.assertTrue(!point.equals(previous) && point.distanceTo(previous)<=0.100001,"Stationary corner or unswept gap in ground route");
                    previous=point;
                }
                h.assertTrue(previous.equals(route.destination()) && route.landings().isEmpty(),"Flat route lost endpoint or gained a landing");
            }
            var refused=TerrainPath.trace(f.mob,new net.minecraft.world.phys.Vec3(2,0,0),p->false);
            h.assertTrue(refused.cost()==0 && refused.samples().isEmpty(),"Removing duplicates bypassed movement control");
            h.assertTrue(f.mob.position().equals(start),"Pure route query moved its entity");
        }h.succeed();
    }
    private static void waterHitsBlock(GameTestHelper h,net.minecraft.world.entity.LivingEntity owner,BlockPos pos){
        class WaterPotion extends net.minecraft.world.entity.projectile.ThrownPotion {
            WaterPotion(){super(EntityType.POTION,h.getLevel());}
            void burst(){super.onHit(new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),net.minecraft.core.Direction.UP,pos,false));}
        }
        var potion=new WaterPotion();potion.setOwner(owner);potion.setPos(net.minecraft.world.phys.Vec3.atCenterOf(pos));
        potion.setItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(net.minecraft.world.item.Items.SPLASH_POTION,net.minecraft.world.item.alchemy.Potions.WATER));
        potion.burst();
    }


    @GameTest(template="empty")
    public static void waterBlockEffectsRejectLockedOwnerAndResumeAfterExit(GameTestHelper h){
        try(var f=new Fixture(h)){
            var pos=f.mob.blockPosition();
            var states=java.util.List.of(Blocks.FIRE.defaultBlockState(),Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.CandleBlock.LIT,true),Blocks.CAMPFIRE.defaultBlockState());
            double av=f.battle.clock.time(),budget=f.battle.budget.remaining();
            for(var state:states){
                h.getLevel().setBlock(pos,state,2);
                waterHitsBlock(h,f.player,pos);
                h.assertTrue(h.getLevel().getBlockState(pos).equals(state),"Locked owner's water changed fire/candle/campfire");
                waterHitsBlock(h,null,pos);
                var result=h.getLevel().getBlockState(pos);
                h.assertTrue(state.is(Blocks.FIRE)?result.isAir():!result.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT),"Unowned water lost native block extinguishing");
            }
            h.assertTrue(f.battle.clock.time()==av && f.battle.budget.remaining()==budget,"Native block effect charged battle resources");
            f.battle.close("test");
            h.getLevel().setBlock(pos,Blocks.CAMPFIRE.defaultBlockState(),2);
            waterHitsBlock(h,f.player,pos);
            h.assertTrue(!h.getLevel().getBlockState(pos).getValue(CampfireBlock.LIT),"Owner's water did not resume after leaving battle");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void outsideWaterExtinguishesCampfireBeforeNextAvContact(GameTestHelper h){
        try(var f=new Fixture(h)){
            var pos=f.mob.blockPosition();var member=f.battle.member(f.mob);
            h.getLevel().setBlock(pos,Blocks.CAMPFIRE.defaultBlockState(),2);
            member.statusTicks=9;BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Lit campfire fixture caused no contact damage");
            waterHitsBlock(h,null,pos);
            for(int i=0;i<10;i++)BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Extinguished campfire kept applying stale AV damage");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void displacementChecksIntermediateBerryContact(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();
            h.getLevel().setBlock(BlockPos.containing(start.add(1,0,0)),Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE,2),2);
            var route=BattleDisplacement.trace(f.battle,f.mob,new net.minecraft.world.phys.Vec3(2,0,0),false,p->true);
            double av=f.battle.clock.time(),budget=f.battle.budget.remaining();
            BattleDisplacement.apply(f.battle,f.mob,route);
            h.assertTrue(f.mob.position().distanceToSqr(route.destination())<1e-8 && f.mob.getHealth()==23,"Displacement missed intermediate berry or repeated damage");
            h.assertTrue(f.battle.clock.time()==av && f.battle.budget.remaining()==budget,"Skill displacement charged normal movement or AV");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void displacementStopsAtNewWallOrLethalContact(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();var block=BlockPos.containing(start.add(1,0,0));
            var route=BattleDisplacement.trace(f.battle,f.mob,new net.minecraft.world.phys.Vec3(2,0,0),false,p->true);
            h.getLevel().setBlock(block,Blocks.STONE.defaultBlockState(),2);
            BattleDisplacement.apply(f.battle,f.mob,route);
            h.assertTrue(f.mob.getX()<start.x+0.3,"Displacement crossed newly placed wall");
            h.getLevel().setBlock(block,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE,2),2);
            f.battle.place(f.mob,start);f.mob.setHealth(1);
            BattleDisplacement.apply(f.battle,f.mob,route);
            h.assertTrue(!f.mob.isAlive() && f.mob.getX()<route.destination().x-0.5,"Lethal contact did not stop displacement at contact location");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void changedWaterRecomputesAnimatedFallDistance(GameTestHelper h){
        try(var f=new Fixture(h)){
            var base=f.mob.position();var water=BlockPos.containing(base);
            // dry, water added after preview, water removed after preview
            for(boolean animated:new boolean[]{true,false})for(int scenario=0;scenario<3;scenario++){
                h.getLevel().setBlock(water,scenario==2?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);
                f.mob.setHealth(24);f.battle.place(f.mob,base.add(0,7,0));
                var route=TerrainPath.trace(f.mob,new net.minecraft.world.phys.Vec3(1,0,0),p->true);
                h.assertTrue(!route.landings().isEmpty(),"Missing planned fall");
                if(scenario==1)h.getLevel().setBlock(water,Blocks.WATER.defaultBlockState(),2);
                if(scenario==2)h.getLevel().setBlock(water,Blocks.AIR.defaultBlockState(),2);
                f.battle.budget=new TurnBudget(20);
                double av=f.battle.clock.time(),budget=f.battle.budget.remaining();
                if(animated){
                    f.battle.motion=new BattleSession.Movement(f.mob,route,"amphibious");
                    for(int i=0;i<200 && f.battle.motion!=null;i++)f.battle.tickMotion();
                }else{f.battle.commitMovement(f.mob,route);budget-=route.cost();}
                h.assertTrue(f.battle.motion==null,"Fall animation never finished");
                h.assertTrue(f.mob.getHealth()==(scenario==1?24:20),"Fall used stale preview water: scenario="+scenario+" health="+f.mob.getHealth());
                h.assertTrue(f.battle.clock.time()==av && f.battle.budget.remaining()==budget,"Water change advanced clock or charged movement again");
                h.getLevel().setBlock(water,Blocks.AIR.defaultBlockState(),2);
            }
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void synchronousRouteRechecksNewWalls(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();
            var route=TerrainPath.trace(f.mob,new net.minecraft.world.phys.Vec3(2,0,0),p->true);
            h.assertTrue(route.cost()>1.9,"Missing initial clear route");
            h.getLevel().setBlock(BlockPos.containing(start.add(1,0,0)),Blocks.STONE.defaultBlockState(),2);
            double av=f.battle.clock.time(),budget=f.battle.budget.remaining();
            f.battle.commitMovement(f.mob,route);
            h.assertTrue(f.mob.getX()<start.x+0.3,"Synchronous route crossed a new wall");
            h.assertTrue(f.battle.clock.time()==av && Math.abs(f.battle.budget.remaining()-(budget-route.cost()))<1e-6,"Route invalidation advanced AV or refunded movement");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void removedFloorStopsOldGroundRoute(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.player.position();f.battle.beginMoveTo(f.player,start.add(3,0,0));
            double budget=f.battle.budget.remaining(),av=f.battle.clock.time();
            var floor=f.player.blockPosition().below();
            for(int x=1;x<=4;x++)for(int z=-2;z<=2;z++)h.getLevel().setBlock(floor.offset(x,0,z),Blocks.AIR.defaultBlockState(),2);
            for(int i=0;i<100 && f.battle.motion!=null;i++)f.battle.tickMotion();
            h.assertTrue(f.battle.motion==null && f.player.getX()<start.x+1,"Old route walked across removed floor: start="+start+" end="+f.player.position());
            h.assertTrue(f.battle.budget.remaining()==budget && f.battle.clock.time()==av,"Floor invalidation refunded movement or advanced AV");
            h.assertTrue(f.battle.lastMessage.contains("路线发生变化"),"Floor invalidation reported success");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void removedSupportDropsWaitingMobOnce(GameTestHelper h){
        try(var f=new Fixture(h)){
            for(int x=6;x<=10;x++)for(int z=5;z<=9;z++)h.setBlock(x,7,z,Blocks.STONE);
            var high=f.mob.position().add(0,7,0);f.battle.place(f.mob,high);f.battle.stabilizePlayers();
            h.assertTrue(f.mob.position().equals(high),"Supported fixture fell early");
            for(int x=6;x<=10;x++)for(int z=5;z<=9;z++)h.setBlock(x,7,z,Blocks.AIR);
            double budget=f.battle.budget.remaining(),av=f.battle.clock.time();
            f.battle.stabilizePlayers();
            h.assertTrue(Math.abs(f.mob.getY()-(high.y-7))<1e-6 && f.mob.getHealth()==20,"Missing support did not cause native seven-block fall");
            f.battle.stabilizePlayers();
            h.assertTrue(f.mob.getHealth()==20 && f.battle.budget.remaining()==budget && f.battle.clock.time()==av,"Stabilization repeated fall damage or spent resources");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void roseRefreshIsAvBoundedAndStopsOnExit(GameTestHelper h){
        try(var f=new Fixture(h)){
            var pos=f.mob.blockPosition();var rose=Blocks.WITHER_ROSE.defaultBlockState();h.getLevel().setBlock(pos,rose,2);
            var effect=net.minecraft.world.effect.MobEffects.WITHER;var member=f.battle.member(f.mob);member.statusTicks=0;
            for(int i=0;i<20;i++)rose.entityInside(h.getLevel(),pos,f.mob);
            h.assertTrue(!f.mob.hasEffect(effect),"Real-time rose added wither");
            for(int i=0;i<10;i++)BattleStatus.tick(member);
            h.assertTrue(f.mob.hasEffect(effect) && f.mob.getEffect(effect).getDuration()==39 && f.mob.getHealth()==23,"Rose did not apply native AV effect");
            for(int i=0;i<9;i++)BattleStatus.tick(member);
            h.assertTrue(f.mob.getEffect(effect).getDuration()==30 && f.mob.getHealth()==23,"Rose caused per-tick damage or refresh");
            BattleStatus.tick(member);h.assertTrue(f.mob.getHealth()==22,"Rose interval did not refresh native effect");
            h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
            for(int i=0;i<40;i++)BattleStatus.tick(member);
            h.assertTrue(!f.mob.hasEffect(effect) && f.mob.getHealth()==22,"Rose continued refreshing outside contact");
            f.battle.close("test");rose.entityInside(h.getLevel(),pos,f.mob);
            h.assertTrue(f.mob.hasEffect(effect),"Rose did not resume outside battle");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void berryCommittedMovementIsRateLimitedAndTeleportSafe(GameTestHelper h){
        try(var f=new Fixture(h)){
            var start=f.mob.position();var end=start.add(1,0,0);var pos=BlockPos.containing(end);
            h.getLevel().setBlock(pos,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE,2),2);
            f.battle.place(f.mob,end);
            h.assertTrue(f.mob.getHealth()==24,"Teleport arrival triggered berry damage");
            for(int i=0;i<20;i++)BattleStatus.tick(f.battle.member(f.mob));
            h.assertTrue(f.mob.getHealth()==24,"Stationary berry contact caused damage");
            f.battle.place(f.mob,start);f.battle.commitMovement(f.mob,new TerrainPath.Result(end,1,java.util.List.of(),java.util.List.of(end)));
            h.assertTrue(f.mob.getHealth()==23,"Committed movement did not trigger berry damage");
            for(int i=0;i<10;i++){
                var from=f.mob.position();f.mob.setPos(from.add(0.005,0,0));BattleFields.contact(f.battle,f.mob,from,f.mob.position(),true);
            }
            h.assertTrue(f.mob.getHealth()==23,"Movement samples repeated berry damage");
            double until=f.battle.clock.time()+50;
            while(f.battle.clock.time()<until-1e-7){
                double next=f.battle.clock.nextDelay(f.battle::agility);
                if(next<1e-7)f.battle.clock.next(f.battle::agility);
                else f.battle.clock.advance(Math.min(next,until-f.battle.clock.time()),f.battle::agility);
            }
            var from=f.mob.position();f.mob.setPos(from.add(0.005,0,0));BattleFields.contact(f.battle,f.mob,from,f.mob.position(),true);
            h.assertTrue(f.mob.getHealth()==22,"Berry cooldown did not expire after 50 AV");
            h.getLevel().setBlock(pos,Blocks.SWEET_BERRY_BUSH.defaultBlockState(),2);
            f.battle.close("test");
            try(var other=new Fixture(h)){
                other.battle.place(other.mob,end);BattleFields.contact(other.battle,other.mob,start,end,true);
                h.assertTrue(other.mob.getHealth()==24,"Young berry bush caused damage");
            }
        }h.succeed();
    }
    private static final class Fixture implements AutoCloseable {
        final ServerPlayer player;final Pillager mob;final BattleSession battle;
          Fixture(GameTestHelper h){this(h,false);}
          Fixture(GameTestHelper h,boolean withBrain){
            for(int x=0;x<12;x++)for(int z=0;z<12;z++)h.setBlock(x,0,z,Blocks.STONE);
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mt-contact"),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);new EmbeddedChannel(connection);
            player.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
            player.setGameMode(GameType.SURVIVAL);player.getFoodData().setFoodLevel(10);
            var pos=h.absolutePos(new BlockPos(2,1,2));player.teleportTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
            mob=h.spawnWithNoFreeWill(EntityType.PILLAGER,new BlockPos(8,1,7));
            var data=CombatData.current;var brains=new HashMap<>(data.mobs());brains.remove("minecraft:pillager");
            if(withBrain)brains.put("minecraft:pillager",new CombatData.Brain(100,1.5,"",Map.of(),null,"ground"));
            battle=new BattleSession(player,mob,new CombatData.Snapshot(data.actions(),Map.copyOf(brains),data.items(),data.sources(),data.grants()),player);
        }
        public void close(){battle.close("test");player.getServer().getPlayerList().remove(player);player.discard();mob.discard();}
    }
    @GameTest(template="empty")
    public static void contactDamageUsesLogicalIntervalAndNativeAmount(GameTestHelper h){
        try(var f=new Fixture(h)){
            var pos=f.mob.blockPosition();var member=f.battle.member(f.mob);
            for(var block:new net.minecraft.world.level.block.Block[]{Blocks.CACTUS,Blocks.CAMPFIRE,Blocks.SOUL_CAMPFIRE}){
                var state=block.defaultBlockState();h.getLevel().setBlock(pos,state,2);
                f.mob.setHealth(24);member.statusTicks=0;f.mob.invulnerableTime=0;
                state.entityInside(h.getLevel(),pos,f.mob);
                h.assertTrue(f.mob.getHealth()==24,"Real-time contact bypassed battle lock");
                for(int i=0;i<9;i++)BattleStatus.tick(member);
                h.assertTrue(f.mob.getHealth()==24,"Contact damage ran before 50 AV");
                BattleStatus.tick(member);float damage=block==Blocks.SOUL_CAMPFIRE?2:1;
                h.assertTrue(f.mob.getHealth()==24-damage,"Native contact amount missing");
                for(int i=0;i<9;i++)BattleStatus.tick(member);
                h.assertTrue(f.mob.getHealth()==24-damage,"Contact damage repeated every logical tick");
                BattleStatus.tick(member);h.assertTrue(f.mob.getHealth()==24-2*damage,"Second contact interval missing");
                h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                for(int i=0;i<10;i++)BattleStatus.tick(member);
                h.assertTrue(f.mob.getHealth()==24-2*damage,"Contact damage continued after leaving hazard");
            }
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void magmaAndCampfireRetainNativeConditions(GameTestHelper h){
        try(var f=new Fixture(h)){
            var member=f.battle.member(f.mob);var pos=f.mob.blockPosition();
            h.getLevel().setBlock(pos.below(),Blocks.MAGMA_BLOCK.defaultBlockState(),2);
            f.mob.setOnGround(true);member.statusTicks=9;BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Magma did not damage supported combatant");
            f.mob.setShiftKeyDown(true);member.statusTicks=19;BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Magma ignored careful stepping");f.mob.setShiftKeyDown(false);
            f.mob.setOnGround(false);member.statusTicks=29;BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Magma hurt unsupported entity");
            h.getLevel().setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2);
            h.getLevel().setBlock(pos,Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT,false),2);
            member.statusTicks=39;BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Unlit campfire damaged combatant");
            h.getLevel().setBlock(pos,Blocks.SOUL_CAMPFIRE.defaultBlockState(),2);
            f.mob.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE,100));
            member.statusTicks=49;BattleStatus.tick(member);
            h.assertTrue(f.mob.getHealth()==23,"Contact bypassed native fire resistance");
            f.mob.removeAllEffects();f.battle.close("test");f.mob.invulnerableTime=0;
            h.getLevel().getBlockState(pos).entityInside(h.getLevel(),pos,f.mob);
            h.assertTrue(f.mob.getHealth()==21,"Native contact did not resume on exit");
        }h.succeed();
    }
}
