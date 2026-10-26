package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.platform.RuntimePlatform;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.level.block.Blocks;

/** Uses vanilla navigation; combat, leads, sitting and absent owners suspend following. */
public final class CompanionFollowGoal extends Goal {
    private final Mob mob;
    private ServerPlayer owner;
    private int cooldown;
    public CompanionFollowGoal(Mob mob) { this.mob = mob; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
    private boolean eligible() {
        if (!RuntimePlatform.data(mob).getBoolean(TrailsCompanions.FOLLOW) || BattleManager.locked(mob)
                || mob.isPassenger() || mob.isVehicle() || mob.isLeashed() || mob.isNoAi()
                || mob instanceof TamableAnimal pet && pet.isOrderedToSit()) return false;
        var id = TrailsParties.owner(mob);
        owner = id == null || mob.getServer() == null ? null : mob.getServer().getPlayerList().getPlayer(id);
        return owner != null && owner.isAlive() && !owner.isSpectator() && owner.level() == mob.level()
                && !BattleManager.locked(owner);
    }
    @Override public boolean canUse() { return eligible() && mob.distanceToSqr(owner) > 16; }
    @Override public boolean canContinueToUse() { return eligible() && mob.distanceToSqr(owner) > 6.25; }
    @Override public void start() { cooldown = 0; }
    @Override public void stop() {
        mob.getNavigation().stop();
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        owner = null;
    }
    @Override public void tick() {
        if (!eligible()) return;
        mob.getLookControl().setLookAt(owner, 10, mob.getMaxHeadXRot());
        if (--cooldown > 0) return;
        cooldown = 10;
        if (mob.distanceToSqr(owner) > 144 && catchUp()) return;
        // Brain-driven animals and villagers consume the same vanilla walking target.
        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(owner, 1.1f, 2));
        mob.getNavigation().moveTo(owner, 1.1);
    }
    private boolean catchUp() {
        var level = mob.level();
        for (int i = 0; i < 12; i++) {
            int dx = mob.getRandom().nextInt(7) - 3, dz = mob.getRandom().nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos pos = owner.blockPosition().offset(dx, mob.getRandom().nextInt(3) - 1, dz);
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            boolean water = level.getFluidState(pos).is(FluidTags.WATER);
            if (mob instanceof WaterAnimal || mob instanceof Axolotl) { if (!water) continue; }
            else if (water || !level.getFluidState(pos).isEmpty()) continue;
            else if (!(mob.getNavigation() instanceof FlyingPathNavigation)
                    && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) continue;
            var below = level.getBlockState(pos.below());
            if (below.is(Blocks.MAGMA_BLOCK) || below.is(Blocks.CAMPFIRE) || below.is(Blocks.SOUL_CAMPFIRE)
                    || below.is(Blocks.CACTUS) || level.getBlockState(pos).is(Blocks.FIRE)
                    || level.getBlockState(pos).is(Blocks.SWEET_BERRY_BUSH) || level.getBlockState(pos).is(Blocks.WITHER_ROSE)) continue;
            var offset = net.minecraft.world.phys.Vec3.atBottomCenterOf(pos).subtract(mob.position());
            var box = mob.getBoundingBox().move(offset);
            if (box.minY < level.getMinBuildHeight() || box.maxY > level.getMaxBuildHeight()
                    || !level.getWorldBorder().isWithinBounds(box) || !level.noCollision(mob, box)) continue;
            mob.getNavigation().stop(); mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            mob.moveTo(pos.getX() + .5, pos.getY(), pos.getZ() + .5, mob.getYRot(), mob.getXRot());
            mob.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); mob.fallDistance = 0;
            return true;
        }
        return false;
    }
}
