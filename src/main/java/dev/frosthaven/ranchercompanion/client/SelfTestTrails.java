package dev.frosthaven.ranchercompanion.client;

import static dev.frosthaven.ranchercompanion.client.SelfTest.*;
import static dev.frosthaven.ranchercompanion.client.SelfTestTraits.check;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.trail.TrailWalks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Footprint trails in the real game (-Drancher_companion.selftest.trails=true; spec, Trails): the hatched pup grown to a
 * Fledgling and checked in, a 400-block trail east of its hatchery; it walks out of its pen along the prints; with the
 * player far away its ground stops ticking and it walks on as a record; at the record's print it appears again, one
 * drekki; then the Ponder scene, when Ponder is loaded.
 */
final class SelfTestTrails {
    private SelfTestTrails() {}

    static final int LENGTH = 400;
    private static UUID id;
    private static BlockPos start;
    private static int recordX;

    private static void onServer(java.util.function.Consumer<ServerLevel> f) {
        var s = mc().getSingleplayerServer();
        if (s != null) s.execute(() -> f.accept(s.overworld()));
    }

    private static TrailWalks.Walk walk() {
        var s = mc().getSingleplayerServer();
        return s == null || id == null ? null : TrailWalks.get(s).of(id);
    }

    private static DrekkiEntity live() { return id == null ? null : Ranch.liveOf(id); }

    static void steps(BlockPos hatch) {
        start = hatch.offset(2, 0, 0);
        quiet("trail_setup", 20, () -> {
            mc().setScreen(null);  /* the hatchery screen from the check-out */
            onServer(level -> {
                var p = mc().getSingleplayerServer().getPlayerList().getPlayers().get(0);
                DrekkiEntity d = SelfTestTraits.sd();
                if (d == null) return;
                id = d.drekkiId();
                d.setLook(2, d.line(), d.diet(), d.astral(), d.horn());  /* a Fledgling: pups keep to the pen */
                d.life.food = dev.frosthaven.ranchercompanion.life.Life.FOOD_MAX;
                Ranch.act(p, hatch, Ranch.Action.CHECK_IN, null);
            });
            cmd("time set 1000");
            cmd("gamerule doDaylightCycle false");
            /* the trail: a stone strip and prints facing east, 400 blocks; its chunks held loaded only while it is built */
            for (int x = 0; x < LENGTH; x += 64) {
                int a = start.getX() + x, b = Math.min(start.getX() + LENGTH - 1, a + 63);
                cmd("forceload add " + a + " " + start.getZ() + " " + b + " " + start.getZ());
                cmd("fill " + a + " " + (start.getY() - 1) + " " + (start.getZ() - 2) + " " + b + " " + (start.getY() - 1) + " " + (start.getZ() + 2) + " minecraft:stone");
                cmd("fill " + a + " " + start.getY() + " " + (start.getZ() - 2) + " " + b + " " + (start.getY() + 3) + " " + (start.getZ() + 2) + " minecraft:air");
                cmd("fill " + a + " " + start.getY() + " " + start.getZ() + " " + b + " " + start.getY() + " " + start.getZ() + " rancher_companion:drekki_footprints[facing=east]");
            }
            cmd("forceload remove all");
        });
        check("trail_in_the_graph", 20, 10, () -> {}, "prints", () -> {
            var s = mc().getSingleplayerServer(); return s == null ? -1 : dev.frosthaven.ranchercompanion.trail.Trails.get(s.overworld()).printCount();
        }, () -> { var s = mc().getSingleplayerServer(); return s != null && dev.frosthaven.ranchercompanion.trail.Trails.get(s.overworld()).printCount() >= LENGTH; });
        quiet("walk_starts", 10, () -> onServer(level -> {
            DrekkiEntity d = live();
            if (d != null && walk() == null) TrailWalks.get(level.getServer()).start(d, start);
        }));
        /* the camera follows it, beside the trail */
        check("walks_out_of_its_pen", 20, 40, () -> {
            DrekkiEntity d = pup();
            if (d != null) cmd("tp @s " + (d.getX() - 3) + " " + (start.getY() + 2) + " " + (start.getZ() + 5.5) + " facing " + d.getX() + " " + (start.getY() + 0.5) + " " + d.getZ());
        }, "blocks out along the trail", () -> live() == null ? "none" : String.format("%.1f", live().getX() - start.getX()),
            () -> live() != null && live().getX() - start.getX() > 25 && walk() != null);
        check("keeps_near_the_prints", 5, 0, () -> {}, "blocks off the trail", () -> live() == null ? "none" : String.format("%.1f", Math.abs(live().getZ() - (start.getZ() + 0.5))),
            () -> live() != null && Math.abs(live().getZ() - (start.getZ() + 0.5)) <= 2.5);
        /* the player goes far west: the drekki's ground stops ticking */
        quiet("player_away", 10, () -> cmd("tp @s " + (start.getX() - 600) + " 200 " + start.getZ()));
        check("walks_on_as_a_record", 40, 30, () -> {}, "record", () -> walk() == null ? "no walk" : "live " + walk().live + " at " + (BlockPos.getX(walk().node) - start.getX()),
            () -> walk() != null && !walk().live && live() == null);
        quiet("record_mark", 2, () -> { if (walk() != null) recordX = BlockPos.getX(walk().node); });
        check("the_record_moves", 200, 0, () -> {}, "moved", () -> walk() == null ? "no walk" : (BlockPos.getX(walk().node) - recordX) + " blocks (dir " + walk().dir + ")",
            () -> walk() != null && BlockPos.getX(walk().node) != recordX);
        /* to the record's print: it appears there */
        quiet("player_back", 5, () -> { var w = walk(); if (w != null) cmd("tp @s " + (BlockPos.getX(w.node) - 4) + " " + (start.getY() + 2) + " " + (start.getZ() + 5.5) + " facing " + BlockPos.getX(w.node) + " " + start.getY() + " " + start.getZ()); });
        check("appears_at_its_print", 40, 30, () -> {
            DrekkiEntity d = pup();
            if (d != null) cmd("tp @s " + (d.getX() - 3) + " " + (start.getY() + 2) + " " + (start.getZ() + 5.5) + " facing " + d.getX() + " " + (start.getY() + 0.5) + " " + d.getZ());
        }, "drekki", () -> live() == null ? "none" : String.format("at %.0f along the trail, record %s", live().getX() - start.getX(), walk() == null ? "ended" : "live " + walk().live),
            () -> live() != null && live().getX() - start.getX() > 40 && mc().level.getEntitiesOfClass(DrekkiEntity.class, mc().player.getBoundingBox().inflate(64)).size() == 1);
        if (net.neoforged.fml.ModList.get().isLoaded("ponder")) {
            quiet("ponder_open", 20, () -> mc().setScreen(net.createmod.ponder.foundation.ui.PonderUI.of(new net.minecraft.world.item.ItemStack(dev.frosthaven.ranchercompanion.block.DrekkiBlocks.FOOTPRINT.get()))));
            step("ponder_prints", 140, () -> {}, () -> mc().screen != null);
            step("ponder_fork", 240, () -> {}, () -> mc().screen != null);
            step("ponder_rest_stop", 260, () -> {}, () -> mc().screen != null);
            quiet("ponder_close", 10, () -> mc().setScreen(null));
        }
        quiet("trail_end", 10, () -> cmd("gamerule doDaylightCycle true"));
    }
}
