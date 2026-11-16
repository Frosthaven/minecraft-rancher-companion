package dev.frosthaven.ranchercompanion.entity;

import dev.frosthaven.ranchercompanion.look.Anim;
import dev.frosthaven.ranchercompanion.look.Lineage;
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
        b.define(STAGE, 3); b.define(LINE, 0); b.define(DIET, 0); b.define(ASTRAL, 1); b.define(HORN, 0); b.define(HAT, 0); b.define(BARDING, 0); b.define(DYE, -1); b.define(ANIM, 0); b.define(LANE, 0f);
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
    public Anim anim() { return Anim.of(entityData.get(ANIM)); }
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
    protected void registerGoals() {}

    @Override
    public void tick() {
        super.tick();
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
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Stage")) setLook(tag.getInt("Stage"), tag.getInt("Line"), tag.getInt("Diet"), tag.getInt("Astral"), tag.getInt("Horn"));
        if (tag.contains("Hat")) setGear(tag.getInt("Hat"), tag.getInt("Barding"), tag.getInt("Dye"));
        if (tag.contains("Anim")) setAnim(Anim.of(tag.getInt("Anim")), tag.getFloat("Lane"));
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
