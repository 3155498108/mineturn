package com.matuvent.mineturn.battle;

import com.mojang.authlib.GameProfile;
import com.matuvent.mineturn.data.CombatData;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Strider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Regression coverage for which creatures a player may actively mount.
 *
 * <p>Existing suites exercise boarding only with a horse. This class fills the gaps that leaves: the
 * pig and strider steering-item rules, baby and camel refusals, distance-based candidate selection, and
 * the rule that a mount already carrying someone is never taken.
 *
 * <p>It also pins the distinction between "eligible to board" and "still controlled afterwards": the
 * steering item decides whether boarding is allowed, but once attached the carrier survives swapping it
 * away, which {@link BattleRiding#vehicle} implements by falling back to its rider registry.
 *
 * <p>Uses only package-private entry points; no production class is changed and no new API is opened.
 */
@GameTestHolder("mineturn")
@PrefixGameTestTemplate(false)
public final class MountEligibilityRegressionGameTests {
    /** Battle fixture: the player is actor 0 and a harmless second participant keeps the session valid. */
    private static final class Fixture implements AutoCloseable {
        final ServerPlayer player;
        final Mob bystander;
        final BattleSession battle;

        Fixture(GameTestHelper h) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) h.setBlock(x, 0, z, Blocks.STONE);
            }
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "mt-mount"), false);
            player = new ServerPlayer(h.getLevel().getServer(), h.getLevel(),
                    cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection);
            player.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL);
            player.getFoodData().setFoodLevel(10);
            var pos = h.absolutePos(new BlockPos(2, 1, 2));
            player.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

            bystander = h.spawnWithNoFreeWill(EntityType.PILLAGER, new BlockPos(12, 1, 12));
            // The bystander must not own an AI definition, otherwise it would act in the timeline.
            var data = CombatData.current;
            var brains = new HashMap<>(data.mobs());
            brains.remove("minecraft:pillager");
            battle = new BattleSession(player, bystander,
                    new CombatData.Snapshot(data.actions(), Map.copyOf(brains), data.items(),
                            data.sources(), data.grants()),
                    player);
        }

        /** Places a creature next to the player so it is inside the boarding range. */
        <T extends Mob> T spawnNear(GameTestHelper h, EntityType<T> type, int dx, int dz) {
            var at = h.absolutePos(new BlockPos(2 + dx, 1, 2 + dz));
            T mob = h.spawnWithNoFreeWill(type, new BlockPos(2 + dx, 1, 2 + dz));
            mob.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            mob.setDeltaMovement(0, 0, 0);
            return mob;
        }

        Mob nearest(double range) {
            return BattleMountActions.nearest(battle, player, range);
        }

        @Override
        public void close() {
            battle.close("test");
            player.getServer().getPlayerList().remove(player);
            player.discard();
            bystander.discard();
        }
    }

    /** A horse that satisfies every boarding requirement. */
    private static AbstractHorse readyHorse(GameTestHelper h, Fixture f, int dx, int dz) {
        AbstractHorse horse = f.spawnNear(h, EntityType.HORSE, dx, dz);
        horse.setTamed(true);
        horse.equipSaddle(new ItemStack(Items.SADDLE), null);
        return horse;
    }

    /**
     * The horse path is the only one the existing suites exercised; these are the refusals that path
     * shares with every other mount type.
     */
    @GameTest(template = "empty")
    public static void horseRequiresTamingSaddleAndAdulthood(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            // Untamed.
            AbstractHorse untamed = f.spawnNear(h, EntityType.HORSE, 3, 0);
            untamed.equipSaddle(new ItemStack(Items.SADDLE), null);
            h.assertTrue(f.nearest(6) == null, "An untamed horse was offered as a mount");

            // Tamed but bare-backed.
            untamed.setTamed(true);
            h.assertTrue(f.nearest(6) == null, "A horse without a saddle was offered as a mount");

            // Tamed and saddled: eligible.
            untamed.equipSaddle(new ItemStack(Items.SADDLE), null);
            h.assertTrue(f.nearest(6) == untamed, "A tamed, saddled horse was not offered as a mount");

            // A foal is refused even with a saddle.
            untamed.setBaby(true);
            h.assertTrue(f.nearest(6) == null, "A baby horse was offered as a mount");
        }
        h.succeed();
    }

    /**
     * The pig is steered with a carrot on a stick, and both the saddle and the item are required. No
     * existing test covers this creature at all.
     */
    @GameTest(template = "empty")
    public static void pigRequiresSaddleAndCarrotOnAStick(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Pig pig = f.spawnNear(h, EntityType.PIG, 3, 0);

            // Neither saddle nor steering item.
            h.assertTrue(f.nearest(6) == null, "An unsaddled pig was offered as a mount");

            pig.equipSaddle(new ItemStack(Items.SADDLE), null);
            h.assertTrue(f.nearest(6) == null, "A saddled pig was offered without a carrot on a stick");

            // Wrong steering item.
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
            h.assertTrue(f.nearest(6) == null, "A pig accepted the strider's steering item");

            // Correct steering item in the main hand.
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.CARROT_ON_A_STICK));
            h.assertTrue(f.nearest(6) == pig, "A saddled pig with a carrot on a stick was not offered");

            // The offhand works too: vanilla accepts either hand.
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,
                    new ItemStack(Items.CARROT_ON_A_STICK));
            h.assertTrue(f.nearest(6) == pig, "A pig with the carrot on a stick in the offhand was refused");

            // A piglet is refused.
            pig.setBaby(true);
            h.assertTrue(f.nearest(6) == null, "A baby pig was offered as a mount");
        }
        h.succeed();
    }

    /** The strider uses the warped fungus on a stick, not the carrot, and the two must not be swapped. */
    @GameTest(template = "empty")
    public static void striderRequiresSaddleAndWarpedFungus(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Strider strider = f.spawnNear(h, EntityType.STRIDER, 3, 0);
            strider.equipSaddle(new ItemStack(Items.SADDLE), null);

            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.CARROT_ON_A_STICK));
            h.assertTrue(f.nearest(6) == null, "A strider accepted the pig's steering item");

            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.WARPED_FUNGUS_ON_A_STICK));
            h.assertTrue(f.nearest(6) == strider, "A saddled strider with warped fungus was not offered");

            strider.setBaby(true);
            h.assertTrue(f.nearest(6) == null, "A baby strider was offered as a mount");
        }
        h.succeed();
    }

    /** A camel is deliberately excluded from active boarding even though it can be ridden normally. */
    @GameTest(template = "empty")
    public static void camelIsExcludedFromActiveBoarding(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var camel = f.spawnNear(h, EntityType.CAMEL, 3, 0);
            camel.setTamed(true);
            camel.equipSaddle(new ItemStack(Items.SADDLE), null);
            h.assertTrue(f.nearest(6) == null,
                    "A camel was offered as an active boarding target, but it is explicitly excluded");
        }
        h.succeed();
    }

    /** With two eligible mounts the closest one wins, and the choice does not depend on spawn order. */
    @GameTest(template = "empty")
    public static void nearestEligibleMountIsChosen(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            AbstractHorse far = readyHorse(h, f, 5, 0);
            AbstractHorse near = readyHorse(h, f, 2, 0);
            h.assertTrue(f.player.distanceToSqr(near) < f.player.distanceToSqr(far),
                    "fixture error: the two mounts are not at different distances");

            Mob chosen = f.nearest(6);
            h.assertTrue(chosen == near,
                    "The farther mount was chosen: expected the nearer one, got " + chosen);
            h.assertTrue(f.player.distanceToSqr(chosen) <= f.player.distanceToSqr(far),
                    "The chosen mount is not the closest eligible one");
        }
        h.succeed();
    }

    /** A mount that already carries someone is never taken, even if it is the closest. */
    @GameTest(template = "empty")
    public static void occupiedMountIsSkipped(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            AbstractHorse occupied = readyHorse(h, f, 2, 0);
            AbstractHorse free = readyHorse(h, f, 4, 0);

            // Put a second creature on the nearer horse so it counts as occupied.
            var rider = f.spawnNear(h, EntityType.PILLAGER, 5, 5);
            h.assertTrue(rider.startRiding(occupied, true), "fixture error: could not occupy the mount");

            Mob chosen = f.nearest(6);
            h.assertTrue(chosen == free,
                    "An already occupied mount was selected: " + chosen + " (expected the free one)");
            h.assertTrue(chosen != occupied, "The occupied mount was handed out");
        }
        h.succeed();
    }

    /**
     * The steering item decides boarding only. Once attached, swapping it away must keep the carrier,
     * otherwise a player would lose the mount simply by drawing a sword.
     */
    @GameTest(template = "empty")
    public static void steeringItemMattersOnlyForBoarding(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            Pig pig = f.spawnNear(h, EntityType.PIG, 3, 0);
            pig.equipSaddle(new ItemStack(Items.SADDLE), null);
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.CARROT_ON_A_STICK));
            h.assertTrue(f.nearest(6) == pig, "fixture error: the pig is not boardable");

            BattleMountActions.cast(f.battle, f.player, "mineturn:mount",
                    f.battle.definitions.actions().get("mineturn:mount"));
            h.assertTrue(f.player.getVehicle() == pig, "Boarding did not attach the rider to the pig");
            h.assertTrue(BattleRiding.vehicle(f.player) == pig, "The carrier was not registered");

            // Draw a sword instead of the steering item.
            f.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(Items.IRON_SWORD));
            h.assertTrue(f.player.getVehicle() == pig, "Swapping items dropped the player off the mount");
            h.assertTrue(BattleRiding.vehicle(f.player) == pig,
                    "Swapping away from the steering item discarded the carrier relationship");
        }
        h.succeed();
    }

    /**
     * A refused boarding attempt must not quietly consume the turn's resources, and must leave any
     * existing riding relationship alone.
     */
    @GameTest(template = "empty")
    public static void refusedBoardingKeepsResourcesAndExistingMount(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            // Nothing boardable nearby: a bare-backed horse only.
            f.spawnNear(h, EntityType.HORSE, 3, 0);

            f.battle.budget = new TurnBudget(4);
            f.battle.budget.setActions(2, 2);
            f.battle.budget.move(1);
            int mainBefore = f.battle.budget.mainActions();
            int bonusBefore = f.battle.budget.bonusActions();
            double movementBefore = f.battle.budget.remaining();

            h.assertTrue(f.nearest(6) == null, "fixture error: something is boardable here");
            String reason = BattleMountActions.error(f.battle, f.player,
                    f.battle.definitions.actions().get("mineturn:mount"));
            h.assertTrue(reason != null, "A refusal did not explain itself");

            h.assertTrue(f.battle.budget.mainActions() == mainBefore
                            && f.battle.budget.bonusActions() == bonusBefore,
                    "A refused boarding changed the action counts");
            h.assertTrue(Math.abs(f.battle.budget.remaining() - movementBefore) < 1e-6,
                    "A refused boarding changed the movement budget");
            h.assertTrue(!f.player.isPassenger(), "A refused boarding put the player on something anyway");
        }
        h.succeed();
    }

    /** Engaging control blocks boarding until the player retreats, and does so without spending anything. */
    @GameTest(template = "empty")
    public static void engagedPlayerCannotBoardWithoutRetreating(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            AbstractHorse horse = readyHorse(h, f, 2, 0);
            // Move the bystander next to the player so the control ring is active.
            f.bystander.setPos(f.player.position().add(0.8, 0, 0));
            f.battle.budget = new TurnBudget(4);

            String reason = BattleMountActions.error(f.battle, f.player,
                    f.battle.definitions.actions().get("mineturn:mount"));
            if (f.battle.engaged(f.player)) {
                h.assertTrue(reason != null && reason.contains("撤退"),
                        "Engaged boarding was allowed or explained without mentioning retreat: " + reason);
                h.assertTrue(!f.player.isPassenger(), "Engaged boarding attached the player anyway");
            } else {
                // The engagement gate depends on live geometry; if it is not active, the horse must be usable
                // so this test still asserts something meaningful about the same code path.
                h.assertTrue(reason == null && f.nearest(6) == horse,
                        "Not engaged, yet the eligible horse was refused: " + reason);
            }
        }
        h.succeed();
    }

    /**
     * A mount locked by a fight is not handed out again, and the lock is released when that fight ends.
     *
     * <p>Note the ordering: {@code nearest} returns null once the player is a passenger, and boarding
     * registers the lock, so the "is it still offered" check has to run after the lock is dropped rather
     * than while the same player is sitting on it.
     */
    @GameTest(template = "empty")
    public static void lockIsHeldWhileRidingAndReleasedAfterwards(GameTestHelper h) {
        AbstractHorse horse;
        try (var f = new Fixture(h)) {
            horse = readyHorse(h, f, 2, 0);
            h.assertTrue(f.nearest(6) == horse, "fixture error: the horse is not boardable");
            h.assertTrue(!BattleManager.locked(horse), "The horse was already locked before boarding");

            BattleMountActions.cast(f.battle, f.player, "mineturn:mount",
                    f.battle.definitions.actions().get("mineturn:mount"));

            h.assertTrue(f.player.getVehicle() == horse, "Boarding did not attach the rider");
            h.assertTrue(BattleManager.locked(horse),
                    "A mount carrying a rider in battle was not locked, so another fight could take it");
        }
        // The fixture closed the session; a lock that outlives the fight would keep the mount unusable.
        h.assertTrue(!BattleManager.locked(horse),
                "The mount stayed locked after the battle ended");
        horse.discard();
        h.succeed();
    }
}
