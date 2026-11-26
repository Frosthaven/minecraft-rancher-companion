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
 * action). The tonic slot holds one tonic while the screen is open; Pair uses it up, closing gives it back. Menu buttons:
 * 0 the Roster tab, 1 the Breeding tab (the tonic slot shows on Breeding only).
 */
public final class HatcheryMenu extends AbstractContainerMenu {
    /** the items the tonic slot takes: the tonics that work so far (the Starlight Tonic) */
    public static final TagKey<Item> TONICS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "tonics"));
    /** where the slots sit on the screen's texture (tools/screen_textures.py hatchery, hatchery_breed) */
    public static final int W = 232, TOP = 144, H = TOP + 89, TONIC_X = 10, TONIC_Y = 101, INV_X = (W - 176) / 2 + 8, INV_Y = TOP + 9;
    private static final int TONIC = 0, INV = 1;

    public final BlockPos pos;
    /** the first view (client); the screen keeps the newer ones */
    public final HatcheryView view;
    public boolean breeding;
    /** a pairing runs at this hatchery: the tonic slot is shut (Shane, 2026-11-26). The server reads the hatchery; the
     *  screen sets it from each view it gets. */
    public boolean pairing;
    private final SimpleContainer tonic = new SimpleContainer(1);

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
        if (index == TONIC) { if (!moveItemStackTo(st, INV, slots.size(), true)) return ItemStack.EMPTY; }
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
