package com.matuvent.mineturn.platform;

import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class NeoForgePayloads implements PayloadRegistrar {
    private final net.neoforged.neoforge.network.registration.PayloadRegistrar registrar;
    public NeoForgePayloads(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent event,String version){registrar=event.registrar(version);}
    @Override public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){registrar.playToClient(type,codec,(p,c)->handler.accept(p,new Context(c.player(),c.flow())));}
    @Override public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){registrar.playToServer(type,codec,(p,c)->handler.accept(p,new Context(c.player(),c.flow())));}
    @Override public <T extends CustomPacketPayload> void playBidirectional(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){registrar.playBidirectional(type,codec,(p,c)->handler.accept(p,new Context(c.player(),c.flow())));}
}
