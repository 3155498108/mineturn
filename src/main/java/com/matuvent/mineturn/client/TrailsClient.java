package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class TrailsClient {
    public static final KeyMapping SWITCH = new KeyMapping("key.mineturn.trails_switch", GLFW.GLFW_KEY_R, "key.categories.mineturn");
    public static final KeyMapping BURST = new KeyMapping("key.mineturn.trails_burst", GLFW.GLFW_KEY_V, "key.categories.mineturn");
    public static final KeyMapping PRIMARY = new KeyMapping("key.mineturn.trails_primary", GLFW.GLFW_KEY_B, "key.categories.mineturn");
    public static TrailsNetwork.View view;
    public static List<TrailsNetwork.Fighter> fighters = List.of();
    private static final List<TrailsNetwork.Fighter> assembling = new ArrayList<>();
    public static boolean preview;
    public static double radius = 12;
    private static int readyTicks, cutTicks, cutTotal;
    private static String cutText = "";
    private static boolean burstCut;
    private static boolean held;
    private static int ticks;
    private static int smokeReadyTicks;

    public static void initialize(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> { e.register(SWITCH); e.register(BURST); e.register(PRIMARY); });
        TrailsNetwork.receiveView = p -> {
            if (!BattleClient.active() || !BattleClient.state.battle().equals(p.battle())) return;
            view = p;
            var mc = Minecraft.getInstance();
            if (mc.screen instanceof BattleScreen || mc.screen == null) mc.setScreen(new TrailsScreen());
        };
        TrailsNetwork.receiveRoster = p -> {
            if (!active() || !view.battle().equals(p.battle()) || view.revision() != p.revision()) return;
            if (p.page() == 0) assembling.clear();
            assembling.addAll(p.fighters());
            if (p.last()) fighters = List.copyOf(assembling);
        };
        TrailsNetwork.receiveCue = p -> {
            radius = p.radius();
            if (p.kind().equals("ready")) readyTicks = p.ticks();
            if (p.kind().equals("enter") || p.kind().equals("burst")) {
                cutTicks = cutTotal = p.ticks(); cutText = p.text(); readyTicks = 0;
                burstCut = p.kind().equals("burst");
            }
        };
        NeoForge.EVENT_BUS.addListener(TrailsClient::tick);
        NeoForge.EVENT_BUS.addListener(TrailsClient::hud);
        NeoForge.EVENT_BUS.addListener(TrailsClient::world);
    }
    public static boolean active() { return view != null && BattleClient.active() && view.battle().equals(BattleClient.state.battle()); }
    public static void reset() {
        view = null; fighters = List.of(); assembling.clear(); preview = false; held = false; readyTicks = 0;
        if (!burstCut || Minecraft.getInstance().level == null) cutTicks = 0;
    }
    public static void send(String op, int target, int slot, double x, double z) {
        PacketDistributor.sendToServer(new TrailsNetwork.Command(active() ? view.battle() : TrailsNetwork.NONE,
                active() ? view.revision() : -1, op, target, slot, x, z));
    }
    private static void tick(ClientTickEvent.Post e) {
        if (Boolean.getBoolean("mineturn.encounterSmoke")) TrailsSmoke.tick();
        ticks++;
        if (readyTicks > 0) readyTicks--;
        if (cutTicks > 0) cutTicks--;
        var mc = Minecraft.getInstance();
        if (Boolean.getBoolean("mineturn.clientSmoke") && mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen
                && mc.getOverlay() == null && ++smokeReadyTicks >= 40) {
            com.matuvent.mineturn.MineTurn.LOGGER.info("TRAILS_CLIENT_SMOKE_READY: real title screen rendered; battle visuals are not covered");
            mc.stop(); return;
        }
        if (mc.player == null || mc.level == null) { reset(); return; }
        if (active()) { held = false; preview = false; return; }
        if (mc.screen != null) { held = false; preview = false; return; }
        boolean down = SWITCH.isDown();
        if (down && !held) { preview = true; send("preview", -1, 0, 0, 0); }
        if (!down && held) { preview = false; send("toggle", -1, 0, 0, 0); }
        held = down;
        while (PRIMARY.consumeClick()) send("primary", -1, 0, 0, 0);
    }
    private static void hud(RenderGuiEvent.Post e) {
        if (active()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var g = e.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (preview || readyTicks > 0) {
            int alpha = readyTicks > 0 ? (int) (150 + 100 * (.5 + .5 * Math.sin(ticks * .15))) : 255;
            String text = preview ? "松开 " + SWITCH.getTranslatedKeyMessage().getString() + " 进入 AT 战斗 · 半径 " + (int) radius
                    : "背袭先制就绪 · 按住 " + SWITCH.getTranslatedKeyMessage().getString();
            int x = (w - mc.font.width(text)) / 2;
            g.fill(x - 10, h / 2 + 36, x + mc.font.width(text) + 10, h / 2 + 58, alpha << 24 | 0x9a1029);
            g.drawString(mc.font, text, x, h / 2 + 43, 0xffffffff);
        }
        cutIn(g, w, h);
    }
    private static void world(RenderLevelStageEvent e) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (!preview && !(active() && mc.screen instanceof TrailsScreen)) return;
        var center = mc.player.position(); var camera = e.getCamera().getPosition();
        var pose = new PoseStack(); pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource(); var lines = buffers.getBuffer(RenderType.lines());
        if (preview) for (int i = 0; i < 144; i++) {
            double a = i * Math.PI * 2 / 144, x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            LevelRenderer.renderLineBox(pose, lines, new AABB(x - .045, center.y + .08, z - .045, x + .045, center.y + .15, z + .045), 1, .12f, .26f, 1);
        }
        if (active() && mc.screen instanceof TrailsScreen screen) {
            var victim = mc.level.getEntity(screen.selectedTarget());
            var actor = mc.level.getEntity(view.actor());
            if (victim != null && actor != null) {
                LevelRenderer.renderLineBox(pose, lines, victim.getBoundingBox().inflate(.06), 1, .7f, .3f, 1);
                var stack = mc.player.getMainHandItem();
                if (actor == mc.player && stack.is(net.minecraft.tags.ItemTags.SWORDS)) {
                    var dir = victim.position().subtract(actor.position()); double heading = Math.atan2(dir.z, dir.x);
                    for (int i = 0; i <= 48; i++) {
                        double a = heading - Math.PI / 4 + i * Math.PI / 96;
                        double x = actor.getX() + 3 * Math.cos(a), z = actor.getZ() + 3 * Math.sin(a);
                        LevelRenderer.renderLineBox(pose, lines, new AABB(x - .04, actor.getY() + .06, z - .04, x + .04, actor.getY() + .12, z + .04), 1, .2f, .3f, .9f);
                    }
                } else if (actor == mc.player && stack.getItem() instanceof net.minecraft.world.item.MaceItem) {
                    for (int i = 0; i < 96; i++) {
                        double a = i * Math.PI / 48, x = victim.getX() + 2 * Math.cos(a), z = victim.getZ() + 2 * Math.sin(a);
                        LevelRenderer.renderLineBox(pose, lines, new AABB(x - .04, victim.getY() + .06, z - .04, x + .04, victim.getY() + .12, z + .04), 1, .2f, .3f, .9f);
                    }
                }
            }
        }
        buffers.endBatch(RenderType.lines());
    }
    public static void cutIn(GuiGraphics g, int w, int h) {
        if (cutTicks <= 0) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, w, h, 0xb0000000);
        var font = Minecraft.getInstance().font;
        double progress = 1 - cutTicks / (double) cutTotal;
        int center = h / 2;
        int height = (int) (Math.sin(Math.PI * progress) * Math.min(h * .4, 100));
        for (int y = -height; y < height; y++) {
            int jag = ((y / 12) % 2) * 22;
            int slide = (int) ((1 - Math.min(1, progress * 5)) * w);
            g.fill(slide + jag, center + y - w / 40, w + slide, center + y - w / 40 + 1, 0xffbd1338);
        }
        if (progress > .12 && progress < .88) {
            g.fill(w / 8, center - 20, w * 7 / 8, center + 24, 0xff111018);
            g.drawCenteredString(font, cutText, w / 2, center - 2, 0xffffffff);
            g.drawCenteredString(font, "/// BREAK THE ORDER ///", w / 2, center + 11, 0xffffd7df);
        }
        g.pose().popPose();
    }
}
