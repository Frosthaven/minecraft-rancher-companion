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
 * sleeps on the first free Straw Bedding by the trail (within 3 blocks of a print) that it can get onto: bedding with no
 * way onto it is given up and it walks on back. Home first, the pen's goals put it to bed; on the trail with no print
 * passed for {@link #SEARCH} ticks (no headway home), it sleeps where it is. Where the next print does not tick, it becomes a record
 * ({@link TrailWalks}) and walks on there.
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
    /** tonight: the bedding by the trail it gave up (no way onto it, or none free), and the ticks spent looking */
    private final Set<BlockPos> gaveUp = new java.util.HashSet<>();
    private int search;
    /** a night's look for bedding along the trail: 60 s with no print passed (no headway home); then it sleeps on the trail */
    static final int SEARCH = 1200;

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
        if (walk() == null) { gaveUp.clear(); search = 0; }
        /* the walk ended (or it left the hatchery's roster): awake, and the pen's goals take over */
        if (walk() == null && d.isSleepingLoop() && !d.restTime()) d.setSleepingLoop(false);
    }

    @Override
    public void tick() {
        TrailWalks.Walk w = walk();
        if (w == null) return;
        Passing.check(d);   /* two walkers stuck against each other on a narrow path pass through each other */
        Trails tr = Trails.get(level());
        boolean night = d.restTime();   /* its night: day for a nocturnal one */
        if (w.asleep) {
            if (night) return;
            w.asleep = false; d.setSleepingLoop(false); walks().changed();
            rest = null; dev.frosthaven.ranchercompanion.entity.Beds.release(d);
        }
        if (!night && search > 0) { search = 0; gaveUp.clear(); }  /* morning: a new night looks afresh */
        if (!tr.isPrint(w.node)) {  /* its print was broken: pick the trail up nearby, or end the walk */
            BlockPos p = tr.nearestPrint(d.blockPosition(), 2);
            if (p == null) { end(); return; }
            w.node = p.asLong(); w.prev = Long.MIN_VALUE; plan.clear(); target = null;
        }
        if (night && w.dir == 1) { w.turn(); plan.clear(); target = null; d.getNavigation().stop(); walks().changed(); }
        if (night && ++search > SEARCH) { sleepHere(w, "no headway home for 60 s"); return; }  /* no bedding it could reach: on the trail */
        if (night && plan.isEmpty() && (rest != null || hasRest(tr, w.node))) { sleepOn(w, tr); return; }
        if (pause > 0) { pause--; return; }
        if (plan.isEmpty()) { plan(w, tr, night); return; }
        if (standing()) { dev.frosthaven.ranchercompanion.Debug.log("trail: {} stood still on its way", dev.frosthaven.ranchercompanion.Debug.of(d)); d.getNavigation().stop(); blocked(w); return; }
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
            if (night && dir == -1 && hasRest(tr, node)) break;
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
        return toward(target) || toward(last);
    }

    private net.minecraft.world.phys.Vec3 stillAt;
    private int stillFor;

    /** on its way (a path under way) but not moving: two seconds within a third of a block. The path then never ends,
     *  so the walk never sees it is blocked (a walker pushing at its pen's fence stood there for good, GameTest
     *  2026-11-28). Not while two walkers pass through each other (Passing) */
    private boolean standing() {
        if (!d.getNavigation().isInProgress() || Passing.passing(d)) { stillAt = null; return false; }
        if (stillAt == null || stillAt.distanceToSqr(d.position()) > 0.3 * 0.3) { stillAt = d.position(); stillFor = 0; return false; }
        if (++stillFor <= 40) return false;
        if (Passing.blockedBy(d)) { stillFor = 0; return false; }   /* another walker in the way: they pass, it does not turn back */
        return true;
    }

    /** a path to this spot that gets there, or at least two blocks nearer it. The pathfinder gives the nearest point when
     *  it cannot reach a spot, and a walk took that as a way on: Tulip, inside her pen with the next print behind its
     *  fence, walked to the fence and back for good (Shane, 2026-11-28); now that counts as no way on (repath, blocked) */
    private boolean toward(BlockPos to) {
        var path = d.getNavigation().createPath(to, 1);
        if (path == null || path.getEndNode() == null) return false;
        double end = path.getEndNode().asBlockPos().distSqr(to), now = d.blockPosition().distSqr(to);
        if (end > 2 * 2 && Math.sqrt(end) > Math.sqrt(now) - 2) {
            dev.frosthaven.ranchercompanion.Debug.log("trail: {} no way to {} (the path ends {} from it, {} now)", dev.frosthaven.ranchercompanion.Debug.of(d), to.toShortString(), Math.round(Math.sqrt(end)), Math.round(Math.sqrt(now)));
            return false;
        }
        return d.getNavigation().moveTo(path, 0.8);
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
        search = 0;   /* a print passed is headway home: the night's 60 s count only time without it (Pom Pom slept on the trail a minute after dusk, its way home clear; Shane, 2026-11-28) */
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
        dev.frosthaven.ranchercompanion.Debug.log("trail: {} blocked ({} in a row) at print {}", dev.frosthaven.ranchercompanion.Debug.of(d), stuck + 1, BlockPos.of(w.node).toShortString());
        plan.clear(); target = null;
        if (++stuck >= 3) { end(); d.goHome(); return; }
        w.turn(); walks().changed();
    }

    /** bedding by this print it has not given up tonight */
    private boolean hasRest(Trails tr, long node) {
        for (BlockPos b : tr.restsNear(node)) if (!gaveUp.contains(b)) return true;
        return false;
    }

    /** the night's rest stop: onto a free bedding block within 3 blocks of the trail (claimed, Beds), not beside it.
     *  One it cannot get onto is given up for another there; with none left there it walks on back along the trail */
    private void sleepOn(TrailWalks.Walk w, Trails tr) {
        if (rest == null) {
            rest = dev.frosthaven.ranchercompanion.entity.Beds.pick(d, tr.restsNear(w.node), gaveUp); bedTries[0] = 0;
            if (rest == null) { gaveUp.addAll(tr.restsNear(w.node)); return; }  /* all taken: the next stop back */
        }
        switch (dev.frosthaven.ranchercompanion.entity.Beds.approach(d, rest, bedTries)) {
            case ON -> sleepHere(w, "on its bedding");
            case STUCK -> { gaveUp.add(rest); rest = null; dev.frosthaven.ranchercompanion.entity.Beds.release(d); d.getNavigation().stop(); }
            case GOING -> { }
        }
    }

    private void sleepHere(TrailWalks.Walk w, String why) {
        dev.frosthaven.ranchercompanion.Debug.log("trail: {} asleep on the trail ({})", dev.frosthaven.ranchercompanion.Debug.of(d), why);
        d.getNavigation().stop(); d.setSleepingLoop(true); w.asleep = true; walks().changed();
    }

    private void end() { walks().end(d.drekkiId()); plan.clear(); target = null; }
}
