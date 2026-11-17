package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.look.LeatherDye;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's blocks (design page, Blocks tab): the functional ones (hatchery, trough) and the decor set (planks, beam,
 * tufted leather in every dye, shingles, straw bedding, fence and gate, with their slabs and stairs). Their items are made
 * in DrekkiItems from {@link #ITEM_ORDER}, so they share the tab and keep this order.
 */
public final class DrekkiBlocks {
    private DrekkiBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RancherCompanion.ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RancherCompanion.ID);

    /** every block in the creative tab's order */
    public static final List<DeferredBlock<? extends Block>> ITEM_ORDER = new ArrayList<>();

    public static final DeferredBlock<HatcheryBlock> HATCHERY = add("hatchery", HatcheryBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<TroughBlock> TROUGH = add("trough", TroughBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion());

    public static final DeferredBlock<Block> PLANKS = add("rancher_planks", Block::new, () -> copy(Blocks.DARK_OAK_PLANKS));
    public static final DeferredBlock<SlabBlock> PLANKS_SLAB = add("rancher_planks_slab", SlabBlock::new, () -> copy(Blocks.DARK_OAK_SLAB));
    public static final DeferredBlock<StairBlock> PLANKS_STAIRS = add("rancher_planks_stairs",
        p -> new StairBlock(PLANKS.get().defaultBlockState(), p), () -> copy(Blocks.DARK_OAK_STAIRS));
    public static final DeferredBlock<RotatedPillarBlock> BEAM = add("rancher_beam", RotatedPillarBlock::new, () -> copy(Blocks.DARK_OAK_LOG));
    public static final DeferredBlock<BeamSlabBlock> BEAM_SLAB = add("rancher_beam_slab", BeamSlabBlock::new, () -> copy(Blocks.DARK_OAK_SLAB));
    public static final DeferredBlock<Block> SHINGLES = add("rancher_shingles", Block::new, () -> copy(Blocks.DEEPSLATE_TILES));
    public static final DeferredBlock<SlabBlock> SHINGLES_SLAB = add("rancher_shingles_slab", SlabBlock::new, () -> copy(Blocks.DEEPSLATE_TILE_SLAB));
    public static final DeferredBlock<StairBlock> SHINGLES_STAIRS = add("rancher_shingles_stairs",
        p -> new StairBlock(SHINGLES.get().defaultBlockState(), p), () -> copy(Blocks.DEEPSLATE_TILE_STAIRS));
    public static final DeferredBlock<StrawBeddingBlock> STRAW_BEDDING = add("straw_bedding", StrawBeddingBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.1f).sound(SoundType.GRASS).noOcclusion());
    public static final DeferredBlock<FenceBlock> FENCE = add("rancher_fence", FenceBlock::new, () -> copy(Blocks.DARK_OAK_FENCE));
    public static final DeferredBlock<FenceGateBlock> FENCE_GATE = add("rancher_fence_gate",
        p -> new FenceGateBlock(WoodType.DARK_OAK, p), () -> copy(Blocks.DARK_OAK_FENCE_GATE));

    /** Tufted Leather and its slab in every color: index 0 is undyed, then the 16 dyes (LeatherDye's order) */
    public static final List<DeferredBlock<Block>> TUFTED = new ArrayList<>();
    public static final List<DeferredBlock<SlabBlock>> TUFTED_SLAB = new ArrayList<>();
    /** the leather color of every tinted block, by block, for the color handlers */
    public static final Map<DeferredBlock<? extends Block>, Integer> TUFTED_COLOR = new HashMap<>();
    static {
        for (int i = 0; i < LeatherDye.COUNT; i++) {
            String name = i == 0 ? "tufted_leather" : LeatherDye.NAMES[i - 1] + "_tufted_leather";
            var block = add(name, Block::new, () -> copy(Blocks.WHITE_WOOL));
            TUFTED.add(block);
            TUFTED_COLOR.put(block, i);
        }
        for (int i = 0; i < LeatherDye.COUNT; i++) {
            String name = (i == 0 ? "tufted_leather" : LeatherDye.NAMES[i - 1] + "_tufted_leather") + "_slab";
            var slab = add(name, SlabBlock::new, () -> copy(Blocks.WHITE_WOOL));
            TUFTED_SLAB.add(slab);
            TUFTED_COLOR.put(slab, i);
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TroughBlockEntity>> TROUGH_ENTITY = BLOCK_ENTITIES.register("trough",
        () -> BlockEntityType.Builder.of(TroughBlockEntity::new, TROUGH.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HatcheryBlockEntity>> HATCHERY_ENTITY = BLOCK_ENTITIES.register("hatchery",
        () -> BlockEntityType.Builder.of(HatcheryBlockEntity::new, HATCHERY.get()).build(null));

    private static BlockBehaviour.Properties copy(Block b) { return BlockBehaviour.Properties.ofFullCopy(b); }

    private static <B extends Block> DeferredBlock<B> add(String id, Function<BlockBehaviour.Properties, B> make, Supplier<BlockBehaviour.Properties> props) {
        DeferredBlock<B> block = BLOCKS.register(id, () -> make.apply(props.get()));
        ITEM_ORDER.add(block);
        return block;
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
