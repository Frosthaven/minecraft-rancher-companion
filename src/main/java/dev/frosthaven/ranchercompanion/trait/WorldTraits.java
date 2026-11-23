package dev.frosthaven.ranchercompanion.trait;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.EnderManAngerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The other traits of 0.5.0 (spec, Traits; the sheet's Activity Traits tab): Staring Contest, Web Cutter, Live Wire and
 * Rewind, and the rule that the void never kills a drekki. How each is learned and what it does, side by side.
 */
public final class WorldTraits {
    private WorldTraits() {}

    public static final int SURGE = 200, NIGHT_VISION = 600;
    public static final double PULL = 64, NEAR_STRIKE = 8;
    /** a bolt Live Wire made itself: not pulled again, and it teaches nothing */
    private static final String OWN_BOLT = "rancher_companion.live_wire";

    /** Rewind: the last solid ground each player stood on */
    private static final Map<UUID, GlobalPos> SAFE = new HashMap<>();
    /** Rewind, learning: the pearl each player threw while falling over nothing, then the game time by which to land */
    private static final Map<UUID, UUID> PEARLS = new HashMap<>();
    private static final Map<UUID, Long> LANDING = new HashMap<>();
    /** Rewind: the cooldown of a drekki that is not loaded (told to stay far away), by drekki id */
    private static final Map<UUID, Long> AWAY_COOL = new HashMap<>();

    public static void register(IEventBus bus) {
        bus.addListener(LivingDeathEvent.class, WorldTraits::kill);
        bus.addListener(EnderManAngerEvent.class, e -> { if (Traits.near(e.getPlayer(), Trait.STARING_CONTEST) != null) e.setCanceled(true); });
        bus.addListener(EntityJoinLevelEvent.class, WorldTraits::joined);
        bus.addListener(EntityTeleportEvent.EnderPearl.class, e -> {
            if (!e.isCanceled() && e.getPearlEntity().getUUID().equals(PEARLS.remove(e.getPlayer().getUUID())))
                LANDING.put(e.getPlayer().getUUID(), e.getPlayer().level().getGameTime() + 100);
        });
        bus.addListener(PlayerTickEvent.Post.class, e -> { if (e.getEntity() instanceof ServerPlayer p) tick(p); });
        bus.addListener(net.neoforged.bus.api.EventPriority.HIGH, LivingIncomingDamageEvent.class, WorldTraits::voidHurt);
    }

    public static void clear() { SAFE.clear(); PEARLS.clear(); LANDING.clear(); AWAY_COOL.clear(); }

    public static void forget(UUID player) { PEARLS.remove(player); LANDING.remove(player); }

    private static void kill(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer p)) return;
        if (e.getEntity() instanceof EnderMan) Traits.activity(p, Trait.STARING_CONTEST);
        if (e.getEntity() instanceof Spider) Traits.activity(p, Trait.WEB_CUTTER);  /* cave spiders are spiders */
    }

    // ---- Web Cutter ----

    /** from the cobweb's mixin, on both sides: the web does not hold a drekki with the trait, nor its owner within 10
     *  blocks of it. The client asks the drekki it can see (their traits and owner are synched). */
    public static boolean cutsWebs(Entity e) {
        if (e instanceof DrekkiEntity d) return holds(d);
        if (!(e instanceof Player p)) return false;
        if (!e.level().isClientSide) return Traits.near(p, Trait.WEB_CUTTER) != null;
        for (DrekkiEntity d : e.level().getEntitiesOfClass(DrekkiEntity.class, e.getBoundingBox().inflate(Traits.RANGE), x -> p.getUUID().equals(x.owner())))
            if (holds(d) && d.distanceToSqr(e) <= Traits.RANGE * Traits.RANGE) return true;
        return false;
    }

    private static boolean holds(DrekkiEntity d) {
        if (d.ghost()) return false;
        for (Tag t : d.traitView().getList("traits", Tag.TAG_STRING)) if (t.getAsString().equals(Trait.WEB_CUTTER.id)) return true;
        return false;
    }

    // ---- Live Wire, and the pearl of Rewind ----

    private static void joined(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide || !(e.getLevel() instanceof ServerLevel level)) return;
        if (e.getEntity() instanceof ThrownEnderpearl pearl && pearl.getOwner() instanceof ServerPlayer p && !e.loadedFromDisk()) {
            /* thrown while falling with nothing under them, all the way down */
            if (!p.onGround() && p.fallDistance > 2 && level.getHeight(Heightmap.Types.MOTION_BLOCKING, p.getBlockX(), p.getBlockZ()) <= level.getMinBuildHeight())
                PEARLS.put(p.getUUID(), pearl.getUUID());
            return;
        }
        if (!(e.getEntity() instanceof LightningBolt bolt) || e.loadedFromDisk() || bolt.getPersistentData().getBoolean(OWN_BOLT)) return;
        /* learned from a strike within 8 blocks of the owner, a lightning rod's or a Channeling trident's too */
        for (ServerPlayer p : level.players()) if (p.distanceToSqr(bolt) <= NEAR_STRIKE * NEAR_STRIKE) Traits.activity(p, Trait.LIVE_WIRE);
        if (!level.isThundering()) return;
        DrekkiEntity rod = null;
        for (DrekkiEntity d : Traits.holders(Trait.LIVE_WIRE)) {
            double dx = d.getX() - bolt.getX(), dz = d.getZ() - bolt.getZ(), far = dx * dx + dz * dz;
            if (d.level() == level && d.isAlive() && far <= PULL * PULL && level.canSeeSky(d.blockPosition().above())
                && (rod == null || far < rod.distanceToSqr(bolt.getX(), rod.getY(), bolt.getZ()))) rod = d;
        }
        if (rod == null) return;
        e.setCanceled(true);  /* the real bolt never lands: no damage, no fire, no changed villagers or pigs */
        strike(rod);
    }

    /** the bolt the drekki draws: the flash and the thunder only; then the surge and its owner's Night Vision */
    public static void strike(DrekkiEntity d) {
        LightningBolt show = EntityType.LIGHTNING_BOLT.create(d.level());
        if (show != null) {
            show.setVisualOnly(true);
            show.getPersistentData().putBoolean(OWN_BOLT, true);
            show.moveTo(d.position());
            d.level().addFreshEntity(show);
        }
        d.surgeUntil = d.tickCount + SURGE;
        Vitals.burst(d, 0xFFF4B0);
        Player owner = d.ownerPlayer();
        if (owner != null && owner.level() == d.level() && owner.distanceToSqr(d) <= PULL * PULL)
            owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION, 0, false, true));
    }

    // ---- Rewind ----

    private static void tick(ServerPlayer p) {
        long now = p.level().getGameTime();
        if (p.tickCount % 5 == 0 && p.onGround() && !p.isSpectator()) {
            BlockPos below = p.blockPosition().below();
            if (p.level().getBlockState(below).isRedstoneConductor(p.level(), below)) SAFE.put(p.getUUID(), GlobalPos.of(p.level().dimension(), p.blockPosition()));
        }
        Long land = LANDING.get(p.getUUID());
        if (land == null) return;
        if (now > land || !p.isAlive()) LANDING.remove(p.getUUID());
        else if (p.onGround()) { LANDING.remove(p.getUUID()); Traits.activity(p, Trait.REWIND); }  /* the pearl saved them */
    }

    /** void damage to a player whose checked-out drekki holds Rewind: none, and back to the last solid ground */
    private static void voidHurt(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD) || p.server == null) return;
        if (p.getY() >= p.level().getMinBuildHeight()) return;  /* /kill and the like deal this type too, above the world */
        GlobalPos safe = SAFE.get(p.getUUID());
        ServerLevel to = safe == null ? null : p.server.getLevel(safe.dimension());
        if (to == null) return;
        Roster.Entry out = Ranch.roster(p.server).out(p.getUUID());
        if (out == null || out.where != Roster.Where.OUT) return;  /* at a hatchery, at work or in a carrier: not travelling with them */
        DrekkiEntity d = Ranch.liveOf(out.id);
        long now = p.server.overworld().getGameTime();
        if (d != null) {
            if (d.life.ghost || !d.life.traits.contains(Trait.REWIND.id) || d.life.cooldowns.getOrDefault(Trait.REWIND.id, Long.MIN_VALUE) > Vitals.now(d)) return;
            Traits.cool(d, Trait.REWIND);
        } else {
            /* told to stay somewhere that is not loaded: its saved record says what it holds */
            var life = out.save == null ? null : out.save.getCompound("Life");
            if (life == null || life.getBoolean("ghost") || AWAY_COOL.getOrDefault(out.id, Long.MIN_VALUE) > now) return;
            boolean holds = false;
            for (Tag t : life.getList("traits", Tag.TAG_STRING)) holds |= t.getAsString().equals(Trait.REWIND.id);
            if (!holds || life.getCompound("cooldowns").getLong(Trait.REWIND.id) > now) return;
            AWAY_COOL.put(out.id, now + Trait.REWIND.cooldown);
        }
        e.setCanceled(true);
        BlockPos at = safe.pos();
        p.teleportTo(to, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, p.getYRot(), p.getXRot());
        p.resetFallDistance();
        p.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        to.playSound(null, at, net.minecraft.sounds.SoundEvents.CHORUS_FRUIT_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        /* the drekki comes with them, unless it was told to stay */
        if (d != null && out.stay == null) Ranch.after(p.server, 2, () -> { if (p.isAlive() && d.isAlive()) { d.resetFallDistance(); d.teleportNear(p); } });
    }

    // ---- the void never kills a drekki ----

    /** from DrekkiEntity, below the world: back to the last solid ground it stood on, else to its owner or home */
    public static void outOfTheVoid(DrekkiEntity d) {
        d.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        d.resetFallDistance();
        GlobalPos safe = d.safeGround;
        if (safe != null && safe.dimension() == d.level().dimension() && d.fitsAt(safe.pos())) {
            d.teleportTo(safe.pos().getX() + 0.5, safe.pos().getY(), safe.pos().getZ() + 0.5);
            return;
        }
        dev.frosthaven.ranchercompanion.ranch.Death.bringBack(d);
    }

    /** from DrekkiEntity's tick: remembers the solid ground it stands on */
    public static void standing(DrekkiEntity d) {
        if (d.tickCount % 10 != 0 || !d.onGround() || d.isPassenger()) return;
        BlockPos below = d.blockPosition().below();
        if (d.level().getBlockState(below).isRedstoneConductor(d.level(), below)) d.safeGround = GlobalPos.of(d.level().dimension(), d.blockPosition());
    }
}
