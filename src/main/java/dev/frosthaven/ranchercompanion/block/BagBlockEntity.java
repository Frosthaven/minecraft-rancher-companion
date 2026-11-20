package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.item.BagItem;
import dev.frosthaven.ranchercompanion.look.Gear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.items.IItemHandler;

/** A placed plain bag (spec, Accessory: bags): it holds the bag item itself, contents and dyes on it, as a shulker box. */
public final class BagBlockEntity extends BlockEntity {
    private ItemStack bag = ItemStack.EMPTY;

    public BagBlockEntity(BlockPos pos, BlockState state) { super(DrekkiBlocks.BAG_ENTITY.get(), pos, state); }

    public ItemStack bag() { return bag; }

    public void setBag(ItemStack s) { bag = s; setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    /** its storage, on the item it holds; a change marks the block for saving */
    public IItemHandler storage() {
        if (!(bag.getItem() instanceof BagItem b)) return null;
        return new ComponentItemHandler(bag, DataComponents.CONTAINER, Gear.BAG_SLOTS[b.kind]) {
            @Override protected void onContentsChanged(int slot, ItemStack oldStack, ItemStack newStack) { setChanged(); }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (!bag.isEmpty()) tag.put("Bag", bag.save(reg));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        bag = tag.contains("Bag") ? ItemStack.parseOptional(reg, tag.getCompound("Bag")) : ItemStack.EMPTY;
    }

    /** the client knows the bag, for its dyes */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider reg) { return saveWithoutMetadata(reg); }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
