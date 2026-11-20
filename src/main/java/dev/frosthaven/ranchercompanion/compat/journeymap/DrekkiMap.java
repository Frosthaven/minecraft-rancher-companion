package dev.frosthaven.ranchercompanion.compat.journeymap;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.net.DeathMark;
import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * JourneyMap (optional; spec, Tombstone): where one's drekki died, a death marker like the player's own (JourneyMap's
 * death X) in magenta. JourneyMap finds this class by its annotation and loads it only when it is installed.
 */
@JourneyMapPlugin(apiVersion = "2.0.0")
public final class DrekkiMap implements IClientPlugin {
    public static final int MAGENTA = 0xFF00FF;
    private static final ResourceLocation DEATH_ICON = ResourceLocation.fromNamespaceAndPath("journeymap", "textures/waypoint/icon/waypoint-death-icon.png");
    private static IClientAPI api;

    @Override public String getModId() { return RancherCompanion.ID; }

    @Override
    public void initialize(IClientAPI jm) {
        api = jm;
        dev.frosthaven.ranchercompanion.net.DrekkiNet.onDeathMark = DrekkiMap::mark;
    }

    public static void mark(DeathMark m) {
        if (api == null) return;
        String name = Component.translatable("waypoint.rancher_companion.died", m.name()).getString();
        Waypoint w = WaypointFactory.createClientWaypoint(RancherCompanion.ID, m.pos(), name, m.dimension(), true);
        w.setColor(MAGENTA);
        w.setIconResourceLoctaion(DEATH_ICON);
        w.setIconColor(MAGENTA);
        api.addWaypoint(RancherCompanion.ID, w);
    }

    /** the self test: the markers this mod has added */
    public static java.util.List<? extends Waypoint> marksForTest() { return api == null ? java.util.List.of() : api.getWaypoints(RancherCompanion.ID); }
}
