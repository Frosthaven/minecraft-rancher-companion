package dev.frosthaven.ranchercompanion.compat.create.client;

/** Create's job hats drawn on the drekki's crown (the client only, with Create loaded) */
public final class CreateClient {
    private CreateClient() {}

    public static void init(net.neoforged.bus.api.IEventBus modBus) {
        JobHatLayer.install();
        modBus.addListener(net.neoforged.neoforge.client.event.EntityRenderersEvent.AddLayers.class, e -> {
            var r = e.getRenderer(dev.frosthaven.ranchercompanion.RancherCompanion.DREKKI.get());
            if (r instanceof dev.frosthaven.ranchercompanion.client.DrekkiRenderer d) d.addLayer(new JobHatLayer(d));
        });
    }
}
