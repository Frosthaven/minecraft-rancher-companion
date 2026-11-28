package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.net.HatcheryView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The hatchery screen's container (spec, Checkout: Roster; Breeding: Pairing): the player's inventory and the Breeding
 * tab's tonic slot. Everything else on the screen is the {@link HatcheryView} the server sends (and sends again after each
 * action). The tonic slot holds one tonic while the screen is open; Pair uses it up, closing gives it back. The nest slot
 * (Roster tab) is the hatchery's egg itself: an Intriguing Egg goes in, and one not Ready yet comes out with its
 * incubation (Shane, 2026-11-27); it is the last slot, so a screen one release ahead keeps the others' numbers. Menu
 * buttons: 0 the Roster tab, 1 the Breeding tab (the tonic slot shows on Breeding only).
 */
public final class HatcheryMenu extends AbstractContainerMenu {
    /** the items the tonic slot takes: the tonics that work so far (the Starlight Tonic) */
    public static final TagKey<Item> TONICS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "tonics"));
    /** where the slots sit on the screen's texture (tools/screen_textures.py hatchery, hatchery_breed) */
    public static final int W = 232, TOP = 144, H = TOP + 89, TONIC_X = 10, TONIC_Y = 101, INV_X = (W - 176) / 2 + 8, INV_Y = TOP + 9;
    private static final int TONIC = 0, INV = 1;
    public static final int NEST = 37;
    /** the nest slot's place on the screen (the egg's spot in the nest) */
    public static final int NEST_X = 32, NEST_Y = 40;

    public final BlockPos pos;
    /** the first view (client); the screen keeps the newer ones */
    public final HatcheryView view;
    public boolean breeding;
    /** a pairing runs at this hatchery: the tonic slot is shut (Shane, 2026-11-26). The server reads the hatchery; the
     *  screen sets it from each view it gets. */
    public boolean pairing;
    private final SimpleContainer tonic = new SimpleContainer(1);
    /** the client's copy of the nest's flags, set by the screen from each view */
    public boolean readyEgg;

    public HatcheryMenu(int id, Inventory inv, BlockPos pos, HatcheryView view) {
        super(DrekkiMenus.HATCHERY.get(), id);
        this.pos = pos;
        this.view = view;
        addSlot(new Slot(tonic, 0, TONIC_X, TONIC_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(TONICS) && !pairing(inv.player); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return breeding && !pairing(inv.player); }
        });
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, 9 + r * 9 + c, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
        Player pl = inv.player;
        addSlot(new Slot(pl.level().isClientSide ? new SimpleContainer(1) : new Nest(pl), 0, NEST_X, NEST_Y) {
            @Override public boolean mayPlace(ItemStack s) { return s.is(dev.frosthaven.ranchercompanion.item.DrekkiItems.INTRIGUING_EGG.get()) && !hasItem() && !pairing(pl); }
            @Override public boolean mayPickup(Player p) { return !ready(p); }
            @Override public int getMaxStackSize() { return 1; }
            @Override public boolean isActive() { return !breeding; }
        });
    }

    private boolean ready(Player p) {
        if (p.level().isClientSide) return readyEgg;
        return p.level().getBlockEntity(pos) instanceof HatcheryBlockEntity be && be.hasEgg()
            && dev.frosthaven.ranchercompanion.look.Incubation.ready(be.incubation());
    }

    /** the hatchery's egg as a one-slot container (server): out with its incubation, in as Ranch.putEgg puts it, and
     *  the player's screen view refreshed after each change (Ready, the hatch checks) */
    private final class Nest implements net.minecraft.world.Container {
        private final Player p;
        Nest(Player p) { this.p = p; }
        private HatcheryBlockEntity be() { return p.level().getBlockEntity(pos) instanceof HatcheryBlockEntity be ? be : null; }
        private void changed() { if (p instanceof ServerPlayer sp) dev.frosthaven.ranchercompanion.ranch.Ranch.refreshFor(sp, pos); }
        @Override public int getContainerSize() { return 1; }
        @Override public boolean isEmpty() { HatcheryBlockEntity be = be(); return be == null || !be.hasEgg(); }
        @Override public ItemStack getItem(int i) { HatcheryBlockEntity be = be(); return be == null ? ItemStack.EMPTY : be.egg().copy(); }
        @Override public ItemStack removeItem(int i, int n) {
            HatcheryBlockEntity be = be();
            if (be == null || !be.hasEgg() || n <= 0) return ItemStack.EMPTY;
            ItemStack s = be.takeEgg();
            changed();
            return s;
        }
        @Override public ItemStack removeItemNoUpdate(int i) { return removeItem(i, 1); }
        @Override public void setItem(int i, ItemStack s) {
            HatcheryBlockEntity be = be();
            if (be == null) return;
            if (s.isEmpty()) { if (be.hasEgg()) be.takeEgg(); }
            else if (!be.hasEgg() && !be.breeding()) be.putEgg(s);
            changed();
        }
        @Override public int getMaxStackSize() { return 1; }
        @Override public void setChanged() {}
        @Override public boolean stillValid(Player pl) { return HatcheryMenu.this.stillValid(pl); }
        @Override public void clearContent() {}
    }

    public static HatcheryMenu client(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        HatcheryView v = HatcheryView.CODEC.decode(buf);
        return new HatcheryMenu(id, inv, v.pos(), v);
    }

    /** opens the screen with its first view */
    public static void open(ServerPlayer p, BlockPos pos, HatcheryView view) {
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new HatcheryMenu(id, inv, pos, null), view.title()),
            buf -> HatcheryView.CODEC.encode(buf, view));
    }

    private boolean pairing(Player p) {
        if (p.level().isClientSide) return pairing;
        return p.level().getBlockEntity(pos) instanceof HatcheryBlockEntity be && be.breedA() != null;
    }

    /** Pair takes the tonic: the hatchery holds it while they breed */
    public ItemStack takeTonic() { return tonic.removeItem(0, 1); }

    public ItemStack tonic() { return tonic.getItem(0); }

    @Override
    public boolean clickMenuButton(Player p, int id) {
        if (id != 0 && id != 1) return false;
        breeding = id == 1;
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        Slot s = slots.get(index);
        if (!s.hasItem()) return ItemStack.EMPTY;
        ItemStack st = s.getItem(), before = st.copy();
        if (index == NEST) {  /* the egg itself out of the nest, with its incubation; back in when the inventory is full */
            if (!s.mayPickup(p)) return ItemStack.EMPTY;
            ItemStack egg = s.remove(1);
            if (!moveItemStackTo(egg, INV, NEST, true)) { s.set(egg); return ItemStack.EMPTY; }
            return ItemStack.EMPTY;
        }
        if (st.is(dev.frosthaven.ranchercompanion.item.DrekkiItems.INTRIGUING_EGG.get()) && !breeding && slots.get(NEST).mayPlace(st)) {
            slots.get(NEST).setByPlayer(st.split(1));
            if (st.isEmpty()) s.setByPlayer(ItemStack.EMPTY); else s.setChanged();
            return ItemStack.EMPTY;
        }
        if (index == TONIC) { if (!moveItemStackTo(st, INV, NEST, true)) return ItemStack.EMPTY; }
        else if (!breeding || pairing(p) || !st.is(TONICS) || !moveItemStackTo(st, TONIC, TONIC + 1, false)) return ItemStack.EMPTY;
        if (st.isEmpty()) s.setByPlayer(ItemStack.EMPTY); else s.setChanged();
        return before;
    }

    @Override
    public boolean stillValid(Player p) {
        return p.level().getBlockEntity(pos) instanceof HatcheryBlockEntity && p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    @Override
    public void removed(Player p) {
        super.removed(p);
        clearContainer(p, tonic);  /* the tonic goes back when the screen closes */
    }
}
