package dev.frosthaven.ranchercompanion.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A water cauldron washes the dye off the mod's dyeable items, as it does leather armor, using one level of water: a
 * hatchery's or trough's block state color, and both colors of barding and bags (plain or Sophisticated). A named class
 * (not a lambda) so other mods can recognize it by name, as the Haven pack's bleaching does for the cauldron actions
 * it runs.
 */
public final class LeatherWash implements CauldronInteraction {
    public static final LeatherWash INSTANCE = new LeatherWash();

    private LeatherWash() {}

    @Override
    public ItemInteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack stack) {
        if (stack.getItem() instanceof BardingItem || stack.getItem() instanceof DrekkiBag) {
            if (Dyeing.main(stack) < 0 && Dyeing.trim(stack) < 0) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide) {
                Dyeing.clear(stack);
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                player.awardStat(net.minecraft.stats.Stats.CLEAN_ARMOR);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        var props = stack.get(DataComponents.BLOCK_STATE);
        if (props == null || props.properties().getOrDefault("color", "0").equals("0")) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            stack.remove(DataComponents.BLOCK_STATE);
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
            level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5f, 1f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
