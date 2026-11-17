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
        if (entityData.get(SLEEPING)) return Anim.SLEEP;
        return speedAvg < 0.15 ? Anim.IDLE : speedAvg < 2.6 ? Anim.WALK : Anim.RUN;
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

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) refreshDimensions();
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
        goalSelector.addGoal(1, new DrekkiGoals.Follow(this));
        goalSelector.addGoal(2, new DrekkiGoals.Sleep(this));
        goalSelector.addGoal(3, new DrekkiGoals.Stroll(this));
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 6f));
        goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this));
    }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) { return new DrekkiGoals.PenNavigation(this, level); }

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
        super.die(cause);
        if (!level().isClientSide && owned()) dev.frosthaven.ranchercompanion.ranch.Ranch.died(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) speedAvg += (Math.hypot(getX() - xo, getZ() - zo) * 20 - speedAvg) * 0.3;
        else if (petTicks() > 0) entityData.set(PET, petTicks() - 1);
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
        if (owner != null) { tag.putUUID("Owner", owner); tag.putUUID("DrekkiId", drekkiId); tag.putBoolean("Out", out); if (home != null) tag.putLong("Home", home.asLong()); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Stage")) setLook(tag.getInt("Stage"), tag.getInt("Line"), tag.getInt("Diet"), tag.getInt("Astral"), tag.getInt("Horn"));
        if (tag.contains("Hat")) setGear(tag.getInt("Hat"), tag.getInt("Barding"), tag.getInt("Dye"));
        if (tag.contains("Anim")) setAnim(Anim.of(tag.getInt("Anim")), tag.getFloat("Lane"));
        if (tag.hasUUID("Owner")) bind(tag.getUUID("Owner"), tag.getUUID("DrekkiId"), tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null);
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
