package com.matuvent.mineturn.client;

import com.matuvent.mineturn.MineTurn;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;

/** Developer-only real-client exercise. It refuses to alter any save except the named QA copy. */
final class TrailsSmoke {
    private static int stage, elapsed;
    private static volatile boolean prepared;
    private static CameraType previous;
    static void tick() {
        if (net.neoforged.fml.loading.FMLLoader.isProduction()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        if (stage == 0) {
            var server = mc.getSingleplayerServer();
            String qaWorld = System.getProperty("mineturn.qaWorld", "AT-QA-20261023");
            if (!qaWorld.matches("AT-QA-[A-Za-z0-9-]+")) throw new IllegalStateException("Invalid QA save name");
            Path expected = mc.gameDirectory.toPath().resolve("saves/" + qaWorld).toAbsolutePath().normalize();
            Path actual = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            if (!actual.equals(expected)) throw new IllegalStateException("Refusing smoke setup outside QA save: " + actual);
            previous = mc.options.getCameraType();
            mc.options.guiScale().set(2);
            server.execute(() -> setup(server)); stage = 1; elapsed = 0;
        }
        if (!prepared) return;
        if (stage == 1 && mc.screen != null) return;
        elapsed++;
        if (stage == 1) {
            TrailsClient.preview = true;
            if (elapsed == 16) screenshot("at-qa-01-circle.png");
            if (elapsed > 24) { TrailsClient.preview = false; TrailsClient.send("toggle", -1, 0, 0, 0); stage = 2; elapsed = 0; }
        } else if (stage == 2 && TrailsClient.active()) {
            if (elapsed == 8) screenshot("at-qa-02-cut-in.png");
            if (elapsed > 42) {
                if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || !(mc.screen instanceof TrailsScreen))
                    throw new IllegalStateException("Battle camera/screen did not activate");
                screenshot("at-qa-03-battle.png");
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_I, 0, 0); stage = 3; elapsed = 0;
            }
        } else if (stage == 3) {
            if (elapsed == 8) screenshot("at-qa-04-items.png");
            if (elapsed > 20) {
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0);
                int target = TrailsClient.fighters.stream().filter(f -> f.enemy()).findFirst().orElseThrow().id();
                TrailsClient.send("burst", target, 0, 0, 0); stage = 4; elapsed = 0;
            }
        } else if (stage == 4) {
            if (elapsed == 8) screenshot("at-qa-05-s-break.png");
            if (elapsed > 45) {
                if (!TrailsClient.active() || TrailsClient.view.cp() != 50 || !TrailsClient.view.alliedTurn())
                    throw new IllegalStateException("S-Break did not update CP / preserve allied control");
                TrailsClient.send("toggle", -1, 0, 0, 0); stage = 5; elapsed = 0;
            }
        } else if (stage == 5 && elapsed > 15) {
            if (BattleClient.active() || mc.options.getCameraType() != previous)
                throw new IllegalStateException("Exit failed to restore camera / release screen");
            screenshot("at-qa-06-restored.png");
            MineTurn.LOGGER.info("TRAILS_ENCOUNTER_SMOKE_PASSED: actual network entry, third-person GUI, S-Break CP and exit/camera restoration");
            stage = 6;
        } else if (stage == 6 && elapsed > 35) mc.stop();
        if (elapsed > 400 && stage < 6) throw new IllegalStateException("Encounter smoke timed out at stage " + stage);
    }
    private static void screenshot(String file) {
        var mc = Minecraft.getInstance();
        String versioned = file.replace(".png", "-" + System.getProperty("mineturn.qaTag", "v1") + ".png");
        Screenshot.grab(mc.gameDirectory, versioned, mc.getMainRenderTarget(), message -> MineTurn.LOGGER.info("QA screenshot: {}", message.getString()));
    }
    private static void setup(MinecraftServer server) {
        var level = server.overworld();
        var p = server.getPlayerList().getPlayers().getFirst();
        p.setGameMode(GameType.SURVIVAL);
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            level.setBlock(new BlockPos(x, -60, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            for (int y = -59; y < -53; y++) level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        p.teleportTo(.5, -59, .5); p.setYRot(0); p.setHealth(20);
        var inv = p.getInventory();
        inv.setItem(0, new ItemStack(Items.IRON_SWORD)); inv.setItem(1, new ItemStack(Items.BOW));
        inv.setItem(2, new ItemStack(Items.CROSSBOW)); inv.setItem(3, new ItemStack(Items.MACE));
        inv.setItem(4, new ItemStack(Items.ARROW, 32)); inv.setItem(5, new ItemStack(Items.WIND_CHARGE, 16));
        inv.setItem(6, new ItemStack(Items.BREAD, 8));
        var potion = new ItemStack(Items.POTION); potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.SWIFTNESS));
        inv.setItem(7, potion); inv.setItem(8, new ItemStack(Items.IRON_AXE)); inv.selected = 0;
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        p.getPersistentData().putInt("mineturn.trails.cp", 150);
        for (int i = 0; i < 2; i++) {
            var zombie = EntityType.ZOMBIE.create(level); zombie.moveTo(2.5 + i * 2, -59, 1.5 + i * 2, 180, 0);
            zombie.setNoAi(true); zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); zombie.setHealth(100); level.addFreshEntity(zombie);
        }
        var pig = EntityType.PIG.create(level); pig.moveTo(-2.5, -59, 1.5, 0, 0); pig.setNoAi(true); level.addFreshEntity(pig);
        var sheep = EntityType.SHEEP.create(level); sheep.moveTo(.5, -59, 3.5, 0, 0); sheep.setNoAi(true); level.addFreshEntity(sheep);
        var wolf = EntityType.WOLF.create(level); wolf.moveTo(-1.5, -59, -2.5, 0, 0); wolf.setNoAi(true); level.addFreshEntity(wolf);
        prepared = true;
    }
}
