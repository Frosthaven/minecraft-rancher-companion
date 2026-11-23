package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Web Cutter (spec, Traits): a cobweb does not hold a drekki with the trait, nor its owner within 10 blocks of it. */
@Mixin(WebBlock.class)
public abstract class WebBlockMixin {
    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void rancher$cutWeb(BlockState state, Level level, BlockPos pos, Entity entity, CallbackInfo ci) {
        if (dev.frosthaven.ranchercompanion.trait.WorldTraits.cutsWebs(entity)) ci.cancel();
    }
}
