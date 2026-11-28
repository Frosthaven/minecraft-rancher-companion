package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.FootprintBlock;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.trail.TrailWalks;
import dev.frosthaven.ranchercompanion.trail.Trails;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Footprint trails (spec, Trails): the graph, a record's walk, and a drekki that follows a trail out of its pen. */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class TrailTests {
    private TrailTests() {}

    private static void ground(GameTestHelper t) {
        for (int x = 0; x < 33; x++) for (int z = 0; z < 33; z++) t.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
    }

    private static void print(GameTestHelper t, int x, int z, Direction f) {
        t.setBlock(new BlockPos(x, 1, z), DrekkiBlocks.FOOTPRINT.get().defaultBlockState().setValue(FootprintBlock.FACING, f));
    }

    /** these tests run in their own batch: other tests set the shared world's time, and a walk turns home at night */
    private static void day(GameTestHelper t) {
        t.getLevel().setDayTime(1000);
        t.onEachTick(() -> { if (t.getLevel().getDayTime() % 24000 > 11000) t.getLevel().setDayTime(1000); });  /* kept: another batch's night may linger */
    }

    private static long at(GameTestHelper t, int x, int z) { return t.absolutePos(new BlockPos(x, 1, z)).asLong(); }

    /** ahead along the facing; a fork gives either branch; a turn's corner leads on beside it; the end gives none */
    @GameTest(template = "field", timeoutTicks = 20)
    public static void theTrailLeadsOnForksTurnsAndEnds(GameTestHelper t) {
        ground(t);
        for (int x = 2; x <= 6; x++) print(t, x, 20, Direction.EAST);
        print(t, 7, 19, Direction.EAST); print(t, 7, 21, Direction.EAST);
        print(t, 10, 26, Direction.NORTH); print(t, 10, 25, Direction.NORTH); print(t, 11, 25, Direction.EAST); print(t, 12, 25, Direction.EAST);
        t.setBlock(new BlockPos(12, 1, 27), DrekkiBlocks.STRAW_BEDDING.get());
        Trails tr = Trails.get(t.getLevel());
        var rnd = t.getLevel().random;
        BlockPos n = tr.next(at(t, 2, 20), Long.MIN_VALUE, 1, rnd);
        t.assertTrue(n != null && n.asLong() == at(t, 3, 20), "the next print east: " + n);
        Set<Long> forks = new HashSet<>();
        for (int i = 0; i < 60; i++) { BlockPos f = tr.next(at(t, 6, 20), at(t, 5, 20), 1, rnd); if (f != null) forks.add(f.asLong()); }
        t.assertTrue(forks.equals(Set.of(at(t, 7, 19), at(t, 7, 21))), "the fork gives both branches: " + forks.size());
        BlockPos c = tr.next(at(t, 10, 25), at(t, 10, 26), 1, rnd);
        t.assertTrue(c != null && c.asLong() == at(t, 11, 25), "the corner leads east: " + c);
        t.assertTrue(tr.next(at(t, 7, 19), at(t, 6, 20), 1, rnd) == null, "the end of a branch has no next print");
        BlockPos back = tr.next(at(t, 3, 20), at(t, 4, 20), -1, rnd);
        t.assertTrue(back != null && back.asLong() == at(t, 2, 20), "back the other way: " + back);
        BlockPos bed = tr.restNear(at(t, 12, 25));
        t.assertTrue(bed != null && bed.asLong() == at(t, 12, 27), "the bedding by the trail is a rest stop: " + bed);
        t.setBlock(new BlockPos(3, 1, 20), Blocks.AIR);
        t.assertFalse(tr.isPrint(at(t, 3, 20)), "a broken print leaves the graph");
        t.succeed();
    }

    /** a record walks to the trail's end and the whole way back, then the walk is over */
    @GameTest(template = "field", timeoutTicks = 40, batch = "trails_by_day")
    public static void aRecordWalksOutAndBack(GameTestHelper t) {
        ground(t);
        for (int x = 2; x <= 20; x++) print(t, x, 10, Direction.EAST);
        day(t);
        t.runAfterDelay(3, () -> {
            TrailWalks.Walk w = new TrailWalks.Walk(UUID.randomUUID(), t.getLevel().dimension(), at(t, 2, 10));
            TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
            int far = 2, steps = 0;
            while (walks.walk(t.getLevel(), w, false) && steps++ < 100) far = Math.max(far, BlockPos.getX(w.node) - t.absolutePos(BlockPos.ZERO).getX());
            t.assertTrue(steps < 100, "the walk ends");
            t.assertTrue(far == 20, "it reached the trail's end: x " + far);
            t.assertTrue(BlockPos.getX(w.node) - t.absolutePos(BlockPos.ZERO).getX() == 2, "and ended back at the start: x " + (BlockPos.getX(w.node) - t.absolutePos(BlockPos.ZERO).getX()));
            t.succeed();
        });
    }

    /** a drekki walks a trail out of its pen to the far end, then back; never a second copy at the hatchery meanwhile */
    @GameTest(template = "field", timeoutTicks = 1800, batch = "trails_by_day")
    public static void aDrekkiFollowsTheTrailOutOfItsPen(GameTestHelper t) {
        ground(t);
        day(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        d.setLook(2, d.line(), d.diet(), d.astral(), d.horn());  /* a Fledgling: pups keep to the pen */
        for (int x = 8; x <= 30; x++) print(t, x, 6, Direction.EAST);
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        t.runAfterDelay(3, () -> walks.start(d, t.absolutePos(new BlockPos(8, 1, 6))));
        int baseX = t.absolutePos(BlockPos.ZERO).getX();
        boolean[] reached = {false};
        StringBuilder log = new StringBuilder();
        t.onEachTick(() -> {
            if (d.getX() - baseX >= 27) reached[0] = true;
            if (t.getTick() % 40 == 0 && log.length() < 1500) {
                var w = walks.of(d.drekkiId());
                log.append(String.format(" | t%d x%.1f %s", t.getTick(), d.getX() - baseX, w == null ? "no walk" : "node " + (BlockPos.getX(w.node) - baseX) + " dir " + w.dir + " live " + w.live))
                   .append(" nav ").append(d.getNavigation().isDone() ? "done" : "on").append(" goals ")
                   .append(d.goalSelector.getAvailableGoals().stream().filter(g -> g.isRunning()).map(g -> g.getGoal().getClass().getSimpleName()).toList());
            }
            if (t.getTick() % 100 == 0) Ranch.syncHatchery(t.getLevel(), h);
            List<DrekkiEntity> mine = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(40), x -> p.getUUID().equals(x.owner()));
            t.assertTrue(mine.size() == 1, "one drekki: " + mine.size());
        });
        t.succeedWhen(() -> {
            t.assertTrue(reached[0], "out along the trail: x " + String.format("%.1f", d.getX() - baseX) + log);
            t.assertTrue(walks.of(d.drekkiId()) == null, "and back: the walk is over");
            t.assertTrue(d.getX() - baseX < 14, "near home again: x " + String.format("%.1f", d.getX() - baseX));
        });
    }

    /** a walking drekki whose ground stops ticking becomes a record; where its print ticks it appears there, one copy */
    @GameTest(template = "field", timeoutTicks = 200, batch = "trails_by_day")
    public static void aRecordAppearsAtItsPrint(GameTestHelper t) {
        ground(t);
        day(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        d.setLook(2, d.line(), d.diet(), d.astral(), d.horn());  /* a Fledgling: pups keep to the pen */
        for (int x = 8; x <= 30; x++) print(t, x, 6, Direction.EAST);
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        var w = walks.start(d, t.absolutePos(new BlockPos(8, 1, 6)));
        UUID id = d.drekkiId();
        w.node = t.absolutePos(new BlockPos(20, 1, 6)).asLong();
        walks.toRecord(d);
        t.assertTrue(d.isRemoved() && !walks.of(id).live, "the entity went, the record walks");
        Ranch.syncHatchery(t.getLevel(), h);
        t.assertTrue(Ranch.liveOf(id) == null, "the hatchery does not put it back while it walks");
        t.succeedWhen(() -> {
            DrekkiEntity n = Ranch.liveOf(id);
            t.assertTrue(n != null, "it appeared");
            int bx = t.absolutePos(BlockPos.ZERO).getX();
            t.assertTrue(n.getX() - bx > 15, "at its print, not the hatchery: x " + String.format("%.1f", n.getX() - bx));
            List<DrekkiEntity> mine = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(40), x -> p.getUUID().equals(x.owner()) && !x.isRemoved());
            t.assertTrue(mine.size() == 1, "one copy: " + mine.size());
            walks.end(id);
        });
    }

    /** a Fledgling takes a trail from its pen by itself; a pup beside it never does */
    @GameTest(template = "field", timeoutTicks = 2400, batch = "trails_by_day")
    public static void aFledglingTakesTheTrailAPupDoesNot(GameTestHelper t) {
        ground(t);
        day(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity pup = LifeTests.hatchOne(t, p, h);
        for (int x = 8; x <= 30; x++) print(t, x, 6, Direction.EAST);
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        boolean[] grown = {false};
        t.onEachTick(() -> {
            if (!grown[0]) t.assertTrue(walks.of(pup.drekkiId()) == null, "a pup never walks a trail");
            if (t.getTick() == 600) { pup.setLook(2, pup.line(), pup.diet(), pup.astral(), pup.horn()); grown[0] = true; }  /* grown: now it may */
        });
        t.succeedWhen(() -> t.assertTrue(grown[0] && walks.of(pup.drekkiId()) != null, "the Fledgling took the trail by itself"));
    }

    /** its bedtime held: night, or day for a nocturnal eye */
    private static void bedtime(GameTestHelper t, DrekkiEntity d) {
        long at = dev.frosthaven.ranchercompanion.look.Astral.nocturnal(d.astral()) ? 6000 : 18000;  /* the sky's darkness follows a tick later */
        t.getLevel().setDayTime(at);
        t.onEachTick(() -> { long now = t.getLevel().getDayTime() % 24000; if (Math.abs(now - at) > 4000) t.getLevel().setDayTime(at); });
    }

    /** a walker out on the trail at bedtime, at the print x 28 (the trail runs x 8..30 on z 6 out of the pen) */
    private static DrekkiEntity outAtNight(GameTestHelper t, ServerPlayer p, BlockPos h) {
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        d.setLook(2, d.line(), d.diet(), d.astral(), d.horn());
        for (int x = 8; x <= 30; x++) print(t, x, 6, Direction.EAST);
        /* bedding by the trail at x 26 fenced in all round: no way onto it */
        BlockPos fenced = new BlockPos(26, 1, 9);
        t.setBlock(fenced, DrekkiBlocks.STRAW_BEDDING.get());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) t.setBlock(fenced.offset(dx, 0, dz), DrekkiBlocks.FENCE.get());
        TrailWalks.get(t.getLevel().getServer()).start(d, t.absolutePos(new BlockPos(28, 1, 6)));
        d.moveTo(t.absoluteVec(new BlockPos(28, 1, 6).getCenter().subtract(0, 0.5, 0)));
        bedtime(t, d);
        return d;
    }

    /** bedding by the trail it cannot get onto is given up quickly: it walks on back and sleeps on the next bedding stop
     *  (Finn stood at his pen's fence all night, 2026-11-27) */
    @GameTest(template = "field", timeoutTicks = 1100, batch = "trails_by_night_skip")
    public static void aWalkerSkipsBeddingItCannotReach(GameTestHelper t) {
        ground(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        t.setBlock(new BlockPos(16, 1, 8), DrekkiBlocks.STRAW_BEDDING.get());
        DrekkiEntity d = outAtNight(t, p, h);
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        StringBuilder log = new StringBuilder();
        t.onEachTick(() -> {
            if (t.getTick() % 40 == 0 && log.length() < 1500) {
                var w = walks.of(d.drekkiId());
                log.append(" | t").append(t.getTick()).append(' ').append(t.relativePos(d.blockPosition()).toShortString())
                   .append(w == null ? " no walk" : " node " + t.relativePos(BlockPos.of(w.node)).toShortString() + " dir " + w.dir + " asleep " + w.asleep)
                   .append(" night ").append(d.restTime());
            }
        });
        t.succeedWhen(() -> {
            var w = walks.of(d.drekkiId());
            t.assertTrue(w != null && w.asleep && d.isSleepingLoop(), "asleep on the trail walk: " + (w == null ? "no walk" : "asleep " + w.asleep) + " at " + t.relativePos(d.blockPosition()) + log);
            t.assertTrue(d.blockPosition().equals(t.absolutePos(new BlockPos(16, 1, 8))), "on the reachable bedding: " + t.relativePos(d.blockPosition()) + log);
            walks.end(d.drekkiId());
        });
    }

    /** with no bedding it can reach by the trail, it walks home: the walk ends near the pen */
    @GameTest(template = "field", timeoutTicks = 1100, batch = "trails_by_night_home")
    public static void aWalkerWithNoReachableBeddingGoesHome(GameTestHelper t) {
        ground(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = outAtNight(t, p, h);
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        int baseX = t.absolutePos(BlockPos.ZERO).getX();
        t.succeedWhen(() -> {
            t.assertTrue(walks.of(d.drekkiId()) == null, "the walk is over: " + (walks.of(d.drekkiId()) == null ? "" : "asleep " + walks.of(d.drekkiId()).asleep) + " x " + String.format("%.1f", d.getX() - baseX));
            t.assertTrue(d.getX() - baseX < 14, "home: x " + String.format("%.1f", d.getX() - baseX));
        });
    }

    /** Shane's stable (2026-11-27): a fenced pen of bedding with one opening on its south side and a trail spur out of it;
     *  two nocturnal Matures come back along the spur at bedtime and each lies down on a bedding block of its own */
    @GameTest(template = "field", timeoutTicks = 900, batch = "trails_pen_bedtime")
    public static void nocturnalWalkersComeHomeToTheirOwnBedding(GameTestHelper t) {
        ground(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        t.setBlock(new BlockPos(3, 1, 3), Blocks.AIR);  /* the stable's torch: the fence runs there */
        for (int x = 2; x <= 13; x++) for (int z = 2; z <= 12; z++) {
            boolean edge = x == 2 || x == 13 || z == 2 || z == 12;
            boolean gap = z == 12 && x >= 6 && x <= 9;
            if (edge && !gap) t.setBlock(new BlockPos(x, 1, z), DrekkiBlocks.FENCE.get());
        }
        for (int x = 4; x <= 11; x++) for (int z = 8; z <= 11; z++) t.setBlock(new BlockPos(x, 1, z), DrekkiBlocks.STRAW_BEDDING.get());
        for (int z = 13; z <= 15; z++) { print(t, 7, z, Direction.SOUTH); print(t, 8, z, Direction.SOUTH); }
        for (int x = 3; x <= 12; x++) print(t, x, 16, Direction.WEST);
        DrekkiEntity a = LifeTests.hatchOne(t, p, h);
        Ranch.putEgg(p, h, dev.frosthaven.ranchercompanion.item.CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        DrekkiEntity b = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(20), x -> p.getUUID().equals(x.owner()) && x != a).get(0);
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        for (DrekkiEntity d : new DrekkiEntity[]{a, b}) {
            d.setLook(3, d.line(), d.diet(), dev.frosthaven.ranchercompanion.look.Astral.LUNAR, d.horn());
            var w = walks.start(d, t.absolutePos(new BlockPos(d == a ? 5 : 10, 1, 16)));
            w.dir = -1;
            d.moveTo(t.absoluteVec(new BlockPos(d == a ? 5 : 10, 1, 16).getCenter().subtract(0, 0.5, 0)));
        }
        bedtime(t, a);
        StringBuilder log = new StringBuilder();
        t.onEachTick(() -> {
            if (t.getTick() % 60 == 0 && log.length() < 2500) for (DrekkiEntity d : new DrekkiEntity[]{a, b}) {
                var w = walks.of(d.drekkiId());
                log.append(" | t").append(t.getTick()).append(d == a ? " A " : " B ").append(t.relativePos(d.blockPosition()).toShortString())
                   .append(w == null ? " no walk" : " node " + t.relativePos(BlockPos.of(w.node)).toShortString() + " asleep " + w.asleep).append(" sleeping ").append(d.isSleepingLoop());
            }
        });
        t.succeedWhen(() -> {
            for (DrekkiEntity d : new DrekkiEntity[]{a, b}) {
                boolean onBed = t.getLevel().getBlockState(d.blockPosition()).is(DrekkiBlocks.STRAW_BEDDING.get());
                t.assertTrue(d.isSleepingLoop() && onBed, (d == a ? "A" : "B") + " asleep on bedding: sleeping " + d.isSleepingLoop() + " on " + t.getLevel().getBlockState(d.blockPosition()).getBlock() + log);
            }
            t.assertFalse(a.blockPosition().equals(b.blockPosition()), "each on its own block");
            walks.end(a.drekkiId()); walks.end(b.drekkiId());
        });
    }

    /** two walkers meet head-on in a corridor one block wide: they pass through each other only once they are blocked,
     *  and both get through (Shane, 2026-11-28: they pushed against each other and stood there) */
    @GameTest(template = "field", timeoutTicks = 1200, batch = "trails_by_day_narrow")
    public static void twoWalkersPassInAOneBlockCorridor(GameTestHelper t) {
        ground(t);
        day(t);
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity a = LifeTests.hatchOne(t, p, h);
        Ranch.putEgg(p, h, dev.frosthaven.ranchercompanion.item.CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        DrekkiEntity b = t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(h).inflate(20), x -> p.getUUID().equals(x.owner()) && x != a).get(0);
        for (int x = 8; x <= 30; x++) print(t, x, 6, Direction.EAST);
        for (int x = 12; x <= 28; x++) for (int y = 1; y <= 3; y++) { t.setBlock(new BlockPos(x, y, 5), Blocks.STONE); t.setBlock(new BlockPos(x, y, 7), Blocks.STONE); }
        TrailWalks walks = TrailWalks.get(t.getLevel().getServer());
        int baseX = t.absolutePos(BlockPos.ZERO).getX();
        boolean[] met = {false}, passedEarly = {false};
        int[] aMax = {0}, bMin = {99};
        t.runAfterDelay(3, () -> {
            for (DrekkiEntity d : new DrekkiEntity[]{a, b}) d.setLook(2, d.line(), d.diet(), d.astral(), d.horn());
            var wa = walks.start(a, t.absolutePos(new BlockPos(13, 1, 6)));
            a.moveTo(t.absoluteVec(new BlockPos(13, 1, 6).getCenter().subtract(0, 0.5, 0)));
            var wb = walks.start(b, t.absolutePos(new BlockPos(27, 1, 6)));
            wb.dir = -1;
            b.moveTo(t.absoluteVec(new BlockPos(27, 1, 6).getCenter().subtract(0, 0.5, 0)));
        });
        t.onEachTick(() -> {
            int ax = (int) Math.floor(a.getX()) - baseX, bx = (int) Math.floor(b.getX()) - baseX;
            if (Math.abs(a.getX() - b.getX()) < 1.5) met[0] = true;
            if (dev.frosthaven.ranchercompanion.trail.Passing.through(a, b) && !met[0]) passedEarly[0] = true;
            aMax[0] = Math.max(aMax[0], ax); bMin[0] = Math.min(bMin[0], bx);
        });
        t.succeedWhen(() -> {
            t.assertFalse(passedEarly[0], "pass-through only once they were blocked");
            t.assertTrue(met[0], "they met");
            t.assertTrue(aMax[0] >= 24 && bMin[0] <= 16, "both got past each other: a reached x " + aMax[0] + ", b x " + bMin[0]);
        });
    }
}
