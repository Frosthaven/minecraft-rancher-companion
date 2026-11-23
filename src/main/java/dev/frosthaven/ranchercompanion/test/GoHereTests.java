package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.GoHere;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import dev.frosthaven.ranchercompanion.ranch.Seats;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The whistle's Go here (spec, Go here): it walks to the very block looked at and holds; it walks at most 17 blocks,
 * and the last few blocks with no way there it is summoned; on a job seat it is employed (no longer with its owner, Passive), and off the seat it goes home.
 * Create's seats are stood in for by a minecart, so these run without Create.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class GoHereTests {
    private GoHereTests() {}

    /** the owner looks at the top of a block (relative) */
    private static void lookAt(GameTestHelper t, ServerPlayer p, BlockPos rel) {
        p.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(t.absolutePos(rel)).add(0, 0.5, 0));
    }

    @GameTest(template = "floor", timeoutTicks = 300)
    public static void itWalksToTheVeryBlockHoldsThenFollows(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        BlockPos floor = new BlockPos(2, 0, 10);
        lookAt(t, p, floor);
        Vec3 from = d.position();
        t.assertTrue(GoHere.command(p), "sent");
        t.assertTrue(d.goHere != null && d.position().equals(from), "with a way there, it walks: not summoned");
        BlockPos stand = t.absolutePos(floor.above());
        long[] arrived = {-1};
        t.onEachTick(() -> {
            if (arrived[0] < 0 && d.goHere == null && d.blockPosition().equals(stand)) {
                arrived[0] = t.getTick();
                t.assertTrue(Math.abs(d.getX() - (stand.getX() + 0.5)) < 0.01 && Math.abs(d.getZ() - (stand.getZ() + 0.5)) < 0.01, "in its middle: " + d.position());
                Roster.Entry e = Ranch.roster(t.getLevel().getServer()).out(p.getUUID());
                t.assertTrue(!d.isStaying() && e != null && e.stay == null && d.held(), "not told to stay: it holds the spot");
                t.runAfterDelay(GoHere.HOLD - 5, () -> t.assertTrue(d.blockPosition().equals(stand), "still there within the hold: " + d.blockPosition()));
                t.runAfterDelay(GoHere.HOLD + 60, () -> {
                    t.assertTrue(!d.held() && d.distanceTo(p) < Vec3.atCenterOf(stand).distanceTo(p.position()) - 2, "after the hold it follows its owner again: " + d.distanceTo(p));
                    t.succeed();
                });
            }
        });
    }

    @GameTest(template = "floor", timeoutTicks = 500)
    public static void itWalksAtMost17Blocks(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        /* a long walkway 20 blocks above the floor, clear of the tests around this one */
        for (int x = 0; x <= 46; x++) for (int z = 0; z <= 2; z++) t.setBlock(new BlockPos(x, 20, z), Blocks.STONE);
        Vec3[] from = new Vec3[1];
        t.runAfterDelay(5, () -> {  /* it is placed after the first tick */
            d.moveTo(Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(1, 21, 1))));
            d.setDeltaMovement(Vec3.ZERO);
            from[0] = d.position();
            GoHere.sendTo(d, t.absolutePos(new BlockPos(45, 21, 1)));
            t.succeedWhen(() -> {
                t.assertTrue(d.goHere == null && d.held(), "it stopped and holds: goHere " + d.goHere + " at " + d.position());
                double went = Math.hypot(d.getX() - from[0].x, d.getZ() - from[0].z);
                t.assertTrue(went > 11 && went < 19 && d.getY() > from[0].y - 1, "about 17 blocks along the walkway, not 44: " + went + " at y " + d.getY());
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 500)
    public static void aPupWalksAtMost17Blocks(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 1, Diet.RAW, Life.Stance.PASSIVE);
        /* a long walkway 20 blocks above the floor, clear of the tests around this one */
        for (int x = 0; x <= 46; x++) for (int z = 0; z <= 2; z++) t.setBlock(new BlockPos(x, 20, z), Blocks.STONE);
        Vec3[] from = new Vec3[1];
        t.runAfterDelay(5, () -> {  /* it is placed after the first tick */
            d.moveTo(Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(1, 21, 1))));
            d.setDeltaMovement(Vec3.ZERO);
            from[0] = d.position();
            GoHere.sendTo(d, t.absolutePos(new BlockPos(45, 21, 1)));
            t.succeedWhen(() -> {
                t.assertTrue(d.goHere == null && d.held(), "it stopped and holds: goHere " + d.goHere + " at " + d.position());
                double went = Math.hypot(d.getX() - from[0].x, d.getZ() - from[0].z);
                t.assertTrue(went > 11 && went < 19 && d.getY() > from[0].y - 1, "about 17 blocks along the walkway, not 44: " + went + " at y " + d.getY());
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void goHereOnAHostileMobIsAnAttackOrderEvenWhenPassive(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        var z = CombatTests.zombie(t, new BlockPos(9, 1, 9));
        t.runAfterDelay(5, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
            t.assertTrue(GoHere.command(p), "ordered");
            t.assertTrue(d.ordered == z && d.getTarget() == z, "its target is that mob, in Passive");
            t.succeedWhen(() -> t.assertTrue(z.getHealth() < z.getMaxHealth(), "it bites: " + z.getHealth()));
        });
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aPupIsTooYoungToFight(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 1, Diet.RAW, Life.Stance.AGGRESSIVE);
        var z = CombatTests.zombie(t, new BlockPos(9, 1, 9));
        t.runAfterDelay(5, () -> {
            p.lookAt(EntityAnchorArgument.Anchor.EYES, z.position().add(0, 1, 0));
            t.assertTrue(!GoHere.command(p), "no order for a pup");
            t.assertTrue(d.ordered == null && d.getTarget() == null, "no target");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void goHereElsewhereDropsTheOrder(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        var z = CombatTests.zombie(t, new BlockPos(9, 1, 9));
        t.runAfterDelay(5, () -> {
            GoHere.attack(d, z);
            lookAt(t, p, new BlockPos(2, 0, 10));
            t.assertTrue(GoHere.command(p), "sent elsewhere");
            t.runAfterDelay(25, () -> {
                t.assertTrue(d.ordered == null && d.getTarget() == null, "the order and its target are gone: " + d.getTarget());
                t.succeed();
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 120)
    public static void noCatchUpTeleportWithin15Blocks(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        /* a walkway 20 above the floor: the owner 13 blocks along it from the drekki, a wall between them (no walking) */
        for (int x = 0; x <= 16; x++) t.setBlock(new BlockPos(x, 20, 1), net.minecraft.world.level.block.Blocks.STONE);
        for (int y = 21; y <= 24; y++) t.setBlock(new BlockPos(7, y, 1), net.minecraft.world.level.block.Blocks.STONE);
        t.runAfterDelay(5, () -> {
            p.teleportTo(t.getLevel(), t.absolutePos(new BlockPos(14, 21, 1)).getX() + 0.5, t.absolutePos(new BlockPos(14, 21, 1)).getY(), t.absolutePos(new BlockPos(14, 21, 1)).getZ() + 0.5, 0, 0);
            d.moveTo(Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(1, 21, 1))));
            Vec3 at = d.position();
            t.runAfterDelay(30, () -> {
                double wall = Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(7, 21, 1))).distanceTo(at);
                t.assertTrue(d.position().distanceTo(at) < wall, "13 blocks behind, not teleported past the wall: moved " + d.position().distanceTo(at));
                t.succeed();
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void withNoWayThereItIsSummoned(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        for (int y = 1; y <= 3; y++) t.setBlock(new BlockPos(10, y, 2), Blocks.STONE);  /* a pillar too tall to climb */
        BlockPos top = new BlockPos(10, 3, 2);
        lookAt(t, p, top);
        t.assertTrue(GoHere.command(p), "sent");
        BlockPos stand = t.absolutePos(top.above());
        t.succeedWhen(() -> t.assertTrue(d.goHere == null && d.blockPosition().equals(stand) && d.held() && !d.isStaying(), "summoned onto the pillar, holding: " + d.blockPosition()));
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void onAJobSeatItIsEmployedAndOffItItGoesHome(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.AGGRESSIVE);
        ServerLevel level = t.getLevel();
        var cart = EntityType.MINECART.create(level);
        cart.moveTo(Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(9, 1, 9))));
        level.addFreshEntity(cart);
        Seats.Seat seat = new Seats.Seat() {
            @Override public Vec3 at() { return cart.position(); }
            @Override public boolean free() { return !cart.isVehicle(); }
            @Override public boolean sit(Mob mob) { return mob.startRiding(cart, true); }
            @Override public int role() { return Seats.EMPLOYED; }
        };
        Seats before = Seats.Holder.seats;
        Seats.Holder.seats = new Seats() {
            @Override public Seat looked(ServerPlayer pl, double range, double blockDist) { return seat; }
            @Override public Seat at(ServerLevel l, BlockPos pos) { return null; }
        };
        try {
            lookAt(t, p, new BlockPos(9, 0, 9));
            t.assertTrue(GoHere.command(p), "sent to the seat");
        } finally { Seats.Holder.seats = before; }
        Roster r = Ranch.roster(level.getServer());
        t.runAtTickTime(t.getTick() + 120, () -> {
            Roster.Entry e = r.get(d.drekkiId());
            t.assertTrue(d.getVehicle() == cart, "it sat on the seat");
            t.assertTrue(e.where == Roster.Where.WORK && r.out(p.getUUID()) == null, "employed: no longer out with its owner, who may take out another");
            t.assertTrue(d.life.stance == Life.Stance.PASSIVE, "Passive at work: " + d.life.stance);
            d.stopRiding();  /* the train taken apart */
            t.runAfterDelay(60, () -> {
                t.assertTrue(d.isRemoved() && e.where == Roster.Where.HOME && e.home != null, "off its seat, it went home to its hatchery: " + e.where);
                t.succeed();
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void recallFromJobTakesItOffItsSeatToFollow(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        ServerLevel level = t.getLevel();
        var cart = EntityType.MINECART.create(level);
        cart.moveTo(Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(9, 1, 9))));
        level.addFreshEntity(cart);
        Seats.Seat seat = new Seats.Seat() {
            @Override public Vec3 at() { return cart.position(); }
            @Override public boolean free() { return !cart.isVehicle(); }
            @Override public boolean sit(Mob mob) { return mob.startRiding(cart, true); }
            @Override public int role() { return Seats.EMPLOYED; }
        };
        Seats before = Seats.Holder.seats;
        Seats.Holder.seats = new Seats() {
            @Override public Seat looked(ServerPlayer pl, double range, double blockDist) { return seat; }
            @Override public Seat at(ServerLevel l, BlockPos pos) { return null; }
        };
        try { lookAt(t, p, new BlockPos(9, 0, 9)); GoHere.command(p); } finally { Seats.Holder.seats = before; }
        Roster r = Ranch.roster(level.getServer());
        t.runAtTickTime(t.getTick() + 120, () -> {
            Roster.Entry e = r.get(d.drekkiId());
            t.assertTrue(e.where == Roster.Where.WORK && d.job() == 1, "employed, the button on: " + e.where + " job " + d.job());
            Ranch.recallFromJob(p, d);
            t.assertTrue(e.where == Roster.Where.OUT && r.out(p.getUUID()) == e, "checked out with its owner again: " + e.where);
            t.assertTrue(!d.isPassenger() && !d.isStaying() && d.job() == 0, "off its seat, following");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void recallWithAnotherOutSendsItHome(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        BlockPos hatchery = t.absolutePos(LifeTests.H);
        ServerLevel level = t.getLevel();
        var cart = EntityType.MINECART.create(level);
        cart.moveTo(Vec3.atBottomCenterOf(t.absolutePos(new BlockPos(9, 1, 9))));
        level.addFreshEntity(cart);
        Seats.Seat seat = new Seats.Seat() {
            @Override public Vec3 at() { return cart.position(); }
            @Override public boolean free() { return !cart.isVehicle(); }
            @Override public boolean sit(Mob mob) { return mob.startRiding(cart, true); }
            @Override public int role() { return Seats.STOCK_KEEPER; }
        };
        Seats before = Seats.Holder.seats;
        Seats.Holder.seats = new Seats() {
            @Override public Seat looked(ServerPlayer pl, double range, double blockDist) { return seat; }
            @Override public Seat at(ServerLevel l, BlockPos pos) { return null; }
        };
        try { lookAt(t, p, new BlockPos(9, 0, 9)); GoHere.command(p); } finally { Seats.Holder.seats = before; }
        Roster r = Ranch.roster(level.getServer());
        t.runAtTickTime(t.getTick() + 120, () -> {
            Roster.Entry e = r.get(d.drekkiId());
            t.assertTrue(e.where == Roster.Where.WORK && e.role == Seats.STOCK_KEEPER && d.job() == Seats.STOCK_KEEPER, "employed as the stock keeper: " + e.where + " role " + e.role);
            Ranch.putEgg(p, hatchery, dev.frosthaven.ranchercompanion.item.CreativeEggItem.egg(4));
            Ranch.hatch(p, hatchery);
            Roster.Entry second = r.of(p.getUUID()).stream().filter(x -> !x.id.equals(e.id)).findFirst().orElseThrow();
            Ranch.act(p, hatchery, Ranch.Action.CHECK_OUT, second.id);
            t.assertTrue(r.out(p.getUUID()) == second, "another drekki is out");
            Ranch.recallFromJob(p, d);
            t.assertTrue(d.isRemoved() && e.where == Roster.Where.HOME && e.role == 0 && r.out(p.getUUID()) == second, "it went home to its hatchery; the other stays out: " + e.where);
            t.succeed();
        });
    }
}
