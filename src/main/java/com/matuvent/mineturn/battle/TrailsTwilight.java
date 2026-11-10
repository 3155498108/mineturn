package com.matuvent.mineturn.battle;

import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.TridentItem;

/** Optional Twilight bridge. No Twilight classes are loaded when the mod is absent. */
public final class TrailsTwilight {
    private static String name(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    public static boolean hostile(LivingEntity entity) {
        return name(entity).startsWith("twilightforest:") && entity.getType().getCategory() == MobCategory.MONSTER;
    }

    static boolean canJoin(LivingEntity entity) {
        return !name(entity).equals("twilightforest:lich") || !Boolean.TRUE.equals(invoke(entity, "isShadowClone"));
    }

    /** A native teleport must finish before the Lich starts its next AT action. */
    static void onTurn(LivingEntity entity) {
        if (!name(entity).equals("twilightforest:lich")) return;
        Object remaining;
        try { remaining = entity.getClass().getMethod("getTeleportInvisibility").invoke(entity); }
        catch (NoSuchMethodException legacyInstantTeleport) { return; }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("暮色巫妖瞬移状态读取失败", exception); }
        if (remaining instanceof Integer ticks && ticks > 0) {
            try { entity.getClass().getMethod("setTeleportInvisibility", int.class).invoke(entity, 0); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException("暮色巫妖瞬移状态接口不匹配", exception); }
        }
    }

    /** Hydra's root rejects hits. Send one hit through a real head, retaining its armor rules. */
    static boolean hurt(LivingEntity target, LivingEntity source, DamageSource damage, float amount, boolean burst) {
        if (name(target).equals("twilightforest:hydra")) {
            Object parts = invoke(target, "getParts");
            if (parts instanceof Object[] children) for (Object part : children) {
                if (part == null || !part.getClass().getSimpleName().equals("HydraHead")) continue;
                for (Method method : target.getClass().getMethods()) {
                    if (method.getName().equals("attackEntityFromPart") && method.getParameterCount() == 3) {
                        try {
                            method.invoke(target, part, damage, amount);
                            return true;
                        } catch (ReflectiveOperationException exception) {
                            throw new IllegalStateException("暮色九头蛇伤害接口调用失败", exception);
                        }
                    }
                }
            }
            throw new IllegalStateException("暮色九头蛇尚未生成可攻击的头部，请稍后再入战。");
        }
        if (name(target).equals("twilightforest:lich")) {
            var item = source.getMainHandItem().getItem();
            var profile = TrailsBestiary.get(source);
            boolean ranged = item instanceof BowItem || item instanceof CrossbowItem || item instanceof TridentItem
                    || profile != null && !profile.basic().projectile().isBlank();
            if (ranged || burst) {
                target.hurt(source.damageSources().indirectMagic(source, source), amount);
                return true;
            }
        }
        return false;
    }

    private static Object invoke(Object target, String name) {
        try { return target.getClass().getMethod(name).invoke(target); }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException("暮色接口不匹配 " + name, exception); }
    }

    private TrailsTwilight() {}
}
