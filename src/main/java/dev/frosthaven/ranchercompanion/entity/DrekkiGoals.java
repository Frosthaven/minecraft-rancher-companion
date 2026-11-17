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
final class DrekkiGoals {
    private DrekkiGoals() {}

    /** a checked-out drekki follows its owner, and teleports next to them when 12 blocks behind (the tamed mobs' rule) */
    static final class Follow extends Goal {
        private final DrekkiEntity d;
        private Player owner;
        private int recalc;

        Follow(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }

        @Override
        public boolean canUse() {
            Player p = d.ownerPlayer();
            if (p == null || !d.isOut() || p.isSpectator() || d.distanceToSqr(p) < 4 * 4) return false;
            owner = p;
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return owner != null && owner.isAlive() && d.isOut() && !d.getNavigation().isDone() && d.distanceToSqr(owner) > 2.5 * 2.5;
        }

        @Override
        public void start() { recalc = 0; }

        @Override
        public void stop() { owner = null; d.getNavigation().stop(); }

        @Override
        public void tick() {
            boolean far = d.distanceToSqr(owner) >= 12 * 12;
            if (!far) d.getLookControl().setLookAt(owner, 10f, d.getMaxHeadXRot());
            if (--recalc > 0) return;
            recalc = adjustedTickDelay(10);
            if (far) d.teleportNear(owner.blockPosition());
            else d.getNavigation().moveTo(owner, d.distanceToSqr(owner) > 8 * 8 ? 1.6 : 1.1);
        }
    }

    /** a checked-in drekki strolls to a random spot of its pen now and then; one found outside walks back in */
    static final class Stroll extends Goal {
        private final DrekkiEntity d;
        private BlockPos target;

        Stroll(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE)); }

        @Override
        public boolean canUse() {
            if (d.isOut()) return false;
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
        private int settle, recheck, retry, tries;
        private static final int MAX_TRIES = 10;

        Sleep(DrekkiEntity d) { this.d = d; setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }

        private boolean night() { return d.level().isNight(); }

        @Override
        public boolean canUse() {
            if (d.isOut() || d.home() == null || !night() || d.pen() == null) return false;
            Set<Long> beds = d.bedding();
            bed = beds != null && !beds.isEmpty() ? pick(d, beds) : null;
            return true;
        }

        @Override
        public boolean canContinueToUse() { return !d.isOut() && night() && d.getLastHurtByMob() == null; }

        @Override
        public void start() {
            settle = 0;
            tries = 0;
            if (bed != null) goBed();
        }

        @Override
        public void tick() {
            if (bed == null && ++recheck % 50 == 0) {  /* goals tick every other tick: about every 5 s */  /* bedding laid after it settled: get up and go to it */
                Set<Long> beds = d.bedding();
                if (beds != null && !beds.isEmpty()) {
                    bed = pick(d, beds);
                    d.setSleepingLoop(false);
                    settle = 0; tries = 0;
                    goBed();
                    return;
                }
            }
            if (d.isSleepingLoop()) return;
            boolean near = bed == null || d.blockPosition().equals(bed);
            /* no path yet (a mob in the air gets none, as right after a hatch or a check-in): try again for a while
               before settling where it stands */
            if (!near && d.getNavigation().isDone() && tries < MAX_TRIES) {
                if (++retry % 10 == 0) { tries++; goBed(); }
                return;
            }
            boolean there = near || d.getNavigation().isDone();
            if (there && ++settle > 10) { d.getNavigation().stop(); d.setSleepingLoop(true); }
        }

        /** a path that ends on the bed itself (moveTo's own accuracy stops a block short) */
        private void goBed() { d.getNavigation().moveTo(d.getNavigation().createPath(bed, 0), 0.8); }

        @Override
        public void stop() { d.setSleepingLoop(false); bed = null; }
    }

    /** ground navigation whose paths never leave a checked-in drekki's pen */
    static final class PenNavigation extends GroundPathNavigation {
        PenNavigation(DrekkiEntity d, Level level) { super(d, level); }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            nodeEvaluator = new PenNodes();
            nodeEvaluator.setCanPassDoors(false);
            nodeEvaluator.setCanOpenDoors(false);
            return new PathFinder(nodeEvaluator, maxVisitedNodes);
        }
    }

    static final class PenNodes extends WalkNodeEvaluator {
        @Override
        public PathType getPathTypeOfMob(PathfindingContext ctx, int x, int y, int z, Mob mob) {
            if (mob instanceof DrekkiEntity d && !d.isOut() && d.pen() != null && d.inPen(new BlockPos(x, y, z)) == false && d.inPen(d.blockPosition()))
                return PathType.BLOCKED;
            return super.getPathTypeOfMob(ctx, x, y, z, mob);
        }
    }
}
