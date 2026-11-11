package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsCompatibilityScenario;
import com.matuvent.mineturn.battle.TrailsUpdateScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import org.lwjgl.glfw.GLFW;

/** Drives the same GUI and server packets used in normal AT play. */
final class TrailsCompatibilitySmoke {
    private static int stage, age, boss;
    private static boolean nativeMod;
    private static void next(int value) { stage = value; age = 0; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException("AUTO_QA " + message); }
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> work) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> work.accept(server));
    }
    private static void shot(String name) {
        var minecraft = Minecraft.getInstance();
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "auto-" + name + ".png", minecraft.getMainRenderTarget(), ignored -> {});
    }
    private static void press(String text) {
        Minecraft.getInstance().screen.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().equals(text))
                .map(child -> (Button) child).findFirst().orElseThrow().onPress();
    }
    static void tick() {
        if (!TrailsUpdateSmoke.complete) { TrailsUpdateSmoke.tick(); return; }
        var minecraft = Minecraft.getInstance();
        if (minecraft.getOverlay() != null) return;
        if (++age > 300) {
            shot("failed-" + stage);
            throw new IllegalStateException("AUTO_QA timeout at " + stage);
        }
        if (stage == 0) {
            nativeMod = Boolean.getBoolean("mineturn.nativeAutoQa");
            server(TrailsCompatibilityScenario::weapon);
            next(1);
        } else if (stage == 1 && age > 35 && TrailsCompatibilityScenario.ready && minecraft.screen instanceof TrailsScreen) {
            if (nativeMod) {
                check(TrailsGuideScreen.contents(2).stream().anyMatch(article -> article.title().equals(TrailsCompatibilityScenario.weaponName)), "modded weapon missing from guide");
                minecraft.setScreen(new TrailsGuideScreen(minecraft.screen));
                press("武器");
                minecraft.screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(TrailsCompatibilityScenario.weaponName);
                next(2);
            } else next(3);
        } else if (stage == 2 && age > 12) {
            shot("weapon-guide");
            minecraft.screen.onClose();
            next(3);
        } else if (stage == 3 && age > 12 && minecraft.screen instanceof TrailsScreen screen) {
            screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
            check(screen.previewShape() != null && screen.previewShape().kind() == com.matuvent.mineturn.battle.TrailsArea.Kind.SINGLE, "attribute weapon preview mismatch");
            next(4);
        } else if (stage == 4 && age > 12) {
            shot("weapon-preview");
            minecraft.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            next(5);
        } else if (stage == 5) {
            server(TrailsCompatibilityScenario::inspectWeapon);
            if (TrailsCompatibilityScenario.verified) {
                shot("weapon-impact");
                server(TrailsUpdateScenario::stop);
                next(6);
            }
        } else if (stage == 6 && age > 30 && !TrailsClient.active()) {
            server(TrailsCompatibilityScenario::burst);
            next(13);
        } else if (stage == 13 && age > 35 && TrailsCompatibilityScenario.ready && minecraft.screen instanceof TrailsScreen screen) {
            screen.keyPressed(GLFW.GLFW_KEY_V, 0, 0);
            check(screen.previewShape() != null && screen.previewShape().kind() == com.matuvent.mineturn.battle.TrailsArea.Kind.SINGLE, "attribute S preview mismatch");
            next(14);
        } else if (stage == 14 && age > 12) {
            shot("burst-preview");
            minecraft.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            next(15);
        } else if (stage == 15) {
            var frame = TrailsPerformances.frame(minecraft.player);
            if (frame != null && frame.style().startsWith("burst_")) {
                check(TrailsPerformances.visualItem(minecraft.player, net.minecraft.world.item.ItemStack.EMPTY, minecraft.player.getMainArm()) == minecraft.player.getMainHandItem(), "S presentation used a different stack with the same item id");
            }
            server(TrailsCompatibilityScenario::inspectBurst);
            if (TrailsCompatibilityScenario.verified) {
                shot("burst-impact");
                server(TrailsUpdateScenario::stop);
                next(16);
            }
        } else if (stage == 16 && age > 30 && !TrailsClient.active()) {
            if (nativeMod) { server(server -> TrailsCompatibilityScenario.boss(server, boss)); next(7); }
            else finish();
        } else if (stage == 7 && age > 45 && TrailsCompatibilityScenario.ready && TrailsClient.active()) {
            check(TrailsClient.fighters.stream().anyMatch(fighter -> fighter.id() == TrailsCompatibilityScenario.targetId && fighter.enemy() && fighter.threat() != null && fighter.threat().golden()), "external gold warning missing");
            check(TrailsGuideScreen.contents(5).stream().anyMatch(article -> article.text().contains("mutantmonsters:" + TrailsCompatibilityScenario.BOSSES[boss])), "external boss guide missing");
            shot(TrailsCompatibilityScenario.BOSSES[boss] + "-gold");
            server(TrailsCompatibilityScenario::releaseBoss);
            next(8);
        } else if (stage == 8 && age > 70 && TrailsCompatibilityScenario.verified) {
            if (++boss == TrailsCompatibilityScenario.BOSSES.length) { server(TrailsCompatibilityScenario::friendly); next(9); }
            else { server(server -> TrailsCompatibilityScenario.boss(server, boss)); next(7); }
        } else if (stage == 9 && age > 40 && TrailsCompatibilityScenario.ready && minecraft.screen instanceof TrailsScreen screen) {
            screen.keyPressed(GLFW.GLFW_KEY_F, 0, 0);
            check(screen.previewShape() != null && screen.previewShape().radius() == .8, "external ally preview mismatch");
            next(10);
        } else if (stage == 10 && age > 12) {
            shot("ally-preview");
            minecraft.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            next(11);
        } else if (stage == 11) {
            server(TrailsCompatibilityScenario::inspectFriendly);
            if (TrailsCompatibilityScenario.verified) {
                shot("ally-heal");
                server(TrailsUpdateScenario::stop);
                next(12);
            }
        } else if (stage == 12 && age > 40 && !TrailsClient.active()) finish();
    }
    private static void finish() {
        MineTurn.LOGGER.info("TRAILS_AUTO_CLIENT_PASSED attribute weapon/S preview/CP/damage/stack selection; external mod={}", nativeMod);
        Minecraft.getInstance().stop();
    }
    private TrailsCompatibilitySmoke() {}
}
