package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Disposable integrated-server fixtures, guarded independently of the client smoke driver. */
public final class TrailsCompanionScenario {
    public static volatile boolean ready, verified;
    private static ServerPlayer player(MinecraftServer server) {
        if (com.matuvent.mineturn.platform.RuntimePlatform.production() || !Boolean.getBoolean("mineturn.encounterSmoke")
                || !System.getProperty("mineturn.qaTag", "").startsWith("companions")) throw new IllegalStateException("QA disabled");
        String world=System.getProperty("mineturn.qaWorld", "");
        if(!world.startsWith("AT-QA-") || !server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().equals(world)) throw new IllegalStateException("Wrong QA save");
        return server.getPlayerList().getPlayers().getFirst();
    }
    public static void setup(MinecraftServer server, boolean reload) {
        var p=player(server);var level=p.serverLevel();
        if (reload) {
            verified=false;verify(server);if(!verified)throw new IllegalStateException("Companion did not survive restart");verified=false;
            for(int i=0;i<9;i++)if(TrailsCompanions.ticket(p.getInventory().getItem(i))!=null)p.getInventory().selected=i;
        } else {
            var active=BattleManager.ACTIVE.get(p.getUUID());if(active!=null)active.close("QA");
            for(var mob:level.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(40)))mob.discard();
            for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++){
                level.setBlockAndUpdate(new BlockPos(x,-60,z),Blocks.STONE.defaultBlockState());
                for(int y=-59;y<-53;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
            p.setGameMode(GameType.SURVIVAL);p.teleportTo(.5,-59,.5);p.setHealth(20);p.removeAllEffects();p.getInventory().clearContent();
            var tag=new ItemStack(Items.NAME_TAG);tag.set(DataComponents.CUSTOM_NAME,Component.literal("QA伙伴"));p.getInventory().setItem(0,tag);
            p.getInventory().setItem(2,new ItemStack(Items.IRON_SWORD));p.getInventory().selected=0;
            var pig=EntityType.PIG.create(level);pig.moveTo(3,-59,.5);pig.setHealth(5);level.addFreshEntity(pig);
        }
        p.inventoryMenu.broadcastChanges();ready=true;
    }
    public static void battle(MinecraftServer server) {
        var p=player(server);p.getInventory().selected=2;p.inventoryMenu.broadcastChanges();
        var zombie=EntityType.ZOMBIE.create(p.serverLevel());zombie.moveTo(p.getX()+3,p.getY(),p.getZ());zombie.setNoAi(true);
        zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));p.serverLevel().addFreshEntity(zombie);
        var b=new BattleSession(p,List.of(zombie),false,false);b.actor=p;b.trails.onTurn();b.syncAll();
    }
    public static void ownTurn(MinecraftServer server) {
        var p=player(server);var b=BattleManager.ACTIVE.get(p.getUUID());
        if(b!=null){b.actor=p;b.trails.onTurn();b.syncAll();}
    }
    public static void verify(MinecraftServer server) {
        var p=player(server);
        for(var item:p.getInventory().items){var ticket=TrailsCompanions.ticket(item);
            if(ticket!=null){var entry=CompanionStorage.get(server).find(ticket);
                if(entry==null || !entry.owner().equals(p.getUUID()) || entry.entity().getFloat("Health")!=5)throw new IllegalStateException("Companion saved receipt/state mismatch");
                MineTurn.LOGGER.info("COMPANION_QA_STORED_OK uuid={} owner={}",entry.entity().getUUID("UUID"),p.getUUID());verified=true;return;}}
        throw new IllegalStateException("Stored companion missing from real inventory");
    }
    private TrailsCompanionScenario(){}
}
