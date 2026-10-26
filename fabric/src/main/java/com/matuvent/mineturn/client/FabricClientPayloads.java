package com.matuvent.mineturn.client;

import com.matuvent.mineturn.platform.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Keep LocalPlayer and all client callbacks out of dedicated-server class verification. */
final class FabricClientPayloads {
    static void initialize(){FabricPayloads.CLIENT_RECEIVERS.forEach(FabricClientPayloads::register);}
    private static <T extends CustomPacketPayload> void register(FabricPayloads.ClientBinding<T> binding){ClientPlayNetworking.registerGlobalReceiver(binding.type(),(payload,context)->context.client().execute(()->binding.handler().accept(payload,new PayloadRegistrar.Context(context.player(),PacketFlow.CLIENTBOUND))));}
}
