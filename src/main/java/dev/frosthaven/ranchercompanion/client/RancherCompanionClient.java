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
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent.class, ItemSubtext::onTooltip);
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class,
            e -> {
                e.register(dev.frosthaven.ranchercompanion.block.DrekkiMenus.TROUGH.get(), dev.frosthaven.ranchercompanion.client.screen.TroughScreen::new);
                e.register(dev.frosthaven.ranchercompanion.block.DrekkiMenus.DREKKI.get(), dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen::new);
            });
        /* Ponder's build guides, only with Ponder loaded (it comes with Create); compat/ponder is never touched otherwise */
        if (net.neoforged.fml.ModList.get().isLoaded("ponder")) dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.register();
        dev.frosthaven.ranchercompanion.net.DrekkiNet.onView = dev.frosthaven.ranchercompanion.client.screen.HatcheryScreen::show;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DrekkiAudio::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ShowcaseClient::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, SelfTest::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, BlockShowcaseClient::tick);
    }
}
