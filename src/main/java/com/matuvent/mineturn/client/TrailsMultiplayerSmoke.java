package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Drives real client screens and normal C2S packets; authoritative assertions live on the QA server. */
final class TrailsMultiplayerSmoke {
    private static String phase="";private static int age,boot;
    private static boolean sawBurst;private static long cameraBefore;
    static void phase(String value){if(net.neoforged.fml.loading.FMLLoader.isProduction() || System.getProperty("mineturn.multiplayerSmoke","").isEmpty())return;phase=value;age=0;MineTurn.LOGGER.info("MULTI_QA_CLIENT_PHASE {}",value);}
    private static void check(boolean ok,String text){if(!ok)throw new IllegalStateException("MULTI_QA_CLIENT "+text);}
    private static boolean host(){return System.getProperty("mineturn.multiplayerSmoke","").equals("host");}
    private static int other(){return TrailsClient.fighters.stream().filter(f->Minecraft.getInstance().level.getEntity(f.id()) instanceof net.minecraft.world.entity.player.Player && f.id()!=Minecraft.getInstance().player.getId()).findFirst().orElseThrow().id();}
    private static void button(String text){var mc=Minecraft.getInstance();mc.screen.children().stream().filter(w->w instanceof Button b && b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow().onPress();}
    private static void shot(){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"multi-"+phase+".png",mc.getMainRenderTarget(),message->{});}
    static void tick(){
        if(net.neoforged.fml.loading.FMLLoader.isProduction())return;
        var mc=Minecraft.getInstance();if(++boot>15000)throw new IllegalStateException("MULTI_QA_CLIENT timeout "+phase);
        if(mc.player==null || mc.level==null)return;age++;mc.getToasts().clear();
        if(TrailsPerformances.bursting()){sawBurst=true;if(age==12)shot();}
        switch(phase){
            case "pvp_enter","coop_enter","reenter"->{if(age==4)mc.setScreen(null);if(age==8 && host())TrailsClient.send("toggle",-1,0,0,0);}
            case "pvp_view"->{if(age==30){
                check(TrailsClient.active() && TrailsClient.view.controllable()==host() && TrailsClient.view.alliedTurn()==host(),"PvP view ownership");
                check(TrailsClient.view.cp()==(host()?200:100) && TrailsClient.view.primary().equals(host()?"minecraft:iron_sword":"minecraft:iron_axe"),"private CP/weapon");
                check(TrailsClient.fighters.size()==5,"roster pages");BattleClient.yaw=0;BattleClient.pitch=25;BattleClient.distance=13;shot();
                if(!host()){TrailsClient.send("end",-1,0,0,0);PacketDistributor.sendToServer(new TrailsNetwork.MoveInput(TrailsClient.view.battle(),TrailsClient.view.turn(),9999,1,0));}
            }}
            case "pvp_attack"->{if(age==5 && host())TrailsClient.send("attack",other(),0,0,1);}
            case "pvp_burst"->{if(age==1 && !host())TrailsClient.send("burst",other(),0,0,-1);cameraBefore=TrailsCinematics.sampledFrames;}
            case "pvp_cinematic"->{if(age==28)shot();if(age==85){check(sawBurst && TrailsCinematics.sampledFrames>cameraBefore,"remote S fixed camera/animation missing");check(TrailsPerformances.visualWeaponFrames>0,"S weapon never rendered");MineTurn.LOGGER.info("MULTI_QA_RENDER_OK role={} camera={} weaponFrames={}",host()?"host":"guest",TrailsCinematics.sampledFrames,TrailsPerformances.visualWeaponFrames);}}
            case "team_open"->{if(age==20){check(!TrailsClient.active(),"closed PvP view retained");mc.setScreen(new TrailsConfigScreen(null));button("队伍");}if(age==45){check(mc.screen instanceof TrailsPartyScreen,"O party entry missing");shot();}}
            case "team_invite"->{if(age==12 && host())button("邀请 AT_B");if(age==45)shot();}
            case "team_accept"->{if(age==12 && !host())button("接受邀请");if(age==30){check(!TrailsPartyScreen.state.team().isBlank(),"accepted party state missing");shot();}}
            case "coop_pig"->{
                if(age==25){check(TrailsClient.active() && TrailsClient.view.alliedTurn() && TrailsClient.view.controllable()!=host() && TrailsClient.view.controller().equals("AT_B"),"co-op shares faction but stole controls");shot();
                    if(host()){TrailsClient.send("end",-1,0,0,0);PacketDistributor.sendToServer(new TrailsNetwork.MoveInput(TrailsClient.view.battle(),TrailsClient.view.turn(),10000,1,0));}
                    else mc.screen.keyPressed(GLFW.GLFW_KEY_W,0,0);
                }if(age==33 && !host())mc.screen.keyReleased(GLFW.GLFW_KEY_W,0,0);if(age==70)shot();
            }
            case "coop_wolf"->{if(age==25){check(TrailsClient.view.controllable()==host() && TrailsClient.view.alliedTurn(),"other owner's wolf control");shot();}}
            case "withdraw"->{if(age==12 && !host())TrailsClient.send("toggle",-1,0,0,0);}
            case "withdraw_view"->{if(age==30){check(TrailsClient.active()==host(),"withdraw closed teammates or retained local UI");if(!host())check(mc.options.getCameraType()==net.minecraft.client.CameraType.FIRST_PERSON,"withdraw camera restore");shot();}}
            case "disconnect"->{if(age==20 && !host()){check(TrailsClient.view.controllable(),"disconnect fixture not own turn");shot();MineTurn.LOGGER.info("MULTI_QA_GUEST_PASSED");mc.stop();}}
            case "passed"->{if(age==25){check(!TrailsClient.active() && !TrailsCinematics.active(),"host final cleanup");MineTurn.LOGGER.info("MULTI_QA_HOST_PASSED");mc.stop();}}
            default->{}
        }
    }
    private TrailsMultiplayerSmoke(){}
}
