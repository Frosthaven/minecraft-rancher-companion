package dev.frosthaven.ranchercompanion.mixin;

import dev.frosthaven.ranchercompanion.trait.Effects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Fallen Emperor: piglins treat the owner as if wearing gold, exactly as for gold armor */
@Mixin(PiglinAi.class)
abstract class PiglinAiMixin {
    @Inject(method = "isWearingGold", at = @At("HEAD"), cancellable = true)
    private static void rancher$emperor(LivingEntity e, CallbackInfoReturnable<Boolean> cir) {
        if (Effects.emperor(e)) cir.setReturnValue(true);
    }
}
