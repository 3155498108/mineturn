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
    private static void prepare() {
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> TrailsSmokeScenario.prepare(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst(), mode));
        ticks = 0; BattleClient.yaw = 0; BattleClient.pitch = 28;
    }
    static void tick() {
        var mc = Minecraft.getInstance();
        if (mode == -1) { mode = 0; prepare(); return; }
        ticks++;
        if (mode == 11) {
            if (ticks > 25) {
                if (TrailsClient.active() || TrailsPerformances.count() != 0) throw new IllegalStateException("Native pose/tint remained after exit");
                MineTurn.LOGGER.info("TRAILS_WEAPON_SMOKE_PASSED: six weapon skill previews, vanilla windups, enemy red overlay, skeleton bow draw, Breeze airborne and sheep/golem/chicken poses, exit cleanup");
                mc.stop();
            }
            return;
        }
        if (ticks == 6) { BattleClient.distance = 17; BattleClient.pitch = 48; BattleClient.yaw = 0; }
        if (mode < 6 && ticks == 10) mc.screen.keyPressed(GLFW.GLFW_KEY_1, 0, 0);
        if (mode < 6 && ticks == 16) {
            if (!(mc.screen instanceof TrailsScreen)) throw new IllegalStateException("Weapon skill screen missing");
            var expected = java.util.List.of(net.minecraft.world.item.Items.IRON_SWORD, net.minecraft.world.item.Items.IRON_AXE,
                    net.minecraft.world.item.Items.BOW, net.minecraft.world.item.Items.CROSSBOW, net.minecraft.world.item.Items.MACE, net.minecraft.world.item.Items.TRIDENT).get(mode);
            if (!mc.player.getMainHandItem().is(expected)) throw new IllegalStateException("QA held wrong weapon for mode " + mode);
            mc.screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
            if (!((TrailsScreen)mc.screen).preparing().equals("weapon_skill")) throw new IllegalStateException("Weapon F did not prepare skill");
            if (!((TrailsScreen)mc.screen).previewShape().equals(com.matuvent.mineturn.battle.TrailsWeaponSkills.shape(mc.player.getMainHandItem())))
                throw new IllegalStateException("Weapon preview shape mismatch");
            MineTurn.LOGGER.info("QA_WEAPON_PREVIEW: {} {}", expected, ((TrailsScreen)mc.screen).previewShape());
        }
        if (mode < 6 && ticks == 22) {
            shot("weapon-" + mode + "-preview"); mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
        }
        if (mode < 6 && ticks == 28 && (!TrailsClient.view.busy() || TrailsPerformances.count() == 0))
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
        if (mode >= 8 && ticks == 16) { mc.screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0); mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0); }
        if (mode >= 8 && ticks == 22) { if (TrailsPerformances.count() == 0) throw new IllegalStateException("Friendly skill animation missing"); shot("friend-" + mode); }
        if (mode < 6 && ticks == 38) shot("weapon-" + mode + "-action");
        if (ticks > (mode == 6 ? 125 : 65)) {
            if (++mode <= 10) { prepare(); return; }
            if (!red || !nativeDraw || !airborne) throw new IllegalStateException("Missing native evidence: red=" + red + " draw=" + nativeDraw + " airborne=" + airborne);
            mc.getSingleplayerServer().execute(() -> TrailsSmokeScenario.stop(mc.getSingleplayerServer().getPlayerList().getPlayers().getFirst()));
            mode = 11; ticks = 0;
        }

    }
    private static void shot(String name) {
        var mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "at-qa-" + name + "-" + System.getProperty("mineturn.qaTag") + ".png", mc.getMainRenderTarget(), ignored -> {});
    }
    private TrailsWeaponSmoke() {}
}
