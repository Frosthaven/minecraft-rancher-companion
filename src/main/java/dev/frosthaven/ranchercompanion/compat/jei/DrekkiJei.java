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
 * JEI (optional; JEI loads this class itself, nothing else touches it): every barding item (both stages) and every plain
 * bag with each of the 16 dyes, as crafting recipes to look up: the dye left of the item colors its main part, right of
 * it its trim (spec, Dyeing). Display only: the real recipe is DyeRecipe, which blends as leather does. Bags that are
 * Sophisticated backpacks show Sophisticated's own dye recipe instead.
 */
@JeiPlugin
public final class DrekkiJei implements IModPlugin {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "jei");

    @Override
    public ResourceLocation getPluginUid() { return ID; }

    @Override
    public void registerRecipes(IRecipeRegistration reg) {
        List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
        List<net.minecraft.world.item.Item> items = new ArrayList<>();
        DrekkiItems.BARDING.values().forEach(i -> items.add(i.get()));
        DrekkiItems.BAGS.forEach(i -> { if (i.get() instanceof dev.frosthaven.ranchercompanion.item.BagItem) items.add(i.get()); });
        for (var item : items) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
            for (DyeColor c : DyeColor.values())
                for (boolean trim : new boolean[]{false, true}) {
                    ItemStack dyed = new ItemStack(item);
                    int rgb = c.getTextureDiffuseColor() & 0xFFFFFF;
                    dev.frosthaven.ranchercompanion.item.Dyeing.set(dyed, trim ? -1 : rgb, trim ? rgb : -1);
                    var pattern = net.minecraft.world.item.crafting.ShapedRecipePattern.of(
                        java.util.Map.of('I', Ingredient.of(item), 'D', Ingredient.of(DyeItem.byColor(c))), List.of(trim ? "ID" : "DI"));
                    out.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "jei/dye_" + id + "_" + c.getName() + (trim ? "_trim" : "_main")),
                        new net.minecraft.world.item.crafting.ShapedRecipe("rancher_companion_dye", CraftingBookCategory.EQUIPMENT, pattern, dyed)));
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
