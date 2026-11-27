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
}
