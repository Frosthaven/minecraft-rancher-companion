package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.net.HatcheryView;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * An employed drekki at work (Shane, 2026-11-26: a train driver's eyes kindled and it sat Miserable): no travel time,
 * so no growth or kindling, and no mood drain. A hatchery taken away without breaking (Carry On) lets its drekki go
 * to "No hatchery" the next time its owner opens a hatchery screen.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class WorkTests {
    private WorkTests() {}

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void anEmployedDrekkiNeitherGrowsNorSours(GameTestHelper t) {
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry e = r.of(p.getUUID()).get(0);
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, e.id);
        r.move(e, Roster.Where.WORK, null);  /* on a job seat: employed */
        long travel = Life.stageTicks(7) - 30;  /* a few ticks short of growing up, as a travelling drekki */
        d.life.travel = travel;
        d.life.mood = 50;
        d.life.food = 0;  /* hungry: a drekki not at work would lose mood for it */
        p.teleportTo(p.serverLevel(), p.getX() + 200, p.getY(), p.getZ(), 0, 0);  /* and its owner far away */
        t.runAfterDelay(150, () -> {
            t.assertTrue(Ranch.employed(d), "still employed");
            t.assertTrue(d.life.travel == travel && d.stage() == 1, "no travel time at work: " + d.life.travel + " (was " + travel + "), stage " + d.stage());
            t.assertTrue(d.life.mood >= 50, "no mood drain at work: " + d.life.mood);
            r.remove(e.id);
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void aHatcheryCarriedAwayLetsItsDrekkiGo(GameTestHelper t) {
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        LifeTests.hatchOne(t, p, h);
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry e = r.of(p.getUUID()).get(0);
        t.assertTrue(e.where == Roster.Where.HOME, "at the hatchery: " + e.where);
        /* Carry On's pick-up: the block entity goes first, so the break hook never sees a hatchery */
        t.getLevel().removeBlockEntity(h);
        t.getLevel().setBlock(h, Blocks.AIR.defaultBlockState(), 3);
        t.assertTrue(e.where == Roster.Where.HOME, "still filed at the old spot: " + e.where);
        BlockPos moved = LifeTests.H.offset(3, 0, 0);
        t.setBlock(moved, DrekkiBlocks.HATCHERY.get());
        HatcheryView.of(p, (HatcheryBlockEntity) t.getBlockEntity(moved));  /* its owner opens the moved hatchery */
        t.assertTrue(e.where == Roster.Where.NONE && e.home == null && e.base == null, "to No hatchery: " + e.where + " home " + e.home + " base " + e.base);
        r.remove(e.id);
        t.succeed();
    }
}
