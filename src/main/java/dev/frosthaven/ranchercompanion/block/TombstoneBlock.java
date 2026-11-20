package dev.frosthaven.ranchercompanion.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A drekki's tombstone (spec, Tombstone). Right-click opens it (anyone may take the gear). It cannot be mined while it
 * holds anything; mined empty, it drops itself with the drekki's name and date.
 */
public final class TombstoneBlock extends BaseEntityBlock {
    public static final MapCodec<TombstoneBlock> CODEC = simpleCodec(TombstoneBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** the plinth and the stone, along x (facing north or south) and along z */
    private static final VoxelShape X = Shapes.or(box(1, 0, 4, 15, 2, 12), box(3, 2, 6, 13, 15, 10)),
        Z = Shapes.or(box(4, 0, 1, 12, 2, 15), box(6, 2, 3, 10, 15, 13));

    public TombstoneBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TombstoneBlockEntity(pos, state); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? X : Z;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, DrekkiBlocks.TOMBSTONE_ENTITY.get(), TombstoneBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be)
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TombstoneMenu(id, inv, pos), TombstoneMenu.title(be)), buf -> buf.writeBlockPos(pos));
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** not while it holds anything: take the gear first */
    private static boolean full(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof TombstoneBlockEntity be && be.holdsItems();
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return full(level, pos) ? 0 : super.getDestroyProgress(state, player, level, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (full(level, pos)) return false;  /* creative's instant break too */
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** mined empty: itself, with the name and date */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack s = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TombstoneBlockEntity be) s.applyComponents(be.collectComponents());
        return List.of(s);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack s = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof TombstoneBlockEntity be) s.applyComponents(be.collectComponents());
        return s;
    }

    /** an emptied death tombstone goes, with a puff of soul smoke */
    static void vanish(Level level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.25, 0.3, 0.25, 0.02);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8f, 1f);
    }
}
