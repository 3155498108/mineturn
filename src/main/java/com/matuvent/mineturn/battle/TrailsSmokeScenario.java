package com.matuvent.mineturn.battle;

import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.level.storage.LevelResource;

/** Developer-only fixtures for the real renderer, fenced to the explicitly named disposable QA save. */
public final class TrailsSmokeScenario {
    private static final List<LivingEntity> spawned = new ArrayList<>();
    public static void prepare(ServerPlayer p, int mode) {
        if (com.matuvent.mineturn.platform.RuntimePlatform.production() || !Boolean.getBoolean("mineturn.encounterSmoke")
                || !p.server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().matches("AT-QA-[A-Za-z0-9-]+"))
            throw new IllegalStateException("QA scenario refused outside named QA save");
        boolean counter=mode==24;if(counter)mode=7;
        var previous = BattleManager.ACTIVE.get(p.getUUID()); if (previous != null) previous.close("QA scenario transition");
        for (var e : spawned) e.discard(); spawned.clear();
        for (var mob : p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(32))) mob.discard();
        p.teleportTo(.5, -59, .5); p.setHealth(20); p.setYRot(0);
        p.getInventory().setItem(4, new ItemStack(Items.ARROW, 32)); p.getInventory().setItem(5, new ItemStack(Items.WIND_CHARGE, 16));
        var type = mode == 6 ? EntityType.BREEZE : mode == 7 ? EntityType.SKELETON : EntityType.ZOMBIE;
        var enemy = type.create(p.serverLevel()); enemy.moveTo(.5, -59, mode == 6 || mode == 7 ? 8.5 : 3.5, 180, 0); enemy.setNoAi(true);
        enemy.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        if (mode == 7) enemy.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500); enemy.setHealth(500); p.serverLevel().addFreshEntity(enemy); spawned.add(enemy);
        LivingEntity friend = null;
        if (mode >= 8 && mode <= 10) {
            var friendlyType = mode == 8 ? EntityType.SHEEP : mode == 9 ? EntityType.IRON_GOLEM : EntityType.CHICKEN;
            var animal = friendlyType.create(p.serverLevel()); animal.moveTo(-1.5, -59, 1.5, 0, 0); animal.setNoAi(true);
            p.serverLevel().addFreshEntity(animal); spawned.add(animal); friend = animal;
        }
        var b = new BattleSession(p, List.copyOf(spawned), false);
        if (mode < 6 || mode >= 11) {
            var weapons = List.of(Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW, Items.MACE, Items.TRIDENT);
            int weaponIndex=mode==23?0:mode<6?mode:mode<17?mode-11:mode-17;
            p.getInventory().setItem(0, new ItemStack(weapons.get(weaponIndex)));
            com.matuvent.mineturn.platform.RuntimePlatform.data(p).putInt("mineturn.trails.cp", 200);
 p.getInventory().selected = 0;
            TrailsCombat.setPrimary(p);
            p.inventoryMenu.broadcastChanges();
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
            ((com.matuvent.mineturn.mixin.StatusAccess)p).mineturn$equipment();
            b.actor = p; b.trails.onTurn();
        } else if (mode < 8) {
            b.actor = enemy; b.trails.onTurn(); b.trails.turns.put(enemy.getUUID(), 3);
            // A high-health QA target must still exercise the normal species skill, not the generic boss loop.
            enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(mode == 6 ? 30 : counter ? 80 : 20); enemy.setHealth(enemy.getMaxHealth());
            b.trails.enemyWait = 20;
        } else { b.actor = friend; b.trails.onTurn(); }
        if(mode==23)for(int z=-4;z<=2;z++)for(int y=-59;y<=-53;y++)p.serverLevel().setBlock(new net.minecraft.core.BlockPos(3,y,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3);
        b.revision++; b.syncAll();
    }
    public static void stop(ServerPlayer p) {
        var b = BattleManager.ACTIVE.get(p.getUUID()); if (b != null) b.close("QA presentation complete");
    }
    public static void bestiary(ServerPlayer p,String id,boolean ultimate) {
        if(com.matuvent.mineturn.platform.RuntimePlatform.production() || !Boolean.getBoolean("mineturn.encounterSmoke")
                || !p.server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().matches("AT-QA-[A-Za-z0-9-]+"))throw new IllegalStateException("QA save required");
        stop(p);for(var e:spawned)e.discard();spawned.clear();
        for(var mob:p.serverLevel().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(40)))mob.discard();
        p.teleportTo(.5,-59,.5);p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);p.setHealth(500);p.removeAllEffects();
        p.getInventory().setItem(0,new ItemStack(Items.IRON_SWORD));p.getInventory().selected=0;p.inventoryMenu.broadcastChanges();
        var type=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.withDefaultNamespace(id));
        var actor=(Mob)type.create(p.serverLevel());actor.moveTo(.5,id.equals("ender_dragon")?-55:-59,8.5,180,0);actor.setNoAi(true);
        if(actor instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither)wither.setInvulnerableTicks(0);
        actor.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);actor.setHealth(500);p.serverLevel().addFreshEntity(actor);spawned.add(actor);
        if(TrailsCombat.friendly(actor,p)){
            actor.moveTo(-1.5,-59,1.5,0,0);var z=EntityType.ZOMBIE.create(p.serverLevel());z.moveTo(-1.5,-59,3,180,0);z.setNoAi(true);z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);z.setHealth(500);p.serverLevel().addFreshEntity(z);spawned.add(z);
        }
        var b=new BattleSession(p,List.copyOf(spawned),false);b.actor=actor;b.trails.onTurn();b.trails.turns.put(actor.getUUID(),3);
        if(ultimate){TrailsBestiaryActions.prepare(b.trails,actor,p,TrailsBestiary.get(actor).ultimate(),true);b.actor=p;b.trails.onTurn();}
        else b.trails.enemyWait=-12;
        b.revision++;b.syncAll();
    }
    public static void releaseBestiary(ServerPlayer p){
        var b=BattleManager.ACTIVE.get(p.getUUID());var actor=spawned.getFirst();b.actor=actor;b.trails.onTurn();
        if(b.trails.allied(actor))b.trails.companionSkill(b.enemies(actor).getFirst().getId());else b.trails.enemyAction();
        b.revision++;b.syncAll();
    }
    public static void damageNumbers(ServerPlayer p,int mode){
        prepare(p,0);p.removeAllEffects();
        if(mode==2)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST,1200,4));
        var b=BattleManager.ACTIVE.get(p.getUUID());b.trails.criticalRoll=()->mode==1 || mode==3 || mode==4?0:1;
        if(mode==4)b.enemies(p).forEach(e->e.setHealth(6));
        if(mode==3)b.enemies(p).forEach(e->e.setInvulnerable(true));
        b.revision++;b.syncAll();
    }
    private TrailsSmokeScenario() {}
}
