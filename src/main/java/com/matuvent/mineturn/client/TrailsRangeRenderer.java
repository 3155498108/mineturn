package com.matuvent.mineturn.client;

import com.matuvent.mineturn.battle.TrailsArea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Filled ground geometry, rather than isolated rays/dots. */
final class TrailsRangeRenderer {
    static void draw(PoseStack pose, VertexConsumer out, Vec3 origin, TrailsArea shape, Vec3 aim) {
        origin = origin.add(0, .08, 0);
        if (shape.cast() > 0) ring(pose, out, origin, shape.cast(), 0xb04bcaff);
        switch (shape.kind()) {
            case SINGLE -> {}
            case DISK -> disk(pose, out, shape.center(origin, aim), shape.radius(), 0x5544e3ac, 0xd044e3ac);
            case RECTANGLE -> {
                Vec3 forward = shape.direction(aim), side = new Vec3(forward.z, 0, -forward.x).scale(shape.width() / 2);
                Vec3 a = origin.add(side), b = origin.subtract(side), c = b.add(forward.scale(shape.radius())), d = a.add(forward.scale(shape.radius()));
                quad(pose, out, a, b, c, d, 0x554bcaff);
                stroke(pose, out, a, b, 0xee4bcaff); stroke(pose, out, b, c, 0xee4bcaff); stroke(pose, out, c, d, 0xee4bcaff); stroke(pose, out, d, a, 0xee4bcaff);
            }
            case SECTOR -> {
                Vec3 forward = shape.direction(aim); double heading = Math.atan2(forward.z, forward.x), half = Math.toRadians(shape.width() / 2);
                Vec3 previous = origin.add(Math.cos(heading - half) * shape.radius(), 0, Math.sin(heading - half) * shape.radius());
                stroke(pose, out, origin, previous, 0xeeffac4b);
                for (int i = 1; i <= 48; i++) {
                    double a = heading - half + 2 * half * i / 48;
                    Vec3 next = origin.add(Math.cos(a) * shape.radius(), 0, Math.sin(a) * shape.radius());
                    quad(pose, out, origin, previous, next, origin, 0x66ff9b45); stroke(pose, out, previous, next, 0xeeffac4b); previous = next;
                }
                stroke(pose, out, previous, origin, 0xeeffac4b);
            }
        }
    }
    private static void disk(PoseStack p, VertexConsumer out, Vec3 center, double r, int fill, int edge) {
        Vec3 previous = center.add(r, 0, 0);
        for (int i = 1; i <= 96; i++) {
            double a = i * Math.PI / 48; Vec3 next = center.add(r * Math.cos(a), 0, r * Math.sin(a));
            quad(p, out, center, previous, next, center, fill); stroke(p, out, previous, next, edge); previous = next;
        }
    }
    private static void ring(PoseStack p, VertexConsumer out, Vec3 center, double r, int color) {
        Vec3 previous = center.add(r, 0, 0);
        for (int i = 1; i <= 96; i++) {
            double a = i * Math.PI / 48; Vec3 next = center.add(r * Math.cos(a), 0, r * Math.sin(a));
            stroke(p, out, previous, next, color); previous = next;
        }
    }
    private static void stroke(PoseStack p, VertexConsumer out, Vec3 a, Vec3 b, int color) {
        Vec3 delta = b.subtract(a), side = new Vec3(-delta.z, 0, delta.x).normalize().scale(.025);
        quad(p, out, a.add(side), a.subtract(side), b.subtract(side), b.add(side), color);
    }
    private static void quad(PoseStack p, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        for (var point : new Vec3[]{a, b, c, d}) out.addVertex(p.last().pose(), (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }
}
