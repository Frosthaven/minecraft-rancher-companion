package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Mending on barding takes its share of an orb before the owner gets the rest (item/BardingEnchants). */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccess {
    @Accessor("value")
    void rancher$setValue(int value);
}
