package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.client.EncounterSwitchGesture;
import com.matuvent.mineturn.platform.RuntimePlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("trails") @PrefixGameTestTemplate(false)
public final class TrailsCompanionGameTests {
    public TrailsCompanionGameTests() {}
    private static ItemStack tag() { var s = new ItemStack(Items.NAME_TAG); s.set(DataComponents.CUSTOM_NAME, Component.literal("队员小猪")); return s; }
    private static ItemStack store(TrailsGameTests.Fixture f, Mob mob) {
        f.p.getInventory().selected = 1; f.p.setItemInHand(InteractionHand.MAIN_HAND, tag());
        Items.NAME_TAG.interactLivingEntity(f.p.getMainHandItem(), f.p, mob, InteractionHand.MAIN_HAND);
        return f.p.getMainHandItem();
    }
    private static Mob release(TrailsGameTests.Fixture f, Vec3 point) {
        var mob = TrailsCompanions.release(f.p, InteractionHand.MAIN_HAND, point);
        if (mob != null) f.entities.add(mob);
        return mob;
    }
    @GameTest(template="empty")
    public static void nameTagStoresAndItemReleasesSameEntityWithHealthEquipmentAndBuffs(GameTestHelper h) {
        try (var f = new TrailsGameTests.Fixture(h)) {
            var pig = f.mob(EntityType.PIG, 7, 5); pig.setHealth(5); pig.setBaby(true);
            pig.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
            pig.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2));
            var token = store(f, pig); var ticket = TrailsCompanions.ticket(token);
            h.assertTrue(pig.isRemoved() && ticket != null && token.getMaxStackSize() == 1, "Tag did not produce one stored companion");
            var block = BlockPos.containing(pig.position()).below();
            var result = token.getItem().useOn(new UseOnContext(f.p, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(block), Direction.UP, block, false)));
            var restored = (Mob)h.getLevel().getEntity(pig.getUUID()); if (restored != null) f.entities.add(restored);
            h.assertTrue(result.consumesAction() && restored != null && restored.getHealth() == 5 && ((AgeableMob)restored).isBaby()
                    && restored.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                    && restored.getEffect(MobEffects.MOVEMENT_SPEED).getAmplifier() == 2
                    && restored.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() == 400, "Release changed identity or saved entity state");
            h.assertTrue(f.p.getMainHandItem().isEmpty() && f.p.getUUID().equals(TrailsParties.owner(restored))
                    && RuntimePlatform.data(restored).getBoolean(TrailsCompanions.FOLLOW) && CompanionStorage.get(f.p.server).find(ticket) == null,
                    "Release did not consume the ticket or enable owner following");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void fullInventoryRejectsBeforeNamingOrSpendingButLastTagCanFreeItsOwnSlot(GameTestHelper h) {
        try (var f = new TrailsGameTests.Fixture(h)) {
            for (int i=0;i<36;i++) f.p.getInventory().setItem(i,new ItemStack(Items.STONE,64));
            var pig=f.mob(EntityType.PIG,7,5); var held=tag(); held.setCount(2); f.p.setItemInHand(InteractionHand.MAIN_HAND,held);
            Items.NAME_TAG.interactLivingEntity(held,f.p,pig,InteractionHand.MAIN_HAND);
            h.assertTrue(held.getCount()==2 && !pig.isRemoved() && !pig.hasCustomName() && TrailsParties.owner(pig)==null,"Full inventory spent tag, lost mob or registered it");
            held.setCount(1); Items.NAME_TAG.interactLivingEntity(held,f.p,pig,InteractionHand.MAIN_HAND);
            h.assertTrue(pig.isRemoved() && TrailsCompanions.ticket(f.p.getMainHandItem())!=null,"Final tag did not reuse its freed slot");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void foreignOwnerAndBlockedPlacementDoNotSpendOrLoseStoredCompanion(GameTestHelper h) {
        try (var f=new TrailsGameTests.Fixture(h);var other=new TrailsGameTests.Fixture(h)) {
            TrailsTeams.invite(f.p,other.p);TrailsTeams.accept(other.p);
            var pig=f.mob(EntityType.PIG,7,5);var token=store(f,pig);var ticket=TrailsCompanions.ticket(token);
            other.p.setItemInHand(InteractionHand.MAIN_HAND,token.copy());
            h.assertTrue(TrailsCompanions.release(other.p,InteractionHand.MAIN_HAND,pig.position())==null,"Other player released stolen companion");
            var pos=BlockPos.containing(pig.position());h.getLevel().setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
            h.assertTrue(release(f,pig.position())==null && f.p.getMainHandItem()==token && CompanionStorage.get(f.p.server).find(ticket)!=null,"Blocked placement lost ticket or entity");
            h.getLevel().setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
            h.assertTrue(release(f,pig.position())!=null,"Failed release could not be retried");
            TrailsTeams.leave(other.p);TrailsTeams.leave(f.p);
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void copiedItemCannotDuplicateEntityAndForgedTicketCannotLoadEntityData(GameTestHelper h) {
        try (var f=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);var copy=store(f,pig).copy();
            var restored=release(f,pig.position());h.assertTrue(restored!=null,"First release failed");
            f.p.setItemInHand(InteractionHand.MAIN_HAND,copy);
            h.assertTrue(release(f,pig.position().add(1,0,0))==null && !restored.isRemoved(),"Copied receipt duplicated or removed existing mob");
            var forged=new ItemStack(MineTurn.COMPANION_TOKEN.get());f.p.setItemInHand(InteractionHand.MAIN_HAND,forged);
            h.assertTrue(release(f,pig.position())==null,"Unissued item created a mob");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void storageSaveReloadRetainsPrivateReceiptAndFullEntitySnapshot(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);var token=store(f,pig);var ticket=TrailsCompanions.ticket(token);
            var storage=CompanionStorage.get(f.p.server);var snapshot=storage.save(new CompoundTag(),h.getLevel().registryAccess());
            var loaded=CompanionStorage.load(snapshot,h.getLevel().registryAccess());
            h.assertTrue(loaded.find(ticket)!=null && loaded.find(ticket).owner().equals(f.p.getUUID())
                    && loaded.find(ticket).entity().equals(storage.find(ticket).entity()),"World save lost ownership or snapshot");
            h.assertTrue(!token.getOrDefault(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains("entity"),"Entity snapshot leaked into editable item NBT");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void sneakEmptyHandRecallsOnlyOwnCompanionAndCombatBlocksStorage(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h);var other=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);store(f,pig);var restored=release(f,pig.position());
            other.p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);other.p.setShiftKeyDown(true);
            restored.interact(other.p,InteractionHand.MAIN_HAND);
            h.assertTrue(!restored.isRemoved(),"Teammate or stranger recalled someone else's mob");
            f.mob(EntityType.ZOMBIE,9,5);f.start(false);f.p.setShiftKeyDown(true);
            h.assertTrue(!TrailsCompanions.capture(f.p,restored) && !restored.isRemoved(),"Recalled a locked combat member");
            f.b.close("test");restored.interact(f.p,InteractionHand.MAIN_HAND);
            h.assertTrue(restored.isRemoved() && TrailsCompanions.ticket(f.p.getMainHandItem())!=null,"Sneak interaction failed to recall");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void passengerAndLeashAndHostileNamingCannotCaptureOrLoseAttachments(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);var chicken=f.mob(EntityType.CHICKEN,7,5);chicken.startRiding(pig,true);
            var tag=tag();Items.NAME_TAG.interactLivingEntity(tag,f.p,pig,InteractionHand.MAIN_HAND);
            h.assertTrue(tag.getCount()==1 && chicken.getVehicle()==pig && !pig.isRemoved(),"Capture lost passengers");
            chicken.stopRiding();pig.setLeashedTo(f.p,false);Items.NAME_TAG.interactLivingEntity(tag,f.p,pig,InteractionHand.MAIN_HAND);
            h.assertTrue(tag.getCount()==1 && pig.isLeashed() && !pig.isRemoved(),"Capture lost leash");
            var zombie=f.mob(EntityType.ZOMBIE,9,5);Items.NAME_TAG.interactLivingEntity(tag,f.p,zombie,InteractionHand.MAIN_HAND);
            h.assertTrue(zombie.hasCustomName() && !zombie.isRemoved() && TrailsParties.owner(zombie)==null,"Hostile name tag became capture");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void followingUsesVanillaNavigationAndStopsDuringBattle(GameTestHelper h) {
        var f=new TrailsGameTests.Fixture(h);
        // spawnWithNoFreeWill removes every Goal, including the real follow goal under test.
        var pig=EntityType.PIG.create(h.getLevel());pig.moveTo(Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(10,1,5))));
        h.getLevel().addFreshEntity(pig);f.entities.add(pig);TrailsParties.named(f.p,pig);RuntimePlatform.data(pig).putBoolean(TrailsCompanions.FOLLOW,true);
        var start=pig.position();double initial=pig.distanceToSqr(f.p);
        h.runAfterDelay(100,()->{try {
            h.assertTrue(pig.position().distanceTo(start)>.8 && pig.distanceToSqr(f.p)<initial*.7,"Released pig did not walk after owner");
            var goal=new CompanionFollowGoal(pig);f.mob(EntityType.ZOMBIE,12,5);f.start(false);
            h.assertTrue(!goal.canUse(),"Following ran inside a tactical turn");
            f.b.close("test");f.p.teleportTo(start.x+2,start.y,start.z+3);
            h.assertTrue(goal.canUse(),"Following did not resume after combat");h.succeed();
        }finally {f.close();}});
    }
    @GameTest(template="empty")
    public static void creativeReleaseConsumesActualHandAndAlreadyLoadedUuidCannotBeDuplicated(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);var token=store(f,pig);var ticket=TrailsCompanions.ticket(token);
            var existing=EntityType.PIG.create(h.getLevel());existing.setUUID(pig.getUUID());existing.moveTo(pig.position().add(1,0,0));h.getLevel().addFreshEntity(existing);
            h.assertTrue(release(f,pig.position())==null && CompanionStorage.get(f.p.server).find(ticket)!=null,"Loaded UUID was duplicated");
            existing.discard();f.p.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            h.assertTrue(release(f,pig.position())!=null && f.p.getMainHandItem().isEmpty(),"Creative release retained live token");
        } h.succeed();
    }
    @GameTest(template="empty")
    public static void rExitConsumesPressReleaseAcrossBothClosePacketTimings(GameTestHelper h) {
        var g=new EncounterSwitchGesture();
        h.assertTrue(g.exitPressed() && !g.exitPressed(),"Held/repeated exit sent another toggle");g.leftBattle();
        h.assertTrue(g.outside(true,false)==EncounterSwitchGesture.Action.NONE,"Held exit became entry preview");g.released();
        h.assertTrue(g.outside(false,false)==EncounterSwitchGesture.Action.NONE,"Exit release re-entered battle");
        h.assertTrue(g.outside(true,false)==EncounterSwitchGesture.Action.PREVIEW && g.outside(false,false)==EncounterSwitchGesture.Action.ENTER,"Next intentional R cycle stopped working");
        g.exitPressed();g.released();g.leftBattle();
        h.assertTrue(g.outside(false,false)==EncounterSwitchGesture.Action.NONE,"Release before close ack re-entered");
        g.reset();h.assertTrue(g.outside(true,true)==EncounterSwitchGesture.Action.NONE && g.outside(false,false)==EncounterSwitchGesture.Action.NONE,"A menu press entered combat on close");h.succeed();
    }
}
