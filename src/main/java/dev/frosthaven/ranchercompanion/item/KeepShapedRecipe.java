package dev.frosthaven.ranchercompanion.item;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;

/**
 * A shaped recipe whose result keeps the data of the drekki bag in the grid (spec, Accessory: bags): a satchel made
 * into a saddlebag, a saddlebag ringed by ingots into the next tier, keeps its contents, its dyes and (with
 * Sophisticated) its upgrades, as Sophisticated's own backpack tiers do. Written like a shaped recipe.
 */
public final class KeepShapedRecipe extends ShapedRecipe {
    KeepShapedRecipe(ShapedRecipe r) { super(r.getGroup(), r.category(), r.pattern, r.getResultItem(null), r.showNotification()); }

    @Override
    public ItemStack assemble(CraftingInput in, HolderLookup.Provider reg) {
        ItemStack out = super.assemble(in, reg);
        for (int i = 0; i < in.size(); i++)
            if (in.getItem(i).getItem() instanceof DrekkiBag) { out.applyComponents(in.getItem(i).getComponentsPatch()); break; }
        return out;
    }

    @Override public RecipeSerializer<?> getSerializer() { return DrekkiRecipes.KEEP_SHAPED.get(); }

    public static final class Serializer implements RecipeSerializer<KeepShapedRecipe> {
        private static final MapCodec<KeepShapedRecipe> CODEC = RecipeSerializer.SHAPED_RECIPE.codec().xmap(KeepShapedRecipe::new, k -> k);
        private static final StreamCodec<RegistryFriendlyByteBuf, KeepShapedRecipe> STREAM =
            RecipeSerializer.SHAPED_RECIPE.streamCodec().map(KeepShapedRecipe::new, k -> k);
        @Override public MapCodec<KeepShapedRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, KeepShapedRecipe> streamCodec() { return STREAM; }
    }
}
