package com.matuvent.mineturn.battle;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;

/** Shared bounded presentation curves. These values never move or damage a world entity. */
public final class TrailsChoreography {
    public record Pose(float arm, float armSide, float twist, float lean, float lift, float advance, boolean arms) {
        public static final Pose IDLE = new Pose(0, 0, 0, 0, 0, 0, false);
    }
    public static String style(LivingEntity e, ItemStack item) {
        if (item.is(ItemTags.SWORDS)) return "sword";
        if (item.is(ItemTags.AXES)) return "axe";
        if (item.getItem() instanceof BowItem) return "bow";
        if (item.getItem() instanceof CrossbowItem) return "crossbow";
        if (item.getItem() instanceof MaceItem) return "mace";
        if (item.getItem() instanceof TridentItem) return "trident";
        String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
        return switch (id) {
            case "breeze", "iron_golem", "ravager", "creeper", "slime", "magma_cube", "snow_golem" -> id;
            case "skeleton", "stray", "bogged" -> "bow";
            case "pillager" -> "crossbow";
            case "wolf", "pig", "spider", "cave_spider", "cat", "ocelot" -> "charge";
            default -> "melee";
        };
    }
    public static int windup(String style, boolean skill) {
        return switch (style) { case "bow", "crossbow", "trident" -> skill ? 24 : 18; case "axe", "mace" -> skill ? 20 : 14; default -> skill ? 16 : 10; };
    }
    public static int flight(double distance) { return (int)Math.clamp(Math.ceil(distance * 1.2), 6, 16); }
    public static Pose pose(String style, String phase, double progress) {
        double t = Math.clamp(progress, 0, 1), e = t * t * (3 - 2 * t), wave = Math.sin(Math.PI * t);
        boolean burst = style.startsWith("burst_"); if (burst) style = style.substring(6);
        if (phase.equals("recover")) {
            var tail=pose((burst?"burst_":"")+style,"strike",1);float fade=(float)(1-e);
            return new Pose(tail.arm()*fade,tail.armSide()*fade,tail.twist()*fade,tail.lean()*fade,tail.lift()*fade,tail.advance()*fade,tail.arms());
        }
        boolean draw = phase.equals("draw"); float reach = (float)(draw ? e : 1-e);
        float lift = style.equals("mace") ? (float)((draw ? e : 1-e) * (burst ? 1.1 : .55)) : 0;
        return switch (style) {
            case "sword" -> new Pose((float)(draw ? -1.0*e : -1.8+2.5*e), (float)(draw ? -.65*e : -.75*wave),
                    (float)(draw ? -.55*e : .7*wave), 0, lift, (float)(draw ? -.08*e : .32*wave), true);
            case "axe", "mace" -> new Pose((float)(draw ? -2.6*e : -2.6+3*e), .08f,
                    -.12f*reach, (float)(draw ? -.1*e : .22*wave), lift, (float)(draw ? 0 : .2*wave), true);
            case "trident" -> new Pose((float)(draw ? -2.7*e : -2.7+3*e), (float)(-.2*wave),
                    -.35f*reach, (float)(draw ? -.1*e : .14*wave), 0, (float)(draw ? 0 : .2*wave), true);
            case "bow", "crossbow" -> new Pose(0, 0, -.08f*reach, 0, 0, (float)(draw ? 0 : -.12*wave), false);
            case "support" -> new Pose((float)(-1.8*wave), (float)(-.5*wave), 0, 0, (float)(.08*wave), 0, true);
            default -> new Pose((float)(draw ? -1.0*e : -1.5+2*e), 0, (float)(.1*wave),
                    (float)(.12*wave), 0, (float)(draw ? -.1*e : .4*wave), true);
        };
    }
    private TrailsChoreography() {}
}
