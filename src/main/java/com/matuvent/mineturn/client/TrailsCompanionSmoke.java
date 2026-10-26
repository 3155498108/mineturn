package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsCompanionScenario;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.*;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.*;
import org.lwjgl.glfw.GLFW;

/** Native client interactions and keyboard callbacks, plus a separate save/restart pass. */
final class TrailsCompanionSmoke {
    private static int stage, age, total;
    private static boolean reload;
    private static Pig pig;
    private static void check(boolean condition,String text){if(!condition)throw new IllegalStateException("COMPANION_QA_CLIENT: "+text);}
    private static void next(int value){stage=value;age=0;MineTurn.LOGGER.info("COMPANION_QA_CLIENT_PHASE {}",value);}
    private static Pig pig(Minecraft mc){return mc.level.getEntitiesOfClass(Pig.class,mc.player.getBoundingBox().inflate(18)).stream().findFirst().orElse(null);}
    private static void key(Minecraft mc,int action){mc.keyboardHandler.keyPress(mc.getWindow().getWindow(),GLFW.GLFW_KEY_R,0,action,0);}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,"companion-"+name+".png",mc.getMainRenderTarget(),message->{});}
    static void tick(){
        if(com.matuvent.mineturn.platform.RuntimePlatform.production())return;
        var mc=Minecraft.getInstance();if(++total>1800)throw new IllegalStateException("Companion client timeout stage="+stage);
        if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
        age++;mc.getToasts().clear();
        if(stage==0){reload=System.getProperty("mineturn.qaTag","").contains("reload");mc.options.guiScale().set(2);mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.getSingleplayerServer().execute(()->TrailsCompanionScenario.setup(mc.getSingleplayerServer(),reload));next(1);return;}
        switch(stage){
            case 1->{if(TrailsCompanionScenario.ready && age>25){mc.setScreen(null);
                if(reload){int slot=-1;for(int i=0;i<9;i++)if(mc.player.getInventory().getItem(i).is(MineTurn.COMPANION_TOKEN.get())){slot=i;break;}
                    check(slot>=0,"saved token missing after restart");mc.player.getInventory().selected=slot;next(3);}
                else{pig=pig(mc);check(pig!=null,"fixture pig missing");mc.gameMode.interact(mc.player,pig,InteractionHand.MAIN_HAND);next(2);}}}
            case 2->{if(mc.player.getMainHandItem().is(MineTurn.COMPANION_TOKEN.get()) && pig(mc)==null){mc.setScreen(new InventoryScreen(mc.player));next(3);}}
            case 3->{if(age==8 && !reload)shot("inventory-token");if(age>15){mc.setScreen(null);var block=new BlockPos(3,-60,0);
                // The reloaded player is at the follow destination; use a nearby block there.
                if(reload)block=mc.player.blockPosition().offset(2,-1,0);
                mc.gameMode.useItemOn(mc.player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(block),Direction.UP,block,false));next(4);}}
            case 4->{pig=pig(mc);if(pig!=null && mc.player.getMainHandItem().isEmpty()){
                check(pig.hasCustomName() && pig.getName().getString().equals("QA伙伴") && pig.getHealth()==5,"name or health changed during storage");
                shot(reload?"persisted-release":"release");
                mc.getSingleplayerServer().execute(()->{var p=mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst();p.teleportTo(reload?3.5:10.5,-59,.5);});next(5);}}
            case 5->{if(age>35 && pig.getX()>(reload?0:5.5) && pig.distanceToSqr(mc.player)<9){
                check(pig.getHealth()==5,"follow damaged companion");
                mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,pig.getEyePosition());next(51);}}
            case 51->{if(age==8){shot(reload?"persisted-follow":"follow");
                mc.player.input.shiftKeyDown=true;mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY));
                mc.gameMode.interact(mc.player,pig,InteractionHand.MAIN_HAND);
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player,ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY));mc.player.input.shiftKeyDown=false;next(6);}}
            case 6->{if(mc.player.getMainHandItem().is(MineTurn.COMPANION_TOKEN.get()) && pig(mc)==null){
                if(reload){mc.getSingleplayerServer().execute(()->TrailsCompanionScenario.verify(mc.getSingleplayerServer()));next(12);}
                else{mc.getSingleplayerServer().execute(()->TrailsCompanionScenario.battle(mc.getSingleplayerServer()));next(7);}}}
            case 7->{if(TrailsClient.active() && mc.screen instanceof TrailsScreen && age>25){key(mc,GLFW.GLFW_PRESS);TrailsClient.SWITCH.setDown(true);next(8);}}
            case 8->{if(age==8)key(mc,GLFW.GLFW_REPEAT);
                if(age>30){check(!TrailsClient.active() && !TrailsClient.preview && mc.options.getCameraType()==CameraType.FIRST_PERSON,"held exit re-entered or kept camera");
                    key(mc,GLFW.GLFW_RELEASE);next(9);}}
            case 9->{if(age==20){check(!TrailsClient.active() && !TrailsClient.preview,"exit release re-entered");shot("r-release-stays-out");key(mc,GLFW.GLFW_PRESS);}
                if(age==23){check(TrailsClient.preview,"fresh R did not preview");key(mc,GLFW.GLFW_RELEASE);next(10);}}
            case 10->{if(TrailsClient.active() && mc.screen instanceof TrailsScreen && age>25){
                mc.getSingleplayerServer().execute(()->TrailsCompanionScenario.ownTurn(mc.getSingleplayerServer()));next(11);}}
            case 11->{if(age==10){key(mc,GLFW.GLFW_PRESS);key(mc,GLFW.GLFW_RELEASE);}
                if(age>40){check(!TrailsClient.active() && !TrailsClient.preview && mc.options.getCameraType()==CameraType.FIRST_PERSON,"quick exit release re-entered");
                    shot("r-fast-release-stays-out");mc.getSingleplayerServer().execute(()->TrailsCompanionScenario.verify(mc.getSingleplayerServer()));next(12);}}
            case 12->{if(TrailsCompanionScenario.verified && age>20){MineTurn.LOGGER.info(reload?"TRAILS_COMPANION_RESTART_PASSED: disk inventory+server receipt, same UUID/name/HP, release, actual following and recall":"TRAILS_COMPANION_CLIENT_PASSED: actual C2S name-tag capture, token model, placement, HP/name, navigation following, sneak recall, held and quick R release do not re-enter, next R cycle works");mc.stop();}}
            default->{}
        }
    }
    private TrailsCompanionSmoke(){}
}
