package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsSmokeScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import org.lwjgl.glfw.GLFW;

/** Runs after the existing end-to-end movement/inventory/exit scenario. */
final class TrailsWeaponSmoke {
    private static int mode = -1, ticks;
    private static boolean red, nativeDraw, airborne;
    private static long posesBefore, impactsBefore, cameraBefore, effectsBefore, numbersBefore, visualBefore;
    private static net.minecraft.world.phys.Vec3 fixed;
    private static int fixedSamples;
    private static long frozenBefore;
    private static boolean resumed;
    private static void prepare() {
        var mc = Minecraft.getInstance();
        int scenario=mode>=23?(mode==26?23:mode==28?24:0):mode;
        mc.getSingleplayerServer().execute(() -> TrailsSmokeScenario.prepare(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(), scenario));
        fixed=null; fixedSamples=0; posesBefore=TrailsPerformances.renderedPoses; impactsBefore=TrailsImpacts.received; cameraBefore=TrailsCinematics.sampledFrames; effectsBefore=TrailsEffects.renderedEffects; numbersBefore=TrailsImpacts.renderedNumbers; visualBefore=TrailsPerformances.visualWeaponFrames;
        ticks = 0; BattleClient.yaw = 0; BattleClient.pitch = 28;
    }
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mode == -1) { mode = 0; prepare(); return; }
        ticks++;
        if (mode>=23) { optionsAndWalls();return; }
        if (ticks == 6) { BattleClient.distance = 17; BattleClient.pitch = 48; BattleClient.yaw = 0; }
        boolean playerMode=mode<6 || mode>=11;
        if (playerMode && ticks == 10) mc.screen.keyPressed(GLFW.GLFW_KEY_1, 0, 0);
        if (mode==17 && ticks==14) {
            mc.player.getInventory().selected=6;
            TrailsClient.send("select",-1,6,0,0);
        }
        if (playerMode && ticks == 16) {
            if (!(mc.screen instanceof TrailsScreen)) throw new IllegalStateException("Weapon skill screen missing");
            var expected = java.util.List.of(net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.IRON_AXE,
                    net.minecraft.world.item.Items.BOW, net.minecraft.world.item.Items.CROSSBOW, net.minecraft.world.item.Items.MACE, net.minecraft.world.item.Items.TRIDENT).get(mode<6?mode:mode<17?mode-11:mode-17);
            if (!mc.player.getMainHandItem().is(mode==17?net.minecraft.world.item.Items.BREAD:expected)) throw new IllegalStateException("QA held wrong weapon for mode " + mode);
            mc.screen.keyPressed(mode<6?GLFW.GLFW_KEY_F:mode<17?GLFW.GLFW_KEY_ENTER:GLFW.GLFW_KEY_V, 0, 0);
            if (!((TrailsScreen)mc.screen).preparing().equals(mode<6?"weapon_skill":mode<17?"attack":"burst")) throw new IllegalStateException("Weapon F did not prepare skill");
            if (mode<6 && !((TrailsScreen)mc.screen).previewShape().equals(com.matuvent.mineturn.battle.TrailsWeaponSkills.shape(mc.player.getMainHandItem())))
                throw new IllegalStateException("Weapon preview shape mismatch");
            MineTurn.LOGGER.info("QA_WEAPON_PREVIEW: {} {}", expected, ((TrailsScreen)mc.screen).previewShape());
        }
        if (playerMode && ticks == 22) {
            shot("action-" + mode + "-preview"); mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
        if (playerMode && ticks == 28 && (!TrailsClient.view.busy() || TrailsPerformances.count() == 0))
            throw new IllegalStateException("Committed weapon skill has no native windup");
        if (mode >= 6 && mode < 8 && TrailsClient.active()) {
            var actor = mc.level.getEntity(TrailsClient.view.actor());
            if (actor instanceof net.minecraft.world.entity.LivingEntity living && TrailsPerformances.red(living)) {
                red = true;
                if (mode == 7 && living.isUsingItem()) nativeDraw = true;
                if (ticks == 18) shot(mode == 6 ? "breeze-red-windup" : "skeleton-red-draw");
            }
            if (mode == 6 && mc.player.getY() > -56) { airborne = true; if (ticks == 62) shot("breeze-airborne"); }
        }
        if (mode >= 8 && mode <= 10 && ticks == 16) { mc.screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0); mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); }
        if (mode >= 8 && mode <= 10 && ticks == 22) { if (TrailsPerformances.count() == 0) throw new IllegalStateException("Friendly skill animation missing"); shot("friend-" + mode); }
        if (playerMode && (ticks==34 || ticks==46 || ticks==64)) shot("action-"+mode+"-frame-"+ticks);
        if (playerMode && ticks>28) {
            var frame=TrailsPerformances.frame(mc.player);
            if (frame!=null && TrailsCinematics.active()) {
                var camera=mc.gameRenderer.getMainCamera().getPosition();
                if (fixed!=null && camera.distanceTo(fixed)>.0001) throw new IllegalStateException("Action camera moved during fixed shot: "+camera.distanceTo(fixed));
                fixed=camera;fixedSamples++;
            }
        }
        if (ticks > (mode==6?140:playerMode?112:85)) {
            if (playerMode && (fixedSamples<4 || TrailsCinematics.sampledFrames<=cameraBefore || TrailsImpacts.received<=impactsBefore || TrailsEffects.renderedEffects<=effectsBefore || TrailsImpacts.renderedNumbers<=numbersBefore))
                throw new IllegalStateException("Missing action/camera/impact evidence: mode="+mode+" fixed="+fixedSamples);
            if (playerMode && !mc.player.getMainHandItem().is(net.minecraft.world.item.Items.BOW) && !mc.player.getMainHandItem().is(net.minecraft.world.item.Items.CROSSBOW)
                    && TrailsPerformances.renderedPoses<=posesBefore) throw new IllegalStateException("Authored limb animation did not render");
            if(mode==17 && TrailsPerformances.visualWeaponFrames<=visualBefore)throw new IllegalStateException("S-Break did not render primary weapon while food was selected");
            if (playerMode) MineTurn.LOGGER.info("QA_ACTION: mode={} fixedSamples={} cameraFrames={} poseFrames={} impacts={} effectFrames={} numberFrames={}",mode,fixedSamples,TrailsCinematics.sampledFrames-cameraBefore,TrailsPerformances.renderedPoses-posesBefore,TrailsImpacts.received-impactsBefore,TrailsEffects.renderedEffects-effectsBefore,TrailsImpacts.renderedNumbers-numbersBefore);
            if (++mode <= 22) { prepare(); return; }
            if (!red || !nativeDraw || !airborne) throw new IllegalStateException("Missing native evidence: red=" + red + " draw=" + nativeDraw + " airborne=" + airborne);
            mc.getSingleplayerServer().execute(() -> TrailsSmokeScenario.stop(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));
            mode = 23; ticks = 0;
        }

    }
    private static void optionsAndWalls() {
        var mc=Minecraft.getInstance();
        if(mode==23) {
            if(ticks==28 && (TrailsClient.active() || TrailsPerformances.count()!=0 || TrailsEffects.count()!=0 || TrailsCinematics.active()))
                throw new IllegalStateException("Presentation remained after exit");
            if(ticks==30)mc.setScreen(new TrailsConfigScreen(null));
            if(ticks==35) {
                try {
                    var path=mc.gameDirectory.toPath().resolve("config/mineturn-presentation.json");
                    var json=new com.google.gson.JsonObject();json.addProperty("qaPreserveUnknown",731);java.nio.file.Files.writeString(path,json.toString());
                }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                toggle("演出镜头");toggle("伤害数字");toggle("武器特效");
                if(TrailsPresentationOptions.camera || TrailsPresentationOptions.numbers || TrailsPresentationOptions.effects)throw new IllegalStateException("Preferences did not toggle through UI");
                mc.screen.onClose();mode=24;prepare();
            }
        } else if(mode==24) {
            if(ticks==16){mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);}
            if(ticks>90){
                if(TrailsCinematics.sampledFrames!=cameraBefore || TrailsEffects.renderedEffects!=effectsBefore || TrailsImpacts.renderedNumbers!=numbersBefore
                        || TrailsImpacts.received<=impactsBefore || TrailsPerformances.renderedPoses<=posesBefore)throw new IllegalStateException("Disabled presentation affected gameplay or kept drawing");
                mc.getSingleplayerServer().execute(()->TrailsSmokeScenario.stop(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));
                mode=25;ticks=0;
            }
        } else if(mode==25) {
            if(ticks==12)mc.setScreen(new TrailsConfigScreen(null));
            if(ticks==18){
                toggle("演出镜头");toggle("伤害数字");toggle("武器特效");TrailsPresentationOptions.load();
                try{
                    var json=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(mc.gameDirectory.toPath().resolve("config/mineturn-presentation.json"))).getAsJsonObject();
                    if(json.get("qaPreserveUnknown").getAsInt()!=731 || !TrailsPresentationOptions.camera || !TrailsPresentationOptions.effects || !TrailsPresentationOptions.numbers || TrailsPresentationOptions.shake)
                        throw new IllegalStateException("Presentation preferences did not persist/preserve fields");
                }catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}
                shot("presentation-settings");mc.screen.onClose();mode=26;prepare();
            }
        } else if(mode==26) {
            if(ticks==16){mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);}
            if(ticks>20 && TrailsCinematics.active()) {
                var point=mc.gameRenderer.getMainCamera().getPosition();
                if(!mc.level.getBlockState(net.minecraft.core.BlockPos.containing(point)).getCollisionShape(mc.level,net.minecraft.core.BlockPos.containing(point)).isEmpty())
                    throw new IllegalStateException("Fixed camera entered nearby wall");
            }
            if(ticks>90){mc.getSingleplayerServer().execute(()->TrailsSmokeScenario.stop(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));mode=27;ticks=0;}
        } else if(mode==27 && ticks>25) {
            frozenBefore=ProjectileAnimations.pausedFrames;mode=28;prepare();
        } else if(mode==28) {
            if(ticks==31){mc.screen.keyPressed(GLFW.GLFW_KEY_V,0,0);mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);}
            if(ticks>70 && TrailsClient.active() && !TrailsPerformances.bursting()) {
                var actor=mc.level.getEntity(TrailsClient.view.actor());
                if(actor instanceof net.minecraft.world.entity.LivingEntity living && living!=mc.player && TrailsPerformances.frame(living)!=null)resumed=true;
            }
            if(ticks>135) {
                if(!resumed || ProjectileAnimations.pausedFrames<=frozenBefore)throw new IllegalStateException("S-Break failed to pause/resume the enemy projectile presentation");
                MineTurn.LOGGER.info("QA_COUNTER_S: pausedProjectileFrames={} resumed={}",ProjectileAnimations.pausedFrames-frozenBefore,resumed);
                mc.getSingleplayerServer().execute(()->TrailsSmokeScenario.stop(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));mode=29;ticks=0;
            }
        } else if(mode==29 && ticks>25) {
            if(TrailsClient.active() || TrailsPerformances.count()!=0 || TrailsEffects.count()!=0)throw new IllegalStateException("Final exit stranded presentation");
            MineTurn.LOGGER.info("TRAILS_WEAPON_SMOKE_PASSED: 18 weapon actions + counter S-Break, fixed cameras, authored poses, glow effects and native particles, damage numbers, species animations, wall clearance, persisted UI preferences, disabled-mode gameplay and exit cleanup");
            mc.stop();
        }
    }
    private static void toggle(String name){
        Minecraft.getInstance().screen.children().stream().filter(c->c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().startsWith(name))
                .map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();
    }
    private static void shot(String name) {
        if (name.contains("preview") && !name.equals("action-0-preview")) return;
        if (name.startsWith("action-")) {
            String[] parts=name.split("-"); int action=Integer.parseInt(parts[1]);
            if (!java.util.Set.of(0,1,4,5,11,15,17,21).contains(action)) return;
            if (name.endsWith("64") && action!=5) return;
        }
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "at-qa-" + name + "-" + System.getProperty("mineturn.qaTag") + ".png", mc.getMainRenderTarget(), ignored -> {});
    }
    private TrailsWeaponSmoke() {}
}
