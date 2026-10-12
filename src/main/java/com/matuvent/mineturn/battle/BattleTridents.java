package com.matuvent.mineturn.battle;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Commit the real recoverable item before the timing minigame. No ephemeral in-memory ownership. */
final class BattleTridents {
    record Returning(ServerPlayer owner,ItemEntity drop,int slot,double at) {}
    record Recoverable(ServerPlayer owner,ItemEntity drop,int slot) {}
    static void launch(BattleSession battle,ServerPlayer player,ItemStack weapon,Vec3 landing,int slot,double baseAv) {
        battle.commitItem(()->launchCommitted(battle,player,weapon,landing,slot,baseAv));
    }
    private static void launchCommitted(BattleSession battle,ServerPlayer player,ItemStack weapon,Vec3 landing,int slot,double baseAv) {
        var owner=battle.member(player);var original=weapon.copy();long revision=battle.revision;var budget=battle.budget;var actor=battle.actor;
        java.util.function.BooleanSupplier valid=()->!battle.closed && battle.members.get(player.getUUID())==owner
                && player.isAlive() && player.level()==battle.level && battle.revision==revision && battle.budget==budget && battle.actor==actor
                && player.getInventory().getItem(slot)==weapon && ItemStack.matches(weapon,original);
        var thrown=weapon.copyWithCount(1);
        try{thrown.hurtAndBreak(1,player,EquipmentSlot.MAINHAND);}
        catch(RuntimeException failure){throw new RuntimeException("Trident durability callback failed",failure);}
        if(!valid.getAsBoolean())throw new RuntimeException("Trident ownership changed during durability callback");
        if(thrown.isEmpty())throw new IllegalArgumentException("三叉戟无法完成投掷。");
        var projectile=new net.minecraft.world.entity.projectile.ThrownTrident(player.level(),player,thrown.copy());
        int loyalty;
        try {loyalty=net.minecraft.util.Mth.clamp(net.minecraft.world.item.enchantment.EnchantmentHelper
                .getTridentReturnToOwnerAcceleration(player.serverLevel(),thrown,projectile),0,127);}
        catch(RuntimeException failure){throw new RuntimeException("Trident enchantment callback failed",failure);}
        finally{projectile.discard();}
        if(!valid.getAsBoolean())throw new RuntimeException("Trident ownership changed during enchantment callback");
        if(!player.hasInfiniteMaterials()) {
            var drop=new ItemEntity(player.level(),landing.x,landing.y,landing.z,thrown);
            drop.setDeltaMovement(Vec3.ZERO);drop.setTarget(player.getUUID());drop.setDefaultPickUpDelay();
            drop.setUnlimitedLifetime();
            var expectedDrop=thrown.copy();boolean spawned;
            try{spawned=player.serverLevel().addFreshEntity(drop);}
            catch(RuntimeException failure){drop.discard();throw new RuntimeException("Trident spawn callback failed",failure);}
            if(!valid.getAsBoolean() || drop.getItem()!=thrown || !ItemStack.matches(thrown,expectedDrop)){
                drop.discard();throw new RuntimeException("Trident ownership changed during spawn callback");
            }
            if(!spawned){drop.discard();throw new IllegalArgumentException("无法生成可回收的三叉戟，本次未投掷。");}
            if(!drop.isAlive() || drop.level()!=battle.level || battle.level.getEntity(drop.getId())!=drop){
                drop.discard();throw new RuntimeException("Trident spawn did not retain its real drop");
            }
            weapon.shrink(1);
            battle.recoverableTridents.put(drop.getUUID(),new Recoverable(player,drop,slot));
            if(loyalty>0)battle.returningTridents.add(new Returning(player,drop,slot,battle.clock.time()+baseAv/loyalty));
        }
        player.inventoryMenu.broadcastChanges();
    }
    static void advance(BattleSession battle) {
        battle.returningTridents.removeIf(pending->{
            if(pending.at()>battle.clock.time()+1e-7)return false;
            recover(battle,pending);return true;
        });
    }
    static void leave(BattleSession battle,ServerPlayer owner) {
        battle.returningTridents.removeIf(pending->{
            if(pending.owner()!=owner)return false;
            recover(battle,pending);return true;
        });
        battle.recoverableTridents.values().removeIf(pending->pending.owner()==owner);
    }
    static Recoverable nearest(BattleSession battle,ServerPlayer owner,double range) {
        if(battle.closed || !battle.members.containsKey(owner.getUUID()) || !owner.isAlive() || owner.level()!=battle.level
                || !Double.isFinite(range) || range<=0 || range>8)return null;
        return battle.recoverableTridents.values().stream().filter(pending->{
            var drop=pending.drop();
            return pending.owner()==owner && drop.isAlive() && drop.level()==battle.level
                    && battle.level.getEntity(drop.getId())==drop && drop.getItem().is(net.minecraft.world.item.Items.TRIDENT)
                    && drop.getItem().getCount()==1 && BattleManager.gap(owner.getBoundingBox(),drop.getBoundingBox())<=range
                    && battle.level.clip(new net.minecraft.world.level.ClipContext(owner.getEyePosition(),drop.getBoundingBox().getCenter(),
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,owner)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
        }).min(java.util.Comparator.<Recoverable>comparingDouble(pending->pending.drop().distanceToSqr(owner))
                .thenComparingInt(pending->pending.drop().getId())).orElse(null);
    }
    private static int emptySlot(ServerPlayer owner,int preferred) {
        if(preferred>=0 && preferred<36 && owner.getInventory().getItem(preferred).isEmpty())return preferred;
        for(int slot=0;slot<36;slot++)if(owner.getInventory().getItem(slot).isEmpty())return slot;
        return -1;
    }
    static String recoveryError(BattleSession battle,ServerPlayer owner,double range) {
        var pending=nearest(battle,owner,range);
        if(pending==null)return "附近没有视线可达的本场投掷三叉戟。";
        return emptySlot(owner,pending.slot())<0?"背包已满，需要一个空位回收三叉戟。":null;
    }
    static void recoverNearest(BattleSession battle,ServerPlayer owner,double range) {
        var pending=nearest(battle,owner,range);
        if(pending==null)throw new IllegalArgumentException("附近没有可回收三叉戟。");
        int slot=emptySlot(owner,pending.slot());
        if(slot<0)throw new IllegalArgumentException("背包已满，需要一个空位回收三叉戟。");
        var drop=pending.drop();var stack=drop.getItem();var visual=stack.copy();var origin=drop.position();
        // Remove bookkeeping and detach the real stack before transfer. No new item is manufactured.
        battle.recoverableTridents.remove(drop.getUUID());
        battle.returningTridents.removeIf(returning->returning.drop()==drop);
        drop.setItem(ItemStack.EMPTY);drop.discard();owner.getInventory().setItem(slot,stack);
        com.matuvent.mineturn.api.CombatVisuals.flight(owner.serverLevel(),origin,owner.getEyePosition(),visual,false,false);
        owner.inventoryMenu.broadcastChanges();
    }
    private static void recover(BattleSession battle,Returning pending) {
        var owner=pending.owner();var drop=pending.drop();
        // The real entity is the sole source of the item; destroyed/unloaded drops cannot be recreated.
        if(!drop.isAlive() || drop.level().getEntity(drop.getId())!=drop || !owner.isAlive()
                || owner.isRemoved() || owner.level()!=drop.level() || owner.isSpectator())return;
        var stack=drop.getItem();var visual=stack.copyWithCount(1);var origin=drop.position().add(0,0.25,0);
        if(owner.getInventory().getItem(pending.slot()).isEmpty()) {
            owner.getInventory().setItem(pending.slot(),stack);drop.discard();
        }else {
            owner.getInventory().add(stack);
            if(stack.isEmpty())drop.discard();
            else {drop.setItem(stack);drop.setPos(owner.position());drop.setDeltaMovement(Vec3.ZERO);drop.setDefaultPickUpDelay();}
        }
        com.matuvent.mineturn.api.CombatVisuals.flight(owner.serverLevel(),origin,owner.getEyePosition(),visual,false,false);
        if(!drop.isAlive())battle.recoverableTridents.remove(drop.getUUID());
        owner.inventoryMenu.broadcastChanges();
    }
    private BattleTridents() {}
}
