package dev.frosthaven.ranchercompanion.mixin;

import dev.frosthaven.ranchercompanion.item.BardingItem;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 0.6.0: every saved copy of a legacy Fledgling barding becomes the one barding of its tier when a dedicated server reads
 * it (chests, barrels, backpacks and shulker boxes inside them, item frames, worn by a drekki, the Ranch roster): every
 * stack passes this constructor. Same result as {@link ItemStack#transmuteCopy}: the new item's defaults with the stack's
 * own changes (dyes, enchantments, wear). The client is left alone, so a new client on a server one release behind
 * keeps the server's items; there the player's inventory and a drekki's slot still convert on touch.
 */
@Mixin(ItemStack.class)
abstract class LegacyBardingStackMixin {
    @Shadow @Final @Mutable private Item item;
    @Shadow @Final @Mutable PatchedDataComponentMap components;

    @Inject(method = "<init>(Lnet/minecraft/world/level/ItemLike;ILnet/minecraft/core/component/PatchedDataComponentMap;)V", at = @At("RETURN"))
    private void rancher$oneBarding(ItemLike like, int count, PatchedDataComponentMap map, CallbackInfo ci) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER || this.item == null) return;
        Item now = BardingItem.successor(this.item);
        if (now == null) return;
        this.item = now;
        this.components = PatchedDataComponentMap.fromPatch(now.components(), this.components.asPatch());
    }
}
