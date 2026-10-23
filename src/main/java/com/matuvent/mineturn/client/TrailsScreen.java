package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Scrollable party/enemy roster; all allied creatures are controlled by the player. */
public final class TrailsScreen extends Screen {
    private int target = -1, allyPage, enemyPage, inventoryPage;
    private boolean items;
    private long revision = -1;
    private boolean orbit;
    private final java.util.Set<Integer> movementKeys = new java.util.HashSet<>();
    private long movementTurn = -1;
    private boolean sentMovement;
    private int movementTick;
    public TrailsScreen() { super(Component.literal("AT 战斗")); }
    public int selectedTarget() { return target; }
    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void renderBackground(GuiGraphics g, int x, int y, float p) {}
    @Override protected void init() { rebuild(); }
    private List<TrailsNetwork.Fighter> side(boolean enemy) { return TrailsClient.fighters.stream().filter(f -> f.enemy() == enemy).toList(); }
    private void command(String op) { TrailsClient.send(op, target, minecraft.player.getInventory().selected, 0, 0); }
    private Button button(String label, int x, int y, int w, Runnable action, boolean enabled) {
        var b = addRenderableWidget(Button.builder(Component.literal(label), ignored -> action.run()).bounds(x, y, w, 20).build());
        b.active = enabled;
        return b;
    }
    private void rebuild() {
        clearWidgets();
        if (!TrailsClient.active() || minecraft == null || minecraft.player == null) return;
        var view = TrailsClient.view;
        boolean own = view.alliedTurn();
        boolean playerTurn = view.actor() == minecraft.player.getId();
        if (!own || !playerTurn) items = false;
        if (items) { inventory(); return; }
        var enemies = side(true);
        if (enemies.stream().noneMatch(e -> e.id() == target)) target = enemies.isEmpty() ? -1 : enemies.getFirst().id();
        int column = Math.min(154, width / 3 - 8);
        roster(false, 8, 57, column);
        roster(true, width - column - 8, 57, column);
        int y = height - 67, cell = Math.max(62, Math.min(90, (width - 24) / 5));
        int left = (width - cell * 5) / 2;
        button("普通攻击", left, y, cell - 3, () -> command("attack"), own && target >= 0);
        var actor = TrailsClient.fighters.stream().filter(f -> f.id() == view.actor()).findFirst();
        var skill = button(playerTurn ? "风弹重锤" : "战技", left + cell, y, cell - 3,
                () -> command(playerTurn ? "wind" : "skill"), own && (!playerTurn || minecraft.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.MaceItem));
        skill.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(playerTurn ? "装备重锤，并消耗背包内 1 个风弹：扩大范围和伤害，增加 AT 硬直。"
                : actor.map(TrailsNetwork.Fighter::skill).orElse("友军战技"))));
        button("道具", left + cell * 2, y, cell - 3, () -> { items = !items; rebuild(); }, own && playerTurn);
        button("防御", left + cell * 3, y, cell - 3, () -> command("guard"), own);
        button("待机", left + cell * 4, y, cell - 3, () -> command("end"), own);
        var burst = button("S-BREAK / " + view.cp() + " CP", width / 2 - 80, y - 24, 160, () -> command("burst"), view.cp() >= view.burstCost());
        burst.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(view.burstName() + " · 消耗 " + view.burstCost() + " CP，可打断行动顺序。")));
    }
    private void inventory() {
        var slots = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 36; i++) {
            var stack = minecraft.player.getInventory().getItem(i);
            if (!stack.isEmpty() && (stack.has(net.minecraft.core.component.DataComponents.FOOD) || stack.is(net.minecraft.world.item.Items.POTION))) slots.add(i);
        }
        int pages = Math.max(1, (slots.size() + 5) / 6);
        inventoryPage = Math.clamp(inventoryPage, 0, pages - 1);
        int start = inventoryPage * 6;
        for (int i = start; i < Math.min(slots.size(), start + 6); i++) {
            final int slot = slots.get(i); var stack = minecraft.player.getInventory().getItem(slot);
            String name = stack.getHoverName().getString() + " ×" + stack.getCount();
            button(font.plainSubstrByWidth(name, 124), width / 2 - 135 + (i - start) % 2 * 138,
                    height - 154 + (i - start) / 2 * 22, 134,
                    () -> TrailsClient.send("item", target, slot, 0, 0), true);
        }
        button("物品页 " + (inventoryPage + 1) + "/" + pages, width / 2 - 60, height - 88, 120,
                () -> { inventoryPage = (inventoryPage + 1) % pages; rebuild(); }, pages > 1);
        button("返回战斗 [Esc]", width / 2 - 70, height - 66, 140, () -> { items = false; rebuild(); }, true);
    }
    private int rows() { return Math.max(2, Math.min(8, (height - 176) / 24)); }
    private void roster(boolean enemy, int x, int y, int column) {
        var fighters = side(enemy);
        int count = rows(), pages = Math.max(1, (fighters.size() + count - 1) / count);
        int page = Math.clamp(enemy ? enemyPage : allyPage, 0, pages - 1);
        if (enemy) enemyPage = page; else allyPage = page;
        for (int i = page * count; i < Math.min(fighters.size(), (page + 1) * count); i++) {
            var f = fighters.get(i); int row = i - page * count;
            String name = (f.id() == TrailsClient.view.actor() ? "▶ " : "") + f.name();
            button(font.plainSubstrByWidth(name, column - 8), x, y + row * 24, column,
                    () -> { if (enemy) target = f.id(); else BattleClient.focus = minecraft.level.getEntity(f.id()) == null ? BattleClient.focus : minecraft.level.getEntity(f.id()).position().add(0, 1, 0); }, true);
        }
        if (pages > 1) {
            button("‹", x, y + count * 24, 23, () -> { if (enemy) enemyPage = (page + pages - 1) % pages; else allyPage = (page + pages - 1) % pages; rebuild(); }, true);
            button((page + 1) + "/" + pages + " ›", x + 26, y + count * 24, column - 26,
                    () -> { if (enemy) enemyPage = (page + 1) % pages; else allyPage = (page + 1) % pages; rebuild(); }, true);
        }
    }
    private boolean playerTurn() {
        return TrailsClient.active() && TrailsClient.view.alliedTurn() && minecraft.player != null && TrailsClient.view.actor() == minecraft.player.getId();
    }
    private void selectSlot(int slot) {
        if (!playerTurn()) return;
        minecraft.player.getInventory().selected = Math.floorMod(slot, 9);
        command("select"); rebuild();
    }
    private void movement(boolean forceStop) {
        var view = TrailsClient.view;
        if (movementTurn != view.turn()) {
            movementTurn = view.turn(); movementKeys.clear(); sentMovement = false;
        }
        double x = 0, z = 0;
        if (!forceStop && !items && view.alliedTurn() && minecraft.isWindowActive()) {
            x = (movementKeys.contains(GLFW.GLFW_KEY_D) ? 1 : 0) - (movementKeys.contains(GLFW.GLFW_KEY_A) ? 1 : 0);
            z = (movementKeys.contains(GLFW.GLFW_KEY_W) ? 1 : 0) - (movementKeys.contains(GLFW.GLFW_KEY_S) ? 1 : 0);
        }
        boolean moving = x != 0 || z != 0;
        if (!moving && !sentMovement) return;
        if (moving && ++movementTick % 2 != 0) return;
        double a = Math.toRadians(BattleClient.yaw), length = Math.max(1, Math.sqrt(x * x + z * z));
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new TrailsNetwork.MoveInput(view.battle(), view.turn(), ++TrailsClient.movementSequence,
                (-x * Math.cos(a) - z * Math.sin(a)) / length, (-x * Math.sin(a) + z * Math.cos(a)) / length));
        sentMovement = moving;
    }
    private static boolean movementKey(int key) {
        return key == GLFW.GLFW_KEY_W || key == GLFW.GLFW_KEY_A || key == GLFW.GLFW_KEY_S || key == GLFW.GLFW_KEY_D;
    }
    @Override public void tick() {
        if (!TrailsClient.active()) return;
        if (!minecraft.isWindowActive()) movementKeys.clear();
        if (revision != TrailsClient.view.revision()) { revision = TrailsClient.view.revision(); rebuild(); }
        movement(false);
    }
    @Override public void removed() {
        if (TrailsClient.active()) movement(true);
        movementKeys.clear(); orbit = false;
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        if (!TrailsClient.active()) return;
        var view = TrailsClient.view;
        g.fill(0, 0, width, 44, 0xd20d101b);
        g.drawString(font, "AT / " + (view.alliedTurn() ? "我方指令" : "敌方行动 · 可抢 S"), 10, 7, 0xffffffff);
        String queue = view.queue().stream().limit(Math.max(2, width / 90)).map(q -> font.plainSubstrByWidth(q.name(), 55) + " " + String.format(Locale.ROOT, "%.0f", q.inAv())).reduce((a, c) -> a + " → " + c).orElse("");
        g.drawString(font, font.plainSubstrByWidth(queue, width - 20), 10, 23, 0xffffd486);
        g.drawString(font, "友军 " + side(false).size(), 10, 46, 0xff72f4ca);
        g.drawString(font, "敌军 " + side(true).size(), width - Math.min(154, width / 3 - 8), 46, 0xffff7794);
        g.drawCenteredString(font, font.plainSubstrByWidth("主武器大招：" + view.burstName(), Math.max(40, width - 180)), width / 2, 46, 0xffe3d5ff);
        g.fill(0, height - 43, width, height, 0xda0d101b);
        g.drawString(font, font.plainSubstrByWidth(view.message(), width - 16), 8, height - 37, 0xffeeeeee);
        g.drawString(font, "WASD 移动 · 1–9/滚轮切武器 · E 背包 · " + TrailsClient.SWITCH.getTranslatedKeyMessage().getString() + " 退出 · "
                + TrailsClient.BURST.getTranslatedKeyMessage().getString() + " 抢 S · 右键转视角", 8, height - 24, 0xffaab6ca);
        var selected = TrailsClient.fighters.stream().filter(f -> f.id() == target).findFirst();
        selected.ifPresent(f -> g.drawString(font, String.format(Locale.ROOT, "目标 %s  HP %.1f/%.0f  SPD %.0f  AT %.1f", f.name(), f.hp(), f.maxHp(), f.spd(), f.at()), 8, height - 12, 0xffffb8c7));
        super.render(g, mouseX, mouseY, partial);
        int column = Math.min(154, width / 3 - 8);
        if (!items) { stats(g, false, 8, column); stats(g, true, width - column - 8, column); }
        else g.drawCenteredString(font, "选择食物或药水 · 使用后消耗本次行动", width / 2, 64, 0xffffffff);
        if (!items) {
            int start = (width - 180) / 2;
            for (int i = 0; i < 9; i++) {
                int x = start + i * 20, y = height - 109;
                g.fill(x - 1, y - 1, x + 18, y + 18, minecraft.player.getInventory().selected == i ? 0xccd92e55 : 0xaa10121b);
                g.renderItem(minecraft.player.getInventory().getItem(i), x, y);
                g.drawString(font, Integer.toString(i + 1), x + 10, y + 9, 0xffeeeeee);
            }
            g.drawCenteredString(font, "移动半径 " + String.format(Locale.ROOT, "%.1f", view.movement()) + " 格 · 圈内自由移动", width / 2, height - 123, 0xffffffff);
        }
        TrailsClient.cutIn(g, width, height);
    }
    private void stats(GuiGraphics g, boolean enemy, int x, int column) {
        var list = side(enemy); int page = enemy ? enemyPage : allyPage;
        for (int i = page * rows(); i < Math.min(list.size(), (page + 1) * rows()); i++) {
            var f = list.get(i); int y = 57 + (i - page * rows()) * 24;
            g.fill(x + 2, y + 17, x + column - 2, y + 20, 0xff282536);
            g.fill(x + 2, y + 17, x + 2 + (int) ((column - 4) * Math.clamp(f.hp() / Math.max(1, f.maxHp()), 0, 1)), y + 20,
                    f.id() == target ? 0xffffc766 : enemy ? 0xffec3e65 : 0xff48d6a8);
        }
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (movementKey(key)) { movementKeys.add(key); return true; }
        if (TrailsClient.SWITCH.matches(key, scan)) { command("toggle"); return true; }
        if (TrailsClient.BURST.matches(key, scan)) { command("burst"); return true; }
        if (TrailsClient.PRIMARY.matches(key, scan)) { if (playerTurn()) command("primary"); return true; }
        if (minecraft.options.keyInventory.matches(key, scan) && playerTurn()) {
            minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player)); return true;
        }
        if (key == GLFW.GLFW_KEY_I && TrailsClient.view.alliedTurn() && TrailsClient.view.actor() == minecraft.player.getId()) {
            items = !items; rebuild(); return true;
        }
        // Number keys select the actual vanilla hotbar slot; action commands use that server-validated slot.
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            selectSlot(key - GLFW.GLFW_KEY_1); return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_SPACE) { command("attack"); return true; }
        if (key == GLFW.GLFW_KEY_F) { command("skill"); return true; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { items = false; rebuild(); return true; }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override public boolean keyReleased(int key, int scan, int modifiers) {
        if (movementKey(key)) { movementKeys.remove(key); movement(false); return true; }
        return super.keyReleased(key, scan, modifiers);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) { orbit = true; return true; }
        int start = (width - 180) / 2;
        if (button == 0 && !items && y >= height - 110 && y <= height - 91 && x >= start && x < start + 180) {
            selectSlot((int) ((x - start) / 20)); return true;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 1) { orbit = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (orbit) { BattleClient.yaw += (float) dx * .4f; BattleClient.pitch = Math.clamp(BattleClient.pitch + (float) dy * .3f, 10, 80); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (orbit) BattleClient.distance = Math.clamp(BattleClient.distance - vertical, 3, 30);
        else if (!items && vertical != 0) selectSlot(minecraft.player.getInventory().selected + (vertical > 0 ? -1 : 1));
        return true;
    }
}
