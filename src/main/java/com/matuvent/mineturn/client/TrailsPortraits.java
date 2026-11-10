package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Reuses the loaded entity's vanilla skin/head texture, including the player's real skin. */
final class TrailsPortraits {
    private static final java.util.Map<ResourceLocation, Integer> textureHeights = new java.util.HashMap<>();
    static void clearTextures() { textureHeights.clear(); }
    static void draw(GuiGraphics g, int entityId, int x, int y, int size, boolean enemy) {
        var mc = Minecraft.getInstance(); var entity = mc.level == null ? null : mc.level.getEntity(entityId);
        g.fill(x - 1, y - 1, x + size + 1, y + size + 1, enemy ? 0xffe83f62 : 0xff43dcb5);
        g.fill(x, y, x + size, y + size, 0xff151725);
        if (entity instanceof AbstractClientPlayer p) { PlayerFaceRenderer.draw(g, p.getSkin(), x, y, size); return; }
        if (!(entity instanceof LivingEntity living)) { g.drawCenteredString(mc.font, "?", x + size / 2, y + size / 2 - 4, -1); return; }
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(living.getType());
        Face face = id.getNamespace().equals("minecraft") ? face(id.getPath()) : null;
        if (face == null) {
            // Modded and non-cuboid heads use their real renderer, fitted to the square.
            int scale = Math.max(1, (int) ((size - 3) / Math.max(living.getBbHeight(), living.getBbWidth() * 1.3)));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x, y, x + size, y + size, scale,
                    0, x + size / 2f, y + size / 2f, living);
            return;
        }
        var texture = mc.getEntityRenderDispatcher().getRenderer(living).getTextureLocation(living);
        int width = Math.max(1, size * face.width / Math.max(face.width, face.height));
        int height = Math.max(1, size * face.height / Math.max(face.width, face.height));
        g.blit(texture, x + (size - width) / 2, y + (size - height) / 2, width, height,
                face.u, face.v, face.width, face.height, face.textureWidth, textureHeight(texture, face));
    }

    /** Read only the PNG header, retaining logical UVs for high-resolution resource packs. */
    private static int textureHeight(ResourceLocation texture, Face face) {
        return textureHeights.computeIfAbsent(texture, ignored -> {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) return face.textureHeight;
            try (var input = new java.io.DataInputStream(resource.get().open())) {
                if (input.readLong() != 0x89504e470d0a1a0aL) return face.textureHeight;
                input.readInt();
                if (input.readInt() != 0x49484452) return face.textureHeight;
                int width = input.readInt(), height = input.readInt();
                return width > 0 && height > 0 ? Math.max(1, (int) Math.round((double) height * face.textureWidth / width)) : face.textureHeight;
            } catch (java.io.IOException exception) { return face.textureHeight; }
        });
    }

    record Face(int u, int v, int width, int height, int textureWidth, int textureHeight) {}
    static Face face(String type) {
        return switch (type) {
            case "zombie", "husk", "drowned", "zombified_piglin" -> new Face(8, 8, 8, 8, 64, 64);
            case "skeleton", "stray", "bogged", "wither_skeleton", "creeper", "blaze", "enderman", "pig" -> new Face(8, 8, 8, 8, 64, 32);
            case "piglin", "piglin_brute" -> new Face(8, 8, 10, 8, 64, 64);
            case "sheep" -> new Face(8, 8, 6, 6, 64, 32);
            case "chicken" -> new Face(2, 3, 4, 6, 64, 32);
            case "wolf" -> new Face(4, 4, 6, 6, 64, 32);
            case "cow", "mooshroom" -> new Face(6, 6, 8, 8, 64, 32);
            case "villager", "wandering_trader", "witch", "pillager", "vindicator", "evoker", "illusioner", "zombie_villager" -> new Face(8, 8, 8, 10, 64, 64);
            case "iron_golem" -> new Face(8, 12, 8, 10, 128, 128);
            case "spider", "cave_spider" -> new Face(40, 4, 8, 8, 64, 32);
            case "ghast" -> new Face(16, 16, 16, 16, 64, 32);
            case "wither" -> new Face(8, 8, 8, 8, 64, 64);
            default -> null;
        };
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
