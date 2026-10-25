package com.matuvent.mineturn.battle;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

/** One geometry contract for damage, hit previews and cast/effect range labels. */
public record TrailsArea(Kind kind, double cast, double radius, double width) {
    public enum Kind { SINGLE, SECTOR, RECTANGLE, DISK }
    public static TrailsArea weapon(ItemStack stack, boolean wind, boolean burst) {
        if (burst) {
            if (stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem) return new TrailsArea(Kind.RECTANGLE, 0, 24, 6);
            if (stack.is(ItemTags.AXES)) return new TrailsArea(Kind.SINGLE, 24, 0, 0);
            if (stack.getItem() instanceof BowItem) return new TrailsArea(Kind.DISK, 24, 5, 0);
            if (stack.getItem() instanceof MaceItem) return new TrailsArea(Kind.DISK, 24, 6, 0);
            return new TrailsArea(Kind.DISK, 0, 10, 0);
        }
        if (stack.is(ItemTags.SWORDS)) return new TrailsArea(Kind.SECTOR, 0, 3, 90);
        if (stack.getItem() instanceof CrossbowItem) return new TrailsArea(Kind.RECTANGLE, 0, 24, 3);
        if (stack.getItem() instanceof MaceItem) return new TrailsArea(Kind.DISK, 3, wind ? 5 : 2, 0);
        return new TrailsArea(Kind.SINGLE, stack.getItem() instanceof BowItem ? 24 : stack.getItem() instanceof TridentItem ? 3.5 : 3, 0, 0);
    }
    public static TrailsArea skill(String entity) {
        var profile=TrailsBestiary.get(entity);
        return profile==null?new TrailsArea(Kind.DISK,0,6,0):profile.skill().area();
    }

    public Vec3 direction(Vec3 aim) { return aim.horizontalDistanceSqr() < 1e-8 ? new Vec3(0, 0, 1) : aim.multiply(1, 0, 1).normalize(); }
    public Vec3 center(Vec3 origin, Vec3 aim) { return origin.add(cast > 0 && kind == Kind.DISK ? aim.multiply(1, 0, 1) : Vec3.ZERO); }
    public boolean validAim(Vec3 aim) {
        return Double.isFinite(aim.x) && Double.isFinite(aim.z) && Math.abs(aim.x) <= 64 && Math.abs(aim.z) <= 64
                && (kind != Kind.DISK || aim.horizontalDistance() <= cast + 1e-6 || cast == 0);
    }
    public boolean contains(Vec3 origin, Vec3 aim, Vec3 point) {
        if (Math.abs(point.y - origin.y) > 3) return false;
        if (kind == Kind.DISK) return point.subtract(center(origin, aim)).horizontalDistanceSqr() <= radius * radius + 1e-7;
        Vec3 delta = point.subtract(origin).multiply(1, 0, 1), forward = direction(aim);
        double along = delta.dot(forward);
        if (kind == Kind.RECTANGLE) return along >= -1e-7 && along <= radius + 1e-7
                && Math.abs(delta.x * forward.z - delta.z * forward.x) <= width / 2 + 1e-7;
        if (kind == Kind.SECTOR) return delta.horizontalDistanceSqr() <= radius * radius + 1e-7
                && (delta.horizontalDistanceSqr() < 1e-8 || along / delta.length() >= Math.cos(Math.toRadians(width / 2)) - 1e-7);
        return delta.horizontalDistance() <= cast + 1e-7;
    }
}
