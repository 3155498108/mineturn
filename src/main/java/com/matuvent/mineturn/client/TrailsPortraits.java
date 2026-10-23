package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/** Reuses the loaded entity's vanilla skin/head texture, including the player's real skin. */
final class TrailsPortraits {
    static void draw(GuiGraphics g, int entityId, int x, int y, int size, boolean enemy) {
        var mc = Minecraft.getInstance(); var entity = mc.level == null ? null : mc.level.getEntity(entityId);
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, enemy ? 0xffe83f62 : 0xff43dcb5);
        g.fill(x, y, x + size, y + size, 0xff151725);
        if (entity instanceof AbstractClientPlayer p) { PlayerFaceRenderer.draw(g, p.getSkin(), x, y, size); return; }
        if (!(entity instanceof LivingEntity living)) { g.drawCenteredString(mc.font, "?", x + size / 2, y + size / 2 - 4, -1); return; }
        var texture = mc.getEntityRenderDispatcher().getRenderer(living).getTextureLocation(living);
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).getPath();
        int u = 8, v = 8, w = 8, h = 8, tw = 64, th = 64;
        switch (type) {
            case "skeleton", "stray", "wither_skeleton", "creeper" -> th = 32;
            case "sheep" -> { u = 6; v = 6; w = 6; h = 6; th = 32; }
            case "chicken" -> { u = 2; v = 3; w = 4; h = 6; th = 32; }
            case "wolf" -> { u = 4; v = 4; w = 6; h = 6; th = 32; }
            case "cow", "mooshroom" -> { u = 6; v = 6; th = 32; }
            case "villager", "wandering_trader", "witch", "pillager", "vindicator", "evoker" -> { h = 10; }
            case "iron_golem" -> { tw = 128; th = 128; v = 12; h = 10; }
            case "spider", "cave_spider" -> { u = 40; v = 4; th = 32; }
            default -> {}
        }
        g.blit(texture, x, y, size, size, u, v, w, h, tw, th);
    }

    static void effects(GuiGraphics g, TrailsNetwork.Fighter fighter, int x, int y, int max, int mouseX, int mouseY) {
        var mc = Minecraft.getInstance(); int i = 0;
        for (var status : fighter.effects()) {
            if (i >= max) break;
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceLocation.parse(status.effect()));
            if (holder.isEmpty()) continue;
            int px = x + i++ * 15;
            var sprite = mc.getMobEffectTextures().get(holder.get());
            g.blit(px, y, 0, 13, 13, sprite);
            if (status.level() > 1) g.drawString(mc.font, Integer.toString(status.level()), px + 8, y + 6, 0xffffff66);
            if (mouseX >= px && mouseX < px + 14 && mouseY >= y && mouseY < y + 14)
                g.renderTooltip(mc.font, net.minecraft.network.chat.Component.literal(holder.get().value().getDisplayName().getString()
                        + " Lv." + status.level() + (status.ticks() < 0 ? " · 持续" : " · 剩余 " + status.ticks() * 5L + " AV")), mouseX, mouseY);
        }
        if (fighter.effects().size() > max) g.drawString(mc.font, "+" + (fighter.effects().size() - max), x + max * 15, y + 3, 0xffeeeeee);
    }
}
