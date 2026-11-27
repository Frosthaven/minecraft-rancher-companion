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
 * An employed drekki at work: no mood drain for being away (2026-11-26); it gets hungry and eats from home, and by
 * default it does not age (server config age_at_work; age_at_home for the stable, 2026-11-27). A hatchery taken away without breaking (Carry On) lets its drekki go
 * to "No hatchery" the next time its owner opens a hatchery screen.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class WorkTests {
    private WorkTests() {}

    /** no travel time by default and no drain for being away from its owner; hungry, it eats from its hatchery's trough
     *  from its seat, and the meal lifts its mood (Shane, 2026-11-27) */
    @GameTest(template = "floor", timeoutTicks = 400)
    public static void anEmployedDrekkiEatsFromHomeAndDoesNotGrow(GameTestHelper t) {
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        LifeTests.trough(t, p, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARROT, 16));
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry e = r.of(p.getUUID()).get(0);
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, e.id);
        r.move(e, Roster.Where.WORK, null);  /* on a job seat: employed */
        d.setStaying(true); d.setJob(1);  /* as Ranch.employ leaves it: staying, with its role */
        t.assertTrue(e.base != null, "it keeps its hatchery's kinship at work");
        long travel = Life.stageTicks(7) - 30;  /* a few ticks short of growing up, as a travelling drekki */
        d.life.travel = travel;
        d.life.mood = 50;
        d.life.food = 0;  /* hungry */
        d.getNavigation().stop();
        p.teleportTo(p.serverLevel(), p.getX() + 200, p.getY(), p.getZ(), 0, 0);  /* and its owner far away */
        t.succeedWhen(() -> {
            t.assertTrue(Ranch.employed(d), "still employed");
            t.assertTrue(d.life.travel == travel && d.stage() == 1, "no travel time at work: " + d.life.travel + " (was " + travel + "), stage " + d.stage());
            t.assertTrue(d.life.food > 0, "it ate from its hatchery's trough: food " + d.life.food);
            t.assertTrue(d.life.mood > 52, "the meal lifted its mood, and being away did not drain it: " + d.life.mood);
            r.remove(e.id);
        });
    }

    /** a drekki at its hatchery does not age by default; with age_at_home on it does (Shane, 2026-11-27) */
    @GameTest(template = "floor", timeoutTicks = 300)
    public static void aDrekkiAtItsHatcheryAgesOnlyWhenSet(GameTestHelper t) {
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry e = r.of(p.getUUID()).get(0);
        long time = Life.stageTicks(dev.frosthaven.ranchercompanion.DrekkiConfig.STAGE_DAYS.get()) - 30;
        d.life.travel = time;
        t.runAfterDelay(100, () -> {
            t.assertTrue(d.life.travel == time && d.stage() == 1, "no aging at home by default: " + d.life.travel + ", stage " + d.stage());
            dev.frosthaven.ranchercompanion.DrekkiConfig.AGE_AT_HOME.set(true);
        });
        t.succeedWhen(() -> {
            t.assertTrue(d.stage() == 2, "with age_at_home it grew up at home: stage " + d.stage());
            dev.frosthaven.ranchercompanion.DrekkiConfig.AGE_AT_HOME.set(false);
            r.remove(e.id);
        });
    }

    /** a drekki employed before kinship had no base: the roster gives it its owner's hatchery when it loads */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void aDriverWithoutABaseGetsItsOwnersHatchery(GameTestHelper t) {
        BlockPos h = LifeTests.stable(t);
        ServerPlayer p = LifeTests.player(t);
        LifeTests.hatchOne(t, p, h);
        Roster r = Ranch.roster(t.getLevel().getServer());
        var home = Roster.at(t.getLevel(), h);
        Roster.Entry driver = r.add(java.util.UUID.randomUUID(), p.getUUID(), new net.minecraft.nbt.CompoundTag(), Roster.Where.WORK, null);
        r.setBase(driver, null);
        var reg = t.getLevel().registryAccess();
        Roster back = Roster.load(r.save(new net.minecraft.nbt.CompoundTag(), reg), reg);
        Roster.Entry loaded = back.get(driver.id);
        t.assertTrue(loaded != null && home.equals(loaded.base), "its owner's hatchery: " + (loaded == null ? "missing" : loaded.base));
        t.assertTrue(back.kin(loaded) >= 1, "with its kin: " + back.kin(loaded));
        for (Roster.Entry x : r.of(p.getUUID())) r.remove(x.id);
        t.succeed();
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
