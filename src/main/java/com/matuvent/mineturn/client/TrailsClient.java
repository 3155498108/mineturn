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
    public static final KeyMapping CONFIG = new KeyMapping("key.mineturn.trails_config", GLFW.GLFW_KEY_O, "key.categories.mineturn");
    public static TrailsNetwork.Settings settings = new TrailsNetwork.Settings(12, 6, 4, false, "");
    private static net.minecraft.world.phys.Vec3 previousFocus = net.minecraft.world.phys.Vec3.ZERO;
    private static List<TrailsNetwork.Fighter> introAllies = List.of(), introEnemies = List.of();
    public static TrailsNetwork.View view;
    public static List<TrailsNetwork.Fighter> fighters = List.of();
    private static final List<TrailsNetwork.Fighter> assembling = new ArrayList<>();
    public static boolean preview;
    public static double radius = 12;
    static long movementSequence;
    private static int readyTicks, cutTicks, cutTotal;
    private static String cutText = "";
    private static boolean burstCut;
    private static boolean held;
    private static int ticks;
    private static int smokeReadyTicks;

    public static void initialize(IEventBus bus) {
        bus.addListener((RegisterKeyMappingsEvent e) -> { e.register(SWITCH); e.register(BURST); e.register(PRIMARY); e.register(CONFIG); });
        net.neoforged.fml.ModList.get().getModContainerById("mineturn").orElseThrow().registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class, (container, parent) -> new TrailsConfigScreen(parent));
        TrailsNetwork.receiveSettings = p -> {
            settings = p; radius = p.encounter();
            if (Minecraft.getInstance().screen instanceof TrailsConfigScreen screen) screen.received(p);
        };
        TrailsNetwork.receiveView = p -> {
            if (!BattleClient.active() || !BattleClient.state.battle().equals(p.battle())) return;
            if (view == null) previousFocus = BattleClient.focus;
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
                if (!burstCut) { introAllies = fighters.stream().filter(f -> !f.enemy()).toList(); introEnemies = fighters.stream().filter(TrailsNetwork.Fighter::enemy).toList(); }
            }
        };
        NeoForge.EVENT_BUS.addListener(TrailsClient::tick);
        NeoForge.EVENT_BUS.addListener(TrailsClient::hud);
        NeoForge.EVENT_BUS.addListener(TrailsClient::world);
    }
    public static boolean active() { return view != null && BattleClient.active() && view.battle().equals(BattleClient.state.battle()); }
    public static net.minecraft.world.phys.Vec3 cameraFocus(float partial) { return previousFocus.lerp(BattleClient.focus, Math.clamp(partial, 0, 1)); }
    public static void reset() {
        view = null; fighters = List.of(); assembling.clear(); preview = false; held = false; readyTicks = 0;
        movementSequence = 0;
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
        while (CONFIG.consumeClick()) mc.setScreen(new TrailsConfigScreen(mc.screen));
        if (active()) {
            held = false; preview = false;
            if (mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                    && (!view.alliedTurn() || view.actor() != mc.player.getId())) mc.setScreen(new TrailsScreen());
            var actor = mc.level.getEntity(view.actor());
            previousFocus = BattleClient.focus;
            if (actor != null && view.alliedTurn()) BattleClient.focus = BattleClient.focus.lerp(actor.position().add(0, 1, 0), .4);
            return;
        }
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
        if (Boolean.getBoolean("mineturn.encounterSmoke")) TrailsSmoke.frame(e.getCamera().getPosition());
        if (!preview && !(active() && mc.screen instanceof TrailsScreen)) return;
        var center = mc.player.position(); var camera = e.getCamera().getPosition();
        var pose = new PoseStack(); pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = mc.renderBuffers().bufferSource(); var lines = buffers.getBuffer(RenderType.lines());
        if (preview) for (int i = 0; i < 144; i++) {
            double a = i * Math.PI * 2 / 144, x = center.x + Math.cos(a) * radius, z = center.z + Math.sin(a) * radius;
            LevelRenderer.renderLineBox(pose, lines, new AABB(x - .045, center.y + .08, z - .045, x + .045, center.y + .15, z + .045), 1, .12f, .26f, 1);
        }
        if (active() && mc.screen instanceof TrailsScreen screen) {
            if (view.alliedTurn()) for (int i = 0; i < 128; i++) {
                var origin = view.origin(); double a = i * Math.PI / 64;
                double x = origin.x + view.movement() * Math.cos(a), z = origin.z + view.movement() * Math.sin(a);
                LevelRenderer.renderLineBox(pose, lines, new AABB(x - .025, origin.y + .06, z - .025, x + .025, origin.y + .10, z + .025), .2f, 1, .65f, .85f);
            }
            var shape = screen.previewShape();
            var actor = mc.level.getEntity(screen.preparing().equals("burst") ? mc.player.getId() : view.actor());
            if (shape != null && actor != null) {
                var aim = screen.aimVector();

                for (var fighter : fighters) {
                    var entity = mc.level.getEntity(fighter.id());
                    if (entity == null) continue;
                    boolean friendlySkill = screen.preparing().equals("skill") && !(actor instanceof net.minecraft.world.entity.animal.IronGolem);
                    boolean side = friendlySkill ? !fighter.enemy() : fighter.enemy();
                    if (side && (shape.kind() == com.matuvent.mineturn.battle.TrailsArea.Kind.SINGLE ? fighter.id() == screen.selectedTarget() : shape.contains(actor.position(), aim, entity.position())))
                        LevelRenderer.renderLineBox(pose, lines, entity.getBoundingBox().inflate(.06), 1, .75f, .2f, 1);
                }
            }
        }
        buffers.endBatch(RenderType.lines());
        // Chunk rendering supplies its own matrices; install the event matrices explicitly for plain POSITION_COLOR geometry.
        if (active() && mc.screen instanceof TrailsScreen screen && screen.previewShape() != null) {
            var actor = mc.level.getEntity(screen.preparing().equals("burst") ? mc.player.getId() : view.actor());
            if (actor != null) {
                var matrix = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack(); matrix.pushMatrix(); matrix.set(e.getModelViewMatrix());
                com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
                com.mojang.blaze3d.systems.RenderSystem.enableBlend(); com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
                com.mojang.blaze3d.systems.RenderSystem.disableCull();
                com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);
                var vertices = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS,
                        com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR);
                var shape = screen.previewShape();
                if (shape.kind() != com.matuvent.mineturn.battle.TrailsArea.Kind.SINGLE || shape.cast() > 0) {
                    TrailsRangeRenderer.draw(pose, vertices, actor.getPosition(e.getPartialTick().getGameTimeDeltaPartialTick(false)), shape, screen.aimVector());
                    com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(vertices.buildOrThrow());
                }
                com.mojang.blaze3d.systems.RenderSystem.enableCull(); com.mojang.blaze3d.systems.RenderSystem.disableBlend();
                matrix.popMatrix(); com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
            }
        }
        if (Boolean.getBoolean("mineturn.encounterSmoke")) TrailsSmoke.afterWorld();
    }
    public static void cutIn(GuiGraphics g, int w, int h) {
        if (cutTicks <= 0) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, w, h, 0xb0000000);
        var font = Minecraft.getInstance().font;
        double progress = 1 - cutTicks / (double) cutTotal;
        if (!burstCut) { versus(g, w, h, progress); g.pose().popPose(); return; }
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
    private static void versus(GuiGraphics g, int w, int h, double p) {
        var font = Minecraft.getInstance().font;
        int size = Math.min(40, Math.max(24, w / 14));
        double enter = Math.min(1, p / .42), ease = 1 - Math.pow(1 - enter, 3);
        int left = (int) (-w / 2.0 + ease * (w / 2.0 + w / 2.0 - 80)), right = w - left;
        int y = h / 2 - size / 2;
        int shake = p > .42 && p < .62 ? (int) (Math.sin(p * 150) * 4) : 0;
        g.fill(left - 90 + shake, y - 22, left + 65 + shake, y + size + 23, 0xee136e70);
        g.fill(right - 65 - shake, y - 22, right + 90 - shake, y + size + 23, 0xeeb91c42);
        introTeam(g, introAllies, left - 68 + shake, y, size, false);
        introTeam(g, introEnemies, right - 68 - shake, y, size, true);
        if (p > .40) {
            if (p < .47) g.fill(0, 0, w, h, 0x66ffffff);
            g.pose().pushPose(); g.pose().translate(w / 2f, h / 2f - 14, 0); g.pose().scale(4, 4, 1);
            g.drawCenteredString(font, "VS", 0, 0, 0xffffd55e); g.pose().popPose();
            g.drawCenteredString(font, cutText, w / 2, y + size + 35, 0xffffffff);
        }
    }
    private static void introTeam(GuiGraphics g, List<TrailsNetwork.Fighter> team, int x, int y, int size, boolean enemy) {
        int shown = Math.min(6, team.size());
        for (int i = 0; i < shown; i++) TrailsPortraits.draw(g, team.get(i).id(), x + i % 3 * (size + 4), y + (i / 3) * (size + 4) - (shown > 3 ? size / 2 : 0), size, enemy);
        g.drawCenteredString(Minecraft.getInstance().font, (enemy ? "敌方 " : "友方 ") + team.size(), x + size * 3 / 2, y - 17 - (shown > 3 ? size / 2 : 0), -1);
    }
}
