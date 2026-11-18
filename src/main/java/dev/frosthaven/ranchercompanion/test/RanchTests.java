package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.CreativeEggItem;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.ranch.Ground;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-world checks of owning drekki (spec, Pup release): a hatch, check out and in, the carrier, release, the pen's
 * placement behind a fence, and a stale copy refused. Each test builds a roofed hatchery on the floor template.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class RanchTests {
    private RanchTests() {}

    private static final BlockPos H = new BlockPos(6, 1, 6);

    /** a hatchery at H with a 3x3 roof 4 blocks up and a torch beside it; returns its absolute position */
    private static BlockPos stable(GameTestHelper t) {
        for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) t.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
        t.setBlock(H, DrekkiBlocks.HATCHERY.get());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) t.setBlock(H.offset(dx, 4, dz), Blocks.OAK_PLANKS);
        t.setBlock(H.offset(-3, 0, -3), Blocks.TORCH);
        return t.absolutePos(H);
    }

    private static ServerPlayer player(GameTestHelper t) {
        ServerPlayer p = t.makeMockServerPlayerInLevel();
        p.moveTo(t.absoluteVec(H.offset(2, 0, 2).getCenter()));
        return p;
    }

    /** this test's player's drekki (tests run side by side, so others are near) */
    private static List<DrekkiEntity> drekki(GameTestHelper t, ServerPlayer p) {
        return t.getLevel().getEntitiesOfClass(DrekkiEntity.class, new AABB(t.absolutePos(BlockPos.ZERO)).inflate(40),
            d -> d.owned() && p.getUUID().equals(d.owner()));
    }

    /** puts a Ready egg in and hatches it; returns the roster entry */
    private static Roster.Entry hatch(GameTestHelper t, ServerPlayer p, BlockPos h) {
        Ranch.putEgg(p, h, CreativeEggItem.egg(4));
        Ranch.hatch(p, h);
        List<Roster.Entry> mine = Ranch.roster(t.getLevel().getServer()).of(p.getUUID());
        if (mine.size() != 1) throw new net.minecraft.gametest.framework.GameTestAssertException("expected one drekki after the hatch, got " + mine.size() + " (" + Ranch.hatchProblems(t.getLevel(), h) + ")");
        return mine.get(0);
    }

    @GameTest(template = "floor")
    public static void hatchPlacesThePupAtHome(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry e = hatch(t, p, h);
        t.assertTrue(e.where == Roster.Where.HOME, "the hatchling lives at the hatchery");
        t.assertTrue(drekki(t, p).size() == 1, "the hatchling is in the world");
        DrekkiEntity d = drekki(t, p).get(0);
        t.assertTrue(d.stage() == 1 && d.blockPosition().distManhattan(h) <= 4, "a stage 1 drekki next to the hatchery");
        t.assertFalse(d.canBeSeenAsEnemy(), "no mob targets a stage 1 drekki");
        Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void checkOutCarryLetOutCheckInRelease(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry e = hatch(t, p, h);
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, e.id);
        t.assertTrue(e.where == Roster.Where.OUT && drekki(t, p).get(0).isOut(), "checked out");

        ItemStack carrier = new ItemStack(DrekkiItems.SMALL_CARRIER.get());
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, carrier);
        Ranch.carry(p, drekki(t, p).get(0), carrier);
        t.assertTrue(e.where == Roster.Where.CARRIED && carrier.has(DrekkiData.CARRIED.get()), "in the carrier");
        t.runAfterDelay(1, () -> {
            t.assertTrue(drekki(t, p).isEmpty(), "a carried drekki is not in the world");
            Ranch.letOut(p, carrier, h.offset(2, 0, 0));
            t.assertTrue(e.where == Roster.Where.OUT && !carrier.has(DrekkiData.CARRIED.get()), "let out");
            Ranch.letOut(p, carrier, h.offset(2, 0, 0));
            t.runAfterDelay(1, () -> {
                t.assertTrue(drekki(t, p).size() == 1, "an empty carrier lets nothing out twice");
                Ranch.act(p, h, Ranch.Action.CHECK_IN, null);
                t.assertTrue(e.where == Roster.Where.HOME && !drekki(t, p).get(0).isOut(), "checked in");
                Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
                t.runAfterDelay(1, () -> {
                    t.assertTrue(r.get(e.id) == null, "released: the record is gone");
                    t.assertTrue(drekki(t, p).isEmpty(), "released: it poofed");
                    t.succeed();
                });
            });
        });
    }

    @GameTest(template = "floor")
    public static void staleCopyIsRefused(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry e = hatch(t, p, h);
        DrekkiEntity copy = RancherCompanion.DREKKI.get().create(t.getLevel());
        copy.load(e.save);
        copy.setUUID(java.util.UUID.randomUUID());
        copy.bind(p.getUUID(), e.id, h);
        copy.moveTo(t.absoluteVec(H.offset(-2, 0, 0).getCenter()));
        t.getLevel().addFreshEntity(copy);
        t.runAfterDelay(1, () -> {
            t.assertTrue(drekki(t, p).size() == 1, "a second copy of the same drekki is refused");
            Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
            t.succeed();
        });
    }

    @GameTest(template = "floor")
    public static void placementPrefersThePenBehindAFence(GameTestHelper t) {
        BlockPos h = stable(t);
        /* a fence line one block east of the hatchery, the whole width: east is outside */
        for (int dz = -5; dz <= 5; dz++) t.setBlock(H.offset(1, 0, dz), Blocks.OAK_FENCE);
        /* fill the west side but one far spot, so the nearest free spot is east (outside) and the walk must find the far one */
        for (int dx = -3; dx <= 0; dx++) for (int dz = -3; dz <= 3; dz++) {
            if ((dx == 0 && dz == 0) || (dx == -3 && dz == 3)) continue;
            t.setBlock(H.offset(dx, 0, dz), Blocks.OAK_LEAVES);
        }
        t.setBlock(H.offset(0, 0, 3), Blocks.AIR);  /* a lane along the fence to the far spot */
        for (int dz = 1; dz <= 3; dz++) t.setBlock(H.offset(0, 0, dz), Blocks.AIR);
        for (int dx = -3; dx <= 0; dx++) t.setBlock(H.offset(dx, 0, 3), Blocks.AIR);
        t.assertBlockPresent(Blocks.STONE, H.below());
        BlockPos at = new Ground(t.getLevel()).place(h, 1);
        t.assertTrue(at != null && at.getX() <= h.getX(), "placed inside the fence, not at the nearer spot outside: " + at + " (hatchery " + h + ")");
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void pupPenKeepsToAnchors(GameTestHelper t) {
        BlockPos h = stable(t);
        Ground g = new Ground(t.getLevel());
        int far = 0;
        for (long p : g.pen(h, 1)) far = Math.max(far, Math.max(Math.abs(dev.frosthaven.ranchercompanion.ranch.Flood.x(p) - h.getX()), Math.abs(dev.frosthaven.ranchercompanion.ranch.Flood.z(p) - h.getZ())));
        t.assertTrue(far == 3, "no anchors: 3 blocks around the hatchery, got " + far);
        /* bedding 5 out is not within 3 blocks of the hatchery: it does not count */
        t.setBlock(H.offset(0, 0, 5), DrekkiBlocks.STRAW_BEDDING.get());
        int south = 0;
        for (long p : g.pen(h, 1)) south = Math.max(south, dev.frosthaven.ranchercompanion.ranch.Flood.z(p) - h.getZ());
        t.assertTrue(south == 3, "bedding out of reach counts for nothing, got " + south);
        /* a chain: bedding 3 west counts, bedding 3 west of that counts from it, and the area runs 3 past the last */
        t.setBlock(H.offset(-3, 0, 0), DrekkiBlocks.STRAW_BEDDING.get());
        t.setBlock(H.offset(-6, 0, 0), DrekkiBlocks.STRAW_BEDDING.get());
        int west = 0;
        for (long p : g.pen(h, 1)) west = Math.max(west, h.getX() - dev.frosthaven.ranchercompanion.ranch.Flood.x(p));
        t.assertTrue(west == 9, "anchors chain: 3 past the bedding 6 out (9), got " + west);
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void sneakEmptyHandPets(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry e = hatch(t, p, h);
        DrekkiEntity d = drekki(t, p).get(0);
        p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        p.setShiftKeyDown(true);
        var r = p.interactOn(d, net.minecraft.world.InteractionHand.MAIN_HAND);
        t.assertTrue(r.consumesAction() && d.petTicks() > 0, "sneak + empty hand pets it (" + r + ", " + d.petTicks() + ")");
        Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
        t.succeed();
    }

    /* the sleep tests set the world's clock, which every test running beside them shares: each runs in a batch of its own */
    @GameTest(template = "floor", timeoutTicks = 1300, batch = "sleep_bedding")
    public static void sleepsOnBeddingLaidAtNightfall(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry e = hatch(t, p, h);
        /* bedding laid at the moment night falls, 3 blocks south: the pen's cache has not seen it yet */
        for (int dx = -1; dx <= 1; dx++) t.setBlock(H.offset(dx, 0, 3), DrekkiBlocks.STRAW_BEDDING.get());
        t.getLevel().setDayTime(18000);
        t.runAfterDelay(800, () -> {
            DrekkiEntity d = drekki(t, p).get(0);
            BlockPos at = d.blockPosition();
            boolean onBed = t.getLevel().getBlockState(at).is(DrekkiBlocks.STRAW_BEDDING.get());
            t.assertTrue(d.isSleepingLoop() && onBed, "asleep on the bedding: sleeping " + d.isSleepingLoop() + " at " + t.relativePos(at) + " on " + t.getLevel().getBlockState(at));
        });
        staysAsleep(t, p, h, e, 800, 300);
    }

    /** bedding fenced in, so no path reaches it: the drekki sleeps where it is and stays asleep (it once got up every 5 s
     *  to try the bedding again, sleeping and standing all night) */
    @GameTest(template = "floor", timeoutTicks = 1300, batch = "sleep_unreachable")
    public static void staysAsleepWhenItsBeddingIsOutOfReach(GameTestHelper t) {
        BlockPos h = stable(t);
        ServerPlayer p = player(t);
        Roster.Entry e = hatch(t, p, h);
        BlockPos bed = H.offset(0, 0, 3);
        t.setBlock(bed, DrekkiBlocks.STRAW_BEDDING.get());
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            if (dx != 0 || dz != 0) t.setBlock(bed.offset(dx, 0, dz), DrekkiBlocks.FENCE.get());
        t.getLevel().setDayTime(18000);
        t.runAfterDelay(700, () -> t.assertTrue(drekki(t, p).get(0).isSleepingLoop(), "asleep by the hatchery"));
        staysAsleep(t, p, h, e, 700, 500);
    }

    /** from tick from, for ticks ticks, the drekki is asleep every tick; then morning, release, pass */
    private static void staysAsleep(GameTestHelper t, ServerPlayer p, BlockPos h, Roster.Entry e, int from, int ticks) {
        for (int i = 1; i <= ticks; i++) {
            int k = from + i;
            t.runAfterDelay(k, () -> t.assertTrue(drekki(t, p).get(0).isSleepingLoop(), "still asleep at tick " + k));
        }
        t.runAfterDelay(from + ticks + 1, () -> {
            t.getLevel().setDayTime(1000);
            Ranch.act(p, h, Ranch.Action.RELEASE, e.id);
            t.succeed();
        });
    }
}
