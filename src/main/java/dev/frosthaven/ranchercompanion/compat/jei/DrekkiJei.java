package dev.frosthaven.ranchercompanion.compat.jei;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

/**
 * JEI (optional; JEI loads this class itself, nothing else touches it): every barding tier with each of the 16 dyes, as
 * crafting recipes to look up. Display only: real dyeing is vanilla's armor dye recipe, which mixes dyes as leather
 * armor does and shows in JEI only as one special recipe.
 */
@JeiPlugin
public final class DrekkiJei implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "jei");

    @Override
    public ResourceLocation getPluginUid() { return ID; }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
        for (Barding tier : Barding.values()) {
            var item = DrekkiItems.BARDING.get(tier).get();
            for (DyeColor c : DyeColor.values()) {
                ItemStack dyed = DyedItemColor.applyDyes(new ItemStack(item), List.of(DyeItem.byColor(c)));
                NonNullList<Ingredient> in = NonNullList.of(Ingredient.EMPTY, Ingredient.of(item), Ingredient.of(DyeItem.byColor(c)));
                out.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "jei/dye_" + tier.id() + "_" + c.getName()),
                    new ShapelessRecipe("rancher_companion_barding_dye", CraftingBookCategory.EQUIPMENT, dyed, in)));
            }
        }
        reg.addRecipes(RecipeTypes.CRAFTING, out);
    }

    /** the self test's check: how many of these JEI holds once it runs */
    @Override
    public void onRuntimeAvailable(mezz.jei.api.runtime.IJeiRuntime rt) {
        if (!Boolean.getBoolean("rancher_companion.selftest")) return;
        long n = rt.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
            .filter(h -> h.id().getNamespace().equals(RancherCompanion.ID) && h.id().getPath().startsWith("jei/dye_")).count();
        RancherCompanion.LOG.info("[selftest] JEI barding dye recipes: {}", n);
    }
}
