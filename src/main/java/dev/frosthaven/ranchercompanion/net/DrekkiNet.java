package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The mod's packets. The client handler is set by the client side (ClientHooks), so this class loads on a server. */
public final class DrekkiNet {
    private DrekkiNet() {}

    /** set by the client: opens or refreshes the hatchery screen */
    public static java.util.function.Consumer<HatcheryView> onView = v -> {};

    public static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("1");
        r.playToClient(HatcheryView.TYPE, HatcheryView.CODEC, (v, ctx) -> ctx.enqueueWork(() -> onView.accept(v)));
        r.playToServer(HatcheryAction.TYPE, HatcheryAction.CODEC, (a, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p && p.blockPosition().closerThan(a.pos(), 8)) Ranch.act(p, a.pos(), a.kind(), a.id());
        }));
    }
}
