package dev.frosthaven.ranchercompanion.item;

import dev.frosthaven.ranchercompanion.look.Gear;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.items.ComponentItemHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * The two colors of a dyeable drekki item (spec, Dyeing): main in vanilla's dyed color, trim in TRIM_COLOR; a
 * Sophisticated bag keeps its own two colors. -1 is undyed. Also a bag's storage, wherever it lives.
 */
public final class Dyeing {
    private Dyeing() {}

    /** plain items dyed by our recipe: barding, and the bags when Sophisticated is not installed */
    public static boolean ours(ItemStack s) { return s.getItem() instanceof BardingItem || s.getItem() instanceof BagItem; }

    public static int main(ItemStack s) {
        if (s.isEmpty()) return -1;
        if (s.getItem() instanceof DrekkiBag && !(s.getItem() instanceof BagItem)) return dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.main(s);
        DyedItemColor c = s.get(DataComponents.DYED_COLOR);
        return c == null ? -1 : c.rgb() & 0xFFFFFF;
    }

    public static int trim(ItemStack s) {
        if (s.isEmpty()) return -1;
        if (s.getItem() instanceof DrekkiBag && !(s.getItem() instanceof BagItem)) return dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.trim(s);
        Integer c = s.get(DrekkiData.TRIM_COLOR.get());
        return c == null ? -1 : c;
    }

    /** both dyes off, wherever the item keeps them (a Sophisticated bag in its own components) */
    public static void clear(ItemStack s) {
        if (s.getItem() instanceof DrekkiBag && !(s.getItem() instanceof BagItem)) dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.undye(s);
        else set(s, -1, -1);
    }

    public static void set(ItemStack s, int main, int trim) {
        if (main >= 0) s.set(DataComponents.DYED_COLOR, new DyedItemColor(main, false)); else s.remove(DataComponents.DYED_COLOR);
        if (trim >= 0) s.set(DrekkiData.TRIM_COLOR.get(), trim); else s.remove(DrekkiData.TRIM_COLOR.get());
    }

    /** a bag's storage: on the item (plain), or Sophisticated's */
    public static IItemHandler storage(ItemStack bag) {
        if (bag.getItem() instanceof BagItem b) return new ComponentItemHandler(bag, DataComponents.CONTAINER, Gear.BAG_SLOTS[b.kind]);
        return dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.handler(bag);
    }
}
