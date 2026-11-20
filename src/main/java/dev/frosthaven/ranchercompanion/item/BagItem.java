package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.look.Gear;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * A drekki bag for its accessory slot (spec, Accessory: bags): the satchel (stage 2 and 3) and the saddlebag in its tiers
 * (stage 3). Its contents live on the item, as a shulker box keeps them, so a bag taken off or carried keeps what it
 * holds. kind is the Gear.BAGS index.
 */
public class BagItem extends Item implements DrekkiBag {
    public final int kind;

    @Override
    public int kind() { return kind; }

    public BagItem(int kind, Properties props) {
        super(props.stacksTo(1).component(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
        this.kind = kind;
    }

    public int slots() { return Gear.BAG_SLOTS[kind]; }

    /** the first stage that wears it */
    public int stage() { return Gear.BAG_STAGE[kind]; }

    /** sneak + right-click on a block places it, as a Sophisticated backpack is placed (spec, Accessory: bags) */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        var player = ctx.getPlayer();
        if (player == null || !player.isShiftKeyDown()) return net.minecraft.world.InteractionResult.PASS;
        var place = new net.minecraft.world.item.context.BlockPlaceContext(ctx);
        var level = ctx.getLevel(); var pos = place.getClickedPos();
        if (!place.canPlace()) return net.minecraft.world.InteractionResult.FAIL;
        var state = dev.frosthaven.ranchercompanion.block.DrekkiBlocks.BAG.get().defaultBlockState()
            .setValue(dev.frosthaven.ranchercompanion.block.BagBlock.FACING, player.getDirection().getOpposite())
            .setValue(dev.frosthaven.ranchercompanion.block.BagBlock.KIND, kind);
        if (!state.canSurvive(level, pos) || !level.isUnobstructed(state, pos, net.minecraft.world.phys.shapes.CollisionContext.of(player))) return net.minecraft.world.InteractionResult.FAIL;
        if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state);
            if (level.getBlockEntity(pos) instanceof dev.frosthaven.ranchercompanion.block.BagBlockEntity be) be.setBag(ctx.getItemInHand().copyWithCount(1));
            level.playSound(null, pos, net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            if (!player.isCreative()) ctx.getItemInHand().shrink(1);
        }
        return net.minecraft.world.InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** a bag never goes inside a bag */
    @Override
    public boolean canFitInsideContainerItems() { return false; }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        long used = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream().count();
        lines.add(Component.translatable("item.rancher_companion.bag.slots", used, slots()).withStyle(ChatFormatting.GRAY));
    }
}
