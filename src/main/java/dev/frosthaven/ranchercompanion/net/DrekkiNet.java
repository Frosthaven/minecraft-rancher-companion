package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.ranch.Ranch;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** The mod's packets. The client handler is set by the client side (ClientHooks), so this class loads on a server. */
public final class DrekkiNet {
    private DrekkiNet() {}

    /** set by the client: opens or refreshes the hatchery screen */
    public static java.util.function.Consumer<HatcheryView> onView = v -> {};
    /** set by the client: marks where the player's drekki died (JourneyMap, when installed) */
    public static java.util.function.Consumer<DeathMark> onDeathMark = m -> {};

    public static void register(RegisterPayloadHandlersEvent e) {
        var r = e.registrar("2");  /* 2: the hatchery action carries a second drekki (pairing), its view the rest and pairing state */
        r.playToClient(HatcheryView.TYPE, HatcheryView.CODEC, (v, ctx) -> ctx.enqueueWork(() -> onView.accept(v)));
        r.playToClient(DeathMark.TYPE, DeathMark.CODEC, (m, ctx) -> ctx.enqueueWork(() -> onDeathMark.accept(m)));
        r.playToServer(HatcheryAction.TYPE, HatcheryAction.CODEC, (a, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p && p.blockPosition().closerThan(a.pos(), 8)) Ranch.act(p, a.pos(), a.kind(), a.id(), a.other(), a.trait());
        }));
        /* a decorative tombstone's message, typed in its screen */
        r.playToServer(TombMessage.TYPE, TombMessage.CODEC, (m, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity.editMessage(p, m.pos(), m.text());
        }));
        /* the open-backpack key on one's own drekki: its bag, if it wears one and is within reach */
        r.playToServer(OpenBag.TYPE, OpenBag.CODEC, (o, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p && p.level().getEntity(o.entityId()) instanceof dev.frosthaven.ranchercompanion.entity.DrekkiEntity d
                && p.getUUID().equals(d.owner()) && p.distanceToSqr(d) <= 64 && d.bagStorage() != null) dev.frosthaven.ranchercompanion.entity.BagMenu.open(p, d);
        }));
    }
}
