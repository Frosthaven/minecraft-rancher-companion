package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Fledgling combat by stance and diet, and death (spec, Growth release): the bite and the Elemental ball, Passive and
 * pups never fighting, doors (through an open one, never opening one), the Phoenix egg there and back, a ghost only gone by /kill, and a ghost's revive.
 */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class CombatTests {
    private CombatTests() {}

    /** a checked-out drekki of this stage, diet and stance beside its owner */
    static DrekkiEntity out(GameTestHelper t, ServerPlayer p, int stage, Diet diet, Life.Stance stance) {
        BlockPos h = LifeTests.stable(t);
        DrekkiEntity d = LifeTests.hatchOne(t, p, h);
        Ranch.act(p, h, Ranch.Action.CHECK_OUT, Ranch.roster(t.getLevel().getServer()).of(p.getUUID()).get(0).id);
        d.setLook(stage, d.line(), diet.ordinal(), d.astral(), d.horn());
        d.applyStageHealth(true);
        d.life.stance = stance;
        d.syncLife();
        return d;
    }

    /** a husk: a monster that does not burn by day */
    static Husk zombie(GameTestHelper t, BlockPos rel) {
        Husk z = t.spawn(EntityType.HUSK, rel);
        z.setNoAi(true);
        return z;
    }

    /** a 3-high stone wall across the floor at z 9 with an oak door in it at x 6, open or closed; the owner beyond it */
    private static void doorway(GameTestHelper t, ServerPlayer p, DrekkiEntity d, boolean open) {
        for (int x = 0; x < 13; x++) for (int y = 1; y <= 3; y++) if (x != 6 || y == 3) t.setBlock(new BlockPos(x, y, 9), Blocks.STONE);
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.NORTH)
            .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, open);
        var half = net.minecraft.world.level.block.DoorBlock.HALF;
        var lower = net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER;
        var upper = net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER;
        t.getLevel().setBlock(t.absolutePos(new BlockPos(6, 1, 9)), door.setValue(half, lower), 18);  /* 2 | 16: no shape updates between the halves */
        t.getLevel().setBlock(t.absolutePos(new BlockPos(6, 2, 9)), door.setValue(half, upper), 18);
        d.moveTo(t.absoluteVec(new net.minecraft.world.phys.Vec3(4.5, 1, 6.5)));
        p.moveTo(t.absoluteVec(new net.minecraft.world.phys.Vec3(6.5, 1, 12.5)));
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void aMatureWalksThroughAnOpenDoor(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 3, Diet.RAW, Life.Stance.PASSIVE);
        t.assertTrue(d.getBbWidth() <= 0.625f, "a Mature fits beside an open door's panel: " + d.getBbWidth());
        doorway(t, p, d, true);
        t.succeedWhen(() -> t.assertTrue(d.getZ() > t.absoluteVec(new net.minecraft.world.phys.Vec3(0, 0, 9.5)).z, "through the door: " + d.position()));
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void noDrekkiOpensAClosedDoor(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        doorway(t, p, d, false);
        t.runAfterDelay(160, () -> {
            t.assertTrue(d.getZ() < t.absoluteVec(new net.minecraft.world.phys.Vec3(0, 0, 9)).z, "still behind the closed door: " + d.position());
            t.assertFalse(t.getBlockState(new BlockPos(6, 1, 9)).getValue(net.minecraft.world.level.block.DoorBlock.OPEN), "the door stays shut");
            t.succeed();
        });
    }

    /** a husk that outlives the test's bites */
    private static Husk tough(GameTestHelper t, BlockPos rel) {
        Husk z = zombie(t, rel);
        z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);
        z.setHealth(1000);
        return z;
    }

    /** the whistle in a fight: it follows (never stays), the fight is off until 2 seconds after it is with its owner,
     *  then its stance applies again */
    @GameTest(template = "floor", timeoutTicks = 400)
    public static void theWhistleCallsItOffAFight(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.AGGRESSIVE);
        Husk z = tough(t, LifeTests.H.offset(-3, 0, 2));
        int[] phase = {0}, at = {0};
        t.onEachTick(() -> {
            if (phase[0] == 0 && d.getTarget() == z) {
                boolean stays = dev.frosthaven.ranchercompanion.ranch.Ranch.whistle(p);
                t.assertTrue(!stays && !d.isStaying() && d.getTarget() == null && d.calm() && !dev.frosthaven.ranchercompanion.combat.CombatGoals.fights(d), "called off: it follows and has no target");
                at[0] = d.tickCount; phase[0] = 1;
            } else if (phase[0] == 1) {
                if (d.calm()) t.assertTrue(d.getTarget() == null, "no target during the truce, a hostile mob 5 blocks off");
                else { t.assertTrue(d.tickCount - at[0] >= DrekkiEntity.TRUCE, "the truce lasts 2 seconds past the catch-up: " + (d.tickCount - at[0])); phase[0] = 2; }
            } else if (phase[0] == 2 && d.getTarget() == z) t.succeed();  /* Aggressive again */
        });
    }

    /** Go here in a fight: no fight for the whole trip and its 2-second hold; an attack order ends any truce */
    @GameTest(template = "floor", timeoutTicks = 500)
    public static void goHereSuspendsTheFight(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.AGGRESSIVE);
        Husk z = tough(t, LifeTests.H.offset(-3, 0, 2));
        int[] phase = {0};
        t.onEachTick(() -> {
            if (phase[0] == 0 && d.getTarget() == z) {
                dev.frosthaven.ranchercompanion.entity.GoHere.sendTo(d, t.absolutePos(LifeTests.H.offset(2, 0, 5)));
                t.assertTrue(d.getTarget() == null && d.calm(), "sent off: no target");
                phase[0] = 1;
            } else if (phase[0] == 1) {
                if (d.calm()) t.assertTrue(d.getTarget() == null, "no target on the trip or its hold");
                else phase[0] = 2;
            } else if (phase[0] == 2 && d.getTarget() == z) {  /* its stance again; now the whistle, then an order */
                dev.frosthaven.ranchercompanion.ranch.Ranch.whistle(p);
                t.assertTrue(d.calm() && d.getTarget() == null, "called off");
                dev.frosthaven.ranchercompanion.entity.GoHere.attack(d, z);
                t.assertTrue(!d.calm() && d.getTarget() == z && dev.frosthaven.ranchercompanion.combat.CombatGoals.fights(d), "an attack order is still an order to fight");
                t.succeed();
            }
        });
    }

    @GameTest(template = "floor", timeoutTicks = 300)
    public static void aggressiveFledglingBitesAMonster(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        out(t, p, 2, Diet.RAW, Life.Stance.AGGRESSIVE);
        Husk z = zombie(t, LifeTests.H.offset(-3, 0, 2));
        t.succeedWhen(() -> t.assertTrue(z.getHealth() < z.getMaxHealth(), "the zombie was bitten: " + z.getHealth()));
    }

    @GameTest(template = "floor", timeoutTicks = 300)
    public static void cookedFledglingShootsTheBall(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        out(t, p, 2, Diet.COOKED, Life.Stance.AGGRESSIVE);
        Husk z = zombie(t, LifeTests.H.offset(-4, 0, -3));
        t.succeedWhen(() -> t.assertTrue(z.getHealth() <= z.getMaxHealth() - 3, "the ball hit for 3: " + z.getHealth()));
    }

    @GameTest(template = "floor", timeoutTicks = 120)
    public static void passiveFledglingsAndPupsNeverFight(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        Husk z = zombie(t, LifeTests.H.offset(-2, 0, 2));
        z.hurt(z.damageSources().playerAttack(p), 1);  /* even when its owner attacks it */
        float after = z.getHealth();
        t.runAfterDelay(100, () -> {
            t.assertTrue(d.getTarget() == null && z.getHealth() == after, "passive: no fight, target " + d.getTarget() + ", husk " + z.getHealth() + " by " + z.getLastHurtByMob());
            d.setLook(1, d.line(), d.diet(), d.astral(), d.horn());
            d.life.stance = Life.Stance.AGGRESSIVE;
            t.runAfterDelay(15, () -> { t.assertTrue(d.getTarget() == null, "a pup never fights"); t.succeed(); });
        });
    }

    /** a village's guard or golem that turns on its owner (who hit a villager) is not fought, nor can it hurt the drekki;
     *  a monster that hits its owner still is (decided 2026-11-22: only the red health bar's hostile mobs) */
    @GameTest(template = "floor", timeoutTicks = 300)
    public static void reactiveLeavesAGolemThatHitsItsOwner(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.REACTIVE);
        var golem = t.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM, new BlockPos(8, 1, 8));
        golem.setNoAi(true);
        t.runAfterDelay(70, () -> {  /* after a new player's spawn protection */
            p.hurt(p.damageSources().mobAttack(golem), 1);
            t.assertTrue(p.getLastHurtByMob() == golem, "the golem hit its owner: " + p.getLastHurtByMob());
            float before = d.getHealth();
            d.hurt(d.damageSources().mobAttack(golem), 6);
            t.assertTrue(d.getHealth() == before, "a golem's hit does nothing: " + before + " -> " + d.getHealth());
            t.runAfterDelay(40, () -> {
                t.assertTrue(d.getTarget() != golem, "the golem that hit its owner is left alone: target " + d.getTarget());
                Husk z = zombie(t, new BlockPos(6, 1, 8));
                p.hurt(p.damageSources().mobAttack(z), 1);
                t.succeedWhen(() -> t.assertTrue(d.getTarget() == z, "a husk that hits its owner is answered: target " + d.getTarget()));
            });
        });
    }

    /** Passive ends a fight at once; off Passive it starts fresh: an old hit starts nothing, a new one does (2026-11-22) */
    @GameTest(template = "floor", timeoutTicks = 400)
    public static void passiveEndsAFightAndItStartsFresh(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.REACTIVE);
        Husk z = zombie(t, new BlockPos(6, 1, 8));
        t.runAfterDelay(70, () -> {  /* after a new player's spawn protection */
            p.hurt(p.damageSources().mobAttack(z), 1);
            t.runAfterDelay(30, () -> {
                t.assertTrue(d.getTarget() == z, "reactive: the husk that hit its owner: " + d.getTarget());
                d.setStance(Life.Stance.PASSIVE);
                t.assertTrue(d.getTarget() == null && d.getLastHurtByMob() == null, "passive: the fight ended");
                t.runAfterDelay(20, () -> {
                    d.setStance(Life.Stance.REACTIVE);
                    t.runAfterDelay(30, () -> {
                        t.assertTrue(d.getTarget() == null, "off passive, the old hit starts nothing: " + d.getTarget());
                        p.invulnerableTime = 0;
                        p.hurt(p.damageSources().mobAttack(z), 1);
                        t.succeedWhen(() -> t.assertTrue(d.getTarget() == z, "a new hit starts the fight: " + d.getTarget()));
                    });
                });
            });
        });
    }

    /** a stay command ends its fight too (2026-11-22) */
    @GameTest(template = "floor", timeoutTicks = 300)
    public static void stayEndsAFight(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.REACTIVE);
        Husk z = zombie(t, new BlockPos(6, 1, 8));
        t.runAfterDelay(70, () -> {
            p.hurt(p.damageSources().mobAttack(z), 1);
            t.runAfterDelay(30, () -> {
                t.assertTrue(d.getTarget() == z, "reactive: the husk that hit its owner: " + d.getTarget());
                Ranch.setStay(p, true);
                t.assertTrue(d.isStaying() && d.getTarget() == null, "told to stay: the fight ended");
                t.runAfterDelay(30, () -> { t.assertTrue(d.getTarget() == null, "the old hit starts nothing: " + d.getTarget()); t.succeed(); });
            });
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void aGhostOnlyGoesByKill(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        Roster r = Ranch.roster(t.getLevel().getServer());
        Roster.Entry e = r.of(p.getUUID()).get(0);
        d.life.ghost = true;
        d.syncLife();
        t.assertTrue(Vitals.now(d) > 0 && !dev.frosthaven.ranchercompanion.combat.CombatGoals.fights(d), "a ghost never fights");
        float hp = d.getHealth();
        for (var s : java.util.List.of(d.damageSources().generic(), d.damageSources().lava(), d.damageSources().fall(), d.damageSources().fellOutOfWorld(),
                d.damageSources().playerAttack(p))) { d.invulnerableTime = 0; d.hurt(s, 1000); }
        t.assertTrue(d.isAlive() && d.getHealth() == hp && r.get(e.id) != null, "nothing hurts a ghost: " + d.getHealth());
        /* fallen out of the world, it comes back */
        d.teleportTo(d.getX(), t.getLevel().getMinBuildHeight() - 70, d.getZ());
        t.runAfterDelay(5, () -> {
            t.assertTrue(d.isAlive() && d.getY() > t.getLevel().getMinBuildHeight(), "out of the void, back: y " + d.getY());
            d.kill();  /* /kill still removes it, gone for good */
            t.runAfterDelay(2, () -> { t.assertTrue(r.get(e.id) == null, "/kill deletes a ghost's record"); t.succeed(); });
        });
    }


    /** the catch-up teleport never lands it in or beside lava: with no safe spot by the owner it stays put; with Fire
     *  Resistance lava is no danger, so it may */
    @GameTest(template = "floor", timeoutTicks = 40)
    public static void teleportNeverLandsInLava(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        BlockPos at = LifeTests.H.offset(2, 0, 2);
        for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++)
            if (dx != 0 || dz != 0) t.setBlock(at.offset(dx, -1, dz), Blocks.LAVA);
        BlockPos before = d.blockPosition();  /* the lava is under it too: staying put is the other right answer */
        java.util.function.BooleanSupplier movedOntoLava = () -> !d.blockPosition().equals(before)
            && (t.getLevel().getFluidState(d.blockPosition()).is(net.minecraft.tags.FluidTags.LAVA) || t.getLevel().getFluidState(d.blockPosition().below()).is(net.minecraft.tags.FluidTags.LAVA));
        /* the wider search reaches the ground beyond the lava: it lands there or stays put, never on the lava */
        for (int i = 0; i < 40; i++) {
            d.teleportNear(t.absolutePos(at));
            t.assertTrue(!movedOntoLava.getAsBoolean(), "never onto lava: " + t.relativePos(d.blockPosition()));
        }
        d.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200));
        for (int i = 0; i < 200 && !movedOntoLava.getAsBoolean(); i++) d.teleportNear(t.absolutePos(at));
        t.assertTrue(movedOntoLava.getAsBoolean(), "with Fire Resistance lava is no danger");
        t.succeed();
    }

    /** it arrives unhurt: a fall it was in the middle of is not carried over */
    @GameTest(template = "floor", timeoutTicks = 60)
    public static void teleportCarriesNoFall(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        BlockPos before = d.blockPosition();
        d.fallDistance = 30;
        for (int i = 0; i < 20 && d.blockPosition().equals(before); i++) d.teleportNear(t.absolutePos(LifeTests.H.offset(2, 0, 2)));
        t.assertTrue(!d.blockPosition().equals(before), "it teleported");
        float health = d.getHealth();
        t.runAfterDelay(20, () -> { t.assertTrue(d.isAlive() && d.getHealth() == health, "it lands unhurt: " + d.getHealth() + " of " + health); t.succeed(); });
    }

    @GameTest(template = "floor", timeoutTicks = 200)
    public static void toldToStayItStaysThenFollowsAgain(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 1, Diet.SEED, Life.Stance.REACTIVE);  /* a pup: Stay and Follow are for every stage */
        Ranch.setStay(p, true);
        BlockPos spot = d.blockPosition();
        t.assertTrue(d.isStaying() && Ranch.roster(t.getLevel().getServer()).out(p.getUUID()).stay != null, "it stays, and the roster keeps the spot");
        p.moveTo(t.absoluteVec(new net.minecraft.world.phys.Vec3(11.5, 1, 11.5)));
        t.runAfterDelay(80, () -> {
            t.assertTrue(d.blockPosition().equals(spot), "it did not follow: " + d.blockPosition() + " from " + spot);
            Ranch.whistle(p);  /* with its owner: the whistle changes it over */
            t.assertTrue(!d.isStaying() && Ranch.roster(t.getLevel().getServer()).out(p.getUUID()).stay == null, "it follows again");
            t.assertTrue(d.blockPosition().equals(spot), "near, with a way to walk, it is not jumped to its owner: " + d.blockPosition());
            t.succeedWhen(() -> t.assertTrue(d.distanceTo(p) < 5, "it came: " + d.distanceTo(p)));
        });
    }

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void theWhistleCallsAFarDrekkiAndItFollows(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        Ranch.setStay(p, true);
        d.moveTo(d.getX(), d.getY() + 60, d.getZ());  /* far from its owner, staying */
        d.setNoGravity(true);
        Ranch.whistle(p);  /* staying: the whistle calls it */
        t.assertTrue(!d.isStaying() && d.distanceTo(p) < 8, "called from afar it came and follows: " + d.distanceTo(p));
        d.setNoGravity(false);
        d.moveTo(d.getX(), d.getY() + 60, d.getZ());
        d.setNoGravity(true);
        Ranch.whistle(p);  /* following, but not with its owner: the whistle calls it, it does not tell it to stay there */
        t.assertTrue(!d.isStaying() && d.distanceTo(p) < 8, "not with its owner, it is called, not told to stay: " + d.distanceTo(p));
        t.succeed();
    }

    @GameTest(template = "floor")
    public static void bardingStopsAnEnemysPushNotAPlayers(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        Husk z = zombie(t, new BlockPos(3, 1, 3));
        z.moveTo(d.getX() + 1, d.getY(), d.getZ());
        p.moveTo(d.getX() + 1, d.getY(), d.getZ());
        d.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        d.hurt(d.damageSources().mobAttack(z), 2);
        t.assertTrue(d.getDeltaMovement().x < 0, "bare, a monster's hit pushes it: " + d.getDeltaMovement());
        d.setBardingItem(GearTests.barding(dev.frosthaven.ranchercompanion.item.Barding.IRON));
        d.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); d.invulnerableTime = 0;
        d.hurt(d.damageSources().mobAttack(z), 2);
        t.assertTrue(d.getDeltaMovement().x == 0, "in barding, it does not: " + d.getDeltaMovement());
        t.runAfterDelay(1, () -> {
            d.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO); d.invulnerableTime = 0;
            d.hurt(d.damageSources().playerAttack(p), 2);
            t.assertTrue(d.getDeltaMovement().x < 0, "a player's push still moves it: " + d.getDeltaMovement());
            t.succeed();
        });
    }

    @GameTest(template = "floor")
    public static void noPlayerHurtsADrekki(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.RAW, Life.Stance.PASSIVE);
        ServerPlayer other = t.makeMockServerPlayerInLevel();
        other.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        var src = d.damageSources();
        var arrow = new net.minecraft.world.entity.projectile.Arrow(net.minecraft.world.entity.EntityType.ARROW, t.getLevel());
        float health = d.getHealth();
        other.moveTo(d.getX() + 1, d.getY(), d.getZ());
        d.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        t.assertTrue(d.hurt(src.playerAttack(other), 6) && d.getDeltaMovement().x < 0, "a player's hit pushes it back: " + d.getDeltaMovement());
        d.invulnerableTime = 0;
        d.hurt(src.playerAttack(p), 6); d.invulnerableTime = 0;
        d.hurt(src.arrow(arrow, other), 6); d.invulnerableTime = 0;
        d.hurt(src.indirectMagic(arrow, p), 6); d.invulnerableTime = 0;
        t.assertTrue(d.getHealth() == health && d.getLastHurtByMob() == null, "no player's hit, arrow or spell takes health or starts a fight: " + d.getHealth() + " of " + health);
        t.assertTrue(!d.hurt(src.lava(), 4) && !d.causeFallDamage(40, 1, src.fall()) && !d.isOnFire() && d.getHealth() == health, "lava and falls do not hurt it");
        t.assertTrue(!d.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100), other), "nor a player's poison");
        Husk z = zombie(t, new BlockPos(3, 1, 3));
        t.assertTrue(d.hurt(src.mobAttack(z), 2) && d.getHealth() < health, "a monster still hurts it");
        t.succeed();
    }

    /** a drekki shows its effects as swirl particles like a wolf or a player (Shane, 2026-11-26): vanilla's effect particle
     *  list, which every client draws around the mob, carries the effect's colour once it is applied */
    @GameTest(template = "floor", timeoutTicks = 60)
    public static void effectsShowAsSwirlParticles(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = out(t, p, 2, Diet.SEED, Life.Stance.REACTIVE);
        d.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200));
        t.runAfterDelay(5, () -> {
            try {
                var f = net.minecraft.world.entity.LivingEntity.class.getDeclaredField("DATA_EFFECT_PARTICLES");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                var key = (net.minecraft.network.syncher.EntityDataAccessor<java.util.List<net.minecraft.core.particles.ParticleOptions>>) f.get(null);
                var list = d.getEntityData().get(key);
                t.assertTrue(!list.isEmpty(), "the drekki's synced effect particles are empty: no swirls would show");
            } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
            t.succeed();
        });
    }
}
