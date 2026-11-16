package dev.frosthaven.ranchercompanion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Straw Bedding: a 1 px layer of loose straw, like a carpet but with no collision box (it is no ceiling and no floor). */
public class StrawBeddingBlock extends CarpetBlock {
    public StrawBeddingBlock(Properties props) {
        super(props);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }
}
