package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The fifteen traits that were the biome traits, learned from activities since 2026-11-23 (spec, Traits; the sheet's
 * Activity Traits tab, rows 31 to 45): how each is learned, and what the nine that need no rider do. Four Winds,
 * Predator's Eye, Ocean Walker, Lava Walker, Leap of Faith and Wings of Hope are learned here and act when riding exists.
 */
public final class RoamTraits {
    private RoamTraits() {}

    /** one player's counts toward the distance and look traits */
    private static final class Trail {
        final Map<ResourceLocation, Integer> stat = new HashMap<>();
        final Map<Trait, Double> owed = new HashMap<>();
        final Set<UUID> spotted = new HashSet<>();
        Vec3 at;
        float peakFall;
        long landedAt = Long.MIN_VALUE, fallHurtAt = Long.MIN_VALUE;
    }
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    /** distance by the game's own statistics, in centimetres: the stat, the trait, the centimetres one count takes */
    private record Road(ResourceLocation stat, Trait trait, int step) {}
    private static final Road[] ROADS = {
        new Road(Stats.SPRINT_ONE_CM, Trait.OPEN_ROAD, 10000),      /* 20 counts: 2,000 blocks */
        new Road(Stats.SWIM_ONE_CM, Trait.DOWNSTREAM, 5000),        /* 10 counts: 500 blocks */
        new Road(Stats.BOAT_ONE_CM, Trait.OCEAN_WALKER, 10000),     /* 10 counts: 1,000 blocks */
        new Road(Stats.HORSE_ONE_CM, Trait.FOUR_WINDS, 10000),      /* horse, donkey, mule or camel: 1,000 blocks */
        new Road(Stats.STRIDER_ONE_CM, Trait.LAVA_WALKER, 3000),    /* 300 blocks */
    };
    static final double SNOW_STEP = 30;  /* Frost Strider: 10 counts, 300 blocks on snow or ice */

    private static final ResourceLocation SPEED = id("open_road"), SWIM = id("downstream_swim"), AIR = id("downstream_air");
    /** Inverse Luminance: where each drekki's light stands */
    private record Lamp(ServerLevel level, BlockPos pos) {}
    private static final Map<UUID, Lamp> LAMPS = new HashMap<>();
    /** Draconic Vampirism: the game time a healer's second began, and what it has healed in it */
    private static final Map<UUID, long[]> FED = new HashMap<>();
    public static final float VAMPIRISM = 0.10f, VAMPIRISM_CAP = 2;

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, path); }

    public static void register(IEventBus bus) {
        bus.addListener(PlayerTickEvent.Post.class, e -> { if (e.getEntity() instanceof ServerPlayer p) tick(p); });
        bus.addListener(ServerTickEvent.Post.class, e -> { if (e.getServer().getTickCount() % 5 == 0) lamps(); });
        bus.addListener(LivingDeathEvent.class, RoamTraits::kill);
        bus.addListener(LivingDamageEvent.Post.class, RoamTraits::hurt);
        bus.addListener(BlockEvent.BreakEvent.class, e -> {
            if (e.getPlayer() instanceof ServerPlayer p && e.getState().getBlock() instanceof CropBlock c && c.isMaxAge(e.getState())) Traits.activity(p, Trait.HOMES_GARDENS);
        });
        bus.addListener(BlockEvent.EntityPlaceEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer p && e.getPlacedBlock().getBlock() instanceof TorchBlock) Traits.activity(p, Trait.INVERSE_LUMINANCE);
        });
        bus.addListener(PlayerWakeUpEvent.class, e -> {  /* the night slept through: the game wakes everyone, no one left the bed */
            if (e.getEntity() instanceof ServerPlayer p && !e.wakeImmediately() && !e.updateLevel()) Traits.activity(p, Trait.HIBERNATION);
        });
        bus.addListener(ProjectileImpactEvent.class, e -> {  /* counted at the hit: Balm & Salve may stop the Levitation */
            if (e.getProjectile() instanceof ShulkerBullet && e.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof ServerPlayer p) Traits.activity(p, Trait.WINGS_OF_HOPE);
        });
        bus.addListener(CropGrowEvent.Post.class, RoamTraits::grown);
        bus.addListener(EntityJoinLevelEvent.class, e -> { if (!e.getLevel().isClientSide && e.getEntity() instanceof Creeper c) repel(c); });
        bus.addListener(EntityLeaveLevelEvent.class, e -> { if (e.getEntity() instanceof DrekkiEntity d && !e.getLevel().isClientSide) dark(d.getUUID()); });
    }

    public static void clear() {
        for (UUID id : new java.util.ArrayList<>(LAMPS.keySet())) dark(id);
        TRAILS.clear(); FED.clear();
    }

    public static void forget(UUID player) { TRAILS.remove(player); }

    // ---- learning ----

    public static void tick(ServerPlayer p) {
        Trail t = TRAILS.computeIfAbsent(p.getUUID(), k -> new Trail());
        boolean watched = Traits.learner(p) != null;
        if (p.tickCount % 20 == 0) {
            for (Road r : ROADS) {
                int now = p.getStats().getValue(Stats.CUSTOM.get(r.stat));
                Integer was = t.stat.put(r.stat, now);
                if (was != null && watched && now > was) owe(p, t, r.trait, now - was, r.step);
            }
            modifiers(p);
        }
        /* Frost Strider: on foot over snow or ice */
        Vec3 at = p.position();
        if (t.at != null && watched && p.onGround() && !p.isPassenger() && snowy(p)) owe(p, t, Trait.FROST_STRIDER, Math.hypot(at.x - t.at.x, at.z - t.at.z), SNOW_STEP);
        t.at = at;
        /* Predator's Eye: a different hostile mob in the spyglass each time */
        if (watched && p.isScoping() && p.tickCount % 5 == 0) {
            Vec3 eye = p.getEyePosition(), to = eye.add(p.getViewVector(1).scale(64));
            var hit = ProjectileUtil.getEntityHitResult(p, eye, to, p.getBoundingBox().expandTowards(p.getViewVector(1).scale(64)).inflate(1), en -> en instanceof Enemy && en.isAlive(), 64 * 64);
            if (hit != null && p.hasLineOfSight(hit.getEntity()) && t.spotted.add(hit.getEntity().getUUID())) Traits.activity(p, Trait.PREDATORS_EYE);
        }
        /* Leap of Faith: a fall of 30 blocks or more that ended without fall damage (water, hay, slime) */
        if (p.fallDistance > t.peakFall) t.peakFall = p.fallDistance;
        else if (p.fallDistance == 0 && t.peakFall > 0) {
            if (t.peakFall >= 30) t.landedAt = p.level().getGameTime();
            t.peakFall = 0;
        }
        if (t.landedAt != Long.MIN_VALUE && p.level().getGameTime() >= t.landedAt + 2) {
            if (p.isAlive() && t.fallHurtAt < t.landedAt - 2) Traits.activity(p, Trait.LEAP_OF_FAITH);
            t.landedAt = Long.MIN_VALUE;
        }
    }

    private static void owe(ServerPlayer p, Trail t, Trait trait, double amount, double step) {
        double owed = t.owed.merge(trait, amount, Double::sum);
        while (owed >= step) { owed -= step; Traits.activity(p, trait); }
        t.owed.put(trait, owed);
    }

    private static boolean snowy(Player p) {
        BlockState feet = p.level().getBlockState(p.blockPosition()), under = p.level().getBlockState(p.getOnPos());
        return feet.is(BlockTags.SNOW) || under.is(BlockTags.SNOW) || under.is(BlockTags.ICE);
    }

    private static void kill(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (e.getEntity() instanceof Creeper) Traits.activity(p, Trait.NATURAL_REPELLANT);
        if (e.getEntity() instanceof Hoglin) Traits.activity(p, Trait.HOGLIN_WARD);
        if (e.getEntity() instanceof Enemy && p.getHealth() <= 6) Traits.activity(p, Trait.DRACONIC_VAMPIRISM);  /* at 3 hearts or less */
    }

    // ---- effects ----

    /** a drekki with the trait helps this entity here: the server asks the trait index, the client the drekki it sees
     *  (their traits and owner are synched). For rules the game checks on both sides: movement, powder snow. */
    public static boolean near(Entity e, Trait t) {
        if (!(e instanceof Player p)) return false;
        if (!e.level().isClientSide) return Traits.near(p, t) != null;
        for (DrekkiEntity d : e.level().getEntitiesOfClass(DrekkiEntity.class, e.getBoundingBox().inflate(Traits.RANGE), x -> p.getUUID().equals(x.owner())))
            if (holds(d, t) && d.distanceToSqr(e) <= Traits.RANGE * Traits.RANGE) return true;
        return false;
    }

    static boolean holds(DrekkiEntity d, Trait t) {
        if (d.ghost()) return false;
        for (Tag tag : d.traitView().getList("traits", Tag.TAG_STRING)) if (tag.getAsString().equals(t.id)) return true;
        return false;
    }

    /** The Open Road and Downstream: the owner's speed, swim speed and breath while a holder is within 10 blocks */
    private static void modifiers(ServerPlayer p) {
        set(p, Attributes.MOVEMENT_SPEED, SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, Traits.near(p, Trait.OPEN_ROAD) != null);
        boolean stream = Traits.near(p, Trait.DOWNSTREAM) != null;
        set(p, NeoForgeMod.SWIM_SPEED, SWIM, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, stream);
        set(p, Attributes.OXYGEN_BONUS, AIR, 0.25, AttributeModifier.Operation.ADD_VALUE, stream);  /* air lasts 1.25 times as long */
    }

    private static void set(LivingEntity e, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation op, boolean on) {
        var a = e.getAttribute(attribute);
        if (a == null || a.hasModifier(id) == on) return;
        if (on) a.addTransientModifier(new AttributeModifier(id, amount, op)); else a.removeModifier(id);
    }

    /** Homes & Gardens: a crop that grew within 20 blocks of a holder grows one more step one time in four */
    private static void grown(CropGrowEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level) || !(e.getState().getBlock() instanceof CropBlock crop)) return;
        BlockPos pos = e.getPos();
        for (DrekkiEntity d : Traits.holders(Trait.HOMES_GARDENS)) {
            if (d.level() != level || !d.isAlive() || d.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 20 * 20) continue;
            BlockState now = level.getBlockState(pos);
            if (now.getBlock() == crop && !crop.isMaxAge(now) && level.random.nextInt(4) == 0) level.setBlock(pos, crop.getStateForAge(crop.getAge(now) + 1), 2);
            return;
        }
    }

    /** Natural Repellant: a creeper keeps away from a holder as it does from a cat (6 blocks). Its own goal: the game's
     *  avoid goal asks whether the creeper could attack what it avoids, and a drekki is often not attackable. */
    private static void repel(Creeper c) {
        c.goalSelector.addGoal(3, new net.minecraft.world.entity.ai.goal.Goal() {
            private net.minecraft.world.level.pathfinder.Path path;
            { setFlags(java.util.EnumSet.of(Flag.MOVE)); }
            @Override public boolean canUse() {
                DrekkiEntity from = null;
                for (DrekkiEntity d : Traits.holders(Trait.NATURAL_REPELLANT))
                    if (d.level() == c.level() && d.isAlive() && d.distanceToSqr(c) <= 6 * 6 && (from == null || d.distanceToSqr(c) < from.distanceToSqr(c))) from = d;
                if (from == null) return false;
                Vec3 to = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(c, 16, 7, from.position());
                if (to == null || from.distanceToSqr(to) < from.distanceToSqr(c)) return false;
                path = c.getNavigation().createPath(to.x, to.y, to.z, 0);
                return path != null;
            }
            @Override public boolean canContinueToUse() { return !c.getNavigation().isDone(); }
            @Override public void start() { c.getNavigation().moveTo(path, 1.2); }
        });
    }

    /** Hoglin Ward, from the hoglin sensor's mixin: with no warped fungus near, a holder within 8 blocks is what it avoids */
    public static Optional<BlockPos> ward(ServerLevel level, Hoglin h) {
        for (DrekkiEntity d : Traits.holders(Trait.HOGLIN_WARD))
            if (d.level() == level && d.isAlive() && d.distanceToSqr(h) <= 8 * 8) return Optional.of(d.blockPosition());
        return Optional.empty();
    }

    /** Hibernation Plan, from the player's exhaustion mixin: a third less while a holder is within 10 blocks */
    public static float exhaustion(Player p, float amount) {
        return !p.level().isClientSide && Traits.near(p, Trait.HIBERNATION) != null ? amount * 0.67f : amount;
    }

    /** Draconic Vampirism: a tenth of the damage the drekki or its owner deals comes back as health, 2 a second at most */
    private static void hurt(LivingDamageEvent.Post e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || e.getNewDamage() <= 0) return;
        if (victim instanceof ServerPlayer p && e.getSource().is(DamageTypeTags.IS_FALL)) TRAILS.computeIfAbsent(p.getUUID(), k -> new Trail()).fallHurtAt = p.level().getGameTime();
        Entity by = e.getSource().getEntity();
        if (by == victim) return;
        if (by instanceof ServerPlayer p && Traits.near(p, Trait.DRACONIC_VAMPIRISM) != null) { float h = feed(p, e.getNewDamage()); if (h > 0) p.heal(h); }
        else if (by instanceof DrekkiEntity d && !d.life.ghost && d.life.traits.contains(Trait.DRACONIC_VAMPIRISM.id)) { float h = feed(d, e.getNewDamage()); if (h > 0) Vitals.ownHeal(d, h); }
    }

    private static float feed(LivingEntity healer, float dealt) {
        long now = healer.level().getGameTime();
        long[] f = FED.computeIfAbsent(healer.getUUID(), k -> new long[]{now, 0});
        if (now - f[0] >= 20) { f[0] = now; f[1] = 0; }
        float room = VAMPIRISM_CAP - f[1] / 1000f, heal = Math.min(room, dealt * VAMPIRISM);
        if (heal <= 0) return 0;
        f[1] += Math.round(heal * 1000);
        return heal;
    }

    // ---- Inverse Luminance ----

    /** the light a holder gives where it stands: 14 less the daylight that reaches it, so full in a cave or at night */
    static int glow(ServerLevel level, BlockPos pos) {
        int sky = Math.max(0, level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        return Math.max(0, Math.min(14, 14 - sky));
    }

    /** a light block of the game's travels with each holder, in the air at its head, and goes out when it leaves */
    private static void lamps() {
        Set<UUID> lit = new HashSet<>();
        for (DrekkiEntity d : Traits.holders(Trait.INVERSE_LUMINANCE)) {
            if (!(d.level() instanceof ServerLevel level) || !d.isAlive()) continue;
            BlockPos pos = BlockPos.containing(d.getX(), d.getY() + d.getBbHeight() * 0.6, d.getZ());
            int glow = glow(level, pos);
            Lamp was = LAMPS.get(d.getUUID());
            BlockState there = level.getBlockState(pos);
            boolean ours = was != null && was.level == level && was.pos.equals(pos);
            if (glow <= 0 || !(there.isAir() || ours && there.is(Blocks.LIGHT))) continue;  /* nothing to give, or no air to stand it in: the old one goes out below */
            lit.add(d.getUUID());
            if (ours && there.is(Blocks.LIGHT) && there.getValue(LightBlock.LEVEL) == glow) continue;
            if (was != null && !ours) put(was, Blocks.AIR.defaultBlockState());
            level.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, glow), 3);
            LAMPS.put(d.getUUID(), new Lamp(level, pos));
        }
        for (UUID id : new java.util.ArrayList<>(LAMPS.keySet())) if (!lit.contains(id)) dark(id);
    }

    private static void dark(UUID drekki) {
        Lamp l = LAMPS.remove(drekki);
        if (l != null) put(l, Blocks.AIR.defaultBlockState());
    }

    private static void put(Lamp l, BlockState state) {
        if (l.level.isLoaded(l.pos) && l.level.getBlockState(l.pos).is(Blocks.LIGHT)) l.level.setBlock(l.pos, state, 3);
    }

    public static int lamps(ServerLevel level) { int n = 0; for (Lamp l : LAMPS.values()) if (l.level == level) n++; return n; }
}
