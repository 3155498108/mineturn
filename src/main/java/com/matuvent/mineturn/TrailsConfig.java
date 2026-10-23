package com.matuvent.mineturn;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.fml.loading.FMLPaths;

/** Editable prototype balance; no entity-count limit is imposed. */
public final class TrailsConfig {
    public static TrailsConfig current = new TrailsConfig();
    public double encounterRadius = 12;
    public double verticalRange = 6;
    public double backstabSeconds = 6;
    public double ambushHealthFraction = .25;
    public double elytraSpeed = 30;
    public double speedPotionPerLevel = .20;
    public int cpPerAction = 15;
    public int cpOnDamage = 8;
    public int burstCost = 100;
    public double foodHealingPerNutrition = .5;
    public Map<String, Double> bootsSpeed = new LinkedHashMap<>(Map.of(
            "minecraft:leather_boots", 12., "minecraft:chainmail_boots", 10.,
            "minecraft:iron_boots", 8., "minecraft:golden_boots", 20.,
            "minecraft:diamond_boots", 14., "minecraft:netherite_boots", 16.));
    public Map<String, Double> foodHealing = new LinkedHashMap<>(Map.of(
            "minecraft:bread", 4., "minecraft:cooked_beef", 6., "minecraft:golden_apple", 8.));
    public Map<String, Integer> foodHunger = new LinkedHashMap<>();
    public Map<String, Double> foodSaturation = new LinkedHashMap<>();

    public static void load() {
        var path = FMLPaths.CONFIGDIR.get().resolve("mineturn-trails.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(path)) {
                var loaded = gson.fromJson(Files.readString(path), TrailsConfig.class);
                if (loaded == null || !Double.isFinite(loaded.encounterRadius) || loaded.encounterRadius < 2
                        || loaded.encounterRadius > 64 || !Double.isFinite(loaded.verticalRange) || loaded.verticalRange < 1
                        || loaded.bootsSpeed == null || loaded.foodHealing == null || loaded.foodHunger == null
                        || loaded.foodSaturation == null || loaded.burstCost < 1
                        || loaded.burstCost > 200 || !Double.isFinite(loaded.ambushHealthFraction)
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
