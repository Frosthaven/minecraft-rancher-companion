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

    /* bedding is a footprint trail's rest stop: the level's trail graph knows where it lies (trail/Trails) */
    @Override
    protected void onPlace(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof net.minecraft.server.level.ServerLevel sl) dev.frosthaven.ranchercompanion.trail.Trails.get(sl).putBed(pos);
    }

    @Override
    protected void onRemove(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, BlockState now, boolean moved) {
        if (level instanceof net.minecraft.server.level.ServerLevel sl && !now.is(this)) dev.frosthaven.ranchercompanion.trail.Trails.get(sl).removeBed(pos);
        super.onRemove(state, level, pos, now, moved);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }
}
