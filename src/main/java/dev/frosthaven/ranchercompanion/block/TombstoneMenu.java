package dev.frosthaven.ranchercompanion.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A tombstone's screen (spec, Tombstone): the gear it holds (take only) over the player's inventory. */
public final class TombstoneMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen's texture (tools/screen_textures.py tombstone; TombstoneScreen draws the same) */
    public static final int SLOT_X = 62, SLOT_Y = 34, INV_Y = 84;

    public final Level level;
    public final BlockPos pos;
    private final Container gear;

    public TombstoneMenu(int id, Inventory inv, BlockPos pos) {
        super(DrekkiMenus.TOMBSTONE.get(), id);
        this.level = inv.player.level();
        this.pos = pos;
        this.gear = level.getBlockEntity(pos) instanceof TombstoneBlockEntity be ? be.items() : new SimpleContainer(TombstoneBlockEntity.SLOTS);
        for (int i = 0; i < TombstoneBlockEntity.SLOTS; i++) addSlot(new Slot(gear, i, SLOT_X + i * 18, SLOT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return false; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

    /** "Here lies <name>", or the plain block name for a tombstone that has none */
    public static Component title(TombstoneBlockEntity be) {
        return be.name().isEmpty() ? Component.translatable("block.rancher_companion.tombstone")
            : Component.translatable("screen.rancher_companion.tombstone.title", be.name());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        int n = TombstoneBlockEntity.SLOTS;
        if (index >= n || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockEntity(pos) instanceof TombstoneBlockEntity && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
