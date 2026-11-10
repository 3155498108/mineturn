package com.matuvent.mineturn.client;

import com.matuvent.mineturn.battle.TrailsBestiary;
import com.matuvent.mineturn.battle.TrailsWeaponSkills;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Items;

/** In-game manual. Species entries read the same moves and CP costs as combat. */
public final class TrailsGuideScreen extends Screen {
    private static final String[] CATEGORIES = {"玩法", "角色", "武器", "友军", "敌军", "Boss"};
    record Article(String title, String text) {}
    private final Screen parent;
    private int category, listPage, scroll;
    private String selected = "", query = "";
    private EditBox search;
    private List<Article> articles = List.of();
    private List<FormattedCharSequence> lines = List.of();

    public TrailsGuideScreen(Screen parent) {
        super(Component.literal("MineTurn · 战斗手册"));
        this.parent = parent;
    }

    private int listWidth() { return Math.min(138, width / 3); }
    private int rows() { return Math.max(1, (height - 126) / 22); }
    private int visibleLines() { return Math.max(1, (height - 146) / 12); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderBackground(GuiGraphics graphics, int x, int y, float partial) {}

    @Override protected void init() {
        int cell = (width - 20) / CATEGORIES.length;
        for (int i = 0; i < CATEGORIES.length; i++) {
            int tab = i;
            var button = addRenderableWidget(Button.builder(Component.literal(CATEGORIES[i]), ignored -> {
                category = tab;
                selected = "";
                listPage = scroll = 0;
                query = "";
                rebuildWidgets();
            }).bounds(10 + i * cell, 32, cell - 3, 20).build());
            button.active = category != i;
        }
        search = addRenderableWidget(new EditBox(font, 10, 61, width - 20, 20, Component.literal("搜索本分类")));
        search.setHint(Component.literal("搜索本分类的名称、技能、效果…"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            listPage = scroll = 0;
            refreshArticles();
            // Rebuild on the next tick so an EditBox never replaces itself inside its input callback.
            refresh = true;
        });
        refreshArticles();
        int start = listPage * rows();
        for (int i = start; i < Math.min(articles.size(), start + rows()); i++) {
            Article article = articles.get(i);
            var button = addRenderableWidget(Button.builder(Component.literal(font.plainSubstrByWidth(article.title(), listWidth() - 14)), ignored -> {
                selected = article.title();
                scroll = 0;
                rebuildWidgets();
            }).bounds(10, 91 + (i - start) * 22, listWidth(), 20).build());
            button.active = !selected.equals(article.title());
        }
        int pages = Math.max(1, (articles.size() + rows() - 1) / rows());
        var previous = addRenderableWidget(Button.builder(Component.literal("‹"), ignored -> {
            listPage--;
            rebuildWidgets();
        }).bounds(10, height - 29, 25, 20).build());
        previous.active = listPage > 0;
        var next = addRenderableWidget(Button.builder(Component.literal("›"), ignored -> {
            listPage++;
            rebuildWidgets();
        }).bounds(40, height - 29, 25, 20).build());
        next.active = listPage + 1 < pages;
        addRenderableWidget(Button.builder(Component.literal("返回"), ignored -> onClose())
                .bounds(width - 62, height - 29, 52, 20).build());
    }

    private boolean refresh;
    @Override public void tick() {
        if (refresh) {
            refresh = false;
            int cursor = search.getCursorPosition();
            boolean focused = search.isFocused();
            rebuildWidgets();
            search.moveCursorTo(cursor, false);
            if (focused) setFocused(search);
        }
    }

    private void refreshArticles() {
        String filter = query.toLowerCase(Locale.ROOT).strip();
        articles = contents(category).stream().filter(article ->
                (article.title() + " " + article.text()).toLowerCase(Locale.ROOT).contains(filter)).toList();
        listPage = Math.max(0, Math.min(listPage, Math.max(0, (articles.size() - 1) / rows())));
        var article = articles.stream().filter(value -> value.title().equals(selected)).findFirst()
                .orElse(articles.isEmpty() ? new Article("没有匹配条目", "试试其他名称或清空搜索框。") : articles.get(0));
        selected = article.title();
        lines = font.split(Component.literal(article.text()), Math.max(40, width - listWidth() - 45));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - visibleLines())));
    }

    static List<Article> contents(int category) {
        var entries = new ArrayList<Article>();
        switch (category) {
            case 0 -> {
                entries.add(new Article("快速开始", "按住切换键 " + key(TrailsClient.SWITCH) + " 查看入战圈，松开后拉入圈内的敌人与友军，在原地切入第三人称 AT 战斗。默认半径 20 格，可在设置中修改。\n\n在自己控制的玩家或友军回合，按下并松开同一键即可退出。敌人行动和动作演出期间需等待。\n\n用 " + key(TrailsClient.GUIDE) + " 或战斗右上角的说明按钮重新打开本手册。"));
                entries.add(new Article("移动与攻击", "轮到你控制的角色后，用 WASD 按镜头方向在绿色移动圈内自由移动。按住鼠标右键拖动视角。\n\nEnter 或空格预览普通攻击，F 预览战技，再按 Enter 确认。Esc 取消预览。点击右侧敌军选择单体目标。\n\n扇形、长方形攻击沿镜头朝向，圆形技能用滚轮调整落点。先确认橙色生效区域再出手。"));
                entries.add(new Article("速度与 AT", "AT 越小越先行动，速度越高，同样行动后的等待越短。不同攻击和战技有不同的恢复时间。\n\n迅捷、缓慢、装备和物种速度会影响行动顺序。头像队列显示当前与后续行动者。\n\n防御可减伤，待机可较快让出回合。范围攻击应同时考虑敌人站位和自己的下一次行动。"));
                entries.add(new Article("背袭与抢 S", "即时战斗中从敌人背后命中可制造眩晕与呼吸灯提示。此时切入，敌军扣除最大生命的 25%，我方优先行动。\n\n用 " + key(TrailsClient.PRIMARY) + " 设定主武器。CP 达到 100 后，用 " + key(TrailsClient.BURST) + " 预览并确认 S 技，可以插入行动顺序。200 CP 时威力更高。不同主武器有不同的 S 技。\n\nBoss 金光表示下次自身回合发动 S 技。趁预警回合移动、防御或准备抢 S。"));
                entries.add(new Article("画面与音乐", "按 " + key(TrailsClient.CONFIG) + " 打开设置，可以调整入战范围、移动半径、暴击和演出。普通伤害显示白字，暴击显示更大的红字。名单显示当前/最大生命、CP 和原版状态图标。\n\n在音乐设置中打开音乐文件夹，将个人 OGG 音频放进 custom，按用途命名为 normal.ogg、advantage.ogg 和 boss.ogg，再点击重读本地音乐。设置可保存开关、曲库与音量，退战时自动淡出。"));
            }
            case 1 -> {
                entries.add(new Article("玩家", "玩家可以使用普通攻击、武器战技、道具、防御和 S 技。轮到玩家时可用 1–9、滚轮或背包切换武器。\n\n生命和护甲沿用实体及原版装备数值。食物和药水可恢复生命、饱食度或施加状态。力量影响攻击，迅捷影响速度与后续 AT。"));
                entries.add(new Article("CP 资源", "每个参战者独立持有 CP，上限 200。新登记友军默认 100。\n\n普攻恢复 15 CP，受伤恢复 8 CP，玩家使用道具恢复 5 CP。普通战技消耗自身 CP，不会再返还普攻 CP。\n\n预览和取消免费，确认有效动作才扣费。CP 不足时先用普攻积累。收纳和放出保留友军的当前 CP 与生命，0 CP 也会保留。"));
                entries.add(new Article("队伍与归属", "多人默认互为对手，可在设置的队伍页创建队伍并邀请玩家加入。每人控制自己与自己用命名牌登记的友军。\n\n多人只有已登记的友军会参战，敌对怪物可直接入战。同队玩家的生物仍由其主人控制。"));
            }
            case 2 -> {
                for (var item : List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW, Items.TRIDENT, Items.MACE)) {
                    var stack = item.getDefaultInstance();
                    if (!BuiltInRegistries.ITEM.containsKey(BuiltInRegistries.ITEM.getKey(item))) continue;
                    entries.add(new Article(stack.getHoverName().getString(), "战技「" + TrailsWeaponSkills.name(stack) + "」\n消耗 " + TrailsWeaponSkills.cpCost(stack) + " CP\n\n" + TrailsWeaponSkills.description(stack) + "\n\n剑普通攻击为扇形，弩为长方形，弓和斧选择单体，重锤为圆形。方向范围按镜头瞄准。\n\n玩家自己回合可切换武器并更新主武器，抢 S 使用已设定的主武器。"));
                }
            }
            default -> {
                for (var profile : TrailsBestiary.all()) {
                    var id = ResourceLocation.parse(profile.id());
                    if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) continue;
                    var type = BuiltInRegistries.ENTITY_TYPE.get(id);
                    boolean boss = profile.ultimate() != null;
                    boolean hostile = type.getCategory() == MobCategory.MONSTER;
                    if (category == 5 ? !boss : boss || (category == 4) != hostile) continue;
                    String body = profile.id() + "\n\n" + describe("普通攻击", profile.basic()) + "\n\n" + describe("战技", profile.skill());
                    if (boss) body += "\n\n" + describe("S 技", profile.ultimate()) + "\n\n金光蓄力后，下次自身回合释放，留意地面危险区域。";
                    else if (!hostile) body += "\n\n用命名牌登记并收纳，右键方块放出，蹲下空手右键再次收回。放出后跟随主人，保留当前生命和 CP。";
                    else body += "\n\n敌方战技会发红光，CP 不足时改用普攻。";
                    if (profile.id().equals("minecraft:chicken")) body += "\n\n振翅号令将范围内友军 AT 拉到 0，消耗 80 CP。";
                    if (profile.id().equals("twilightforest:lich")) body += "\n\n巫妖护盾仍生效，远程武器和法术攻击逐层破盾后才能伤及本体。";
                    entries.add(new Article(type.getDescription().getString(), body));
                }
                if (category == 3) entries.add(0, new Article("招募与收纳", "单人圈内的友好生物可作为友军，多人需要先用命名牌登记归属。\n\n命名后直接进入背包，右键方块放出，蹲下空手右键再次收回。放出后会跟随主人。\n\n友军可使用普攻、各自战技和防御，道具由玩家使用。收纳保留名字、UUID、装备、生命和 CP。请在战斗外收放。"));
            }
        }
        return entries;
    }

    private static String key(net.minecraft.client.KeyMapping mapping) { return mapping.getTranslatedKeyMessage().getString(); }
    private static String describe(String label, TrailsBestiary.Move move) {
        String shape = switch (move.shape()) {
            case "self" -> "自身周围半径 " + move.radius() + " 格";
            case "circle" -> "施放 " + move.range() + " 格，落点半径 " + move.radius() + " 格";
            case "cone" -> "扇形 " + move.range() + " 格 / " + move.width() + "°";
            case "line" -> "长方形 " + move.range() + " × " + move.width() + " 格";
            default -> "单体 " + move.range() + " 格";
        };
        String effect = switch (move.kind()) {
            case "HEAL" -> "直接恢复生命";
            case "REGEN" -> "恢复生命，并在后续回合再生";
            case "CLEANSE" -> "移除不利状态并治疗";
            case "READY" -> "范围内友军 AT 变为 0";
            case "ADVANCE" -> "提前友军行动";
            case "BUFF" -> "给予有利状态";
            case "DRAIN" -> "攻击并吸取生命";
            case "WIND", "LEVITATE" -> "击飞并结算落地伤害";
            case "PHASE", "DASH", "TELEPORT" -> "突袭或位移攻击";
            default -> "造成伤害";
        };
        return label + "「" + move.name() + "」" + (move.cpCost() > 0 ? " · " + move.cpCost() + " CP" : "")
                + "\n" + shape + "\n" + effect + "，基础数值 " + move.power()
                + (move.buff().isBlank() ? "" : "\n附加 " + Component.translatable("effect.minecraft." + move.buff()).getString() + " " + (move.amplifier() + 1) + " 级");
    }

    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        graphics.fill(0, 0, width, height, 0xf20e1523);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xffffd486);
        graphics.fill(listWidth() + 18, 89, width - 10, height - 35, 0xcc1b2639);
        graphics.enableScissor(listWidth() + 23, 91, width - 14, height - 37);
        graphics.drawString(font, selected, listWidth() + 27, 96, 0xffffd486);
        for (int i = scroll; i < Math.min(lines.size(), scroll + visibleLines()); i++)
            graphics.drawString(font, lines.get(i), listWidth() + 27, 115 + (i - scroll) * 12, 0xffedf1f8);
        graphics.disableScissor();
        graphics.drawString(font, (listPage + 1) + "/" + Math.max(1, (articles.size() + rows() - 1) / rows()), 72, height - 23, 0xffb9cbe3);
        graphics.drawString(font, "滚轮阅读", listWidth() + 27, height - 23, 0xffb9cbe3);
        super.render(graphics, x, y, partial);
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (x > listWidth() + 18) scroll = Math.max(0, Math.min(scroll - (int) Math.signum(vertical) * 3, Math.max(0, lines.size() - visibleLines())));
        else {
            listPage = Math.max(0, Math.min(listPage - (int) Math.signum(vertical), Math.max(0, (articles.size() - 1) / rows())));
            rebuildWidgets();
        }
        return true;
    }

    @Override public void onClose() {
        minecraft.setScreen(parent instanceof TrailsScreen ? TrailsClient.active() ? new TrailsScreen() : null : parent);
    }
}
