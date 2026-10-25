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
    final Map<UUID,TrailsBestiaryActions.Plan> plans=new HashMap<>();
    final Set<UUID> guarding = new HashSet<>();
    boolean resolving;
    boolean pendingBurst;
    int pendingTarget = -1;
    Vec3 pendingAim = Vec3.ZERO;
    double recovery = 1;
    int enemyWait;
    Pending pending;
    Pending burstAction;
    final Map<UUID, String[]> performances = new HashMap<>();
    final Map<UUID, TrailsNetwork.Performance> lastPerformances = new HashMap<>();
    boolean knockback;
    record Pending(LivingEntity source, long turn, int ticks, Runnable release, boolean red) {}
    boolean busy() { return pending != null || burstAction != null || b.motion != null || knockback; }
    void perform(LivingEntity source, String animation, int ticks, boolean red, Vec3 aim) {
        var entry = performances.get(source.getUUID());
        String style = animation.equals("support") ? "support" : entry != null ? entry[0] : TrailsChoreography.style(source, source.getMainHandItem());
        String title = animation.equals("support") ? skillName(source) : entry != null ? entry[1] : red ? skillName(source) : "普通攻击";
        perform(source, animation, ticks, red, aim, style, title);
    }
    void perform(LivingEntity source, String animation, int ticks, boolean red, Vec3 aim, String style, String title) {
        if(b.closed)return;
        var packet=new TrailsNetwork.Performance(b.id, ++b.actionSequence, source.getId(), animation, ticks, red, aim, style, title);
        lastPerformances.put(source.getUUID(),packet);BattleNetwork.send(player,packet);
    }
    void windup(LivingEntity source, LivingEntity victim, int ticks, boolean red, Runnable release) {
        windupAt(source, victim == null ? source.position().add(source.getLookAngle()) : victim.getEyePosition(), ticks, red, release);
    }
    void windupAt(LivingEntity source, Vec3 aim, int ticks, boolean red, Runnable release) {
        inputTicks = 0; direction = Vec3.ZERO;
        pending = new Pending(source, turn, ticks, release, red);
        perform(source, "draw", ticks, red, aim);
        b.revision++; b.syncAll();
    }
    void tickPending() {
        var action = pending;
        if (action == null) return;
        if (b.actor != action.source || turn != action.turn || !action.source.isAlive() || !b.members.containsKey(action.source.getUUID())) { pending = null; return; }
        if (action.ticks > 1) {
            pending = new Pending(action.source, action.turn, action.ticks - 1, action.release, action.red);
            if (action.red && action.ticks % 3 == 0) b.level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                    new org.joml.Vector3f(1, .04f, .02f), 1.4f), action.source.getX(), action.source.getY() + 1, action.source.getZ(), 8, .4, .5, .4, .01);
            return;
        }
        pending = null;
        boolean previous = resolving;
        resolving = true;
        try { action.release.run(); }
        finally { resolving = previous; }
        if (!b.closed) { b.revision++; b.syncAll(); }
    }
    void clear() { burstAction = null; plans.clear(); charging.clear(); performances.clear(); lastPerformances.clear(); pending = null; knockback = false; pendingBurst = false; direction = Vec3.ZERO; inputTicks = 0; }
    long turn, inputSequence = -1;
    Vec3 movementOrigin = Vec3.ZERO, direction = Vec3.ZERO;
    double movementRadius;
    int inputTicks;
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
        return e != p && !hostile(e,p) && e instanceof Mob;
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

    public static void input(ServerPlayer p, TrailsNetwork.MoveInput packet) {
        var session = BattleManager.ACTIVE.get(p.getUUID());
        if (session == null || session.closed || session.trails == null || !session.id.equals(packet.battle())) return;
        var rules = session.trails;
        if (rules.player != p || !p.isAlive() || session.actor == null || !rules.allied(session.actor) || rules.busy() || rules.resolving
                || packet.turn() != rules.turn || packet.sequence() <= rules.inputSequence
                || !Double.isFinite(packet.x()) || !Double.isFinite(packet.z()) || Math.abs(packet.x()) > 1 || Math.abs(packet.z()) > 1) return;
        rules.inputSequence = packet.sequence();
        var direction = new Vec3(packet.x(), 0, packet.z());
        rules.direction = direction.lengthSqr() > 1 ? direction.normalize() : direction;
        rules.inputTicks = 5;
    }

    public static boolean inventoryAllowed(ServerPlayer p) {
        var session = BattleManager.ACTIVE.get(p.getUUID());
        return session != null && !session.closed && session.trails != null && session.trails.player == p
                && session.actor == p && !session.trails.busy() && !session.trails.resolving && p.isAlive()
                && p.containerMenu == p.inventoryMenu;
    }

    public static void settings(ServerPlayer p, TrailsNetwork.Settings packet) {
        String message = "范围设置；移动半径在下一回合生效。";
        if (packet.save()) {
            if (!p.server.isSingleplayerOwner(p.getGameProfile()) && !p.hasPermissions(2)) message = "只有单人存档主人或管理员可修改。";
            else try { TrailsConfig.saveRanges(packet.encounter(), packet.vertical(), packet.movement()); message = "已保存，入战范围立即生效，移动半径下回合生效。"; }
            catch (IllegalArgumentException | IllegalStateException | java.io.IOException | com.google.gson.JsonParseException error) { message = "保存失败：" + error.getMessage(); }
        }
        BattleNetwork.send(p, new TrailsNetwork.Settings(cfg().encounterRadius, cfg().verticalRange, cfg().movementRadius, false, message));
    }

    public static void equipmentChanged(ServerPlayer p) {
        if (!inventoryAllowed(p)) return;
        var session = BattleManager.ACTIVE.get(p.getUUID());
        ((com.matuvent.mineturn.mixin.StatusAccess) p).mineturn$equipment();
        if (weapon(p.getMainHandItem())) p.getPersistentData().putString(PRIMARY, itemId(p.getMainHandItem()));
        session.revision++; session.syncAll();
    }

    static BattleSession start(ServerPlayer p) {
        if (!BattleManager.eligible(p) || p.isPassenger() || p.isVehicle() || BattleManager.reloading()) throw new IllegalArgumentException("请在生存或冒险模式、非骑乘状态下进入战斗。");
        var candidates = p.serverLevel().getEntitiesOfClass(LivingEntity.class,
                p.getBoundingBox().inflate(cfg().encounterRadius, cfg().verticalRange, cfg().encounterRadius),
                e -> e != p && !(e instanceof net.minecraft.world.entity.boss.wither.WitherBoss wither && wither.getInvulnerableTicks()>0) && BattleManager.eligible(e) && !BattleManager.locked(e)
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
                primary(), burstName(primaryStack()), movementRadius, turn, movementOrigin, busy(), charging.isEmpty() ? b.lastMessage
                        : "蓄力预告：" + b.members.values().stream().filter(m -> plans.containsKey(m.entity.getUUID()))
                                .map(m -> (plans.get(m.entity.getUUID()).warning().golden()?"[金光 S] ":"[红光] ")+m.entity.getName().getString()+" · "+plans.get(m.entity.getUUID()).move().name()).reduce((a, c) -> a + "、" + c).orElse("") + " · 下次自身回合发动，可移动、防御或抢 S！", queue));
        var roster = b.members.values().stream().map(m -> new TrailsNetwork.Fighter(m.entity.getId(), b.displayName(m.entity),
                !allied(m.entity), m.entity.getHealth(), m.entity.getMaxHealth(), speed(m.entity),
                b.clock.until(m.entity, this::speed), m.entity == player ? TrailsWeaponSkills.name(player.getMainHandItem()) : skillName(m.entity), m.entity.getActiveEffects().stream().limit(64)
                        .map(e -> new TrailsNetwork.Status(BuiltInRegistries.MOB_EFFECT.getKey(e.getEffect().value()).toString(), e.getAmplifier() + 1, e.getDuration())).toList(), plans.containsKey(m.entity.getUUID())?plans.get(m.entity.getUUID()).warning():null)).toList();
        for (int from = 0, page = 0; from < roster.size(); from += 48, page++)
            BattleNetwork.send(recipient, new TrailsNetwork.Roster(b.id, b.revision, page, from + 48 >= roster.size(),
                    roster.subList(from, Math.min(from + 48, roster.size()))));
    }

    void tick() {
        for(var member:List.copyOf(b.members.values()))if(member.entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon
                && dragon.getPhaseManager().getCurrentPhase().getPhase()==net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase.DYING)b.remove(dragon,"末影龙已被击败，恢复原版死亡飞行。");
        b.prune(); if (b.closed) return;
        plans.keySet().retainAll(b.members.keySet());charging.retainAll(b.members.keySet());
        b.ticks++; b.turnTicks++;
        boolean wasKnockback = knockback;
        if (burstAction == null) b.tickMotion(); if (b.closed) return;
        if (wasKnockback && b.motion == null && knockback) { knockback = false; recover(b.actor, 1.4); if (b.closed) return; }
        if (burstAction != null) tickBurst(); else tickPending(); if (b.closed) return;
        if (inputTicks > 0 && --inputTicks >= 0 && allied(b.actor) && !busy() && direction.lengthSqr() > 1e-8) {
            move(direction.x * .215, direction.z * .215);
            if (b.closed) return;
        }
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
            try { burst(pendingTarget, pendingAim); } catch (IllegalArgumentException error) { b.message(error.getMessage()); }
            if (b.closed) return;
        }
        if (!allied(b.actor) && !busy() && ++enemyWait >= 28) {
            enemyWait = 0;
            enemyAction();
        }
        if (!b.closed && b.ticks % 20 == 0) b.syncAll();
    }

    void onTurn() {
        turn++; inputSequence = -1; inputTicks = 0; direction = Vec3.ZERO;
        enemyWait = 0;
        recovery = 1;
        var actor = b.actor;
        if (actor == null) return;
        movementOrigin = actor.position(); movementRadius = cfg().movementRadius;
        guarding.remove(actor.getUUID());
        int left = regrowth.getOrDefault(actor.getUUID(), 0);
        if (left > 0) { actor.heal(2); regrowth.put(actor.getUUID(), left - 1); }
        turns.merge(actor.getUUID(), 1, Integer::sum);
    }

    void command(TrailsNetwork.Command p) {
        if (p.operation().equals("burst")) {
            if (cp() < cfg().burstCost) throw new IllegalArgumentException("CP 不足，普通行动及受伤可积累 CP。");
            if (burstAction != null) throw new IllegalArgumentException("请等待 S 技演出结束。");
            if (pending != null && b.actor == player) throw new IllegalArgumentException("请等待自己的战技完成后再抢 S。");
            burst(p.target(), new Vec3(p.x(), 0, p.z())); return;
        }
        if (b.actor == null || !allied(b.actor)) throw new IllegalArgumentException("敌方回合：可以抢 S，不能退出或下达普通指令。");
        if (busy()) throw new IllegalArgumentException("请等待当前动作完成。");
        if (p.operation().equals("toggle")) { b.close("已退出 AT 战斗，恢复即时战斗。"); return; }
        if (p.operation().equals("select") || p.operation().equals("primary")) {
            if (b.actor != player) throw new IllegalArgumentException("只能在玩家回合切换武器。");
            if (p.slot() < 0 || p.slot() > 8) throw new IllegalArgumentException("无效武器槽。");
            player.getInventory().selected = p.slot();
            ((com.matuvent.mineturn.mixin.StatusAccess) player).mineturn$equipment();
            if (p.operation().equals("primary")) setPrimary(player);
            else if (weapon(player.getMainHandItem())) player.getPersistentData().putString(PRIMARY, itemId(player.getMainHandItem()));
            b.revision++; b.syncAll(); return;
        }
        if (p.operation().equals("end")) { finish(.7); return; }
        if (p.operation().equals("guard")) {
            guarding.add(b.actor.getUUID()); finish(.5); return;
        }
        if (p.operation().equals("weapon_skill")) { weaponSkill(p.target(), p.slot(), new Vec3(p.x(), 0, p.z())); return; }
        if (p.operation().equals("skill")) { companionSkill(p.target(), new Vec3(p.x(), 0, p.z())); return; }
        if (p.operation().equals("attack") || p.operation().equals("wind")) { attack(p.target(), p.slot(), p.operation().equals("wind"), new Vec3(p.x(), 0, p.z())); return; }
        if (p.operation().equals("item")) { item(p.slot()); return; }
        throw new IllegalArgumentException("未知 AT 指令。");
    }

    void finish(double delay) {
        inputTicks = 0; direction = Vec3.ZERO;
        if (b.closed) return;
        var actor = b.actor;
        b.prune(); if (b.closed) return;
        if (b.actor != actor) return;
        if (actor != null) { performances.remove(actor.getUUID()); lastPerformances.remove(actor.getUUID()); }
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
        damage(source,target,damage,source instanceof ServerPlayer p ? source.damageSources().playerAttack(p) : source.damageSources().mobAttack(source));
    }
    void damage(LivingEntity source,LivingEntity target,double damage,net.minecraft.world.damagesource.DamageSource damageSource) {
        if(b.closed || !source.isAlive() || !target.isAlive() || !b.members.containsKey(target.getUUID()) || !enemy(source,target))return;
        float before=target.getHealth()+target.getAbsorptionAmount();
        BattleManager.authorized(() -> {
            target.invulnerableTime = 0;
            if (target instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon dragon) dragon.hurt(dragon.head, allied(source)?source.damageSources().playerAttack(player):damageSource, (float) damage);
            else target.hurt(damageSource, (float) damage);
        });
        float lost = Math.max(0, before - target.getHealth() - target.getAbsorptionAmount());
        BattleNetwork.send(player, new TrailsNetwork.Impact(b.id, ++b.actionSequence, target.getId(), lost, target.getEyePosition().add(0, .35, 0)));
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
        perform(source, "strike", 12, !allied(source), victim.position());
        source.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        var animation = new BattleNetwork.ActionAnimation(b.id, ++b.actionSequence,
                net.minecraft.resources.ResourceLocation.parse(ranged ? "mineturn:ranged" : "mineturn:melee"), 10,
                source.getId(), victim.getId(), victim.position(), true);
        BattleNetwork.send(player, animation);
    }

    void attack(int targetId, int slot, boolean wind) {
        var victim = b.level.getEntity(targetId);
        Vec3 aim = victim == null ? b.actor.getLookAngle().multiply(1, 0, 1) : victim.position().subtract(b.actor.position()).multiply(1, 0, 1);
        var shape = TrailsArea.weapon(player.getInventory().getItem(Math.clamp(slot, 0, 8)), wind, false);
        if (shape.kind() == TrailsArea.Kind.DISK && aim.length() > shape.cast()) aim = aim.normalize().scale(shape.cast());
        attack(targetId, slot, wind, aim);
    }
    void attack(int targetId, int slot, boolean wind, Vec3 aim) {
        var source = b.actor;
        if (source != player) {
            var victim = target(targetId, source);
            if (wind) throw new IllegalArgumentException("友军不能使用风弹或物品。");
            if (id(source).equals("minecraft:pig") && gap(source, victim) > 2.2) {
                requireRange(source, victim, 5);
                Vec3 toward = victim.position().subtract(source.position()).multiply(1, 0, 1);
                var route = TerrainPath.trace(source, toward.normalize().scale(Math.max(0, toward.length() - 1.2)), ignored -> true);
                if (route.cost() < .01 || route.cost() > b.budget.remaining()) throw new IllegalArgumentException("冲撞路线被阻挡或距离不足。");
                b.budget.move(route.cost()); b.motion = new BattleSession.Movement(source, route, "ground");
                b.motion.onLanding = () -> {
                    if (victim.isAlive() && gap(source, victim) <= 2.2) friendAttack(source, victim, false);
                    else finish(1.1);
                };
                b.revision++; b.syncAll(); return;
            }
            friendAttack(source,victim,source instanceof SnowGolem);return;
        }
        playerAction(targetId, slot, wind, aim, false);
    }
    void friendAttack(LivingEntity source,LivingEntity target,boolean snowball) {
        var profile=TrailsBestiary.get(source);
        if(profile==null)throw new IllegalArgumentException("此生物没有 AT 配置。");
        TrailsBestiaryActions.cast(this,source,target,profile.basic(),profile.basic().aim(source,target),false,source.position());
    }
    void projectile(LivingEntity source, Vec3 end, Item item) {
        var from = source.getEyePosition();
        var wall = b.level.clip(new net.minecraft.world.level.ClipContext(from, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, source));
        if (wall.getType() != net.minecraft.world.phys.HitResult.Type.MISS) end = wall.getLocation();
        com.matuvent.mineturn.api.CombatVisuals.flight(b.level, from, end, new ItemStack(item), true, false);
    }
    void weaponSkill(int targetId, int slot, Vec3 aim) { playerAction(targetId, slot, false, aim, true); }
    void recover(LivingEntity source, double delay) {
        if (b.closed || b.actor != source || !source.isAlive()) return;
        perform(source, "recover", 8, false, source.position().add(source.getLookAngle()));
        pending = new Pending(source, turn, 8, () -> finish(delay), false);
    }
    void playerAction(int targetId, int slot, boolean wind, Vec3 aim, boolean skill) {
        if (b.actor != player) throw new IllegalArgumentException("只有玩家能使用武器战技。");
        if (busy()) throw new IllegalArgumentException("请等待当前动作完成。");
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("无效武器槽。");
        var stack = player.getInventory().getItem(slot);
        if (skill && !weapon(stack)) throw new IllegalArgumentException("请先装备武器。");
        if (wind && !(stack.getItem() instanceof MaceItem)) throw new IllegalArgumentException("风弹组合需要手持重锤。");
        var shape = skill ? TrailsWeaponSkills.shape(stack) : TrailsArea.weapon(stack, wind, false);
        if (!shape.validAim(aim)) throw new IllegalArgumentException("攻击方向或落点无效。");
        var victim = shape.kind() == TrailsArea.Kind.SINGLE ? target(targetId, player) : null;
        if (victim != null) requireRange(player, victim, shape.cast());
        boolean bow = stack.getItem() instanceof BowItem, crossbow = stack.getItem() instanceof CrossbowItem;
        boolean mace = stack.getItem() instanceof MaceItem, trident = stack.getItem() instanceof TridentItem;
        boolean charged = wind || skill && mace;
        int resource = bow || crossbow ? find(Items.ARROW) : charged ? find(Items.WIND_CHARGE) : -2;
        if (resource == -1) throw new IllegalArgumentException(bow || crossbow ? "背包内需要 1 支普通箭。" : "背包内需要 1 个风弹。");
        player.getInventory().selected = slot;
        ((com.matuvent.mineturn.mixin.StatusAccess) player).mineturn$equipment();
        double base = baseDamage(player);
        var snapshot = stack.copy();
        if (resource >= 0) player.getInventory().getItem(resource).shrink(1);
        String style = TrailsChoreography.style(player, snapshot), title = skill || wind ? TrailsWeaponSkills.name(snapshot) : "普通攻击 · " + snapshot.getHoverName().getString();
        performances.put(player.getUUID(), new String[]{style, title});
        Vec3 end = victim != null ? victim.getEyePosition() : shape.kind() == TrailsArea.Kind.DISK ? shape.center(player.position(), aim).add(0, 1, 0)
                : player.getEyePosition().add(shape.direction(aim).scale(Math.max(2, shape.radius())));
        BattleFacing.along(player, shape.direction(aim));
        windupAt(player, end, TrailsChoreography.windup(style, skill || wind), false, () -> {
            perform(player, "strike", bow || crossbow || skill && trident ? TrailsChoreography.flight(player.getEyePosition().distanceTo(end)) : 4, false, end); player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (bow || crossbow || skill && trident) {
                Item arrow = trident ? Items.TRIDENT : Items.ARROW;
                projectile(player, end, arrow);
                if (skill && crossbow) {
                    var forward = shape.direction(aim); var across = new Vec3(forward.z, 0, -forward.x);
                    for (int lane : new int[]{-1, 1}) projectile(player, end.add(across.scale(lane * 3)), Items.ARROW);
                }
                player.playSound(trident ? SoundEvents.TRIDENT_THROW.value() : crossbow ? SoundEvents.CROSSBOW_SHOOT : SoundEvents.ARROW_SHOOT, 1, 1);
            }
            pending = new Pending(player, turn, bow || crossbow || skill && trident ? TrailsChoreography.flight(player.getEyePosition().distanceTo(end)) : 4, () -> {
                if (victim != null && (!victim.isAlive() || !b.members.containsKey(victim.getUUID()) || gap(player, victim) > shape.cast() || !player.hasLineOfSight(victim))) {
                    b.message("目标已离场或离开射程，攻击落空。"); recover(player, 1.5); return;
                }
                double damage = bow ? (skill ? 14 : 8) : crossbow ? (skill ? 12 : 10) : mace ? Math.max(6, base) * (charged ? 2.3 : 1)
                        : skill && trident ? Math.max(8, base) * 1.5 : snapshot.is(ItemTags.AXES) ? base * (skill ? 1.8 : 1.15) : base * (skill ? 1.4 : 1);
                double delay = bow ? (skill ? 1.6 : 1.1) : crossbow ? (skill ? 1.8 : 1.3) : mace ? (charged ? 2.2 : 1.3)
                        : snapshot.is(ItemTags.AXES) ? (skill ? 1.7 : 1.2) : skill ? (trident ? 1.6 : 1.5) : 1;
                if (victim != null) hit(player, victim, damage); else shaped(player, shape, aim, damage);
                if (skill && snapshot.is(ItemTags.AXES) && victim != null && victim.isAlive() && b.members.containsKey(victim.getUUID()))
                    b.clock.recovery(victim, b.clock.remaining(victim) + 30 * speed(victim));
                if (snapshot.is(ItemTags.SWORDS)) {
                    var point = player.position().add(shape.direction(aim).scale(1.5));
                    b.level.sendParticles(ParticleTypes.SWEEP_ATTACK, point.x, point.y + 1, point.z, skill ? 3 : 1, .2, 0, .2, 0);
                    player.playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1, 1);
                }
                if (mace) {
                    shockwave(shape.center(player.position(), aim), shape.radius(), charged);
                    player.playSound(SoundEvents.MACE_SMASH_GROUND_HEAVY, .9f, charged ? .8f : 1.1f);
                }
                if (stack.isDamageableItem()) stack.hurtAndBreak(skill ? 2 : 1, player, EquipmentSlot.MAINHAND);
                b.message(title); addCp(cfg().cpPerAction); recover(player, delay);
            }, false);
        });
    }
    void shockwave(Vec3 center, double radius, boolean wind) {
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            b.level.sendParticles(wind ? ParticleTypes.GUST : ParticleTypes.CLOUD, center.x + Math.cos(angle) * radius, center.y + .15,
                    center.z + Math.sin(angle) * radius, 1, 0, .05, 0, .02);
        }
    }
    void shaped(LivingEntity source, TrailsArea shape, Vec3 aim, double damage) {
        Vec3 origin = source.position();
        for (var m : List.copyOf(b.members.values())) if (shape.contains(origin, aim, m.entity.position()) && source.hasLineOfSight(m.entity)) hit(source, m.entity, damage);
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
        var potion = stack.get(DataComponents.POTION_CONTENTS);
        var beforeEffects = new HashMap<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>, MobEffectInstance>();
        for (var e : player.getActiveEffects()) beforeEffects.put(e.getEffect(), new MobEffectInstance(e));
        if (food == null && !stack.is(Items.POTION)) throw new IllegalArgumentException("请选择食物或可饮用药水。");
        if (stack.isEmpty()) throw new IllegalArgumentException("物品已用完。");
        double healing = food == null ? 0 : cfg().foodHealing.getOrDefault(itemId(stack), food.nutrition() * cfg().foodHealingPerNutrition);
        String foodId = itemId(stack);
        int hungerBefore = player.getFoodData().getFoodLevel();
        float saturationBefore = player.getFoodData().getSaturationLevel();
        BattleManager.authorized(() -> {
            var remaining = stack.finishUsingItem(player.level(), player);
            player.getInventory().setItem(slot, remaining);
            if (potion != null) for (var e : potion.getAllEffects()) {
                var previous = beforeEffects.get(e.getEffect());
                if (previous != null && e.getEffect().value().isBeneficial() && !e.getEffect().value().isInstantenous())
                    player.addEffect(new MobEffectInstance(e.getEffect(), Math.max(previous.getDuration(), e.getDuration()),
                            Math.min(Math.clamp(cfg().maxBuffStacks, 1, 256) - 1, previous.getAmplifier() + e.getAmplifier() + 1)));
            }
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
        if (b.actor == null || !allied(b.actor) || busy() || !Double.isFinite(dx) || !Double.isFinite(dz)) return;
        var entity = b.actor;
        Vec3 offset = new Vec3(dx, 0, dz);
        if (offset.length() > .215) offset = offset.normalize().scale(.215);
        Vec3 radial = entity.position().add(offset).subtract(movementOrigin).multiply(1, 0, 1);
        if (radial.length() > movementRadius) offset = movementOrigin.add(radial.normalize().scale(movementRadius))
                .subtract(entity.position()).multiply(1, 0, 1);
        if (offset.lengthSqr() < 1e-10) return;
        var route = TerrainPath.trace(entity, offset,
                point -> point.subtract(movementOrigin).horizontalDistanceSqr() <= movementRadius * movementRadius + 1e-8);
        if (route.cost() < 1e-6) return;
        Vec3 start = entity.position();
        // Reuse supported collision samples, contact/fall rules and authoritative motion packets.
        for (var point : route.samples()) {
            var previous = entity.position();
            entity.setPos(point); b.member(entity).anchor = point;
            BattleRealm.contact(b, entity, previous, point);
            BattleFields.contact(b, entity, previous, point, true);
            if (!entity.isAlive() || b.closed || !b.members.containsKey(entity.getUUID())) { b.prune(); return; }
        }
        BattleFacing.along(entity, entity.position().subtract(start));
        for (var landing : route.landings()) { b.fall(entity, landing); if (b.closed || !entity.isAlive()) { b.prune(); return; } }
        entity.setDeltaMovement(Vec3.ZERO); entity.fallDistance = 0;
        b.sendMotion(entity, false);
    }

    static String skillName(LivingEntity entity) {
        var profile=TrailsBestiary.get(entity);return profile==null?"生命鼓舞":profile.skill().name();
    }
    void companionSkill(int targetId) {
        var target=b.level.getEntity(targetId);
        var move=TrailsBestiary.get(b.actor).skill();
        companionSkill(targetId,target instanceof LivingEntity living?move.aim(b.actor,living):Vec3.ZERO);
    }
    void companionSkill(int targetId,Vec3 aim) {
        var source=b.actor;if(source==player)throw new IllegalArgumentException("玩家请使用武器战技。");
        var profile=TrailsBestiary.get(source);if(profile==null)throw new IllegalArgumentException("此生物没有 AT 配置。");
        var move=profile.skill();LivingEntity victim=null;
        if(move.area().kind()==TrailsArea.Kind.SINGLE)victim=target(targetId,source);
        else if(b.level.getEntity(targetId) instanceof LivingEntity living && enemy(source,living))victim=living;
        TrailsBestiaryActions.cast(this,source,victim,move,aim,false,source.position());
    }
    static void effect(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> type, int ticks, int amplifier) {
        var previous = entity.getEffect(type);
        if (previous != null && type.value().isBeneficial() && !type.value().isInstantenous()) {
            amplifier = Math.min(Math.clamp(cfg().maxBuffStacks, 1, 256) - 1, previous.getAmplifier() + amplifier + 1);
            ticks = Math.max(ticks, previous.getDuration());
        }
        var effect = new MobEffectInstance(type, ticks, amplifier);
        BattleManager.authorized(() -> entity.addEffect(effect));
    }

    void burst(int targetId) {
        var victim = b.level.getEntity(targetId);
        var shape = TrailsArea.weapon(primaryStack(), false, true);
        var aim = victim == null ? new Vec3(0, 0, 1) : victim.position().subtract(player.position()).multiply(1, 0, 1);
        if (shape.kind() == TrailsArea.Kind.DISK && shape.cast() > 0 && aim.length() > shape.cast()) aim = aim.normalize().scale(shape.cast());
        burst(targetId, aim);
    }
    void burst(int targetId, Vec3 aim) {
        if (cp() < cfg().burstCost) throw new IllegalArgumentException("CP 不足。");
        if (burstAction != null || b.actor == player && pending != null) throw new IllegalArgumentException("请等待当前演出完成。");
        var stack = primaryStack();
        if (stack.isEmpty() || !weapon(stack)) throw new IllegalArgumentException("主武器必须仍在背包内；战外手持武器按 B 设置。");
        var shape = TrailsArea.weapon(stack, false, true);
        if (!shape.validAim(aim)) throw new IllegalArgumentException("S 技落点超出施放范围。");
        var victim = shape.kind() == TrailsArea.Kind.SINGLE ? target(targetId, player) : null;
        if (victim != null) requireRange(player, victim, shape.cast());
        inputTicks = 0; direction = Vec3.ZERO;
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
        boolean ownTurn = b.actor == player;
        if (!ownTurn) b.clock.recovery(player, b.clock.remaining(player) + Timeline.LAP * 1.5);
        String style = "burst_" + TrailsChoreography.style(player, stack), title = burstName(stack);
        Vec3 end = victim != null ? victim.getEyePosition() : shape.kind() == TrailsArea.Kind.DISK ? shape.center(player.position(), aim).add(0, 1, 0)
                : player.getEyePosition().add(shape.direction(aim).scale(Math.max(3, shape.radius())));
        BattleNetwork.send(player, new TrailsNetwork.Cue("burst", title, 22, cfg().encounterRadius));
        perform(player, "draw", 26, false, end, style, title);
        burstAction = new Pending(player, turn, 26, () -> {
            boolean projectile = stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem || stack.getItem() instanceof TridentItem;
            perform(player,"strike",projectile?TrailsChoreography.flight(player.getEyePosition().distanceTo(end)):6,false,end,style,title);
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            if (projectile) {
                Item item = stack.getItem() instanceof TridentItem ? Items.TRIDENT : Items.ARROW;
                for (int lane = -1; lane <= 1; lane++) projectile(player, end.add(lane * .8, 0, 0), item);
            }
            burstAction = new Pending(player, turn, projectile ? TrailsChoreography.flight(player.getEyePosition().distanceTo(end)) : 6, () -> {
                if (victim != null) {
                    if (victim.isAlive() && b.members.containsKey(victim.getUUID()) && player.hasLineOfSight(victim) && gap(player, victim) <= shape.cast()) hit(player, victim, damage * 1.8);
                } else shaped(player, shape, aim, damage * (stack.getItem() instanceof MaceItem ? 1.2 : 1));
                if(b.closed)return;
                perform(player,"recover",12,false,end,style,title);
                shockwave(shape.center(player.position(), aim), shape.kind() == TrailsArea.Kind.DISK ? shape.radius() : 4, stack.getItem() instanceof MaceItem);
                b.level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, .7f, 1.3f);
                b.message("S-BREAK · " + title);
                burstAction = new Pending(player, turn, 12, () -> {
                    if (ownTurn && b.actor == player) finish(2.5);
                    enemyWait = -12; b.prune();
                    if(!b.closed && pending!=null && b.actor==pending.source && pending.source.isAlive()) {
                        var paused=lastPerformances.get(pending.source.getUUID());
                        if(paused!=null)perform(pending.source,paused.animation(),Math.max(1,pending.ticks),paused.red(),paused.aim(),paused.style(),paused.title());
                    }
                    if (!b.closed) { b.revision++; b.syncAll(); }
                }, false);
            }, false);
        }, false);
        b.revision++; b.syncAll();
    }
    void tickBurst() {
        var action = burstAction; if (action == null) return;
        if (!player.isAlive() || !b.members.containsKey(player.getUUID())) { burstAction = null; return; }
        if (action.ticks > 1) { burstAction = new Pending(player, action.turn, action.ticks - 1, action.release, false); return; }
        burstAction = null;
        boolean previous = resolving; resolving = true;
        try { action.release.run(); } finally { resolving = previous; }
        if (!b.closed) { b.revision++; b.syncAll(); }
    }

    static boolean ranged(LivingEntity actor) {
        String kind = id(actor);
        return actor.getMainHandItem().getItem() instanceof BowItem || actor.getMainHandItem().getItem() instanceof CrossbowItem
                || actor.getMainHandItem().getItem() instanceof TridentItem || Set.of("minecraft:skeleton", "minecraft:stray", "minecraft:bogged",
                "minecraft:pillager", "minecraft:witch", "minecraft:breeze", "minecraft:blaze", "minecraft:ghast", "minecraft:guardian", "minecraft:elder_guardian").contains(kind);
    }
    void enemyAction() { TrailsBestiaryActions.enemy(this); }
    /** Collision-checked leap samples reuse native landing damage and the existing motion interpolator. */
    boolean knockUp(LivingEntity source, LivingEntity victim, double vertical) {
        if (b.closed || !victim.isAlive() || !b.members.containsKey(victim.getUUID()) || AquaticPath.immersed(victim, victim.position())) return false;
        Vec3 horizontal = victim.position().subtract(source.position()).multiply(1, 0, 1).normalize().scale(.10);
        Vec3 point = victim.position(); var samples = new ArrayList<Vec3>();
        for (int tick = 0; tick < 80; tick++) {
            var offset = horizontal.add(0, vertical, 0);
            int steps = Math.max(1, (int)Math.ceil(offset.length() / .07));
            boolean blocked = false;
            for (int step = 0; step < steps; step++) {
                Vec3 next = point.add(offset.scale(1. / steps));
                if (Math.abs(next.y - b.center.y) > 6 || next.subtract(b.center).horizontalDistance() > cfg().encounterRadius + 4
                        || !SpatialPath.canStep(victim, point, next, false)) { blocked = true; break; }
                samples.add(next); point = next;
            }
            if (blocked) {
                if (vertical < 0 && TerrainPath.supportedAt(victim, point)) break;
                // A wall stops horizontal travel; a ceiling cuts the ascent short.
                horizontal = Vec3.ZERO; if (vertical > 0) vertical = 0;
                if (vertical < 0) break;
            }
            vertical = (vertical - .08) * .98;
        }
        if (samples.isEmpty()) return false;
        var route = new TerrainPath.Result(point, MovementDistance.between(victim.position(), point), List.of(), samples);
        b.motion = new BattleSession.Movement(victim, route, "leap"); knockback = true;
        // Completion (including an obstructed route) is settled in tick after the native fall handler.
        b.message("风弹命中：击飞后按实际落差结算原版摔落伤害。"); b.revision++; b.syncAll(); return true;
    }
}
