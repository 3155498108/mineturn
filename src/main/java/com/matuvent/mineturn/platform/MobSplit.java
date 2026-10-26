package com.matuvent.mineturn.platform;

import java.util.List;
import net.minecraft.world.entity.Mob;

/** Children are inspected after native spawning has finished. */
public interface MobSplit {
    boolean isCanceled();
    List<? extends Mob> getChildren();
}
