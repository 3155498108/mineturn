package com.matuvent.mineturn.battle;

import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/** Entity snapshots stay on the server; copying or editing an item cannot copy a companion. */
public final class CompanionStorage extends SavedData {
    record Entry(UUID owner, CompoundTag entity) {}
    private final Map<UUID, Entry> entries = new HashMap<>();
    public static CompanionStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CompanionStorage::new, CompanionStorage::load, DataFixTypes.LEVEL), "mineturn_companions");
    }
    static CompanionStorage load(CompoundTag tag, HolderLookup.Provider registries) {
        var result = new CompanionStorage();
        for (var value : tag.getList("companions", Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) value;
            if (row.hasUUID("ticket") && row.hasUUID("owner") && row.contains("entity", Tag.TAG_COMPOUND))
                result.entries.put(row.getUUID("ticket"), new Entry(row.getUUID("owner"), row.getCompound("entity").copy()));
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        entries.forEach((ticket, entry) -> {
            var row = new CompoundTag(); row.putUUID("ticket", ticket); row.putUUID("owner", entry.owner());
            row.put("entity", entry.entity().copy()); list.add(row);
        });
        tag.put("companions", list); return tag;
    }
    Entry find(UUID ticket) { return entries.get(ticket); }
    void put(UUID ticket, UUID owner, CompoundTag entity) { entries.put(ticket, new Entry(owner, entity.copy())); setDirty(); }
    void consume(UUID ticket) { entries.remove(ticket); setDirty(); }
}
