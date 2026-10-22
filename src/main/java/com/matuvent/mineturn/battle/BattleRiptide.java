package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.api.Riptide;
import com.matuvent.mineturn.data.CombatData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class BattleRiptide {
    record Route(TerrainPath.Result path,LivingEntity contact) {}
    static LivingEntity contact(BattleSession battle,ServerPlayer player,Vec3 from,Vec3 to) {
        var box=player.getBoundingBox().move(from.subtract(player.position())).expandTowards(to.subtract(from)).inflate(0.05);
        return battle.members.values().stream().map(m->m.entity).filter(e->e!=player && e.isAlive() && box.intersects(e.getBoundingBox()))
                .min(java.util.Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
    }
    static Route route(BattleSession battle,ServerPlayer player,ItemStack weapon,CombatData.Action action,Vec3 destination) {
        var offset=destination.subtract(player.position());double length=MovementDistance.spatial(offset);
        if(!Double.isFinite(length) || length<0.1 || length>Riptide.distance(player,weapon,action)+1e-6)
            throw new IllegalArgumentException("落点超出激流距离或距离过短。");
        LivingEntity[] contact={null};Vec3[] previous={player.position()};
        var path=BattleDisplacement.trace(battle,player,offset,true,point->{
            contact[0]=contact(battle,player,previous[0],point);previous[0]=point;return contact[0]==null;
        });
        if(path.samples().isEmpty())throw new IllegalArgumentException("前方没有可冲刺的安全路线。");
        return new Route(path,contact[0]);
    }
    static void launch(BattleSession battle,ServerPlayer player,ItemStack weapon,CombatData.Action action,Route route) {
        try{battle.commitItem(()->launchCommitted(battle,player,weapon,action,route));}
        catch(RuntimeException failure){throw new RuntimeException("Riptide committed action failed",failure);}
    }
    private static void launchCommitted(BattleSession battle,ServerPlayer player,ItemStack weapon,CombatData.Action action,Route route) {
        var owner=battle.member(player);var origin=player.position();var budget=battle.budget;long revision=battle.revision;
        java.util.function.BooleanSupplier present=()->!battle.closed && player.isAlive() && player.level()==battle.level && battle.members.get(player.getUUID())==owner;
        var snapshot=weapon.copy();
        try{weapon.hurtAndBreak(1,player,net.minecraft.world.entity.EquipmentSlot.MAINHAND);}
        catch(RuntimeException failure){throw new RuntimeException("Riptide durability callback failed",failure);}
        if(!present.getAsBoolean() || battle.budget!=budget || battle.revision!=revision || !player.position().equals(origin))
            throw new RuntimeException("Riptide owner changed during durability callback");
        BattleManager.authorized(()->{
            BattleDisplacement.apply(battle,player,route.path(),true);
            var target=route.contact();
            if(present.getAsBoolean() && target!=null && battle.members.containsKey(target.getUUID()) && target.isAlive() && battle.enemy(player,target)
                    && BattleManager.gap(player.getBoundingBox(),target.getBoundingBox())<=0.5 && player.hasLineOfSight(target)){
                var victim=battle.member(target);
                java.util.function.BooleanSupplier finish=()->present.getAsBoolean() && target.level()==battle.level && battle.members.get(target.getUUID())==victim;
                Riptide.hit(player,target,snapshot,()->finish.getAsBoolean() && target.isAlive()
                        && BattleManager.gap(player.getBoundingBox(),target.getBoundingBox())<=0.5 && player.hasLineOfSight(target),finish);
            }
        });
        if(!present.getAsBoolean())return;
        var sound=net.minecraft.world.item.enchantment.EnchantmentHelper.pickHighestLevel(snapshot,
                net.minecraft.world.item.enchantment.EnchantmentEffectComponents.TRIDENT_SOUND).orElse(net.minecraft.sounds.SoundEvents.TRIDENT_RIPTIDE_1);
        player.serverLevel().playSound(null,player,sound.value(),net.minecraft.sounds.SoundSource.PLAYERS,1,1);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(snapshot.getItem()));
        player.inventoryMenu.broadcastChanges();
    }
    private BattleRiptide() {}
}
