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
    private static int bootTicks;
    private static CameraType previous;
    private static net.minecraft.world.phys.Vec3 moveStart, moveDirection, stoppedAt;
    private static net.minecraft.world.phys.Vec3 previousFrame;
    private static int motionFrames, stillFrames;
    private static double largestFrameStep;
    private static boolean sectorWorld, rectangleWorld;
    static void afterWorld() {
        if (stage == 12 && elapsed == 10 && !sectorWorld) { screenshot("at-qa-sector-world.png"); sectorWorld = true; }
        if (stage == 13 && elapsed == 10 && !rectangleWorld) { screenshot("at-qa-rectangle-world.png"); rectangleWorld = true; }
    }
    static void frame(net.minecraft.world.phys.Vec3 camera) {
        if (stage != 7 || elapsed < 10 || elapsed > 25) return;
        if (previousFrame != null) {
            double step = previousFrame.distanceTo(camera); motionFrames++;
            if (step < 1e-5) stillFrames++;
            largestFrameStep = Math.max(largestFrameStep, step);
        }
        previousFrame = camera;
    }
    static void tick() {
        if (com.matuvent.mineturn.platform.RuntimePlatform.production()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            if (++bootTicks % 100 == 0) MineTurn.LOGGER.info("QA waiting for world, screen={}", mc.screen == null ? "none" : mc.screen.getClass().getName());
            if (bootTicks > 600) throw new IllegalStateException("QA world did not start");
            return;
        }
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
            if (elapsed > 24) { mc.getToasts().clear(); TrailsClient.preview = false; TrailsClient.send("toggle", -1, 0, 0, 0); stage = 2; elapsed = 0; }
        } else if (stage == 2 && TrailsClient.active()) {
            if (elapsed == 16) screenshot("at-qa-02-cut-in.png");
            if (elapsed > 42) {
                if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK || !(mc.screen instanceof TrailsScreen))
                    throw new IllegalStateException("Battle camera/screen did not activate");
                screenshot("at-qa-03-battle.png");
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0); stage = 12; elapsed = 0;
            }
        } else if (stage == 12 && elapsed > 14) {
                if (!(mc.screen instanceof TrailsScreen screen) || screen.previewShape() == null
                        || screen.previewShape().kind() != com.matuvent.mineturn.battle.TrailsArea.Kind.SECTOR || TrailsClient.view.cp() != 150)
                    throw new IllegalStateException("Sword preview committed an action or failed to show a sector");
                screenshot("at-qa-09-sector.png");
                moveStart = mc.player.position();
                mc.screen.mouseClicked(100, 100, 1);
                mc.screen.mouseDragged(140, 100, 1, 40, 0);
                mc.screen.mouseReleased(140, 100, 1);
                double yaw = Math.toRadians(BattleClient.yaw);
                moveDirection = new net.minecraft.world.phys.Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 0, 0); stage = 7; elapsed = 0;
        } else if (stage == 7 && elapsed > 38) {
            MineTurn.LOGGER.info("QA_MOTION: frames={} stationary={} largestStep={}", motionFrames, stillFrames, largestFrameStep);
            if (motionFrames < 10 || stillFrames > motionFrames * .4) throw new IllegalStateException("Walking camera still advances in visible tick-sized steps");
            mc.screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_W, 0, 0);
            var delta = mc.player.position().subtract(moveStart).multiply(1, 0, 1);
            if (delta.length() < 3.5 || delta.length() > TrailsClient.view.movement() + .05 || delta.normalize().dot(moveDirection) < .98)
                throw new IllegalStateException("Camera-relative W or circle boundary failed: " + delta);
            screenshot("at-qa-07-movement.png"); stage = 8; elapsed = 0;
        } else if (stage == 8) {
            if (elapsed == 8) { stoppedAt = mc.player.position(); mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0); mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_3, 0, 0); }
            if (elapsed > 20) {
                if (mc.player.position().distanceTo(stoppedAt) > .02 || !TrailsClient.view.primary().equals("minecraft:crossbow"))
                    throw new IllegalStateException("Key release / live primary weapon selection failed");
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER, 0, 0); stage = 13; elapsed = 0;
            }
        } else if (stage == 13 && elapsed > 12) {
            if (!(mc.screen instanceof TrailsScreen screen) || screen.previewShape().kind() != com.matuvent.mineturn.battle.TrailsArea.Kind.RECTANGLE || TrailsClient.view.cp() != 150)
                throw new IllegalStateException("Crossbow did not preview a rectangular strip before firing");
            screenshot("at-qa-10-rectangle.png");
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE, 0, 0); mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_O, 0, 0); stage = 14; elapsed = 0;
        } else if (stage == 14) {
            if (elapsed == 10) {
                if (!(mc.screen instanceof TrailsConfigScreen)) throw new IllegalStateException("Config screen did not open");
                var boxes = mc.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox)
                        .map(c -> (net.minecraft.client.gui.components.EditBox)c).toList();
                boxes.get(0).setValue("14"); boxes.get(1).setValue("7"); boxes.get(2).setValue("4");
                mc.screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.Button b && b.getMessage().getString().equals("保存并应用"))
                        .map(c -> (net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow().onPress();
            }
            if (elapsed > 25) {
                if (TrailsClient.settings.encounter() != 14 || TrailsClient.radius != 14) throw new IllegalStateException("Server did not save/synchronize settings");
                screenshot("at-qa-11-settings.png"); mc.screen.onClose(); stage = 15; elapsed = 0;
            }
        } else if (stage == 15 && elapsed > 8) {
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_E, 0, 0); stage = 9; elapsed = 0;
        } else if (stage == 9) {
            if (elapsed == 8) {
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen)) throw new IllegalStateException("E did not open vanilla inventory");
                mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, 9, 2,
                        net.minecraft.world.inventory.ClickType.SWAP, mc.player);
            }
            if (elapsed > 20) {
                if (!mc.player.getMainHandItem().is(Items.DIAMOND_SWORD) || !TrailsClient.view.primary().equals("minecraft:diamond_sword"))
                    throw new IllegalStateException("Actual inventory packet did not equip/update primary weapon");
                screenshot("at-qa-08-inventory.png"); mc.screen.onClose(); stage = 10; elapsed = 0;
            }
        } else if (stage == 10 && elapsed > 10) {
            if (!(mc.screen instanceof TrailsScreen)) throw new IllegalStateException("Battle screen did not resume after inventory");
            stoppedAt = mc.player.position(); mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0); stage = 11; elapsed = 0;
        } else if (stage == 11 && elapsed > 10) {
            mc.screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_S, 0, 0);
            if (mc.player.position().distanceTo(stoppedAt) < .6) throw new IllegalStateException("Movement did not resume after inventory / sequence reset");
            mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_I, 0, 0); stage = 3; elapsed = 0;
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
                mc.screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_O, 0, 0);
                TrailsClient.send("toggle", -1, 0, 0, 0); stage = 5; elapsed = 0;
            }
        } else if (stage == 5 && elapsed > 15) {
            if (BattleClient.active() || mc.options.getCameraType() != previous)
                throw new IllegalStateException("Exit failed to restore camera / release screen");
            if (mc.screen instanceof TrailsConfigScreen) mc.screen.onClose();
            if (mc.screen instanceof TrailsScreen) throw new IllegalStateException("Config returned to a closed battle screen");
            screenshot("at-qa-06-restored.png");
            MineTurn.LOGGER.info("TRAILS_ENCOUNTER_SMOKE_PASSED: avatar VS, sector/rectangle confirmation previews, saved config GUI, smooth camera, WASD, weapon/inventory, items, S-Break and restored camera");
            stage = 6;
        } else if (stage == 6 && elapsed > 35) {
            if (System.getProperty("mineturn.qaTag", "").startsWith("weapons")) TrailsWeaponSmoke.tick(); else if(System.getProperty("mineturn.qaTag", "").startsWith("bestiary"))TrailsBestiarySmoke.tick();else if(System.getProperty("mineturn.qaTag", "").startsWith("critical"))TrailsCriticalSmoke.tick();else mc.stop();
        }
        if (elapsed > 400 && stage != 6) throw new IllegalStateException("Encounter smoke timed out at stage " + stage);
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
        inv.setItem(9, new ItemStack(Items.DIAMOND_SWORD));
        p.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
        com.matuvent.mineturn.platform.RuntimePlatform.data(p).putInt("mineturn.trails.cp", 150);
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 1200, 1));
        p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 1200, 1));
        for (int i = 0; i < 2; i++) {
            var zombie = EntityType.ZOMBIE.create(level); zombie.moveTo(2.5 + i * 2, -59, 1.5 + i * 2, 180, 0);
            zombie.setNoAi(true); zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); zombie.setHealth(100); level.addFreshEntity(zombie);
        }
        var pig = EntityType.PIG.create(level); pig.moveTo(-2.5, -59, 1.5, 0, 0); pig.setNoAi(true); level.addFreshEntity(pig);
        var sheep = EntityType.SHEEP.create(level); sheep.moveTo(.5, -59, 3.5, 0, 0); sheep.setNoAi(true); level.addFreshEntity(sheep);
        var wolf = EntityType.WOLF.create(level); wolf.moveTo(-1.5, -59, -2.5, 0, 0); wolf.setNoAi(true); level.addFreshEntity(wolf);
        var chicken = EntityType.CHICKEN.create(level); chicken.moveTo(-1.5, -59, 2.5, 0, 0); chicken.setNoAi(true); level.addFreshEntity(chicken);
        prepared = true;
    }
}
