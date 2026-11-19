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

/** A trough's food: up to four stacks, sent to clients so the renderer can lay them on the straw; and its owner, whose
 *  drekki alone eat from it (spec, Feeding at troughs). */
public class TroughBlockEntity extends BlockEntity {
    public static final int SLOTS = 4;
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

    /** puts as much of the stack in as fits (merging first), shrinking it unless the player builds freely; returns how many went in */
    public int insert(ItemStack stack, boolean keep) {
        int left = stack.getCount();
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = 0; i < SLOTS && left > 0; i++) {
                ItemStack s = items.get(i);
                if (pass == 0 && !s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack)) {
                    int add = Math.min(left, s.getMaxStackSize() - s.getCount());
                    s.grow(add); left -= add;
                } else if (pass == 1 && s.isEmpty()) {
                    int add = Math.min(left, stack.getMaxStackSize());
                    items.set(i, stack.copyWithCount(add)); left -= add;
                }
            }
        }
        int put = stack.getCount() - left;
        if (put > 0) {
            if (!keep) stack.shrink(put);
            changed();
        }
        return put;
    }

    public List<ItemStack> takeAll() {
        List<ItemStack> out = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) if (!items.get(i).isEmpty()) { out.add(items.get(i)); items.set(i, ItemStack.EMPTY); }
        if (!out.isEmpty()) changed();
        return out;
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        ContainerHelper.saveAllItems(tag, items, reg);
        if (owner != null) tag.putUUID("Owner", owner);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, reg);
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
