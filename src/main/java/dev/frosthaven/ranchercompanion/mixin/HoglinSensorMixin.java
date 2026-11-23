package dev.frosthaven.ranchercompanion.mixin;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.sensing.HoglinSpecificSensor;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hoglin Ward (spec, Traits): a hoglin that finds no warped fungus near takes a drekki with the trait for one. */
@Mixin(HoglinSpecificSensor.class)
public abstract class HoglinSensorMixin {
    @Inject(method = "findNearestRepellent", at = @At("RETURN"), cancellable = true)
    private void rancher$hoglinWard(ServerLevel level, Hoglin hoglin, CallbackInfoReturnable<Optional<BlockPos>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            Optional<BlockPos> ward = dev.frosthaven.ranchercompanion.trait.RoamTraits.ward(level, hoglin);
            if (ward.isPresent()) cir.setReturnValue(ward);
        }
    }
}
