package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.block.DrekkiMenus;
import dev.frosthaven.ranchercompanion.item.BardingItem;
import dev.frosthaven.ranchercompanion.item.DrekkiBag;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Gear;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The drekki screen (spec, Drekki screen): its equipment and the player's inventory. The hat slot is the drekki's hat;
 * from stage 2 a barding slot (barding of its own stage) and an accessory slot (a bag; spec, Accessory). Putting an item
 * in wears it, taking it out takes it off. Menu buttons: the stances 0..2 (Aggressive, Reactive, Passive), the trait
 * offer's yes and no 3 and 4, and the bag 5 (a right-click on the bag in the accessory slot opens it), sent by the game's
 * own button packet. A ghost's screen is read-only: its slots and stance are locked (spec, Ghost drekki).
 */
public final class DrekkiMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen's texture (tools/screen_textures.py drekki; DrekkiScreen draws the same layout) */
    public static final int HAT_X = 8, HAT_Y = 27, BARDING_Y = 47, ACCESSORY_Y = 67;
    /** the inventory: vanilla's 176 wide, centered under the 220-wide top */
    public static final int INV_X = 22 + 8, INV_Y = 137;

    public final DrekkiEntity drekki;
    /** the layout this menu was opened with: the barding and accessory slots from stage 2 */
    public final boolean gear;
    /** opened from the chat offer: the screen starts on its Traits tab */
    public final boolean traitsTab;
    private final SimpleContainer hat = new SimpleContainer(1), barding = new SimpleContainer(1), accessory = new SimpleContainer(1);

    public DrekkiMenu(int id, Inventory inv, DrekkiEntity d) { this(id, inv, d, false); }

    public DrekkiMenu(int id, Inventory inv, DrekkiEntity d, boolean traitsTab) {
        super(DrekkiMenus.DREKKI.get(), id);
        this.drekki = d;
        this.traitsTab = traitsTab;
        this.gear = d != null && d.stage() >= 2;
        if (d != null) {
            hat.setItem(0, d.hat() > 0 ? DrekkiItems.hatStack(d.hat()) : ItemStack.EMPTY);
            barding.setItem(0, d.bardingItem());
            accessory.setItem(0, d.accessory());
        }
        if (d != null && !d.level().isClientSide) {
            hat.addListener(c -> {
                int now = DrekkiItems.hatIndex(c.getItem(0));
                if (now != d.hat()) { d.setGear(now, d.barding(), d.dye()); Ranch.touch(d); }
            });
            barding.addListener(c -> { if (c.getItem(0) != d.bardingItem()) { d.setBardingItem(c.getItem(0)); Ranch.touch(d); } });
            accessory.addListener(c -> { if (c.getItem(0) != d.accessory()) { d.setAccessory(c.getItem(0)); Ranch.touch(d); } });
        }
        addSlot(new Slot(hat, 0, HAT_X, HAT_Y) {
            @Override public boolean mayPlace(ItemStack s) { return !locked() && DrekkiItems.hatIndex(s) > 0; }
            @Override public boolean mayPickup(Player p) { return !locked(); }
            @Override public int getMaxStackSize() { return 1; }
        });
        if (gear) {
            addSlot(new Slot(barding, 0, HAT_X, BARDING_Y) {
                @Override public boolean mayPlace(ItemStack s) { return !locked() && fitsBarding(d, s); }
                @Override public boolean mayPickup(Player p) { return !locked() && !dev.frosthaven.ranchercompanion.item.BardingEnchants.bound(getItem(), p); }
                @Override public int getMaxStackSize() { return 1; }
            });
            addSlot(new Slot(accessory, 0, HAT_X, ACCESSORY_Y) {
                @Override public boolean mayPlace(ItemStack s) { return !locked() && fitsAccessory(d, s); }
                @Override public boolean mayPickup(Player p) { return !locked(); }
                @Override public int getMaxStackSize() { return 1; }
            });
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, INV_X + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, INV_X + c * 18, INV_Y + 58));
    }

    /** barding of the drekki's own stage (spec, Barding: split by stage) */
    public static boolean fitsBarding(DrekkiEntity d, ItemStack s) { return d != null && s.getItem() instanceof BardingItem b && b.stage == d.stage(); }

    /** a bag the drekki is old enough to wear: the satchel from stage 2, a saddlebag from stage 3 */
    public static boolean fitsAccessory(DrekkiEntity d, ItemStack s) {
        int kind = DrekkiBag.kindOf(s);
        return d != null && kind > 0 && Gear.BAG_STAGE[kind] <= d.stage();
    }

    private int invStart() { return gear ? 3 : 1; }

    /** a ghost's screen is read-only (spec, Ghost drekki): no gear on or off, no stance */
    public boolean locked() { return drekki != null && drekki.ghost(); }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        int inv = invStart();
        if (locked()) return ItemStack.EMPTY;
        if (index < inv) { if (!moveItemStackTo(s, inv, slots.size(), true)) return ItemStack.EMPTY; }
        else if (DrekkiItems.hatIndex(s) > 0 && !slots.get(0).hasItem()) { if (!moveItemStackTo(s, 0, 1, false)) return ItemStack.EMPTY; }
        else if (gear && fitsBarding(drekki, s) && !slots.get(1).hasItem()) { if (!moveItemStackTo(s, 1, 2, false)) return ItemStack.EMPTY; }
        else if (gear && fitsAccessory(drekki, s) && !slots.get(2).hasItem()) { if (!moveItemStackTo(s, 2, 3, false)) return ItemStack.EMPTY; }
        else return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    public static final int LEARN = 3, PASS = 4, BAG = 5, STAY = 6, FOLLOW = 7, RECALL = 8;

    /** the stance buttons (stage 2 on; spec, Combat), the offer's yes and no, and the bag */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (drekki == null) return false;
        if (id == LEARN || id == PASS) return dev.frosthaven.ranchercompanion.trait.Traits.answer(drekki, id == LEARN);
        if (id == BAG) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp) || drekki.bagStorage() == null) return false;
            BagMenu.open(sp, drekki);
            return true;
        }
        if (id == RECALL) {  /* employed: off its job, back with its owner (spec, Go here: Employed) */
            if (locked() || !(player instanceof net.minecraft.server.level.ServerPlayer sp) || !sp.getUUID().equals(drekki.owner())) return false;
            Ranch.recallFromJob(sp, drekki);
            return true;
        }
        if (id == STAY || id == FOLLOW) {  /* every stage; only its owner's checked-out one */
            if (locked() || !drekki.isOut() || !(player instanceof net.minecraft.server.level.ServerPlayer sp) || !sp.getUUID().equals(drekki.owner())) return false;
            if (Ranch.employed(drekki)) { Ranch.tell(sp, "gohere.employed", drekki); return true; }  /* recalled at its hatchery */
            Ranch.setStay(sp, id == STAY);
            return true;
        }
        if (locked() || drekki.stage() < 2 || id < 0 || id >= Life.Stance.values().length) return false;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp && Ranch.employed(drekki)) { Ranch.tell(sp, "gohere.employed", drekki); return true; }  /* Passive at work */
        drekki.setStance(Life.Stance.values()[id]);
        Ranch.touch(drekki);
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (drekki != null && !drekki.level().isClientSide) Ranch.touch(drekki);
    }

    /** 12 blocks: the chat offer opens it from as far as the 10 it learns in */
    @Override
    public boolean stillValid(Player player) {
        return drekki != null && drekki.isAlive() && !drekki.isRemoved() && player.distanceToSqr(drekki) <= 144 && player.getUUID().equals(drekki.owner());
    }
}
