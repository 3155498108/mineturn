package com.matuvent.mineturn.client;

import com.google.gson.*;
import com.matuvent.mineturn.MineTurn;
import java.nio.file.*;

/** Client-only preferences, independent from the server's balance/range configuration. */
public final class TrailsPresentationOptions {
    public static boolean camera = true, numbers = true, shake = false, effects = true;
    private static Path path() { return com.matuvent.mineturn.platform.RuntimePlatform.configDir().resolve("mineturn-presentation.json"); }
    public static void load() {
        try {
            if (!Files.exists(path())) return;
            var json = JsonParser.parseString(Files.readString(path())).getAsJsonObject();
            if (json.has("camera")) camera = json.get("camera").getAsBoolean();
            if (json.has("numbers")) numbers = json.get("numbers").getAsBoolean();
            if (json.has("effects")) effects = json.get("effects").getAsBoolean();
            if (json.has("shake")) shake = json.get("shake").getAsBoolean();
        } catch (Exception e) { MineTurn.LOGGER.warn("Cannot read AT presentation preferences", e); }
    }
    static void toggle(String option) {
        boolean oldCamera = camera, oldNumbers = numbers, oldShake = shake, oldEffects = effects;
        switch (option) { case "camera" -> camera = !camera; case "numbers" -> numbers = !numbers; case "shake" -> shake = !shake; case "effects" -> effects = !effects; default -> throw new IllegalArgumentException(option); }
        try {
            var p = path(); var json = Files.exists(p) ? JsonParser.parseString(Files.readString(p)).getAsJsonObject() : new JsonObject();
            json.addProperty("camera", camera); json.addProperty("numbers", numbers); json.addProperty("shake", shake); json.addProperty("effects", effects);
            Files.createDirectories(p.getParent());
            var temporary = Files.createTempFile(p.getParent(), "mineturn-presentation-", ".tmp");
            try {
                Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(json));
                try { Files.move(temporary, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, p, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
        } catch (Exception e) { camera = oldCamera; numbers = oldNumbers; shake = oldShake; effects = oldEffects; MineTurn.LOGGER.warn("Cannot save AT presentation preferences", e); }
    }
    private TrailsPresentationOptions() {}
}
