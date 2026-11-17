package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.DrekkiSounds;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A player's right-click on a live drekki (spec, Pup release): sneak with an empty hand pets it; its owner puts a hat
 * on with the hat in hand and takes it off with an empty hand. Name tags and carriers are the items' own interactions,
 * kept to the owner here. Null passes to the vanilla mob interactions.
 */
public final class DrekkiInteract {
    private DrekkiInteract() {}

    static InteractionResult interact(DrekkiEntity d, Player player, InteractionHand hand) {
        if (!d.owned()) return null;
        ItemStack stack = player.getItemInHand(hand);
        boolean mine = player.getUUID().equals(d.owner());
        if (stack.isEmpty() && player.isSecondaryUseActive()) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!d.level().isClientSide) pet(d);
            return InteractionResult.sidedSuccess(d.level().isClientSide);
        }
        if (stack.is(DrekkiItems.SMALL_CARRIER.get()) && !mine) {
            if (!d.level().isClientSide) player.displayClientMessage(Component.translatable("message.rancher_companion.not_yours", Ranch.ownerName(d)), true);
            return InteractionResult.sidedSuccess(d.level().isClientSide);
        }
        int hat = DrekkiItems.hatIndex(stack);
        if (hat > 0 && mine) {
            if (!d.level().isClientSide) {
                int old = d.hat();
                d.setGear(hat, d.barding(), d.dye());
                stack.consume(1, player);
                if (old > 0) player.getInventory().placeItemBackInInventory(DrekkiItems.hatStack(old));
                d.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 1.2f);
                Ranch.touch(d);
            }
            return InteractionResult.sidedSuccess(d.level().isClientSide);
        }
        if (stack.isEmpty() && mine && d.hat() > 0 && hand == InteractionHand.MAIN_HAND) {
            if (!d.level().isClientSide) {
                player.setItemInHand(hand, DrekkiItems.hatStack(d.hat()));
                d.setGear(0, d.barding(), d.dye());
                d.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f, 0.9f);
                Ranch.touch(d);
            }
            return InteractionResult.sidedSuccess(d.level().isClientSide);
        }
        return null;
    }

    /** a name tag runs before the mob's own interaction, so only the owner may name it is checked on the event */
    public static void onEntityInteract(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract ev) {
        if (!(ev.getTarget() instanceof DrekkiEntity d) || !d.owned() || !ev.getItemStack().is(Items.NAME_TAG)) return;
        if (ev.getEntity().getUUID().equals(d.owner())) {
            if (!d.level().isClientSide) d.level().getServer().execute(() -> Ranch.touch(d));  /* after the tag applies */
            return;
        }
        if (!d.level().isClientSide) ev.getEntity().displayClientMessage(Component.translatable("message.rancher_companion.not_yours", Ranch.ownerName(d)), true);
        ev.setCanceled(true);
        ev.setCancellationResult(InteractionResult.sidedSuccess(d.level().isClientSide));
    }

    /** a happy sound, hearts and a short lean-in */
    private static void pet(DrekkiEntity d) {
        if (d.petTicks() > 0) return;
        d.pet();
        d.level().playSound(null, d.getX(), d.getY(), d.getZ(), DrekkiSounds.CALL.get(), SoundSource.NEUTRAL, 0.6f, 1.5f + d.getRandom().nextFloat() * 0.2f);
        if (d.level() instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.HEART, d.getX(), d.getY() + d.getBbHeight() + 0.2, d.getZ(), 3, 0.25, 0.1, 0.25, 0.02);
    }
}
