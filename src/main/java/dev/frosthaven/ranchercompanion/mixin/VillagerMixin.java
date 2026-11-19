package dev.frosthaven.ranchercompanion.mixin;

import dev.frosthaven.ranchercompanion.trait.Effects;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Creature Comforts: +75 reputation near the drekki, without writing gossip */
@Mixin(Villager.class)
abstract class VillagerMixin {
    @Inject(method = "getPlayerReputation", at = @At("RETURN"), cancellable = true)
    private void rancher$comforts(Player p, CallbackInfoReturnable<Integer> cir) {
        int add = Effects.comforts((Villager) (Object) this);
        if (add != 0) cir.setReturnValue(cir.getReturnValue() + add);
    }
}
