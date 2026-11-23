package dev.frosthaven.ranchercompanion.test;

import static dev.frosthaven.ranchercompanion.test.TraitTests.as;
import static dev.frosthaven.ranchercompanion.test.TraitTests.give;
import static dev.frosthaven.ranchercompanion.test.TraitTests.has;
import static dev.frosthaven.ranchercompanion.test.TraitTests.until;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.entity.GoHere;
import dev.frosthaven.ranchercompanion.item.Barding;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.trait.FightTraits;
import dev.frosthaven.ranchercompanion.trait.Trait;
import dev.frosthaven.ranchercompanion.trait.WorldTraits;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The traits and rules of 0.5.0 (spec, Traits): each new trait learned from its activity and doing what the sheet says,
 * the void, and a drekki saved under the 3/6/9 caps. Rewind's learning (an ender pearl thrown while falling over the
 * void) needs a void under the player, which a test floor does not have: that one is checked in the real game.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class NewTraitTests {
    private NewTraitTests() {}

    private static DrekkiEntity fledgling(GameTestHelper t, ServerPlayer p, int astral) {
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.SEED, Life.Stance.REACTIVE);
        as(d, astral);
        return d;
    }

    private static Husk husk(GameTestHelper t, int dx, int dz) { return CombatTests.zombie(t, LifeTests.H.offset(dx, 0, dz)); }

    /** the drekki's bite landing on a mob, as its attack goal deals it */
    private static void bite(DrekkiEntity d, Husk z) { z.invulnerableTime = 0; z.hurt(d.damageSources().mobAttack(d), 1); }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void provokeIsLearnedFromWhistleAttacksAndHoldsTheMob(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        Husk first = husk(t, -3, 3);
        bite(d, first);
        t.assertTrue(!d.life.progress.containsKey(Trait.PROVOKE.id), "a bite in a fight it picked itself counts nothing");
        GoHere.attack(d, first);
        bite(d, first); bite(d, first);
        t.assertTrue(d.life.progress.get(Trait.PROVOKE.id) == 1, "two hits on one ordered mob count once");
        for (int i = 0; i < 4; i++) { Husk z = husk(t, -3, -3); GoHere.attack(d, z); bite(d, z); }
        t.assertTrue(has(d, Trait.PROVOKE), "Provoke at 5 different mobs hit under an attack order");
        Husk held = husk(t, 3, -3);
        GoHere.attack(d, held);
        bite(d, held);
        t.assertTrue(held.getTarget() == d && d.life.cooldowns.get(Trait.PROVOKE.id) == Vitals.now(d) + Trait.PROVOKE.cooldown, "the ordered mob is on the drekki; the cooldown runs from the start");
        held.setTarget(p);
        t.assertTrue(held.getTarget() == d, "a held mob takes no other target");
        d.disengage();  /* Stay, Passive or a new Go here: the order is off */
        t.runAfterDelay(25, () -> {
            held.setTarget(p);
            t.assertTrue(held.getTarget() == p, "the hold ends with the order");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void fadeIsLearnedAtHalfHealthAndTurnsMobsAway(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.LUNAR);
        Husk z = husk(t, -3, 3);
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().mobAttack(z), 1);
        t.assertTrue(!has(d, Trait.FADE), "a hit above half health teaches nothing");
        until(t, d, Trait.FADE, 80, () -> { d.setHealth(d.getMaxHealth() / 2); d.invulnerableTime = 0; d.hurt(d.damageSources().mobAttack(z), 1); });
        d.setHealth(d.getMaxHealth());
        /* a test's mock player always counts as creative, so its owner is no one for a mob to fight */
        Husk alone = husk(t, 3, -3);
        alone.setTarget(d);
        t.assertTrue(alone.getTarget() == d && !FightTraits.faded(d) && !d.life.cooldowns.containsKey(Trait.FADE.id), "with only the drekki in the fight it is not spent");
        var golem = t.spawn(EntityType.IRON_GOLEM, LifeTests.H.offset(4, 0, 0));
        golem.setNoAi(true);
        Husk turned = husk(t, 3, 3);
        turned.setLastHurtByMob(golem);  /* someone else hit this one a moment ago */
        turned.setTarget(d);
        t.assertTrue(turned.getTarget() == golem && FightTraits.faded(d) && d.life.cooldowns.get(Trait.FADE.id) == Vitals.now(d) + Trait.FADE.cooldown,
            "with someone else in the fight the mob turns to them, and no mob targets the drekki (target " + turned.getTarget() + ")");
        Husk later = husk(t, -3, -3);
        later.setTarget(d);
        t.assertTrue(later.getTarget() != d, "nor does another mob while it lasts");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void barbedCoatIsLearnedFromSpikesAndReturnsMeleeDamage(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.DEEP);
        t.runAfterDelay(TraitTests.SPAWN_GUARD, () -> {
            p.invulnerableTime = 0; p.setHealth(20);
            p.hurt(p.damageSources().generic(), 1);
            t.assertTrue(!has(d, Trait.BARBED_COAT), "other damage teaches nothing");
            until(t, d, Trait.BARBED_COAT, 80, () -> { p.invulnerableTime = 0; p.setHealth(20); p.hurt(p.damageSources().cactus(), 1); });
            Husk z = husk(t, -3, 3);
            float full = z.getHealth();
            d.invulnerableTime = 0;
            d.hurt(d.damageSources().mobAttack(z), 1);
            float small = full - z.getHealth();
            z.setHealth(full); z.invulnerableTime = 0; d.invulnerableTime = 0;
            d.hurt(d.damageSources().mobAttack(z), 16);
            float big = full - z.getHealth();
            t.assertTrue(small > 0.5f && small < 1.01f && big > 3 && big < 4.01f, "a quarter of the hit goes back, at least half a heart (got " + small + " and " + big + ")");
            z.setHealth(full); z.invulnerableTime = 0; d.invulnerableTime = 0;
            d.hurt(d.damageSources().arrow(new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, t.getLevel()), z), 8);
            t.assertTrue(z.getHealth() == full, "an arrow returns nothing");
            t.succeed();
        });
    }

    private static ItemStack wornBarding() {
        ItemStack b = GearTests.fledglingBarding(Barding.IRON);
        b.setDamageValue(b.getMaxDamage() - 1);
        return b;
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void pointBreakIsLearnedFromACreeperAndBlastsWhenBardingBreaks(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.DEEP);
        d.setBardingItem(wornBarding());
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().explosion(null, null), 10);
        t.assertTrue(d.bardingItem().isEmpty() && d.life.offer == null && d.getHealth() == d.getMaxHealth(), "barding broken by another blast takes the hit and teaches nothing");
        var creeper = t.spawn(EntityType.CREEPER, LifeTests.H.offset(-4, 0, -4));
        creeper.setNoAi(true);
        d.setBardingItem(wornBarding());
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().explosion(creeper, creeper), 10);
        t.assertTrue(has(d, Trait.POINT_BREAK), "Point Break when a creeper's blast breaks its barding");
        creeper.discard();
        BlockPos wall = LifeTests.H.offset(3, 0, 4);
        t.setBlock(wall, Blocks.DIRT);
        d.moveTo(t.absoluteVec(LifeTests.H.offset(3, 0, 3).getCenter()).add(0, -0.5, 0));
        Husk foe = husk(t, 4, 3);
        Villager friend = t.spawn(EntityType.VILLAGER, LifeTests.H.offset(2, 0, 3));
        friend.setNoAi(true);
        float foeHealth = foe.getHealth(), friendHealth = friend.getHealth(), ownHealth = d.getHealth();
        d.setBardingItem(wornBarding());
        d.invulnerableTime = 0;
        d.hurt(d.damageSources().mobAttack(foe), 5);
        t.assertTrue(d.bardingItem().isEmpty() && foe.getHealth() < foeHealth - 5, "the blast hurts the enemy beside it (" + foeHealth + " to " + foe.getHealth() + ")");
        t.assertTrue(friend.getHealth() == friendHealth && d.getHealth() == ownHealth, "it hurts neither a villager nor the drekki");
        t.assertBlockPresent(Blocks.DIRT, wall);
        float after = foe.getHealth();
        d.setBardingItem(wornBarding());
        d.invulnerableTime = 0; foe.invulnerableTime = 0;
        d.hurt(d.damageSources().mobAttack(foe), 5);
        t.assertTrue(foe.getHealth() == after, "once an hour");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void markedIsLearnedFromLongShotsAndMarksTheOrderedMob(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        Husk far = husk(t, -3, 3);
        var arrow = new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, t.getLevel());
        for (int i = 0; i < 10; i++) { far.invulnerableTime = 0; far.hurt(far.damageSources().arrow(arrow, p), 0.1f); }
        t.assertTrue(!d.life.progress.containsKey(Trait.MARKED.id), "arrow hits from close by count nothing");
        /* the owner and the drekki 35 blocks up for the moment of the shots */
        var home = p.position(); var den = d.position();
        p.moveTo(home.add(0, 35, 0)); d.moveTo(den.add(0, 35, 0));
        for (int i = 0; i < 10; i++) { far.invulnerableTime = 0; far.hurt(far.damageSources().arrow(arrow, p), 0.1f); }
        p.moveTo(home); d.moveTo(den);
        t.assertTrue(has(d, Trait.MARKED), "Marked at 10 arrow hits from 30 blocks or more");
        Husk marked = husk(t, 3, -3), plain = husk(t, -3, -3);
        GoHere.attack(d, marked);
        float before = marked.getHealth();
        marked.hurt(marked.damageSources().magic(), 4);
        plain.hurt(plain.damageSources().magic(), 4);
        float more = before - marked.getHealth(), same = before - plain.getHealth();
        t.assertTrue(Math.abs(more - same * FightTraits.MARKED) < 0.01f, "the ordered mob takes 15% more (" + more + " against " + same + ")");
        t.runAfterDelay(25, () -> {
            t.assertTrue(marked.hasEffect(MobEffects.GLOWING) && !plain.hasEffect(MobEffects.GLOWING), "and glows");
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void staringContestAndWebCutterAreLearnedFromKills(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.DEEP);
        var stared = t.spawn(EntityType.ENDERMAN, LifeTests.H.offset(-4, 0, -4));
        stared.setNoAi(true);
        t.assertTrue(!NeoForge.EVENT_BUS.post(new EnderManAngerEvent(stared, p)).isCanceled() && !WorldTraits.cutsWebs(p) && !WorldTraits.cutsWebs(d), "before: a stare angers, a web holds");
        for (int i = 0; i < 5; i++) { var e = t.spawn(EntityType.ENDERMAN, LifeTests.H.offset(-3, 0, 3)); e.hurt(e.damageSources().playerAttack(p), 1000); }
        t.assertTrue(has(d, Trait.STARING_CONTEST), "Staring Contest at 5 endermen");
        for (int i = 0; i < 10; i++) {
            net.minecraft.world.entity.monster.Spider s = i < 5 ? t.spawn(EntityType.SPIDER, LifeTests.H.offset(3, 0, -3)) : t.spawn(EntityType.CAVE_SPIDER, LifeTests.H.offset(3, 0, -3));
            s.hurt(s.damageSources().playerAttack(p), 1000);
        }
        t.assertTrue(has(d, Trait.WEB_CUTTER), "Web Cutter at 10 spiders, cave spiders too");
        t.assertTrue(NeoForge.EVENT_BUS.post(new EnderManAngerEvent(stared, p)).isCanceled(), "its owner's stare angers no enderman");
        t.assertTrue(WorldTraits.cutsWebs(p) && WorldTraits.cutsWebs(d), "a cobweb holds neither the drekki nor its owner");
        t.succeed();
    }

    private static LightningBolt bolt(GameTestHelper t, net.minecraft.world.phys.Vec3 at, boolean show) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(t.getLevel());
        b.setVisualOnly(show);
        b.moveTo(at);
        t.getLevel().addFreshEntity(b);
        return b;
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void liveWireIsLearnedFromANearStrikeAndDrawsLightning(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.AERO);
        bolt(t, p.position().add(20, 0, 0), true);
        t.assertTrue(!has(d, Trait.LIVE_WIRE) && d.life.offer == null, "a strike 20 blocks away teaches nothing");
        until(t, d, Trait.LIVE_WIRE, 80, () -> bolt(t, p.position().add(3, 0, 0), true));
        d.moveTo(t.absoluteVec(LifeTests.H.offset(4, 0, 4).getCenter()).add(0, -0.5, 0));  /* out from under the stable's roof */
        LightningBolt clear = bolt(t, d.position().add(6, 0, 0), false);
        t.assertTrue(clear.isAddedToLevel() && !d.surged(), "in clear weather it draws nothing");
        clear.discard();
        t.getLevel().setWeatherParameters(0, 600, true, true);
        t.getLevel().setRainLevel(1); t.getLevel().setThunderLevel(1);  /* the levels rise over time by themselves */
        LightningBolt drawn = bolt(t, d.position().add(6, 0, 0), false);
        t.assertTrue(!drawn.isAddedToLevel() && d.surged() && p.hasEffect(MobEffects.NIGHT_VISION), "in a thunderstorm the bolt never lands: the drekki surges and its owner sees in the dark");
        t.assertTrue(d.getHealth() == d.getMaxHealth() && !d.isOnFire(), "unharmed");
        t.getLevel().setWeatherParameters(6000, 0, false, false);
        t.getLevel().setRainLevel(0); t.getLevel().setThunderLevel(0);
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void rewindReturnsItsOwnerFromTheVoidAndTheVoidNeverKillsADrekki(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.END);
        var ground = p.position();
        int under = t.getLevel().getMinBuildHeight() - 70;
        t.runAfterDelay(TraitTests.SPAWN_GUARD, () -> {
            p.setOnGround(true);
            for (int i = 0; i < 5; i++) { p.tickCount++; WorldTraits.tick(p); }  /* a real player's tick remembers the ground */
            p.moveTo(ground.x, under, ground.z);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().fellOutOfWorld(), 4);
            t.assertTrue(p.getY() == under && p.getHealth() < p.getMaxHealth(), "without the trait the void hurts");
            p.moveTo(ground); p.setHealth(p.getMaxHealth());
            give(d, Trait.REWIND);
            p.moveTo(ground.x, under, ground.z);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().fellOutOfWorld(), 4);
            t.assertTrue(p.position().distanceTo(ground) < 1.5 && p.getHealth() == p.getMaxHealth(), "with it: no damage, and back on the last solid ground (at " + p.position() + ")");
            p.moveTo(ground.x, under, ground.z);
            p.invulnerableTime = 0;
            p.hurt(p.damageSources().fellOutOfWorld(), 4);
            t.assertTrue(p.getY() == under && p.getHealth() < p.getMaxHealth(), "once per 5 minutes");
            p.moveTo(ground); p.setHealth(p.getMaxHealth());
            /* the drekki itself, trait or not */
            d.life.traits.clear(); d.syncLife();
            d.moveTo(d.getX(), under, d.getZ());
            t.runAfterDelay(5, () -> {
                t.assertTrue(d.isAlive() && d.getY() > t.getLevel().getMinBuildHeight() && d.getHealth() == d.getMaxHealth(), "the void puts a drekki back, unhurt (at " + d.position() + ")");
                t.succeed();
            });
        });
    }

    /** a drekki saved before 0.5.0 (no "caps" mark in its Life) with this many traits, loaded now */
    private static void loadOld(DrekkiEntity d, int traits) {
        CompoundTag save = d.saveWithoutId(new CompoundTag());
        CompoundTag life = save.getCompound("Life");
        life.remove("caps"); life.remove("horizons");
        ListTag held = new ListTag();
        for (int i = 0; i < traits; i++) held.add(StringTag.valueOf(Trait.values()[i].id));
        life.put("traits", held);
        d.load(save);
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void aDrekkiFromTheOldCapsKeepsItsTraits(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = fledgling(t, p, Astral.SOLAR);
        loadOld(d, 6);
        t.assertTrue(d.life.horizons && d.life.traits.size() == 6 && d.traitView().getBoolean("horizons"), "a Fledgling with 6 keeps them and counts as having had the potion");
        t.assertTrue(d.saveWithoutId(new CompoundTag()).getCompound("Life").contains("caps"), "and is saved as settled");
        d.life.horizons = false;
        d.load(d.saveWithoutId(new CompoundTag()));
        t.assertTrue(!d.life.horizons, "settled once: a later load does not look again");
        loadOld(d, 4);
        t.assertTrue(!d.life.horizons && d.life.traits.size() == 4, "a Fledgling with 4 is left to take the potion later");
        t.succeed();
    }
}
