package dev.frosthaven.ranchercompanion.item;

import net.minecraft.world.item.ItemStack;

/** A bag for the drekki's accessory slot, plain or a Sophisticated backpack: its Gear.BAGS index. */
public interface DrekkiBag {
    int kind();

    /** the bag kind of a stack, 0 when it is not a bag */
    static int kindOf(ItemStack s) { return s.getItem() instanceof DrekkiBag b ? b.kind() : 0; }
}
