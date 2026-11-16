package dev.frosthaven.ranchercompanion.showcase;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Builds the showcase world without a client: with {@code -Drancher_companion.buildworld=true} the dedicated server
 * force-loads the layout's chunks, spawns every group once, saves and stops. tools/showcase.py then hands the world on.
 */
public final class ShowcaseBuild {
    private ShowcaseBuild() {}

    public static final boolean ON = Boolean.getBoolean("rancher_companion.buildworld");
    /** chunks the layout covers: x -8..128, z -8..424 in blocks */
    private static final int CX0 = -1, CX1 = 8, CZ0 = -1, CZ1 = 26;
    private static int tick = -1;

    public static void onStarted(ServerStartedEvent e) {
        if (!ON) return;
        ServerLevel level = e.getServer().overworld();
        for (int cx = CX0; cx <= CX1; cx++) for (int cz = CZ0; cz <= CZ1; cz++) level.setChunkForced(cx, cz, true);
        level.setDayTime(6000);
        GameRules g = level.getGameRules(); MinecraftServer s = e.getServer();
        g.getRule(GameRules.RULE_DAYLIGHT).set(false, s); g.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, s);
        g.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, s);
        level.setWeatherParameters(100000, 0, false, false);
        tick = 0;
    }

    public static void onTick(ServerTickEvent.Post e) {
        if (tick < 0) return;
        tick++;
        ServerLevel level = e.getServer().overworld();
        if (tick == 40) {
            int n = level.getEntities(RancherCompanion.DREKKI.get(), d -> true).isEmpty() ? Showcase.spawn(level, Vec3.ZERO) : 0;
            RancherCompanion.LOG.info("showcase build: spawned {} drekki", n);
        }
        if (tick == 160) {
            for (int cx = CX0; cx <= CX1; cx++) for (int cz = CZ0; cz <= CZ1; cz++) level.setChunkForced(cx, cz, false);
            e.getServer().saveEverything(false, true, true);
            RancherCompanion.LOG.info("showcase build: saved, stopping");
            e.getServer().halt(false);
            tick = -1;
        }
    }
}
