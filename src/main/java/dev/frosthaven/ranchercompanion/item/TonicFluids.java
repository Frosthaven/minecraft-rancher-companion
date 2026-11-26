package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * The tonics as fluids, for Create (Shane, 2026-11-26): a heated Basin brews 1000 mB of a tonic from Awkward potion
 * fluid and its ingredient, as Create brews potions; a Spout fills a glass bottle with 250 mB into the tonic, an Item
 * Drain empties it back (data/rancher_companion/recipe/{mixing,filling,emptying}). Each has a fluid block so it can lie
 * and flow in the world, but no bucket: nothing a player has places it.
 */
public final class TonicFluids {
    private TonicFluids() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, RancherCompanion.ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, RancherCompanion.ID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RancherCompanion.ID);

    /** one tonic's fluid: its type (its color for the client), source, flowing and block */
    public record Tonic(String name, int color, DeferredHolder<FluidType, FluidType> type, DeferredHolder<Fluid, FlowingFluid> source,
                        DeferredHolder<Fluid, FlowingFluid> flowing, DeferredHolder<Block, LiquidBlock> block) {}

    public static final Tonic STARLIGHT = tonic("starlight_tonic", 0xFF9A6AE0);  /* the purple in its bottle (textures/item/starlight_tonic.png) */
    public static final Tonic HEIRLOOM = tonic("heirloom_tonic", 0xFFE8B830);  /* the gold in its bottle */
    /** the Potion of Horizons as a fluid (Shane, 2026-11-26): Create's brewing and Item Drain give this, not Create's
     *  potion fluid (mixin/CreatePotionFluidMixin); the teal at the bottom of its bottle */
    public static final Tonic HORIZONS = tonic("horizons", 0xFF2A9A86);
    public static final java.util.List<Tonic> ALL = java.util.List.of(STARLIGHT, HEIRLOOM, HORIZONS);

    private static Tonic tonic(String name, int color) {
        /* as haven-compat's Bleach (water-like, no new sources), without its harm: a tonic neither hurts nor nauseates */
        var type = TYPES.register(name, () -> new FluidType(FluidType.Properties.create().descriptionId("fluid_type." + RancherCompanion.ID + "." + name)
            .canSwim(true).canDrown(true).canExtinguish(true).supportsBoating(true).canConvertToSource(false).fallDistanceModifier(0F)
            .motionScale(0.014D).density(1000).viscosity(1000)
            .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
            .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));
        @SuppressWarnings("unchecked") BaseFlowingFluid.Properties[] props = new BaseFlowingFluid.Properties[1];
        DeferredHolder<Fluid, FlowingFluid> source = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(props[0]));
        DeferredHolder<Fluid, FlowingFluid> flowing = FLUIDS.register(name + "_flowing", () -> new BaseFlowingFluid.Flowing(props[0]));
        DeferredHolder<Block, LiquidBlock> block = BLOCKS.register(name, () -> new LiquidBlock(source.get(),
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER).mapColor(MapColor.COLOR_PURPLE)));
        props[0] = new BaseFlowingFluid.Properties(type, source, flowing).block(block).slopeFindDistance(4).levelDecreasePerBlock(1)
            .tickRate(5).explosionResistance(100F);
        return new Tonic(name, color, type, source, flowing, block);
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
    }
}
