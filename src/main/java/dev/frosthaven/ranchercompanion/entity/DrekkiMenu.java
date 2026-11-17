package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.block.DrekkiMenus;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The drekki screen (spec, Drekki screen; pup release: the hat slot only): its equipment over the player's inventory.
 * The hat slot is the drekki's hat: putting a hat in puts it on, taking it out takes it off.
 */
public final class DrekkiMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen's texture (DrekkiScreen draws the same layout) */
    public static final int HAT_X = 8, HAT_Y = 27, INV_Y = 114;

    public final DrekkiEntity drekki;
    private final SimpleContainer hat = new SimpleContainer(1);

    public DrekkiMenu(int id, Inventory inv, DrekkiEntity d) {
        super(DrekkiMenus.DREKKI.get(), id);
        this.drekki = d;
        if (d != null) hat.setItem(0, d.hat() > 0 ? DrekkiItems.hatStack(d.hat()) : ItemStack.EMPTY);
        if (d != null && !d.level().isClientSide) hat.addListener(c -> {
            int now = DrekkiItems.hatIndex(c.getItem(0));
            if (now != d.hat()) { d.setGear(now, d.barding(), d.dye()); Ranch.touch(d); }
        });
        addSlot(new Slot(hat, 0, HAT_X, HAT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return DrekkiItems.hatIndex(s) > 0; }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index == 0) { if (!moveItemStackTo(s, 1, slots.size(), true)) return ItemStack.EMPTY; }
        else if (DrekkiItems.hatIndex(s) == 0 || slots.get(0).hasItem() || !moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return drekki != null && drekki.isAlive() && !drekki.isRemoved() && player.distanceToSqr(drekki) <= 64 && player.getUUID().equals(drekki.owner());
    }
}
