package com.matuvent.mineturn.mixin;

import net.minecraft.server.ReloadableServerResources;
import net.minecraft.tags.TagManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ReloadableServerResources.class)
public interface FabricReloadResourcesAccess { @Accessor("tagManager") TagManager mineturn$tags(); }
