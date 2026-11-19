package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Lineage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The drekki. Its look (stage, lineage, diet, astral, horns) and the loop it plays are synched to clients; the
 * showcase pins an animation and, for the travelling loops, walks it up and down a lane.
 */
public class DrekkiEntity extends PathfinderMob {
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DIET = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ASTRAL = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HORN = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HAT = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BARDING = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DYE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANIM = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LANE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.FLOAT);
    /** a live drekki (one with an owner) plays the loop that fits its movement; a showcase one plays its pinned loop */
    private static final EntityDataAccessor<Boolean> OWNED = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** ticks left of a petting lean-in */
    private static final EntityDataAccessor<Integer> PET = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    public static final int PET_TICKS = 30;
    /** the owner, synched so the client knows which clicks do something (petting swings the arm, hats, the screen) */
    private static final EntityDataAccessor<java.util.Optional<java.util.UUID>> OWNER = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** its life as the screen, Jade and the mood icon show it: food, effective mood, band, seconds of Recently pet left,
     *  kin, ghost, stance, and a pinned action loop (Anim index + 1, 0 none) */
    private static final EntityDataAccessor<Float> FOOD = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> MOOD = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> BAND = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PET_LEFT = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIN = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GHOST = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    /** a ghost shaking back to life (spec, Ghost drekki: revive) */
    private static final EntityDataAccessor<Boolean> REVIVING = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** the traits tab: learned trait ids and counted progress */
    private static final EntityDataAccessor<CompoundTag> TRAITS = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.COMPOUND_TAG);

    /** its life (server: the truth, saved under "Life"; client: unused, the synched values above stand in) */
    public final dev.frosthaven.ranchercompanion.life.Life life = new dev.frosthaven.ranchercompanion.life.Life();
    /** its pack from stage 2 (spec, Drekki screen: 9 slots) */
    private final net.minecraft.world.SimpleContainer pack = new net.minecraft.world.SimpleContainer(9);
    private boolean caughtUp;
    /** on its way to a trough or eating: following waits while its owner is near (EatGoal) */
    boolean eating;
    /** a heal of its own (regeneration): Hearty does not learn from it */
    public boolean selfHeal;

    /** owner and roster id (null for a showcase drekki), its hatchery, and whether it is checked out (spec, Checkout) */
    private java.util.UUID owner, drekkiId;
    private BlockPos home;
    private boolean out;
    /** client side: smoothed ground speed in blocks per second, for the loop */
    private double speedAvg;

    /** Per-entity loop offset so a row does not move in lockstep. Zero for showcase rows, which should. */
    public float phase;
    /** client side: the loop phase last seen by the particle tick, for one-shot bursts */
    public double lastF = -1;
    private Vec3 laneStart;
    private double laneDist;
    private double laneTime;

    public DrekkiEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STAGE, 3); b.define(LINE, 0); b.define(DIET, 0); b.define(ASTRAL, 1); b.define(HORN, 0); b.define(HAT, 0); b.define(BARDING, 0); b.define(DYE, -1); b.define(ANIM, 0); b.define(LANE, 0f); b.define(OWNED, false); b.define(PET, 0); b.define(SLEEPING, false); b.define(OWNER, java.util.Optional.empty());
        b.define(FOOD, 10f); b.define(MOOD, 50f); b.define(BAND, 2); b.define(PET_LEFT, 0); b.define(KIN, 0); b.define(GHOST, false); b.define(STANCE, 1); b.define(ACTION, 0); b.define(REVIVING, false); b.define(TRAITS, new CompoundTag());
    }

    public int stage() { return entityData.get(STAGE); }
    public int line() { return entityData.get(LINE); }
    public int diet() { return entityData.get(DIET); }
    public int astral() { return entityData.get(ASTRAL); }
    public int horn() { return entityData.get(HORN); }
    /** Gear.HATS index, 0 none */
    public int hat() { return entityData.get(HAT); }
    /** Gear.BARDING tier, 0 none; drawn at stage 3 only */
    public int barding() { return entityData.get(BARDING); }
    /** the barding's dye as RGB, -1 undyed */
    public int dye() { return entityData.get(DYE); }
    public void setGear(int hat, int barding, int dye) { entityData.set(HAT, hat); entityData.set(BARDING, barding); entityData.set(DYE, dye); }
    public Anim anim() {
        if (!entityData.get(OWNED)) return Anim.of(entityData.get(ANIM));
        if (entityData.get(ACTION) > 0) return Anim.of(entityData.get(ACTION) - 1);
        if (entityData.get(SLEEPING)) return Anim.SLEEP;
        return speedAvg < 0.15 ? Anim.IDLE : speedAvg < 2.6 ? Anim.WALK : Anim.RUN;
    }

    public float food() { return entityData.get(FOOD); }
    public float mood() { return entityData.get(MOOD); }
    public dev.frosthaven.ranchercompanion.life.Life.Band band() { return dev.frosthaven.ranchercompanion.life.Life.Band.values()[entityData.get(BAND)]; }
    public int petLeft() { return entityData.get(PET_LEFT); }
    public int kin() { return entityData.get(KIN); }
    public boolean ghost() { return entityData.get(GHOST); }
    public boolean reviving() { return entityData.get(REVIVING); }
    public dev.frosthaven.ranchercompanion.life.Life.Stance stance() { return dev.frosthaven.ranchercompanion.life.Life.Stance.values()[entityData.get(STANCE)]; }
    public CompoundTag traitView() { return entityData.get(TRAITS); }
    public net.minecraft.world.SimpleContainer pack() { return pack; }

    /** the server's life to the synched values: kin from the roster, the buffs, the band */
    public void syncLife() {
        if (level().isClientSide) return;
        var l = life;
        int kin = drekkiId == null || getServer() == null ? 0 : dev.frosthaven.ranchercompanion.ranch.Ranch.kin(getServer(), drekkiId);
        boolean pet = l.petted(System.currentTimeMillis());
        double eff = dev.frosthaven.ranchercompanion.life.Life.effective(l.mood, pet, kin);
        entityData.set(FOOD, (float) l.food);
        entityData.set(MOOD, (float) eff);
        entityData.set(BAND, dev.frosthaven.ranchercompanion.life.Life.band(eff, pet, l.food).ordinal());
        entityData.set(PET_LEFT, pet ? (int) ((l.petUntil - System.currentTimeMillis()) / 1000) : 0);
        entityData.set(KIN, kin);
        entityData.set(REVIVING, l.ghost && l.cooldowns.containsKey(dev.frosthaven.ranchercompanion.life.Vitals.REVIVE));
        entityData.set(STANCE, l.stance.ordinal());
        CompoundTag v = new CompoundTag();
        net.minecraft.nbt.ListTag t = new net.minecraft.nbt.ListTag();
        for (String id : l.traits) t.add(net.minecraft.nbt.StringTag.valueOf(id));
        v.put("traits", t);
        CompoundTag pr = new CompoundTag();
        l.progress.forEach(pr::putInt);
        v.put("progress", pr);
        if (l.offer != null) v.putString("offer", l.offer);
        /* the trait index changes with the traits or the ghost state, not with a cooldown */
        if (!t.equals(entityData.get(TRAITS).getList("traits", net.minecraft.nbt.Tag.TAG_STRING)) || l.ghost != entityData.get(GHOST))
            dev.frosthaven.ranchercompanion.trait.Traits.changed();
        /* when each trait is ready again, in game time (the client counts down against its own clock) */
        CompoundTag ready = new CompoundTag();
        long now = dev.frosthaven.ranchercompanion.life.Vitals.now(this);
        for (String id : l.traits) { Long at = l.cooldowns.get(id); if (at != null && at > now) ready.putLong(id, at); }
        v.put("ready", ready);
        if (!v.equals(entityData.get(TRAITS))) entityData.set(TRAITS, v);
        entityData.set(GHOST, l.ghost);
    }

    /** a loop to play over its movement for a while (eating, a bite, a ball); 0 ticks ends it */
    public void act(Anim a, int ticks) { entityData.set(ACTION, ticks > 0 ? a.ordinal() + 1 : 0); actionLeft = ticks; }
    private int actionLeft;

    /** max health by stage, plus Hearty (spec: 10, 20, 30) */
    public void applyStageHealth(boolean heal) {
        var a = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a == null) return;
        double max = dev.frosthaven.ranchercompanion.life.Vitals.maxHealth(stage(), life);
        if (a.getBaseValue() != max) a.setBaseValue(max);
        if (heal || getHealth() > max) setHealth((float) max);
    }

    /** grown into a bigger hitbox: out of any wall it now pokes into */
    public void onGrown() {
        if (level().noCollision(this)) return;
        var p = ownerPlayer();
        if (isOut() && p != null && p.level() == level()) teleportNear(p.blockPosition()); else goHome();
    }

    public boolean isSleepingLoop() { return entityData.get(SLEEPING); }
    public void setSleepingLoop(boolean on) { entityData.set(SLEEPING, on); }

    public java.util.Set<Long> bedding() {
        if (home == null || !level().isLoaded(home)) return null;
        return level().getBlockEntity(home) instanceof dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity h ? h.bedding(stage()) : null;
    }

    public int petTicks() { return entityData.get(PET); }
    public java.util.UUID owner() { return owner != null ? owner : entityData.get(OWNER).orElse(null); }
    public java.util.UUID drekkiId() { return drekkiId; }
    public BlockPos home() { return home; }
    public boolean isOut() { return out; }
    public boolean owned() { return owner != null; }
    /** client and server: a live drekki (owned), as synched */
    public boolean live() { return entityData.get(OWNED); }

    /** makes it a live drekki of this owner: checked in at a hatchery (home) or checked out (home null) */
    public void bind(java.util.UUID owner, java.util.UUID id, BlockPos home) {
        this.owner = owner; this.drekkiId = id; this.home = home; this.out = home == null;
        entityData.set(OWNED, true); entityData.set(OWNER, java.util.Optional.of(owner)); setPersistenceRequired();
    }

    public net.minecraft.world.entity.player.Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }

    /** the pen of its hatchery, or null when the hatchery is not loaded */
    public java.util.Set<Long> pen() {
        if (home == null || !level().isLoaded(home)) return null;
        return level().getBlockEntity(home) instanceof dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity h ? h.pen(stage()) : null;
    }

    public boolean inPen(BlockPos p) {
        java.util.Set<Long> pen = pen();
        if (pen == null) return true;
        for (int dy = -1; dy <= 1; dy++) if (pen.contains(dev.frosthaven.ranchercompanion.ranch.Flood.pack(p.getX(), p.getY() + dy, p.getZ()))) return true;
        return false;
    }

    /** back next to its hatchery by the Placement rule (a drekki stuck outside its pen) */
    public void goHome() {
        if (home == null) return;
        BlockPos p = new dev.frosthaven.ranchercompanion.ranch.Ground(level()).place(home, stage());
        if (p != null) { moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), 0); getNavigation().stop(); }
    }

    /** next to a block (its owner's feet): the first clear spot in a 5x5 around it, as tamed mobs teleport */
    public void teleportNear(BlockPos pos) {
        for (int i = 0; i < 10; i++) {
            int dx = random.nextInt(-3, 4), dz = random.nextInt(-3, 4), dy = random.nextInt(-1, 2);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos p = pos.offset(dx, dy, dz);
            if (net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(this, p) != net.minecraft.world.level.pathfinder.PathType.WALKABLE) continue;
            if (!level().noCollision(this, getBoundingBox().move(p.subtract(blockPosition())))) continue;
            moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
            getNavigation().stop();
            return;
        }
    }
    public float lane() { return entityData.get(LANE); }
    public Lineage lineage() { return Lineage.of(line()); }

    public void setLook(int stage, int line, int diet, int astral, int horn) {
        entityData.set(STAGE, Math.max(1, Math.min(3, stage))); entityData.set(LINE, line); entityData.set(DIET, diet);
        entityData.set(ASTRAL, astral); entityData.set(HORN, horn); refreshDimensions();
    }

    /** Pin a loop; a lane longer than zero makes the travelling loops walk that far forward, then cut back. */
    public void setAnim(Anim anim, float lane) {
        entityData.set(ANIM, anim.ordinal()); entityData.set(LANE, lane); laneStart = null;
    }

    /** Seconds into the loops, shared by the model, the particles and the lane. */
    public double time(float partial) { return (tickCount + partial) / 20.0 + phase; }

    /** client side: the loop time a live drekki fell asleep at (NaN awake; far back when it was asleep as it came into view) */
    private double sleptAt = Double.NaN;

    /** Seconds into the sleep loop: a showcase drekki repeats the page's 8 s loop; a live one lies down once, then stays down. */
    public double sleepTime(double t) {
        if (!live()) return Anim.loop(t, 8) * 8;
        if (Double.isNaN(sleptAt)) sleptAt = t;
        return t - sleptAt;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) refreshDimensions();
        if (ACTION.equals(key) && level().isClientSide && entityData.get(ACTION) > 0) { phase = -tickCount / 20f; lastF = -1; }
        if (SLEEPING.equals(key) && level().isClientSide) sleptAt = !entityData.get(SLEEPING) ? Double.NaN : tickCount == 0 ? -1e9 : time(0);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        return switch (stage()) {
            case 1 -> EntityDimensions.scalable(0.5f, 0.55f);
            case 2 -> EntityDimensions.scalable(1.0f, 1.3f);
            default -> EntityDimensions.scalable(1.5f, 2.3f);
        };
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));
        goalSelector.addGoal(1, new dev.frosthaven.ranchercompanion.combat.CombatGoals.Attack(this));
        goalSelector.addGoal(2, new DrekkiGoals.Follow(this));
        goalSelector.addGoal(3, new EatGoal(this));
        goalSelector.addGoal(4, new DrekkiGoals.Sleep(this));
        goalSelector.addGoal(5, new DrekkiGoals.Stroll(this));
        targetSelector.addGoal(1, new dev.frosthaven.ranchercompanion.combat.CombatGoals.PickTarget(this));
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 6f));
        goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) { return new DrekkiGoals.PenNavigation(this, level); }

    /** a pup takes no damage (spec, Pup release): nothing fights it, so it never needs healing. The void and /kill still
     *  reach it, so a stuck one can be removed */
    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource s) {
        return super.isInvulnerableTo(s) || stage() == 1 && !s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** stage 1 is never a target: no mob picks it (spec, Pup release) */
    @Override
    public boolean canBeSeenAsEnemy() { return stage() > 1 && super.canBeSeenAsEnemy(); }

    @Override
    protected net.minecraft.world.InteractionResult mobInteract(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        var r = DrekkiInteract.interact(this, player, hand);
        return r != null ? r : super.mobInteract(player, hand);
    }

    public void pet() { entityData.set(PET, PET_TICKS); }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        if (!level().isClientSide && owned() && dev.frosthaven.ranchercompanion.ranch.Death.survives(this, cause)) return;
        super.die(cause);
        if (!level().isClientSide && owned()) dev.frosthaven.ranchercompanion.ranch.Death.died(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) speedAvg += (Math.hypot(getX() - xo, getZ() - zo) * 20 - speedAvg) * 0.3;
        else {
            if (petTicks() > 0) entityData.set(PET, petTicks() - 1);
            if (stage() == 1 && isAlive() && getHealth() < getMaxHealth()) setHealth(getMaxHealth());  /* a pup hurt before 0.3.2 */
            if (actionLeft > 0 && --actionLeft == 0) entityData.set(ACTION, 0);
            if (owned() && isAlive()) {
                if (!caughtUp) { caughtUp = true; dev.frosthaven.ranchercompanion.life.Vitals.resume(this); }
                else if (tickCount % 20 == 0) dev.frosthaven.ranchercompanion.life.Vitals.update(this);
            }
        }
        if (!level().isClientSide && lane() > 0 && anim().speed != 0) laneTick();
    }

    /** Moves along the facing at the loop's speed; past the lane's end it cuts back to the start, like the page's loop. */
    private void laneTick() {
        if (laneStart == null) { laneStart = position(); laneDist = 0; laneTime = 0; }
        double v = anim().speedAt(stage(), laneTime);
        laneTime += 0.05; laneDist += v * 0.05;
        if (laneDist > lane()) { laneDist = 0; laneTime = 0; }
        float yaw = getYRot();
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));
        setPos(laneStart.x + fx * laneDist, laneStart.y, laneStart.z + fz * laneDist);
        setYBodyRot(yaw); setYHeadRot(yaw); yRotO = yaw; yBodyRotO = yaw;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Stage", stage()); tag.putInt("Line", line()); tag.putInt("Diet", diet()); tag.putInt("Astral", astral());
        tag.putInt("Horn", horn()); tag.putInt("Hat", hat()); tag.putInt("Barding", barding()); tag.putInt("Dye", dye()); tag.putInt("Anim", entityData.get(ANIM)); tag.putFloat("Lane", lane());
        tag.put("Life", dev.frosthaven.ranchercompanion.life.LifeTag.save(life));
        tag.put("Pack", pack.createTag(registryAccess()));
        if (owner != null) { tag.putUUID("Owner", owner); tag.putUUID("DrekkiId", drekkiId); tag.putBoolean("Out", out); if (home != null) tag.putLong("Home", home.asLong()); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Life")) dev.frosthaven.ranchercompanion.life.LifeTag.load(life, tag.getCompound("Life"));
        if (tag.contains("Pack")) pack.fromTag(tag.getList("Pack", net.minecraft.nbt.Tag.TAG_COMPOUND), registryAccess());
        caughtUp = false;
        if (tag.contains("Stage")) setLook(tag.getInt("Stage"), tag.getInt("Line"), tag.getInt("Diet"), tag.getInt("Astral"), tag.getInt("Horn"));
        if (tag.contains("Hat")) setGear(tag.getInt("Hat"), tag.getInt("Barding"), tag.getInt("Dye"));
        if (tag.contains("Anim")) setAnim(Anim.of(tag.getInt("Anim")), tag.getFloat("Lane"));
        applyStageHealth(false);
        if (tag.hasUUID("Owner")) bind(tag.getUUID("Owner"), tag.getUUID("DrekkiId"), tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null);
        syncLife();
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
