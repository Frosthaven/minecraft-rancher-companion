package dev.frosthaven.ranchercompanion.mixin;

import dev.frosthaven.ranchercompanion.trait.Effects;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Patchwork: just before an item breaks, it may be restored to 25% durability instead */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
    @Inject(method = "hurtAndBreak(ILnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"), cancellable = true)
    private void rancher$patch(int damage, ServerLevel level, LivingEntity holder, Consumer<Item> onBreak, CallbackInfo ci) {
        if (Effects.patch((ItemStack) (Object) this, holder)) ci.cancel();
    }
}
