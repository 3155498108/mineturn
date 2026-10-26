package com.matuvent.mineturn.mixin;

import com.matuvent.mineturn.battle.BattleManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class FabricPickupMixin {
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void mineturn$pickup(Player player,CallbackInfo ci){if(BattleManager.locked(player))ci.cancel();}
}
