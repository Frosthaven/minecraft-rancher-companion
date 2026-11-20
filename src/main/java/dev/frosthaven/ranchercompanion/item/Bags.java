package dev.frosthaven.ranchercompanion.item;

import net.minecraft.world.item.Item;

/**
 * Makes the bag items (spec, Accessory: bags). With Sophisticated Backpacks installed they are its backpack items, so its
 * storage and upgrades come with them (compat/sophisticated, reached only when the mod is loaded); otherwise plain bags.
 */
public final class Bags {
    private Bags() {}

    public static final String SOPHISTICATED = "sophisticatedbackpacks";

    public static boolean sophisticated() { return net.neoforged.fml.ModList.get().isLoaded(SOPHISTICATED); }

    public static Item create(int kind, Item.Properties props) {
        return sophisticated() ? dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.create(kind, props) : new BagItem(kind, props);
    }
}
