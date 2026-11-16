package dev.frosthaven.ranchercompanion.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.ranchercompanion.FoodDiets;
import dev.frosthaven.ranchercompanion.look.Diet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Drekki Trough. Its silhouette is the feeder the feeding loops are built around: a 2 px floor, 14 px walls under a
 * padded leather roll, open low on one side. FACING is the open side (toward the player on placement, away when sneaking).
 * Troughs facing the same way side by side join into a straight line of up to 10 (LEFT and RIGHT: joined toward the
 * neighbor on that side); a line holds one diet (DIET: 0 none, then Raw, Cooked, Seed, Root), set by the first food put in
 * and cleared when the line is empty. COLOR is the leather's dye (0 undyed), set per trough with a dye.
 */
public class TroughBlock extends BaseEntityBlock {
    public static final MapCodec<TroughBlock> CODEC = simpleCodec(TroughBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right");
    public static final IntegerProperty DIET = IntegerProperty.create("diet", 0, 4), COLOR = IntegerProperty.create("color", 0, 16);
    public static final int MAX_LINE = 10;

    public TroughBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.WEST).setValue(LEFT, false).setValue(RIGHT, false)
            .setValue(DIET, 0).setValue(COLOR, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, LEFT, RIGHT, DIET, COLOR); }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new TroughBlockEntity(pos, state); }

    /** the line runs along the open side: left is clockwise of FACING, as the model's local -z */
    public static Direction left(BlockState s) { return s.getValue(FACING).getClockWise(); }
    public static Direction right(BlockState s) { return s.getValue(FACING).getCounterClockWise(); }

    // ---- placement and joining ----

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction look = ctx.getHorizontalDirection();
        boolean flip = ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown();
        BlockState s = defaultBlockState().setValue(FACING, flip ? look : look.getOpposite());
        return withJoins(ctx.getLevel(), ctx.getClickedPos(), s);
    }

    /** joins toward each side while the line stays at 10 or fewer and the two lines' diets agree */
    private BlockState withJoins(BlockGetter level, BlockPos pos, BlockState s) {
        Direction l = left(s), r = right(s);
        int ln = run(level, pos.relative(l), s, l), rn = run(level, pos.relative(r), s, r);
        int ld = ln > 0 ? level.getBlockState(pos.relative(l)).getValue(DIET) : 0, rd = rn > 0 ? level.getBlockState(pos.relative(r)).getValue(DIET) : 0;
        boolean jl = ln > 0 && 1 + ln <= MAX_LINE;
        int diet = jl ? ld : 0;
        boolean jr = rn > 0 && 1 + (jl ? ln : 0) + rn <= MAX_LINE && (diet == 0 || rd == 0 || rd == diet);
        if (jr && diet == 0) diet = rd;
        return s.setValue(LEFT, jl).setValue(RIGHT, jr).setValue(DIET, diet);
    }

    /** how many troughs facing the same way run from p in direction dir, following their own joins */
    private int run(BlockGetter level, BlockPos p, BlockState self, Direction dir) {
        int n = 0;
        while (n < MAX_LINE) {
            BlockState s = level.getBlockState(p);
            if (!s.is(this) || s.getValue(FACING) != self.getValue(FACING)) break;
            n++;
            if (!s.getValue(dir == left(s) ? LEFT : RIGHT)) break;
            p = p.relative(dir);
        }
        return n;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && state.getValue(DIET) != 0) setLineDiet(level, pos, state.getValue(DIET));  /* a line joined to a fed one shares its diet */
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState n, LevelAccessor level, BlockPos pos, BlockPos npos) {
        boolean isLeft = dir == left(state), isRight = dir == right(state);
        if (!isLeft && !isRight) return state;
        boolean joined = n.is(this) && n.getValue(FACING) == state.getValue(FACING) && n.getValue(isLeft ? RIGHT : LEFT);
        BooleanProperty side = isLeft ? LEFT : RIGHT;
        if (state.getValue(side) && !joined) level.scheduleTick(pos, this, 1);  /* split off: check whether this part is empty */
        return state.setValue(side, joined);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(DIET) != 0 && lineEmpty(level, line(level, pos))) setLineDiet(level, pos, 0);
    }

    // ---- the line ----

    /** every trough joined to this one, this one included */
    public static List<BlockPos> line(BlockGetter level, BlockPos pos) {
        List<BlockPos> out = new ArrayList<>();
        out.add(pos);
        BlockState s = level.getBlockState(pos);
        for (boolean toLeft : new boolean[]{true, false}) {
            BlockPos p = pos;
            BlockState cur = s;
            while (cur.getBlock() instanceof TroughBlock && cur.getValue(toLeft ? LEFT : RIGHT) && out.size() < MAX_LINE) {
                p = p.relative(toLeft ? left(cur) : right(cur));
                cur = level.getBlockState(p);
                if (!(cur.getBlock() instanceof TroughBlock)) break;
                out.add(p);
            }
        }
        return out;
    }

    private static boolean lineEmpty(BlockGetter level, List<BlockPos> line) {
        for (BlockPos p : line) if (level.getBlockEntity(p) instanceof TroughBlockEntity be && !be.isEmpty()) return false;
        return true;
    }

    private static void setLineDiet(Level level, BlockPos pos, int diet) {
        for (BlockPos p : line(level, pos)) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof TroughBlock && s.getValue(DIET) != diet) level.setBlock(p, s.setValue(DIET, diet), Block.UPDATE_ALL);
        }
    }

    // ---- interaction ----

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye) {  /* a dye recolors this trough's leather, only this one */
            int color = dye.getDyeColor().getId() + 1;
            if (state.getValue(COLOR) == color) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return FoodDiets.byDefault(stack) != null ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        Diet diet = FoodDiets.of(stack);
        if (diet == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int want = diet.ordinal() + 1, cur = state.getValue(DIET);
        if (cur != 0 && cur != want) {
            player.displayClientMessage(Component.translatable("block.rancher_companion.trough.holds", Diet.of(cur - 1).label), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!(level.getBlockEntity(pos) instanceof TroughBlockEntity be)) return ItemInteractionResult.FAIL;
        int put = be.insert(stack, player.hasInfiniteMaterials());
        if (put == 0) {
            player.displayClientMessage(Component.translatable("block.rancher_companion.trough.full"), true);
            return ItemInteractionResult.CONSUME;
        }
        if (cur == 0) setLineDiet(level, pos, want);
        level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 0.9f + level.random.nextFloat() * 0.2f);
        return ItemInteractionResult.CONSUME;
    }

    /** an empty hand takes the food back out */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TroughBlockEntity be) || be.isEmpty()) return InteractionResult.PASS;
        if (!level.isClientSide) {
            for (ItemStack s : be.takeAll()) player.getInventory().placeItemBackInInventory(s);
            if (lineEmpty(level, line(level, pos))) setLineDiet(level, pos, 0);
            level.playSound(null, pos, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 1f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TroughBlockEntity be) Containers.dropContents(level, pos, be.items());
        super.onRemove(state, level, pos, newState, moved);
    }

    // ---- shape: the model's boxes, local (open toward -x, the line along z), turned to FACING ----

    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int key = s.getValue(FACING).get2DDataValue() * 4 + (s.getValue(LEFT) ? 2 : 0) + (s.getValue(RIGHT) ? 1 : 0);
        VoxelShape v = SHAPES[key];
        if (v == null) SHAPES[key] = v = shape(s.getValue(FACING), s.getValue(LEFT), s.getValue(RIGHT));
        return v;
    }

    private static VoxelShape shape(Direction facing, boolean l, boolean r) {
        List<double[]> b = new ArrayList<>(List.of(
            new double[]{2, 0, 2, 14, 2, 14}, new double[]{14, 0, 2, 16, 14, 14}, new double[]{0, 12, 2, 2, 14, 14}, new double[]{0, 2, 2, 2, 5, 14},
            new double[]{0, 0, 0, 2, 14, 2}, new double[]{14, 0, 0, 16, 14, 2}, new double[]{0, 0, 14, 2, 14, 16}, new double[]{14, 0, 14, 16, 14, 16},
            new double[]{0, 14, 0, 2.5, 16, 16}, new double[]{13.5, 14, 0, 16, 16, 16}));
        b.add(l ? new double[]{2, 0, 0, 14, 2, 2} : new double[]{2, 0, 0, 14, 14, 2});
        b.add(r ? new double[]{2, 0, 14, 14, 2, 16} : new double[]{2, 0, 14, 14, 14, 16});
        if (!l) b.add(new double[]{2.5, 14, 0, 13.5, 16, 2.5});
        if (!r) b.add(new double[]{2.5, 14, 13.5, 13.5, 16, 16});
        int turns = switch (facing) { case NORTH -> 1; case EAST -> 2; case SOUTH -> 3; default -> 0; };  /* local opens west */
        VoxelShape out = Shapes.empty();
        for (double[] box : b) {
            double x0 = box[0], z0 = box[2], x1 = box[3], z1 = box[5];
            for (int t = 0; t < turns; t++) {  /* a quarter turn clockwise seen from above: (x, z) to (16 - z, x) */
                double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
                x0 = nx0; x1 = nx1; z0 = nz0; z1 = nz1;
            }
            out = Shapes.or(out, Block.box(x0, box[1], z0, x1, box[4], z1));
        }
        return out.optimize();
    }
}
