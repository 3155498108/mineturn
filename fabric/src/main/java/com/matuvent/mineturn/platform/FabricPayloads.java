package com.matuvent.mineturn.platform;

import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Fabric codecs are registered on both sides; receivers always run game logic on the game thread. */
public final class FabricPayloads implements PayloadRegistrar {
    public record ClientBinding<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,BiConsumer<T,Context> handler){}
    public static final java.util.List<ClientBinding<?>> CLIENT_RECEIVERS=new java.util.ArrayList<>();
    @Override public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){
        PayloadTypeRegistry.playS2C().register(type,codec);
        CLIENT_RECEIVERS.add(new ClientBinding<>(type,handler));
    }
    @Override public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){
        PayloadTypeRegistry.playC2S().register(type,codec);
        ServerPlayNetworking.registerGlobalReceiver(type,(p,c)->c.server().execute(()->handler.accept(p,new Context(c.player(),PacketFlow.SERVERBOUND))));
    }
}
