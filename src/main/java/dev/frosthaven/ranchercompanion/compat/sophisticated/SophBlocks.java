package dev.frosthaven.ranchercompanion.compat.sophisticated;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.look.Gear;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlock;
import net.p3pp3rf1y.sophisticatedbackpacks.init.ModBlocks;

/**
 * The bags placed in the world with Sophisticated Backpacks (spec, Accessory: bags): Sophisticated's backpack block, so
 * its block entity (storage, screen, upgrades, Create contraptions, breaking into the bag) is the one at work, drawn as
 * our satchel or saddlebag instead of a backpack. One block per bag kind, added to Sophisticated's backpack block entity
 * type (NeoForge's BlockEntityTypeAddBlocksEvent).
 */
public final class SophBlocks {
    private SophBlocks() {}

    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RancherCompanion.ID);
    /** by Gear.BAGS index (1..6; 0 unused) */
    public static final List<DeferredBlock<Placed>> PLACED = new ArrayList<>();
    static {
        PLACED.add(null);
        for (int k = 1; k < Gear.BAGS.length; k++) { int kind = k; PLACED.add(BLOCKS.register("placed_" + Gear.BAGS[k], () -> new Placed(kind))); }
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        bus.addListener(BlockEntityTypeAddBlocksEvent.class, e -> {
            Block[] ours = PLACED.stream().skip(1).map(DeferredBlock::get).toArray(Block[]::new);
            e.modify(ModBlocks.BACKPACK_TILE_TYPE.get(), ours);
        });
    }

    /** a placed bag: Sophisticated's backpack block with the bag's own shape (the model is ours, by blockstate) */
    public static final class Placed extends BackpackBlock {
        public final int kind;
        private static final VoxelShape SATCHEL = box(1, 0, 4, 15, 8, 12), SADDLEBAG = box(0, 0, 3, 16, 11, 13),
            SATCHEL_X = box(4, 0, 1, 12, 8, 15), SADDLEBAG_X = box(3, 0, 0, 13, 11, 16);

        Placed(int kind) { super(); this.kind = kind; }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            boolean x = state.getValue(FACING).getAxis() == Direction.Axis.X, big = kind > 1;
            return big ? (x ? SADDLEBAG_X : SADDLEBAG) : (x ? SATCHEL_X : SATCHEL);
        }
    }
}
