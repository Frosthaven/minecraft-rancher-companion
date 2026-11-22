package dev.frosthaven.ranchercompanion.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * A trough line as one inventory for pipes, funnels, chutes and hoppers (spec, Drekki Trough): one slot per trough of the
 * line, the same from any trough of it, as a Create vault. It takes only food a drekki eats, of the line's diet, where a
 * slot has room; anything else is refused, so it waits in the pipe. Food can be pulled out of any slot, as from a chest.
 * The line is read on every call, so joins and splits need no bookkeeping.
 */
public final class TroughLine implements IItemHandler {
    private final TroughBlockEntity from;

    public TroughLine(TroughBlockEntity from) { this.from = from; }

    private List<TroughBlockEntity> line() {
        List<TroughBlockEntity> out = new ArrayList<>();
        var level = from.getLevel();
        if (level == null) return out;
        for (var p : TroughBlock.line(level, from.getBlockPos())) if (level.getBlockEntity(p) instanceof TroughBlockEntity be) out.add(be);
        return out;
    }

    @Override public int getSlots() { return line().size(); }
    @Override public ItemStack getStackInSlot(int slot) { var l = line(); return slot < 0 || slot >= l.size() ? ItemStack.EMPTY : l.get(slot).items().get(0); }
    @Override public int getSlotLimit(int slot) { return 64; }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        var l = line();
        var level = from.getLevel();
        if (slot < 0 || slot >= l.size() || level == null || level.isClientSide) return ItemStack.EMPTY;
        ItemStack out = l.get(slot).take(amount, simulate);
        if (!simulate && !out.isEmpty()) TroughBlock.contentsChangedAt(level, from.getBlockPos());   /* an empty line has no diet */
        return out;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        var level = from.getLevel();
        return level != null && !level.isClientSide && TroughBlock.accepts(level, from.getBlockPos(), stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        var l = line();
        if (stack.isEmpty() || slot < 0 || slot >= l.size() || !isItemValid(slot, stack)) return stack;
        int in = l.get(slot).put(stack, simulate);
        if (in > 0 && !simulate) TroughBlock.contentsChanged(from.getLevel(), from.getBlockPos(), stack);
        return in >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - in);
    }
}
