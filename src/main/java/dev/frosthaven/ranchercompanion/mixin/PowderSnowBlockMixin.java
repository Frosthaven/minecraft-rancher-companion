package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.PowderSnowBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Frost Strider (spec, Traits): its owner walks on powder snow within 10 blocks of the drekki, as in leather boots. */
@Mixin(PowderSnowBlock.class)
public abstract class PowderSnowBlockMixin {
    @Inject(method = "canEntityWalkOnPowderSnow", at = @At("RETURN"), cancellable = true)
    private static void rancher$frostStrider(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && dev.frosthaven.ranchercompanion.trait.RoamTraits.near(entity, dev.frosthaven.ranchercompanion.trait.Trait.FROST_STRIDER)) cir.setReturnValue(true);
    }
}
