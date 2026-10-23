package com.matuvent.mineturn.battle;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;

/** Shared descriptions and geometry; server execution still validates resources and membership. */
public final class TrailsWeaponSkills {
    public static String name(ItemStack s) {
        if (s.is(ItemTags.SWORDS)) return "回旋横扫";
        if (s.is(ItemTags.AXES)) return "破势重斩";
        if (s.getItem() instanceof BowItem) return "满弦狙击";
        if (s.getItem() instanceof CrossbowItem) return "多重齐射";
        if (s.getItem() instanceof MaceItem) return "风弹震地";
        if (s.getItem() instanceof TridentItem) return "贯穿投掷";
        return "请装备武器";
    }
    public static boolean supported(ItemStack s) { return TrailsCombat.weapon(s); }
    public static TrailsArea shape(ItemStack s) {
        if (s.is(ItemTags.SWORDS)) return new TrailsArea(TrailsArea.Kind.SECTOR, 0, 4, 140);
        if (s.getItem() instanceof CrossbowItem) return new TrailsArea(TrailsArea.Kind.RECTANGLE, 0, 24, 7);
        if (s.getItem() instanceof MaceItem) return TrailsArea.weapon(s, true, false);
        if (s.getItem() instanceof TridentItem) return new TrailsArea(TrailsArea.Kind.RECTANGLE, 0, 20, 2);
        return new TrailsArea(TrailsArea.Kind.SINGLE, s.getItem() instanceof BowItem ? 28 : 3, 0, 0);
    }
    public static String description(ItemStack s) {
        if (s.is(ItemTags.SWORDS)) return "原版横扫动作；4 格、140° 扇形，1.4 倍伤害；AT ×1.5。";
        if (s.is(ItemTags.AXES)) return "单体 1.8 倍伤害，目标 AT 增加 30；AT ×1.7。";
        if (s.getItem() instanceof BowItem) return "蓄力拉弓，单体 14 点基础伤害；消耗 1 支箭；AT ×1.6。";
        if (s.getItem() instanceof CrossbowItem) return "三箭齐射，24×7 格长方形，每个敌人最多受伤一次；消耗 1 支箭；AT ×1.8。";
        if (s.getItem() instanceof MaceItem) return "消耗 1 个风弹，落点半径 5 格、2.3 倍伤害；AT ×2.2。";
        if (s.getItem() instanceof TridentItem) return "三叉戟投掷，20×2 格贯穿，1.5 倍伤害；武器保留，AT ×1.6。";
        return "装备剑、斧、弓、弩、重锤或三叉戟后可使用战技。";
    }
    private TrailsWeaponSkills() {}
}
