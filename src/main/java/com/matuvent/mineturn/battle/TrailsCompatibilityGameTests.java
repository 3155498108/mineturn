package com.matuvent.mineturn.battle;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("trails")
@PrefixGameTestTemplate(false)
public final class TrailsCompatibilityGameTests {
    static ItemStack attributeWeapon() {
        var stack = new ItemStack(Items.STICK);
        var builder = ItemAttributeModifiers.builder();
        builder.add(Attributes.ATTACK_DAMAGE, modifier("weapon_add", 4, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        builder.add(Attributes.ATTACK_DAMAGE, modifier("weapon_base", .5, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.MAINHAND);
        builder.add(Attributes.ATTACK_DAMAGE, modifier("weapon_total", .2, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.MAINHAND);
        builder.add(Attributes.ATTACK_DAMAGE, modifier("offhand_only", 99, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
        builder.add(Attributes.ATTACK_SPEED, modifier("unrelated_speed", -2, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
        return stack;
    }

    private static AttributeModifier modifier(String name, double amount, AttributeModifier.Operation operation) {
        return new AttributeModifier(ResourceLocation.fromNamespaceAndPath("mineturn", name), amount, operation);
    }

    @GameTest(template = "empty")
    public static void attributeWeaponUsesHeldSlotAndAllModifierOperations(GameTestHelper helper) {
        var stack = attributeWeapon();
        helper.assertTrue(TrailsWeaponSkills.kind(stack) == TrailsWeaponSkills.Kind.MELEE, "Attribute weapon was not recognized");
        helper.assertTrue(Math.abs(TrailsWeaponSkills.attackDamage(stack) - 9) < .001, "Weapon mixed slots, attributes or multiplication order");
        helper.assertTrue(TrailsWeaponSkills.cpCost(stack) == 25 && TrailsWeaponSkills.shape(stack).kind() == TrailsArea.Kind.SINGLE, "Generic skill preview or cost mismatch");
        helper.assertTrue(TrailsArea.weapon(stack, false, true).kind() == TrailsArea.Kind.SINGLE, "Generic S preview mismatch");
        helper.assertTrue(!TrailsWeaponSkills.supported(new ItemStack(Items.STICK)), "Ordinary non-weapon became a weapon");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void genericWeaponCommitsThroughNormalPlayerAction(GameTestHelper helper) {
        try (var fixture = new TrailsGameTests.Fixture(helper)) {
            var target = fixture.mob(EntityType.ZOMBIE, 6, 5);
            fixture.p.getInventory().setItem(0, attributeWeapon());
            fixture.start(false);
            fixture.b.actor = fixture.p;
            fixture.b.trails.onTurn();
            TrailsCp.set(fixture.p, 100);
            fixture.b.trails.weaponSkill(target.getId(), 0, target.position().subtract(fixture.p.position()));
            helper.assertTrue(TrailsCp.get(fixture.p) == 75 && fixture.b.trails.pending != null, "Generic skill did not commit once");
            float health = target.getHealth();
            TrailsGameTests.resolve(fixture.b.trails);
            helper.assertTrue(target.getHealth() < health && TrailsCp.get(fixture.p) == 75, "Generic skill failed or refunded CP");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void genericSUsesSelectedStackWhenAnotherVariantComesFirst(GameTestHelper helper) {
        try (var fixture = new TrailsGameTests.Fixture(helper)) {
            var target = fixture.mob(EntityType.ZOMBIE, 6, 5);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            target.setHealth(200);
            var selected = attributeWeapon();
            fixture.p.getInventory().setItem(0, new ItemStack(Items.STICK));
            fixture.p.getInventory().setItem(1, selected);
            fixture.p.getInventory().selected = 1;
            TrailsCombat.setPrimary(fixture.p);
            fixture.start(false);
            fixture.b.actor = fixture.p;
            fixture.b.trails.onTurn();
            TrailsCp.set(fixture.p, 200);
            helper.assertTrue(fixture.b.trails.primaryStack() == selected, "S selected the first item id instead of the held variant");
            fixture.b.trails.burst(target.getId(), target.position().subtract(fixture.p.position()));
            TrailsGameTests.resolve(fixture.b.trails);
            helper.assertTrue(TrailsCp.get(fixture.p) == 100 && target.getHealth() < 140, "Generic S lost its attributes or CP cost");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void explicitProfilesWinWhileFallbackUnderstandsRoles(GameTestHelper helper) {
        try (var fixture = new TrailsGameTests.Fixture(helper)) {
            var melee = fixture.mob(EntityType.ZOMBIE, 8, 5);
            var ranged = fixture.mob(EntityType.SKELETON, 10, 5);
            var friendly = fixture.mob(EntityType.PIG, 5, 8);
            helper.assertTrue(TrailsBestiary.get(melee) == TrailsBestiary.get("minecraft:zombie"), "Fallback replaced an authored profile");
            helper.assertTrue(TrailsAutoProfiles.get(melee).skill().cpCost() == 30, "Melee fallback cost mismatch");
            var shot = TrailsAutoProfiles.get(ranged);
            helper.assertTrue(shot.basic().range() == 24 && !shot.basic().projectile().isBlank() && shot.skill().cpCost() == 40, "Ranged fallback became melee");
            helper.assertTrue(TrailsAutoProfiles.get(friendly).skill().friendly(), "Friendly fallback has no support skill");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void attributeAttacksUseCurrentDamageWithoutDoublingStrength(GameTestHelper helper) {
        try (var fixture = new TrailsGameTests.Fixture(helper)) {
            for (int tick = 0; tick < 61; tick++) fixture.p.tick();
            var source = fixture.mob(EntityType.ZOMBIE, 6, 5);
            fixture.start(false);
            TrailsCombat.effect(source, MobEffects.DAMAGE_BOOST, 200, 0);
            double damage = source.getAttributeValue(Attributes.ATTACK_DAMAGE);
            float health = fixture.p.getHealth();
            var move = TrailsAutoProfiles.get(source).basic();
            TrailsBestiaryActions.apply(fixture.b.trails, source, fixture.p, move, move.aim(source, fixture.p), source.position());
            helper.assertTrue(Math.abs(health - fixture.p.getHealth() - damage) < .001, "Attribute attack applied Strength twice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fallbackBossWarnsAndCommitsOnItsNextTurn(GameTestHelper helper) {
        try (var fixture = new TrailsGameTests.Fixture(helper)) {
            var boss = fixture.mob(EntityType.ZOMBIE, 6, 5);
            boss.getAttribute(Attributes.MAX_HEALTH).setBaseValue(160);
            boss.setHealth(160);
            fixture.start(false);
            fixture.b.actor = boss;
            fixture.b.trails.onTurn();
            var profile = TrailsAutoProfiles.get(boss);
            helper.assertTrue(profile.ultimate() != null, "High-health hostile has no fallback S");
            TrailsBestiaryActions.prepare(fixture.b.trails, boss, fixture.p, profile.ultimate(), true);
            helper.assertTrue(fixture.b.trails.plans.get(boss.getUUID()).warning().golden() && !fixture.b.trails.busy(), "Fallback S skipped its response turn");
            fixture.b.actor = boss;
            fixture.b.trails.onTurn();
            fixture.b.trails.enemyAction();
            helper.assertTrue(fixture.b.trails.pending != null && !fixture.b.trails.plans.containsKey(boss.getUUID()), "Fallback S never committed");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void otherMovementModifiersChangeAtAlongsideSpeedPotion(GameTestHelper helper) {
        try (var fixture = new TrailsGameTests.Fixture(helper)) {
            fixture.mob(EntityType.ZOMBIE, 8, 5);
            fixture.start(false);
            double base = fixture.b.trails.speed(fixture.p);
            fixture.p.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(modifier("mod_speed", .5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            helper.assertTrue(Math.abs(fixture.b.trails.speed(fixture.p) - base * 1.5) < .001, "Other movement modifier did not affect AT");
            TrailsCombat.effect(fixture.p, MobEffects.MOVEMENT_SPEED, 200, 0);
            helper.assertTrue(Math.abs(fixture.b.trails.speed(fixture.p) - base * 1.8) < .001, "Native Speed was counted twice");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void consumablesKeepDrinkableAndThrowablePotionRules(GameTestHelper helper) {
        helper.assertTrue(TrailsCombat.consumable(new ItemStack(Items.POTION)) && TrailsCombat.consumable(new ItemStack(Items.BREAD)), "Standard consumables unavailable");
        helper.assertTrue(!TrailsCombat.consumable(new ItemStack(Items.SPLASH_POTION)) && !TrailsCombat.consumable(new ItemStack(Items.LINGERING_POTION))
                && !TrailsCombat.consumable(new ItemStack(Items.TIPPED_ARROW)), "Throwable item became drinkable");
        helper.succeed();
    }
}
