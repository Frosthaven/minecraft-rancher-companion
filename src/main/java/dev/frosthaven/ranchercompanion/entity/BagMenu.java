package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.block.DrekkiMenus;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * A plain bag (spec, Accessory: bags), worn by a drekki or placed in the world: its slots, 9 to a row, over the player's
 * inventory, as a chest shows them. The storage is on the bag item. (A Sophisticated bag opens in Sophisticated's screen.)
 */
public final class BagMenu extends AbstractContainerMenu {
    public final DrekkiEntity drekki;
    public final int rows;
    private final int size;
    /** the server's rules for where the bag is: still there and in reach, and what to save on close */
    private java.util.function.Predicate<Player> valid = p -> true;
    private Runnable closed = () -> {};

    public BagMenu(int id, Inventory inv, DrekkiEntity d, IItemHandler storage) {
        super(DrekkiMenus.BAG.get(), id);
        this.drekki = d;
        if (d != null) {
            valid = pl -> d.isAlive() && !d.isRemoved() && d.bagStorage() != null && pl.distanceToSqr(d) <= 144 && pl.getUUID().equals(d.owner());
            closed = () -> { if (!d.level().isClientSide) Ranch.touch(d); };  /* the bag's contents into the record */
        }
        this.size = storage.getSlots();
        this.rows = (size + 8) / 9;
        for (int i = 0; i < size; i++) addSlot(new SlotItemHandler(storage, i, 8 + i % 9 * 18, 18 + i / 9 * 18));
        int y = 18 + rows * 18 + 13;
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, y + 58));
    }

    /** the client's copy: the drekki by id, and an empty storage of the bag's size that the server fills */
    public static BagMenu client(int id, Inventory inv, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        var e = inv.player.level().getEntity(buf.readVarInt());
        return new BagMenu(id, inv, e instanceof DrekkiEntity d ? d : null, new ItemStackHandler(buf.readVarInt()));
    }

    /** a Sophisticated bag opens in Sophisticated's own screen (compat/sophisticated); a plain one in this one */
    public static void open(ServerPlayer p, DrekkiEntity d) {
        IItemHandler storage = d.bagStorage();
        if (storage == null) return;
        if (!(d.accessory().getItem() instanceof dev.frosthaven.ranchercompanion.item.BagItem)) { dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.open(p, d); return; }
        Component title = d.accessory().getHoverName();
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new BagMenu(id, inv, d, storage), title),
            buf -> { buf.writeVarInt(d.getId()); buf.writeVarInt(storage.getSlots()); });
    }

    /** a bag placed in the world, as a shulker box opens */
    public static void open(ServerPlayer p, dev.frosthaven.ranchercompanion.block.BagBlockEntity be) {
        IItemHandler storage = be.storage();
        if (storage == null) return;
        var pos = be.getBlockPos();
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> {
            BagMenu m = new BagMenu(id, inv, null, storage);
            m.valid = pl2 -> pl2.level().getBlockEntity(pos) == be && pl2.distanceToSqr(pos.getCenter()) <= 64;
            m.closed = be::setChanged;
            return m;
        }, be.bag().getHoverName()), buf -> { buf.writeVarInt(-1); buf.writeVarInt(storage.getSlots()); });
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index < size) { if (!moveItemStackTo(s, size, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!moveItemStackTo(s, 0, size, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) closed.run();
    }

    @Override
    public boolean stillValid(Player player) { return valid.test(player); }
}
