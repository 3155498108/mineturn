package com.matuvent.mineturn.battle;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.network.chat.Component;

/** Name-tag ownership is an immutable UUID receipt, independent of the displayed mob name. */
public final class TrailsParties {
    public static final String OWNER="mineturn.trails.partyOwner";
    public static boolean multiplayer(MinecraftServer server){return !server.isSingleplayer() || server.isPublished() || server.getPlayerList().getPlayerCount()>1;}
    public static UUID owner(LivingEntity entity){return entity.getPersistentData().hasUUID(OWNER)?entity.getPersistentData().getUUID(OWNER):null;}
    public static boolean canName(ServerPlayer player,LivingEntity target){
        if(BattleManager.locked(player) || BattleManager.locked(target))return false;
        UUID owner=owner(target);
        if(owner!=null && !owner.equals(player.getUUID()))return false;
        return !(target instanceof OwnableEntity nativePet) || nativePet.getOwnerUUID()==null || nativePet.getOwnerUUID().equals(player.getUUID());
    }
    public static void named(ServerPlayer player,LivingEntity target){
        if(target instanceof Mob && !(target instanceof Enemy) && !TrailsCombat.hostile(target,player) && owner(target)==null){
            target.getPersistentData().putUUID(OWNER,player.getUUID());
            player.displayClientMessage(Component.literal(target.getName().getString()+" 已加入你的队伍；多人时仅由你操控。"),true);
        }
    }
    public static String faction(ServerPlayer player){return player.getTeam()==null?"player:"+player.getUUID():"team:"+player.getTeam().getName();}
    public static boolean teammates(ServerPlayer a,ServerPlayer b){return a==b || a.getTeam()!=null && a.getTeam()==b.getTeam();}
    private TrailsParties(){}
}
