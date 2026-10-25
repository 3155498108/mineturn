package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsSmokeScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;

/** Real damage via server rules, actual world text draw and roster HP text, never fake impact packets. */
final class TrailsCriticalSmoke {
    private static int mode=-1,ticks,impactTick;
    private static long received,white,red,health;
    private static void server(java.util.function.Consumer<net.minecraft.server.level.ServerPlayer> action){var s=Minecraft.getInstance().getSingleplayerServer();s.execute(()->action.accept(s.getPlayerList().getPlayers().getFirst()));}
    private static void prepare(){
        ticks=0;impactTick=0;received=TrailsImpacts.received;white=TrailsImpacts.whiteNumbers;red=TrailsImpacts.redCriticalNumbers;health=TrailsScreen.renderedHealthNumbers;
        TrailsPresentationOptions.numbers=true;TrailsPresentationOptions.camera=true;BattleClient.yaw=0;BattleClient.pitch=25;BattleClient.distance=12;
        server(p->TrailsSmokeScenario.damageNumbers(p,mode));
    }
    static void tick(){
        var mc=Minecraft.getInstance();if(mode==-1){mode=0;prepare();return;}ticks++;
        if(mode==6){if(ticks==15){
            if(TrailsClient.active() || TrailsImpacts.lastReceived!=null)throw new IllegalStateException("Exit retained damage numbers");
            MineTurn.LOGGER.info("TRAILS_CRITICAL_CLIENT_PASSED: actual white normal, larger red critical, high normal remains white, immune BLOCK, killing critical survives natural close, numeric HP bars, saved chance/multiplier and exit cleanup; white={} red={} health={}",TrailsImpacts.whiteNumbers,TrailsImpacts.redCriticalNumbers,TrailsScreen.renderedHealthNumbers);mc.stop();
        }return;}
        if(mode==5){settings();return;}
        if(ticks==10){BattleClient.yaw=0;BattleClient.pitch=25;BattleClient.distance=12;}
        if(ticks==15){if(!(mc.screen instanceof TrailsScreen))throw new IllegalStateException("Damage QA battle UI missing");mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);}
        if(TrailsImpacts.received>received && impactTick==0){
            impactTick=ticks;var p=TrailsImpacts.lastReceived;
            if(p==null || p.critical()!=(mode==1 || mode==4) || mode==3 && p.amount()!=0 || mode!=3 && p.amount()<=0 || mode==2 && p.amount()<12)throw new IllegalStateException("Authoritative critical/amount failed mode="+mode+" packet="+p);
        }
        if(impactTick>0 && (ticks==impactTick+4 || ticks==impactTick+8))shot("damage-"+mode+"-"+(ticks-impactTick));
        if(ticks==55){
            if(impactTick==0 || TrailsScreen.renderedHealthNumbers<=health || (mode==1 || mode==4?TrailsImpacts.redCriticalNumbers<=red:TrailsImpacts.whiteNumbers<=white))throw new IllegalStateException("World numbers / HP text not actually drawn "+mode);
            if(mode!=1 && mode!=4 && TrailsImpacts.redCriticalNumbers!=red || TrailsImpacts.color(false)!=0xffffffff || TrailsImpacts.color(true)!=0xffff3030 || TrailsImpacts.size(true)<=TrailsImpacts.size(false))throw new IllegalStateException("Critical color / scale mismatch");
            shot("hp-"+mode);MineTurn.LOGGER.info("QA_CRITICAL_OK: mode={} impactTick={} packet={}",mode,impactTick,TrailsImpacts.lastReceived);mode++;if(mode<5)prepare();else{ticks=0;mc.setScreen(new TrailsConfigScreen(mc.screen));}
        }
    }
    private static void settings(){
        var mc=Minecraft.getInstance();
        if(ticks==8){
            var boxes=mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.EditBox).map(c->(net.minecraft.client.gui.components.EditBox)c).toList();
            if(boxes.size()!=5)throw new IllegalStateException("Critical setting fields missing");boxes.get(3).setValue("25");boxes.get(4).setValue("2");
            mc.screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("保存并应用")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();
        }
        if(ticks==24){
            if(TrailsClient.settings.criticalChance()!=.25 || TrailsClient.settings.criticalMultiplier()!=2)throw new IllegalStateException("Critical GUI settings did not save/synchronize");
            shot("critical-settings");mc.screen.onClose();server(TrailsSmokeScenario::stop);mode=6;ticks=0;
        }
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"critical-"+name+"-"+System.getProperty("mineturn.qaTag")+".png",mc.getMainRenderTarget(),message->{});}
    private TrailsCriticalSmoke(){}
}
