package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.data.CombatData;
import com.matuvent.mineturn.network.BattleNetwork;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Synchronous server request workload; not a socket latency or FPS benchmark. */
@GameTestHolder("mineturn")
@PrefixGameTestTemplate(false)
public final class PreviewLoadGameTests {
    private static final class Client implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final List<BattleNetwork.Preview> replies=new ArrayList<>();
        Client(GameTestHelper h,int index){
            var cookie=CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(),"mt-preview"+index),false);
            player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
            var connection=new Connection(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel(connection);
            player.getServer().getPlayerList().placeNewPlayer(connection,player,cookie);
            player.connection=new ServerGamePacketListenerImpl(player.getServer(),connection,player,cookie){
                @Override public boolean hasChannel(ResourceLocation id){return id.equals(BattleNetwork.Preview.TYPE.id());}
                @Override public void send(Packet<?> packet){
                    if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof BattleNetwork.Preview preview)replies.add(preview);
                }
            };
            player.setGameMode(GameType.SURVIVAL);
            var pos=h.absolutePos(new BlockPos(4+4*index,1,4));
            player.teleportTo(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        }
        public void close(){player.getServer().getPlayerList().remove(player);player.discard();channel.finishAndReleaseAll();}
    }
    @GameTest(template="empty",timeoutTicks=400)
    public static void mixedPreviewBurstHasBoundedIndependentRepliesAndFreshTerrain(GameTestHelper h){
        for(int x=0;x<24;x++)for(int z=0;z<12;z++)h.setBlock(x,0,z,Blocks.STONE);
        var clients=new ArrayList<Client>();BattleSession battle=null;
        var mob=h.spawnWithNoFreeWill(EntityType.PILLAGER,new BlockPos(20,1,8));
        try{
            for(int i=0;i<4;i++)clients.add(new Client(h,i));
            var data=CombatData.current;var brains=new HashMap<>(data.mobs());brains.remove("minecraft:pillager");
            battle=new BattleSession(clients.getFirst().player,mob,new CombatData.Snapshot(data.actions(),brains,data.items(),data.sources()),clients.getFirst().player);
            for(int i=1;i<clients.size();i++)battle.add(clients.get(i).player);
            var starts=clients.stream().map(c->c.player.position()).toList();
            double av=battle.clock.time();long revision=battle.revision;
            int requests=0,replies=0;
            var obstacle=BlockPos.containing(starts.getFirst().add(1,0,0));
            for(int tick=0;tick<40;tick++){
                battle.ticks++;battle.actor=clients.get(tick%4).player;battle.budget=new TurnBudget(4);
                if(tick==20 || tick==30)for(int dy=0;dy<2;dy++)h.getLevel().setBlock(obstacle.above(dy),(tick==20?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
                for(int i=0;i<clients.size();i++){
                    var client=clients.get(i);client.replies.clear();var destination=starts.get(i).add(1,0,0);
                    int staleId=tick*100+i*2,currentId=staleId+1;
                    for(int repeat=0;repeat<12;repeat++){
                        BattleManager.request(client.player,new BattleNetwork.Request(battle.id,revision-1,"potion_preview",0,"",-1,destination,staleId));
                        BattleManager.request(client.player,new BattleNetwork.Request(battle.id,revision,repeat==0?"preview":"potion_preview",0,"",-1,destination,currentId));
                        requests+=2;
                    }
                    h.assertTrue(client.replies.size()==2,"Duplicate previews exceeded per-member current/stale reply quota: "+client.replies.size());
                    var stale=client.replies.get(0);var current=client.replies.get(1);
                    boolean expected=i==tick%4 && !(i==0 && tick>=20 && tick<30);
                    h.assertTrue(!stale.valid() && stale.requestId()==staleId && stale.path().isEmpty(),"Stale reply lost identity or computed route");
                    h.assertTrue(current.requestId()==currentId && current.battle().equals(battle.id) && current.revision()==revision && current.destination().equals(destination),"Preview response crossed request/player identity");
                    h.assertTrue(current.valid()==expected,"Preview ignored turn permission or changed terrain at tick "+tick+" player "+i+": "+current.reason());
                    h.assertTrue(!current.valid() || !current.path().isEmpty(),"Valid preview omitted its path");
                    replies+=client.replies.size();
                }
                h.assertTrue(battle.budget.remaining()==4 && battle.budget.canAct() && battle.clock.time()==av && battle.revision==revision && battle.motion==null && battle.shot==null,"Preview workload mutated combat state");
                for(int i=0;i<clients.size();i++)h.assertTrue(clients.get(i).player.position().equals(starts.get(i)),"Preview workload moved a player");
            }
            h.assertTrue(requests==3840 && replies==320,"Workload fixture did not exercise expected request count");
            com.matuvent.mineturn.MineTurn.LOGGER.info("Preview server workload: {} requests, {} replies, 4 members, 40 simulated combat ticks",requests,replies);
        }finally{
            if(battle!=null)battle.close("test");
            for(var client:clients)client.close();mob.discard();
        }
        h.succeed();
    }
}
