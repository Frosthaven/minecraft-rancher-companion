package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.block.DrekkiMenus;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The drekki screen (spec, Drekki screen): its equipment, its pack from stage 2 (one row of 9), and the player's
 * inventory. The hat slot is the drekki's hat: putting a hat in puts it on, taking it out takes it off. The stance
 * buttons are menu buttons 0..2 (Aggressive, Reactive, Passive), and the trait offer's yes and no are 3 and 4, sent by
 * the game's own button packet.
 */
public final class DrekkiMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen's texture (tools/screen_textures.py drekki; DrekkiScreen draws the same layout) */
    public static final int HAT_X = 8, HAT_Y = 27, PACK_Y = 129;
    /** the pack and the inventory: vanilla's 176 wide, centered under the 220-wide top */
    public static final int INV_X = 22 + 8;

    public final DrekkiEntity drekki;
    /** the layout this menu was opened with: a pack row from stage 2 */
    public final boolean pack;
    private final SimpleContainer hat = new SimpleContainer(1);

    public static int invY(boolean pack) { return pack ? 161 : 137; }

    /** opened from the chat offer: the screen starts on its Traits tab */
    public final boolean traitsTab;

    public DrekkiMenu(int id, Inventory inv, DrekkiEntity d) { this(id, inv, d, false); }

    public DrekkiMenu(int id, Inventory inv, DrekkiEntity d, boolean traitsTab) {
        super(DrekkiMenus.DREKKI.get(), id);
        this.drekki = d;
        this.traitsTab = traitsTab;
        this.pack = d != null && d.stage() >= 2;
        if (d != null) hat.setItem(0, d.hat() > 0 ? DrekkiItems.hatStack(d.hat()) : ItemStack.EMPTY);
        if (d != null && !d.level().isClientSide) hat.addListener(c -> {
            int now = DrekkiItems.hatIndex(c.getItem(0));
            if (now != d.hat()) { d.setGear(now, d.barding(), d.dye()); Ranch.touch(d); }
        });
        addSlot(new Slot(hat, 0, HAT_X, HAT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return DrekkiItems.hatIndex(s) > 0; }
            @Override public int getMaxStackSize() { return 1; }
        });
        if (pack) {
            Container c = d.level().isClientSide ? new SimpleContainer(9) : d.pack();
            for (int i = 0; i < 9; i++) addSlot(new Slot(c, i, INV_X + i * 18, PACK_Y));
        }
        int y = invY(pack);
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, y + 58));
    }

    private int invStart() { return pack ? 10 : 1; }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int inv = invStart();
        if (index < inv) { if (!moveItemStackTo(s, inv, slots.size(), true)) return ItemStack.EMPTY; }
        else if (DrekkiItems.hatIndex(s) > 0 && !slots.get(0).hasItem()) { if (!moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY; }
        else if (!pack || !moveItemStackTo(s, 1, inv, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    public static final int LEARN = 3, PASS = 4;

    /** the stance buttons (stage 2 on; spec, Combat), and the offer's yes and no */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drekki == null) return false;
        if (id == LEARN || id == PASS) return dev.frosthaven.ranchercompanion.trait.Traits.answer(drekki, id == LEARN);
        if (drekki.stage() < 2 || id < 0 || id >= Life.Stance.values().length) return false;
        drekki.life.stance = Life.Stance.values()[id];
        drekki.setTarget(null);
        drekki.syncLife();
        Ranch.touch(drekki);
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (drekki != null && !drekki.level().isClientSide && pack) Ranch.touch(drekki);  /* the pack's contents into the record */
    }

    /** 12 blocks: the chat offer opens it from as far as the 10 it learns in */
    @Override
    public boolean stillValid(Player player) {
        return drekki != null && drekki.isAlive() && !drekki.isRemoved() && player.distanceToSqr(drekki) <= 144 && player.getUUID().equals(drekki.owner());
    }
}
