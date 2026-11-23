package dev.frosthaven.ranchercompanion.test;

import static dev.frosthaven.ranchercompanion.test.TraitTests.as;
import static dev.frosthaven.ranchercompanion.test.TraitTests.give;
import static dev.frosthaven.ranchercompanion.test.TraitTests.has;
import static dev.frosthaven.ranchercompanion.test.TraitTests.room;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.BardingEnchants;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.trait.RoamTraits;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The fifteen former biome traits (spec, Traits: biome traits become activity traits): each learned from its activity,
 * and the nine that need no rider doing what the sheet says. The six that act while ridden are only learned here.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class RoamTraitTests {
    private RoamTraitTests() {}

    private static DrekkiEntity fledgling(GameTestHelper t, ServerPlayer p, int astral) {
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.SEED, Life.Stance.PASSIVE);
        as(d, astral);
        return d;
    }

    private static Husk husk(GameTestHelper t, int dx, int dz) { return CombatTests.zombie(t, LifeTests.H.offset(dx, 0, dz)); }

    /** a second of the player's ticks, as a real player's connection drives them: the distance traits read once a second */
    private static void second(ServerPlayer p) { p.tickCount = (p.tickCount / 20 + 1) * 20; RoamTraits.tick(p); }

    private static void travel(GameTestHelper t, ServerPlayer p, DrekkiEntity d, ResourceLocation stat, int cm, Trait trait, int astral, String what) {
        room(d); as(d, astral);
        second(p);  /* where the count starts */
        p.awardStat(Stats.CUSTOM.get(stat), cm - 100);
        second(p);
        t.assertFalse(has(d, trait), trait.id + " not before " + what);
        p.awardStat(Stats.CUSTOM.get(stat), 200);
        second(p);
        t.assertTrue(has(d, trait), trait.id + " from " + what + " (progress " + d.life.progress.get(trait.id) + ")");
    }

    @GameTest(template = "floor")
    public static void theDistanceTraitsAreLearnedFromTravel(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        travel(t, p, d, Stats.SPRINT_ONE_CM, 200_000, Trait.OPEN_ROAD, Astral.SOLAR, "2,000 blocks at a sprint");
        travel(t, p, d, Stats.SWIM_ONE_CM, 50_000, Trait.DOWNSTREAM, Astral.END, "500 blocks swum");
        travel(t, p, d, Stats.BOAT_ONE_CM, 100_000, Trait.OCEAN_WALKER, Astral.LUNAR, "1,000 blocks by boat");
        travel(t, p, d, Stats.HORSE_ONE_CM, 100_000, Trait.FOUR_WINDS, Astral.AERO, "1,000 blocks on a horse");
        travel(t, p, d, Stats.STRIDER_ONE_CM, 30_000, Trait.LAVA_WALKER, Astral.UNDERWORLD, "300 blocks on a strider");
        /* Frost Strider: on foot over snow, here back and forth between two snow blocks */
        room(d); as(d, Astral.LUNAR);
        BlockPos a = LifeTests.H.offset(3, 0, 3);
        t.setBlock(a.below(), Blocks.SNOW_BLOCK); t.setBlock(a.below().east(), Blocks.SNOW_BLOCK);
        var from = t.absoluteVec(a.getCenter()).add(0, -0.5, 0);
        p.setOnGround(true);
        for (int i = 0; i < 302 && !has(d, Trait.FROST_STRIDER); i++) { p.moveTo(from.add(i % 2, 0, 0)); RoamTraits.tick(p); }
        t.assertTrue(has(d, Trait.FROST_STRIDER), "Frost Strider from 300 blocks on snow (progress " + d.life.progress.get(Trait.FROST_STRIDER.id) + ")");
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void theCountedOnesAreLearnedFromTheirActivities(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        for (int i = 0; i < 10; i++) { var c = t.spawn(EntityType.CREEPER, LifeTests.H.offset(-3, 0, 3)); c.hurt(c.damageSources().playerAttack(p), 1000); }
        t.assertTrue(has(d, Trait.NATURAL_REPELLANT), "Natural Repellant at 10 creepers");
        for (int i = 0; i < 5; i++) { var h = t.spawn(EntityType.HOGLIN, LifeTests.H.offset(-3, 0, 3)); h.hurt(h.damageSources().playerAttack(p), 1000); }
        t.assertTrue(has(d, Trait.HOGLIN_WARD), "Hoglin Ward at 5 hoglins");
        room(d);
        BlockPos field = LifeTests.H.offset(-3, 0, -3);
        BlockState ripe = ((CropBlock) Blocks.WHEAT).getStateForAge(CropBlock.MAX_AGE), young = ((CropBlock) Blocks.WHEAT).getStateForAge(2);
        for (int i = 0; i < 64; i++) NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(t.getLevel(), t.absolutePos(field), young, p));
        t.assertTrue(!d.life.progress.containsKey(Trait.HOMES_GARDENS.id), "a crop not yet grown counts nothing");
        for (int i = 0; i < 64; i++) NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(t.getLevel(), t.absolutePos(field), ripe, p));
        t.assertTrue(has(d, Trait.HOMES_GARDENS), "Homes & Gardens at 64 grown crops");
        room(d); as(d, Astral.DEEP);
        t.setBlock(field, Blocks.TORCH);
        for (int i = 0; i < 64; i++)
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(t.getLevel().dimension(), t.getLevel(), t.absolutePos(field)), Blocks.STONE.defaultBlockState(), p));
        t.assertTrue(has(d, Trait.INVERSE_LUMINANCE), "Inverse Luminance at 64 torches");
        for (int i = 0; i < 5; i++) NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(p, false, true));
        t.assertTrue(!d.life.progress.containsKey(Trait.HIBERNATION.id), "leaving the bed early counts nothing");
        for (int i = 0; i < 5; i++) NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(p, false, false));
        t.assertTrue(has(d, Trait.HIBERNATION), "Hibernation Plan at 5 nights slept through");
        room(d); as(d, Astral.END);
        p.setHealth(20);
        for (int i = 0; i < 10; i++) { Husk z = husk(t, 3, -3); z.hurt(z.damageSources().playerAttack(p), 1000); }
        t.assertTrue(!d.life.progress.containsKey(Trait.DRACONIC_VAMPIRISM.id), "kills at full health count nothing");
        p.setHealth(6);
        for (int i = 0; i < 10; i++) { Husk z = husk(t, 3, -3); z.hurt(z.damageSources().playerAttack(p), 1000); }
        p.setHealth(20);
        t.assertTrue(has(d, Trait.DRACONIC_VAMPIRISM), "Draconic Vampirism at 10 kills at 3 hearts or less");
        var bullet = EntityType.SHULKER_BULLET.create(t.getLevel());
        for (int i = 0; i < 10; i++) NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(bullet, new EntityHitResult(p)));
        t.assertTrue(has(d, Trait.WINGS_OF_HOPE), "Wings of Hope at 10 shulker bullets");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 400)
    public static void predatorsEyeAndLeapOfFaithAreLearned(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.LUNAR);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS));
        Husk[] seen = new Husk[10];
        for (int i = 0; i < 10; i++) seen[i] = husk(t, -4 + i % 5 * 2, i < 5 ? -5 : 5);
        p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, seen[0].getEyePosition());
        p.tickCount = 5; RoamTraits.tick(p);
        t.assertTrue(!d.life.progress.containsKey(Trait.PREDATORS_EYE.id), "a look without the spyglass counts nothing");
        p.startUsingItem(InteractionHand.MAIN_HAND);
        for (int round = 0; round < 2; round++) for (Husk z : seen) {  /* each mob counts once, however often it is looked at */
            p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, z.getEyePosition());
            p.tickCount += 5; RoamTraits.tick(p);
            if (round == 0 && z == seen[8]) t.assertFalse(has(d, Trait.PREDATORS_EYE), "not at nine");
        }
        p.stopUsingItem();
        t.assertTrue(has(d, Trait.PREDATORS_EYE), "Predator's Eye at 10 different hostile mobs in the spyglass (progress " + d.life.progress.get(Trait.PREDATORS_EYE.id) + ")");
        room(d); as(d, Astral.AERO);
        /* a long fall that ends without fall damage, one time in four: tried until learned */
        for (int i = 0; i < 80; i++) {
            t.runAfterDelay(i * 4L + 1, () -> { if (!has(d, Trait.LEAP_OF_FAITH)) { p.fallDistance = 35; RoamTraits.tick(p); p.fallDistance = 0; RoamTraits.tick(p); } });
            t.runAfterDelay(i * 4L + 4, () -> RoamTraits.tick(p));
        }
        t.succeedWhen(() -> t.assertTrue(has(d, Trait.LEAP_OF_FAITH), "Leap of Faith from a 35 block fall without fall damage"));
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void theOwnersEffectsWork(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        second(p);
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED); var swim = p.getAttribute(NeoForgeMod.SWIM_SPEED); var air = p.getAttribute(Attributes.OXYGEN_BONUS);
        double walk = speed.getValue(), stroke = swim.getValue();
        t.assertTrue(!PowderSnowBlock.canEntityWalkOnPowderSnow(p) && RoamTraits.exhaustion(p, 1) == 1 && air.getValue() == 0, "before: none of it");
        give(d, Trait.OPEN_ROAD); give(d, Trait.DOWNSTREAM); give(d, Trait.FROST_STRIDER); give(d, Trait.HIBERNATION);
        second(p);
        t.assertTrue(Math.abs(speed.getValue() - walk * 1.1) < 1e-6, "The Open Road: 10% faster (" + walk + " to " + speed.getValue() + ")");
        t.assertTrue(Math.abs(swim.getValue() - stroke * 1.25) < 1e-6 && air.getValue() == 0.25, "Downstream: swims 25% faster, air lasts a quarter longer");
        t.assertTrue(PowderSnowBlock.canEntityWalkOnPowderSnow(p), "Frost Strider: its owner walks on powder snow");
        t.assertTrue(Math.abs(RoamTraits.exhaustion(p, 1) - 0.67f) < 1e-6, "Hibernation Plan: a third less hunger");
        room(d);
        second(p);
        t.assertTrue(speed.getValue() == walk && swim.getValue() == stroke && air.getValue() == 0 && !PowderSnowBlock.canEntityWalkOnPowderSnow(p), "without the traits: as before");
        /* Draconic Vampirism: a tenth back, the owner's and the drekki's, 2 health a second at most */
        give(d, Trait.DRACONIC_VAMPIRISM);
        Husk z = husk(t, -3, 3);
        p.setHealth(10);
        z.hurt(z.damageSources().playerAttack(p), 10);
        float once = p.getHealth() - 10;
        t.assertTrue(once > 0.7f && once <= 1.01f, "a hit of 10 heals its owner about 1 (" + once + ")");
        for (int i = 0; i < 6; i++) { z.setHealth(20); z.invulnerableTime = 0; z.hurt(z.damageSources().playerAttack(p), 10); }
        t.assertTrue(p.getHealth() <= 12.01f, "2 health a second at most (" + p.getHealth() + ")");
        d.setHealth(20); z.setHealth(20); z.invulnerableTime = 0;
        BardingEnchants.strike(d, z, d.damageSources().mobAttack(d), 10, true);
        t.assertTrue(d.getHealth() > 20.7f, "and its own bite heals the drekki (" + d.getHealth() + ")");
        p.setHealth(20);
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void cropsCreepersAndHoglins(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        d.setNoAi(true);
        give(d, Trait.HOMES_GARDENS); give(d, Trait.NATURAL_REPELLANT); give(d, Trait.HOGLIN_WARD);
        CropBlock wheat = (CropBlock) Blocks.WHEAT;
        BlockPos near = LifeTests.H.offset(3, 0, 3), far = t.relativePos(t.absolutePos(LifeTests.H).offset(26, 0, 0));
        t.setBlock(near.below(), Blocks.FARMLAND); t.setBlock(far.below(), Blocks.FARMLAND);
        int extra = 0, extraFar = 0;
        for (int i = 0; i < 200; i++) {
            t.setBlock(near, wheat.getStateForAge(1)); t.setBlock(far, wheat.getStateForAge(1));
            NeoForge.EVENT_BUS.post(new CropGrowEvent.Post(t.getLevel(), t.absolutePos(near), wheat.getStateForAge(0), wheat.getStateForAge(1)));
            NeoForge.EVENT_BUS.post(new CropGrowEvent.Post(t.getLevel(), t.absolutePos(far), wheat.getStateForAge(0), wheat.getStateForAge(1)));
            if (wheat.getAge(t.getBlockState(near)) == 2) extra++;
            if (wheat.getAge(t.getBlockState(far)) == 2) extraFar++;
        }
        t.assertTrue(extra > 25 && extra < 80 && extraFar == 0, "Homes & Gardens: one growth in four gets a second step within 20 blocks (" + extra + " of 200), none beyond (" + extraFar + ")");
        var creeper = EntityType.CREEPER.create(t.getLevel()); creeper.moveTo(d.getX() + 2, d.getY(), d.getZ()); creeper.setInvulnerable(true); t.getLevel().addFreshEntity(creeper);
        var hoglin = EntityType.HOGLIN.create(t.getLevel()); hoglin.moveTo(d.getX() - 3, d.getY(), d.getZ()); hoglin.setInvulnerable(true); t.getLevel().addFreshEntity(hoglin);
        hoglin.setImmuneToZombification(true);
        double start = creeper.distanceTo(d);
        t.runAfterDelay(80, () -> {
            t.assertTrue(creeper.distanceTo(d) > start + 1.5, "Natural Repellant: the creeper moved away (" + start + " to " + creeper.distanceTo(d) + ")");
            t.assertTrue(hoglin.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT), "Hoglin Ward: the hoglin takes the drekki for warped fungus");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void inverseLuminanceLightsTheDark(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.DEEP);
        d.setNoAi(true);
        give(d, Trait.INVERSE_LUMINANCE);
        /* a sealed stone cell: no daylight inside */
        BlockPos cell = LifeTests.H.offset(-4, 0, 3);
        for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 3; dy++) for (int dz = -1; dz <= 1; dz++)
            t.setBlock(cell.offset(dx, dy, dz), dx == 0 && dz == 0 && dy < 3 ? Blocks.AIR : Blocks.STONE);
        var inside = t.absoluteVec(cell.getCenter()).add(0, -0.5, 0);
        d.moveTo(inside);
        t.runAfterDelay(20, () -> {
            BlockPos head = BlockPos.containing(d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ());
            BlockState lamp = t.getLevel().getBlockState(head);
            t.assertTrue(lamp.is(Blocks.LIGHT) && lamp.getValue(LightBlock.LEVEL) == 14, "in the dark it carries a light of 14 (found " + lamp + ")");
            d.moveTo(t.absoluteVec(LifeTests.H.offset(4, 0, -4).getCenter()).add(0, -0.5, 0));  /* out under the open sky, by day */
            t.runAfterDelay(20, () -> {
                t.assertTrue(t.getLevel().getBlockState(head).isAir(), "the light it left behind is gone");
                BlockPos out = BlockPos.containing(d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ());
                t.assertTrue(!t.getLevel().getBlockState(out).is(Blocks.LIGHT), "in full daylight it gives none");
                d.moveTo(inside);
                t.runAfterDelay(20, () -> {
                    room(d);
                    t.runAfterDelay(15, () -> {
                        t.assertTrue(t.getLevel().getBlockState(head).isAir(), "without the trait the light goes out");
                        t.succeed();
                    });
                });
            });
        });
    }
}
