package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.network.TrailsNetwork;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("trails") @PrefixGameTestTemplate(false)
public final class TrailsCpGameTests {
    public TrailsCpGameTests() {}
    private static void rejects(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected rejected action");
    }
    @GameTest(template="empty")
    public static void chickenCosts80ExactlyOnceAndCannotBorrowOwnersCp(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var chicken=f.mob(EntityType.CHICKEN,6,5);f.mob(EntityType.SHEEP,7,5);f.mob(EntityType.ZOMBIE,12,12);
            TrailsCp.set(f.p,173);f.start(false);var c=f.b.trails;f.b.actor=chicken;c.onTurn();
            h.assertTrue(TrailsCp.get(chicken)==100,"New ally did not start with 100 CP");
            var command=new TrailsNetwork.Command(f.b.id,f.b.revision,"skill",-1,0,0,0);
            TrailsCombat.request(f.p,command);
            h.assertTrue(c.pending!=null && TrailsCp.get(chicken)==20 && c.cp()==173,"Bird skill did not reserve exactly 80 of its own CP");
            TrailsCombat.request(f.p,command);TrailsCombat.request(f.p,new TrailsNetwork.Command(f.b.id,f.b.revision,"skill",-1,0,0,0));
            h.assertTrue(TrailsCp.get(chicken)==20,"Replay/busy request charged twice");
            TrailsGameTests.resolve(c);f.b.actor=chicken;c.onTurn();long turn=c.turn;double time=f.b.clock.time();
            rejects(()->c.companionSkill(-1,Vec3.ZERO));
            h.assertTrue(TrailsCp.get(chicken)==20 && c.cp()==173 && !c.busy() && c.turn==turn && f.b.clock.time()==time,"Insufficient CP spent the owner/turn or triggered another pull");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void weaponTechniquePaysButBasicAttackRecoversCp(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var zombie=f.mob(EntityType.ZOMBIE,7,5);zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);zombie.setHealth(200);
            TrailsCp.set(f.p,100);f.start(false);var c=f.b.trails;f.b.actor=f.p;c.onTurn();
            c.weaponSkill(zombie.getId(),0,new Vec3(1,0,0));
            h.assertTrue(c.cp()==75,"Sword did not deduct 25 before windup");
            TrailsGameTests.resolve(c);h.assertTrue(c.cp()==75,"Paid technique refunded itself as an ordinary action");
            f.b.actor=f.p;c.onTurn();c.attack(zombie.getId(),0,false,new Vec3(1,0,0));TrailsGameTests.resolve(c);
            h.assertTrue(c.cp()==90,"Ordinary attack lost its 15 CP recovery");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void insufficientOrInvalidTechniquePreservesAmmoDurabilityAndTurn(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var zombie=f.mob(EntityType.ZOMBIE,8,5);f.p.getInventory().setItem(1,new ItemStack(Items.CROSSBOW));
            f.p.getInventory().setItem(2,new ItemStack(Items.ARROW,8));TrailsCp.set(f.p,44);f.start(false);var c=f.b.trails;
            f.b.actor=f.p;c.onTurn();long turn=c.turn;int selected=f.p.getInventory().selected;
            rejects(()->c.weaponSkill(zombie.getId(),1,new Vec3(1,0,0)));
            h.assertTrue(c.cp()==44 && f.p.getInventory().getItem(2).getCount()==8 && f.p.getInventory().getItem(1).getDamageValue()==0
                    && !c.busy() && c.turn==turn && f.p.getInventory().selected==selected,"Insufficient CP consumed resources or action");
            TrailsCp.set(f.p,100);rejects(()->c.weaponSkill(zombie.getId(),1,new Vec3(Double.NaN,0,0)));
            h.assertTrue(c.cp()==100 && f.p.getInventory().getItem(2).getCount()==8,"Bad preview direction cost CP/ammo");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void cpIsPerEntityBoundedAndZeroDoesNotReinitialize(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);var sheep=f.mob(EntityType.SHEEP,8,5);TrailsParties.named(f.p,pig);TrailsCp.set(f.p,73);
            h.assertTrue(TrailsCp.get(pig)==100 && TrailsCp.get(sheep)==100,"Initial companion CP");
            TrailsCp.set(pig,0);TrailsCp.initialize(pig);TrailsCp.add(pig,15);
            h.assertTrue(TrailsCp.get(pig)==15 && TrailsCp.get(f.p)==73 && TrailsCp.get(sheep)==100,"CP leaked across members or zero refilled");
            TrailsCp.add(pig,Integer.MAX_VALUE);h.assertTrue(TrailsCp.get(pig)==200,"CP exceeded 200 or overflowed");
            TrailsCp.add(pig,Integer.MIN_VALUE);TrailsCp.initialize(pig);h.assertTrue(TrailsCp.get(pig)==0,"Zero CP refilled");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void lowCpEnemyFallsBackToBasicAndRegainsItsOwnCp(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var zombie=f.mob(EntityType.ZOMBIE,6,5);f.start(false);var c=f.b.trails;f.b.actor=zombie;c.onTurn();c.turns.put(zombie.getUUID(),3);TrailsCp.set(zombie,0);
            c.enemyAction();h.assertTrue(c.pending!=null && c.performances.get(zombie.getUUID())[1].equals(TrailsBestiary.get(zombie).basic().name()),"Enemy attempted an unaffordable skill");
            TrailsGameTests.resolve(c);h.assertTrue(TrailsCp.get(zombie)==15,"Enemy basic did not restore its resource");
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void storedCompanionKeepsCpAndHpAcrossReceiptsAndRepeatedRelease(GameTestHelper h) {
        try(var f=new TrailsGameTests.Fixture(h)) {
            var pig=f.mob(EntityType.PIG,7,5);pig.setHealth(5);var pos=pig.position();var uuid=pig.getUUID();
            var tag=new ItemStack(Items.NAME_TAG);tag.set(DataComponents.CUSTOM_NAME,Component.literal("CP伙伴"));f.p.setItemInHand(InteractionHand.MAIN_HAND,tag);
            Items.NAME_TAG.interactLivingEntity(tag,f.p,pig,InteractionHand.MAIN_HAND);
            var restored=TrailsCompanions.release(f.p,InteractionHand.MAIN_HAND,pos);f.entities.add(restored);
            h.assertTrue(restored!=null && TrailsCp.get(restored)==100 && restored.getHealth()==5,"Initial capture/release changed CP or HP");
            for(int cp:new int[]{20,0,200}) {
                TrailsCp.set(restored,cp);restored.setHealth(3);
                h.assertTrue(TrailsCompanions.capture(f.p,restored),"Recall failed");
                var ticket=TrailsCompanions.ticket(f.p.getInventory().getItem(0));
                var storage=CompanionStorage.get(f.p.server);var saved=storage.save(new CompoundTag(),h.getLevel().registryAccess());
                var loaded=CompanionStorage.load(saved,h.getLevel().registryAccess());
                h.assertTrue(loaded.find(ticket)!=null && loaded.find(ticket).entity().equals(storage.find(ticket).entity()),"Disk receipt lost snapshot");
                f.p.setItemInHand(InteractionHand.MAIN_HAND,f.p.getInventory().getItem(0));
                restored=TrailsCompanions.release(f.p,InteractionHand.MAIN_HAND,pos);f.entities.add(restored);
                h.assertTrue(restored!=null && restored.getUUID().equals(uuid) && TrailsCp.get(restored)==cp && restored.getHealth()==3,"Release reset CP/HP or identity");
            }
        }h.succeed();
    }
    @GameTest(template="empty")
    public static void rosterTransmitsIndependentCpAndSkillCost(GameTestHelper h) {
        var buffer=new FriendlyByteBuf(Unpooled.buffer());
        try {
            var fighter=new TrailsNetwork.Fighter(9,"chicken",false,3,4,100,0,"振翅号令",20,80,List.of(),null);
            var payload=new TrailsNetwork.Roster(java.util.UUID.randomUUID(),4,0,true,List.of(fighter));
            TrailsNetwork.Roster.CODEC.encode(buffer,payload);var restored=TrailsNetwork.Roster.CODEC.decode(buffer);
            h.assertTrue(restored.equals(payload) && buffer.readableBytes()==0,"CP roster codec lost or misaligned fields");
        }finally{buffer.release();}h.succeed();
    }
}
