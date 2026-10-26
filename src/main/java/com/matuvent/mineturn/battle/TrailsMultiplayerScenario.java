package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.network.*;
import java.net.InetAddress;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Disposable two-process network exercise. No listener, save edits or fixtures in production. */
public final class TrailsMultiplayerScenario {
    private static int stage, age, total;
    private static boolean listening;
    private static ServerPlayer a,b;
    private static Mob wolf,pig,sheep,zombie;
    private static BattleSession battle;
    private static net.minecraft.world.phys.Vec3 pigStart;
    private static float hpA,hpB;
    private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException("MULTI_QA: "+text);}
    private static void phase(String name){age=0;for(var p:List.of(a,b))if(!p.hasDisconnected())BattleNetwork.send(p,new TrailsNetwork.Cue("multi_qa",name,0,20));MineTurn.LOGGER.info("MULTI_QA_PHASE {}",name);}
    private static void next(int value,String name){stage=value;phase(name);}
    private static void turn(LivingEntity actor){battle.actor=actor;battle.trails.onTurn();battle.revision++;battle.syncAll();}
    private static void name(ServerPlayer p,Mob mob,String name){mob.setCustomName(Component.literal(name));TrailsParties.named(p,mob);}
    private static Mob mob(EntityType<? extends Mob> type,double x,double z){var m=type.create(a.serverLevel());m.moveTo(x,-59,z,180,0);m.setNoAi(true);a.serverLevel().addFreshEntity(m);return m;}
    private static void fixture(){
        var level=a.serverLevel();level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,a.server);
        for(int x=-17;x<=17;x++)for(int z=-17;z<=17;z++)level.setBlock(new BlockPos(x,-60,z),Blocks.STONE.defaultBlockState(),3);
        for(var m:level.getEntitiesOfClass(Mob.class,new net.minecraft.world.phys.AABB(-20,-64,-20,20,-40,20)))m.discard();
        for(var p:List.of(a,b)){
            if(p.getTeam()!=null)TrailsTeams.leave(p);
            p.setGameMode(GameType.SURVIVAL);p.teleportTo(.5,-59,p==a?.5:3);p.setYRot(p==a?0:180);
            p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);p.setHealth(100);p.removeAllEffects();
            p.getInventory().clearContent();p.getInventory().setItem(0,new ItemStack(p==a?Items.IRON_SWORD:Items.IRON_AXE));p.getInventory().selected=0;
            com.matuvent.mineturn.platform.RuntimePlatform.data(p).putInt("mineturn.trails.cp",p==a?200:100);com.matuvent.mineturn.platform.RuntimePlatform.data(p).putString("mineturn.trails.primary",p==a?"minecraft:iron_sword":"minecraft:iron_axe");
            p.inventoryMenu.broadcastChanges();
        }
        wolf=mob(EntityType.WOLF,-2.5,1.5);name(a,wolf,"A的狼");pig=mob(EntityType.PIG,2.5,4.5);name(b,pig,"B的猪");
        sheep=mob(EntityType.SHEEP,6,2.5);sheep.setCustomName(Component.literal("未登记的羊"));zombie=mob(EntityType.ZOMBIE,.5,9);
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);
    }
    public static void tick(MinecraftServer server){
        if(com.matuvent.mineturn.platform.RuntimePlatform.production() || !System.getProperty("mineturn.multiplayerSmoke","").equals("host"))return;
        check(server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().equals("AT-QA-Multiplayer-20261026"),"wrong save");
        a=server.getPlayerList().getPlayerByName("AT_A");if(a==null)return;
        if(!listening){
            try{server.setUsesAuthentication(false);server.setPvpAllowed(true);server.getConnection().startTcpServerListener(InetAddress.getByName("127.0.0.1"),25578);listening=true;MineTurn.LOGGER.info("MULTI_QA_HOST_LISTENING 127.0.0.1:25578");}catch(Exception error){throw new IllegalStateException(error);}return;
        }
        if(++total>12000)throw new IllegalStateException("MULTI_QA timeout stage="+stage);
        if(stage==0){b=server.getPlayerList().getPlayerByName("AT_B");if(b==null)return;fixture();next(1,"ready");return;}
        age++;
        switch(stage){
            case 1->{if(age==80)next(2,"pvp_enter");}
            case 2->{battle=BattleManager.ACTIVE.get(a.getUUID());if(battle!=null){
                check(battle.members.size()==5 && !battle.members.containsKey(sheep.getUUID()),"admission set");check(battle.trails.enemy(a,b) && battle.trails.controls(b,pig) && !battle.trails.controls(a,pig),"faction/ownership");
                battle.trails.criticalRoll=()->1;turn(a);next(3,"pvp_view");}}
            case 3->{if(age==65){check(battle.actor==a && battle.trails.inputTicks==0,"rival forged turn/input accepted");hpA=a.getHealth();hpB=b.getHealth();next(4,"pvp_attack");}}
            case 4->{if(battle.trails.pending!=null)next(5,"pvp_burst");}
            case 5->{if(battle.trails.burstAction!=null){check(com.matuvent.mineturn.platform.RuntimePlatform.data(b).getInt("mineturn.trails.cp")==0 && com.matuvent.mineturn.platform.RuntimePlatform.data(a).getInt("mineturn.trails.cp")==200,"S CP mixed: host="+com.matuvent.mineturn.platform.RuntimePlatform.data(a).getInt("mineturn.trails.cp")+", guest="+com.matuvent.mineturn.platform.RuntimePlatform.data(b).getInt("mineturn.trails.cp")+", actor="+battle.actor.getScoreboardName());check(battle.trails.lastPerformances.get(b.getUUID()).weapon().equals("minecraft:iron_axe"),"S weapon mixed");next(6,"pvp_cinematic");}}
            case 6->{if(age>100 && !battle.trails.busy()){
                check(a.getHealth()<hpA && b.getHealth()<hpB,"S or suspended attack failed");battle.close("QA phase transition");next(7,"team_open");}}
            case 7->{if(age==70)next(8,"team_invite");}
            case 8->{if(age==70)next(9,"team_accept");}
            case 9->{if(TrailsParties.teammates(a,b) && age>40)next(10,"coop_enter");}
            case 10->{battle=BattleManager.ACTIVE.get(a.getUUID());if(battle!=null){check(battle.members.size()==5 && battle.trails.sameSide(a,b),"co-op admission");turn(pig);pigStart=pig.position();next(11,"coop_pig");}}
            case 11->{if(age==100){check(pig.position().distanceTo(pigStart)>.35 && pig.position().distanceTo(pigStart)<4.1,"owner pet WASD");check(battle.actor==pig,"teammate ended another owner's turn");turn(wolf);next(12,"coop_wolf");}}
            case 12->{if(age==65){turn(pig);next(13,"withdraw");}}
            case 13->{if(!BattleManager.locked(b)){
                check(!BattleManager.locked(pig) && BattleManager.locked(a) && BattleManager.locked(wolf) && BattleManager.locked(zombie) && !battle.closed,"withdraw cleanup");next(14,"withdraw_view");}}
            case 14->{if(age==65){battle.close("QA phase transition");b.teleportTo(.5,-59,3);next(15,"reenter");}}
            case 15->{battle=BattleManager.ACTIVE.get(a.getUUID());if(battle!=null){turn(pig);next(16,"disconnect");}}
            case 16->{if(server.getPlayerList().getPlayerByName("AT_B")==null && age>50){
                check(!BattleManager.locked(b) && !BattleManager.locked(pig) && BattleManager.locked(a) && !battle.closed,"logout cleanup");battle.close("QA complete");
                BattleNetwork.send(a,new TrailsNetwork.Cue("multi_qa","passed",0,20));stage=17;
                MineTurn.LOGGER.info("MULTI_QA_SERVER_PASSED: actual two clients, PvP faction, wild enemies, tag receipt, private control, rival S independent CP/weapon and suspended cast, invite/accept, co-op pet WASD, own withdrawal and disconnect cleanup");}}
            default->{}
        }
    }
    private TrailsMultiplayerScenario(){}
}
