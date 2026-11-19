package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hook, Line & Sinker shortens the wait for a bite (trait.Effects) */
@Mixin(FishingHook.class)
public interface FishingHookAccess {
    @Accessor("timeUntilLured") int rancher$timeUntilLured();
    @Accessor("timeUntilLured") void rancher$setTimeUntilLured(int ticks);
    /** ticks left to reel in a bite (the self test reels on it) */
    @Accessor("nibble") int rancher$nibble();
}
