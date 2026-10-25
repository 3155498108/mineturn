package com.matuvent.mineturn.client;

import com.matuvent.mineturn.network.TrailsNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.*;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;

/** Only client-native animation flags; every flag we borrow has a matching restoration. */
final class TrailsSpeciesAnimations {
    static Runnable begin(LivingEntity e,TrailsNetwork.Performance packet) {
        var restores=new java.util.ArrayList<Runnable>();
        if(e instanceof Vex vex){boolean value=vex.isCharging();restores.add(()->vex.setIsCharging(value));}
        if(e instanceof Pufferfish fish){int value=fish.getPuffState();restores.add(()->fish.setPuffState(value));}
        if(e instanceof Bat bat){boolean value=bat.isResting();restores.add(()->bat.setResting(value));bat.setResting(false);}
        if(e instanceof SpellcasterIllager spell){var data=com.matuvent.mineturn.mixin.TrailsSpellAccess.mineturn$spellData();byte value=spell.getEntityData().get(data);restores.add(()->spell.getEntityData().set(data,value));}
        if(e instanceof Shulker shulker){var access=(com.matuvent.mineturn.mixin.ShulkerBattleAccess)shulker;int value=access.mineturn$peek();restores.add(()->access.mineturn$peek(value));}
        if(e instanceof net.minecraft.world.entity.animal.armadillo.Armadillo armadillo){var state=armadillo.getState();restores.add(()->armadillo.switchToState(state));armadillo.switchToState(net.minecraft.world.entity.animal.armadillo.Armadillo.ArmadilloState.ROLLING);}
        if(e instanceof Goat goat)restores.add(()->goat.handleEntityEvent((byte)59));
        if(e instanceof Frog frog){var target=frog.getTongueTarget();restores.add(()->{frog.eraseTongueTarget();target.ifPresent(frog::setTongueTarget);});}
        if(e instanceof WitherBoss wither){int[] targets={wither.getAlternativeTarget(0),wither.getAlternativeTarget(1),wither.getAlternativeTarget(2)};restores.add(()->{for(int i=0;i<3;i++)wither.setAlternativeTarget(i,targets[i]);});}
        if(e instanceof EnderDragon dragon){var phase=dragon.getPhaseManager().getCurrentPhase().getPhase();restores.add(()->dragon.getPhaseManager().setPhase(phase));dragon.getPhaseManager().setPhase(packet.animation().equals("strike")?EnderDragonPhase.SITTING_FLAMING:EnderDragonPhase.HOVERING);}
        if(e instanceof Warden warden){
            restores.add(()->{warden.roarAnimationState.stop();warden.attackAnimationState.stop();warden.sonicBoomAnimationState.stop();});
            if(packet.style().contains("sonic") && (packet.animation().equals("draw") || packet.animation().equals("strike")))warden.handleEntityEvent((byte)62);
            else if(packet.animation().equals("strike"))warden.handleEntityEvent((byte)4);
            else if(packet.animation().equals("draw") || packet.animation().equals("telegraph"))warden.setPose(Pose.ROARING);
        }
        return ()->restores.forEach(Runnable::run);
    }
    static void pose(LivingEntity e,TrailsNetwork.Performance packet) {
        boolean active=!packet.animation().equals("recover"),draw=packet.animation().equals("draw") || packet.animation().equals("telegraph");
        if(e instanceof Vex vex)vex.setIsCharging(active);
        if(e instanceof SpellcasterIllager spell)spell.getEntityData().set(com.matuvent.mineturn.mixin.TrailsSpellAccess.mineturn$spellData(),(byte)(active?2:0));
        if(e instanceof Shulker shulker)((com.matuvent.mineturn.mixin.ShulkerBattleAccess)shulker).mineturn$peek(active?100:0);
        if(e instanceof Pufferfish fish)fish.setPuffState(active?2:0);
        if(e instanceof Goat goat)goat.handleEntityEvent((byte)(draw?58:59));
        if(e instanceof Frog frog && active){
            if(packet.style().contains("tongue")){
                var target=Minecraft.getInstance().level.getEntitiesOfClass(LivingEntity.class,e.getBoundingBox().inflate(12),other->other!=e && other.position().distanceToSqr(packet.aim())<4).stream().findFirst();
                target.ifPresent(frog::setTongueTarget);frog.setPose(Pose.USING_TONGUE);
            }else frog.setPose(Pose.CROAKING);
        }
        if(e instanceof WitherBoss wither && active){
            var target=Minecraft.getInstance().level.getEntitiesOfClass(LivingEntity.class,e.getBoundingBox().inflate(32),other->other!=e && other.getEyePosition().distanceToSqr(packet.aim())<9).stream().findFirst();
            target.ifPresent(t->{for(int i=0;i<3;i++)wither.setAlternativeTarget(i,t.getId());});
        }
    }
    private TrailsSpeciesAnimations(){}
}
