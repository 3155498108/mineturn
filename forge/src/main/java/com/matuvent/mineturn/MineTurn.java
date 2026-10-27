package com.matuvent.mineturn;

import com.matuvent.mineturn.battle.*;
import com.matuvent.mineturn.compat.DataComponentType;
import com.matuvent.mineturn.data.CombatItem;
import com.matuvent.mineturn.platform.ForgePayloads;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(MineTurn.MODID)
public final class MineTurn {
    public static final String MODID = "mineturn";
    public static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(MODID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    private static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final Attribute AGILITY = attribute("agility", 100, 1, 1000);
    public static final Attribute MAIN_ACTIONS = attribute("main_actions", 1, 0, 100);
    public static final Attribute BONUS_ACTIONS = attribute("bonus_actions", 1, 0, 100);
    public static final RegistryObject<EntityType<BattleBullet>> BATTLE_BULLET = ENTITIES.register("shulker_bullet_unit",
            () -> EntityType.Builder.of(BattleBullet::new, MobCategory.MISC).sized(.3f, .3f).noSave().noSummon().clientTrackingRange(8).updateInterval(1).build("mineturn:shulker_bullet_unit"));
    public static final RegistryObject<EntityType<BattleDevice>> BATTLE_DEVICE = ENTITIES.register("field_core",
            () -> EntityType.Builder.of(BattleDevice::new, MobCategory.MISC).sized(.75f, .75f).noSave().noSummon().clientTrackingRange(8).build("mineturn:field_core"));
    public static final RegistryObject<Item> COMPANION_TOKEN = ITEMS.register("companion_token", CompanionTokenItem::new);
    public static final java.util.function.Supplier<DataComponentType<CombatItem>> COMBAT = () -> Components.COMBAT;

    private static final class Components {
        static final DataComponentType<CombatItem> COMBAT = new DataComponentType<>("mineturn:combat", CombatItem.CODEC);
    }

    private static Attribute attribute(String name, double value, double min, double max) {
        Attribute attribute = new RangedAttribute("attribute.mineturn." + name, value, min, max).setSyncable(true);
        ATTRIBUTES.register(name, () -> attribute);
        return attribute;
    }

    public MineTurn() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITIES.register(modBus);
        ATTRIBUTES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) -> {
            event.put(BATTLE_BULLET.get(), BattleBullet.attributes().build());
            event.put(BATTLE_DEVICE.get(), BattleDevice.attributes().build());
        });
        modBus.addListener((EntityAttributeModificationEvent event) -> event.getTypes().forEach(type -> {
            event.add(type, AGILITY);
            event.add(type, MAIN_ACTIONS);
            event.add(type, BONUS_ACTIONS);
        }));
        ForgePayloads.initialize();
        MinecraftForge.EVENT_BUS.addListener((ServerStartingEvent event) -> TrailsConfig.load());
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> BattleManager.stopping(event.getServer()));
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> { BattleManager.stopped(event.getServer()); TrailsTeams.stop(event.getServer()); });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                BattleManager.tick();
                if (!System.getProperty("mineturn.multiplayerSmoke", "").isEmpty()) TrailsMultiplayerScenario.tick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> BattleManager.logout(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent event) -> BattleManager.changedDimension(event.getEntity()));
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> { BattleManager.commands(event.getDispatcher()); TrailsTeams.commands(event.getDispatcher()); });
    }
}
