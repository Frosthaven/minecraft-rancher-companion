package dev.frosthaven.ranchercompanion.item.client;

import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Tints the barding icon's plate layer (layer0) by its dye, or by the tier's own plate color when undyed. */
public final class BardingColors {
    private BardingColors() {}

    public static void register(RegisterColorHandlersEvent.Item event) {
        DrekkiItems.BARDING.forEach((tier, item) -> event.register(
            (stack, layer) -> layer == 0 ? FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, tier.plate)) : -1, item.get()));
    }
}
