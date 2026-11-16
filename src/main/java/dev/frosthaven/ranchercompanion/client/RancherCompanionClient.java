package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.item.client.BardingColors;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client wiring: the renderer, per-tick particles and the showcase camera driver. Only loaded on the client. */
public final class RancherCompanionClient {
    private RancherCompanionClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> e.registerEntityRenderer(RancherCompanion.DREKKI.get(), DrekkiRenderer::new));
        modBus.addListener(RegisterColorHandlersEvent.Item.class, BardingColors::register);
        modBus.addListener(RegisterColorHandlersEvent.Block.class, BlockClient::blockColors);
        modBus.addListener(RegisterColorHandlersEvent.Item.class, BlockClient::itemColors);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, BlockClient::renderers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DrekkiParticles::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DrekkiAudio::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ShowcaseClient::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, BlockShowcaseClient::tick);
    }
}
