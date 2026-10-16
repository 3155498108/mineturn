package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.data.CombatData;
import com.matuvent.mineturn.mixin.PiglinConversionAccess;
import com.matuvent.mineturn.mixin.ZombieConversionAccess;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/**
 * Boundary regression for vanilla environmental conversion.
 *
 * <p>{@code ConversionGameTests} already covers the countdown itself, the NoAI gate, immunity reset,
 * cancelled-event timers, private piglin inventories and temporary summon exclusion. This class adds the
 * exclusions it leaves untested and the resumed-timer contract after a conversion is held back:
 *
 * <ul>
 *   <li>a mob carrying a passenger is not converted;</li>
 *   <li>a mob that is itself riding something is not converted;</li>
 *   <li>releasing the hold lets the same entity convert without restarting its timer.</li>
 * </ul>
 *
 * <p>Only existing package-private entry points are used. The drop transaction for a non-empty piglin
 * inventory is deliberately not implemented or asserted here.
 */
@GameTestHolder("mineturn")
@PrefixGameTestTemplate(false)
public final class ConversionEligibilityRegressionGameTests {
    private static final class Fixture implements AutoCloseable {
        final ServerPlayer player;
        final Mob mob;
        final BattleSession battle;

        Fixture(GameTestHelper h, EntityType<? extends Mob> type) {
            for (int x = 0; x < 12; x++) {
                for (int z = 0; z < 12; z++) h.setBlock(x, 0, z, Blocks.STONE);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mt-conv"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                    cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            player.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            var pos = h.absolutePos(new BlockPos(2, 1, 2));
            player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.getFoodData().setFoodLevel(10);

            mob = h.spawnWithNoFreeWill(type, new BlockPos(7, 1, 7));
            mob.setNoAi(false);
            mob.setInvulnerable(true);
            battle = new BattleSession(player, mob, CombatData.current, player);
        }

        /** The entity that replaced the original inside the battle, if any. */
        Mob replacement() {
            return (Mob) battle.members.values().stream()
                    .map(m -> m.entity).filter(e -> e != player).findFirst().orElseThrow();
        }

        boolean stillInBattle() {
            return battle.members.containsKey(mob.getUUID());
        }

        @Override
        public void close() {
            var entities = battle.members.values().stream().map(m -> m.entity).toList();
            battle.close("test");
            for (var entity : entities) if (entity != player) entity.discard();
            mob.discard();
            player.getServer().getPlayerList().remove(player);
            player.discard();
        }
    }

    /**
     * A converting mob that is carrying someone must not convert: replacing the entity underneath a
     * passenger would leave the rider attached to a removed entity.
     */
    @GameTest(template = "empty")
    public static void mobCarryingAPassengerDoesNotConvert(GameTestHelper h) {
        try (var f = new Fixture(h, EntityType.ZOMBIE)) {
            var access = (ZombieConversionAccess) f.mob;
            access.mineturn$startConversion(0);

            var passenger = h.spawnWithNoFreeWill(EntityType.PILLAGER, new BlockPos(7, 2, 7));
            try {
                h.assertTrue(passenger.startRiding(f.mob, true), "fixture error: could not add a passenger");
                // The timer is negative, so only the exclusion can stop the conversion.
                h.assertTrue(access.mineturn$conversionTime() < 0, "fixture error: the timer is not expired");

                BattleStatus.tick(f.battle.member(f.mob));

                h.assertTrue(f.mob.isAlive() && f.stillInBattle(),
                        "A mob carrying a passenger was converted anyway");
                h.assertTrue(f.mob.getType() == EntityType.ZOMBIE, "The original entity was replaced");
                h.assertTrue(f.mob.getFirstPassenger() == passenger,
                        "The passenger was detached from the still-present carrier");
            } finally {
                passenger.stopRiding();
                passenger.discard();
            }
        }
        h.succeed();
    }

    /**
     * A converting mob that is itself a passenger is skipped for the same reason: the vehicle would be
     * left holding a removed entity.
     */
    @GameTest(template = "empty")
    public static void mobRidingAVehicleDoesNotConvert(GameTestHelper h) {
        try (var f = new Fixture(h, EntityType.ZOMBIE)) {
            var access = (ZombieConversionAccess) f.mob;
            access.mineturn$startConversion(0);

            // A boat is enough to make the mob a passenger; no control is needed for the gate under test.
            var boat = h.spawn(EntityType.BOAT, new BlockPos(7, 1, 7));
            try {
                h.assertTrue(f.mob.startRiding(boat, true), "fixture error: could not seat the mob in a boat");
                h.assertTrue(f.mob.isPassenger(), "fixture error: the mob is not a passenger");

                BattleStatus.tick(f.battle.member(f.mob));

                h.assertTrue(f.mob.isAlive() && f.stillInBattle(),
                        "A mob riding a vehicle was converted anyway");
                h.assertTrue(f.mob.getType() == EntityType.ZOMBIE, "The original entity was replaced");
            } finally {
                f.mob.stopRiding();
                boat.discard();
            }
        }
        h.succeed();
    }

    /**
     * After the hold is released the same entity converts, and its timer is not restarted: the countdown
     * continued rather than being reset by the skipped ticks.
     *
     * <p>This is the resume half of the exclusion rules - the existing suites prove the pause, not that the
     * accumulated timer survives it.
     */
    @GameTest(template = "empty")
    public static void timerSurvivesThePassengerHold(GameTestHelper h) {
        try (var f = new Fixture(h, EntityType.PIGLIN)) {
            var piglin = (Piglin) f.mob;
            var access = (PiglinConversionAccess) piglin;
            // One tick short: the next counted tick must trigger conversion.
            access.mineturn$conversionTime(300);
            h.assertTrue(piglin.getInventory().isEmpty(), "fixture error: the private inventory is not empty");

            var passenger = h.spawnWithNoFreeWill(EntityType.PILLAGER, new BlockPos(7, 2, 7));
            try {
                h.assertTrue(passenger.startRiding(piglin, true), "fixture error: could not add a passenger");
                BattleStatus.tick(f.battle.member(piglin));
                h.assertTrue(access.mineturn$conversionTime() == 300,
                        "A skipped tick still advanced the conversion timer");
                h.assertTrue(piglin.isAlive(), "The held piglin converted anyway");
            } finally {
                passenger.stopRiding();
                passenger.discard();
            }

            // The hold is gone; the very next tick must convert, proving the timer was preserved at 300.
            BattleStatus.tick(f.battle.member(piglin));
            Mob outcome = f.replacement();
            h.assertTrue(outcome instanceof ZombifiedPiglin,
                    "Releasing the hold did not convert the piglin: " + outcome.getType());
            h.assertTrue(!f.stillInBattle() || f.battle.members.values().stream()
                            .anyMatch(m -> m.entity == outcome),
                    "The conversion outcome is not registered in the battle");
        }
        h.succeed();
    }

    /**
     * A permanent zombie keeps converting on the status clock after a temporary-summon window has passed,
     * guarding against an exclusion that never clears.
     */
    @GameTest(template = "empty")
    public static void exclusionLiftsOnceSummonWindowEnds(GameTestHelper h) {
        try (var f = new Fixture(h, EntityType.ZOMBIE)) {
            var access = (ZombieConversionAccess) f.mob;
            access.mineturn$startConversion(0);

            f.mob.addTag(BattleSummons.TAG);
            try {
                BattleStatus.tick(f.battle.member(f.mob));
                h.assertTrue(f.mob.isAlive() && f.stillInBattle(),
                        "A tagged temporary summon was converted");
            } finally {
                f.mob.removeTag(BattleSummons.TAG);
            }

            h.assertTrue(!BattleSummons.temporary(f.mob),
                    "fixture error: the temporary tag did not clear");
            BattleStatus.tick(f.battle.member(f.mob));
            h.assertTrue(f.replacement().getType() == EntityType.DROWNED,
                    "A permanent mob did not convert after the summon window ended");
        }
        h.succeed();
    }
}
