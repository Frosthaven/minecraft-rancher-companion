package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.item.DrekkiPotions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/**
 * The three brewed potions' own icons (spec, Traits: caps). They are potion types of the game's potion item, which
 * draws one bottle tinted by its effects; with no effects that is the plain blue of an uncraftable potion. The game's
 * potion model is wrapped, not replaced: a bottle of one of the three resolves to its own model, every other potion to
 * what the game (or a resource pack that restyles potions) gives. Splash and lingering bottles keep the game's look.
 */
public final class PotionModels {
    private PotionModels() {}

    private static final String[] NAMES = {"potion_of_distance", "potion_of_twilight", "potion_of_horizons"};

    private static ModelResourceLocation model(int step) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "item/" + NAMES[step - 1]));
    }

    public static void additional(ModelEvent.RegisterAdditional e) {
        for (int step = 1; step <= NAMES.length; step++) e.register(model(step));
    }

    public static void wrap(ModelEvent.ModifyBakingResult e) {
        ModelResourceLocation potion = ModelResourceLocation.inventory(ResourceLocation.withDefaultNamespace("potion"));
        BakedModel base = e.getModels().get(potion);
        if (base == null) return;
        BakedModel[] own = new BakedModel[NAMES.length];
        for (int step = 1; step <= NAMES.length; step++) {
            own[step - 1] = e.getModels().get(model(step));
            if (own[step - 1] == null) return;  /* a model did not load: the game's bottle for all */
        }
        ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
                int step = DrekkiPotions.step(stack);
                return step > 0 ? own[step - 1] : base.getOverrides().resolve(model, stack, level, entity, seed);
            }
        };
        e.getModels().put(potion, new BakedModelWrapper<>(base) {
            @Override
            public ItemOverrides getOverrides() { return overrides; }
        });
    }
}
