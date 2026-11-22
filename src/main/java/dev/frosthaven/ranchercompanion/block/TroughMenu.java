package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.FoodDiets;
import dev.frosthaven.ranchercompanion.look.Diet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The trough's screen: its line's food slots, one per trough from the line's left end to its right, over the player's
 * inventory. A slot takes only food a drekki eats, and only the diet the line holds (spec, Drekki Trough); the first
 * food in an empty line sets its diet.
 */
public final class TroughMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen (TroughScreen draws their frames at the same places) */
    public static final int INV_Y = 84, ROW = 5;

    private final Level level;
    private final BlockPos pos;
    private final java.util.List<BlockPos> line;
    private final int n;

    /** up to five slots in a row, a longer line in two rows, centered in the well */
    public static int slotX(int i, int n) { int per = n <= ROW ? n : (n + 1) / 2; return (176 - per * 18) / 2 + 1 + i % per * 18; }
    public static int slotY(int i, int n) { int per = n <= ROW ? n : (n + 1) / 2; return n <= ROW ? 37 : 28 + i / per * 18; }

    /** the server's menu: a slot per trough of the line, each that trough's own */
    public TroughMenu(int id, Inventory inv, BlockPos pos) { this(id, inv, pos, -1); }

    /** the client's menu: as many slots as the server says, filled by the server. A server before 0.4.9 names no
     *  number and sends the four slots a trough had then, so a game one release ahead still opens its troughs */
    public TroughMenu(int id, Inventory inv, BlockPos pos, int sent) {
        super(DrekkiMenus.TROUGH.get(), id);
        this.level = inv.player.level();
        this.pos = pos;
        this.line = TroughBlock.line(level, pos);
        this.n = level.isClientSide ? (sent > 0 ? sent : 4) : line.size();
        Container shown = new net.minecraft.world.SimpleContainer(n);
        for (int i = 0; i < n; i++) {
            BlockPos at = level.isClientSide ? pos : line.get(i);
            Container food = !level.isClientSide && level.getBlockEntity(at) instanceof TroughBlockEntity be ? be.container() : shown;
            addSlot(new Slot(food, food == shown ? i : 0, slotX(i, n), slotY(i, n)) {
                @Override
                public boolean mayPlace(ItemStack s) { return accepts(s); }

                @Override
                public void setChanged() {
                    super.setChanged();
                    TroughBlock.contentsChanged(level, at, getItem());
                }
            });
        }
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

    /** the line's food slots */
    public int foodSlots() { return n; }

    /** the diet of the trough's line, or null when it holds none yet */
    public Diet lineDiet() {
        var st = level.getBlockState(pos);
        if (!st.hasProperty(TroughBlock.DIET) || st.getValue(TroughBlock.DIET) == 0) return null;
        return Diet.of(st.getValue(TroughBlock.DIET) - 1);
    }

    boolean accepts(ItemStack s) {
        Diet d = level.isClientSide ? FoodDiets.byDefault(s) : FoodDiets.of(s);
        if (d == null) return false;
        Diet line = lineDiet();
        return line == null || line == d;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack s = slot.getItem(), copy = s.copy();
        if (index < n) { if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!accepts(s) || !moveItemStackTo(s, 0, n, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        /* the line as it was when the screen opened: a trough broken or joined since closes it */
        return player.distanceToSqr(pos.getCenter()) <= 64 && (level.isClientSide || TroughBlock.line(level, pos).equals(line));
    }
}
