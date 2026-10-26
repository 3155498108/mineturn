package com.matuvent.mineturn.platform;

import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

/** Payload schema and handlers are common; each loader supplies transport and main-thread dispatch. */
public interface PayloadRegistrar {
    record Context(Player player,PacketFlow flow){}
    <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler);
    <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler);
    default <T extends CustomPacketPayload> void playBidirectional(CustomPacketPayload.Type<T> type,StreamCodec<? super RegistryFriendlyByteBuf,T> codec,BiConsumer<T,Context> handler){playToClient(type,codec,handler);playToServer(type,codec,handler);}
}
