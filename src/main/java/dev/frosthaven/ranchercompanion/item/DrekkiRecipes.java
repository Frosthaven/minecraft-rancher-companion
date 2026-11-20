package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's recipe types: two-color dyeing, and a shaped recipe that keeps a bag's data. */
public final class DrekkiRecipes {
    private DrekkiRecipes() {}

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, RancherCompanion.ID);

    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<DyeRecipe>> DYE =
        SERIALIZERS.register("dye", () -> new SimpleCraftingRecipeSerializer<>(DyeRecipe::new));
    public static final DeferredHolder<RecipeSerializer<?>, KeepShapedRecipe.Serializer> KEEP_SHAPED =
        SERIALIZERS.register("keep_shaped", KeepShapedRecipe.Serializer::new);

    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
}
