package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.network.TrailsNetwork;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("trails")
@PrefixGameTestTemplate(false)
public final class TrailsGameTests {
    static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final ServerPlayer p;
        final EmbeddedChannel channel;
        final List<LivingEntity> entities = new ArrayList<>();
        BattleSession b;
        Fixture(GameTestHelper h) {
            this.h = h;
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) h.setBlock(x, 0, z, Blocks.STONE);
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "at-" + UUID.randomUUID().toString().substring(0, 6)), false);
            p = new ServerPlayer(h.getLevel().getServer(), h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            p.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL);
            var pos = h.absolutePos(new BlockPos(5, 1, 5)); p.teleportTo(pos.getX() + .23, pos.getY(), pos.getZ() + .37);
            p.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
            ((com.matuvent.mineturn.mixin.StatusAccess) p).mineturn$equipment();
        }
        <T extends Mob> T mob(EntityType<T> type, int x, int z) {
            T m = h.spawnWithNoFreeWill(type, new BlockPos(x, 1, z));
            if (m instanceof net.minecraft.world.entity.monster.Zombie) m.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            entities.add(m); return m;
        }
        void start(boolean advantage) { b = new BattleSession(p, List.copyOf(entities), advantage); }
        TrailsNetwork.Command cmd(String op, int target, int slot) { return new TrailsNetwork.Command(b.id, b.revision, op, target, slot, 0, 0); }
        @Override public void close() {
            if (b != null) b.close("test cleanup");
            p.getServer().getPlayerList().remove(p); p.discard();
            for (var e : entities) e.discard();
            channel.finishAndReleaseAll();
        }
    }

    @GameTest(template = "empty")
    public static void circleKeepsPositionAndFortyAllies(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 8, 5);
            for (int i = 0; i < 40; i++) f.mob(EntityType.PIG, 5 + i % 5, 7 + i / 5);
            var start = f.p.position(); f.start(false);
            h.assertTrue(f.b.members.size() == 42, "A fixed 32-member cap lost allies");
            h.assertTrue(f.p.position().equals(start), "Entering AT moved the player");
            h.assertTrue(f.b.members.values().stream().filter(m -> f.b.trails.allied(m.entity)).count() == 41, "Passive animals were not allies");
            f.b.close("exit");
            h.assertTrue(f.entities.stream().noneMatch(BattleManager::locked) && !BattleManager.locked(f.p), "Exit stranded locks");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void normalEntryUsesSpeedAndDoesNotGivePlayerAnExtraTurn(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 8, 5);
            zombie.getAttribute(com.matuvent.mineturn.MineTurn.AGILITY).setBaseValue(200);
            f.start(false);
            h.assertTrue(f.b.actor == zombie, "Fastest enemy did not receive the normal initial turn");
            f.b.trails.finish(1);
            h.assertTrue(f.b.actor == f.p, "The slower player lost its partial initial progress");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ambushDamagesEnemiesAndAllAlliesGoFirst(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 8, 5);
            var pig = f.mob(EntityType.PIG, 6, 5); var sheep = f.mob(EntityType.SHEEP, 5, 6);
            float hp = zombie.getHealth(); f.start(true);
            h.assertTrue(Math.abs(zombie.getHealth() - (hp - zombie.getMaxHealth() * .25)) < .001, "Ambush health penalty");
            var seen = new HashSet<LivingEntity>(); seen.add(f.p);
            f.b.trails.finish(1);
            h.assertTrue(f.b.trails.allied(f.b.actor), "Enemy acted before allies"); seen.add(f.b.actor);
            f.b.trails.finish(1);
            h.assertTrue(f.b.trails.allied(f.b.actor), "Enemy acted before second ally"); seen.add(f.b.actor);
            h.assertTrue(seen.contains(pig) && seen.contains(sheep), "An ally's guaranteed first turn was skipped");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void gearAndPotionChangeSpeedWithoutResettingAt(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 8, 5); f.start(false);
            double old = f.b.trails.speed(f.p), remaining = f.b.clock.remaining(f.p);
            f.p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
            f.p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            f.p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200, 0));
            h.assertTrue(f.b.trails.speed(f.p) > old * 1.4, "Equipment/potion speed was ignored");
            h.assertTrue(f.b.clock.remaining(f.p) == remaining, "Buff reset action progress");
            h.assertTrue(f.b.clock.until(f.p, f.b::agility) < remaining / old, "Speed did not shorten next AT");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void exitRequiresAlliedTurnButBurstInterruptsEnemy(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 8, 5);
            zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200); zombie.setHealth(200);
            f.start(false); f.b.actor = zombie; f.b.trails.addCp(150);
            TrailsCombat.request(f.p, f.cmd("toggle", -1, 0));
            h.assertTrue(!f.b.closed, "Enemy turn allowed exit");
            var original = f.b.actor; long revision = f.b.revision;
            var request = f.cmd("burst", zombie.getId(), 0);
            TrailsCombat.request(f.p, request);
            h.assertTrue(zombie.getHealth() < 200 && f.b.actor == original, "S-Break did not interrupt while retaining enemy turn");
            h.assertTrue(f.b.trails.cp() == 50 && f.b.revision > revision, "Burst CP cost");
            TrailsCombat.request(f.p, request);
            h.assertTrue(f.b.trails.cp() == 50, "Replayed burst spent CP again");
            f.b.actor = f.p;
            TrailsCombat.request(f.p, f.cmd("toggle", -1, 0));
            h.assertTrue(f.b.closed && !BattleManager.locked(zombie), "Allied exit did not release encounter");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void foodRestoresHealthAndHungerOnce(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 8, 5); f.start(false);
            f.p.setHealth(8); f.p.getFoodData().setFoodLevel(10);
            f.p.getInventory().setItem(1, new ItemStack(Items.BREAD, 2));
            f.b.trails.item(1);
            h.assertTrue(f.p.getHealth() >= 12 && f.p.getFoodData().getFoodLevel() > 10, "Food did not restore both resources");
            h.assertTrue(f.p.getInventory().getItem(1).getCount() == 1, "Food consumed more than once");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void speedPotionKeepsBottleAndChangesAt(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 8, 5); f.start(false);
            var potion = new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
            f.p.getInventory().setItem(1, potion); double speed = f.b.trails.speed(f.p);
            f.b.trails.item(1);
            h.assertTrue(f.p.getInventory().getItem(1).is(Items.GLASS_BOTTLE), "Potion container was lost");
            h.assertTrue(f.b.trails.speed(f.p) > speed, "Swiftness did not affect AT speed");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void swordConeDoesNotHitAlliesOrEnemiesBehind(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var front = f.mob(EntityType.ZOMBIE, 7, 5);
            var behind = f.mob(EntityType.ZOMBIE, 3, 5); var pig = f.mob(EntityType.PIG, 6, 5);
            f.start(false); float hp = front.getHealth(), behindHp = behind.getHealth(), pigHp = pig.getHealth();
            f.b.trails.attack(front.getId(), 0, false);
            h.assertTrue(front.getHealth() < hp, "Sword missed cone target");
            h.assertTrue(behind.getHealth() == behindHp && pig.getHealth() == pigHp,
                    "Sword hit behind or friendly target: behind=" + behind.getHealth() + "/" + behindHp + ", ally=" + pig.getHealth() + "/" + pigHp);
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bowRequiresAndConsumesAmmunition(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 12, 5); f.start(false);
            f.p.getInventory().setItem(1, new ItemStack(Items.BOW)); float hp = zombie.getHealth();
            TrailsCombat.request(f.p, f.cmd("attack", zombie.getId(), 1));
            h.assertTrue(zombie.getHealth() == hp && f.b.actor == f.p, "Missing ammo still attacked/spent turn");
            f.p.getInventory().setItem(2, new ItemStack(Items.ARROW, 3));
            f.b.trails.attack(zombie.getId(), 1, false);
            h.assertTrue(zombie.getHealth() < hp && f.p.getInventory().getItem(2).getCount() == 2, "Ranged attack/ammo consumption");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void windMaceHasAreaDamageCostAndLongRecovery(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var a = f.mob(EntityType.ZOMBIE, 7, 5); var c = f.mob(EntityType.ZOMBIE, 9, 5);
            f.p.getInventory().setItem(1, new ItemStack(Items.MACE)); f.p.getInventory().setItem(2, new ItemStack(Items.WIND_CHARGE, 2));
            f.start(false); float hp = c.getHealth(); f.b.trails.attack(a.getId(), 1, true);
            h.assertTrue(c.getHealth() < hp && f.p.getInventory().getItem(2).getCount() == 1, "Wind combo area/resource");
            h.assertTrue(f.b.clock.until(f.p, f.b::agility) > 100, "Heavy combo did not lengthen recovery");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void pigRegeneratesAcrossOwnTurnsAndSheepHealsArea(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 9, 5); var pig = f.mob(EntityType.PIG, 6, 5); var sheep = f.mob(EntityType.SHEEP, 5, 6);
            f.start(false); pig.setHealth(2); f.p.setHealth(8);
            f.b.actor = pig; f.b.trails.companionSkill(-1); float hp = pig.getHealth();
            f.b.actor = pig; f.b.trails.onTurn();
            h.assertTrue(pig.getHealth() == hp + 2, "Pig did not regenerate on later own turn");
            f.b.actor = sheep; f.b.trails.companionSkill(-1);
            h.assertTrue(f.p.getHealth() >= 12, "Sheep did not heal nearby party");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void companionsCannotUseInventoryAndCanExitOnTheirTurn(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 9, 5); var pig = f.mob(EntityType.PIG, 6, 5); f.start(false); f.b.actor = pig;
            f.p.getInventory().setItem(1, new ItemStack(Items.BREAD, 2));
            TrailsCombat.request(f.p, f.cmd("item", -1, 1));
            h.assertTrue(f.p.getInventory().getItem(1).getCount() == 2, "Ally consumed player inventory");
            TrailsCombat.request(f.p, f.cmd("toggle", -1, 0));
            h.assertTrue(f.b.closed, "Own side includes allied creature turns");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rearHitStunsWithoutAutomaticAdmission(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 7, 7);
            zombie.setYRot(0); zombie.yHeadRot = 0;
            f.p.teleportTo(zombie.getX(), zombie.getY(), zombie.getZ() - 1.5);
            zombie.hurt(f.p.damageSources().playerAttack(f.p), 1);
            h.assertTrue(TrailsCombat.stunned(zombie), "A rear melee hit did not create the ambush window");
            h.assertTrue(!BattleManager.locked(f.p) && !BattleManager.locked(zombie), "A real-time hit auto-started AT");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void rearArrowAlsoCreatesAmbushOpportunity(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 7, 7);
            zombie.setYRot(0); zombie.yHeadRot = 0;
            f.p.teleportTo(zombie.getX(), zombie.getY(), zombie.getZ() - 4);
            var arrow = EntityType.ARROW.create(h.getLevel()); arrow.setOwner(f.p);
            try {
                zombie.hurt(f.p.damageSources().arrow(arrow, f.p), 1);
                h.assertTrue(TrailsCombat.stunned(zombie), "A player's rear ranged hit did not create an ambush opportunity");
            } finally { arrow.discard(); }
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void bossTelegraphsBeforeUltimate(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var boss = f.mob(EntityType.RAVAGER, 8, 5); f.start(false);
            f.b.actor = boss; f.b.trails.turns.put(boss.getUUID(), 3);
            float hp = f.p.getHealth(); f.b.trails.enemyAction();
            h.assertTrue(f.b.trails.charging.contains(boss.getUUID()) && f.p.getHealth() == hp, "Boss did not telegraph before damage");
            f.b.actor = boss; f.b.trails.enemyAction();
            h.assertTrue(!f.b.trails.charging.contains(boss.getUUID()), "Boss did not resolve charged ultimate");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void freeMovementUsesCircleAndNeverSpendsTravelDistance(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 13, 13); f.start(true);
            var rules = f.b.trails; var origin = f.p.position(); double time = f.b.clock.time();
            double budget = f.b.budget.remaining();
            for (int i = 0; i < 80; i++) rules.move(1, 1);
            h.assertTrue(Math.abs(f.p.position().subtract(origin).horizontalDistance() - rules.movementRadius) < .02, "Diagonal movement did not reach the Euclidean circle edge");
            for (int i = 0; i < 20; i++) rules.move(-1, -1);
            h.assertTrue(f.p.position().distanceTo(origin) < .4, "Returning inside the circle was blocked by accumulated travel");
            h.assertTrue(rules.movementOrigin.equals(origin) && f.b.budget.remaining() == budget && f.b.clock.time() == time,
                    "Moving shifted the circle, spent travel budget or advanced AT");
            var nextOrigin = f.p.position(); rules.onTurn();
            h.assertTrue(rules.movementOrigin.equals(nextOrigin), "New turn did not reset its circle center");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void movementInputStopsExpiresAndRejectsOldTurns(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 13, 13); f.start(true);
            var rules = f.b.trails; var origin = f.p.position();
            for (int i = 0; i < 50; i++) TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, rules.turn, i, 1, 0));
            rules.tick();
            h.assertTrue(f.p.position().distanceTo(origin) <= .216, "Packet spam moved more than once per server tick");
            var stopped = f.p.position();
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, rules.turn, 51, 0, 0));
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, rules.turn, 50, 1, 0));
            for (int i = 0; i < 10; i++) rules.tick();
            h.assertTrue(f.p.position().equals(stopped), "Release or sequence protection failed");
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, rules.turn, 52, 1, 0));
            for (int i = 0; i < 8; i++) rules.tick();
            stopped = f.p.position(); rules.tick();
            h.assertTrue(f.p.position().equals(stopped), "Input kept moving after heartbeat expired");
            long oldTurn = rules.turn; rules.onTurn();
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, oldTurn, 999, 1, 0));
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, rules.turn, 1000, Double.NaN, 0));
            rules.tick();
            h.assertTrue(f.p.position().equals(stopped), "Stale turn / nonfinite direction was accepted");
            f.b.actor = zombie; rules.onTurn();
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, rules.turn, 1, 1, 0));
            rules.tick();
            h.assertTrue(f.p.position().equals(stopped) && rules.inputTicks == 0, "Enemy turn accepted player movement");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void circleMovementCannotCrossTallWallAndControlsAllies(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 13, 13); var pig = f.mob(EntityType.PIG, 3, 3); f.start(true);
            for (int z = 0; z < 16; z++) for (int y = 1; y < 4; y++) h.setBlock(7, y, z, Blocks.STONE);
            for (int i = 0; i < 30; i++) f.b.trails.move(1, 0);
            double wall = h.absolutePos(new BlockPos(7, 1, 5)).getX();
            h.assertTrue(f.p.getBoundingBox().maxX <= wall + 1e-6, "WASD crossed a tall solid wall");
            f.b.actor = pig; f.b.trails.onTurn(); var start = pig.position();
            TrailsCombat.input(f.p, new TrailsNetwork.MoveInput(f.b.id, f.b.trails.turn, 1, 0, 1));
            f.b.trails.tick();
            h.assertTrue(pig.getZ() > start.z, "WASD did not control the active allied creature");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void ownTurnWeaponSwitchUpdatesPrimaryWithoutSpendingAction(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 13, 13); f.start(true);
            f.p.getInventory().setItem(1, new ItemStack(Items.CROSSBOW));
            double time = f.b.clock.time(); int cp = f.b.trails.cp();
            TrailsCombat.request(f.p, f.cmd("select", -1, 1));
            h.assertTrue(f.p.getMainHandItem().is(Items.CROSSBOW) && f.b.trails.primary().equals("minecraft:crossbow"), "Selected slot did not become primary weapon");
            h.assertTrue(f.b.actor == f.p && f.b.clock.time() == time && f.b.trails.cp() == cp, "Switching weapons consumed a turn or CP");
            f.b.actor = zombie;
            TrailsCombat.request(f.p, f.cmd("select", -1, 0));
            h.assertTrue(f.p.getInventory().selected == 1, "Weapon switching was allowed during an enemy turn");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void vanillaInventoryPacketsWorkOnlyOnPlayersTurn(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var zombie = f.mob(EntityType.ZOMBIE, 13, 13); f.start(true);
            f.p.getInventory().setItem(1, new ItemStack(Items.BOW));
            f.p.getInventory().setItem(9, new ItemStack(Items.CROSSBOW));
            f.p.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1));
            h.assertTrue(f.p.getMainHandItem().is(Items.BOW), "Vanilla hotbar packet was blocked on player turn");
            var menu = f.p.inventoryMenu;
            var swap = new net.minecraft.network.protocol.game.ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), 9, 1,
                    net.minecraft.world.inventory.ClickType.SWAP, ItemStack.EMPTY, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>());
            f.p.connection.handleContainerClick(swap);
            h.assertTrue(f.p.getMainHandItem().is(Items.CROSSBOW) && f.b.trails.primary().equals("minecraft:crossbow"), "Inventory swap did not update the actual held / primary weapon");
            f.b.actor = zombie;
            f.p.connection.handleSetCarriedItem(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
            f.p.connection.handleContainerClick(swap);
            h.assertTrue(f.p.getInventory().selected == 1 && f.p.getMainHandItem().is(Items.CROSSBOW), "Enemy turn accepted vanilla inventory changes");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void cameraSectorIgnoresSelectedEnemyAndKeepsFriendlySafe(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var forward = f.mob(EntityType.ZOMBIE, 5, 7); var selectedBehind = f.mob(EntityType.ZOMBIE, 5, 3); var pig = f.mob(EntityType.PIG, 6, 7);
            f.start(true); float a = forward.getHealth(), c = selectedBehind.getHealth(), friend = pig.getHealth();
            TrailsCombat.request(f.p, new TrailsNetwork.Command(f.b.id, f.b.revision, "attack", selectedBehind.getId(), 0, 0, 1));
            h.assertTrue(forward.getHealth() < a && selectedBehind.getHealth() == c && pig.getHealth() == friend,
                    "Camera sector locked to the selected enemy or hit friendly units");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void crossbowRectangleHitsMultipleWithoutTargetLock(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var a = f.mob(EntityType.ZOMBIE, 5, 9); var c = f.mob(EntityType.ZOMBIE, 6, 12); var outside = f.mob(EntityType.ZOMBIE, 9, 9);
            f.p.getInventory().setItem(1, new ItemStack(Items.CROSSBOW)); f.p.getInventory().setItem(2, new ItemStack(Items.ARROW, 3));
            f.start(true); float ah = a.getHealth(), ch = c.getHealth(), oh = outside.getHealth();
            TrailsCombat.request(f.p, new TrailsNetwork.Command(f.b.id, f.b.revision, "attack", outside.getId(), 1, 0, 1));
            h.assertTrue(a.getHealth() < ah && c.getHealth() < ch && outside.getHealth() == oh, "Crossbow did not use the displayed rectangular strip");
            h.assertTrue(f.p.getInventory().getItem(2).getCount() == 2, "Rectangle consumed ammo per target instead of per shot");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidCastDoesNotSpendItemsOrTurn(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var enemy = f.mob(EntityType.ZOMBIE, 7, 5); f.p.getInventory().setItem(1, new ItemStack(Items.MACE));
            f.p.getInventory().setItem(2, new ItemStack(Items.WIND_CHARGE, 3)); f.start(true); double time = f.b.clock.time();
            TrailsCombat.request(f.p, new TrailsNetwork.Command(f.b.id, f.b.revision, "wind", enemy.getId(), 1, 40, 0));
            h.assertTrue(f.b.actor == f.p && f.b.clock.time() == time && f.p.getInventory().getItem(2).getCount() == 3,
                    "Out-of-cast-range aim spent a turn or wind charge");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void chickenReadiesOnlyNearbyAlliesAheadOfTiedEnemy(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            var enemy = f.mob(EntityType.ZOMBIE, 9, 5); var pig = f.mob(EntityType.PIG, 6, 5);
            var chicken = f.mob(EntityType.CHICKEN, 6, 6); var far = f.mob(EntityType.PIG, 14, 14); f.start(true);
            f.b.actor = chicken; f.b.trails.onTurn(); f.b.clock.recovery(enemy, 0); f.b.clock.recovery(far, 3456); f.b.clock.recovery(pig, 4567);
            double time = f.b.clock.time(); f.b.trails.companionSkill(-1);
            h.assertTrue(f.b.actor == f.p && f.b.clock.remaining(pig) == 0 && f.b.clock.remaining(chicken) == 0, "Chicken did not ready the entire nearby allied group including itself");
            h.assertTrue(f.b.clock.remaining(far) == 3456 && f.b.clock.remaining(enemy) == 0 && f.b.clock.time() == time, "Chicken changed out-of-area/enemy progress or advanced AT");
            f.b.trails.finish(1); h.assertTrue(f.b.trails.allied(f.b.actor), "Tied enemy cut ahead of readied allies");
            f.b.trails.finish(1); h.assertTrue(f.b.trails.allied(f.b.actor), "A second readied ally lost its priority");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void repeatedCompanionBuffAndPotionStackVanillaLevels(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 12, 12); var wolf = f.mob(EntityType.WOLF, 5, 6); f.start(true);
            f.b.actor = wolf; f.b.trails.companionSkill(-1); f.b.actor = wolf; f.b.trails.companionSkill(-1);
            h.assertTrue(f.p.getEffect(MobEffects.DAMAGE_BOOST).getAmplifier() == 1, "Repeated wolf warcry did not stack Strength levels");
            TrailsCombat.effect(f.p, MobEffects.MOVEMENT_SPEED, 240, 0);
            var potion = new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
            f.p.getInventory().setItem(1, potion); f.b.actor = f.p; f.b.trails.item(1);
            h.assertTrue(f.p.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 1 && f.p.hasEffect(MobEffects.DAMAGE_BOOST), "Potion did not stack Speed while preserving Strength");
            h.assertTrue(f.p.getInventory().getItem(1).is(Items.GLASS_BOTTLE), "Stacking duplicated/lost potion container");
        }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void settingsPersistRangesWithoutReplacingOtherFields(GameTestHelper h) throws Exception {
        var path = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("mineturn-trails.json");
        byte[] saved = java.nio.file.Files.readAllBytes(path);
        var gson = new com.google.gson.Gson(); var previous = gson.fromJson(gson.toJson(com.matuvent.mineturn.TrailsConfig.current), com.matuvent.mineturn.TrailsConfig.class);
        try {
            var json = com.google.gson.JsonParser.parseString(new String(saved, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            json.addProperty("qaPreserveUnknown", 731); java.nio.file.Files.writeString(path, gson.toJson(json));
            com.matuvent.mineturn.TrailsConfig.saveRanges(14, 7, 5);
            var result = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            h.assertTrue(result.get("qaPreserveUnknown").getAsInt() == 731 && result.get("encounterRadius").getAsDouble() == 14,
                    "Settings discarded unrelated/unknown config fields or failed to persist range");
            try { com.matuvent.mineturn.TrailsConfig.saveRanges(Double.NaN, 7, 5); throw new IllegalStateException("Nonfinite setting was accepted"); }
            catch (IllegalArgumentException expected) { }
        } finally { java.nio.file.Files.write(path, saved); com.matuvent.mineturn.TrailsConfig.current = previous; }
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void waitingDoesNotAdvanceAtOrRunAlliedAi(GameTestHelper h) {
        try (var f = new Fixture(h)) {
            f.mob(EntityType.ZOMBIE, 8, 5); var pig = f.mob(EntityType.PIG, 6, 5); f.start(false);
            f.b.actor = pig; double time = f.b.clock.time();
            for (int i = 0; i < 650; i++) f.b.trails.tick();
            h.assertTrue(f.b.actor == pig && f.b.clock.time() == time, "Waiting auto-played an allied turn or ran old timeout");
        }
        h.succeed();
    }
}
