package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.ranch.Rebirth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A tombstone to pipes, funnels, chutes and hoppers (Shane, 2026-11-26): they may push in a Drekki Lotus Blossom (it
 * starts the day and raises the ghost, as the owner's hand does) and pull out the gear the drekki wore. They never take
 * the blossom out (that would cancel the day), never put one in while its ghost is breeding, and never into an inert
 * tombstone (no record: nothing to bring back).
 */
public final class TombHandler implements IItemHandler {
    private final TombstoneBlockEntity be;

    public TombHandler(TombstoneBlockEntity be) { this.be = be; }

    private static boolean blossom(ItemStack s) { return s.is(DrekkiItems.LOTUS_BLOSSOM.get()); }

    /** a blossom may go in now */
    private boolean takesBlossom() {
        if (!be.hasRecord() || be.blossomSlot() >= 0 || be.getLevel() == null || be.getLevel().isClientSide) return false;
        return !Rebirth.breeding(be.getLevel().getServer(), be);
    }

    @Override public int getSlots() { return TombstoneBlockEntity.SLOTS; }
    @Override public ItemStack getStackInSlot(int slot) { return be.items().getItem(slot); }
    @Override public int getSlotLimit(int slot) { return 1; }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return blossom(stack) && be.items().getItem(slot).isEmpty() && takesBlossom();
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) return stack;
        if (!simulate) be.items().setItem(slot, stack.copyWithCount(1));  /* the tombstone's listener starts the day */
        return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack in = be.items().getItem(slot);
        if (amount <= 0 || in.isEmpty() || blossom(in)) return ItemStack.EMPTY;  /* the blossom stays */
        return simulate ? in.copy() : be.items().removeItem(slot, in.getCount());
    }
}
