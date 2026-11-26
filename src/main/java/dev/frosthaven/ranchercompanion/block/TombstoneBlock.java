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
 * A drekki's tombstone (spec, Tombstone). Right-click opens it (anyone may take the gear; only the drekki's owner summons
 * its ghost, puts a Drekki Lotus Blossom in and reclaims it). It cannot be mined while it holds anything; mined empty, it
 * drops itself with the drekki's name, date and record. While a blossom's day runs, cyan swirls rise from its base.
 */
public final class TombstoneBlock extends BaseEntityBlock {
    public static final MapCodec<TombstoneBlock> CODEC = simpleCodec(TombstoneBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** the candle at its foot (Shane, 2026-11-26): lit with soul fire while a blossom's day runs (light 3, a fifth of a torch, decorative or not),
     *  melted once it is done */
    public enum Candle implements net.minecraft.util.StringRepresentable {
        UNLIT, LIT, MELTED;
        @Override public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    /** a used tombstone (its drekki reclaimed; Shane, 2026-11-26): a crack runs from the top down to the base; its candle is
     *  whole again, a light source anyone may light and put out; the item keeps the crack (its BLOCK_STATE component) */
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty CRACKED =
        net.minecraft.world.level.block.state.properties.BooleanProperty.create("cracked");
    public static final net.minecraft.world.level.block.state.properties.EnumProperty<Candle> CANDLE =
        net.minecraft.world.level.block.state.properties.EnumProperty.create("candle", Candle.class);
    /** the plinth and the stone, along x (facing north or south) and along z */
    private static final VoxelShape X = Shapes.or(box(1, 0, 4, 15, 2, 12), box(3, 2, 6, 13, 15, 10)),
        Z = Shapes.or(box(4, 0, 1, 12, 2, 15), box(6, 2, 3, 10, 15, 13));

    public TombstoneBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CANDLE, Candle.UNLIT).setValue(CRACKED, false));
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, CANDLE, CRACKED); }
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

    /** a Drekki Lotus Blossom's day running: the candle's soul-fire flame, and cyan swirls rise from its base, as magenta
     *  ones from a breeding hatchery */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource r) {
        if (state.getValue(CANDLE) == Candle.LIT && r.nextInt(3) == 0) {  /* the candle's soul-fire flame, as a soul torch's */
            double cx = 12.25 / 16 - 0.5, cz = 5.25 / 16 - 0.5;  /* the wick, in the north-facing model */
            double ang = Math.toRadians(-state.getValue(FACING).toYRot() + 180);  /* the blockstate's y turn for this facing */
            double x = cx * Math.cos(ang) - cz * Math.sin(ang), z = cx * Math.sin(ang) + cz * Math.cos(ang);
            level.addParticle(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + x, pos.getY() + 7.6 / 16, pos.getZ() + 0.5 + z, 0, 0.005, 0);
        }
        if (!(level.getBlockEntity(pos) instanceof TombstoneBlockEntity be) || be.reviveStart() == 0
            || dev.frosthaven.ranchercompanion.ranch.Rebirth.progress(be, level.getDayTime()) >= 1) return;
        var cyan = net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0xFF40E0E8);
        for (int i = 0; i < 3; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            level.addParticle(cyan, pos.getX() + 0.5 + Math.cos(a) * 0.55, pos.getY() + 0.05 + r.nextDouble() * 0.15, pos.getZ() + 0.5 + Math.sin(a) * 0.55, 0, 0, 0);
        }
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, DrekkiBlocks.TOMBSTONE_ENTITY.get(), TombstoneBlockEntity::serverTick);
    }

    /** flint and steel lights the candle (owner only, a blossom in, not lit yet); otherwise it does nothing here, so no
     *  fire is set on the tombstone */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                               net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof net.minecraft.world.item.FlintAndSteelItem)) return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be) {
            boolean lit;
            if (!be.hasRecord()) lit = be.lightDecor();  /* a decorative (used or placed) one: anyone lights it, a plain light source */
            else {
                String why = dev.frosthaven.ranchercompanion.ranch.Rebirth.lightWhy(sp, be);
                if (why != null) dev.frosthaven.ranchercompanion.ranch.Ranch.say(sp, why);
                lit = why == null && be.light();
            }
            if (lit) {
                level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FLINTANDSTEEL_USE, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                stack.hurtAndBreak(1, sp, net.minecraft.world.entity.LivingEntity.getSlotForHand(hand));
            }
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive() && state.getValue(CANDLE) == Candle.LIT) {  /* sneak, empty hand: the candle goes out */
            if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be) {
                String why = be.hasRecord() && !dev.frosthaven.ranchercompanion.ranch.Rebirth.owner(sp, be) ? "tomb.not_owner" : null;
                if (why != null) dev.frosthaven.ranchercompanion.ranch.Ranch.say(sp, why);
                else {
                    be.putOut(sp);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.CANDLE_EXTINGUISH, net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be)
        {
            boolean owner = dev.frosthaven.ranchercompanion.ranch.Rebirth.owner(sp, be);
            sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TombstoneMenu(id, inv, pos, owner), TombstoneMenu.title(be)), buf -> { buf.writeBlockPos(pos); buf.writeBoolean(owner); });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** a player mining it (Shane, 2026-11-26): what it holds goes into their inventory, the rest at their feet, never where
     *  the tombstone stood (on a lava surface, items there would burn); the onRemove below then finds it empty */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be)
            for (int i = 0; i < be.items().getContainerSize(); i++) {
                ItemStack s = be.items().removeItemNoUpdate(i);
                if (!s.isEmpty()) player.getInventory().placeItemBackInInventory(s);
            }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** the tombstone itself, mined by a player: into their inventory, or at their feet */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity be, ItemStack tool) {
        player.awardStat(net.minecraft.stats.Stats.BLOCK_MINED.get(this));
        player.causeFoodExhaustion(0.005f);
        if (level instanceof ServerLevel sl && !player.isCreative())
            for (ItemStack s : getDrops(state, sl, pos, be, player, tool)) player.getInventory().placeItemBackInInventory(s);
    }

    /** mined at any time (Shane, 2026-11-26): what it holds drops, the ghost is laid to rest and the day's progress is lost;
     *  the item keeps the name, date and drekki */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be) {
            if (be.summoned()) dev.frosthaven.ranchercompanion.ranch.Rebirth.lay(sl, pos, be);
            net.minecraft.world.Containers.dropContents(level, pos, be.items());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** mined: itself, with the name, date and drekki */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ItemStack s = new ItemStack(this);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof TombstoneBlockEntity be) s.applyComponents(be.collectComponents());
        return List.of(cracked(s, state));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack s = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof TombstoneBlockEntity be) s.applyComponents(be.collectComponents());
        return cracked(s, state);
    }

    /** a cracked tombstone's item places cracked again */
    private static ItemStack cracked(ItemStack s, BlockState state) {
        if (state.getValue(CRACKED)) s.set(net.minecraft.core.component.DataComponents.BLOCK_STATE,
            net.minecraft.world.item.component.BlockItemStateProperties.EMPTY.with(CRACKED, true));
        return s;
    }

    /** an emptied death tombstone goes, with a puff of soul smoke */
    static void vanish(Level level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        if (level instanceof ServerLevel sl) sl.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.25, 0.3, 0.25, 0.02);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 0.8f, 1f);
    }
}
