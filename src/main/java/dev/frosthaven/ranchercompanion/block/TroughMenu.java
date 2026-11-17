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
 * The trough's screen: its four food slots over the player's inventory. A slot takes only food a drekki eats, and
 * only the diet the trough's line holds (spec, Drekki Trough); the first food in an empty line sets its diet.
 */
public final class TroughMenu extends AbstractContainerMenu {
    /** where the slots sit on the screen's texture (TroughScreen draws the same layout) */
    public static final int SLOT_X = 53, SLOT_Y = 34, INV_Y = 84;

    private final Level level;
    private final BlockPos pos;
    private final Container food;

    public TroughMenu(int id, Inventory inv, BlockPos pos) {
        super(DrekkiMenus.TROUGH.get(), id);
        this.level = inv.player.level();
        this.pos = pos;
        this.food = level.getBlockEntity(pos) instanceof TroughBlockEntity be ? be.container() : new net.minecraft.world.SimpleContainer(TroughBlockEntity.SLOTS);
        for (int i = 0; i < TroughBlockEntity.SLOTS; i++) addSlot(new Slot(food, i, SLOT_X + i * 18, SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack s) { return accepts(s); }

            @Override
            public void setChanged() {
                super.setChanged();
                TroughBlock.contentsChanged(level, pos, getItem());
            }
        });
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

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
        int n = TroughBlockEntity.SLOTS;
        if (index < n) { if (!moveItemStackTo(s, n, slots.size(), true)) return ItemStack.EMPTY; }
        else if (!accepts(s) || !moveItemStackTo(s, 0, n, false)) return ItemStack.EMPTY;
        if (s.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return level.getBlockEntity(pos) instanceof TroughBlockEntity && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
