package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsCpScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.*;
import org.lwjgl.glfw.GLFW;

final class TrailsCpSmoke {
    private static int stage,age,total;private static boolean reload;private static long turn;
    private static void next(int n){stage=n;age=0;}
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException("CP_CLIENT_QA: "+reason);}
    private static void shot(String name){var mc=Minecraft.getInstance();net.minecraft.client.Screenshot.grab(mc.gameDirectory,"cp-"+name+"-"+System.getProperty("mineturn.qaTag")+".png",mc.getMainRenderTarget(),message->{});}
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> action){var server=Minecraft.getInstance().getSingleplayerServer();server.execute(()->action.accept(server));}
    static void tick(){
        var mc=Minecraft.getInstance();if(++total>1200)throw new IllegalStateException("CP QA timeout stage="+stage);
        if(mc.player==null || mc.getSingleplayerServer()==null)return;age++;mc.getToasts().clear();mc.options.pauseOnLostFocus=false;
        if(stage==0){reload=System.getProperty("mineturn.qaTag","").contains("reload");mc.options.guiScale().set(2);server(s->TrailsCpScenario.setup(s,reload));next(1);return;}
        if(stage==1 && TrailsCpScenario.ready && age>30){
            if(reload){mc.setScreen(null);int slot=-1;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(MineTurn.COMPANION_TOKEN.get()))slot=i;check(slot>=0,"saved token missing");mc.player.getInventory().selected=slot;next(7);}
            else if(mc.screen instanceof TrailsScreen){check(TrailsClient.fighters.stream().anyMatch(f->f.cp()==100 && f.skillCost()==80),"roster omitted initial CP/80 cost");mc.screen.keyPressed(GLFW.GLFW_KEY_F,0,0);next(2);}return;
        }
        if(stage==2 && age>12){check(((TrailsScreen)mc.screen).preparing().equals("skill") && TrailsClient.view.cp()==137 && TrailsScreen.renderedCpNumbers>0,"preview changed CP or bars did not render");shot("preview80");mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);next(3);}
        if(stage==3 && age>40 && !TrailsClient.view.busy()){
            check(TrailsClient.fighters.stream().anyMatch(f->f.skillCost()==80 && f.cp()==20) && TrailsClient.view.cp()==137,"chicken did not pay its own 80 CP");server(TrailsCpScenario::ownTurn);next(4);
        }
        if(stage==4 && age>12){turn=TrailsClient.view.turn();mc.screen.keyPressed(GLFW.GLFW_KEY_F,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);next(5);}
        if(stage==5 && age>15){check(((TrailsScreen)mc.screen).preparing().equals("skill"),"insufficient CP skipped preview");shot("insufficient20");check(TrailsClient.view.turn()==turn && !TrailsClient.view.busy() && TrailsClient.fighters.stream().anyMatch(f->f.skillCost()==80 && f.cp()==20),"insufficient CP cast/turn mutation");TrailsClient.send("toggle",-1,0,0,0);next(6);}
        if(stage==6 && age>20 && !TrailsClient.active()){
            mc.setScreen(null);mc.player.getInventory().selected=1;
            var bird=mc.level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,mc.player.getBoundingBox().inflate(7)).stream().filter(e->e.getType()==EntityType.CHICKEN).findFirst().orElseThrow();
            mc.gameMode.interact(mc.player,bird,InteractionHand.MAIN_HAND);next(8);
        }
        if(stage==7 && age>15){
            var block=mc.player.blockPosition().offset(2,-1,0);mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(block),Direction.UP,block,false));next(9);
        }
        if(stage==8 && age>20 && mc.player.getMainHandItem().is(MineTurn.COMPANION_TOKEN.get())){server(TrailsCpScenario::verifyStored);next(10);}
        if(stage==9 && age>20 && mc.player.getMainHandItem().isEmpty()){server(TrailsCpScenario::verifyReleased);next(10);}
        if(stage==10 && age>20 && TrailsCpScenario.verified){shot(reload?"restart-release":"stored");MineTurn.LOGGER.info(reload?"TRAILS_CP_RESTART_PASSED: disk receipt releases same companion with 20 CP and 3 HP":"TRAILS_CP_CLIENT_PASSED: CP HUD/cost preview, 100 to 20 chicken, independent player CP, insufficient rejection and native name-tag capture");mc.stop();}
    }
    private TrailsCpSmoke(){}
}
