package dev.frosthaven.ranchercompanion.trail;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.ranch.Flood;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A drekki that lives at a hatchery walks a footprint trail (spec, Trails): now and then, by day, from a print inside its
 * pen; then print to print, a step to either side now and then and a pause at times, so it follows the trail loosely.
 * At a fork it takes one at random; at the trail's end it walks the whole way back. At night it turns for home and
 * sleeps on the first Straw Bedding by the trail (within 3 blocks of a print); with none, it walks home. Where the next
 * print does not tick, it becomes a record ({@link TrailWalks}) and walks on there.
 */
public final class TrailWalk extends Goal {
    /** about one stroll in three by day, as a stroll starts about every 5 s */
    private static final int START = 300;
    private final DrekkiEntity d;
    /** prints a drekki can walk past in one path */
    private static final int LOOK = 5;
    private BlockPos target;
    /** the stretch under way: each print with the walk's place after it ({node, prev, dir}) */
    private final java.util.List<long[]> plan = new java.util.ArrayList<>();
    private int pause, stuck, tries, wait;
    /** tonight's bedding block by the trail (claimed), and the steps spent getting onto it */
    private BlockPos rest;
    private final int[] bedTries = {0};

    public TrailWalk(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

    private ServerLevel level() { return (ServerLevel) d.level(); }
    private TrailWalks walks() { return TrailWalks.get(level().getServer()); }
    private TrailWalks.Walk walk() { return walks().of(d.drekkiId()); }

    private boolean home() { return d.owned() && !d.isOut() && d.home() != null && !d.ghost() && d.drekkiId() != null; }

    @Override
    public boolean canUse() {
        if (d.level().isClientSide || !home()) return false;
        TrailWalks.Walk w = walk();
        if (w != null) return true;  /* a walk under way: this entity carries it on (it came back into ticking ground) */
        if (d.stage() < 2) return false;  /* Fledglings and Matures walk trails; a pup keeps to its pen (Shane, 2026-11-27) */
        if (d.restTime() || d.isSleepingLoop() || d.life.food < Life.FOOD_MAX / 2 || d.getTarget() != null) return false;
        if (d.getRandom().nextInt(adjustedTickDelay(START)) != 0) return false;
        BlockPos head = trailhead();
        if (head == null) return false;
        walks().start(d, head);
        return true;
    }

    /** a print inside its pen, at random */
    private BlockPos trailhead() {
        Set<Long> pen = d.pen();
        if (pen == null || pen.isEmpty()) return null;
        Trails tr = Trails.get(level());
        if (tr.printCount() == 0) return null;
        java.util.List<BlockPos> heads = new java.util.ArrayList<>();
        for (long p : pen) {
            long q = BlockPos.asLong(Flood.x(p), Flood.y(p), Flood.z(p));
            if (tr.isPrint(q)) heads.add(BlockPos.of(q));
        }
        return heads.isEmpty() ? null : heads.get(d.getRandom().nextInt(heads.size()));
    }

    @Override
    public boolean canContinueToUse() { return home() && walk() != null && !d.isRemoved(); }

    @Override
    public boolean requiresUpdateEveryTick() { return true; }

    @Override
    public void start() {
        TrailWalks.Walk w = walk();
        if (w != null) { w.live = true; if (w.asleep) d.setSleepingLoop(true); }
        target = null; plan.clear(); pause = 0; stuck = 0;
    }

    @Override
    public void stop() {
        d.getNavigation().stop();
        target = null; plan.clear();
        rest = null; dev.frosthaven.ranchercompanion.entity.Beds.release(d);
        /* the walk ended (or it left the hatchery's roster): awake, and the pen's goals take over */
        if (walk() == null && d.isSleepingLoop() && !d.restTime()) d.setSleepingLoop(false);
    }

    @Override
    public void tick() {
        TrailWalks.Walk w = walk();
        if (w == null) return;
        Trails tr = Trails.get(level());
        boolean night = d.restTime();   /* its night: day for a nocturnal one */
        if (w.asleep) {
            if (night) return;
            w.asleep = false; d.setSleepingLoop(false); walks().changed();
            rest = null; dev.frosthaven.ranchercompanion.entity.Beds.release(d);
        }
        if (!tr.isPrint(w.node)) {  /* its print was broken: pick the trail up nearby, or end the walk */
            BlockPos p = tr.nearestPrint(d.blockPosition(), 2);
            if (p == null) { end(); return; }
            w.node = p.asLong(); w.prev = Long.MIN_VALUE; plan.clear(); target = null;
        }
        if (night && w.dir == 1) { w.turn(); plan.clear(); target = null; d.getNavigation().stop(); walks().changed(); }
        if (night && plan.isEmpty()) {
            BlockPos bed = tr.restNear(w.node);
            if (bed != null) { sleepOn(w, bed); return; }
        }
        if (pause > 0) { pause--; return; }
        if (plan.isEmpty()) { plan(w, tr, night); return; }
        /* on the way: each print it comes near is passed (its place on the trail moves on) */
        while (!plan.isEmpty() && near(BlockPos.of(plan.get(0)[0]), 1.8)) pass(w, plan.remove(0));
        if (plan.isEmpty()) { arrived(); return; }
        if (d.getNavigation().isDone()) {
            if (near(BlockPos.of(plan.get(plan.size() - 1)[0]), 2.5)) { while (!plan.isEmpty()) pass(w, plan.remove(0)); arrived(); }
            else repath(w);
        }
    }

    /** the next stretch: up to 5 prints along the trail (turning at its end), to the first print in ground that does
     *  not tick or, at night, the first rest stop; then a path to the last of them, a block to one side at times. Prints
     *  are a block apart: a path to the very next one is often no path at all */
    private void plan(TrailWalks.Walk w, Trails tr, boolean night) {
        long node = w.node, prev = w.prev; int dir = w.dir;
        boolean frontier = false;
        for (int i = 0; i < LOOK; i++) {
            BlockPos n = tr.next(node, prev, dir, d.getRandom());
            if (n == null && dir == 1) {
                if (!plan.isEmpty()) break;  /* the stretch ends on the trail's last print; the next one turns back */
                dir = -1; prev = Long.MIN_VALUE; n = tr.next(node, prev, dir, d.getRandom());  /* the end: back the whole way */
            }
            if (n == null) break;
            if (!level().isPositionEntityTicking(n)) { frontier = true; break; }
            prev = node; node = n.asLong();
            plan.add(new long[]{node, prev, dir});
            if (night && dir == -1 && tr.restNear(node) != null) break;
        }
        if (plan.isEmpty()) {
            if (frontier) walks().toRecord(d);  /* the trail goes on where nothing ticks: it walks on as a record */
            else end();  /* back at the trail's start */
            return;
        }
        BlockPos last = BlockPos.of(plan.get(plan.size() - 1)[0]);
        target = spot(last);
        tries = 0;
        if (go(last)) return;
        if (near(last, 2.5)) { while (!plan.isEmpty()) pass(w, plan.remove(0)); arrived(); }  /* already there */
    }

    private boolean go(BlockPos last) {
        return d.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8)
            || d.getNavigation().moveTo(last.getX() + 0.5, last.getY(), last.getZ() + 0.5, 0.8);
    }

    /** no path this tick: a drekki off the ground for a moment (a step, a hop) gets none at all. It tries again every
     *  few ticks for 2 seconds before the way counts as blocked */
    private void repath(TrailWalks.Walk w) {
        if (++wait % 5 != 0) return;
        if (go(BlockPos.of(plan.get(plan.size() - 1)[0]))) { tries = 0; return; }
        if (++tries >= 8) { tries = 0; blocked(w); }
    }

    private boolean near(BlockPos p, double r) {
        double dx = d.getX() - (p.getX() + 0.5), dz = d.getZ() - (p.getZ() + 0.5);
        return dx * dx + dz * dz <= r * r && Math.abs(d.getY() - p.getY()) < 2;
    }

    private void pass(TrailWalks.Walk w, long[] step) {
        w.node = step[0]; w.prev = step[1]; w.dir = (int) step[2]; walks().changed(); stuck = 0;
    }

    /** the stretch walked: now and then a pause (a sniff, a look round) */
    private void arrived() {
        target = null;
        if (d.getRandom().nextInt(4) == 0) { pause = 20 + d.getRandom().nextInt(50); d.getNavigation().stop(); }
    }

    /** where to walk for a print: on it, or a block to one side of it (it follows the trail loosely) */
    private BlockPos spot(BlockPos print) {
        if (d.getRandom().nextInt(3) == 0) return print;
        for (int i = 0; i < 4; i++) {
            BlockPos s = print.offset(d.getRandom().nextInt(3) - 1, 0, d.getRandom().nextInt(3) - 1);
            if (new dev.frosthaven.ranchercompanion.ranch.Ground(level()).stand(s.getX(), s.getY(), s.getZ())) return s;
        }
        return print;
    }

    /** no way on: back the other way; blocked three times in a row, the walk ends and it goes home */
    private void blocked(TrailWalks.Walk w) {
        plan.clear(); target = null;
        if (++stuck >= 3) { end(); d.goHome(); return; }
        w.turn(); walks().changed();
    }

    /** the night's rest stop: onto a free bedding block within 3 blocks of the trail (claimed, Beds), not beside it;
     *  one it cannot step onto is given up for another, and with none free it sleeps by the trail */
    private void sleepOn(TrailWalks.Walk w, BlockPos near) {
        if (rest == null) { rest = freeRest(w, null); bedTries[0] = 0; }
        if (rest == null) { d.getNavigation().stop(); d.setSleepingLoop(true); w.asleep = true; walks().changed(); return; }
        switch (dev.frosthaven.ranchercompanion.entity.Beds.approach(d, rest, bedTries)) {
            case ON -> { d.getNavigation().stop(); d.setSleepingLoop(true); w.asleep = true; walks().changed(); }
            case STUCK -> { rest = freeRest(w, rest); bedTries[0] = 0; }
            case GOING -> { }
        }
    }

    private BlockPos freeRest(TrailWalks.Walk w, BlockPos not) {
        return dev.frosthaven.ranchercompanion.entity.Beds.pick(d, Trails.get(level()).restsNear(w.node), not == null ? java.util.Set.of() : java.util.Set.of(not));
    }

    private void end() { walks().end(d.drekkiId()); plan.clear(); target = null; }
}
