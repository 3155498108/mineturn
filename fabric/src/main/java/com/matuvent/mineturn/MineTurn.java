package com.matuvent.mineturn;

import net.fabricmc.api.ModInitializer;
import com.matuvent.mineturn.battle.*;
import com.matuvent.mineturn.data.CombatItem;
import com.matuvent.mineturn.platform.FabricPayloads;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;

/** Fabric loader entry point; shared combat and platform hooks are registered here. */
public final class MineTurn implements ModInitializer {
    public static final String MODID="mineturn";
    public static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger(MODID);
    public static final Holder.Reference<Attribute> AGILITY=attribute("agility",100,1,1000);
    public static final Holder.Reference<Attribute> MAIN_ACTIONS=attribute("main_actions",1,0,100);
    public static final Holder.Reference<Attribute> BONUS_ACTIONS=attribute("bonus_actions",1,0,100);
    public static final java.util.function.Supplier<EntityType<BattleBullet>> BATTLE_BULLET=registered("shulker_bullet_unit",EntityType.Builder.of(BattleBullet::new,MobCategory.MISC).sized(.3f,.3f).noSave().noSummon().clientTrackingRange(8).updateInterval(1).build("mineturn:shulker_bullet_unit"));
    public static final java.util.function.Supplier<EntityType<BattleDevice>> BATTLE_DEVICE=registered("field_core",EntityType.Builder.of(BattleDevice::new,MobCategory.MISC).sized(.75f,.75f).noSave().noSummon().clientTrackingRange(8).build("mineturn:field_core"));
    public static final java.util.function.Supplier<DataComponentType<CombatItem>> COMBAT=()->Components.COMBAT;
    public static final java.util.function.Supplier<net.minecraft.world.item.Item> COMPANION_TOKEN=()->Components.COMPANION_TOKEN;
    private static final class Components {
        static final DataComponentType<CombatItem> COMBAT=Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,id("combat"),DataComponentType.<CombatItem>builder().persistent(CombatItem.CODEC).build());
        static final net.minecraft.world.item.Item COMPANION_TOKEN=Registry.register(BuiltInRegistries.ITEM,id("companion_token"),new CompanionTokenItem());
    }
    private static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath(MODID,path);}
    private static Holder.Reference<Attribute> attribute(String path,double value,double min,double max){return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE,id(path),new RangedAttribute("attribute.mineturn."+path,value,min,max).setSyncable(true));}
    private static <T extends Entity> java.util.function.Supplier<EntityType<T>> registered(String path,EntityType<T> type){Registry.register(BuiltInRegistries.ENTITY_TYPE,id(path),type);return ()->type;}
    @Override public void onInitialize(){
        COMBAT.get();
        FabricDefaultAttributeRegistry.register(BATTLE_BULLET.get(),BattleBullet.attributes());
        FabricDefaultAttributeRegistry.register(BATTLE_DEVICE.get(),BattleDevice.attributes());
        var payloads=new FabricPayloads();
        com.matuvent.mineturn.network.BattleNetwork.register(payloads);
        com.matuvent.mineturn.network.TrailsNetwork.register(payloads);
        ServerLifecycleEvents.SERVER_STARTING.register(server->TrailsConfig.load());
        ServerLifecycleEvents.SERVER_STOPPING.register(BattleManager::stopping);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{BattleManager.stopped(server);TrailsTeams.stop(server);});
        ServerTickEvents.END_SERVER_TICK.register(server->{BattleManager.tick();if(!System.getProperty("mineturn.multiplayerSmoke","").isEmpty())TrailsMultiplayerScenario.tick(server);});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->BattleManager.logout(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player,origin,destination)->BattleManager.changedDimension(player));
        CommandRegistrationCallback.EVENT.register((dispatcher,access,environment)->{BattleManager.commands(dispatcher);TrailsTeams.commands(dispatcher);});
    }
}
