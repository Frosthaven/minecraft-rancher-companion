package dev.frosthaven.ranchercompanion.compat.sophisticated.client;

import dev.frosthaven.ranchercompanion.compat.sophisticated.SophBlocks;
import dev.frosthaven.ranchercompanion.look.Gear;
import net.minecraft.util.FastColor;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackBlockEntity;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;

/** A placed bag (Sophisticated's block): leather and trim in the bag's colors, its metal by tier, as the item draws them. */
public final class SophColors {
    private SophColors() {}

    public static void blocks(RegisterColorHandlersEvent.Block e) {
        for (int k = 1; k < SophBlocks.PLACED.size(); k++) {
            int kind = k;
            e.register((state, level, pos, tint) -> {
                BackpackBlockEntity be = level != null && pos != null && level.getBlockEntity(pos) instanceof BackpackBlockEntity b ? b : null;
                return switch (tint) {
                    case 0 -> FastColor.ARGB32.opaque(Gear.bagMain(be == null ? -1 : undyed(be.getMainColor(), BackpackItem.DEFAULT_MAIN_COLOR)));
                    case 1 -> FastColor.ARGB32.opaque(Gear.bagTrim(be == null ? -1 : undyed(be.getAccentColor(), BackpackItem.DEFAULT_ACCENT_COLOR)));
                    case 2 -> FastColor.ARGB32.opaque(Gear.BAG_METAL[kind]);
                    default -> -1;
                };
            }, SophBlocks.PLACED.get(k).get());
        }
    }

    /** Sophisticated's default color (or none) means undyed: the bag's own leather */
    private static int undyed(int c, int def) { return c == -1 || (c & 0xFFFFFF) == (def & 0xFFFFFF) ? -1 : c & 0xFFFFFF; }
}
