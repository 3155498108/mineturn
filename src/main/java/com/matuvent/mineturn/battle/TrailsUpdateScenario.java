package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Real client regression fixture, restricted to a disposable AT-QA world. */
public final class TrailsUpdateScenario {
    public static volatile boolean ready, verified;
    public static final List<Integer> portraits = new ArrayList<>();
    public static final String[] BOSSES = {"naga", "lich", "minoshroom", "hydra", "knight_phantom", "ur_ghast", "alpha_yeti", "snow_queen"};
    public static volatile int bossId;

    private static ServerPlayer player(MinecraftServer server) {
        String world = System.getProperty("mineturn.qaWorld", "");
        if (com.matuvent.mineturn.platform.RuntimePlatform.production() || !Boolean.getBoolean("mineturn.encounterSmoke")
                || !System.getProperty("mineturn.qaTag", "").startsWith("update") || !world.matches("AT-QA-[A-Za-z0-9-]+")
                || !server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString().equals(world))
            throw new IllegalStateException("Update QA requires its isolated world");
        return server.getPlayerList().getPlayers().getFirst();
    }

    public static void setup(MinecraftServer server) {
        var player = player(server);
        var level = player.serverLevel();
        ready = verified = false;
        for (var mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(80))) mob.discard();
        level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(18000);
        for (int x = -22; x <= 22; x++) for (int z = -22; z <= 22; z++) {
            level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.STONE.defaultBlockState());
            for (int y = -59; y < -38; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
        }
        player.setGameMode(GameType.SURVIVAL);
        player.teleportTo(.5, -59, .5);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        player.setHealth(200);
        player.getInventory().clearContent();
        player.getInventory().setItem(0, Items.IRON_SWORD.getDefaultInstance());
        player.getInventory().setItem(1, Items.BOW.getDefaultInstance());
        portraits.clear();
        var members = new ArrayList<LivingEntity>();
        var types = List.of(EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.COW, EntityType.WOLF,
                EntityType.ENDERMAN, EntityType.GHAST, EntityType.FOX, EntityType.RABBIT, EntityType.IRON_GOLEM, EntityType.ZOMBIE);
        for (int i = 0; i < types.size(); i++) {
            var mob = types.get(i).create(level);
            mob.moveTo(4 + i % 4 * 3, -59, 3 + i / 4 * 4);
            mob.setNoAi(true);
            level.addFreshEntity(mob);
            portraits.add(mob.getId());
            members.add(mob);
        }
        var battle = new BattleSession(player, members, false, false);
        battle.actor = player;
        battle.trails.onTurn();
        battle.syncAll();
        player.inventoryMenu.broadcastChanges();
        ready = true;
    }

    public static void boss(MinecraftServer server, int index) {
        var player = player(server);
        var old = BattleManager.ACTIVE.get(player.getUUID());
        if (old != null) old.close("next QA boss");
        for (var mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(80))) mob.discard();
        var type = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("twilightforest", BOSSES[index]));
        if (!(type.create(player.serverLevel()) instanceof Mob boss)) throw new IllegalStateException("Missing Twilight boss " + BOSSES[index]);
        boss.moveTo(10, -59, .5);
        boss.setNoAi(true);
        player.serverLevel().addFreshEntity(boss);
        // A native tick initializes multipart positions before the battle freezes them.
        boss.tick();
        player.setHealth(player.getMaxHealth());
        player.teleportTo(.5, -59, .5);
        var battle = new BattleSession(player, List.of(boss), false, false);
        battle.trails.criticalRoll = () -> 1;
        if (!TrailsCombat.hostile(boss, player) || battle.trails.allied(boss)) throw new IllegalStateException("Twilight boss joined as ally");
        battle.actor = boss;
        battle.trails.onTurn();
        battle.trails.turns.put(boss.getUUID(), 3);
        TrailsBestiaryActions.prepare(battle.trails, boss, player, TrailsBestiary.get(boss).ultimate(), true);
        // Hold the player's response turn so the gold warning is visible for screenshot review.
        battle.actor = player;
        battle.trails.onTurn();
        battle.revision++;
        battle.syncAll();
        bossId = boss.getId();
        verified = false;
    }

    public static void releaseBoss(MinecraftServer server) {
        var player = player(server);
        var battle = BattleManager.ACTIVE.get(player.getUUID());
        var boss = (LivingEntity) player.serverLevel().getEntity(bossId);
        float health = boss.getHealth();
        player.getInventory().selected = 1;
        int shieldsBefore = 0;
        if (BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath().equals("lich")) {
            try { shieldsBefore = (int) boss.getClass().getMethod("getShieldStrength").invoke(boss); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        }
        battle.trails.hit(player, boss, 16);
        if (BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()).getPath().equals("lich")) {
            try {
                int shields = (int) boss.getClass().getMethod("getShieldStrength").invoke(boss);
                if (shields != shieldsBefore - 1) throw new IllegalStateException("Ranged hit did not break one Lich shield, shields=" + shieldsBefore + " -> " + shields);
                boss.getClass().getMethod("setTeleportInvisibility", int.class).invoke(boss, 20);
                TrailsTwilight.onTurn(boss);
                if ((int) boss.getClass().getMethod("getTeleportInvisibility").invoke(boss) != 0) throw new IllegalStateException("Lich teleport immunity never finished in AT");
            } catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        } else if (boss.getHealth() >= health) throw new IllegalStateException("Boss cannot take AT damage " + boss.getType());
        battle.actor = boss;
        battle.trails.onTurn();
        TrailsBestiaryActions.enemy(battle.trails);
        if (battle.trails.pending == null || battle.trails.plans.containsKey(boss.getUUID())) throw new IllegalStateException("Boss S did not commit after warning");
        verified = true;
        MineTurn.LOGGER.info("TWILIGHT_QA_NATIVE_DAMAGE_AND_S {}", BuiltInRegistries.ENTITY_TYPE.getKey(boss.getType()));
    }

    public static void stop(MinecraftServer server) {
        var battle = BattleManager.ACTIVE.get(player(server).getUUID());
        if (battle != null) battle.close("Update QA complete");
    }
    private TrailsUpdateScenario() {}
}
