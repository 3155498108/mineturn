package com.matuvent.mineturn.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class TrailsMusicScreen extends Screen {
    private final Screen parent;
    public TrailsMusicScreen(Screen parent) { super(Component.literal("战斗音乐")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}
    @Override protected void init() {
        int left = width / 2 - 110;
        addRenderableWidget(Button.builder(Component.literal("战斗 BGM " + (TrailsMusic.enabled ? "开" : "关")), ignored -> {
            TrailsMusic.enabled = !TrailsMusic.enabled;
            TrailsMusic.save();
            rebuildWidgets();
        }).bounds(left, 34, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("曲库 " + TrailsMusic.label()), ignored -> {
            TrailsMusic.cycle();
            rebuildWidgets();
        }).bounds(left + 114, 34, 106, 20).build());
        addRenderableWidget(Button.builder(Component.literal("音量 −"), ignored -> changeVolume(-.1f)).bounds(left, 60, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("音量 +"), ignored -> changeVolume(.1f)).bounds(left + 145, 60, 75, 20).build());
        addRenderableWidget(Button.builder(Component.literal("重读本地音乐"), ignored -> TrailsMusic.reloadLocal()).bounds(left, 86, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("打开音乐文件夹"), ignored -> net.minecraft.Util.getPlatform().openFile(TrailsMusic.directory().toFile()))
                .bounds(left, 112, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), ignored -> onClose()).bounds(left + 60, height - 29, 100, 20).build());
    }
    private void changeVolume(float amount) {
        TrailsMusic.volume = Math.max(0, Math.min(1, Math.round((TrailsMusic.volume + amount) * 100) / 100f));
        TrailsMusic.save();
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        graphics.fill(0, 0, width, height, 0xf2101521);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xffffd486);
        graphics.drawCenteredString(font, Math.round(TrailsMusic.volume * 100) + "%", width / 2, 66, -1);
        int yText = 140;
        String text = TrailsMusic.library + "/normal.ogg：普通 / 缺省\n"
                + TrailsMusic.library + "/advantage.ogg：先制\n"
                + TrailsMusic.library + "/boss.ogg：Boss\n"
                + "重读后生效，音量也受原版“音乐”控制。\n" + TrailsMusic.status();
        for (var line : font.split(Component.literal(text), Math.max(100, width - 40))) {
            if (yText + 10 >= height - 36) break;
            graphics.drawString(font, line, 20, yText, 0xffd9e4f5);
            yText += 12;
        }
        super.render(graphics, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
