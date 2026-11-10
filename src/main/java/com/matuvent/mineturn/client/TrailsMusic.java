package com.matuvent.mineturn.client;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.matuvent.mineturn.MineTurn;
import com.matuvent.mineturn.battle.TrailsBestiary;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Streams personal OGG files through Minecraft's sound engine and resource packs. */
public final class TrailsMusic {
    private static final List<String> LIBRARIES = List.of("custom", "trails", "p5", "p4");
    private static final List<String> SITUATIONS = List.of("normal", "advantage", "boss");
    static boolean enabled = true;
    static String library = "custom";
    static float volume = .65f;
    private static BattleTrack current;
    private static final List<BattleTrack> fading = new ArrayList<>();
    private static boolean advantage, checkedLocalFiles;
    private static int retryTicks;
    private static String message = "将个人曲目导入后，点击重读本地音乐。";

    public static Path directory() {
        return com.matuvent.mineturn.platform.RuntimePlatform.configDir().resolve("mineturn/music");
    }
    private static Path preferences() { return directory().getParent().resolve("music.json"); }

    static void initialize() {
        try {
            for (String set : LIBRARIES) Files.createDirectories(directory().resolve(set));
            if (!Files.exists(preferences())) { save(); return; }
            if (Files.exists(preferences())) {
                var json = JsonParser.parseString(Files.readString(preferences())).getAsJsonObject();
                if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
                if (json.has("library") && LIBRARIES.contains(json.get("library").getAsString())) library = json.get("library").getAsString();
                if (json.has("volume")) {
                    float value = json.get("volume").getAsFloat();
                    if (Float.isFinite(value)) volume = Math.max(0, Math.min(1, value));
                }
            }
        } catch (Exception exception) { MineTurn.LOGGER.warn("Cannot load battle music preferences", exception); }
    }

    static String label() {
        return switch (library) {
            case "trails" -> "空轨";
            case "p5" -> "P5";
            case "p4" -> "P4";
            default -> "自定义";
        };
    }
    static String status() { return message; }
    static SoundInstance activeTrack() { return current; }
    static void cycle() { library = LIBRARIES.get((LIBRARIES.indexOf(library) + 1) % LIBRARIES.size()); save(); }
    static void save() {
        try {
            var json = new JsonObject();
            json.addProperty("enabled", enabled);
            json.addProperty("library", library);
            json.addProperty("volume", volume);
            Path temporary = Files.createTempFile(preferences().getParent(), "music-", ".tmp");
            try {
                Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json));
                Files.move(temporary, preferences(), StandardCopyOption.REPLACE_EXISTING);
            } finally { Files.deleteIfExists(temporary); }
        } catch (IOException exception) { MineTurn.LOGGER.warn("Cannot save battle music preferences", exception); }
    }

    /** Called on the game thread, on request or once after joining the first world. */
    static void reloadLocal() {
        var minecraft = Minecraft.getInstance();
        try {
            Path pack = minecraft.gameDirectory.toPath().resolve("resourcepacks/mineturn-battle-music");
            var sounds = new JsonObject();
            int count = 0;
            boolean changed = false;
            for (String set : LIBRARIES) for (String situation : SITUATIONS) {
                Path source = directory().resolve(set).resolve(situation + ".ogg");
                Path target = pack.resolve("assets/mineturnmusic/sounds/" + set + "/" + situation + ".ogg");
                if (!Files.isRegularFile(source)) {
                    changed |= Files.deleteIfExists(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                if (!Files.isRegularFile(target) || Files.size(source) != Files.size(target)
                        || !Files.getLastModifiedTime(source).equals(Files.getLastModifiedTime(target))) {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                    changed = true;
                }
                var sound = new JsonObject();
                sound.addProperty("name", "mineturnmusic:" + set + "/" + situation);
                sound.addProperty("stream", true);
                var choices = new JsonArray();
                choices.add(sound);
                var event = new JsonObject();
                event.add("sounds", choices);
                sounds.add(set + "." + situation, event);
                count++;
            }
            if (count == 0 && !Files.isDirectory(pack)) { message = "尚未找到 OGG 曲目，请先导入个人音频。"; return; }
            Path soundsFile = pack.resolve("assets/mineturnmusic/sounds.json");
            if (!Files.exists(soundsFile) || !Files.readString(soundsFile).equals(sounds.toString())) {
                Files.writeString(soundsFile, sounds.toString());
                changed = true;
            }
            var metadata = new JsonObject();
            var packInfo = new JsonObject();
            packInfo.addProperty("pack_format", SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES));
            packInfo.addProperty("description", "MineTurn personal battle music");
            metadata.add("pack", packInfo);
            Path metadataFile = pack.resolve("pack.mcmeta");
            if (!Files.exists(metadataFile) || !Files.readString(metadataFile).equals(metadata.toString())) {
                Files.writeString(metadataFile, metadata.toString());
                changed = true;
            }
            var repository = minecraft.getResourcePackRepository();
            String packId = "file/mineturn-battle-music";
            message = count == 0 ? "尚未找到 OGG 曲目，请先导入个人音频。" : "已导入 " + count + " 首曲目，按战斗类型自动播放。";
            if (!changed && repository.getSelectedIds().contains(packId)) return;
            repository.reload();
            var selected = new ArrayList<>(repository.getSelectedIds());
            if (!selected.contains(packId)) selected.add(packId);
            repository.setSelected(selected);
            minecraft.options.updateResourcePacks(repository);
            stopImmediately();
            minecraft.reloadResourcePacks();
        } catch (IOException | RuntimeException exception) {
            message = "导入失败，请检查音频文件及日志。";
            MineTurn.LOGGER.warn("Cannot import personal battle music", exception);
        }
    }

    static void entered(boolean ambush) { advantage = ambush; }
    static ResourceLocation choose() {
        var minecraft = Minecraft.getInstance();
        boolean boss = TrailsClient.fighters.stream().anyMatch(fighter -> {
            var entity = minecraft.level == null ? null : minecraft.level.getEntity(fighter.id());
            var profile = entity instanceof net.minecraft.world.entity.LivingEntity living ? TrailsBestiary.get(living) : null;
            return fighter.enemy() && profile != null && profile.ultimate() != null;
        });
        String situation = boss ? "boss" : advantage ? "advantage" : "normal";
        for (String candidate : List.of(situation, "normal")) {
            var id = ResourceLocation.fromNamespaceAndPath("mineturnmusic", library + "." + candidate);
            var event = minecraft.getSoundManager().getSoundEvent(id);
            if (event != null && event.getWeight() > 0) return id;
        }
        return null;
    }

    static void tick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) { stopImmediately(); return; }
        if (!checkedLocalFiles && minecraft.getOverlay() == null) {
            checkedLocalFiles = true;
            reloadLocal();
        }
        fading.removeIf(BattleTrack::isStopped);
        boolean requested = enabled && volume > 0 && TrailsClient.active();
        ResourceLocation wanted = requested ? choose() : null;
        if (current != null && (!current.getLocation().equals(wanted) || !requested)) {
            current.fadeOut = true;
            fading.add(current);
            current = null;
            retryTicks = 0;
        }
        if (current == null && wanted != null && retryTicks-- <= 0 && minecraft.getOverlay() == null) {
            current = new BattleTrack(wanted);
            minecraft.getSoundManager().play(current);
            retryTicks = 40;
        }
        if (current != null || !fading.isEmpty()) minecraft.getMusicManager().stopPlaying();
        if (current != null && retryTicks-- <= 0 && !minecraft.getSoundManager().isActive(current)) {
            // Sound reload and device switches may stop a looping stream without a battle ending.
            current = null;
            retryTicks = 40;
        }
    }

    static void stopImmediately() {
        var manager = Minecraft.getInstance().getSoundManager();
        if (current != null) manager.stop(current);
        for (var track : fading) manager.stop(track);
        current = null;
        fading.clear();
        retryTicks = 0;
    }

    private static final class BattleTrack extends AbstractTickableSoundInstance {
        private boolean fadeOut;
        BattleTrack(ResourceLocation id) {
            super(SoundEvent.createVariableRangeEvent(id), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            looping = true;
            relative = true;
            attenuation = SoundInstance.Attenuation.NONE;
            volume = .001f;
        }
        @Override public void tick() {
            float target = fadeOut ? 0 : TrailsMusic.volume;
            volume += Math.max(-.04f, Math.min(.04f, target - volume));
            if (fadeOut && volume <= .001f) stop();
        }
    }

    private TrailsMusic() {}
}
