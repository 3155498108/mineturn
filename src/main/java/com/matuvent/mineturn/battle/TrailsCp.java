package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.platform.RuntimePlatform;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Each combatant owns its resource; the existing entity snapshot persists it. */
public final class TrailsCp {
    public static final String KEY = "mineturn.trails.cp";
    public static final int MAX = 200;
    public static final int INITIAL_COMPANION = 100;

    public static int get(LivingEntity entity) {
        var data = RuntimePlatform.data(entity);
        return data.contains(KEY) ? Math.max(0, Math.min(MAX, data.getInt(KEY)))
                : entity instanceof Player ? 0 : INITIAL_COMPANION;
    }

    public static void set(LivingEntity entity, int value) {
        RuntimePlatform.data(entity).putInt(KEY, Math.max(0, Math.min(MAX, value)));
    }

    public static void initialize(LivingEntity entity) { set(entity, get(entity)); }

    public static void add(LivingEntity entity, int amount) {
        set(entity, (int)Math.max(0L, Math.min(MAX, (long)get(entity) + amount)));
    }

    public static void require(LivingEntity entity, int cost) {
        if (cost < 0 || cost > MAX) throw new IllegalArgumentException("无效 CP 消耗。");
        if (get(entity) < cost) throw new IllegalArgumentException("CP 不足：需要 " + cost + "，当前 " + get(entity) + "。");
    }

    public static void spend(LivingEntity entity, int cost) {
        require(entity, cost);
        if (cost > 0) set(entity, get(entity) - cost);
    }

    private TrailsCp() {}
}
