package com.matuvent.mineturn.battle;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;

/** Derives AT actions for mobs without a dedicated species profile. */
public final class TrailsAutoProfiles {
    private record Traits(boolean hostile, boolean ranged, boolean boss, double health, double reach, String animation) {}
    private record Cached(Traits traits, TrailsBestiary.Profile profile) {}
    private static final Map<LivingEntity, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    public static boolean excluded(LivingEntity entity) {
        return entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("mineturn", "at_auto_excluded")));
    }

    public static boolean hostile(LivingEntity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER
                || bossTag(entity, "mineturn") || bossTag(entity, "c") || bossTag(entity, "forge");
    }

    public static TrailsBestiary.Profile get(LivingEntity entity) {
        if (!(entity instanceof Mob) || excluded(entity)) return null;
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (id.getNamespace().equals("mineturn")) return null;
        var weapon = TrailsWeaponSkills.kind(entity.getMainHandItem());
        boolean hostile = hostile(entity);
        boolean ranged = entity instanceof RangedAttackMob || entity.getType().is(net.minecraft.tags.EntityTypeTags.SKELETONS)
                || entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("mineturn", "at_ranged")))
                || weapon == TrailsWeaponSkills.Kind.BOW || weapon == TrailsWeaponSkills.Kind.CROSSBOW;
        double health = entity.getMaxHealth();
        boolean boss = hostile && (health >= 100 || bossTag(entity, "mineturn") || bossTag(entity, "c") || bossTag(entity, "forge"))
                && !entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("mineturn", "at_no_ultimate")));
        double reach = Math.clamp(entity.getBbWidth() + 1.5, 2.4, 8);
        String animation = "melee";
        if (ranged) animation = weapon == TrailsWeaponSkills.Kind.CROSSBOW ? "crossbow" : "bow";
        else if (weapon == TrailsWeaponSkills.Kind.AXE) animation = "axe";
        else if (weapon == TrailsWeaponSkills.Kind.SWORD) animation = "sword";
        var traits = new Traits(hostile, ranged, boss, health, reach, animation);
        var cached = CACHE.get(entity);
        if (cached != null && cached.traits().equals(traits)) return cached.profile();
        var profile = create(id.toString(), traits);
        CACHE.put(entity, new Cached(traits, profile));
        return profile;
    }

    private static boolean bossTag(LivingEntity entity, String namespace) {
        String path = namespace.equals("mineturn") ? "at_bosses" : "bosses";
        return entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }

    private static TrailsBestiary.Profile create(String id, Traits traits) {
        double range = traits.ranged() ? 24 : traits.reach();
        String projectile = traits.ranged() ? "arrow" : "";
        var basic = move(traits.ranged() ? "远程射击" : "普通攻击", "ATTRIBUTE", traits.animation(), "target", range, 0, 1, projectile, 0);
        TrailsBestiary.Move skill;
        if (!traits.hostile()) {
            skill = move("自我恢复", "HEAL", "support", "self", 0, .8, Math.max(2, traits.health() * .2), "", 35);
        } else if (traits.ranged()) {
            skill = move("集中齐射", "ATTRIBUTE", traits.animation(), "circle", 24, 3, 1.5, projectile, 40);
        } else {
            skill = move("蓄力猛击", "ATTRIBUTE", traits.animation(), "target", range, 0, 1.7, "", 30);
        }
        TrailsBestiary.Move ultimate = null;
        if (traits.boss()) {
            ultimate = move("极限爆发", "ATTRIBUTE", traits.animation(), traits.ranged() ? "circle" : "self",
                    traits.ranged() ? 24 : 0, traits.ranged() ? 6 : Math.min(12, traits.reach() + 3), 2.6, projectile, 0);
        }
        return new TrailsBestiary.Profile(id, basic, skill, ultimate);
    }

    private static TrailsBestiary.Move move(String name, String kind, String animation, String shape, double range,
                                            double radius, double power, String projectile, int cost) {
        return new TrailsBestiary.Move(name, kind, animation, shape, range, radius, 0, power, "", 0, 120, projectile, false, cost);
    }

    static double attackDamage(LivingEntity entity) {
        double damage;
        if (entity.getAttribute(Attributes.ATTACK_DAMAGE) != null) damage = entity.getAttributeValue(Attributes.ATTACK_DAMAGE);
        else {
            var strength = entity.getEffect(MobEffects.DAMAGE_BOOST);
            var weakness = entity.getEffect(MobEffects.WEAKNESS);
            damage = 3 + (strength == null ? 0 : 3 * (strength.getAmplifier() + 1))
                    - (weakness == null ? 0 : 4 * (weakness.getAmplifier() + 1));
        }
        return Double.isFinite(damage) ? Math.max(0, damage) : 3;
    }

    /** Remove the vanilla potion factors already handled by the configurable AT bonuses. */
    static double movementMultiplier(LivingEntity entity) {
        var attribute = entity.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attribute == null || attribute.getBaseValue() <= 0) return 1;
        double vanilla = 1;
        var fast = entity.getEffect(MobEffects.MOVEMENT_SPEED);
        var slow = entity.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (fast != null) vanilla *= 1 + .2 * (fast.getAmplifier() + 1);
        if (slow != null) vanilla *= Math.max(0, 1 - .15 * (slow.getAmplifier() + 1));
        if (vanilla <= 0) return 1;
        double ratio = attribute.getValue() / attribute.getBaseValue() / vanilla;
        return Double.isFinite(ratio) ? Math.max(0, ratio) : 1;
    }

    private TrailsAutoProfiles() {}
}
