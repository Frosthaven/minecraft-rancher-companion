package dev.frosthaven.ranchercompanion.entity;

import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import dev.frosthaven.ranchercompanion.ranch.Flood;

/** The live drekki's goals and its pen-aware navigation (spec, Checkout: Wandering; Pup release: follow). */
public final class DrekkiGoals {
    private DrekkiGoals() {}

    /** blocks behind its owner from which a following drekki is caught up (teleported); nearer it walks */
    public static final double CATCH_UP = 16;

    /** a checked-out drekki follows its owner, and teleports next to them when 16 blocks behind */
    static final class Follow extends Goal {
        private final DrekkiEntity d;
        private Player owner;
        private int recalc;
        /** the tick it first found no way to walk to its owner, -1 while it has one */
        private int lost = -1;

        Follow(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            Player p = d.ownerPlayer();
            if (p == null || !d.isOut() || d.isStaying() || d.held() || p.isSpectator() || d.distanceToSqr(p) < 4 * 4) return false;
            if (d.eating && d.distanceToSqr(p) < 12 * 12) return false;  /* at a trough near its still owner */
            owner = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && d.isOut() && !d.isStaying() && !d.getNavigation().isDone() && d.distanceToSqr(owner) > 2.5 * 2.5;
        }

        @Override
        public void start() { recalc = 0; }

        @Override
        public void stop() { owner = null; d.getNavigation().stop(); }

        @Override
        public void tick() {
            boolean far = d.distanceToSqr(owner) >= CATCH_UP * CATCH_UP;  /* nearer, it walks (decided 2026-11-23: 16, was 12) */
            if (!far) d.getLookControl().setLookAt(owner, 10f, d.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (far) { d.teleportNear(owner); lost = -1; return; }
            d.getNavigation().moveTo(owner, d.distanceToSqr(owner) > 8 * 8 ? 1.6 : 1.1);
            /* no way to walk to them: after 2 seconds of that it jumps to them, near as they are, when a catch-up put
               it where it is (always: the landing must be one it can reach them from), or when it is 3 blocks or more
               above or below them (a ledge, a pit, a cave under the floor). One sat 5 blocks under its owner, 8 away,
               and never came (2026-11-21). One that walked up to a closed door or a fence on their level waits: that
               keeps it out, as its owner means */
            var path = d.getNavigation().getPath();
            /* by the clock, not by tries: with no path the goal stops and starts again every tick or two */
            boolean way = path != null && path.canReach();
            if (way) d.landed = false;
            lost = way || !d.landed && Math.abs(owner.getY() - d.getY()) < 3 ? -1 : lost < 0 ? d.tickCount : lost;
            if (lost >= 0 && d.tickCount - lost >= 40) { dev.frosthaven.ranchercompanion.Debug.log("server: {} has no way to walk to its owner, it jumps", dev.frosthaven.ranchercompanion.Debug.of(d)); d.teleportNear(owner); lost = -1; }
        }
    }

    /** a checked-in drekki strolls to a random spot of its pen now and then; one found outside walks back in */
    static final class Stroll extends Goal {
        private final DrekkiEntity d;
        private BlockPos target;

        Stroll(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (d.isOut() || dev.frosthaven.ranchercompanion.trail.TrailWalks.walking(d)) return false;
            Set<Long> pen = d.pen();
            if (pen == null || pen.isEmpty()) return false;
            boolean outside = !d.inPen(d.blockPosition());
            if (!outside && d.getRandom().nextInt(adjustedTickDelay(100)) != 0) return false;
            Set<Long> beds = d.bedding();
            Set<Long> from = beds != null && !beds.isEmpty() && d.getRandom().nextInt(4) != 0 ? beds : pen;  /* bedding draws it */
            target = pick(d, from);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() { return !d.getNavigation().isDone() && !d.isOut(); }

        @Override
        public void start() {
            if (!d.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, 0.8) && !d.inPen(d.blockPosition()))
                d.goHome();  /* stuck outside with no way back in: back to the hatchery */
        }

        @Override
        public void stop() { target = null; }
    }

    static BlockPos pick(DrekkiEntity d, Set<Long> from) {
        int i = d.getRandom().nextInt(from.size());
        for (long p : from) if (i-- == 0) return new BlockPos(Flood.x(p), Flood.y(p), Flood.z(p));
        return null;
    }

    /** a checked-in drekki sleeps at night: on bedding in its pen when it can reach some, else where it is by the hatchery */
    static final class Sleep extends Goal {
        private final DrekkiEntity d;
        private BlockPos bed;
        private int settle, recheck;
        private final int[] tries = {0};
        /** bedding it could not get onto tonight: never picked again before morning (an unreachable block would wake it
         *  all night) */
        private final Set<BlockPos> gaveUp = new java.util.HashSet<>();

        Sleep(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        private boolean night() { return d.restTime(); }   /* night, or day for Lunar, Underworld and Deep eyes */

        /** a free bedding block of its pen, claimed (Beds), or null */
        private BlockPos freeBed() {
            Set<Long> beds = d.bedding();
            if (beds == null || beds.isEmpty()) return null;
            java.util.List<BlockPos> list = new java.util.ArrayList<>();
            for (long p : beds) list.add(new BlockPos(dev.frosthaven.ranchercompanion.ranch.Flood.x(p), dev.frosthaven.ranchercompanion.ranch.Flood.y(p), dev.frosthaven.ranchercompanion.ranch.Flood.z(p)));
            return Beds.pick(d, list, gaveUp);
        }

        @Override
        public boolean canUse() {
            if (d.ghost() || d.isOut() || d.home() == null || !night() || d.pen() == null || dev.frosthaven.ranchercompanion.trail.TrailWalks.walking(d)) return false;  /* a ghost never sleeps (Shane, 2026-11-26) */
            gaveUp.clear();
            bed = freeBed();
            return true;
        }

        @Override
        public boolean canContinueToUse() { return !d.ghost() && !d.isOut() && night() && d.getLastHurtByMob() == null; }

        @Override
        public void start() { settle = 0; tries[0] = 0; recheck = 0; }

        /* every tick: the last step onto the bedding sets the move control's target, which lasts one tick */
        @Override
        public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            /* asleep off bedding (none was free): about every 5 s it looks again, and gets up for a block that came free */
            if (bed == null && ++recheck % 100 == 0) {
                BlockPos to = freeBed();
                var path = to == null ? null : d.getNavigation().createPath(to, 0);
                if (path != null && path.canReach()) { bed = to; tries[0] = 0; settle = 0; d.setSleepingLoop(false); }
                else if (to != null) { gaveUp.add(to); Beds.release(d); }
            }
            if (d.isSleepingLoop()) return;
            if (bed != null) {
                /* onto its own free bedding block, not beside it (Shane, 2026-11-27: they slept a block short); one it
                   cannot step onto in about 3 s is given up for another free block, or it sleeps where it stands */
                switch (Beds.approach(d, bed, tries)) {
                    case ON -> { if (++settle > 10) { d.getNavigation().stop(); d.setSleepingLoop(true); } }
                    case STUCK -> { gaveUp.add(bed); bed = freeBed(); tries[0] = 0; }
                    case GOING -> { }
                }
                return;
            }
            if (d.getNavigation().isDone() && ++settle > 10) d.setSleepingLoop(true);
        }

        @Override
        public void stop() { d.setSleepingLoop(false); bed = null; Beds.release(d); }
    }

    /** ground navigation whose paths never leave a checked-in drekki's pen */
    static final class PenNavigation extends GroundPathNavigation {
        PenNavigation(DrekkiEntity d, Level level) { super(d, level); }

        /** told to stay, it walks nowhere: no chase, no stroll, no trough. It still bites what comes within reach */
        @Override
        public boolean moveTo(net.minecraft.world.level.pathfinder.Path path, double speed) {
            return !(mob instanceof DrekkiEntity d && d.isStaying()) && super.moveTo(path, speed);
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            nodeEvaluator = new PenNodes();
            nodeEvaluator.setCanPassDoors(true);   /* open doors and gates: every age walks through (the wander area still bounds a checked-in one) */
            nodeEvaluator.setCanOpenDoors(false);  /* none opens a closed door */
            return new PathFinder(nodeEvaluator, maxVisitedNodes);
        }
    }

    static final class PenNodes extends WalkNodeEvaluator {
        @Override
        public PathType getPathTypeOfMob(PathfindingContext ctx, int x, int y, int z, Mob mob) {
            if (mob instanceof DrekkiEntity d && !d.isOut() && d.pen() != null && d.inPen(new BlockPos(x, y, z)) == false
                && (dev.frosthaven.ranchercompanion.trail.TrailWalks.walking(d)
                    /* out on a trail: within 3 blocks of a footprint (an anchor's reach, as the hatchery, bedding and dirt
                       paths; 2 until 2026-11-27), so it keeps near the trail (spec, Trails) */
                    ? dev.frosthaven.ranchercompanion.trail.Trails.get((net.minecraft.server.level.ServerLevel) d.level()).nearestPrint(new BlockPos(x, y, z), dev.frosthaven.ranchercompanion.ranch.Ground.ANCHOR) == null
                    : d.inPen(d.blockPosition())))
                return PathType.BLOCKED;
            return super.getPathTypeOfMob(ctx, x, y, z, mob);
        }
    }
}
