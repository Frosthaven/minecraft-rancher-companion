package dev.frosthaven.ranchercompanion.life;

import dev.frosthaven.ranchercompanion.DrekkiConfig;
import dev.frosthaven.ranchercompanion.DrekkiSounds;
import dev.frosthaven.ranchercompanion.FoodDiets;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Diet;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;

/**
 * A drekki's life on the server (spec, Growth release): growth, the astral and diet lock-ins, biome time, hunger and
 * mood, while it is live: {@link #update} runs once a second. Time it was not ticking (its chunk unloaded, in a carrier,
 * its owner offline) passes it by: {@link #resume} starts it again where it left off (decided 2026-11-18, so a player
 * back after a week does not find every drekki starving).
 */
public final class Vitals {
    private Vitals() {}

    /** the last stage: Mature (0.5.0; growth stopped at Fledgling before) */
    public static final int STAGE_CAP = 3;
    /** a gap longer than this between two updates of a live drekki was not live time (its chunk stopped ticking) */
    private static final long LIVE_GAP = 200;
    private static final int[] HEALTH = {10, 30, 50};

    public static long now(DrekkiEntity d) { return d.getServer().overworld().getGameTime(); }

    public static double maxHealth(int stage, Life l) {
        return HEALTH[Math.max(1, Math.min(3, stage)) - 1] + (l.traits.contains(Trait.HEARTY.id) ? 6 : 0);
    }

    private static double hungerRate(DrekkiEntity d, boolean travelling) {
        double r = travelling ? DrekkiConfig.TRAVEL_HUNGER_PER_DAY.get() : DrekkiConfig.HUNGER_PER_DAY.get();
        return d.life.traits.contains(Trait.HIBERNATION_PLAN) ? r * 0.67 : r;
    }

    /** the first tick after it joins the world, or after a gap: it carries on from now; the missed time costs nothing */
    public static void resume(DrekkiEntity d) { d.life.stamp = now(d); }

    /** once a second while live */
    public static void update(DrekkiEntity d) {
        Life l = d.life;
        long now = now(d), dt = now - l.stamp;
        if (l.stamp == Life.NEVER || dt > LIVE_GAP) { resume(d); return; }
        if (dt <= 0) return;
        l.stamp = now;
        boolean employed = dev.frosthaven.ranchercompanion.ranch.Ranch.employed(d);
        boolean travelling = d.isOut() && !employed;  /* at work on its seat it is not travelling (spec, Go here: Employed) */
        /* a ghost does not get hungry. An employed drekki does, at the rate it would at home, and eats from its bag or its
           hatchery's troughs (EatGoal.homeMeal; Shane, 2026-11-27: a driver that never ate never got a meal's mood) */
        if (!l.ghost) l.spend(Life.drain(dt, hungerRate(d, travelling)));
        /* it ages while out with its owner; at work and at its hatchery only where the server config says so (age_at_work,
           age_at_home: off by default, Shane, 2026-11-27). Time at work counts as time out (its eyes and biome); time at
           the stable shapes nothing (spec, Affinities). A ghost does not age */
        boolean ages = travelling || employed && DrekkiConfig.AGE_AT_WORK.get() || !d.isOut() && !employed && DrekkiConfig.AGE_AT_HOME.get();
        if (!l.ghost && ages) grow(d, dt, travelling || employed);
        mood(d, dt);
        d.syncLife();
        effects(d, dt);
        dev.frosthaven.ranchercompanion.trait.Effects.trainingDay(d);
    }

    private static final net.minecraft.resources.ResourceLocation SLOW = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.frosthaven.ranchercompanion.RancherCompanion.ID, "miserable");

    /** the band's effects (spec, Mood): Miserable is 25% slower and has no home bonus; Uneasy and worse, and hunger, stop
     *  the home and Hearty regeneration: 1 health every 10 s in its home terrain; Hearty 1 every 10 s anywhere, every 5 s at
     *  home. Fed healing (spec, Hunger) is apart from mood, as a player's is */
    private static void effects(DrekkiEntity d, long dt) {
        Life l = d.life;
        Life.Band band = d.band();
        var speed = d.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            boolean slow = band == Life.Band.MISERABLE;
            if (slow && !speed.hasModifier(SLOW)) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SLOW, -0.25,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            else if (!slow && speed.hasModifier(SLOW)) speed.removeModifier(SLOW);
        }
        float fed = l.fedHeal(dt, d.getMaxHealth() - d.getHealth());
        if (fed > 0) { ownHeal(d, fed); d.syncLife(); }
        if (l.hungry() || band == Life.Band.UNEASY || band == Life.Band.MISERABLE || d.getHealth() >= d.getMaxHealth()) { l.regen = 0; return; }
        boolean home = atHome(d), hearty = l.traits.contains(Trait.HEARTY.id);
        double perTenSeconds = hearty ? (home ? 2 : 1) : home ? 1 : 0;
        l.regen += perTenSeconds * dt / 200.0;
        if (l.regen >= 1) { ownHeal(d, (float) Math.floor(l.regen)); l.regen -= Math.floor(l.regen); }
    }

    /** a heal of its own, which Hearty does not learn from */
    public static void ownHeal(DrekkiEntity d, float amount) { d.selfHeal = true; d.heal(amount); d.selfHeal = false; }

    /** in its astral home terrain, which Miserable takes away (spec, Mood) */
    /** its astral (either eye's) is the astral of where it stands, mood aside */
    public static boolean inHomeTerrain(DrekkiEntity d) {
        return d.astral() != Astral.DORMANT && d.hasAstral(Places.astral(d.level(), d.blockPosition()));
    }

    public static boolean atHome(DrekkiEntity d) {
        return d.astral() != Astral.DORMANT && d.band() != Life.Band.MISERABLE && d.hasAstral(Places.astral(d.level(), d.blockPosition()));  /* a two-eyed drekki: both are home */
    }

    // ---- growth ----

    private static void grow(DrekkiEntity d, long dt, boolean out) {
        Life l = d.life;
        double days = DrekkiConfig.STAGE_DAYS.get();
        l.travel += dt;
        var pos = d.blockPosition();
        if (d.stage() == 1 && d.astral() == Astral.DORMANT && !l.ghost) {
            if (out) l.astral[Places.astral(d.level(), pos)] += dt;
            if (l.travel >= Life.stageTicks(days) / 2) lockAstral(d);  /* none counted (raised at home): the sky it is under */
        }
        if (d.stage() == 2 && out) l.biome.merge(Places.category(d.level(), pos), dt, Long::sum);
        int next = Life.stageFor(l.travel, days, STAGE_CAP);
        if (next > d.stage()) growUp(d, next);
    }

    private static void lockAstral(DrekkiEntity d) {
        Integer a = Life.pick(d.life.astral, 1, n -> d.getRandom().nextInt(n));
        if (a == null) a = Places.astral(d.level(), d.blockPosition());
        d.setLook(d.stage(), d.line(), d.diet(), a, d.horn());
        Ranch.touch(d);
        Component name = Component.literal(Astral.NAMES[a]).withStyle(Style.EMPTY.withColor(Astral.iris(a))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, dev.frosthaven.ranchercompanion.trait.Tips.hover(dev.frosthaven.ranchercompanion.trait.Tips.astral(a)))));
        dev.frosthaven.ranchercompanion.trait.Traits.tell(d, Component.translatable("message.rancher_companion.astral_lock", who(d), name));  /* shared, as a learned trait is */
        burst(d, Astral.iris(a));
        d.playSound(DrekkiSounds.CHIME.get(), 1f, 1f);
    }

    private static void growUp(DrekkiEntity d, int stage) {
        Life l = d.life;
        int diet = d.diet();
        if (d.stage() == 1) {  /* the diet locks at stage 2: what it ate most, or any diet when it never ate */
            Integer pick = Life.pick(l.meals, n -> d.getRandom().nextInt(n));
            diet = pick != null ? pick : d.getRandom().nextInt(4);
            if (d.astral() == Astral.DORMANT) lockAstral(d);
            l.stance = Life.Stance.PASSIVE;  /* a new Fledgling does not fight until its owner says so (Shane, 2026-11-23) */
        }
        d.setLook(stage, d.line(), diet, d.astral(), d.horn());
        d.applyStageHealth(true);
        d.outgrowBarding();
        d.onGrown();
        Ranch.touch(d);
        dev.frosthaven.ranchercompanion.trait.Traits.tell(d, Component.translatable("message.rancher_companion.grew", who(d), Component.translatable("stage.rancher_companion." + stage)));  /* shared, as a learned trait is */
        burst(d, 0xF2D27A);
        d.playSound(DrekkiSounds.GROW.get(), 1f, 1f);
    }

    /** "Pip", or "Your drekki" for an unnamed one (spec, Pup release: Names) */
    public static Component who(DrekkiEntity d) {
        return d.hasCustomName() ? d.getCustomName() : Component.translatable("drekki.rancher_companion.yours");
    }

    private static void tell(DrekkiEntity d, Component msg) {
        Player p = d.ownerPlayer();
        if (p == null && d.owner() != null) p = d.getServer().getPlayerList().getPlayer(d.owner());
        if (p != null) p.sendSystemMessage(msg);
    }

    public static void burst(DrekkiEntity d, int rgb) {
        if (!(d.level() instanceof ServerLevel sl)) return;
        var c = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
        sl.sendParticles(new DustParticleOptions(c, 1.2f), d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ(), 40,
            d.getBbWidth() * 0.6, d.getBbHeight() * 0.4, d.getBbWidth() * 0.6, 0.05);
        sl.sendParticles(ParticleTypes.END_ROD, d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ(), 8, 0.2, 0.3, 0.2, 0.05);
    }

    // ---- hunger and meals ----

    /** a food this drekki eats: any diet's while its diet is open (stage 1), else its own */
    public static boolean eats(DrekkiEntity d, ItemStack s) {
        Diet diet = FoodDiets.of(s);
        return diet != null && (d.stage() == 1 || diet.ordinal() == d.diet());
    }

    /** one meal: the food's own nutrition and saturation (a food without them, as wheat or seeds: one shank and a
     *  little saturation), mood, and in stage 1 a vote for its diet */
    public static void eat(DrekkiEntity d, ItemStack food, boolean show) {
        Life l = d.life;
        Diet diet = FoodDiets.of(food);
        var props = food.getFoodProperties(d);
        l.eat(props != null ? props.nutrition() / 2.0 : 1, props != null ? props.saturation() / 2.0 : 0.6);
        if (show) l.addMood(5);
        if (d.stage() == 1 && diet != null) l.meals[diet.ordinal()]++;
        if (show && d.level() instanceof ServerLevel sl) {
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, food.copyWithCount(1)), d.getX(), d.getY() + d.getBbHeight() * 0.7, d.getZ(),
                6, 0.15, 0.1, 0.15, 0.05);
            sl.playSound(null, d.getX(), d.getY(), d.getZ(), DrekkiSounds.EAT.get(), SoundSource.NEUTRAL, 0.8f, 1f + (3 - d.stage()) * 0.2f);
        }
        d.syncLife();
    }

    // ---- mood ----

    /** -1 for each 2 health it loses (spec, Mood) */
    public static void hurt(DrekkiEntity d, float amount) { d.life.addMood(-amount / 2); d.syncLife(); }

    /** +3 for each hostile mob killed within 10 blocks of it, by anyone */
    public static void hostileDied(net.minecraft.world.entity.LivingEntity mob) {
        for (DrekkiEntity d : Ranch.lives())
            if (d.level() == mob.level() && d.distanceToSqr(mob) <= 10 * 10) { d.life.addMood(3); d.syncLife(); }
    }

    private static void mood(DrekkiEntity d, long dt) {
        Life l = d.life;
        if (l.ghost) return;  /* a ghost has no mood: it neither rises nor falls */
        double minutes = dt / 1200.0;
        if (l.hungry()) l.addMood(-2 * minutes);
        /* an employed drekki is away from its owner by design: no drain for that (Shane, 2026-11-26: a train driver sat
           Miserable). Otherwise its mood runs as at home: hunger, meals, petting, kills (Shane, 2026-11-27) */
        if (dev.frosthaven.ranchercompanion.ranch.Ranch.employed(d)) return;
        Player owner = d.ownerPlayer();
        if (d.isOut() && (owner == null || owner.level() != d.level() || owner.distanceToSqr(d) > 30 * 30)) l.addMood(-minutes);
    }

    /** Recently pet: an hour of real time from now (spec, Mood) */
    public static void pet(DrekkiEntity d) {
        d.life.petUntil = System.currentTimeMillis() + Life.PET_MILLIS;
        d.syncLife();
    }

}
