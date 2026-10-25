package com.matuvent.mineturn;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;

/** Editable prototype balance; no entity-count limit is imposed. */
public final class TrailsConfig {
    public static TrailsConfig current = new TrailsConfig();
    public double encounterRadius = 20;
    public double verticalRange = 6;
    public double movementRadius = 4;
    public double backstabSeconds = 6;
    public double ambushHealthFraction = .25;
    public double elytraSpeed = 30;
    public double speedPotionPerLevel = .20;
    public int cpPerAction = 15;
    public int cpOnDamage = 8;
    public int burstCost = 100;
    public int maxBuffStacks = 10;
    public double criticalChance = .10;
    public double criticalMultiplier = 1.5;
    public double foodHealingPerNutrition = .5;
    public Map<String, Double> bootsSpeed = new LinkedHashMap<>(Map.of(
            "minecraft:leather_boots", 12., "minecraft:chainmail_boots", 10.,
            "minecraft:iron_boots", 8., "minecraft:golden_boots", 20.,
            "minecraft:diamond_boots", 14., "minecraft:netherite_boots", 16.));
    public Map<String, Double> foodHealing = new LinkedHashMap<>(Map.of(
            "minecraft:bread", 4., "minecraft:cooked_beef", 6., "minecraft:golden_apple", 8.));
    public Map<String, Integer> foodHunger = new LinkedHashMap<>();
    public Map<String, Double> foodSaturation = new LinkedHashMap<>();

    public static void saveRanges(double encounter, double vertical, double movement) throws java.io.IOException {
        saveCombat(encounter,vertical,movement,current.criticalChance,current.criticalMultiplier);
    }
    public static void saveCombat(double encounter,double vertical,double movement,double chance,double multiplier) throws java.io.IOException {
        if (!Double.isFinite(encounter) || encounter < 2 || encounter > 64 || !Double.isFinite(vertical) || vertical < 1 || vertical > 64
                || !Double.isFinite(movement) || movement < 0 || movement > 32) throw new IllegalArgumentException("入战半径 2–64，高度 1–64，移动半径 0–32。");
        if(!Double.isFinite(chance) || chance<0 || chance>1 || !Double.isFinite(multiplier) || multiplier<1 || multiplier>10)
            throw new IllegalArgumentException("暴击率 0–100%，暴击倍率 1–10。");
        var path = FMLPaths.CONFIGDIR.get().resolve("mineturn-trails.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var json = Files.exists(path) ? com.google.gson.JsonParser.parseString(Files.readString(path)).getAsJsonObject() : gson.toJsonTree(current).getAsJsonObject();
        json.addProperty("encounterRadius", encounter); json.addProperty("verticalRange", vertical); json.addProperty("movementRadius", movement);
        json.addProperty("criticalChance",chance);json.addProperty("criticalMultiplier",multiplier);
        Files.createDirectories(path.getParent());
        var temporary = Files.createTempFile(path.getParent(), "mineturn-trails-", ".json.tmp");
        try {
            Files.writeString(temporary, gson.toJson(json));
            try { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException unsupported) { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        current.encounterRadius = encounter; current.verticalRange = vertical; current.movementRadius = movement;
        current.criticalChance=chance;current.criticalMultiplier=multiplier;
    }

    public static void load() {
        var path = FMLPaths.CONFIGDIR.get().resolve("mineturn-trails.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                var loaded = gson.fromJson(Files.readString(path), TrailsConfig.class);
                if (loaded == null || !Double.isFinite(loaded.encounterRadius) || loaded.encounterRadius < 2
                        || loaded.encounterRadius > 64 || !Double.isFinite(loaded.verticalRange) || loaded.verticalRange < 1
                        || !Double.isFinite(loaded.movementRadius) || loaded.movementRadius < 0 || loaded.movementRadius > 32
                        || loaded.bootsSpeed == null || loaded.foodHealing == null || loaded.foodHunger == null
                        || loaded.foodSaturation == null || loaded.burstCost < 1
                        || loaded.burstCost > 200 || !Double.isFinite(loaded.ambushHealthFraction)
                        || !Double.isFinite(loaded.criticalChance) || loaded.criticalChance<0 || loaded.criticalChance>1
                        || !Double.isFinite(loaded.criticalMultiplier) || loaded.criticalMultiplier<1 || loaded.criticalMultiplier>10
                        || loaded.ambushHealthFraction < 0 || loaded.ambushHealthFraction >= 1)
                    throw new IllegalArgumentException("Invalid encounter/balance configuration");
                current = loaded;
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, gson.toJson(current));
            }
        } catch (Exception error) {
            MineTurn.LOGGER.error("Cannot load {}; using safe prototype defaults", path, error);
            current = new TrailsConfig();
        }
    }
}
