package com.matuvent.mineturn.battle;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

/** Shared descriptions and geometry; server execution still validates resources and membership. */
public final class TrailsWeaponSkills {
    public enum Kind { NONE, SWORD, AXE, BOW, CROSSBOW, MACE, TRIDENT, MELEE }
    private static final java.util.Map<String, net.minecraft.tags.TagKey<Item>> TAGS = new java.util.concurrent.ConcurrentHashMap<>();

    public static Kind kind(ItemStack stack) {
        if (stack.isEmpty() || tagged(stack, "mineturn:at_weapons/excluded")) return Kind.NONE;
        for (Kind kind : Kind.values()) {
            if (kind != Kind.NONE && tagged(stack, "mineturn:at_weapons/" + kind.name().toLowerCase(java.util.Locale.ROOT))) return kind;
        }
        Item item = stack.getItem();
        if (item instanceof CrossbowItem) return Kind.CROSSBOW;
        if (item instanceof BowItem) return Kind.BOW;
        if (item instanceof TridentItem) return Kind.TRIDENT;
        if (item instanceof MaceItem) return Kind.MACE;
        if (item instanceof AxeItem || stack.is(ItemTags.AXES)) return Kind.AXE;
        if (item instanceof SwordItem || stack.is(ItemTags.SWORDS)) return Kind.SWORD;
        if (tagged(stack, "c:tools/crossbow", "c:crossbows", "forge:tools/crossbows")) return Kind.CROSSBOW;
        if (tagged(stack, "c:tools/bow", "c:bows", "forge:tools/bows")) return Kind.BOW;
        if (tagged(stack, "c:tools/trident", "c:tridents", "forge:tools/tridents")) return Kind.TRIDENT;
        if (tagged(stack, "c:tools/axe", "forge:tools/axes")) return Kind.AXE;
        if (tagged(stack, "c:tools/sword", "forge:tools/swords")) return Kind.SWORD;
        if (attackDamage(stack) > 1) return Kind.MELEE;
        return Kind.NONE;
    }

    private static boolean tagged(ItemStack stack, String... tags) {
        for (String id : tags) {
            var tag = TAGS.computeIfAbsent(id, key -> net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.parse(key)));
            if (stack.is(tag)) return true;
        }
        return false;
    }

    /** Read held-slot callbacks so stack-specific and event-supplied modifiers participate. */
    public static double attackDamage(ItemStack stack) {
        double[] additive = {1};
        double[] baseMultiplier = {0};
        double[] totalMultiplier = {1};
        stack.forEachModifier(net.minecraft.world.entity.EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (!attribute.equals(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)) return;
            switch (modifier.operation()) {
                case ADD_VALUE -> additive[0] += modifier.amount();
                case ADD_MULTIPLIED_BASE -> baseMultiplier[0] += modifier.amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier[0] *= 1 + modifier.amount();
            }
        });
        double damage = additive[0] * (1 + baseMultiplier[0]) * totalMultiplier[0];
        return Double.isFinite(damage) ? Math.max(0, damage) : 1;
    }

    public static String name(ItemStack s) {
        if (kind(s) == Kind.SWORD) return "回旋横扫";
        if (kind(s) == Kind.AXE) return "破势重斩";
        if (kind(s) == Kind.BOW) return "满弦狙击";
        if (kind(s) == Kind.CROSSBOW) return "多重齐射";
        if (kind(s) == Kind.MACE) return "风弹震地";
        if (kind(s) == Kind.TRIDENT) return "贯穿投掷";
        if (kind(s) == Kind.MELEE) return "蓄力打击";
        return "请装备武器";
    }
    public static boolean supported(ItemStack s) { return kind(s) != Kind.NONE; }
    public static int cpCost(ItemStack s) {
        if (kind(s) == Kind.SWORD) return 25;
        if (kind(s) == Kind.AXE) return 30;
        if (kind(s) == Kind.BOW) return 35;
        if (kind(s) == Kind.CROSSBOW) return 45;
        if (kind(s) == Kind.MACE) return 60;
        if (kind(s) == Kind.TRIDENT) return 40;
        if (kind(s) == Kind.MELEE) return 25;
        return 0;
    }
    public static TrailsArea shape(ItemStack s) {
        if (kind(s) == Kind.SWORD) return new TrailsArea(TrailsArea.Kind.SECTOR, 0, 4, 140);
        if (kind(s) == Kind.CROSSBOW) return new TrailsArea(TrailsArea.Kind.RECTANGLE, 0, 24, 7);
        if (kind(s) == Kind.MACE) return TrailsArea.weapon(s, true, false);
        if (kind(s) == Kind.TRIDENT) return new TrailsArea(TrailsArea.Kind.RECTANGLE, 0, 20, 2);
        return new TrailsArea(TrailsArea.Kind.SINGLE, kind(s) == Kind.BOW ? 28 : 3, 0, 0);
    }
    public static String description(ItemStack s) {
        if (kind(s) == Kind.SWORD) return "原版横扫动作；4 格、140° 扇形，1.4 倍伤害；AT ×1.5。";
        if (kind(s) == Kind.AXE) return "单体 1.8 倍伤害，目标 AT 增加 30；AT ×1.7。";
        if (kind(s) == Kind.BOW) return "蓄力拉弓，单体 14 点基础伤害；消耗 1 支箭；AT ×1.6。";
        if (kind(s) == Kind.CROSSBOW) return "三箭齐射，24×7 格长方形，每个敌人最多受伤一次；消耗 1 支箭；AT ×1.8。";
        if (kind(s) == Kind.MACE) return "消耗 1 个风弹，落点半径 5 格、2.3 倍伤害；AT ×2.2。";
        if (kind(s) == Kind.TRIDENT) return "三叉戟投掷，20×2 格贯穿，1.5 倍伤害；武器保留，AT ×1.6。";
        if (kind(s) == Kind.MELEE) return "读取武器的攻击属性，单体 1.4 倍伤害；AT ×1.5。";
        return "装备可识别的武器后可使用战技。";
    }
    private TrailsWeaponSkills() {}
}
