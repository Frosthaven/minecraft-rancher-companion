package dev.frosthaven.ranchercompanion.test;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.look.Diet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** No player hurts a drekki, nor what a player owns (spec, Combat: players; FriendlyFire) */
@GameTestHolder(RancherCompanion.ID)
@PrefixGameTestTemplate(false)
public final class FriendlyFireTests {
    private FriendlyFireTests() {}

    @GameTest(template = "floor", timeoutTicks = 100)
    public static void aPlayersHitOnlyPushes(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.REACTIVE);
        t.runAfterDelay(70, () -> {  /* after a new player's spawn protection */
            float before = d.getHealth();
            d.setDeltaMovement(0, 0, 0);
            p.attack(d);
            t.assertTrue(d.getHealth() == before && d.hurtTime == 0, "no damage, no hurt flash: " + d.getHealth() + " hurtTime " + d.hurtTime);
            t.assertTrue(p.getLastHurtMob() != d, "it is not the player's last-hurt mob (summons and pets attack that)");
            t.assertTrue(d.getDeltaMovement().horizontalDistanceSqr() > 0, "but it is pushed: " + d.getDeltaMovement());
            t.succeed();
        });
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aPlayersDebuffIsRefusedAHealIsNot(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.REACTIVE);
        d.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 4), p);
        d.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), p);
        t.assertTrue(!d.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "a player's slowness is refused");
        t.assertTrue(d.hasEffect(MobEffects.REGENERATION), "a player's regeneration applies");
        t.succeed();
    }

    @GameTest(template = "floor", timeoutTicks = 60)
    public static void aPlayersPetNeverTargetsADrekki(GameTestHelper t) {
        ServerPlayer p = LifeTests.player(t);
        DrekkiEntity d = CombatTests.out(t, p, 2, Diet.RAW, Life.Stance.REACTIVE);
        var wolf = t.spawn(EntityType.WOLF, new BlockPos(8, 1, 8));
        wolf.tame(p);
        wolf.setTarget(d);
        t.assertTrue(wolf.getTarget() == null, "the owner's wolf does not take it as a target: " + wolf.getTarget());
        t.succeed();
    }
}
