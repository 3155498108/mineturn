package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.platform.MobSplit;

import com.matuvent.mineturn.battle.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.*;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.*;

/** Native event cancellation stays at the loader boundary. */
public final class NeoForgeBattleEvents {
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void explosion(ExplosionEvent.Detonate e){BattleExplosion.filter(e.getExplosion(),e.getAffectedEntities());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void split(MobSplitEvent e){
        if(BattleSummons.temporary(e.getParent())){e.setCanceled(true);return;}
        BattleManager.split(e.getParent(),new MobSplit(){public boolean isCanceled(){return e.isCanceled();} public java.util.List<? extends net.minecraft.world.entity.Mob> getChildren(){return e.getChildren();}});
    }
    @SubscribeEvent public static void drops(LivingDropsEvent e){if(BattleSummons.temporary(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void experience(LivingExperienceDropEvent e){if(BattleSummons.temporary(e.getEntity()))e.setDroppedExperience(0);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void join(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide && e.loadedFromDisk() && BattleSummons.temporary(e.getEntity()))e.setCanceled(true);
        if(com.matuvent.mineturn.api.CombatChanneling.capture(e.getEntity()))e.setCanceled(true);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void shield(LivingShieldBlockEvent e){BattleManager.shieldBlocked(e.getEntity(),e.getBlocked(),e.getBlockedDamage());}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void effect(MobEffectEvent.Applicable e){
        if(!e.getEntity().level().isClientSide && !BattleManager.authorized() && e.getEffectSource()!=null && !BattleManager.allowsRealtimeEffect(e.getEffectSource(),e.getEntity()))e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
    }
    @SubscribeEvent public static void knockback(LivingKnockBackEvent e){if(BattleManager.locked(e.getEntity()) || BattleRiding.rider(e.getEntity())!=null)e.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void knockback(ExplosionKnockbackEvent e){var source=e.getExplosion().getIndirectSourceEntity();if(BattleManager.locked(e.getAffectedEntity()) || source!=null && BattleManager.locked(source))e.setKnockbackVelocity(net.minecraft.world.phys.Vec3.ZERO);}
    @SubscribeEvent public static void incoming(LivingIncomingDamageEvent e){e.setAmount(TrailsCombat.guardDamage(e.getEntity(),e.getAmount()));if(BattleManager.cancelIncoming(e.getEntity(),e.getSource(),e.getAmount()))e.setCanceled(true);}
    @SubscribeEvent public static void damaged(LivingDamageEvent.Post e){BattleManager.damaged(e.getEntity(),e.getSource(),e.getNewDamage());}
    @SubscribeEvent public static void entityTick(EntityTickEvent.Pre e){if(BattleManager.freezeTick(e.getEntity()))e.setCanceled(true);}
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Pre e){if(BattleManager.locked(e.getPlayer()))e.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){BattleManager.tick();}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){BattleManager.logout(e.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){BattleManager.changedDimension(e.getEntity());}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){BattleManager.stopping(e.getServer());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){BattleManager.stopped(e.getServer());TrailsTeams.stop(e.getServer());}
    @SubscribeEvent public static void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){BattleManager.commands(e.getDispatcher());TrailsTeams.commands(e.getDispatcher());}
    private NeoForgeBattleEvents(){}
}
