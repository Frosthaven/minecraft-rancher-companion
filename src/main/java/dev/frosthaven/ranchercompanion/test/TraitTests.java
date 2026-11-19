package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.trait.Effects;
import dev.frosthaven.ranchercompanion.trait.Trait;
import dev.frosthaven.ranchercompanion.trait.Traits;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerDestroyItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSpawnPhantomsEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A smoke test of every activity trait (spec, Growth release: Tests): each is learned from its activity by a checked-out
 * pup watching its owner, and each that works now does what the sheet says. Activities come from the game itself where
 * a test can cause them (kills, damage, effects, XP, fire, freezing, air), else from the event the game would fire.
 * One-off activities teach at a chance, so they repeat until learned.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class TraitTests {
    private TraitTests() {}

    /** a checked-out pup beside its owner, ready to learn */
    static DrekkiEntity pup(GameTestHelper t, ServerPlayer p) {
        return CombatTests.out(t, p, 1, Diet.SEED, Life.Stance.REACTIVE);
    }

    /** learned, its owner saying yes to the offer first when it is the one waiting */
    static boolean has(DrekkiEntity d, Trait tr) {
        if (tr.id.equals(d.life.offer)) Traits.answer(d, true);
        return d.life.traits.contains(tr.id);
    }

    /** gives it a trait outright (syncLife: the trait index sees it) */
    static void give(DrekkiEntity d, Trait tr) { d.life.traits.add(tr.id); d.syncLife(); }

    /** the mock player's base tick (fire, freezing, air, water) and the player tick handlers: a real client's connection
     *  drives the player tick, which a mock player lacks */
    static void ticks(GameTestHelper t, ServerPlayer p) {
        for (int i = 1; i < 300; i++) t.runAfterDelay(i, () -> {
            if (p.isRemoved()) return;
            p.baseTick();
            dev.frosthaven.ranchercompanion.trait.Learning.ongoing(p);  /* the game asks every other tick; the test every tick */
            Effects.tick(p);
        });
    }

    /** a newly joined player cannot be hurt for 3 seconds */
    static final int SPAWN_GUARD = 65;

    /** repeats an activity until the trait is learned, at most n times */
    static void until(GameTestHelper t, DrekkiEntity d, Trait tr, int n, Runnable act) {
        for (int i = 0; i < n && !has(d, tr); i++) act.run();
        t.assertTrue(has(d, tr), tr.id + " learned (progress " + d.life.progress.get(tr.id) + ")");
    }

    /** a state that starts and stops (on fire, frozen, out of air): on for 3 ticks, off for 3, until learned */
    static void cycle(GameTestHelper t, ServerPlayer p, DrekkiEntity d, Trait tr, Runnable on, Runnable off) {
        ticks(t, p);
        /* the scheduler keys tasks by object: a fresh wrapper each time, or later rounds replace earlier ones */
        for (int i = 0; i < 40; i++) { t.runAfterDelay(i * 6L + 1, () -> on.run()); t.runAfterDelay(i * 6L + 4, () -> off.run()); }
        t.succeedWhen(() -> t.assertTrue(has(d, tr), tr.id + " learned"));
    }

    // ---- learning ----

    @GameTest(template = "floor", timeoutTicks = 160)
    public static void learnsCountedActivities(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        FishingHook hook = new FishingHook(p, t.getLevel(), 0, 0);
        for (int i = 0; i < 4; i++) NeoForge.EVENT_BUS.post(new ItemFishedEvent(List.of(new ItemStack(Items.COD)), 1, hook));
        t.assertTrue(!has(d, Trait.HOOK_LINE_SINKER) && d.life.progress.get(Trait.HOOK_LINE_SINKER.id) == 4, "4 of 5 catches counted");
        NeoForge.EVENT_BUS.post(new ItemFishedEvent(List.of(new ItemStack(Items.COD)), 1, hook));
        t.assertTrue(has(d, Trait.HOOK_LINE_SINKER), "Hook, Line & Sinker at 5 catches");
        p.giveExperienceLevels(3);
        t.assertTrue(has(d, Trait.TRAINING_DAY), "Training Day at 3 levels");
        Villager v = t.spawn(EntityType.VILLAGER, LifeTests.H.offset(-3, 0, 3));
        for (int i = 0; i < 5; i++) {
            MerchantOffer o = new MerchantOffer(new ItemCost(Items.EMERALD, 2), new ItemStack(Items.BREAD), 12, 1, 0.05f);
            NeoForge.EVENT_BUS.post(new TradeWithVillagerEvent(p, o, v));
        }
        /* the cap: three traits at stage 1, so the third counted trait needs a free slot first */
        t.assertTrue(has(d, Trait.CREATURE_COMFORTS) && d.life.traits.size() == 3, "Creature Comforts at 5 trades without a discount");
        d.life.traits.clear(); d.syncLife();
        for (int i = 0; i < 5; i++) { Ghast g = t.spawn(EntityType.GHAST, LifeTests.H.offset(0, 6, 0)); g.hurt(g.damageSources().playerAttack(p), 1000); }
        t.assertTrue(has(d, Trait.HIDE_SEEK), "Hide & Seek at 5 ghasts");
        for (int i = 0; i < 5; i++) { Phantom ph = t.spawn(EntityType.PHANTOM, LifeTests.H.offset(0, 6, 0)); ph.hurt(ph.damageSources().playerAttack(p), 1000); }
        t.assertTrue(has(d, Trait.NIGHT_WATCH), "Night Watch at 5 phantoms");
        d.life.traits.clear(); d.syncLife();
        t.setBlock(LifeTests.H.offset(2, 0, 2), Blocks.SLIME_BLOCK);
        p.moveTo(t.absoluteVec(LifeTests.H.offset(2, 1, 2).getCenter()).add(0, -0.5, 0));
        NeoForge.EVENT_BUS.post(new LivingFallEvent(p, 3, 0));
        NeoForge.EVENT_BUS.post(new LivingFallEvent(p, 3, 0));  /* one landing, seen twice: counted once */
        t.assertTrue(d.life.progress.getOrDefault(Trait.CLOUD_STOMPERS.id, 0) == 1, "one slime landing counts once");
        for (int i = 1; i < 10; i++) t.runAfterDelay(i * 10, () -> NeoForge.EVENT_BUS.post(new LivingFallEvent(p, 3, 0)));
        t.runAfterDelay(100, () -> { t.assertTrue(has(d, Trait.CLOUD_STOMPERS), "Cloud Stompers at 10 slime bounces"); t.succeed(); });
    }

    /** a Fledgling learns on past a pup's three, to its own cap of six, and learns Hearty from heals while hurt */
    @GameTest(template = "floor", timeoutTicks = 120)
    public static void fledglingLearnsOnAndHearty(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.SEED, Life.Stance.PASSIVE);
        give(d, Trait.PATCHWORK); give(d, Trait.STAMP_OUT); give(d, Trait.HUDDLE_UP);
        p.giveExperienceLevels(3);
        t.assertTrue(has(d, Trait.TRAINING_DAY), "a Fledgling learns a fourth trait");
        for (int i = 0; i < 5; i++) {
            d.setHealth(d.getMaxHealth() - 4);
            d.life.cooldowns.remove("hearty_heal");  /* one heal counts per 30 s */
            d.heal(2);
        }
        t.assertTrue(has(d, Trait.HEARTY), "Hearty at 5 heals while hurt");
        give(d, Trait.UNDERTOW);
        int before = d.life.traits.size();
        for (int i = 0; i < 5; i++) { Phantom ph = t.spawn(EntityType.PHANTOM, LifeTests.H.offset(0, 6, 0)); ph.hurt(ph.damageSources().playerAttack(p), 1000); }
        t.assertTrue(before == 6 && d.life.traits.size() == 6 && d.life.offer == null && !has(d, Trait.NIGHT_WATCH), "a Fledgling's cap is six, and at it nothing is offered");
        t.succeed();
    }

    /** a learned trait is an offer first: a new one replaces it (its count stays full, so it comes back), a pass sets
     *  the count back to 0, a yes learns it */
    @GameTest(template = "floor", timeoutTicks = 60)
    public static void offersWaitForTheOwner(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        FishingHook hook = new FishingHook(p, t.getLevel(), 0, 0);
        for (int i = 0; i < 5; i++) NeoForge.EVENT_BUS.post(new ItemFishedEvent(List.of(new ItemStack(Items.COD)), 1, hook));
        t.assertTrue(Trait.HOOK_LINE_SINKER.id.equals(d.life.offer) && d.life.traits.isEmpty(), "5 catches offer Hook, Line & Sinker, not learned yet");
        p.giveExperienceLevels(3);
        t.assertTrue(Trait.TRAINING_DAY.id.equals(d.life.offer) && d.life.progress.get(Trait.HOOK_LINE_SINKER.id) == 5, "a new offer replaces the old; the old count stays full");
        Traits.answer(d, false);
        t.assertTrue(d.life.offer == null && !d.life.progress.containsKey(Trait.TRAINING_DAY.id) && d.life.traits.isEmpty(), "a pass learns nothing and sets the count to 0");
        NeoForge.EVENT_BUS.post(new ItemFishedEvent(List.of(new ItemStack(Items.COD)), 1, hook));
        t.assertTrue(Trait.HOOK_LINE_SINKER.id.equals(d.life.offer), "the replaced offer comes back on its next count");
        Traits.answer(d, true);
        t.assertTrue(d.life.offer == null && d.life.traits.contains(Trait.HOOK_LINE_SINKER.id), "a yes learns it");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 120)
    public static void learnsDeathActivitiesAndTheCapHolds(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        Piglin piglin = t.spawn(EntityType.PIGLIN, LifeTests.H.offset(-3, 0, -3));
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(p, p.damageSources().mobAttack(piglin)));
        t.assertFalse(has(d, Trait.FALLEN_EMPEROR), "dying to a piglin teaches nothing");
        /* 10 kills: piglins, brutes and zombified piglins all count */
        for (int i = 0; i < 10; i++) {
            var type = i < 4 ? EntityType.PIGLIN : i < 7 ? EntityType.PIGLIN_BRUTE : EntityType.ZOMBIFIED_PIGLIN;
            var m = t.spawn(type, LifeTests.H.offset(-3, 0, -3));
            if (i == 9) t.assertFalse(has(d, Trait.FALLEN_EMPEROR), "not before the tenth kill");
            m.hurt(m.damageSources().playerAttack(p), 1000);
        }
        t.assertTrue(has(d, Trait.FALLEN_EMPEROR), "The Fallen Emperor at 10 piglin kills");
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        t.runAfterDelay(SPAWN_GUARD, () -> {
            p.hurt(p.damageSources().generic(), 1000);
            t.assertTrue(has(d, Trait.PHOENIX_HEART) && p.isAlive(), "Phoenix Heart from a totem");
            give(d, Trait.PATCHWORK);
            int before = d.life.traits.size();
            p.giveExperienceLevels(3);
            t.assertTrue(d.life.traits.size() == before && !has(d, Trait.TRAINING_DAY), "at the cap it learns nothing");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 120)
    public static void learnsOneOffsFromEffectsItemsAndFalls(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        until(t, d, Trait.BALM_SALVE, 80, () -> { p.addEffect(new MobEffectInstance(MobEffects.POISON, 200)); p.removeEffectsCuredBy(EffectCures.MILK); dev.frosthaven.ranchercompanion.trait.Learning.forget(p.getUUID()); });
        /* using up a stack is not a break: the game's item-destroyed event for it teaches nothing */
        for (int i = 0; i < 20; i++) NeoForge.EVENT_BUS.post(new PlayerDestroyItemEvent(p, new ItemStack(Items.DIRT), InteractionHand.MAIN_HAND));
        t.assertFalse(has(d, Trait.PATCHWORK), "Patchwork: a stack used up teaches nothing");
        until(t, d, Trait.PATCHWORK, 80, () -> {  /* an item worn through */
            ItemStack pick = new ItemStack(Items.WOODEN_PICKAXE);
            pick.setDamageValue(pick.getMaxDamage() - 1);
            pick.hurtAndBreak(1, t.getLevel(), p, item -> {});
        });
        t.runAfterDelay(SPAWN_GUARD, () -> {
            until(t, d, Trait.SOFT_LANDING, 80, () -> { p.invulnerableTime = 0; p.setHealth(20); p.hurt(p.damageSources().fall(), 1); });
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 300)
    public static void learnsStampOutFromCatchingFire(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        cycle(t, p, d, Trait.STAMP_OUT, () -> p.setRemainingFireTicks(40), () -> p.clearFire());
    }

    @GameTest(template = "floor", timeoutTicks = 300)
    public static void learnsHuddleUpFromFreezing(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        cycle(t, p, d, Trait.HUDDLE_UP, () -> p.setTicksFrozen(p.getTicksRequiredToFreeze() + 20), () -> p.setTicksFrozen(0));
    }

    @GameTest(template = "floor", timeoutTicks = 300)
    public static void learnsUndertowFromTheLastBubble(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        BlockPos w = LifeTests.H.offset(3, 0, 3);
        for (int y = 0; y < 3; y++) t.setBlock(w.above(y), Blocks.WATER);
        cycle(t, p, d, Trait.UNDERTOW, () -> { p.moveTo(t.absoluteVec(w.getCenter()).add(0, -0.4, 0)); p.setAirSupply(10); }, () -> p.setAirSupply(300));
    }

    // ---- effects ----

    /** a drekki holding the trait beside the player (checked out, so traits work) */
    static DrekkiEntity holding(GameTestHelper t, ServerPlayer p, Trait tr) {
        DrekkiEntity d = pup(t, p);
        d.life.traits.add(tr.id);
        d.syncLife();
        return d;
    }

    @GameTest(template = "floor")
    public static void effectsThatChangeVanillaRules(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = pup(t, p);
        t.assertFalse(PiglinAi.isWearingGold(p), "no gold, no trait");
        give(d, Trait.FALLEN_EMPEROR);
        t.assertTrue(PiglinAi.isWearingGold(p), "The Fallen Emperor: piglins see gold");
        Villager v = t.spawn(EntityType.VILLAGER, LifeTests.H.offset(-2, 0, 2));
        int rep = v.getPlayerReputation(p);
        give(d, Trait.CREATURE_COMFORTS);
        t.assertTrue(v.getPlayerReputation(p) == rep + 75, "Creature Comforts: +75 reputation");
        d.life.traits.clear(); d.syncLife();
        give(d, Trait.PATCHWORK);
        ItemStack pick = new ItemStack(Items.IRON_PICKAXE);
        pick.hurtAndBreak(pick.getMaxDamage() + 5, t.getLevel(), p, i -> {});
        t.assertTrue(!pick.isEmpty() && pick.getMaxDamage() - pick.getDamageValue() == pick.getMaxDamage() / 4, "Patchwork: back to 25%, " + pick.getDamageValue());
        pick.hurtAndBreak(pick.getMaxDamage() + 5, t.getLevel(), p, i -> {});
        t.assertTrue(pick.isEmpty(), "Patchwork: once in 7 days, the next break breaks it");
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void effectsOnEffectsTargetsAndSpawns(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = holding(t, p, Trait.BALM_SALVE);
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        t.assertFalse(p.hasEffect(MobEffects.POISON), "Balm & Salve: poison does not land");
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 200));
        t.assertTrue(p.hasEffect(MobEffects.MOVEMENT_SPEED), "Balm & Salve: beneficial effects stay");
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 200));
        t.assertTrue(p.hasEffect(MobEffects.POISON), "Balm & Salve: 15 seconds between uses");
        give(d, Trait.HIDE_SEEK);
        Ghast g = t.spawn(EntityType.GHAST, LifeTests.H.offset(0, 6, 0));
        g.setTarget(p);
        t.assertTrue(g.getTarget() == null, "Hide & Seek: the ghast cannot target its player");
        d.life.traits.clear(); d.syncLife();
        give(d, Trait.NIGHT_WATCH);
        var ev = NeoForge.EVENT_BUS.post(new PlayerSpawnPhantomsEvent(p, 3));
        t.assertTrue(ev.getResult() == PlayerSpawnPhantomsEvent.Result.DENY, "Night Watch: no phantoms");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void effectsOnFireFreezingAndFalls(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = holding(t, p, Trait.STAMP_OUT);
        give(d, Trait.HUDDLE_UP);
        give(d, Trait.SOFT_LANDING);
        ticks(t, p);
        p.setRemainingFireTicks(100);
        t.runAfterDelay(3, () -> {
            t.assertTrue(!p.isOnFire() && p.hasEffect(MobEffects.FIRE_RESISTANCE), "Stamp Out: put out, Fire Resistance");
            t.setBlock(LifeTests.H.offset(2, 1, 2), Blocks.POWDER_SNOW);
            p.moveTo(t.absoluteVec(LifeTests.H.offset(2, 1, 2).getCenter()));
            p.setTicksFrozen(40);
        });
        t.runAfterDelay(8, () -> {
            t.assertTrue(p.getTicksFrozen() == 0, "Huddle Up: thawed and kept from freezing, " + p.getTicksFrozen());
            t.setBlock(LifeTests.H.offset(2, 1, 2), Blocks.AIR);
            p.moveTo(t.absoluteVec(LifeTests.H.offset(2, 8, 2).getCenter()));
            p.fallDistance = 7;
            p.setOnGround(false);
        });
        t.runAfterDelay(10, () -> {
            t.assertTrue(p.hasEffect(MobEffects.SLOW_FALLING), "Soft Landing: Slow Falling after 6 blocks");
            t.succeed();
        });
    }

    /** Soft Landing at the landing: a fall over 6 blocks the tick missed lands with no damage; a short one is left alone */
    @GameTest(template = "floor")
    public static void softLandingCatchesTheLanding(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        holding(t, p, Trait.SOFT_LANDING);
        LivingFallEvent low = new LivingFallEvent(p, 5, 1);
        NeoForge.EVENT_BUS.post(low);
        t.assertTrue(low.getDamageMultiplier() == 1, "Soft Landing: 5 blocks is left alone");
        ServerPlayer other = LifeTests.player(t);  /* another player beside the drekki: its traits help only its owner */
        LivingFallEvent theirs = new LivingFallEvent(other, 9, 1);
        NeoForge.EVENT_BUS.post(theirs);
        t.assertTrue(theirs.getDamageMultiplier() == 1, "Soft Landing: another player is not helped");
        LivingFallEvent high = new LivingFallEvent(p, 9, 1);
        NeoForge.EVENT_BUS.post(high);
        t.assertTrue(high.getDamageMultiplier() == 0, "Soft Landing: 9 blocks lands softly");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 160)
    public static void undertowLiftsToOpenWater(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        holding(t, p, Trait.UNDERTOW);
        BlockPos w = LifeTests.H.offset(3, 0, 3);
        for (int y = 0; y < 3; y++) t.setBlock(w.above(y), Blocks.WATER);
        p.moveTo(t.absoluteVec(w.getCenter()).add(0, -0.4, 0));
        p.setAirSupply(40);
        ticks(t, p);
        /* low on air, not yet drowning: nothing; after the spawn guard, out of air, the first drowning damage starts it */
        t.runAfterDelay(20, () -> t.assertFalse(p.hasEffect(MobEffects.WATER_BREATHING), "Undertow: nothing before drowning damage"));
        t.runAfterDelay(SPAWN_GUARD, () -> { p.setAirSupply(0); p.invulnerableTime = 0; });
        t.runAfterDelay(SPAWN_GUARD + 1, () -> t.succeedWhen(() -> t.assertTrue(p.getHealth() < 20 && p.hasEffect(MobEffects.WATER_BREATHING) && p.getDeltaMovement().y > 0.3,
            "Undertow: after drowning damage, lifted with Water Breathing: " + p.getHealth() + ", " + p.hasEffect(MobEffects.WATER_BREATHING) + ", " + p.getDeltaMovement().y)));
    }

    @GameTest(template = "floor")
    public static void effectsOnTheDrekkiAndFishing(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = holding(t, p, Trait.HEARTY);
        d.applyStageHealth(false);
        t.assertTrue(d.getMaxHealth() == 16, "Hearty: +3 hearts on a pup, " + d.getMaxHealth());
        give(d, Trait.TRAINING_DAY);
        d.life.cooldowns.put(Trait.TRAINING_DAY.id, Vitals.now(d));
        Effects.trainingDay(d);
        var orbs = t.getLevel().getEntitiesOfClass(ExperienceOrb.class, new AABB(d.blockPosition()).inflate(3));
        t.assertTrue(orbs.stream().mapToInt(ExperienceOrb::getValue).sum() == 18, "Training Day: 18 XP");
        give(d, Trait.HOOK_LINE_SINKER);
        FishingHook hook = new FishingHook(p, t.getLevel(), 0, 0);
        var a = (dev.frosthaven.ranchercompanion.mixin.FishingHookAccess) hook;
        a.rancher$setTimeUntilLured(1000);
        for (int i = 0; i < 200; i++) Effects.hook(hook);
        int saved = 1000 - a.rancher$timeUntilLured();
        t.assertTrue(saved > 60 && saved < 140, "Hook, Line & Sinker: about one extra tick in two, " + saved + " in 200");
        t.succeed();
    }
}
