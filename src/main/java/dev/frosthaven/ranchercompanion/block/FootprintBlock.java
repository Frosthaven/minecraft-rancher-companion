package dev.frosthaven.ranchercompanion.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.ranchercompanion.trail.Trails;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Drekki Footprints (spec, Trails): a 1 px plate, clear but for two prints, that faces the way its placer looks. Drekki
 * that live at a hatchery follow a line of them as a trail (trail/). No collision box, like Straw Bedding. Each one
 * placed or removed is told to the level's trail graph, which knows the trail where no chunk is loaded.
 */
public class FootprintBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<FootprintBlock> CODEC = simpleCodec(FootprintBlock::new);
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 1, 16);

    public FootprintBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return Shapes.empty(); }

    /* like a carpet: it needs a block under it, and goes when that goes */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return !level.isEmptyBlock(pos.below()); }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        return !state.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (level instanceof ServerLevel sl) Trails.get(sl).putPrint(pos, state.getValue(FACING));
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        if (level instanceof ServerLevel sl && !now.is(this)) Trails.get(sl).removePrint(pos);
        super.onRemove(state, level, pos, now, moved);
    }
}
