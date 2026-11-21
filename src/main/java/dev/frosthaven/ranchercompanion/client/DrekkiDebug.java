package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.Debug;
import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The game's side of {@link Debug}: every drekki this game receives or drops, each first draw, and /drekkidebug, which
 * lists the drekki this game holds right now and when each was last drawn. Registered only with debugging on.
 */
public final class DrekkiDebug {
    private DrekkiDebug() {}

    /** the game tick each drekki (by entity id) was last drawn */
    private static final Map<Integer, Long> DRAWN = new HashMap<>();

    public static void register() {
        if (!Debug.ON) return;
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, e -> {
            if (e.getLevel().isClientSide() && e.getEntity() instanceof DrekkiEntity d) Debug.log("game: received {} uuid {}", Debug.of(d), d.getUUID());
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent.class, e -> {
            if (e.getLevel().isClientSide() && e.getEntity() instanceof DrekkiEntity d) { Debug.log("game: dropped {} ({})", Debug.of(d), d.getRemovalReason()); DRAWN.remove(d.getId()); }
        });
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.RegisterClientCommandsEvent.class, e ->
            e.getDispatcher().register(Commands.literal("drekkidebug").executes(c -> { dump(); return 1; })));
    }

    /** called for every drekki drawn */
    public static void drawn(DrekkiEntity d) {
        if (!Debug.ON) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (DRAWN.put(d.getId(), mc.level.getGameTime()) == null) Debug.log("game: first draw of {}, model valid {}", Debug.of(d), DrekkiModels.get(d).baked.alive());
    }

    private static void dump() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        int n = 0;
        Debug.log("game: /drekkidebug, you at {} in {}", mc.player.blockPosition().toShortString(), mc.level.dimension().location());
        for (var en : mc.level.entitiesForRendering()) {
            if (!(en instanceof DrekkiEntity d)) continue;
            n++;
            Long at = DRAWN.get(d.getId());
            Debug.log("game: holds {} uuid {}, {} blocks away, yours {}, staying {}, removed {}, invisible {}, model valid {}, last drawn {}", Debug.of(d), d.getUUID(),
                Math.round(d.distanceTo(mc.player)), mc.player.getUUID().equals(d.owner()), d.isStaying(), d.isRemoved(), d.isInvisible(), DrekkiModels.get(d).baked.alive(),
                at == null ? "never" : (mc.level.getGameTime() - at) + " ticks ago");
        }
        mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Drekki debug: " + n + " drekki in this game's world, written to the log"), false);
        RancherCompanion.LOG.info("[drekki debug] game: {} drekki held", n);
    }
}
