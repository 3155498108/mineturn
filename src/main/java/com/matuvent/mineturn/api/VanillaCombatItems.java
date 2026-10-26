package com.matuvent.mineturn.api;

import com.matuvent.mineturn.mixin.StatusAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/** Explicit, targeted adapters. Arbitrary item use is never invoked by the battle GUI. */
final class VanillaCombatItems {
    static String slotError(CombatEffects.Context context) {
        if(!(context.source() instanceof ServerPlayer player))return "这个动作仅供玩家使用。";
        return slot(player,context.item())<0 ? "请将物品放入快捷栏。" : null;
    }
    private static int slot(ServerPlayer player,ItemStack item) {
        for(int i=0;i<9;i++)if(player.getInventory().getItem(i)==item)return i;
        return -1;
    }
    static void melee(CombatEffects.Context context) {
        if(!(context.source() instanceof ServerPlayer player)) {
            context.battle().hurt(context.target(),(float)context.action().amount());return;
        }
        withSelected(context,()->{
            var target=context.target();var stack=context.item();
            // Battle mace attacks are ordinary melee; only the initiating vanilla hit may smash.
            if(stack.getItem() instanceof net.minecraft.world.item.MaceItem)player.resetFallDistance();
            if(!com.matuvent.mineturn.platform.RuntimePlatform.attack(player,target))return;
            if(!context.battle().canContinue(target))return;
            var damageSource=player.damageSources().playerAttack(player);
            float base=(float)player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float damage=EnchantmentHelper.modifyDamage(player.serverLevel(),stack,target,damageSource,base)
                    +stack.getItem().getAttackDamageBonus(target,base,damageSource);
            // Snapshot around the selected target; never query arbitrary world entities for collateral damage.
            var sweepTargets=com.matuvent.mineturn.platform.RuntimePlatform.swordSweep(stack)
                    ? context.battle().enemies().stream().filter(other->other!=target && other.isAlive()
                        && com.matuvent.mineturn.platform.RuntimePlatform.sweep(stack,player,target).intersects(other.getBoundingBox())
                        && player.distanceToSqr(other)<player.entityInteractionRange()*player.entityInteractionRange()
                        && player.hasLineOfSight(other)).toList()
                    : java.util.List.<net.minecraft.world.entity.LivingEntity>of();
            float sweepBase=1+(float)player.getAttributeValue(Attributes.SWEEPING_DAMAGE_RATIO)*base;
            if(!context.battle().canContinue(target))return;
            target.invulnerableTime=0;
            if(target.hurt(damageSource,damage)) {
                if(!context.battle().canFinishAttack(target))return;
                player.setLastHurtMob(target);
                for(var other:sweepTargets) {
                    if(!context.battle().canContinue(other))continue;
                    other.invulnerableTime=0;
                    float sweepDamage=EnchantmentHelper.modifyDamage(player.serverLevel(),stack,other,damageSource,sweepBase);
                    if(!context.battle().canContinue(other))continue;
                    if(other.hurt(damageSource,sweepDamage) && context.battle().canFinishAttack(other))
                        EnchantmentHelper.doPostAttackEffects(player.serverLevel(),other,damageSource);
                }
                for(var other:sweepTargets)if(context.battle().canFinishAttack(other))other.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                if(!context.battle().canFinishAttack(target))return;
                if(!sweepTargets.isEmpty())player.sweepAttack();
                boolean durability=stack.hurtEnemy(target,player);
                if(!context.battle().canFinishAttack(target))return;
                EnchantmentHelper.doPostAttackEffects(player.serverLevel(),target,damageSource);
                if(!context.battle().canFinishAttack(target))return;
                if(durability && !stack.isEmpty())stack.postHurtEnemy(target,player);
                if(!context.battle().canFinishAttack(target))return;
                target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                player.causeFoodExhaustion(0.1f);
            }
        });
    }
    static void mobMelee(CombatEffects.Context context) {
        var mob=context.source();var target=context.target();
        ((StatusAccess)mob).mineturn$equipment();
        var stack=mob.getMainHandItem();var source=mob.damageSources().mobAttack(mob);
        var level=(net.minecraft.server.level.ServerLevel)mob.level();
        float base=(float)mob.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float damage=EnchantmentHelper.modifyDamage(level,stack,target,source,base)
                +stack.getItem().getAttackDamageBonus(target,base,source);
        if(context.battle()!=null && !context.battle().canContinue(target))return;
        target.invulnerableTime=0;
        if(target.hurt(source,damage)) {
            if(context.battle()!=null && !context.battle().canFinishAttack(target))return;
            mob.setLastHurtMob(target);
            EnchantmentHelper.doPostAttackEffects(level,target,source);
        }
        if(context.battle()==null || context.battle().canFinishAttack(target))target.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
    }
    static void drink(CombatEffects.Context context) {
        withSelected(context,()->{
            ServerPlayer player=(ServerPlayer)context.source();
            int usedSlot=player.getInventory().selected;
            ItemStack result=context.item().finishUsingItem(player.level(),player);
            storeUseResult(player,usedSlot,context.item(),result);
        });
    }
    static boolean drinkable(ItemStack stack){return stack.is(Items.POTION)||stack.is(Items.MILK_BUCKET);}
    static void food(CombatEffects.Context context) {
        ServerPlayer player=(ServerPlayer)context.source();
        var stack=context.item();
        for(int slot=0;slot<36;slot++)if(player.getInventory().getItem(slot)==stack) {
            // Player.eat owns consumption and FOOD.using_converts_to, including last-stack replacement.
            var result=player.eat(player.level(),stack,com.matuvent.mineturn.platform.RuntimePlatform.food(stack,player));
            storeUseResult(player,slot,stack,result);
            player.inventoryMenu.broadcastChanges();
            return;
        }
        throw new IllegalStateException("Food left player inventory");
    }
    private static void storeUseResult(ServerPlayer player,int originalSlot,ItemStack used,ItemStack result) {
        var inventory=player.getInventory();
        // Follow the actual stack, including an exhausted stack, never a matching copy.
        if(inventory.getItem(originalSlot)==used){inventory.setItem(originalSlot,result);return;}
        for(int slot=0;slot<inventory.getContainerSize();slot++)if(inventory.getItem(slot)==used){
            inventory.setItem(slot,result);return;
        }
        // A callback replaced/removed ownership. Do not overwrite its replacement or mint a second result.
        throw new IllegalStateException("Used item left inventory during native consumption");
    }
    private static void withSelected(CombatEffects.Context context,Runnable action) {
        ServerPlayer player=(ServerPlayer)context.source();int chosen=slot(player,context.item());
        if(chosen<0)throw new IllegalStateException("Battle item left hotbar");
        int previous=player.getInventory().selected;
        var access=(StatusAccess)player;
        try {
            player.getInventory().selected=chosen;access.mineturn$equipment();action.run();
        }finally {
            player.getInventory().selected=previous;access.mineturn$equipment();
            player.inventoryMenu.broadcastChanges();
        }
    }
    private VanillaCombatItems(){}
}
