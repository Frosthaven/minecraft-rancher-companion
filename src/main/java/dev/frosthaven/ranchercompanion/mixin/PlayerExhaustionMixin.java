package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Hibernation Plan (spec, Traits): its owner gets hungry a third slower within 10 blocks of the drekki. */
@Mixin(Player.class)
public abstract class PlayerExhaustionMixin {
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float rancher$hibernation(float exhaustion) {
        return dev.frosthaven.ranchercompanion.trait.RoamTraits.exhaustion((Player) (Object) this, exhaustion);
    }
}
