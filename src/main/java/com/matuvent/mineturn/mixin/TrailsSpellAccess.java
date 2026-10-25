package com.matuvent.mineturn.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.SpellcasterIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Byte flag is needed because vanilla's spell enum is protected; client animation use only. */
@Mixin(SpellcasterIllager.class)
public interface TrailsSpellAccess {
    @Accessor("DATA_SPELL_CASTING_ID")
    static EntityDataAccessor<Byte> mineturn$spellData(){throw new AssertionError();}
}
