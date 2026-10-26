package com.matuvent.mineturn.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;

/** Loader boundary for native extensions used by shared combat rules. */
public final class RuntimePlatform {
    public static net.minecraft.world.food.FoodProperties food(ItemStack stack,LivingEntity entity){return stack.getFoodProperties(entity);}
    public static net.minecraft.world.phys.AABB sweep(ItemStack stack,Player player,LivingEntity target){return stack.getSweepHitBox(player,target);}
    public static boolean canDestroy(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,LivingEntity entity){return state.canEntityDestroy(level,pos,entity);}
    public static boolean extinguishingFluid(LivingEntity entity){return entity.isInFluidType((type,height)->entity.canFluidExtinguish(type));}
    public static CompoundTag data(Entity entity){return entity.getPersistentData();}
    public static java.nio.file.Path configDir(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();}
    public static boolean production(){return net.neoforged.fml.loading.FMLLoader.isProduction();}
    public static boolean canSend(net.minecraft.server.level.ServerPlayer player,net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type<?> type){return player.connection.hasChannel(type);}
    public static void send(net.minecraft.server.level.ServerPlayer player,net.minecraft.network.protocol.common.custom.CustomPacketPayload payload){net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,payload);}
    public static void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload... payloads){for(var payload:payloads)net.neoforged.neoforge.network.PacketDistributor.sendToServer(payload);}
    public static boolean attack(Player player,Entity target){return net.neoforged.neoforge.common.CommonHooks.onPlayerAttackTarget(player,target);}
    public static boolean swordSweep(ItemStack stack){return stack.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SWORD_SWEEP);}
    public static boolean water(FluidState state){return state.getFluidType()==net.neoforged.neoforge.common.NeoForgeMod.WATER_TYPE.value();}
    public static void breathe(LivingEntity entity,int consume,int refill){net.neoforged.neoforge.common.CommonHooks.onLivingBreathe(entity,consume,refill);}
    public static boolean grief(net.minecraft.server.level.ServerLevel level,Entity entity){return net.neoforged.neoforge.event.EventHooks.canEntityGrief(level,entity);}
    public static boolean destroy(LivingEntity entity,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return net.neoforged.neoforge.event.EventHooks.onEntityDestroyBlock(entity,pos,state);}
    public static boolean lightning(Entity entity,net.minecraft.world.entity.LightningBolt bolt){return net.neoforged.neoforge.event.EventHooks.onEntityStruckByLightning(entity,bolt);}
    public static java.util.Map<net.minecraft.resources.ResourceLocation,? extends java.util.Collection<net.minecraft.core.Holder<net.minecraft.world.entity.EntityType<?>>>> entityTags(net.minecraft.server.ReloadableServerResources resources){return resources.getConditionContext().getAllTags(net.minecraft.core.registries.Registries.ENTITY_TYPE);}
    public static TeleportResult pearl(net.minecraft.server.level.ServerPlayer player,double x,double y,double z,net.minecraft.world.entity.projectile.ThrownEnderpearl pearl,float damage,net.minecraft.world.phys.HitResult hit){
        var e=net.neoforged.neoforge.event.EventHooks.onEnderPearlLand(player,x,y,z,pearl,damage,hit);
        return new TeleportResult(e.isCanceled(),e.getTarget(),e.getAttackDamage());
    }
    public static TeleportResult chorus(LivingEntity entity,double x,double y,double z){
        var e=net.neoforged.neoforge.event.EventHooks.onChorusFruitTeleport(entity,x,y,z);
        return new TeleportResult(e.isCanceled(),e.getTarget(),0);
    }
    private RuntimePlatform(){}
}
