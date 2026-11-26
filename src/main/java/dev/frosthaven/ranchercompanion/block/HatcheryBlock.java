package dev.frosthaven.ranchercompanion.block;

import com.mojang.serialization.MapCodec;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Drekki Hatchery: a dark oak chest bound in steel with a leather nest and a bed of straw (spec, Hatchery). Its
 * leather takes a dye on right-click (COLOR, 0 undyed) and keeps it when broken. Right-click with the egg puts it in
 * the nest; right-click the nest's top with the egg Ready hatches it; any other right-click opens the hatchery screen.
 */
public class HatcheryBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<HatcheryBlock> CODEC = simpleCodec(HatcheryBlock::new);
    public static final IntegerProperty COLOR = TroughBlock.COLOR;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public HatcheryBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOR, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, COLOR); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) { return SHAPE; }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HatcheryBlockEntity(pos, state); }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != DrekkiBlocks.HATCHERY_ENTITY.get() ? null
            : (BlockEntityTicker<T>) (BlockEntityTicker<HatcheryBlockEntity>) HatcheryBlockEntity::serverTick;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player p && level.getBlockEntity(pos) instanceof HatcheryBlockEntity be) be.claim(p.getUUID());
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye) {
            int color = dye.getDyeColor().getId() + 1;
            if (state.getValue(COLOR) == color) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COLOR, color), Block.UPDATE_ALL);
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1f, 1f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.is(DrekkiItems.INTRIGUING_EGG.get())) {
            if (!level.isClientSide) Ranch.putEgg((ServerPlayer) player, pos, stack);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean onNest = hit.getDirection() == Direction.UP;
            if (onNest && level.getBlockEntity(pos) instanceof HatcheryBlockEntity be && be.hasEgg()
                && dev.frosthaven.ranchercompanion.look.Incubation.ready(be.incubation())) Ranch.hatch((ServerPlayer) player, pos);
            else Ranch.openHatchery((ServerPlayer) player, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** a Ready egg: teal motes rise from the nest now and then (spec, Hatchery). Breeding: magenta swirls rise from the
     *  hatchery's base on every side (Shane, 2026-11-26) */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, net.minecraft.util.RandomSource r) {
        if (!(level.getBlockEntity(pos) instanceof HatcheryBlockEntity be)) return;
        if (be.breeding()) {
            var magenta = net.minecraft.core.particles.ColorParticleOption.create(net.minecraft.core.particles.ParticleTypes.ENTITY_EFFECT, 0xFFE040C8);
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI * 2;
                level.addParticle(magenta, pos.getX() + 0.5 + Math.cos(a) * 0.55, pos.getY() + 0.05 + r.nextDouble() * 0.15, pos.getZ() + 0.5 + Math.sin(a) * 0.55, 0, 0, 0);
            }
        }
        if (!be.hasEgg() || !dev.frosthaven.ranchercompanion.look.Incubation.ready(be.incubation())) return;
        for (int i = 0; i < 2; i++)
            level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, pos.getX() + 0.35 + r.nextDouble() * 0.3, pos.getY() + 1.0,
                pos.getZ() + 0.35 + r.nextDouble() * 0.3, 0, 0.02 + r.nextDouble() * 0.02, 0);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide && level.getBlockEntity(pos) instanceof HatcheryBlockEntity be) {
            if (be.hasEgg()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, be.takeEgg());
            Ranch.hatcheryBroken((net.minecraft.server.level.ServerLevel) level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
