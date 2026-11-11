package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Real-client compatibility checks use only the existing isolated update QA world. */
public final class TrailsCompatibilityScenario {
    public static volatile boolean ready, verified;
    public static volatile int targetId;
    public static volatile String weaponName;
    public static final String[] BOSSES = {"mutant_zombie", "mutant_skeleton", "mutant_creeper", "mutant_enderman"};

    private static ServerPlayer prepare(MinecraftServer server) {
        var player = TrailsUpdateScenario.player(server);
        TrailsUpdateScenario.stop(server);
        for (var mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(80))) mob.discard();
        player.teleportTo(.5, -59, .5);
        player.setHealth(player.getMaxHealth());
        ready = verified = false;
        return player;
    }

    public static void weapon(MinecraftServer server) {
        var player = prepare(server);
        ItemStack weapon = Items.IRON_PICKAXE.getDefaultInstance();
        if (Boolean.getBoolean("mineturn.nativeAutoQa")) {
            var item = ResourceLocation.fromNamespaceAndPath("mutantmonsters", "hulk_hammer");
            if (!BuiltInRegistries.ITEM.containsKey(item)) throw new IllegalStateException("Native compatibility weapon missing");
            weapon = BuiltInRegistries.ITEM.get(item).getDefaultInstance();
        }
        if (TrailsWeaponSkills.kind(weapon) != TrailsWeaponSkills.Kind.MELEE) throw new IllegalStateException("Attribute weapon was not adapted");
        player.getInventory().clearContent();
        player.getInventory().setItem(0, weapon);
        player.getInventory().selected = 0;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        TrailsCp.set(player, 100);
        var target = EntityType.ZOMBIE.create(player.serverLevel());
        target.moveTo(3, -59, .5);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        target.setNoAi(true);
        player.serverLevel().addFreshEntity(target);
        TrailsCombat.start(player);
        var battle = BattleManager.ACTIVE.get(player.getUUID());
        battle.actor = player;
        battle.trails.onTurn();
        battle.trails.criticalRoll = () -> 1;
        battle.syncAll();
        player.inventoryMenu.broadcastChanges();
        targetId = target.getId();
        weaponName = weapon.getHoverName().getString();
        ready = true;
    }

    public static void inspectWeapon(MinecraftServer server) {
        inspect(server, 75, "WEAPON");
    }

    public static void burst(MinecraftServer server) {
        weapon(server);
        var player = TrailsUpdateScenario.player(server);
        player.getInventory().setItem(1, player.getMainHandItem().copy());
        player.getInventory().selected = 1;
        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(1));
        TrailsCombat.setPrimary(player);
        TrailsCp.set(player, 200);
        BattleManager.ACTIVE.get(player.getUUID()).syncAll();
        player.inventoryMenu.broadcastChanges();
    }

    public static void inspectBurst(MinecraftServer server) {
        inspect(server, 100, "BURST");
    }

    private static void inspect(MinecraftServer server, int expectedCp, String action) {
        if (verified) return;
        var player = TrailsUpdateScenario.player(server);
        var battle = BattleManager.ACTIVE.get(player.getUUID());
        var impact = battle == null ? null : battle.trails.lastImpact;
        if (impact == null || impact.target() != targetId || impact.amount() <= 0) return;
        if (TrailsCp.get(player) != expectedCp) throw new IllegalStateException("Attribute action CP mismatch");
        verified = true;
        MineTurn.LOGGER.info("AUTO_QA_{} {} damage={} cp={}", action, player.getMainHandItem(), impact.amount(), TrailsCp.get(player));
    }

    public static void boss(MinecraftServer server, int index) {
        var player = prepare(server);
        var id = ResourceLocation.fromNamespaceAndPath("mutantmonsters", BOSSES[index]);
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id) || TrailsBestiary.get(id.toString()) != null) throw new IllegalStateException("QA needs an unconfigured external entity");
        var entity = BuiltInRegistries.ENTITY_TYPE.get(id).create(player.serverLevel());
        if (!(entity instanceof Mob boss)) throw new IllegalStateException("External mob missing");
        boss.moveTo(5, -59, .5);
        boss.setNoAi(true);
        player.serverLevel().addFreshEntity(boss);
        var profile = TrailsBestiary.get(boss);
        if (profile == null || profile.ultimate() == null) throw new IllegalStateException("External boss did not generate an S skill");
        if (index == 1 && profile.basic().range() != 24) throw new IllegalStateException("Skeleton tag did not generate a ranged profile");
        TrailsCombat.start(player);
        var battle = BattleManager.ACTIVE.get(player.getUUID());
        if (battle == null || !battle.trails.enemy(player, boss)) throw new IllegalStateException("External hostile joined the wrong faction");
        battle.trails.criticalRoll = () -> 1;
        battle.actor = boss;
        battle.trails.onTurn();
        battle.trails.turns.put(boss.getUUID(), 3);
        battle.trails.enemyAction();
        if (!battle.trails.plans.containsKey(boss.getUUID())) throw new IllegalStateException("External boss did not forecast S");
        battle.actor = player;
        battle.trails.onTurn();
        battle.syncAll();
        targetId = boss.getId();
        ready = true;
    }

    public static void releaseBoss(MinecraftServer server) {
        var player = TrailsUpdateScenario.player(server);
        var battle = BattleManager.ACTIVE.get(player.getUUID());
        var boss = (Mob) player.serverLevel().getEntity(targetId);
        battle.actor = boss;
        battle.trails.onTurn();
        battle.trails.enemyAction();
        if (battle.trails.pending == null || battle.trails.plans.containsKey(boss.getUUID())) throw new IllegalStateException("External S did not commit");
        verified = true;
        MineTurn.LOGGER.info("AUTO_QA_EXTERNAL_S {}", BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()));
    }

    public static void friendly(MinecraftServer server) {
        var player = prepare(server);
        var id = ResourceLocation.fromNamespaceAndPath("mutantmonsters", "mutant_snow_golem");
        var ally = (Mob) BuiltInRegistries.ENTITY_TYPE.get(id).create(player.serverLevel());
        ally.moveTo(2, -59, .5);
        ally.setNoAi(true);
        ally.setHealth(5);
        player.serverLevel().addFreshEntity(ally);
        var enemy = EntityType.ZOMBIE.create(player.serverLevel());
        enemy.moveTo(10, -59, .5);
        enemy.setNoAi(true);
        player.serverLevel().addFreshEntity(enemy);
        TrailsCombat.start(player);
        var battle = BattleManager.ACTIVE.get(player.getUUID());
        if (!battle.trails.allied(ally) || !TrailsBestiary.get(ally).skill().friendly()) throw new IllegalStateException("External friendly has no support skill");
        battle.actor = ally;
        battle.trails.onTurn();
        battle.syncAll();
        targetId = ally.getId();
        ready = true;
    }

    public static void inspectFriendly(MinecraftServer server) {
        var player = TrailsUpdateScenario.player(server);
        var ally = (Mob) player.serverLevel().getEntity(targetId);
        if (ally.getHealth() > 5 && TrailsCp.get(ally) == 65) {
            verified = true;
            MineTurn.LOGGER.info("AUTO_QA_EXTERNAL_ALLY hp={} cp={}", ally.getHealth(), TrailsCp.get(ally));
        }
    }

    private TrailsCompatibilityScenario() {}
}
