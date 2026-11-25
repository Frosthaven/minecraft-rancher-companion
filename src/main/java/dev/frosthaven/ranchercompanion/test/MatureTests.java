package dev.frosthaven.ranchercompanion.test;

import static dev.frosthaven.ranchercompanion.test.TraitTests.give;
import static dev.frosthaven.ranchercompanion.test.TraitTests.room;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.combat.Abilities;
import dev.frosthaven.ranchercompanion.combat.CombatGoals;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiMenu;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.DrekkiConfig;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The Mature stage (spec, Stage 3; 0.5.0): growing up, the saddle slot and riding, the four abilities, and the rules a
 * server keeps for a ridden drekki. The steering itself runs in the rider's game: the real-game self test rides it.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class MatureTests {
    private MatureTests() {}

    private static DrekkiEntity mature(GameTestHelper t, ServerPlayer p, Diet diet, Life.Stance stance) {
        DrekkiEntity d = CombatTests.out(t, p, 3, diet, stance);
        room(d);
        return d;
    }

    private static ItemStack saddle() { return new ItemStack(Items.SADDLE); }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void aFledglingGrowsIntoAMatureAndANewFledglingIsPassive(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 1, Diet.RAW, Life.Stance.AGGRESSIVE);
        double days = DrekkiConfig.STAGE_DAYS.get();
        d.life.travel = Life.stageTicks(days) - 5;
        t.runAfterDelay(45, () -> {
            t.assertTrue(d.stage() == 2 && d.life.stance == Life.Stance.PASSIVE, "a new Fledgling, Passive: stage " + d.stage() + ", " + d.life.stance);
            d.setBardingItem(new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get()));
            d.setStance(Life.Stance.REACTIVE);
            d.life.travel = Life.stageTicks(days) * 2 - 5;
            t.runAfterDelay(45, () -> {
                t.assertTrue(d.stage() == 3, "grown to Mature: stage " + d.stage() + ", travel " + d.life.travel);
                t.assertTrue(d.getMaxHealth() == 50 && d.getHealth() == 50, "50 health: " + d.getHealth() + " of " + d.getMaxHealth());
                t.assertTrue(d.bardingItem().is(DrekkiItems.BARDING.get(Barding.IRON).get()), "its barding stays on (one barding for both stages since 0.6.0)");
                t.assertTrue(d.life.stance == Life.Stance.REACTIVE, "the stance its owner chose stays");
                t.assertTrue(d.getBbHeight() > 1.6, "a Mature's hitbox: " + d.getBbHeight());
                d.life.travel = Life.stageTicks(days) * 9;
                t.runAfterDelay(25, () -> { t.assertTrue(d.stage() == 3, "and no further"); t.succeed(); });
            });
        });
    }

    @GameTest(template = "floor")
    public static void theSaddleSlotIsAMaturesAndOnlyASaddledOneIsRidden(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        /* a new entity (as a game makes one from the server's packet, before any synced value) has its stage's hitbox */
        DrekkiEntity fresh = new DrekkiEntity(dev.frosthaven.ranchercompanion.RancherCompanion.DREKKI.get(), t.getLevel());
        t.assertTrue(fresh.getBbWidth() == dev.frosthaven.ranchercompanion.look.Dims.of(fresh.stage()).hitW, "a new entity's hitbox is its stage's: " + fresh.getBbWidth());
        DrekkiMenu young = new DrekkiMenu(1, p.getInventory(), d);
        t.assertTrue(!young.saddled && young.slots.size() == 3 + 36, "a Fledgling has no saddle slot");
        d.setLook(3, d.line(), d.diet(), d.astral(), d.horn());
        DrekkiMenu m = new DrekkiMenu(2, p.getInventory(), d);
        t.assertTrue(m.saddled && m.slots.size() == 4 + 36, "a Mature has one");
        t.assertTrue(m.slots.get(3).mayPlace(saddle()) && !m.slots.get(3).mayPlace(new ItemStack(Items.LEATHER)) && !m.slots.get(1).mayPlace(saddle()), "it takes a saddle and nothing else");
        /* no saddle: the empty-handed click opens the screen as before, it is not ridden */
        t.assertTrue(!d.rideable() && !d.saddled(), "without a saddle it is not rideable and none is drawn");
        /* shift-click a saddle in from the inventory */
        p.getInventory().setItem(9, saddle());
        m.quickMoveStack(p, 4);
        t.assertTrue(d.saddle().is(Items.SADDLE) && d.saddled() && d.rideable() && p.getInventory().getItem(9).isEmpty(), "a shift-clicked saddle is worn: " + d.saddle());
        ServerPlayer other = t.makeMockServerPlayerInLevel();
        other.moveTo(p.position());
        d.interact(other, InteractionHand.MAIN_HAND);
        t.assertTrue(!other.isPassenger(), "another player cannot mount it");
        d.interact(p, InteractionHand.MAIN_HAND);
        t.assertTrue(p.getVehicle() == d && d.getControllingPassenger() == p, "its owner mounts with an empty hand");
        /* any item with no use of its own on a drekki mounts too; one with a use keeps it; a sneak click opens the screen */
        p.stopRiding();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        p.setShiftKeyDown(true);
        try { d.interact(p, InteractionHand.MAIN_HAND); } catch (RuntimeException e) { /* the mock player has no connection for the screen's packet */ }
        t.assertTrue(!p.isPassenger(), "a sneak click with an item does not mount (it opens the screen)");
        p.setShiftKeyDown(false);
        d.interact(p, InteractionHand.MAIN_HAND);
        t.assertTrue(p.getVehicle() == d, "its owner mounts with a sword in hand");
        p.stopRiding();
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LEAD));
        d.interact(p, InteractionHand.MAIN_HAND);
        t.assertTrue(!p.isPassenger(), "a lead keeps its own use");
        /* employed, it is not ridden, and work puts a rider down */
        p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        /* checked in (it lives at a hatchery), it is not ridden, and a check in puts its rider down */
        d.interact(p, InteractionHand.MAIN_HAND);
        d.bind(p.getUUID(), d.drekkiId(), t.absolutePos(net.minecraft.core.BlockPos.ZERO));
        t.assertTrue(!d.rideable() && !p.isPassenger(), "a check in puts its rider down");
        try { d.interact(p, InteractionHand.MAIN_HAND); } catch (RuntimeException e) { /* the mock player has no connection for the screen's packet */ }
        t.assertTrue(!p.isPassenger(), "a drekki that is not checked out is not ridden");
        d.bind(p.getUUID(), d.drekkiId(), null);
        d.setJob(1);
        d.interact(p, InteractionHand.MAIN_HAND);
        t.assertTrue(!d.rideable() && !p.isPassenger(), "an employed drekki is not ridden");
        d.setJob(0);
        d.interact(p, InteractionHand.MAIN_HAND);
        t.assertTrue(p.getVehicle() == d, "back from work it is ridden again");
        d.setJob(1);
        t.assertTrue(!p.isPassenger(), "taking a job puts its rider down");
        d.setJob(0);
        d.interact(p, InteractionHand.MAIN_HAND);
        t.assertTrue(!CombatGoals.fights(d), "a ridden drekki picks no fights");
        /* the saddle taken off under the rider: the rider gets down */
        m.slots.get(3).set(ItemStack.EMPTY);
        t.assertTrue(!p.isPassenger() && !d.saddled(), "no saddle, no rider");
        /* a ghost and a Fledgling are never ridden, saddle or not */
        d.setSaddle(saddle());
        d.life.ghost = true; d.syncLife();
        t.assertTrue(!d.rideable(), "a ghost is not ridden");
        d.life.ghost = false; d.syncLife();
        /* saved and loaded with it */
        var tag = new net.minecraft.nbt.CompoundTag();
        d.addAdditionalSaveData(tag);
        d.setSaddle(ItemStack.EMPTY);
        d.readAdditionalSaveData(tag);
        t.assertTrue(d.saddle().is(Items.SADDLE) && d.saddled(), "the saddle is saved with it");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 40)
    public static void aTombstoneKeepsTheSaddle(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, Diet.RAW, Life.Stance.PASSIVE);
        d.setSaddle(saddle());
        d.setBardingItem(new ItemStack(DrekkiItems.BARDING.get(Barding.IRON).get()));
        TombstoneBlockEntity be = TombTests.dieForGood(t, d);
        t.assertTrue(be.items().getItem(3).is(Items.SADDLE) && be.items().getItem(1).is(DrekkiItems.BARDING.get(Barding.IRON).get()), "the saddle is in the fourth slot: " + be.items());
        t.assertTrue(!be.drekki().contains("Saddle"), "and not in its record");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void itNeverSuffocates(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, Diet.RAW, Life.Stance.PASSIVE);
        d.setNoAi(true);
        t.assertTrue(d.isInvulnerableTo(d.damageSources().drown()) && d.isInvulnerableTo(d.damageSources().inWall()), "drowning and a block around its head do it no harm");
        float before = d.getHealth();
        d.hurt(d.damageSources().drown(), 5); d.hurt(d.damageSources().inWall(), 5);
        d.setAirSupply(0);
        t.getLevel().setBlockAndUpdate(d.blockPosition().above(), Blocks.STONE.defaultBlockState());  /* its head inside a block */
        t.runAfterDelay(60, () -> {
            t.assertTrue(d.getHealth() == before && d.isAlive(), "unhurt after 3 s with its head in stone and no air: " + d.getHealth());
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void thePounceHitsForTwoBites(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, Diet.RAW, Life.Stance.AGGRESSIVE);
        Husk z = EntityAt.husk(t, d, 4, 0);
        float full = z.getHealth();
        /* the leap lands at biting reach of the target, not past it, as the bite lands */
        int[] began = {-1};
        t.onEachTick(() -> {
            if (began[0] < 0 && !Abilities.ready(d)) began[0] = d.tickCount;
            if (began[0] >= 0 && d.tickCount == began[0] + Abilities.POUNCE_LANDS + 3) {
                double gap = z.getX() - d.getX(), touch = d.getBbWidth() / 2 + z.getBbWidth() / 2;
                t.assertTrue(gap > touch - 0.2 && gap < touch + 1.0 && Math.abs(z.getZ() - d.getZ()) < 0.6 && d.onGround(),
                    "it lands in front of its target, in reach: " + gap + " blocks short of it (boxes touch at " + touch + "), on the ground " + d.onGround());
            }
        });
        t.runAfterDelay(40, () -> {
            t.assertTrue(began[0] >= 0, "it pounced");
            t.assertTrue(full - z.getHealth() >= Abilities.POUNCE - 2.01f, "a pounce from 4 blocks: " + (full - z.getHealth()) + " damage (armor takes some of 8)");
            t.assertTrue(!Abilities.ready(d) && d.abilityAt > Vitals.now(d) + 100, "and its 15 s cooldown runs");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void aFledglingHasNoPounce(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.AGGRESSIVE);
        EntityAt.husk(t, d, 3, 0);
        t.runAfterDelay(40, () -> { t.assertTrue(d.abilityAt == 0 && !Abilities.ready(d), "no ability before Mature"); t.succeed(); });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void theBreathHitsEverythingInItsCone(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, Diet.COOKED, Life.Stance.AGGRESSIVE);
        d.setNoAi(true);
        d.setYRot(-90); d.yBodyRot = -90; d.yHeadRot = -90;  /* facing +x */
        Husk a = EntityAt.husk(t, d, 2, 0), b = EntityAt.husk(t, d, 3, 1), behind = EntityAt.husk(t, d, -3, 0), far = EntityAt.husk(t, d, 7, 0);
        float full = a.getHealth();
        d.breathIn = 1;
        t.runAfterDelay(3, () -> {
            t.assertTrue(a.getHealth() < full && b.getHealth() < full, "both in the cone are hit: " + a.getHealth() + ", " + b.getHealth());
            t.assertTrue(behind.getHealth() == full && far.getHealth() == full, "not the one behind it nor the one 7 blocks off");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void steelEdgeAndRejuvenate(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, Diet.SEED, Life.Stance.PASSIVE);
        d.setNoAi(true);
        double base = p.getAttributeValue(Attributes.ATTACK_DAMAGE);
        t.runAfterDelay(15, () -> {
            t.assertTrue(!Abilities.edged(p), "no Steel edge out of a fight");
            Husk z = EntityAt.husk(t, d, 3, 3);
            p.setLastHurtMob(z);
            t.runAfterDelay(15, () -> {
                t.assertTrue(Abilities.edged(p) && p.getAttributeValue(Attributes.ATTACK_DAMAGE) == base + 2, "in a fight, Passive too: +2 attack damage (" + p.getAttributeValue(Attributes.ATTACK_DAMAGE) + ")");
                t.runAfterDelay(Abilities.EDGE_TICKS + 10, () -> {
                    t.assertTrue(!Abilities.edged(p) && p.getAttributeValue(Attributes.ATTACK_DAMAGE) == base, "gone after 5 s");
                    /* the same drekki as a Root: Rejuvenate when its owner is hurt */
                    d.setLook(3, d.line(), Diet.ROOT.ordinal(), d.astral(), d.horn());
                    d.abilityAt = 0;
                    p.setHealth(10);
                    t.runAfterDelay(12, () -> {
                        t.assertTrue(p.getHealth() >= 14 && p.hasEffect(MobEffects.REGENERATION), "Rejuvenate heals 4 and gives regeneration: " + p.getHealth());
                        p.setHealth(20);
                        t.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void theServersRulesForARider(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = mature(t, p, Diet.RAW, Life.Stance.PASSIVE);
        d.setSaddle(saddle());
        p.startRiding(d, true);
        t.assertTrue(p.getVehicle() == d, "riding");
        /* Ocean Walker and Lava Walker: on top from a step or a short drop, not from a plunge */
        var water = Fluids.WATER.getSource(false); var lava = Fluids.LAVA.getSource(false);
        t.assertTrue(!d.canStandOnFluid(water) && !d.canStandOnFluid(lava), "without the traits it walks on neither");
        give(d, Trait.OCEAN_WALKER);
        t.assertTrue(d.canStandOnFluid(water) && !d.canStandOnFluid(lava), "Ocean Walker: water only");
        t.assertTrue(!d.isInvulnerableTo(d.damageSources().lava()), "lava burns a ridden drekki without Lava Walker");
        give(d, Trait.LAVA_WALKER);
        t.assertTrue(d.canStandOnFluid(lava), "Lava Walker: lava");
        t.assertTrue(d.isInvulnerableTo(d.damageSources().lava()), "and lava does not burn a ridden Lava Walker");
        d.fallDistance = 6;
        t.assertTrue(!d.canStandOnFluid(water), "from more than 3 blocks it plunges in");
        d.fallDistance = 0;
        p.stopRiding();
        t.assertTrue(!d.canStandOnFluid(water), "unridden it walks on no liquid");
        /* Predator's Eye: hostile mobs near a ridden holder glow */
        p.startRiding(d, true);
        give(d, Trait.PREDATORS_EYE);
        Husk z = EntityAt.husk(t, d, 5, 0);
        z.setNoAi(true);
        t.runAfterDelay(70, () -> {  /* past a new player's 3 s without damage */
            t.assertTrue(z.hasEffect(MobEffects.GLOWING), "Predator's Eye: the husk glows");
            /* the fall is the rider's; with Leap of Faith it is no one's */
            p.setHealth(20); p.invulnerableTime = 0;
            d.causeFallDamage(13, 1, d.damageSources().fall());
            t.assertTrue(p.getHealth() < 20 && d.getHealth() == d.getMaxHealth(), "a 13 block fall hurts the rider, not the drekki: " + p.getHealth());
            p.setHealth(20); p.invulnerableTime = 0;
            give(d, Trait.LEAP_OF_FAITH);
            d.causeFallDamage(40, 1, d.damageSources().fall());
            t.assertTrue(p.getHealth() == 20, "Leap of Faith: safe from any height");
            /* deep liquid takes the rider off */
            BlockPos at = d.blockPosition();
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 0; dy <= 1; dy++)
                t.getLevel().setBlockAndUpdate(at.offset(dx, dy, dz), (dx == 0 && dz == 0 ? Blocks.WATER : Blocks.GLASS).defaultBlockState());
            t.runAfterDelay(20, () -> {
                t.assertTrue(!p.isPassenger(), "in deep water the rider is off");
                t.succeed();
            });
        });
    }

    /** the foot tint: the biome category of the hatch, kept for life; a drekki from before 0.5.0 takes its hatchery's */
    @GameTest(template = "floor", timeoutTicks = 60)
    public static void theBirthBiomeIsKeptForLife(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 1, Diet.RAW, Life.Stance.PASSIVE);
        int here = dev.frosthaven.ranchercompanion.life.Places.CATEGORIES.indexOf(dev.frosthaven.ranchercompanion.life.Places.category(t.getLevel(), d.blockPosition()));
        t.assertTrue(d.birth() == here, "hatched with the biome of its hatchery: " + d.birth() + " against " + here);
        d.setBirth(5);  /* as if hatched in a jungle */
        var tag = new net.minecraft.nbt.CompoundTag();
        d.addAdditionalSaveData(tag);
        d.setBirth(0);
        d.readAdditionalSaveData(tag);
        t.assertTrue(d.birth() == 5, "saved and loaded with it");
        t.assertTrue(dev.frosthaven.ranchercompanion.look.FootTint.of(5) != dev.frosthaven.ranchercompanion.look.FootTint.of(here), "and a jungle's tint is not this biome's");
        /* a save from before 0.5.0 has none: it takes its hatchery's at its next tick, and its record keeps it */
        tag.remove("Birth");
        d.setBirth(7);
        d.readAdditionalSaveData(tag);
        t.runAfterDelay(5, () -> {
            var rec = dev.frosthaven.ranchercompanion.ranch.Ranch.roster(t.getLevel().getServer()).get(d.drekkiId());
            t.assertTrue(d.birth() == here, "it took its hatchery's biome: " + d.birth());
            t.assertTrue(rec != null && rec.save.getInt("Birth") == here, "and its roster record has it");
            t.succeed();
        });
    }

    /** places a husk beside a drekki, whatever way the test area lies */
    static final class EntityAt {
        static Husk husk(GameTestHelper t, DrekkiEntity d, double dx, double dz) {
            Husk z = net.minecraft.world.entity.EntityType.HUSK.create(t.getLevel());
            z.moveTo(d.getX() + dx, d.getY(), d.getZ() + dz);
            z.setNoAi(true);
            t.getLevel().addFreshEntity(z);
            return z;
        }
    }
}
