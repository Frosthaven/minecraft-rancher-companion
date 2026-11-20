package dev.frosthaven.ranchercompanion.item.client;

import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Tints the barding icon's plate layer (layer0) by its dye, or by the tier's own plate color when undyed. */
public final class BardingColors {
    private BardingColors() {}

    public static void register(RegisterColorHandlersEvent.Item event) {
        /* barding (its 3D heap): the plates (tint 0) by the main dye or the tier's plate color, the rims (1) by the trim dye
           or the tier's rim color, as on the drekki; straps and buckles untinted */
        DrekkiItems.allBarding().forEach(item -> event.register((stack, layer) -> switch (layer) {
            case 0 -> FastColor.ARGB32.opaque(DyedItemColor.getOrDefault(stack, item.get().tier.plate));
            case 1 -> FastColor.ARGB32.opaque(dev.frosthaven.ranchercompanion.look.Gear.rim(item.get().tier.gearIndex(), dev.frosthaven.ranchercompanion.item.Dyeing.trim(stack)));
            default -> -1;
        }, item.get()));
        /* bags (their 3D model): leather (tint 0) by the main dye, flaps and strap (1) by the trim dye, metal (2) by the tier */
        DrekkiItems.BAGS.forEach(item -> event.register((stack, layer) -> switch (layer) {
            case 0 -> FastColor.ARGB32.opaque(dev.frosthaven.ranchercompanion.look.Gear.bagMain(dev.frosthaven.ranchercompanion.item.Dyeing.main(stack)));
            case 1 -> FastColor.ARGB32.opaque(dev.frosthaven.ranchercompanion.look.Gear.bagTrim(dev.frosthaven.ranchercompanion.item.Dyeing.trim(stack)));
            case 2 -> FastColor.ARGB32.opaque(dev.frosthaven.ranchercompanion.look.Gear.BAG_METAL[dev.frosthaven.ranchercompanion.item.DrekkiBag.kindOf(stack)]);  /* the 3D bag's metal */
            default -> -1;
        }, item.get()));
    }
}
