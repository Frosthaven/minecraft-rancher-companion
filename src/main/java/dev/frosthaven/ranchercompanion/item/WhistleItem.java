package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Drekki Whistle (spec, Stay and follow): the owner's checked-out drekki changes between staying and following.
 * One that is not with them (far, another dimension, an unloaded chunk) comes to them and follows. Sneaking: Go here
 * (entity/GoHere), to the block or seat looked at.
 */
public final class WhistleItem extends Item {
    public WhistleItem(Properties props) { super(props); }

    /** on a block too: Go here points at it (use, below, looks along the player's view up to 64 blocks) */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) { return net.minecraft.world.InteractionResult.PASS; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp && player.isShiftKeyDown()) {
            /* Go here: a long low tone, then up */
            if (dev.frosthaven.ranchercompanion.entity.GoHere.command(sp)) {
                level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1f, 1.2f);
                Ranch.after(sp.server, 4, () -> { if (sp.isAlive()) sp.level().playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1f, 1.8f); });
            }
            player.getCooldowns().addCooldown(this, 20);
        } else if (player instanceof ServerPlayer sp) {
            /* one tone for stay; two short ones, the second higher, for follow */
            boolean stays = Ranch.whistle(sp);
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1f, stays ? 1.5f : 1.6f);
            if (!stays) Ranch.after(sp.server, 3, () -> { if (sp.isAlive()) sp.level().playSound(null, sp.blockPosition(), SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.PLAYERS, 1f, 2f); });
            player.getCooldowns().addCooldown(this, 20);  /* one toggle a blow */
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        /* its two uses, one line each, while Shift is held (as the pack's other mods expand a tooltip) */
        if (flag.hasShiftDown()) {
            lines.add(Component.translatable(getDescriptionId() + ".use").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable(getDescriptionId() + ".sneak_use").withStyle(ChatFormatting.GRAY));
        } else lines.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.DARK_GRAY));
    }
}
