package dev.frosthaven.ranchercompanion.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** A trough's food: one stack, sent to clients so the renderer can lay it on the straw; and its owner, whose drekki
 *  alone eat from it (spec, Feeding at troughs). The troughs of a line share their slots: {@link TroughLine}. A trough
 *  saved with the four stacks it held before 0.4.9 keeps the first and moves the rest into the line (see settle). */
public class TroughBlockEntity extends BlockEntity {
    public static final int SLOTS = 1;
    /** stacks from the four slots it had before 0.4.9, until the next server tick puts them in the line */
    private final List<ItemStack> overflow = new ArrayList<>();
    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private java.util.UUID owner;

    public TroughBlockEntity(BlockPos pos, BlockState state) {
        super(DrekkiBlocks.TROUGH_ENTITY.get(), pos, state);
    }

    public NonNullList<ItemStack> items() { return items; }

    /** who placed it; a trough placed before 0.4.0 has none until a player uses it */
    public java.util.UUID owner() { return owner; }
    public void claim(java.util.UUID player) { if (owner == null) { owner = player; setChanged(); } }

    /** the food as a container, for the trough's screen: every change syncs to clients */
    public net.minecraft.world.Container container() {
        return new net.minecraft.world.SimpleContainer(SLOTS) {
            @Override public int getContainerSize() { return SLOTS; }
            @Override public ItemStack getItem(int i) { return items.get(i); }
            @Override public ItemStack removeItem(int i, int n) { ItemStack s = ContainerHelper.removeItem(items, i, n); if (!s.isEmpty()) changed(); return s; }
            @Override public ItemStack removeItemNoUpdate(int i) { return ContainerHelper.takeItem(items, i); }
            @Override public void setItem(int i, ItemStack s) { items.set(i, s); changed(); }
            @Override public boolean isEmpty() { return TroughBlockEntity.this.isEmpty(); }
            @Override public void setChanged() { changed(); }
        };
    }

    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    /** how much of the stack its slot takes (the same food with room, or an empty slot); puts it in unless simulated */
    public int put(ItemStack stack, boolean simulate) {
        ItemStack s = items.get(0);
        int add;
        if (s.isEmpty()) add = Math.min(stack.getCount(), stack.getMaxStackSize());
        else if (ItemStack.isSameItemSameComponents(s, stack)) add = Math.min(stack.getCount(), s.getMaxStackSize() - s.getCount());
        else return 0;
        if (add > 0 && !simulate) {
            if (s.isEmpty()) items.set(0, stack.copyWithCount(add)); else s.grow(add);
            changed();
        }
        return Math.max(0, add);
    }

    /** up to this many of its food, for a pipe that pulls; taken out unless simulated */
    public ItemStack take(int amount, boolean simulate) {
        ItemStack s = items.get(0);
        if (s.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        if (simulate) return s.copyWithCount(Math.min(amount, s.getCount()));
        ItemStack out = ContainerHelper.removeItem(items, 0, amount);
        if (!out.isEmpty()) changed();
        return out;
    }

    /** everything it holds, for the drop when it is broken */
    public List<ItemStack> drops() {
        List<ItemStack> out = new ArrayList<>(overflow);
        if (!items.get(0).isEmpty()) out.add(items.get(0));
        return out;
    }

    /** server tick: the stacks beyond its one slot go into free slots of its line; what does not fit drops on it */
    public static void settle(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TroughBlockEntity be) {
        if (be.overflow.isEmpty()) return;
        for (ItemStack s : be.overflow) {
            int in = TroughBlock.lineInsert(level, pos, s, false);
            if (in < s.getCount()) net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, s.copyWithCount(s.getCount() - in));
        }
        be.overflow.clear();
        be.setChanged();
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
        if (!overflow.isEmpty()) {
            net.minecraft.nbt.ListTag more = new net.minecraft.nbt.ListTag();
            for (ItemStack s : overflow) more.add(s.save(reg));
            tag.put("Overflow", more);
        }
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        NonNullList<ItemStack> saved = NonNullList.withSize(4, ItemStack.EMPTY);  /* four slots before 0.4.9 */
        ContainerHelper.loadAllItems(tag, saved, reg);
        items.set(0, ItemStack.EMPTY);
        overflow.clear();
        for (ItemStack s : saved) if (!s.isEmpty()) { if (items.get(0).isEmpty()) items.set(0, s); else overflow.add(s); }
        for (net.minecraft.nbt.Tag t : tag.getList("Overflow", net.minecraft.nbt.Tag.TAG_COMPOUND)) ItemStack.parse(reg, t).ifPresent(overflow::add);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) {
        CompoundTag tag = new CompoundTag();
        ContainerHelper.saveAllItems(tag, items, true, reg);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
