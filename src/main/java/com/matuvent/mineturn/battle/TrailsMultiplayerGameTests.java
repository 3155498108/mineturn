package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("trails") @PrefixGameTestTemplate(false)
public final class TrailsMultiplayerGameTests {
    private static ItemStack tag(String name){var s=new ItemStack(Items.NAME_TAG);s.set(DataComponents.CUSTOM_NAME,Component.literal(name));return s;}
    private static void name(TrailsGameTests.Fixture f,LivingEntity mob){Items.NAME_TAG.interactLivingEntity(tag("队员"),f.p,mob,InteractionHand.MAIN_HAND);}
    private static BattleSession battle(TrailsGameTests.Fixture a,TrailsGameTests.Fixture b){
        var entities=new ArrayList<LivingEntity>(a.entities);entities.addAll(b.entities);entities.add(b.p);a.b=new BattleSession(a.p,entities,false,true);a.b.trails.criticalRoll=()->1;return a.b;
    }
    @GameTest(template="empty")
    public static void nameTagRecordsUUIDAndPreventsRenameTheftAndSurvivesSave(GameTestHelper h){
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            var pig=a.mob(EntityType.PIG,6,5);var first=tag("小猪");Items.NAME_TAG.interactLivingEntity(first,a.p,pig,InteractionHand.MAIN_HAND);
            h.assertTrue(first.isEmpty() && a.p.getUUID().equals(TrailsParties.owner(pig)),"Real name tag did not register owner");
            var stolen=tag("接管");Items.NAME_TAG.interactLivingEntity(stolen,b.p,pig,InteractionHand.MAIN_HAND);
            h.assertTrue(stolen.getCount()==1 && pig.getName().getString().equals("小猪") && a.p.getUUID().equals(TrailsParties.owner(pig)),"Other player stole or renamed pet");
            var copy=EntityType.PIG.create(h.getLevel());copy.load(pig.saveWithoutId(new net.minecraft.nbt.CompoundTag()));
            h.assertTrue(a.p.getUUID().equals(TrailsParties.owner(copy)),"Ownership lost in entity save/load");
            var wolf=a.mob(EntityType.WOLF,6,7);wolf.setTame(true,false);wolf.setOwnerUUID(b.p.getUUID());var attempt=tag("别人的狗");Items.NAME_TAG.interactLivingEntity(attempt,a.p,wolf,InteractionHand.MAIN_HAND);
            h.assertTrue(attempt.getCount()==1 && TrailsParties.owner(wolf)==null,"Stole an already tamed animal");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void multiplayerAdmitsNamedPetsAndWildEnemiesButNotCosmeticallyNamedAnimals(GameTestHelper h){
        boolean pvp=h.getLevel().getServer().isPvpAllowed();h.getLevel().getServer().setPvpAllowed(true);
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            b.p.teleportTo(a.p.getX()+5,a.p.getY(),a.p.getZ());var pig=a.mob(EntityType.PIG,6,5);name(a,pig);
            var sheep=a.mob(EntityType.SHEEP,6,7);sheep.setCustomName(Component.literal("只有名字，没有用命名牌"));var wild=a.mob(EntityType.ZOMBIE,9,7);
            a.b=TrailsCombat.start(a.p);var c=a.b.trails;
            h.assertTrue(c.multiplayer && a.b.members.containsKey(pig.getUUID()) && a.b.members.containsKey(wild.getUUID()) && a.b.members.containsKey(b.p.getUUID()),"Named friend, untagged hostile or opponent player excluded");
            h.assertTrue(!a.b.members.containsKey(sheep.getUUID()) && c.controls(a.p,pig) && c.enemy(a.p,b.p),"Unregistered friend admitted or opponent/control wrong");
        }finally{h.getLevel().getServer().setPvpAllowed(pvp);}h.succeed();
    }
    @GameTest(template="empty")
    public static void teamsShareFactionButNotPetCommandsOrMovement(GameTestHelper h){
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            var pig=a.mob(EntityType.PIG,6,5);name(a,pig);a.mob(EntityType.ZOMBIE,11,9);
            TrailsTeams.invite(a.p,b.p);h.assertTrue(b.p.getTeam()==null,"Invite silently joined the receiver");TrailsTeams.accept(b.p);
            var session=battle(a,b);var c=session.trails;session.actor=pig;c.onTurn();var position=pig.position();
            h.assertTrue(c.sameSide(a.p,b.p) && c.sameSide(b.p,pig) && c.controls(a.p,pig) && !c.controls(b.p,pig),"Team sharing gave pet control away");
            TrailsCombat.request(b.p,new TrailsNetwork.Command(session.id,session.revision,"end",-1,0,0,0));
            TrailsCombat.input(b.p,new TrailsNetwork.MoveInput(session.id,c.turn,10,1,0));
            h.assertTrue(session.actor==pig && c.inputTicks==0 && pig.position().equals(position),"Teammate issued another owner's action/input");
            TrailsCombat.input(a.p,new TrailsNetwork.MoveInput(session.id,c.turn,11,1,0));h.assertTrue(c.inputTicks>0,"Actual owner input rejected");
            session.close("test");TrailsTeams.leave(b.p);TrailsTeams.leave(a.p);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void threeUnteamedPlayersAreThreeDistinctSides(GameTestHelper h){
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h);var d=new TrailsGameTests.Fixture(h)){
            a.entities.add(d.p);var session=battle(a,b);var c=session.trails;
            h.assertTrue(c.enemy(a.p,b.p) && c.enemy(a.p,d.p) && c.enemy(b.p,d.p),"Three-player encounter collapsed both visitors into one side");
            a.entities.remove(d.p);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void opponentSBorrowNeitherCpWeaponNorPendingCast(GameTestHelper h){
        boolean pvp=h.getLevel().getServer().isPvpAllowed();h.getLevel().getServer().setPvpAllowed(true);
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            for(int i=0;i<61;i++){a.p.tick();b.p.tick();}b.p.teleportTo(a.p.getX(),a.p.getY(),a.p.getZ()+3);
            for(var p:List.of(a.p,b.p)){p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);p.setHealth(100);com.matuvent.mineturn.platform.RuntimePlatform.data(p).putInt("mineturn.trails.cp",100);}
            b.p.getInventory().setItem(0,new ItemStack(Items.IRON_AXE));var session=battle(a,b);var c=session.trails;session.actor=a.p;c.onTurn();
            c.attack(b.p.getId(),0,false);var pending=c.pending;
            TrailsCombat.request(b.p,new TrailsNetwork.Command(session.id,session.revision,"burst",a.p.getId(),0,0,-1));
            h.assertTrue(com.matuvent.mineturn.platform.RuntimePlatform.data(b.p).getInt("mineturn.trails.cp")==0 && com.matuvent.mineturn.platform.RuntimePlatform.data(a.p).getInt("mineturn.trails.cp")==100 && c.pending==pending && c.lastPerformances.get(b.p.getUUID()).weapon().equals("minecraft:iron_axe"),"Remote S spent other player's resources or replaced original cast");
            int safety=0;while(c.burstAction!=null && safety++<150)c.tick();
            h.assertTrue(c.pending==pending && session.actor==a.p && a.p.getHealth()<100,"S failed to damage rival or restore interrupted turn");
            TrailsGameTests.resolve(c);h.assertTrue(b.p.getHealth()<100,"Original player never resumed attack after rival S");
        }finally{h.getLevel().getServer().setPvpAllowed(pvp);}h.succeed();
    }
    @GameTest(template="empty")
    public static void leavingOnlyRemovesOwnPetsAndOwnerAndKeepsRivalsBattle(GameTestHelper h){
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            var pig=a.mob(EntityType.PIG,6,5);name(a,pig);var sheep=b.mob(EntityType.SHEEP,9,8);name(b,sheep);var wild=a.mob(EntityType.ZOMBIE,12,12);
            var session=battle(a,b);session.actor=a.p;session.trails.onTurn();session.trails.withdraw(a.p);
            h.assertTrue(!session.closed && !BattleManager.locked(a.p) && !BattleManager.locked(pig) && BattleManager.locked(b.p) && BattleManager.locked(sheep) && BattleManager.locked(wild),"One player withdrew everybody or stranded own pets");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void teamChangesAreBlockedInBattleAndSnapshotDoesNotFlipOnAdminEdit(GameTestHelper h){
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            TrailsTeams.invite(a.p,b.p);TrailsTeams.accept(b.p);a.mob(EntityType.ZOMBIE,12,12);var session=battle(a,b);var c=session.trails;
            try{TrailsTeams.leave(b.p);throw new IllegalStateException("Battle team edit was accepted");}catch(IllegalArgumentException expected){}
            b.p.getScoreboard().removePlayerFromTeam(b.p.getScoreboardName());
            h.assertTrue(c.sameSide(a.p,b.p),"Administrative scoreboard edit flipped active battle faction");session.close("test");TrailsTeams.leave(a.p);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void pvpDisabledStillAdmitsWildEnemiesAndKeepsRivalsOut(GameTestHelper h){
        boolean pvp=h.getLevel().getServer().isPvpAllowed();h.getLevel().getServer().setPvpAllowed(false);
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            b.p.teleportTo(a.p.getX()+5,a.p.getY(),a.p.getZ());var zombie=a.mob(EntityType.ZOMBIE,9,7);var own=a.mob(EntityType.PIG,6,7);name(a,own);
            a.b=TrailsCombat.start(a.p);
            h.assertTrue(a.b.members.containsKey(zombie.getUUID()) && a.b.members.containsKey(own.getUUID()) && !a.b.members.containsKey(b.p.getUUID()),"PvP-disabled policy admitted rival or lost wild enemy/pet");
        }finally{h.getLevel().getServer().setPvpAllowed(pvp);}h.succeed();
    }
    @GameTest(template="empty")
    public static void newlyJoinedWildMobKeepsHostileFactionAndTeamBuffsStayOnOwnSide(GameTestHelper h){
        try(var a=new TrailsGameTests.Fixture(h);var b=new TrailsGameTests.Fixture(h)){
            TrailsTeams.invite(a.p,b.p);TrailsTeams.accept(b.p);var sheep=a.mob(EntityType.SHEEP,6,5);name(a,sheep);var zombie=a.mob(EntityType.ZOMBIE,7,5);
            b.p.teleportTo(a.p.getX()+1,a.p.getY(),a.p.getZ());var session=battle(a,b);var c=session.trails;
            var joined=a.mob(EntityType.ZOMBIE,8,5);session.add(joined);
            h.assertTrue(c.sameSide(zombie,joined) && c.enemy(a.p,joined),"New hostile lost frozen faction");
            for(var e:List.of(a.p,b.p,sheep,zombie))e.setHealth(2);
            session.actor=sheep;c.onTurn();c.companionSkill(-1);TrailsGameTests.resolve(c);
            h.assertTrue(a.p.getHealth()>2 && b.p.getHealth()>2 && zombie.getHealth()==2,"Team heal missed teammate or healed wild enemy");
            h.assertTrue(com.matuvent.mineturn.platform.RuntimePlatform.data(a.p).getInt("mineturn.trails.cp")==10 && com.matuvent.mineturn.platform.RuntimePlatform.data(b.p).getInt("mineturn.trails.cp")==0,"Pet credited another owner's CP");
            session.close("test");TrailsTeams.leave(b.p);TrailsTeams.leave(a.p);
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void multiplayerPacketCodecsPreservePrivateControlAndRemoteWeapon(GameTestHelper h){
        var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try{
            var id=UUID.randomUUID();var pos=new net.minecraft.world.phys.Vec3(2,3,4);
            var view=new TrailsNetwork.View(id,13,7,true,100,100,"minecraft:iron_axe","S",4,19,pos,false,false,"owner","wait",List.of());
            TrailsNetwork.View.CODEC.encode(buf,view);h.assertTrue(view.equals(TrailsNetwork.View.CODEC.decode(buf)),"Shared faction overwrote private control flag");
            var animation=new TrailsNetwork.Performance(id,23,7,"strike",16,false,pos,"burst_axe","S","minecraft:iron_axe");
            TrailsNetwork.Performance.CODEC.encode(buf,animation);h.assertTrue(animation.equals(TrailsNetwork.Performance.CODEC.decode(buf)),"Remote S lost primary weapon");
            var request=new TrailsNetwork.PartyRequest("invite",id.toString());TrailsNetwork.PartyRequest.CODEC.encode(buf,request);h.assertTrue(request.equals(TrailsNetwork.PartyRequest.CODEC.decode(buf)),"Party request mismatch");
            var state=new TrailsNetwork.PartyState("party","owner","invite","ok");TrailsNetwork.PartyState.CODEC.encode(buf,state);h.assertTrue(state.equals(TrailsNetwork.PartyState.CODEC.decode(buf)) && !buf.isReadable(),"Party state mismatch");
        }finally{buf.release();}h.succeed();
    }
    private TrailsMultiplayerGameTests(){}
}
