package dev.frosthaven.ranchercompanion.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.ItemLike;

/**
 * The Drekki Lotus (spec, Tombstone: the way back; Shane, 2026-11-26): its seeds plant only on soul soil, and it grows only
 * while a drekki tombstone touches it on one side (north, south, east or west of the crop block, the same level). It grows
 * on the clock ({@link LotusBlockEntity}): a stage a third of a day, unloaded too. Four stages; the last gives a Drekki
 * Lotus Blossom (its loot table), which goes in the tombstone. Light does not matter.
 */
public final class LotusCropBlock extends CropBlock implements net.minecraft.world.level.block.EntityBlock {
    public static final MapCodec<LotusCropBlock> CODEC = simpleCodec(LotusCropBlock::new);
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;

    public LotusCropBlock(Properties p) { super(p); }

    @Override public MapCodec<? extends CropBlock> codec() { return CODEC; }
    @Override protected IntegerProperty getAgeProperty() { return AGE; }
    @Override public int getMaxAge() { return 3; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(AGE); }
    @Override protected ItemLike getBaseSeedId() { return DrekkiItems.LOTUS_SEEDS.get(); }

    @Override protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) { return state.is(Blocks.SOUL_SOIL); }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return mayPlaceOn(level.getBlockState(pos.below()), level, pos.below()); }

    /** a drekki tombstone on one of its four sides */
    public static boolean byTomb(BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {  /* in front of a tombstone or behind it, not at its sides (Shane, 2026-11-26) */
            BlockState t = level.getBlockState(pos.relative(d));
            if (t.getBlock() instanceof TombstoneBlock && t.getValue(TombstoneBlock.FACING).getAxis() == d.getAxis()) return true;
        }
        return false;
    }

    /** growth is the block entity's, on the clock; random ticks do nothing */
    @Override protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {}
    @Override protected boolean isRandomlyTicking(BlockState state) { return false; }

    @Override public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LotusBlockEntity(pos, state); }

    @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state,
            net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        return level.isClientSide || type != DrekkiBlocks.LOTUS_ENTITY.get() ? null
            : (l, p, s, be) -> LotusBlockEntity.serverTick(l, p, s, (LotusBlockEntity) be);
    }

    /** bone meal does nothing to it (Shane, 2026-11-26): it grows on the clock alone */
    @Override public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) { return false; }
    @Override public boolean isBonemealSuccess(Level level, RandomSource r, BlockPos pos, BlockState state) { return false; }
    @Override public void performBonemeal(ServerLevel level, RandomSource r, BlockPos pos, BlockState state) {}
}
