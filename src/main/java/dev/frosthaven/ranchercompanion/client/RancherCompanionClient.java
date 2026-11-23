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
        /* the ball is its particles (ElementalBall.tick) */
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, e -> e.registerEntityRenderer(RancherCompanion.ELEMENTAL_BALL.get(), net.minecraft.client.renderer.entity.NoopRenderer::new));
        modBus.addListener(RegisterColorHandlersEvent.Item.class, BardingColors::register);
        modBus.addListener(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional.class, PotionModels::additional);
        modBus.addListener(net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult.class, PotionModels::wrap);
        modBus.addListener(RegisterColorHandlersEvent.Block.class, BlockClient::blockColors);
        if (dev.frosthaven.ranchercompanion.item.Bags.sophisticated())
            modBus.addListener(RegisterColorHandlersEvent.Block.class, dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophColors::blocks);
        modBus.addListener(RegisterColorHandlersEvent.Item.class, BlockClient::itemColors);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, BlockClient::renderers);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DrekkiParticles::tick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.ItemTooltipEvent.class, ItemSubtext::onTooltip);
        /* Sophisticated's open-backpack key opens one's own drekki's bag while looking at it */
        if (dev.frosthaven.ranchercompanion.item.Bags.sophisticated())
            NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.client.event.ClientTickEvent.Pre.class, dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophKeys::onTick);
        modBus.addListener(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent.class,
            e -> {
                e.register(dev.frosthaven.ranchercompanion.block.DrekkiMenus.TROUGH.get(), dev.frosthaven.ranchercompanion.client.screen.TroughScreen::new);
                e.register(dev.frosthaven.ranchercompanion.block.DrekkiMenus.DREKKI.get(), dev.frosthaven.ranchercompanion.client.screen.DrekkiScreen::new);
                e.register(dev.frosthaven.ranchercompanion.block.DrekkiMenus.BAG.get(), dev.frosthaven.ranchercompanion.client.screen.BagScreen::new);
                e.register(dev.frosthaven.ranchercompanion.block.DrekkiMenus.TOMBSTONE.get(), dev.frosthaven.ranchercompanion.client.screen.TombstoneScreen::new);
                if (dev.frosthaven.ranchercompanion.item.Bags.sophisticated()) dev.frosthaven.ranchercompanion.compat.sophisticated.client.SophBagScreen.register(e);
            });
        /* Ponder's build guides, only with Ponder loaded (it comes with Create); compat/ponder is never touched otherwise */
        if (net.neoforged.fml.ModList.get().isLoaded("ponder")) dev.frosthaven.ranchercompanion.compat.ponder.DrekkiPonder.register();
        /* Create's job hats (a conductor, a stock keeper) on the drekki's crown, only with Create loaded */
        if (net.neoforged.fml.ModList.get().isLoaded("create")) dev.frosthaven.ranchercompanion.compat.create.client.CreateClient.init(modBus);
        dev.frosthaven.ranchercompanion.net.DrekkiNet.onView = dev.frosthaven.ranchercompanion.client.screen.HatcheryScreen::show;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, DrekkiAudio::tick);
        DrekkiDebug.register();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ShowcaseClient::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, SelfTest::tick);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, BlockShowcaseClient::tick);
    }
}
