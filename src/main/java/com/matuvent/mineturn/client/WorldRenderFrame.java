package com.matuvent.mineturn.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;

/** Loader-neutral snapshots of the matrices and camera for the current render stage. */
public record WorldRenderFrame(Stage stage,Camera camera,Matrix4f modelView,Matrix4f projection,DeltaTracker partialTick) {
    public enum Stage { AFTER_TRANSLUCENT_BLOCKS, AFTER_PARTICLES }
    public Stage getStage(){return stage;}
    public Camera getCamera(){return camera;}
    public Matrix4f getModelViewMatrix(){return modelView;}
    public Matrix4f getProjectionMatrix(){return projection;}
    public DeltaTracker getPartialTick(){return partialTick;}
}
