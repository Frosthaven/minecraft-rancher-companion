package dev.frosthaven.ranchercompanion.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A plain bag placed in the world (spec, Accessory: bags; without Sophisticated Backpacks, which places its own backpack
 * block instead): sneak + right-click with the bag places it, right-click opens it, breaking it gives the bag back with
 * all it holds, as a shulker box does (also in creative, when it holds anything). KIND is the Gear.BAGS index.
 */
public final class BagBlock extends BaseEntityBlock {
    public static final MapCodec<BagBlock> CODEC = simpleCodec(BagBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 1, 6);
    private static final VoxelShape SATCHEL = box(1, 0, 4, 15, 8, 12), SADDLEBAG = box(0, 0, 3, 16, 11, 13),
        SATCHEL_X = box(4, 0, 1, 12, 8, 15), SADDLEBAG_X = box(3, 0, 0, 13, 11, 16);

    public BagBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, 1));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, KIND); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new BagBlockEntity(pos, state); }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        boolean x = state.getValue(FACING).getAxis() == Direction.Axis.X, big = state.getValue(KIND) > 1;
        return big ? (x ? SADDLEBAG_X : SADDLEBAG) : (x ? SATCHEL_X : SATCHEL);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof BagBlockEntity be)
            dev.frosthaven.ranchercompanion.entity.BagMenu.open(sp, be);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** its drop is the bag it holds, contents and dyes on it */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof BagBlockEntity be && !be.bag().isEmpty()
            ? List.of(be.bag().copy()) : List.of();
    }

    /** in creative, as a shulker box: a bag that holds anything still drops */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof BagBlockEntity be && !be.bag().isEmpty()
            && be.storage() != null && hasAny(be)) popResource(level, pos, be.bag().copy());
        return super.playerWillDestroy(level, pos, state, player);
    }

    private static boolean hasAny(BagBlockEntity be) {
        var h = be.storage();
        for (int i = 0; i < h.getSlots(); i++) if (!h.getStackInSlot(i).isEmpty()) return true;
        return false;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(pos) instanceof BagBlockEntity be && !be.bag().isEmpty() ? be.bag().copyWithCount(1) : ItemStack.EMPTY;
    }
}
