package dev.frosthaven.ranchercompanion.showcase;

import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Lineage;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the showcase puts everything, shared by the server that spawns it, the client that draws the particles
 * around the stand-ins, and the camera schedule. Pure numbers; the world is the flat preset, ground at y -60.
 */
public final class Layout {
    private Layout() {}

    public static final double GROUND = -60;
    /** group A, the lineages: one row per family, tiers along the row, the three stages side by side in each tier */
    public static final double AX = 0, AZ = 0, TIER_DX = 7, ROW_DZ = 20;  /* rows far enough apart that the next one is behind the camera */
    public static final double[] STAGE_DX = {0, 1.5, 3.8};
    public static final float A_YAW = 30;
    /** group B, the animations: one row per stage, one loop every few blocks, facing east so the camera sees them side on */
    public static final double BX = 0, BZ = 280, STAGE_DZ = 20;  /* clear of the five rare-line rows (z 180 to 260) */
    /** loop spacing, lane length and camera distance per stage (index stage - 1): the smaller the drekki, the tighter and closer */
    public static final double[] ANIM_DX = {3, 5.6, 8}, LANE = {2.1, 3.5, 5}, CAM_DIST = {5.5, 9, 13}, CAM_UP = {1.7, 2.8, 4};
    public static final float B_YAW = -90;

    /** group C, the gear: a Ruin wearing each hat at every stage (one cluster per hat along the row), a Ruin adult in each
       barding tier, and a wall of item frames holding every item */
    public static final double HAT_Z = 360, BARD_Z = 380, WALL_Z = 400, GEAR_DX = 7, BARD_DX = 5;
    public static final int WALL_W = 16;

    public record Shot(String name, double x, double y, double z, float yaw, float pitch) {}

    public static double rowZ(int fam) { return AZ + fam * ROW_DZ; }
    public static double rareRowZ(int rareIndex) { return AZ + (Lineage.FAMILIES + rareIndex / 5) * ROW_DZ; }
    public static double stageRowZ(int stage) { return BZ + (3 - stage) * STAGE_DZ; }

    public static List<Anim> animsAt(int stage) {
        List<Anim> out = new ArrayList<>();
        for (Anim a : Anim.values()) if (a.availableAt(stage) && a != Anim.FEED) out.add(a);
        if (Anim.FEED.availableAt(stage)) out.add(Anim.FEED);  /* last in the row: its hay feeder sits past the loop, clear of any neighbour */
        return out;
    }

    /** stand-ins an ability plays against: (forward, right) in blocks; a lone entry is the training dummy, two are players */
    public static double[][] props(Anim a, double k) {
        return switch (a) {
            case BITE -> new double[][]{{2.2 * k, 0}};
            case POUNCE -> new double[][]{{6.15 * k, 0}};
            case BALL -> new double[][]{{5.6 * k, 0}};
            case BREATH -> new double[][]{{5.05 * k, 0}};
            case GUARD -> new double[][]{{2.8, 0}, {4.2, -1.1}};
            case REJUV -> new double[][]{{3.0, 0}, {4.6, -1.1}};
            default -> new double[0][];
        };
    }

    /** forward and right unit vectors for a yaw in degrees, Minecraft's convention */
    public static double[] forward(float yaw) { double r = Math.toRadians(yaw); return new double[]{-Math.sin(r), 0, Math.cos(r)}; }
    public static double[] right(float yaw) { double r = Math.toRadians(yaw); return new double[]{Math.cos(r), 0, Math.sin(r)}; }

    public static List<Shot> shots() {
        List<Shot> s = new ArrayList<>();
        double camY = GROUND + 4;
        for (int fam = 0; fam < Lineage.FAMILIES; fam++) s.add(new Shot("lineage_" + Lineage.familyName(fam).toLowerCase(), AX + 12.5, camY, rowZ(fam) + 13, 180, 14));
        for (int r = 0; r < Lineage.SPECIALS; r += 5) s.add(new Shot("lineage_rare_" + (r / 5 + 1), AX + 14.5, camY, rareRowZ(r) + 13, 180, 14));
        for (int r = 0; r < 10; r += 5) s.add(new Shot("night_rare_" + (r / 5 + 1), AX + 14.5, camY, rareRowZ(r) + 13, 180, 14));  /* the sheen, at midnight */
        s.add(new Shot("lineage_overview", AX + 13, GROUND + 25, AZ + 225, 180, 20));
        s.add(new Shot("closeup_head", AX + 5.6, GROUND + 1.9, AZ + 2.4, 118.6f, 16));  /* the Moss muted adult's head from two and a half blocks, for the eyes and the face */
        double[][] centers = {{5, 16}, {8, 25, 42}, {14, 42, 70, 98}};
        for (int h = 0; h < 15; h += 5) s.add(new Shot("gear_hats_" + (h / 5 + 1), AX + 14.5 + h * GEAR_DX, camY, HAT_Z + 11, 180, 14));
        s.add(new Shot("gear_barding", AX + 15, GROUND + 5, BARD_Z + 22, 180, 12));
        s.add(new Shot("gear_items", AX + WALL_W / 2.0, GROUND + 2.6, WALL_Z + 8, 180, 6));
        for (int stage = 3; stage >= 1; stage--) { /* the feed loop beside its hay feeder, side on, three moments of the loop */
            double fx = BX + animsAt(stage).indexOf(Anim.FEED) * ANIM_DX[stage - 1], hay = Math.floor(fx) + 4, d = CAM_DIST[stage - 1] * 0.55;
            for (int m = 1; m <= 3; m++) s.add(new Shot("anim_feed_stage" + stage + "_" + m, hay - 0.5, GROUND + CAM_UP[stage - 1] * 0.6, stageRowZ(stage) + 0.5 + d, 180, 10));
            s.add(new Shot("anim_feed_stage" + stage + "_top", hay - 0.5, GROUND + 1 + CAM_UP[stage - 1] * 1.4, stageRowZ(stage) + 0.5, 180, 90));
        }
        for (int stage = 3; stage >= 1; stage--) { int i = 1; for (double cx : centers[stage - 1]) s.add(new Shot("anim_stage" + stage + "_" + i++, BX + cx, GROUND + CAM_UP[stage - 1], stageRowZ(stage) + CAM_DIST[stage - 1], 180, 14)); }
        return s;
    }

    public static final int FIRST_TICK = 60, SHOT_TICKS = 70, GRAB_DELAY = 50;
}
