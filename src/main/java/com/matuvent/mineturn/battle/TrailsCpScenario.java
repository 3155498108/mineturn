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

/** CP integration QA is confined to a disposable, explicitly named save. */
public final class TrailsCpScenario {
    public static volatile boolean ready,verified;
    private static UUID bird;
    private static ServerPlayer player(MinecraftServer server) {
        if(com.matuvent.mineturn.platform.RuntimePlatform.production() || !Boolean.getBoolean("mineturn.encounterSmoke") || !System.getProperty("mineturn.qaTag","").startsWith("cp"))throw new IllegalStateException("CP QA disabled");
        String name=System.getProperty("mineturn.qaWorld","");
        if(!name.matches("AT-QA-[A-Za-z0-9-]+") || !server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().equals(name))throw new IllegalStateException("Wrong CP QA world");
        return server.getPlayerList().getPlayers().getFirst();
    }
    public static void setup(MinecraftServer server,boolean reload) {
        var p=player(server);var level=p.serverLevel();ready=false;verified=false;
        if(reload){verifyStored(server);verified=false;ready=true;return;}
        for(var mob:level.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(40)))mob.discard();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++){
            level.setBlockAndUpdate(new BlockPos(x,-60,z),Blocks.STONE.defaultBlockState());
            for(int y=-59;y<-51;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
        }
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false,server);
        p.setGameMode(GameType.SURVIVAL);p.teleportTo(.5,-59,.5);p.getInventory().clearContent();p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(20);p.setHealth(20);TrailsCp.set(p,137);
        p.getInventory().setItem(0,new ItemStack(Items.IRON_SWORD));
        var tag=new ItemStack(Items.NAME_TAG);tag.set(DataComponents.CUSTOM_NAME,Component.literal("CP伙伴"));p.getInventory().setItem(1,tag);p.getInventory().selected=0;
        var chicken=EntityType.CHICKEN.create(level);chicken.moveTo(2.5,-59,.5);chicken.setNoAi(true);chicken.setHealth(3);level.addFreshEntity(chicken);bird=chicken.getUUID();TrailsParties.named(p,chicken);
        if(TrailsCp.get(chicken)!=100)throw new IllegalStateException("New chicken CP is not 100");
        var zombie=EntityType.ZOMBIE.create(level);zombie.moveTo(6,-59,3);zombie.setNoAi(true);zombie.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));level.addFreshEntity(zombie);
        var battle=new BattleSession(p,List.of(chicken,zombie),false,false);battle.actor=chicken;battle.trails.onTurn();battle.syncAll();p.inventoryMenu.broadcastChanges();ready=true;
    }
    public static void ownTurn(MinecraftServer server) {
        var p=player(server);var battle=BattleManager.ACTIVE.get(p.getUUID());battle.actor=(LivingEntity)p.serverLevel().getEntity(bird);battle.trails.onTurn();battle.revision++;battle.syncAll();
    }
    public static void verifyStored(MinecraftServer server) {
        var p=player(server);
        for(int i=0;i<9;i++){
            var ticket=TrailsCompanions.ticket(p.getInventory().getItem(i));if(ticket==null)continue;
            var entry=CompanionStorage.get(server).find(ticket);if(entry==null)throw new IllegalStateException("Missing CP receipt");
            var restored=EntityType.loadEntityRecursive(entry.entity().copy(),p.serverLevel(),e->e);
            if(!(restored instanceof Mob mob) || TrailsCp.get(mob)!=20 || mob.getHealth()!=3)throw new IllegalStateException("Stored CP/HP changed");
            bird=mob.getUUID();mob.discard();p.getInventory().selected=i;verified=true;
            MineTurn.LOGGER.info("CP_QA_STORED uuid={} cp=20 hp=3",bird);return;
        }
        throw new IllegalStateException("Missing stored CP companion");
    }
    public static void verifyReleased(MinecraftServer server) {
        var p=player(server);var e=p.serverLevel().getEntity(bird);
        if(!(e instanceof Mob mob) || TrailsCp.get(mob)!=20 || mob.getHealth()!=3)throw new IllegalStateException("Restart release reset CP/HP");
        verified=true;MineTurn.LOGGER.info("CP_QA_RESTART_RELEASED uuid={} cp=20 hp=3",bird);
    }
    private TrailsCpScenario(){}
}
