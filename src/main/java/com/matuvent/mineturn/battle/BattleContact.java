package com.matuvent.mineturn.battle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;

/** Selected native contact callbacks, once per ten logical status ticks (50 AV). */
final class BattleContact {
    private record BerryStamp(java.util.UUID battle,double until){}
    // Server-thread only. Values never retain the weak entity key or battle instance.
    private static final java.util.Map<LivingEntity,BerryStamp> BERRIES=new java.util.WeakHashMap<>();
    static void moved(BattleSession battle,LivingEntity entity,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to){
        if(Math.abs(to.x-from.x)<0.003 && Math.abs(to.z-from.z)<0.003)return;
        berry(battle,entity);
        var mount=BattleRiding.vehicle(entity);if(mount!=null)berry(battle,mount);
    }
    private static void berry(BattleSession battle,LivingEntity entity){
        if(!entity.isAlive() || entity.getType()==net.minecraft.world.entity.EntityType.FOX || entity.getType()==net.minecraft.world.entity.EntityType.BEE)return;
        var stamp=BERRIES.get(entity);
        if(stamp!=null && stamp.battle.equals(battle.id) && battle.clock.time()<stamp.until)return;
        var box=entity.getBoundingBox().deflate(1e-7);
        var min=BlockPos.containing(box.minX,box.minY,box.minZ);var max=BlockPos.containing(box.maxX,box.maxY,box.maxZ);
        if(!battle.level.hasChunksAt(min,max))return;
        for(var pos:BlockPos.betweenClosed(min,max)){
            var state=battle.level.getBlockState(pos);
            if(state.is(Blocks.SWEET_BERRY_BUSH) && state.getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE)>0){
                BERRIES.put(entity,new BerryStamp(battle.id,battle.clock.time()+50));
                BattleManager.authorized(()->{entity.invulnerableTime=0;entity.hurt(entity.damageSources().sweetBerryBush(),1);});
                return;
            }
        }
    }
    static void tick(LivingEntity entity,int statusTick){
        if(statusTick%10!=0)return;
        var level=entity.level();var box=entity.getBoundingBox();
        var min=BlockPos.containing(box.minX+1e-7,box.minY+1e-7,box.minZ+1e-7);
        var max=BlockPos.containing(box.maxX-1e-7,box.maxY-1e-7,box.maxZ-1e-7);
        if(!level.hasChunksAt(min,max))return;
        for(var pos:BlockPos.betweenClosed(min,max)){
            if(!entity.isAlive())return;
            var state=level.getBlockState(pos);
            if(state.is(Blocks.CACTUS) || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE) || state.is(Blocks.WITHER_ROSE))
                state.entityInside(level,pos,entity);
        }
        if(!entity.isAlive() || !entity.onGround())return;
        var floor=entity.getOnPos();var state=level.getBlockState(floor);
        if(state.is(Blocks.MAGMA_BLOCK))state.getBlock().stepOn(level,floor,state,entity);
    }
    private BattleContact(){}
}
