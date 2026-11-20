package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.look.DyeMix;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;

/**
 * Two-color dyeing for barding and plain bags (spec, Dyeing), the rule Sophisticated Backpacks uses: one dyeable item
 * and dyes; only the column counts (DyeMix). A Sophisticated bag is dyed by Sophisticated's own recipe.
 */
public final class DyeRecipe extends CustomRecipe {
    public DyeRecipe(CraftingBookCategory category) { super(category); }

    @Override
    public boolean matches(CraftingInput in, Level level) {
        int items = 0, dyes = 0;
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.isEmpty()) continue;
            if (Dyeing.ours(s)) items++;
            else if (s.is(Tags.Items.DYES) && DyeColor.getColor(s) != null) dyes++;
            else return false;
        }
        return items == 1 && dyes > 0;
    }

    @Override
    public ItemStack assemble(CraftingInput in, HolderLookup.Provider reg) {
        ItemStack item = ItemStack.EMPTY; int column = 0; List<int[]> dyes = new ArrayList<>();
        for (int i = 0; i < in.size(); i++) {
            ItemStack s = in.getItem(i);
            if (s.isEmpty()) continue;
            if (Dyeing.ours(s)) { item = s; column = i % in.width(); }
            else { DyeColor c = DyeColor.getColor(s); if (c == null) return ItemStack.EMPTY; dyes.add(new int[]{i % in.width(), c.getTextureDiffuseColor() & 0xFFFFFF}); }
        }
        if (item.isEmpty()) return ItemStack.EMPTY;
        DyeMix.Split split = DyeMix.split(dyes, column);
        ItemStack out = item.copyWithCount(1);
        Dyeing.set(out, DyeMix.mix(Dyeing.main(item), split.main()), DyeMix.mix(Dyeing.trim(item), split.trim()));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) { return w * h >= 2; }

    @Override
    public RecipeSerializer<?> getSerializer() { return DrekkiRecipes.DYE.get(); }
}
