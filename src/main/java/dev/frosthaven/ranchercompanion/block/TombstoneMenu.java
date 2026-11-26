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

/** A tombstone's screen (spec, Tombstone): the gear it holds (anyone may take it); its owner alone may put a Drekki Lotus
 *  Blossom in a free slot (one at a time) and take it out; over the player's inventory. Menu button 1: Reclaim (its owner
 *  only, checked on the server). */
public final class TombstoneMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen's texture (tools/screen_textures.py tombstone; TombstoneScreen draws the same) */
    public static final int SLOT_X = 53, SLOT_Y = 31, TOP = 108, H = TOP + 92, INV_Y = TOP + 10;

    public final Level level;
    public final BlockPos pos;
    private final Container gear;
    /** the viewer is the fallen drekki's owner (decided by the server when the screen opens) */
    public final boolean owner;

    public TombstoneMenu(int id, Inventory inv, BlockPos pos, boolean owner) {
        super(DrekkiMenus.TOMBSTONE.get(), id);
        this.level = inv.player.level();
        this.pos = pos;
        this.owner = owner;
        TombstoneBlockEntity tb = level.getBlockEntity(pos) instanceof TombstoneBlockEntity be ? be : null;
        this.gear = tb != null ? tb.items() : new SimpleContainer(TombstoneBlockEntity.SLOTS);
        for (int i = 0; i < TombstoneBlockEntity.SLOTS; i++) addSlot(new Slot(gear, i, SLOT_X + i * 18, SLOT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return owner && s.is(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get()) && !holdsBlossom(); }
            @Override public boolean mayPickup(Player p) {
                if (!getItem().is(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get())) return true;
                if (!owner) return false;
                /* the blossom stays while its ghost is breeding (the server decides; the client's guess is put right) */
                if (p instanceof net.minecraft.server.level.ServerPlayer sp && tb != null && dev.frosthaven.ranchercompanion.ranch.Rebirth.breeding(sp.server, tb)) {
                    dev.frosthaven.ranchercompanion.ranch.Ranch.say(sp, "tomb.ghost_breeding");
                    return false;
                }
                return true;
            }
            @Override public int getMaxStackSize() { return 1; }
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
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        boolean blossom = s.is(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get());
        if (index >= n) {  /* from the inventory: a blossom to a free slot, its owner only, one at a time */
            if (!owner || !blossom || holdsBlossom()) return ItemStack.EMPTY;
            ItemStack one = s.split(1);
            if (!moveItemStackTo(one, 0, n, false)) { s.grow(1); return ItemStack.EMPTY; }
        } else if (blossom && !owner) return ItemStack.EMPTY;
        else if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    private boolean holdsBlossom() {
        for (int i = 0; i < TombstoneBlockEntity.SLOTS; i++) if (gear.getItem(i).is(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get())) return true;
        return false;
    }

    /** 1 Reclaim fallen drekki: the server checks the owner and the rest */
    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != 1) return false;
        if (p instanceof net.minecraft.server.level.ServerPlayer sp && level.getBlockEntity(pos) instanceof TombstoneBlockEntity be)
            dev.frosthaven.ranchercompanion.ranch.Rebirth.reclaim(sp, pos, be);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockEntity(pos) instanceof TombstoneBlockEntity && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
