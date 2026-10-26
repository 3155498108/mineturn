package com.matuvent.mineturn.battle;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.platform.RuntimePlatform;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;

public final class TrailsCompanions {
    private static final String TICKET = "mineturn.companionTicket";
    public static final String FOLLOW = "mineturn.trails.followOwner";
    public static boolean friendly(ServerPlayer player, LivingEntity entity) {
        return entity instanceof Mob && !(entity instanceof Enemy) && !TrailsCombat.hostile(entity, player)
                && !BattleManager.temporarySummon(entity);
    }
    public static boolean canStore(ServerPlayer player, LivingEntity entity) {
        return friendly(player, entity) && TrailsParties.canName(player, entity) && entity.isAlive() && !entity.isRemoved()
                && !entity.isPassenger() && !entity.isVehicle() && !((Mob) entity).isLeashed();
    }
    public static boolean hasSpace(ServerPlayer player, ItemStack namingTag) {
        if (player.getInventory().getFreeSlot() >= 0) return true;
        // A final naming tag in a normal inventory slot is consumed before capture runs.
        return namingTag.getCount() == 1 && player.getInventory().items.stream().anyMatch(s -> s == namingTag);
    }
    public static UUID ticket(ItemStack stack) {
        if (!stack.is(MineTurn.COMPANION_TOKEN.get())) return null;
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(TICKET) ? tag.getUUID(TICKET) : null;
    }
    public static boolean capture(ServerPlayer player, Mob mob) {
        if (!canStore(player, mob) || !player.getUUID().equals(TrailsParties.owner(mob))) return false;
        int slot = player.getInventory().getFreeSlot();
        if (slot < 0) { message(player, "背包已满，请先腾出一个格子。"); return false; }
        var entity = new CompoundTag();
        if (!mob.save(entity)) return false;
        var ticket = UUID.randomUUID();
        var data = new CompoundTag(); data.putUUID(TICKET, ticket);
        var item = new ItemStack(MineTurn.COMPANION_TOKEN.get());
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        item.set(DataComponents.CUSTOM_NAME, Component.translatable("item.mineturn.companion_token.named", mob.getDisplayName()));
        var storage = CompanionStorage.get(player.server);
        storage.put(ticket, player.getUUID(), entity);
        player.getInventory().setItem(slot, item);
        mob.discard();
        player.inventoryMenu.broadcastChanges();
        message(player, "已收纳 " + mob.getName().getString() + "；手持友军收纳牌右键方块放出。");
        return true;
    }
    public static Mob release(ServerPlayer player, InteractionHand hand, Vec3 position) {
        if (BattleManager.locked(player)) { message(player, "请先退出战斗，再放出友军。"); return null; }
        var stack = player.getItemInHand(hand); var ticket = ticket(stack);
        var storage = CompanionStorage.get(player.server); var entry = ticket == null ? null : storage.find(ticket);
        if (entry == null) { message(player, "这张收纳牌已失效，或不属于这个世界。"); return null; }
        if (!player.getUUID().equals(entry.owner())) { message(player, "只有原主人能放出这名友军。"); return null; }
        var level = player.serverLevel();
        var pos = net.minecraft.core.BlockPos.containing(position);
        if (!Double.isFinite(position.x + position.y + position.z) || !level.hasChunkAt(pos)
                || position.distanceToSqr(player.position()) > 64 || !level.getWorldBorder().isWithinBounds(pos)) return null;
        var tag = entry.entity().copy();
        if (!tag.hasUUID("UUID")) return null;
        for (var world : player.server.getAllLevels()) if (world.getEntity(tag.getUUID("UUID")) != null) return null;
        var loaded = EntityType.loadEntityRecursive(tag, level, entity -> entity);
        if (!(loaded instanceof Mob mob)) return null;
        mob.moveTo(position.x, position.y, position.z, player.getYRot(), 0);
        if (mob.getY() < level.getMinBuildHeight() || mob.getBoundingBox().maxY > level.getMaxBuildHeight()
                || !level.noCollision(mob, mob.getBoundingBox()) || !level.getWorldBorder().isWithinBounds(mob.getBoundingBox())) {
            message(player, "这里放不下友军，请选择空旷的位置。"); return null;
        }
        RuntimePlatform.data(mob).putUUID(TrailsParties.OWNER, player.getUUID());
        RuntimePlatform.data(mob).putBoolean(FOLLOW, true);
        mob.setPersistenceRequired(); mob.setDeltaMovement(Vec3.ZERO); mob.fallDistance = 0;
        if (mob instanceof TamableAnimal pet) { pet.setOrderedToSit(false); pet.setInSittingPose(false); }
        if (!level.addFreshEntity(mob)) return null;
        storage.consume(ticket);
        // Replace the hand slot too: creative-mode use must not restore the consumed ticket's count.
        stack.setCount(0); player.setItemInHand(hand, ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
        message(player, mob.getName().getString() + " 已放出并跟随；蹲下空手右键可再次收纳。");
        return mob;
    }
    private static void message(ServerPlayer player, String text) { player.displayClientMessage(Component.literal(text), true); }
    private TrailsCompanions() {}
}
