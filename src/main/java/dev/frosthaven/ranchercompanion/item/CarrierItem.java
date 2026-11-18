package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * The small drekki carrier (spec, Carriers): right-click your checked-out stage 1 drekki to put it inside, right-click
 * the ground to let it out. Inside it does nothing and learns nothing. The carrier holds only the drekki's roster id;
 * the roster keeps the drekki, so a copied carrier lets nothing out twice.
 */
public final class CarrierItem extends Item {
    public static final int STAGE = 1;

    public CarrierItem(Properties props) {
        super(props);
    }

    /** the carrier holds a drekki of someone other than this player */
    public static boolean notYours(ItemStack stack, Player p) {
        var owner = stack.get(DrekkiData.CARRIED_OWNER.get());
        return full(stack) && owner != null && p != null && !owner.equals(p.getUUID());
    }

    public static boolean full(ItemStack stack) { return stack.has(DrekkiData.CARRIED.get()); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof DrekkiEntity d) || !d.owned()) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        /* DrekkiInteract already stops others; this holds too if a mod hands the item straight to the entity */
        if (!player.getUUID().equals(d.owner())) player.displayClientMessage(Component.translatable("message.rancher_companion.not_yours", Ranch.ownerName(d)), true);
        else if (full(stack)) msg(player, "carrier.full");
        else if (d.stage() != STAGE) msg(player, "carrier.too_big");
        else if (!d.isOut()) msg(player, "carrier.checked_in");
        else Ranch.carry((ServerPlayer) player, d, player.getItemInHand(hand));
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        ItemStack stack = ctx.getItemInHand();
        if (!full(stack) || ctx.getPlayer() == null) return InteractionResult.PASS;
        /* someone else's drekki: nothing happens, on either side (the tooltip tells them whose it is) */
        var owner = stack.get(DrekkiData.CARRIED_OWNER.get());
        if (owner != null && !owner.equals(ctx.getPlayer().getUUID())) return InteractionResult.PASS;
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        Ranch.letOut((ServerPlayer) ctx.getPlayer(), stack, ctx.getClickedPos().relative(ctx.getClickedFace()));
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String name = stack.get(DrekkiData.CARRIED_NAME.get());
        if (full(stack)) lines.add(Component.translatable("item.rancher_companion.small_drekki_carrier.holds", name == null ? "?" : name).withStyle(ChatFormatting.GRAY));
        else lines.add(Component.translatable("item.rancher_companion.small_drekki_carrier.empty").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static void msg(Player p, String key) { p.displayClientMessage(Component.translatable("message.rancher_companion." + key), true); }
}
