package com.matuvent.mineturn.platform;

import com.matuvent.mineturn.compat.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.FluidState;

/** Forge 1.20.1 boundary for the shared combat implementation. */
public final class RuntimePlatform {
    public static net.minecraft.world.food.FoodProperties food(ItemStack stack,LivingEntity entity){return stack.getItem().getFoodProperties();}
    public static net.minecraft.world.phys.AABB sweep(ItemStack stack,Player player,LivingEntity target){return target.getBoundingBox().inflate(1,.25,1);}
    public static boolean canDestroy(net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,LivingEntity entity){return state.getDestroySpeed(level,pos)>=0;}
    public static boolean extinguishingFluid(LivingEntity entity){return entity.isInWaterOrBubble();}
    public interface PersistentEntity {CompoundTag mineturn$data();}
    public static CompoundTag data(Entity entity){return ((PersistentEntity)entity).mineturn$data();}
    public static java.nio.file.Path configDir(){return net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();}
    public static boolean production(){return net.minecraftforge.fml.loading.FMLEnvironment.production;}
    public static boolean canSend(net.minecraft.server.level.ServerPlayer player,CustomPacketPayload.Type<?> type){return ForgePayloads.canSend(player,type);}
    public static void send(net.minecraft.server.level.ServerPlayer player,CustomPacketPayload payload){ForgePayloads.send(player,payload);}
    public static void sendToServer(CustomPacketPayload... payloads){for(var payload:payloads)ForgePayloads.sendToServer(payload);}
    public static boolean attack(Player player,Entity target){return !target.isSpectator() && target.isAttackable();}
    public static boolean swordSweep(ItemStack stack){return stack.getItem() instanceof net.minecraft.world.item.SwordItem;}
    public static boolean water(FluidState state){return state.is(net.minecraft.tags.FluidTags.WATER);}
    public static void breathe(LivingEntity entity,int consume,int refill){
        boolean submerged=entity.isEyeInFluid(net.minecraft.tags.FluidTags.WATER) && !entity.level().getBlockState(net.minecraft.core.BlockPos.containing(entity.getX(),entity.getEyeY(),entity.getZ())).is(net.minecraft.world.level.block.Blocks.BUBBLE_COLUMN);
        if(submerged){
            if(!entity.canBreatheUnderwater() && !net.minecraft.world.effect.MobEffectUtil.hasWaterBreathing(entity) && !(entity instanceof Player p && p.getAbilities().invulnerable)){
                entity.setAirSupply(entity.getAirSupply()-consume);
                if(entity.getAirSupply()==-20){
                    entity.setAirSupply(0);
                    if(entity.level() instanceof net.minecraft.server.level.ServerLevel level)level.sendParticles(net.minecraft.core.particles.ParticleTypes.BUBBLE,entity.getX(),entity.getY(),entity.getZ(),8,.5,.5,.5,0);
                    entity.hurt(entity.damageSources().drown(),2);
                }
            }
        }else if(entity.getAirSupply()<entity.getMaxAirSupply())entity.setAirSupply(Math.min(entity.getMaxAirSupply(),entity.getAirSupply()+refill));
    }
    public static boolean grief(net.minecraft.server.level.ServerLevel level,Entity entity){return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);}
    public static boolean destroy(LivingEntity entity,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state){return entity.level().getWorldBorder().isWithinBounds(pos);}
    public static boolean lightning(Entity entity,LightningBolt bolt){return false;}
    @SuppressWarnings({"unchecked","rawtypes"})
    public static java.util.Map<net.minecraft.resources.ResourceLocation,? extends java.util.Collection<net.minecraft.core.Holder<net.minecraft.world.entity.EntityType<?>>>> entityTags(net.minecraft.server.ReloadableServerResources resources){
        for(var result:((com.matuvent.mineturn.mixin.FabricReloadResourcesAccess)resources).mineturn$tags().getResult())if(result.key().equals(net.minecraft.core.registries.Registries.ENTITY_TYPE))return (java.util.Map)result.tags();
        return java.util.Map.of();
    }
    public static TeleportResult pearl(net.minecraft.server.level.ServerPlayer player,double x,double y,double z,net.minecraft.world.entity.projectile.ThrownEnderpearl pearl,float damage,net.minecraft.world.phys.HitResult hit){return new TeleportResult(false,new net.minecraft.world.phys.Vec3(x,y,z),damage);}
    public static TeleportResult chorus(LivingEntity entity,double x,double y,double z){return new TeleportResult(false,new net.minecraft.world.phys.Vec3(x,y,z),0);}
    private RuntimePlatform(){}
}
