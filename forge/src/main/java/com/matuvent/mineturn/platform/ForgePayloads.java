package com.matuvent.mineturn.platform;

import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.compat.CustomPacketPayload;
import com.matuvent.mineturn.compat.StreamCodec;
import com.matuvent.mineturn.network.BattleNetwork;
import com.matuvent.mineturn.network.TrailsNetwork;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** One versioned Forge channel carries the existing, direction-specific payload schemas. */
public final class ForgePayloads implements PayloadRegistrar {
    private static final String PROTOCOL = "trails-11-cn-1";
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineTurn.MODID, "combat"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static final Map<ResourceLocation, Binding<?>> TO_CLIENT = new HashMap<>();
    private static final Map<ResourceLocation, Binding<?>> TO_SERVER = new HashMap<>();
    private static volatile Supplier<Player> clientPlayer = () -> null;

    private record Binding<T extends CustomPacketPayload>(StreamCodec<? super FriendlyByteBuf, T> codec, BiConsumer<T, Context> handler) { }
    private record Envelope(ResourceLocation type, byte[] bytes) { }

    public static void initialize() {
        ForgePayloads registrar = new ForgePayloads();
        BattleNetwork.register(registrar);
        TrailsNetwork.register(registrar);
        CHANNEL.registerMessage(0, Envelope.class,
                (message, buffer) -> { buffer.writeResourceLocation(message.type()); buffer.writeByteArray(message.bytes()); },
                buffer -> new Envelope(buffer.readResourceLocation(), buffer.readByteArray(MAX_PACKET_BYTES)),
                ForgePayloads::receive);
    }

    @Override public <T extends CustomPacketPayload> void playToClient(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, BiConsumer<T, Context> handler) {
        register(TO_CLIENT, type.id(), new Binding<>(codec, handler));
    }
    @Override public <T extends CustomPacketPayload> void playToServer(CustomPacketPayload.Type<T> type, StreamCodec<? super FriendlyByteBuf, T> codec, BiConsumer<T, Context> handler) {
        register(TO_SERVER, type.id(), new Binding<>(codec, handler));
    }
    private static void register(Map<ResourceLocation, Binding<?>> bindings, ResourceLocation id, Binding<?> binding) {
        if (bindings.putIfAbsent(id, binding) != null) throw new IllegalStateException("Duplicate payload: " + id);
    }
    public static void setClientPlayer(Supplier<Player> supplier) { clientPlayer = supplier; }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return TO_CLIENT.containsKey(type.id()) && player.connection != null && CHANNEL.isRemotePresent(player.connection.connection);
    }
    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), encode(payload, TO_CLIENT));
    }
    public static void sendToServer(CustomPacketPayload payload) { CHANNEL.sendToServer(encode(payload, TO_SERVER)); }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Envelope encode(CustomPacketPayload payload, Map<ResourceLocation, Binding<?>> bindings) {
        Binding binding = bindings.get(payload.type().id());
        if (binding == null) throw new IllegalArgumentException("Unregistered payload: " + payload.type().id());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            binding.codec().encode(buffer, payload);
            if (buffer.readableBytes() > MAX_PACKET_BYTES) throw new IllegalArgumentException("Payload exceeds size limit");
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.readBytes(bytes);
            return new Envelope(payload.type().id(), bytes);
        } finally { buffer.release(); }
    }

    private static void receive(Envelope message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context network = supplier.get();
        network.enqueueWork(() -> {
            boolean serverbound = network.getDirection() == NetworkDirection.PLAY_TO_SERVER;
            boolean clientbound = network.getDirection() == NetworkDirection.PLAY_TO_CLIENT;
            if (!serverbound && !clientbound) return;
            Binding<?> binding = (serverbound ? TO_SERVER : TO_CLIENT).get(message.type());
            Player player = serverbound ? network.getSender() : clientPlayer.get();
            if (binding == null || player == null) return;
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(message.bytes()));
            try {
                dispatch(binding, buffer, new Context(player, serverbound ? PacketFlow.SERVERBOUND : PacketFlow.CLIENTBOUND));
            } catch (RuntimeException error) {
                MineTurn.LOGGER.warn("Rejected malformed MineTurn payload {}", message.type(), error);
            } finally { buffer.release(); }
        });
        network.setPacketHandled(true);
    }
    private static <T extends CustomPacketPayload> void dispatch(Binding<T> binding, FriendlyByteBuf buffer, Context context) {
        T payload = binding.codec().decode(buffer);
        if (buffer.isReadable()) throw new IllegalArgumentException("Unexpected trailing payload bytes");
        binding.handler().accept(payload, context);
    }
}
