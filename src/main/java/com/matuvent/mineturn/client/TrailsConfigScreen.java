package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class TrailsConfigScreen extends Screen {
    private final Screen parent;
    private EditBox encounter, vertical, movement,criticalChance,criticalMultiplier;
    private String message = "正在读取当前存档的范围设置…";
    private TrailsNetwork.Settings values;
    public TrailsConfigScreen(Screen parent) { super(Component.literal("AT 战斗设置")); this.parent = parent; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float partial) {}
    @Override protected void init() {
        var settings = values != null ? values : TrailsClient.settings;
        encounter = field(42, settings.encounter()); vertical = field(68, settings.vertical()); movement = field(94, settings.movement());
        criticalChance=field(120,settings.criticalChance()*100);criticalMultiplier=field(146,settings.criticalMultiplier());
        addRenderableWidget(Button.builder(Component.literal("保存并应用"), b -> save()).bounds(width / 2 - 106, 188, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("返回"), b -> onClose()).bounds(width / 2 + 2, 188, 104, 20).build());
        int cell=Math.min(104,(width-28)/4), left=(width-cell*4-12)/2;
        toggle("camera", "演出镜头", left, cell, TrailsPresentationOptions.camera);
        toggle("numbers", "伤害数字", left+cell+4, cell, TrailsPresentationOptions.numbers);
        toggle("shake", "命中微震", left+cell*2+8, cell, TrailsPresentationOptions.shake);
        toggle("effects", "武器特效", left+cell*3+12, cell, TrailsPresentationOptions.effects);
        if (minecraft.player != null) PacketDistributor.sendToServer(new TrailsNetwork.Settings(0, 0, 0,0,1, false, ""));
        else message = "请先进入单人存档，再修改该存档的战斗范围。";
    }
    private void toggle(String key,String name,int x,int width,boolean value) {
        addRenderableWidget(Button.builder(Component.literal(name+"："+(value?"开":"关")), b -> {
            TrailsPresentationOptions.toggle(key);
            boolean current=switch(key) { case "camera" -> TrailsPresentationOptions.camera; case "numbers" -> TrailsPresentationOptions.numbers; case "effects" -> TrailsPresentationOptions.effects; default -> TrailsPresentationOptions.shake; };
            b.setMessage(Component.literal(name+"："+(current?"开":"关")));
        }).bounds(x,height-29,width,20).build());
    }
    private EditBox field(int y, double value) {
        var box = new EditBox(font, width / 2 + 16, y, 86, 20, Component.literal("范围"));
        box.setMaxLength(6); box.setValue(Double.toString(value)); addRenderableWidget(box); return box;
    }
    void received(TrailsNetwork.Settings p) {
        values = p; message = p.message();
        if (encounter != null) { encounter.setValue(Double.toString(p.encounter())); vertical.setValue(Double.toString(p.vertical())); movement.setValue(Double.toString(p.movement()));criticalChance.setValue(Double.toString(p.criticalChance()*100));criticalMultiplier.setValue(Double.toString(p.criticalMultiplier())); }
    }
    private void save() {
        if (minecraft.player == null) return;
        try {
            double a = Double.parseDouble(encounter.getValue()), b = Double.parseDouble(vertical.getValue()), c = Double.parseDouble(movement.getValue());
            if (!Double.isFinite(a) || a < 2 || a > 64 || !Double.isFinite(b) || b < 1 || b > 64 || !Double.isFinite(c) || c < 0 || c > 32) throw new NumberFormatException();
            double chance=Double.parseDouble(criticalChance.getValue())/100,multiplier=Double.parseDouble(criticalMultiplier.getValue());
            if(!Double.isFinite(chance) || chance<0 || chance>1 || !Double.isFinite(multiplier) || multiplier<1 || multiplier>10)throw new NumberFormatException();
            PacketDistributor.sendToServer(new TrailsNetwork.Settings(a, b, c,chance,multiplier, true, "")); message = "正在保存…";
        } catch (NumberFormatException error) { message = "范围或暴击值无效；暴击率 0–100%，倍率 1–10。"; }
    }
    @Override public void render(GuiGraphics g, int x, int y, float partial) {
        g.fill(0, 0, width, height, 0xee101521);
        g.drawCenteredString(font, title, width / 2, 24, 0xffffffff);
        g.drawString(font, "发起战斗半径（格）", width / 2 - 112, 48, 0xffeeeeee);
        g.drawString(font, "入战上下范围（格）", width / 2 - 112, 74, 0xffeeeeee);
        g.drawString(font, "每回合移动半径（格）", width / 2 - 112, 100, 0xffeeeeee);
        g.drawCenteredString(font, font.plainSubstrByWidth(message, width - 16), width / 2, 174, 0xffffd486);
        g.drawString(font,"暴击概率（%）",width/2-112,126,0xffeeeeee);
        g.drawString(font,"暴击伤害倍率",width/2-112,152,0xffeeeeee);
        super.render(g, x, y, partial);
    }
    @Override public void onClose() { minecraft.setScreen(parent instanceof TrailsScreen && !TrailsClient.active() ? null : parent); }
}
