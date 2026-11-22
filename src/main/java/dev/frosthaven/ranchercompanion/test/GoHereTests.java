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
 * The whistle's Go here (spec, Go here): it walks to the very block looked at and stays; with no way to walk there it
 * is summoned; on a job seat it is employed (no longer with its owner, Passive), and off the seat it goes home.
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
    public static void itWalksToTheVeryBlockAndStays(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        BlockPos floor = new BlockPos(2, 0, 10);
        lookAt(t, p, floor);
        Vec3 from = d.position();
        t.assertTrue(GoHere.command(p), "sent");
        t.assertTrue(d.goHere != null && d.position().equals(from), "with a way there, it walks: not summoned");
        BlockPos stand = t.absolutePos(floor.above());
        t.succeedWhen(() -> {
            t.assertTrue(d.goHere == null && d.blockPosition().equals(stand), "on the block: " + d.blockPosition() + " for " + stand);
            t.assertTrue(Math.abs(d.getX() - (stand.getX() + 0.5)) < 0.01 && Math.abs(d.getZ() - (stand.getZ() + 0.5)) < 0.01, "in its middle: " + d.position());
            Roster.Entry e = Ranch.roster(t.getLevel().getServer()).out(p.getUUID());
            t.assertTrue(d.isStaying() && e != null && e.stay != null && e.stay.pos().equals(stand), "it stays there, the roster keeps the spot");
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
        t.succeedWhen(() -> t.assertTrue(d.goHere == null && d.blockPosition().equals(stand) && d.isStaying(), "summoned onto the pillar, staying: " + d.blockPosition()));
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
            @Override public boolean job() { return true; }
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
}
