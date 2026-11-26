package dev.frosthaven.ranchercompanion.mixin;

import dev.frosthaven.ranchercompanion.item.DrekkiPotions;
import dev.frosthaven.ranchercompanion.item.TonicFluids;
import net.minecraft.world.item.alchemy.PotionContents;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Potion of Horizons as its own fluid in Create (Shane, 2026-11-26). Create makes every potion fluid here: its Basin
 * recipes made from brewing (Twilight + Emerald) and its Item Drain both call it. A drinkable Horizons gives our
 * rancher_companion:horizons fluid instead of create:potion, so it has its own color and flows in the world; the Spout
 * bottles it through data/rancher_companion/recipe/filling/horizons.json. Splash and lingering stay Create's.
 * Skipped without Create (@Pseudo).
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.fluids.potion.PotionFluidHandler", remap = false)
public abstract class CreatePotionFluidMixin {
    @Inject(method = "getFluidFromPotion", at = @At("HEAD"), cancellable = true, require = 0)
    private static void rancher$horizons(PotionContents contents, com.simibubi.create.content.fluids.potion.PotionFluid.BottleType bottle, int amount,
                                         CallbackInfoReturnable<FluidStack> cir) {
        if (bottle == com.simibubi.create.content.fluids.potion.PotionFluid.BottleType.REGULAR && contents.is(DrekkiPotions.HORIZONS))
            cir.setReturnValue(new FluidStack(TonicFluids.HORIZONS.source().get(), amount));
    }
}
