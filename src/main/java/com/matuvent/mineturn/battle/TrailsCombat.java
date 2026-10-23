package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.TrailsConfig;
import com.matuvent.mineturn.network.BattleNetwork;
import com.matuvent.mineturn.network.TrailsNetwork;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/** Single-player manual AT rules, built on MineTurn's isolated membership/status clock. */
public final class TrailsCombat {
    final BattleSession b;
    final ServerPlayer player;
    final Set<UUID> allies = new HashSet<>();
    final Map<UUID, Integer> regrowth = new HashMap<>();
    final Map<UUID, Integer> turns = new HashMap<>();
    final Set<UUID> charging = new HashSet<>();
    final Set<UUID> guarding = new HashSet<>();
    boolean resolving;
    boolean pendingBurst;
    int pendingTarget = -1;
    double recovery = 1;
    int enemyWait;
    private static final String CP = "mineturn.trails.cp";
    private static final String PRIMARY = "mineturn.trails.primary";
    private static final String AMBUSH = "mineturn.trails.ambushUntil";
    private static final String OWNER = "mineturn.trails.ambushOwner";

    TrailsCombat(BattleSession battle, ServerPlayer player) { this.b = battle; this.player = player; }
    static TrailsConfig cfg() { return TrailsConfig.current; }
    static String id(Entity e) { return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString(); }
    static String itemId(ItemStack s) { return BuiltInRegistries.ITEM.getKey(s.getItem()).toString(); }
    static boolean hostile(LivingEntity e, ServerPlayer p) {
        return e instanceof Enemy || e instanceof Mob m && m.getTarget() == p;
    }
    static boolean friendly(LivingEntity e, ServerPlayer p) {
        return e != p && !hostile(e, p) && (e instanceof Animal || e instanceof IronGolem
                || e instanceof SnowGolem || e instanceof AbstractVillager);
    }
    boolean allied(LivingEntity e) {
        if (e == player) return true;
        return allies.contains(e.getUUID()) || !b.members.containsKey(e.getUUID()) && friendly(e, player);
    }
    boolean enemy(LivingEntity a, LivingEntity c) { return a != c && allied(a) != allied(c); }

    void initialize(boolean advantage) {
        for (var m : b.members.values()) if (m.entity == player || friendly(m.entity, player)) allies.add(m.entity.getUUID());
        // Once admitted, faction does not flip when native AI target memories expire.
        for (var m : b.members.values()) m.allyOwner = allied(m.entity) ? player.getUUID() : null;
        if (primary().isEmpty() && weapon(player.getMainHandItem())) setPrimary(player);
        if (advantage) {
            for (var m : List.copyOf(b.members.values())) {
                if (allied(m.entity) && m.entity != player) b.clock.recovery(m.entity, 0);
                else if (!allied(m.entity)) m.entity.setHealth(Math.max(.1f,
                        m.entity.getHealth() - m.entity.getMaxHealth() * (float) cfg().ambushHealthFraction));
            }
        }
        b.message(advantage ? "背袭先制！敌方扣除最大生命的 25%，我方全员优先行动。" : "AT 战斗开始。选择行动者的指令。R：我方回合退出；V：抢 S。");
        onTurn();
    }

    public static boolean stunned(Entity entity) {
        return !entity.level().isClientSide && entity.getPersistentData().getLong(AMBUSH) > entity.level().getGameTime();
    }
    public static void guardDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        var session = BattleManager.ACTIVE.get(event.getEntity().getUUID());
        if (BattleManager.authorized() && session != null && session.trails != null
                && session.trails.guarding.contains(event.getEntity().getUUID())) event.setAmount(event.getAmount() * .5f);
    }
    public static void damaged(LivingDamageEvent.Post event) {
        var victim = event.getEntity();
        if (victim.level().isClientSide || event.getNewDamage() <= 0) return;
        var active = BattleManager.ACTIVE.get(victim.getUUID());
        if (active != null && active.trails != null && active.trails.allied(victim)) active.trails.addCp(cfg().cpOnDamage);
        if (BattleManager.authorized() || BattleManager.locked(victim) || !victim.isAlive()
                || !(event.getSource().getEntity() instanceof ServerPlayer p)
                || !(event.getSource().getDirectEntity() == p || event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile)
                || BattleManager.locked(p) || !hostile(victim, p)) return;
        long now = victim.level().getGameTime();
        if (victim.getPersistentData().getLong("mineturn.trails.ambushCooldown") > now) return;
        Vec3 facing = victim.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 towardPlayer = p.position().subtract(victim.position()).multiply(1, 0, 1).normalize();
        if (facing.dot(towardPlayer) > -.5) return;
        int ticks = (int) Math.clamp(cfg().backstabSeconds * 20, 20, 400);
        victim.getPersistentData().putLong(AMBUSH, now + ticks);
        victim.getPersistentData().putLong("mineturn.trails.ambushCooldown", now + 1200);
        victim.getPersistentData().putUUID(OWNER, p.getUUID());
        victim.setDeltaMovement(Vec3.ZERO);
        BattleNetwork.send(p, new TrailsNetwork.Cue("ready", "背袭成功 · 按住切换键圈选", ticks, cfg().encounterRadius));
        victim.playSound(SoundEvents.PLAYER_ATTACK_CRIT, .7f, 1.4f);
    }

    public static void request(ServerPlayer p, TrailsNetwork.Command packet) {
        if (p.level().isClientSide || !p.isAlive()) return;
        var session = BattleManager.ACTIVE.get(p.getUUID());
        try {
            if (packet.operation().equals("preview") && session == null) {
                BattleNetwork.send(p, new TrailsNetwork.Cue("radius", "", 0, cfg().encounterRadius)); return;
            }
            if (packet.operation().equals("primary") && session == null) { setPrimary(p); return; }
            if (session == null) {
                if (packet.operation().equals("toggle") && packet.battle().equals(TrailsNetwork.NONE)) start(p);
                return;
            }
            var rules = session.trails;
            if (rules == null || rules.player != p || !session.id.equals(packet.battle()) || rules.resolving) return;
            if (packet.revision() != session.revision) { session.sync(p); return; }
            rules.resolving = true;
            try { rules.command(packet); }
            finally { rules.resolving = false; }
        } catch (IllegalArgumentException | IllegalStateException error) {
            if (session != null && !session.closed) { session.message(error.getMessage()); session.sync(p); }
            else p.displayClientMessage(Component.literal(error.getMessage()), true);
        }
    }

    static BattleSession start(ServerPlayer p) {
        if (!BattleManager.eligible(p) || p.isPassenger() || p.isVehicle() || BattleManager.reloading()) throw new IllegalArgumentException("请在生存或冒险模式、非骑乘状态下进入战斗。");
        var candidates = p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(cfg().encounterRadius, cfg().verticalRange, cfg().encounterRadius),
                e -> e != p && BattleManager.eligible(e) && !BattleManager.locked(e)
                        && !(e instanceof ServerPlayer) && (hostile(e, p) || friendly(e, p))
                        && Math.abs(e.getY() - p.getY()) <= cfg().verticalRange
                        && e.position().subtract(p.position()).horizontalDistance() <= cfg().encounterRadius);
        if (candidates.stream().noneMatch(e -> hostile(e, p))) throw new IllegalArgumentException("圈内没有敌人。");
        long now = p.level().getGameTime();
        boolean advantage = candidates.stream().anyMatch(e -> hostile(e, p) && e.getPersistentData().getLong(AMBUSH) > now
                && e.getPersistentData().hasUUID(OWNER) && p.getUUID().equals(e.getPersistentData().getUUID(OWNER)));
        for (var e : candidates) if (e.getPersistentData().hasUUID(OWNER) && p.getUUID().equals(e.getPersistentData().getUUID(OWNER))) {
            e.getPersistentData().remove(AMBUSH); e.getPersistentData().remove(OWNER);
        }
        var battle = new BattleSession(p, candidates, advantage);
        battle.syncAll();
        BattleNetwork.send(p, new TrailsNetwork.Cue("enter", advantage ? "AMBUSH / 先制攻击" : "TACTICAL SHIFT / 战术切换", 28, cfg().encounterRadius));
        return battle;
    }

    int cp() { return Math.clamp(player.getPersistentData().getInt(CP), 0, 200); }
    void addCp(int amount) { player.getPersistentData().putInt(CP, Math.clamp(cp() + amount, 0, 200)); }
    String primary() { return player.getPersistentData().getString(PRIMARY); }
    static void setPrimary(ServerPlayer p) {
        var stack = p.getMainHandItem();
        if (!weapon(stack)) throw new IllegalArgumentException("请手持剑、斧、弓、弩、重锤或三叉戟，再设为主武器。");
        p.getPersistentData().putString(PRIMARY, itemId(stack));
        p.displayClientMessage(Component.literal("主武器：" + stack.getHoverName().getString() + " · " + burstName(stack)), true);
    }
    static boolean weapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
    }
    static String burstName(ItemStack s) {
        if (s.getItem() instanceof CrossbowItem) return "贯界冲击波";
        if (s.getItem() instanceof BowItem) return "星落箭雨";
        if (s.getItem() instanceof MaceItem) return "陨星震地";
        if (s.is(ItemTags.AXES)) return "裂甲断空";
        if (s.getItem() instanceof TridentItem) return "苍潮奔流";
        return "终式 · 万刃风暴";
    }
    ItemStack primaryStack() {
        for (int i = 0; i < 36; i++) {
            var s = player.getInventory().getItem(i);
            if (!s.isEmpty() && itemId(s).equals(primary())) return s;
        }
        return ItemStack.EMPTY;
    }

    double speed(LivingEntity e) {
        double base = e.getAttribute(MineTurn.AGILITY) == null ? 100 : e.getAttributeValue(MineTurn.AGILITY);
        var brain = b.member(e).brain;
        if (brain != null) base *= brain.agility() / 100.;
        if (e instanceof ServerPlayer) {
            base += cfg().bootsSpeed.getOrDefault(itemId(e.getItemBySlot(EquipmentSlot.FEET)), 0.);
            if (e.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) base += cfg().elytraSpeed;
        }
        var fast = e.getEffect(MobEffects.MOVEMENT_SPEED);
        var slow = e.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (fast != null) base *= 1 + cfg().speedPotionPerLevel * (fast.getAmplifier() + 1);
        if (slow != null) base *= Math.max(.1, 1 - .15 * (slow.getAmplifier() + 1));
        return Double.isFinite(base) ? Math.clamp(base, 10, 1000) : 100;
    }

    void sync(ServerPlayer recipient) {
        if (b.actor == null) return;
        // Existing camera and status locks remain authoritative; the new UI receives its own chunked roster.
        BattleNetwork.send(recipient, new BattleNetwork.State(b.id, b.revision, true, -1, b.actor.getId(), b.clock.time(),
                b.member(player).anchor, b.center, 0, 0, allied(b.actor), allied(b.actor), false, allied(b.actor),
                b.budget.remaining(), b.lastMessage, List.of(), List.of(), List.of(), 1, 0, -1, b.motionSequence, b.motion != null));
        var queue = b.clock.forecast(16, this::speed).stream()
                .map(t -> new BattleNetwork.QueueEntry(t.actor().getId(), b.displayName(t.actor()), t.inAv())).toList();
        BattleNetwork.send(recipient, new TrailsNetwork.View(b.id, b.revision, b.actor.getId(), allied(b.actor), cp(), cfg().burstCost,
                primary(), burstName(primaryStack()), b.budget.remaining(), charging.isEmpty() ? b.lastMessage
                        : "危险蓄力：" + b.members.values().stream().filter(m -> charging.contains(m.entity.getUUID()))
                                .map(m -> m.entity.getName().getString()).reduce((a, c) -> a + "、" + c).orElse("") + " · 防御或抢 S！", queue));
        var roster = b.members.values().stream().map(m -> new TrailsNetwork.Fighter(m.entity.getId(), b.displayName(m.entity),
                !allied(m.entity), m.entity.getHealth(), m.entity.getMaxHealth(), speed(m.entity),
                b.clock.until(m.entity, this::speed), skillName(m.entity))).toList();
        for (int from = 0, page = 0; from < roster.size(); from += 48, page++)
            BattleNetwork.send(recipient, new TrailsNetwork.Roster(b.id, b.revision, page, from + 48 >= roster.size(),
                    roster.subList(from, Math.min(from + 48, roster.size()))));
    }

    void tick() {
        b.prune(); if (b.closed) return;
        b.ticks++; b.turnTicks++;
        b.tickMotion(); if (b.closed) return;
        for (var m : List.copyOf(b.members.values())) {
            var e = m.entity;
            e.setDeltaMovement(Vec3.ZERO); e.fallDistance = 0;
            if (e.position().distanceToSqr(m.anchor) > .001) {
                if (e instanceof ServerPlayer p) p.connection.teleport(m.anchor.x, m.anchor.y, m.anchor.z, p.getYRot(), p.getXRot());
                else e.setPos(m.anchor);
            }
        }
        if (pendingBurst && b.motion == null) {
            pendingBurst = false;
            try { burst(pendingTarget); } catch (IllegalArgumentException error) { b.message(error.getMessage()); }
            if (b.closed) return;
        }
        if (!allied(b.actor) && b.motion == null && ++enemyWait >= 28) {
            enemyWait = 0;
            enemyAction();
        }
        if (!b.closed && b.ticks % 20 == 0) b.syncAll();
    }

    void onTurn() {
        enemyWait = 0;
        recovery = 1;
        var actor = b.actor;
        if (actor == null) return;
        guarding.remove(actor.getUUID());
        int left = regrowth.getOrDefault(actor.getUUID(), 0);
        if (left > 0) { actor.heal(2); regrowth.put(actor.getUUID(), left - 1); }
        turns.merge(actor.getUUID(), 1, Integer::sum);
    }

    void command(TrailsNetwork.Command p) {
        if (p.operation().equals("burst")) {
            if (cp() < cfg().burstCost) throw new IllegalArgumentException("CP 不足，普通行动及受伤可积累 CP。");
            if (b.motion != null) { pendingBurst = true; pendingTarget = p.target(); return; }
            burst(p.target()); return;
        }
        if (b.actor == null || !allied(b.actor)) throw new IllegalArgumentException("敌方回合：可以抢 S，不能退出或下达普通指令。");
        if (b.motion != null) throw new IllegalArgumentException("请等待移动完成。");
        if (p.operation().equals("toggle")) { b.close("已退出 AT 战斗，恢复即时战斗。"); return; }
        if (p.operation().equals("end")) { finish(.7); return; }
        if (p.operation().equals("guard")) {
            guarding.add(b.actor.getUUID()); finish(.5); return;
        }
        if (p.operation().equals("move")) { move(p.x(), p.z()); return; }
        if (p.operation().equals("skill")) { companionSkill(p.target()); return; }
        if (p.operation().equals("attack") || p.operation().equals("wind")) { attack(p.target(), p.slot(), p.operation().equals("wind")); return; }
        if (p.operation().equals("item")) { item(p.slot()); return; }
        throw new IllegalArgumentException("未知 AT 指令。");
    }

    void finish(double delay) {
        if (b.closed) return;
        var actor = b.actor;
        b.prune(); if (b.closed) return;
        if (b.actor != actor) return;
        if (actor != null && b.members.containsKey(actor.getUUID())) b.clock.recovery(actor, Timeline.LAP * delay);
        b.next();
    }
    LivingEntity target(int entityId, LivingEntity source) {
        var entity = b.level.getEntity(entityId);
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || !b.members.containsKey(living.getUUID()) || !enemy(source, living))
            throw new IllegalArgumentException("请选择本场存活的敌人。");
        return living;
    }
    static double gap(LivingEntity a, LivingEntity c) { return BattleManager.gap(a.getBoundingBox(), c.getBoundingBox()); }
    void requireRange(LivingEntity source, LivingEntity target, double range) {
        if (gap(source, target) > range || !source.hasLineOfSight(target)) throw new IllegalArgumentException("目标超出攻击范围或被墙壁遮挡，请先移动。");
    }
    void hit(LivingEntity source, LivingEntity target, double damage) {
        if (b.closed || !source.isAlive() || !target.isAlive() || !b.members.containsKey(target.getUUID()) || !enemy(source, target)) return;
        BattleManager.authorized(() -> {
            target.invulnerableTime = 0;
            var damageSource = source instanceof ServerPlayer p ? source.damageSources().playerAttack(p) : source.damageSources().mobAttack(source);
            if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) dragon.hurt(dragon.head, damageSource, (float) damage);
            else target.hurt(damageSource, (float) damage);
        });
        b.level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + .7, target.getZ(), 8, .25, .3, .25, .1);
    }
    void area(LivingEntity source, Vec3 center, double radius, double damage) {
        for (var m : List.copyOf(b.members.values()))
            if (m.entity.position().distanceTo(center) <= radius && source.hasLineOfSight(m.entity)) hit(source, m.entity, damage);
    }
    double baseDamage(LivingEntity source) {
        if (source.getAttribute(Attributes.ATTACK_DAMAGE) != null) return Math.max(2, source.getAttributeValue(Attributes.ATTACK_DAMAGE));
        var strength = source.getEffect(MobEffects.DAMAGE_BOOST);
        return 3 + (strength == null ? 0 : 3 * (strength.getAmplifier() + 1));
    }
    void animate(LivingEntity source, LivingEntity victim, boolean ranged) {
        source.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        var animation = new BattleNetwork.ActionAnimation(b.id, ++b.actionSequence,
                net.minecraft.resources.ResourceLocation.parse(ranged ? "mineturn:ranged" : "mineturn:melee"), 10,
                source.getId(), victim.getId(), victim.position(), true);
        BattleNetwork.send(player, animation);
    }

    void attack(int targetId, int slot, boolean wind) {
        var source = b.actor;
        var victim = target(targetId, source);
        if (source != player) {
            if (wind) throw new IllegalArgumentException("友军不能使用风弹或物品。");
            if (id(source).equals("minecraft:pig") && gap(source, victim) > 2.2) {
                requireRange(source, victim, 5);
                Vec3 toward = victim.position().subtract(source.position()).multiply(1, 0, 1);
                var route = TerrainPath.trace(source, toward.normalize().scale(Math.max(0, toward.length() - 1.2)), ignored -> true);
                if (route.cost() < .01 || route.cost() > b.budget.remaining()) throw new IllegalArgumentException("冲撞路线被阻挡或距离不足。");
                b.budget.move(route.cost()); b.motion = new BattleSession.Movement(source, route, "ground");
                b.motion.onLanding = () -> {
                    if (victim.isAlive() && gap(source, victim) <= 2.2) { hit(source, victim, baseDamage(source)); animate(source, victim, false); addCp(cfg().cpPerAction); }
                    finish(1.1);
                };
                b.revision++; b.syncAll(); return;
            }
            requireRange(source, victim, 2.2);
            hit(source, victim, id(source).equals("minecraft:iron_golem") ? 10 : baseDamage(source));
            animate(source, victim, false); addCp(cfg().cpPerAction); finish(1); return;
        }
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("无效武器槽。");
        player.getInventory().selected = slot;
        ((com.matuvent.mineturn.mixin.StatusAccess) player).mineturn$equipment();
        var s = player.getMainHandItem();
        boolean ranged = s.getItem() instanceof BowItem || s.getItem() instanceof CrossbowItem;
        double damage = baseDamage(player), delay = 1;
        if (wind && !(s.getItem() instanceof MaceItem)) throw new IllegalArgumentException("风弹组合需要手持重锤。");
        requireRange(source, victim, ranged ? 24 : s.getItem() instanceof TridentItem ? 3.5 : 3);
        if (ranged) {
            int ammo = find(Items.ARROW);
            if (ammo < 0) throw new IllegalArgumentException("背包内需要箭。");
            player.getInventory().getItem(ammo).shrink(1);
            damage = s.getItem() instanceof CrossbowItem ? 10 : 8;
            delay = s.getItem() instanceof CrossbowItem ? 1.3 : 1.1;
            hit(source, victim, damage);
        } else if (s.getItem() instanceof MaceItem) {
            if (wind) {
                int charge = find(Items.WIND_CHARGE);
                if (charge < 0) throw new IllegalArgumentException("背包内需要风弹，组合技消耗 1 个。");
                player.getInventory().getItem(charge).shrink(1);
            }
            area(source, victim.position(), wind ? 5 : 2, Math.max(6, damage) * (wind ? 2.3 : 1));
            delay = wind ? 2.2 : 1.3;
        } else if (s.is(ItemTags.SWORDS)) {
            Vec3 direction = victim.position().subtract(source.position()).multiply(1, 0, 1).normalize();
            for (var m : List.copyOf(b.members.values())) {
                Vec3 delta = m.entity.position().subtract(source.position()).multiply(1, 0, 1);
                if (gap(source, m.entity) <= 3 && direction.dot(delta.normalize()) >= Math.cos(Math.PI / 4)
                        && source.hasLineOfSight(m.entity)) hit(source, m.entity, damage);
            }
        } else { hit(source, victim, s.is(ItemTags.AXES) ? damage * 1.15 : damage); delay = s.is(ItemTags.AXES) ? 1.2 : 1; }
        if (s.isDamageableItem()) s.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        animate(source, victim, ranged); addCp(cfg().cpPerAction);
        finish(delay);
    }
    int find(Item item) {
        for (int i = 0; i < 36; i++) if (player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }
    void item(int slot) {
        if (b.actor != player) throw new IllegalArgumentException("友军不能使用道具。");
        if (slot < 0 || slot >= 36) throw new IllegalArgumentException("无效物品槽。");
        var stack = player.getInventory().getItem(slot);
        var food = stack.get(DataComponents.FOOD);
        if (food == null && !stack.is(Items.POTION)) throw new IllegalArgumentException("请选择食物或可饮用药水。");
        if (stack.isEmpty()) throw new IllegalArgumentException("物品已用完。");
        double healing = food == null ? 0 : cfg().foodHealing.getOrDefault(itemId(stack), food.nutrition() * cfg().foodHealingPerNutrition);
        String foodId = itemId(stack);
        int hungerBefore = player.getFoodData().getFoodLevel();
        float saturationBefore = player.getFoodData().getSaturationLevel();
        BattleManager.authorized(() -> {
            var remaining = stack.finishUsingItem(player.level(), player);
            player.getInventory().setItem(slot, remaining);
            if (Double.isFinite(healing) && healing > 0) player.heal((float) healing);
            if (food != null && cfg().foodHunger.containsKey(foodId))
                player.getFoodData().setFoodLevel(Math.clamp(hungerBefore + cfg().foodHunger.get(foodId), 0, 20));
            if (food != null && cfg().foodSaturation.containsKey(foodId)) {
                double saturation = cfg().foodSaturation.get(foodId);
                if (Double.isFinite(saturation)) player.getFoodData().setSaturation((float) Math.clamp(saturationBefore + saturation, 0, player.getFoodData().getFoodLevel()));
            }
        });
        player.containerMenu.broadcastChanges(); addCp(5); finish(.6);
    }

    void move(double dx, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dz) || Math.abs(dx) + Math.abs(dz) > b.budget.remaining() + .001)
            throw new IllegalArgumentException("超过剩余移动范围。");
        var entity = b.actor;
        var route = TerrainPath.trace(entity, new Vec3(dx, 0, dz), ignored -> true);
        if (route.cost() < .01 || route.cost() > b.budget.remaining()) throw new IllegalArgumentException("路径被阻挡或距离不足。");
        b.budget.move(route.cost());
        b.motion = new BattleSession.Movement(entity, route, "ground");
        b.revision++; b.syncAll();
    }

    static String skillName(LivingEntity e) {
        return switch (id(e)) {
            case "minecraft:pig" -> "自愈 · 三回合恢复";
            case "minecraft:sheep" -> "羊毛祝福 · 范围治疗";
            case "minecraft:wolf" -> "战吼 · 群体强化";
            case "minecraft:cow", "minecraft:mooshroom" -> "坚韧守护";
            case "minecraft:chicken", "minecraft:parrot" -> "轻羽 · 群体加速";
            case "minecraft:cat", "minecraft:ocelot" -> "灵巧守势";
            case "minecraft:iron_golem" -> "震地重拳";
            case "minecraft:villager", "minecraft:wandering_trader" -> "急救术";
            default -> "生命鼓舞";
        };
    }
    void companionSkill(int targetId) {
        var actor = b.actor;
        if (actor == player) throw new IllegalArgumentException("玩家使用装备、道具或主武器 S 技。");
        String kind = id(actor);
        if (kind.equals("minecraft:pig")) { actor.heal(2); regrowth.put(actor.getUUID(), 3); }
        else if (kind.equals("minecraft:iron_golem")) {
            var victim = target(targetId, actor); requireRange(actor, victim, 3); area(actor, victim.position(), 3, 12);
        } else {
            for (var m : b.members.values()) if (allied(m.entity) && m.entity.distanceTo(actor) <= 6) {
                switch (kind) {
                    case "minecraft:sheep" -> m.entity.heal(4);
                    case "minecraft:wolf" -> effect(m.entity, MobEffects.DAMAGE_BOOST, 240, 0);
                    case "minecraft:chicken", "minecraft:parrot" -> effect(m.entity, MobEffects.MOVEMENT_SPEED, 240, 0);
                    case "minecraft:cow", "minecraft:mooshroom" -> effect(m.entity, MobEffects.DAMAGE_RESISTANCE, 240, 0);
                    case "minecraft:cat", "minecraft:ocelot" -> { if (m.entity == actor) effect(actor, MobEffects.DAMAGE_RESISTANCE, 300, 1); }
                    default -> m.entity.heal(3);
                }
                b.level.sendParticles(ParticleTypes.HEART, m.entity.getX(), m.entity.getY() + 1, m.entity.getZ(), 3, .3, .3, .3, 0);
            }
        }
        b.message(actor.getName().getString() + " 使用 " + skillName(actor));
        addCp(10); finish(1.3);
    }
    static void effect(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type, int ticks, int amplifier) {
        BattleManager.authorized(() -> entity.addEffect(new MobEffectInstance(type, ticks, amplifier)));
    }

    void burst(int targetId) {
        if (cp() < cfg().burstCost) throw new IllegalArgumentException("CP 不足。");
        var stack = primaryStack();
        if (stack.isEmpty() || !weapon(stack)) throw new IllegalArgumentException("主武器必须仍在背包内；战外手持武器按 B 设置。");
        var victim = target(targetId, player);
        requireRange(player, victim, 24);
        int before = cp(); addCp(-cfg().burstCost);
        double multiplier = before == 200 ? 1.5 : 1;
        double weaponDamage = 1, baseMultiplier = 0, totalMultiplier = 1;
        for (var entry : stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, net.minecraft.world.item.component.ItemAttributeModifiers.EMPTY).modifiers()) {
            if (!entry.attribute().equals(Attributes.ATTACK_DAMAGE) || !entry.slot().test(EquipmentSlot.MAINHAND)) continue;
            switch (entry.modifier().operation()) {
                case ADD_VALUE -> weaponDamage += entry.modifier().amount();
                case ADD_MULTIPLIED_BASE -> baseMultiplier += entry.modifier().amount();
                case ADD_MULTIPLIED_TOTAL -> totalMultiplier *= 1 + entry.modifier().amount();
            }
        }
        weaponDamage = (weaponDamage + baseMultiplier) * totalMultiplier;
        double damage = Math.max(6, weaponDamage) * 3.2 * multiplier;
        if (stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem) {
            Vec3 forward = victim.position().subtract(player.position()).normalize();
            for (var m : List.copyOf(b.members.values())) {
                Vec3 delta = m.entity.position().subtract(player.position());
                double along = delta.dot(forward);
                if (along >= 0 && along <= 24 && delta.subtract(forward.scale(along)).length() <= 3
                        && player.hasLineOfSight(m.entity)) hit(player, m.entity, damage);
            }
        } else if (stack.is(ItemTags.AXES)) hit(player, victim, damage * 1.8);
        else if (stack.getItem() instanceof BowItem) area(player, victim.position(), 5, damage);
        else if (stack.getItem() instanceof MaceItem) area(player, victim.position(), 6, damage * 1.2);
        else area(player, player.position(), 10, damage);
        BattleNetwork.send(player, new TrailsNetwork.Cue("burst", burstName(stack), 36, cfg().encounterRadius));
        b.level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, .7f, 1.3f);
        b.message("S-BREAK · " + burstName(stack));
        // The interrupted actor keeps its turn and partial AT; only the player's next action is delayed.
        if (b.actor == player) { finish(2.5); enemyWait = -12; return; }
        enemyWait = -12;
        b.clock.recovery(player, b.clock.remaining(player) + Timeline.LAP * 1.5);
        b.revision++; b.prune(); if (!b.closed) b.syncAll();
    }

    void enemyAction() {
        var actor = b.actor;
        if (actor == null || !actor.isAlive()) { b.prune(); return; }
        var victim = b.enemies(actor).stream().min(Comparator.comparingDouble(actor::distanceToSqr)).orElse(null);
        if (victim == null) { b.close("战斗结束。"); return; }
        String kind = id(actor);
        boolean boss = actor.getMaxHealth() >= 100 || kind.equals("minecraft:elder_guardian");
        int turn = turns.getOrDefault(actor.getUUID(), 1);
        if (charging.remove(actor.getUUID())) {
            area(actor, victim.position(), boss ? 6 : 4, boss ? 12 : 9);
            b.message(actor.getName().getString() + (boss ? " 发动大招：毁灭震荡！" : " 引爆蓄力攻击！"));
            if (kind.equals("minecraft:creeper")) BattleManager.authorized(() -> actor.hurt(actor.damageSources().generic(), Float.MAX_VALUE));
            finish(1.8); return;
        }
        if ((boss || kind.equals("minecraft:creeper")) && turn % 3 == 0) {
            charging.add(actor.getUUID());
            b.message("⚠ " + actor.getName().getString() + " 正在蓄力，下次行动发动范围大招！可防御或抢 S。");
            finish(1.6); return;
        }
        boolean ranged = kind.contains("skeleton") || kind.contains("pillager") || kind.contains("witch")
                || kind.contains("blaze") || kind.contains("ghast") || kind.contains("guardian") || boss;
        if (gap(actor, victim) > (ranged ? 20 : 2.4) || !actor.hasLineOfSight(victim)) {
            if (actor instanceof Mob mob) {
                var route = b.pursuitRoute(mob, victim);
                if (route.cost() > .01) {
                    b.budget.move(route.cost()); b.motion = new BattleSession.Movement(actor, route, b.animationMode(actor, route));
                    b.motion.onLanding = () -> finish(1);
                    b.revision++; b.syncAll(); return;
                }
            }
            finish(.8); return;
        }
        double damage = Math.min(12, Math.max(3, baseDamage(actor)));
        if (turn % 3 == 0) {
            if (kind.contains("witch")) { actor.heal(6); effect(victim, MobEffects.POISON, 100, 0); }
            else if (kind.contains("spider")) { hit(actor, victim, damage); effect(victim, MobEffects.POISON, 100, 0); }
            else if (kind.contains("skeleton") || kind.contains("pillager")) { hit(actor, victim, damage); hit(actor, victim, damage * .6); }
            else if (kind.contains("zombie")) { hit(actor, victim, damage * 1.4); actor.heal(2); }
            else { hit(actor, victim, damage * 1.6); effect(victim, MobEffects.MOVEMENT_SLOWDOWN, 120, 0); }
        } else hit(actor, victim, damage);
        animate(actor, victim, ranged); finish(turn % 3 == 0 ? 1.3 : 1);
    }
}
