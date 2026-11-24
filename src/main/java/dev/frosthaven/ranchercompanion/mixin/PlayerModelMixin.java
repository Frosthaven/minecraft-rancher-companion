package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A drekki's rider holds the reins. Late (priority 2000), so it is the last word after mods that pose players
 *  (Not Enough Animations in the Haven pack let the arms hang). */
@Mixin(value = PlayerModel.class, priority = 2000)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void rancher$riderPose(LivingEntity e, float swing, float amount, float age, float yaw, float pitch, CallbackInfo ci) {
        dev.frosthaven.ranchercompanion.client.RiderSeat.pose((PlayerModel<?>) (Object) this, e);
    }
}
