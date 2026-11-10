package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsUpdateScenario;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

final class TrailsUpdateSmoke {
    private static int stage, age, total, category, boss;
    private static boolean twilight;
    private static void next(int value) { stage = value; age = 0; }
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException("UPDATE_QA " + message); }
    private static void server(java.util.function.Consumer<net.minecraft.server.MinecraftServer> work) { Minecraft.getInstance().getSingleplayerServer().execute(() -> work.accept(Minecraft.getInstance().getSingleplayerServer())); }
    private static void shot(String name) {
        var minecraft = Minecraft.getInstance();
        net.minecraft.client.Screenshot.grab(minecraft.gameDirectory, "update-" + name + ".png", minecraft.getMainRenderTarget(), ignored -> {});
    }
    private static void press(String label) {
        var screen = Minecraft.getInstance().screen;
        screen.children().stream().filter(child -> child instanceof Button button && button.getMessage().getString().equals(label))
                .map(child -> (Button) child).findFirst().orElseThrow().onPress();
    }
    static void tick() {
        var minecraft = Minecraft.getInstance();
        if (++total > 6000) throw new IllegalStateException("Update QA timeout stage=" + stage);
        if (minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            if (total % 200 == 0) MineTurn.LOGGER.info("UPDATE_QA_WAIT screen={}", minecraft.screen == null ? "none" : minecraft.screen.getClass().getName());
            if (total == 200) shot("boot-prompt");
            return;
        }
        if (minecraft.getOverlay() != null) return;
        age++;
        minecraft.options.pauseOnLostFocus = false;
        minecraft.getToasts().clear();
        if (stage == 0) {
            twilight = Boolean.getBoolean("mineturn.twilightQa");
            minecraft.options.guiScale().set(2);
            server(TrailsUpdateScenario::setup);
            next(1);
        } else if (stage == 1 && TrailsUpdateScenario.ready && age > 50 && minecraft.screen instanceof TrailsScreen) {
            check(TrailsClient.GUIDE.getTranslatedKeyMessage().getString().equalsIgnoreCase("H"), "guide key not registered");
            shot("battle");
            press("说明");
            check(minecraft.screen instanceof TrailsGuideScreen, "battle guide button failed");
            next(2);
        } else if (stage == 2 && age == 8) {
            check(!TrailsGuideScreen.contents(category).isEmpty(), "empty guide category " + category);
            press(new String[]{"玩法", "角色", "武器", "友军", "敌军", "Boss"}[category == 0 ? 1 : category]);
            if (category == 0) press("玩法");
        } else if (stage == 2 && age > 13) {
            shot("guide-" + category);
            if (++category == 6) {
                check(TrailsGuideScreen.contents(3).stream().anyMatch(article -> article.text().contains("80 CP")), "guide lost chicken CP cost");
                minecraft.screen.onClose();
                check(minecraft.screen instanceof TrailsScreen, "guide failed to restore battle");
                minecraft.screen.keyPressed(GLFW.GLFW_KEY_H, 0, 0);
                check(minecraft.screen instanceof TrailsGuideScreen, "battle H did not open guide");
                minecraft.screen.onClose();
                minecraft.options.guiScale().set(1);
                minecraft.resizeDisplay();
                minecraft.setScreen(new PortraitSheet());
                next(3);
            } else age = 0;
        } else if (stage == 3 && age > 20) {
            shot("portraits");
            check(TrailsPortraits.face("pig").textureHeight() == 32 && TrailsPortraits.face("ghast").width() == 16, "incorrect vanilla portrait UV");
            minecraft.options.guiScale().set(2);
            minecraft.resizeDisplay();
            minecraft.setScreen(new TrailsConfigScreen(new TrailsScreen()));
            press("音乐");
            check(minecraft.screen instanceof TrailsMusicScreen, "music settings button failed");
            next(8);
        } else if (stage == 8 && age > 20) {
            shot("music");
            if (Boolean.getBoolean("mineturn.musicQa")) {
                check(TrailsMusic.choose() != null, "imported OGG did not resolve");
                check(minecraft.getSoundManager().isActive(TrailsMusic.activeTrack()), "battle music stream not active");
            }
            press("战斗 BGM 开");
            next(9);
        } else if (stage == 9 && age > 40) {
            check(TrailsMusic.activeTrack() == null, "music switch failed to stop stream");
            press("音量 +");
            try {
                var saved = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(TrailsMusic.directory().getParent().resolve("music.json"))).getAsJsonObject();
                check(!saved.get("enabled").getAsBoolean() && Math.abs(saved.get("volume").getAsFloat() - TrailsMusic.volume) < .001f, "music settings not saved");
            } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
            press("战斗 BGM 关");
            next(10);
        } else if (stage == 10 && age > 40) {
            if (Boolean.getBoolean("mineturn.musicQa"))
                check(minecraft.getSoundManager().isActive(TrailsMusic.activeTrack()), "music switch failed to restart stream");
            press("音量 −");
            server(TrailsUpdateScenario::stop);
            next(4);
        } else if (stage == 4 && age > 45 && !TrailsClient.active()) {
            check(TrailsMusic.activeTrack() == null, "battle music survived exit");
            minecraft.setScreen(null);
            if (twilight) { server(server -> TrailsUpdateScenario.boss(server, boss)); next(5); }
            else finish();
        } else if (stage == 5 && age > 45 && TrailsClient.active()) {
            check(TrailsClient.fighters.stream().anyMatch(fighter -> fighter.id() == TrailsUpdateScenario.bossId && fighter.enemy() && fighter.threat() != null && fighter.threat().golden()), "gold boss warning missing");
            check(minecraft.level.getEntity(TrailsUpdateScenario.bossId) instanceof net.minecraft.world.entity.LivingEntity living && TrailsWarnings.gold(living), "gold boss model missing");
            shot("twilight-" + TrailsUpdateScenario.BOSSES[boss] + "-gold");
            server(TrailsUpdateScenario::releaseBoss);
            next(6);
        } else if (stage == 6 && age > 100 && !TrailsUpdateScenario.verified) {
            throw new IllegalStateException("Native boss validation failed " + TrailsUpdateScenario.BOSSES[boss]);
        } else if (stage == 6 && age > 65 && TrailsUpdateScenario.verified) {
            if (++boss == TrailsUpdateScenario.BOSSES.length) { server(TrailsUpdateScenario::stop); next(7); }
            else { server(server -> TrailsUpdateScenario.boss(server, boss)); next(5); }
        } else if (stage == 7 && age > 30 && !TrailsClient.active()) finish();
    }
    private static void finish() {
        MineTurn.LOGGER.info("TRAILS_UPDATE_CLIENT_PASSED guide six categories, H/button return, portraits, music lifecycle, twilight={}", twilight);
        Minecraft.getInstance().stop();
    }
    private static final class PortraitSheet extends Screen {
        PortraitSheet() { super(Component.literal("Portrait regression")); }
        @Override public boolean isPauseScreen() { return false; }
        @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
        @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
            graphics.fill(0, 0, width, height, 0xff121725);
            graphics.drawCenteredString(font, title, width / 2, 12, -1);
            for (int i = 0; i < TrailsUpdateScenario.portraits.size(); i++) {
                int entityId = TrailsUpdateScenario.portraits.get(i);
                var entity = minecraft.level.getEntity(entityId);
                if (entity == null) continue;
                int px = 22 + i % 6 * 94, py = 48 + i / 6 * 112;
                TrailsPortraits.draw(graphics, entityId, px, py, 64, false);
                graphics.drawString(font, entity.getName(), px, py + 72, -1);
            }
        }
    }
    private TrailsUpdateSmoke() {}
}
