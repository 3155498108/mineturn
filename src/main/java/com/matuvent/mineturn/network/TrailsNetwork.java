package com.matuvent.mineturn.network;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import com.matuvent.mineturn.battle.TrailsCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Roster chunks keep encounters independent of an arbitrary 32-member UI/packet limit. */
public final class TrailsNetwork {
    public static final UUID NONE = new UUID(0, 0);
    public static Consumer<View> receiveView = ignored -> {};
    public static Consumer<Roster> receiveRoster = ignored -> {};
    public static Consumer<Cue> receiveCue = ignored -> {};

    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("trails-2");
        r.playToServer(Command.TYPE, Command.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) TrailsCombat.request(player, p);
        });
        r.playToClient(View.TYPE, View.CODEC, (p, ctx) -> receiveView.accept(p));
        r.playToClient(Roster.TYPE, Roster.CODEC, (p, ctx) -> receiveRoster.accept(p));
        r.playToClient(Cue.TYPE, Cue.CODEC, (p, ctx) -> receiveCue.accept(p));
        r.playToServer(MoveInput.TYPE, MoveInput.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) TrailsCombat.input(player, p);
        });
    }

    public record Command(UUID battle, long revision, String operation, int target, int slot, double x, double z)
            implements CustomPacketPayload {
        public static final Type<Command> TYPE = new Type<>(ResourceLocation.parse("mineturn:trails_command"));
        public static final StreamCodec<FriendlyByteBuf, Command> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.battle); b.writeVarLong(p.revision); b.writeUtf(p.operation, 24);
            b.writeVarInt(p.target); b.writeVarInt(p.slot); b.writeDouble(p.x); b.writeDouble(p.z);
        }, b -> new Command(b.readUUID(), b.readVarLong(), b.readUtf(24), b.readVarInt(), b.readVarInt(), b.readDouble(), b.readDouble()));
        @Override public Type<Command> type() { return TYPE; }
    }

    /** Directional intent, not client positions. Turn and sequence reject delayed/reordered input. */
    public record MoveInput(UUID battle, long turn, long sequence, double x, double z) implements CustomPacketPayload {
        public static final Type<MoveInput> TYPE = new Type<>(ResourceLocation.parse("mineturn:trails_move_input"));
        public static final StreamCodec<FriendlyByteBuf, MoveInput> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.battle); b.writeVarLong(p.turn); b.writeVarLong(p.sequence); b.writeDouble(p.x); b.writeDouble(p.z);
        }, b -> new MoveInput(b.readUUID(), b.readVarLong(), b.readVarLong(), b.readDouble(), b.readDouble()));
        @Override public Type<MoveInput> type() { return TYPE; }
    }

    public record Fighter(int id, String name, boolean enemy, float hp, float maxHp, double spd, double at, String skill) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(id); b.writeUtf(name, 256); b.writeBoolean(enemy); b.writeFloat(hp); b.writeFloat(maxHp);
            b.writeDouble(spd); b.writeDouble(at); b.writeUtf(skill, 64);
        }
        static Fighter read(FriendlyByteBuf b) {
            return new Fighter(b.readVarInt(), b.readUtf(256), b.readBoolean(), b.readFloat(), b.readFloat(), b.readDouble(), b.readDouble(), b.readUtf(64));
        }
    }

    public record View(UUID battle, long revision, int actor, boolean alliedTurn, int cp, int burstCost,
                       String primary, String burstName, double movement, long turn, net.minecraft.world.phys.Vec3 origin,
                       String message, List<BattleNetwork.QueueEntry> queue)
            implements CustomPacketPayload {
        public static final Type<View> TYPE = new Type<>(ResourceLocation.parse("mineturn:trails_view"));
        public static final StreamCodec<FriendlyByteBuf, View> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.battle); b.writeVarLong(p.revision); b.writeVarInt(p.actor); b.writeBoolean(p.alliedTurn);
            b.writeVarInt(p.cp); b.writeVarInt(p.burstCost); b.writeUtf(p.primary, 256); b.writeUtf(p.burstName, 64);
            b.writeDouble(p.movement); b.writeVarLong(p.turn); b.writeVec3(p.origin); b.writeUtf(p.message, 1024);
            b.writeCollection(p.queue, (out, q) -> q.write(out));
        }, b -> new View(b.readUUID(), b.readVarLong(), b.readVarInt(), b.readBoolean(), b.readVarInt(), b.readVarInt(),
                b.readUtf(256), b.readUtf(64), b.readDouble(), b.readVarLong(), b.readVec3(), b.readUtf(1024),
                b.readCollection(FriendlyByteBuf.limitValue(java.util.ArrayList::new, 32), BattleNetwork.QueueEntry::read)));
        @Override public Type<View> type() { return TYPE; }
    }

    public record Roster(UUID battle, long revision, int page, boolean last, List<Fighter> fighters) implements CustomPacketPayload {
        public static final Type<Roster> TYPE = new Type<>(ResourceLocation.parse("mineturn:trails_roster"));
        public static final StreamCodec<FriendlyByteBuf, Roster> CODEC = StreamCodec.of((b, p) -> {
            b.writeUUID(p.battle); b.writeVarLong(p.revision); b.writeVarInt(p.page); b.writeBoolean(p.last);
            b.writeCollection(p.fighters, (out, f) -> f.write(out));
        }, b -> new Roster(b.readUUID(), b.readVarLong(), b.readVarInt(), b.readBoolean(),
                b.readCollection(FriendlyByteBuf.limitValue(java.util.ArrayList::new, 48), Fighter::read)));
        @Override public Type<Roster> type() { return TYPE; }
    }

    /** kind: ready = breathing ambush hint, enter/burst = original procedural cut-in. */
    public record Cue(String kind, String text, int ticks, double radius) implements CustomPacketPayload {
        public static final Type<Cue> TYPE = new Type<>(ResourceLocation.parse("mineturn:trails_cue"));
        public static final StreamCodec<FriendlyByteBuf, Cue> CODEC = StreamCodec.of((b, p) -> {
            b.writeUtf(p.kind, 24); b.writeUtf(p.text, 128); b.writeVarInt(p.ticks); b.writeDouble(p.radius);
        }, b -> new Cue(b.readUtf(24), b.readUtf(128), b.readVarInt(), b.readDouble()));
        @Override public Type<Cue> type() { return TYPE; }
    }
}
