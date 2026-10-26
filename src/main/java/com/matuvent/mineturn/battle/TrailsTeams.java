package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.network.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Vanilla scoreboard teams persist membership; invitations require the receiver to accept. */
public final class TrailsTeams {
    private record Invite(UUID from,String team,long expires){}
    private static final Map<MinecraftServer,Map<UUID,Invite>> INVITES=new WeakHashMap<>();
    private static Map<UUID,Invite> invites(MinecraftServer server){
        var map=INVITES.computeIfAbsent(server,key->new HashMap<>());map.values().removeIf(i->i.expires<server.overworld().getGameTime());return map;
    }
    private static void unlocked(ServerPlayer p){if(BattleManager.locked(p))throw new IllegalArgumentException("请先结束或退出战斗，再更改队伍。");}
    public static void create(ServerPlayer p,String name){
        unlocked(p);if(p.getTeam()!=null)throw new IllegalArgumentException("请先离开当前队伍。");
        name=name.strip();if(name.isEmpty() || name.length()>24)throw new IllegalArgumentException("队伍名需为 1–24 字。");
        var board=p.getScoreboard();String key;do{key="mt_"+UUID.randomUUID().toString().replace("-","").substring(0,13);}while(board.getPlayerTeam(key)!=null);
        var team=board.addPlayerTeam(key);team.setDisplayName(Component.literal(name));team.setAllowFriendlyFire(false);board.addPlayerToTeam(p.getScoreboardName(),team);
    }
    public static void invite(ServerPlayer p,ServerPlayer target){
        unlocked(p);unlocked(target);if(p==target)throw new IllegalArgumentException("请选择其他在线玩家。");
        if(target.getTeam()!=null)throw new IllegalArgumentException("对方已经在队伍中，请对方先离队。");
        if(p.getTeam()==null)create(p,p.getScoreboardName()+"的小队");
        invites(p.server).put(target.getUUID(),new Invite(p.getUUID(),p.getTeam().getName(),p.server.overworld().getGameTime()+1200));
        target.sendSystemMessage(Component.literal(p.getScoreboardName()+" 邀请你加入 "+p.getTeam().getDisplayName().getString()+"；O → 队伍接受，或 /mtparty accept（60 秒内）。"));
        state(target,"收到组队邀请；接受后各自操控自己与自己的命名牌友军。");
    }
    public static void accept(ServerPlayer p){
        unlocked(p);var invite=invites(p.server).get(p.getUUID());if(invite==null)throw new IllegalArgumentException("没有有效邀请，或邀请已过期。");
        var sender=p.server.getPlayerList().getPlayer(invite.from);var team=p.getScoreboard().getPlayerTeam(invite.team);
        if(sender==null || team==null || sender.getTeam()!=team)throw new IllegalArgumentException("邀请者已离线或更换队伍，请重新邀请。");
        unlocked(sender);if(p.getTeam()!=null)throw new IllegalArgumentException("请先离开当前队伍。");
        p.getScoreboard().addPlayerToTeam(p.getScoreboardName(),team);invites(p.server).remove(p.getUUID());state(sender,p.getScoreboardName()+" 已接受邀请。");
    }
    public static void leave(ServerPlayer p){
        unlocked(p);var team=p.getTeam();if(team==null)throw new IllegalArgumentException("当前没有队伍。");
        p.getScoreboard().removePlayerFromTeam(p.getScoreboardName());invites(p.server).remove(p.getUUID());
        if(team.getName().matches("mt_[0-9a-f]{13}") && team.getPlayers().isEmpty())p.getScoreboard().removePlayerTeam(team);
    }
    public static void state(ServerPlayer p,String message){
        var invite=invites(p.server).get(p.getUUID());var from=invite==null?null:p.server.getPlayerList().getPlayer(invite.from);
        var team=invite==null?null:p.getScoreboard().getPlayerTeam(invite.team);
        BattleNetwork.send(p,new TrailsNetwork.PartyState(p.getTeam()==null?"":p.getTeam().getDisplayName().getString(),from==null?"":from.getScoreboardName(),team==null?"":team.getDisplayName().getString(),message));
    }
    public static String handle(ServerPlayer p,TrailsNetwork.PartyRequest request){
        String message="组队共用原版队伍；未组队玩家默认是对手。";
        try{switch(request.operation()){
            case "create"->{create(p,request.value());message="队伍已创建。";}
            case "invite"->{var target=p.server.getPlayerList().getPlayer(UUID.fromString(request.value()));if(target==null)throw new IllegalArgumentException("玩家已离线。");invite(p,target);message="邀请已发出，等待对方接受。";}
            case "accept"->{accept(p);message="已加入队伍；你只控制自己与自己的命名牌友军。";}
            case "leave"->{leave(p);message="已离队；生物的个人归属保留。";}
            case "state"->{}
            default->throw new IllegalArgumentException("未知队伍操作。");
        }}catch(IllegalArgumentException error){message=error.getMessage();}
        state(p,message);return message;
    }
    public static void commands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher){
        dispatcher.register(Commands.literal("mtparty")
                .executes(c->{state(c.getSource().getPlayerOrException(),"O → 队伍；/mtparty create 名称 | invite 玩家 | accept | leave");return 1;})
                .then(Commands.literal("create").then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->command(c.getSource().getPlayerOrException(),"create",StringArgumentType.getString(c,"name")))))
                .then(Commands.literal("invite").then(Commands.argument("player",EntityArgument.player()).executes(c->command(c.getSource().getPlayerOrException(),"invite",EntityArgument.getPlayer(c,"player").getUUID().toString()))))
                .then(Commands.literal("accept").executes(c->command(c.getSource().getPlayerOrException(),"accept","")))
                .then(Commands.literal("leave").executes(c->command(c.getSource().getPlayerOrException(),"leave",""))));
    }
    private static int command(ServerPlayer p,String op,String value){var message=handle(p,new TrailsNetwork.PartyRequest(op,value));p.sendSystemMessage(Component.literal(message+" 队伍："+(p.getTeam()==null?"未组队":p.getTeam().getDisplayName().getString())+"；详见 O → 队伍。"));return 1;}
    public static void stop(MinecraftServer server){INVITES.remove(server);}
    private TrailsTeams(){}
}
