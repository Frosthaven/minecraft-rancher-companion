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
    /** the barding's trim dye (rims), the bag (Gear.BAGS index) and its main and trim dyes: the look of its equipment */
    private static final EntityDataAccessor<Integer> DYE2 = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BAG = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BAG_DYE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> BAG_DYE2 = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
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
    /** its equipment from stage 2 (spec, Drekki screen): barding of its stage, and its accessory (a bag) */
    private net.minecraft.world.item.ItemStack bardingItem = net.minecraft.world.item.ItemStack.EMPTY, accessory = net.minecraft.world.item.ItemStack.EMPTY;
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
        b.define(STAGE, 3); b.define(LINE, 0); b.define(DIET, 0); b.define(ASTRAL, 1); b.define(HORN, 0); b.define(HAT, 0); b.define(BARDING, 0); b.define(DYE, -1); b.define(DYE2, -1); b.define(BAG, 0); b.define(BAG_DYE, -1); b.define(BAG_DYE2, -1); b.define(ANIM, 0); b.define(LANE, 0f); b.define(OWNED, false); b.define(PET, 0); b.define(SLEEPING, false); b.define(OWNER, java.util.Optional.empty());
        b.define(FOOD, 10f); b.define(MOOD, 50f); b.define(BAND, 2); b.define(PET_LEFT, 0); b.define(KIN, 0); b.define(GHOST, false); b.define(STANCE, 1); b.define(ACTION, 0); b.define(REVIVING, false); b.define(TRAITS, new CompoundTag());
    }

    public int stage() { return entityData.get(STAGE); }
    public int line() { return entityData.get(LINE); }
    public int diet() { return entityData.get(DIET); }
    public int astral() { return entityData.get(ASTRAL); }
    public int horn() { return entityData.get(HORN); }
    /** Gear.HATS index, 0 none */
    public int hat() { return entityData.get(HAT); }
    /** Gear.BARDING tier, 0 none; drawn from stage 2 */
    public int barding() { return entityData.get(BARDING); }
    /** the barding's dyes as RGB, -1 undyed: plates (main) and rims (trim) */
    public int dye() { return entityData.get(DYE); }
    public int dye2() { return entityData.get(DYE2); }
    /** Gear.BAGS index of its accessory, 0 none, and the bag's main and trim dyes */
    public int bag() { return entityData.get(BAG); }
    public int bagDye() { return entityData.get(BAG_DYE); }
    public int bagDye2() { return entityData.get(BAG_DYE2); }
    /** the hat and the drawn barding without items (the showcase, the hatchery's portraits) */
    public void setGear(int hat, int barding, int dye) { entityData.set(HAT, hat); entityData.set(BARDING, barding); entityData.set(DYE, dye); }

    public net.minecraft.world.item.ItemStack bardingItem() { return bardingItem; }
    public net.minecraft.world.item.ItemStack accessory() { return accessory; }

    /** wears this barding (EMPTY for none); the look follows */
    public void setBardingItem(net.minecraft.world.item.ItemStack s) {
        bardingItem = s;
        int tier = s.getItem() instanceof dev.frosthaven.ranchercompanion.item.BardingItem b ? b.tier.gearIndex() : 0;
        entityData.set(BARDING, tier);
        entityData.set(DYE, dev.frosthaven.ranchercompanion.item.Dyeing.main(s));
        entityData.set(DYE2, dev.frosthaven.ranchercompanion.item.Dyeing.trim(s));
    }

    /** wears this accessory (EMPTY for none); the look follows */
    public void setAccessory(net.minecraft.world.item.ItemStack s) {
        accessory = s;
        entityData.set(BAG, dev.frosthaven.ranchercompanion.item.DrekkiBag.kindOf(s));
        entityData.set(BAG_DYE, dev.frosthaven.ranchercompanion.item.Dyeing.main(s));
        entityData.set(BAG_DYE2, dev.frosthaven.ranchercompanion.item.Dyeing.trim(s));
    }

    /** the storage of the bag it wears, or null without one (spec, Accessory: bags) */
    public net.neoforged.neoforge.items.IItemHandler bagStorage() {
        return dev.frosthaven.ranchercompanion.item.DrekkiBag.kindOf(accessory) == 0 ? null : dev.frosthaven.ranchercompanion.item.Dyeing.storage(accessory);
    }
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


    /** the server's life to the synched values: kin from the roster, the buffs, the band */
    public void syncLife() {
        if (level().isClientSide) return;
        var l = life;
        int kin = drekkiId == null || getServer() == null ? 0 : dev.frosthaven.ranchercompanion.ranch.Ranch.kin(getServer(), drekkiId);
        boolean pet = l.petted(System.currentTimeMillis());
        double eff = dev.frosthaven.ranchercompanion.life.Life.effective(l.mood, kin);
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

    /** max health by stage, plus Hearty (spec: 10, 30, 50) */
    public void applyStageHealth(boolean heal) {
        var a = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a == null) return;
        double max = dev.frosthaven.ranchercompanion.life.Vitals.maxHealth(stage(), life);
        if (a.getBaseValue() != max) a.setBaseValue(max);
        if (heal || getHealth() > max) setHealth((float) max);
    }

    /** grown into a bigger hitbox: out of any wall it now pokes into */
    /** grown out of its barding (Fledgling barding on a Mature): it goes to its owner, or drops where it stands */
    public void outgrowBarding() {
        if (bardingItem.isEmpty() || DrekkiMenu.fitsBarding(this, bardingItem)) return;
        var old = bardingItem;
        setBardingItem(net.minecraft.world.item.ItemStack.EMPTY);
        var p = ownerPlayer();
        if (p == null || !p.getInventory().add(old)) spawnAtLocation(old);
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.rancher_companion.outgrew_barding", dev.frosthaven.ranchercompanion.life.Vitals.who(this)));
    }

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
    /** to a safe spot 2 to 3 blocks from pos, then up to 6 when none is free there, or nowhere: it stays put rather than
     *  land somewhere that hurts it. It arrives unhurt: no fall carried over, no fire. */
    public void teleportNear(BlockPos pos) {
        for (int i = 0; i < 30; i++) {
            int r = i < 10 ? 3 : 6;  /* a cramped portal room had no spot within 3, and the drekki stayed in the portal */
            int dx = random.nextInt(-r, r + 1), dz = random.nextInt(-r, r + 1), dy = random.nextInt(-1, 2);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos p = pos.offset(dx, dy, dz);
            var box = getBoundingBox().move(p.subtract(blockPosition()));
            if (!safeAt(p, box)) continue;
            boolean jump = distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) > 16 * 16;
            moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
            /* a long jump: players get it fresh. A plain move update reached an owner's game that had already dropped
               the old chunk; its copy there was frozen, never moved, and the owner saw no drekki (after a waystone and
               after a respawn, 2026-11-21) */
            if (jump && isAddedToLevel() && level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.getChunkSource().removeEntity(this);
                sl.getChunkSource().addEntity(this);
            }
            resetFallDistance();
            clearFire();
            getNavigation().stop();
            return;
        }
    }

    /** room to stand, firm ground, and no lava or fire in or around it, unless fire cannot hurt it (immune, or Fire
     *  Resistance) */
    private boolean safeAt(BlockPos p, net.minecraft.world.phys.AABB box) {
        if (!level().noCollision(this, box)) return false;
        if (level().getBlockStates(box).anyMatch(s -> s.is(net.minecraft.tags.BlockTags.PORTALS))) return false;  /* never in a portal */
        var type = net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(this, p);
        boolean fireproof = fireImmune() || hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
        if (fireproof && (type == net.minecraft.world.level.pathfinder.PathType.LAVA || type == net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE
            || type == net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE)) return true;
        if (type != net.minecraft.world.level.pathfinder.PathType.WALKABLE) return false;
        return fireproof || !hasLava(box.inflate(1, 0, 1).expandTowards(0, -1, 0));
    }

    /** lava or fire in the box (the spot, a block around it, and the ground under it) */
    private boolean hasLava(net.minecraft.world.phys.AABB box) {
        return BlockPos.betweenClosedStream(box).anyMatch(b -> level().getFluidState(b).is(net.minecraft.tags.FluidTags.LAVA)
            || level().getBlockState(b).is(net.minecraft.tags.BlockTags.FIRE));
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
        var d = dev.frosthaven.ranchercompanion.look.Dims.of(stage());
        return EntityDimensions.scalable(d.hitW, d.hitH);
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

    /** barding as wolf armor (spec, Barding): while it holds, damage goes into its durability, one point per point of
     *  damage, and the drekki takes none; vanilla's bypasses_wolf_armor damage still reaches it. Done here, as
     *  Wolf.actuallyHurt does it, so a hit the barding takes moves nothing else (mood, events) */
    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (!bardingItem.isEmpty() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_WOLF_ARMOR) && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            bardingItem.hurtAndBreak(net.minecraft.util.Mth.ceil(amount), sl, this, item -> {
                playSound(net.minecraft.sounds.SoundEvents.WOLF_ARMOR_BREAK);
                setBardingItem(net.minecraft.world.item.ItemStack.EMPTY);
            });
            if (bardingItem.isEmpty() || bardingItem.getCount() == 0) setBardingItem(net.minecraft.world.item.ItemStack.EMPTY);
            else playSound(net.minecraft.sounds.SoundEvents.WOLF_ARMOR_DAMAGE);
            dev.frosthaven.ranchercompanion.ranch.Ranch.touch(this);
            return;
        }
        super.actuallyHurt(source, amount);
    }

    /** an owned drekki never goes through a portal itself: its owner's crossing brings it along (Ranch.follow). One
     *  placed on its owner's block, on a portal's surface, was carried back through by Immersive Portals and stuck
     *  there (2026-11-21); that mod asks this before it carries a mob */
    @Override
    public boolean canChangeDimensions(net.minecraft.world.level.Level from, net.minecraft.world.level.Level to) { return !owned() && super.canChangeDimensions(from, to); }

    @Override
    public boolean canUsePortal(boolean allowPassengers) { return !owned() && super.canUsePortal(allowPassengers); }

    /** a pup takes no damage (spec, Pup release): nothing fights it, so it never needs healing. The void and /kill still
     *  reach it, so a stuck one can be removed */
    @Override
    public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource s) {
        if (life.ghost) return !s.is(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);  /* a ghost cannot die again; /kill still removes it */
        return super.isInvulnerableTo(s) || stage() == 1 && !s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /** a ghost that falls out of the world comes back (spec, Ghost drekki) instead of falling forever */
    @Override
    protected void onBelowWorld() {
        if (life.ghost && !level().isClientSide && owned()) { dev.frosthaven.ranchercompanion.ranch.Death.bringBack(this); return; }
        super.onBelowWorld();
    }

    /** stage 1 is never a target: no mob picks it (spec, Pup release) */
    @Override
    public boolean canBeSeenAsEnemy() { return stage() > 1 && !life.ghost && super.canBeSeenAsEnemy(); }  /* no mob targets a pup or a ghost */

    /** a ghost exists for its owner only (spec, Ghost drekki): no other player is sent it */
    @Override
    public boolean broadcastToPlayer(net.minecraft.server.level.ServerPlayer p) {
        return life.ghost ? p.getUUID().equals(owner()) : super.broadcastToPlayer(p);
    }

    /** after it turns into a ghost or back: stop or start sending it to other players, and drop the mobs after it */
    public void ghostChanged() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl) || isRemoved()) return;
        if (life.ghost) for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(48), m -> m.getTarget() == this)) m.setTarget(null);
        sl.getChunkSource().removeEntity(this);
        sl.getChunkSource().addEntity(this);
    }

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
                if (!accessory.isEmpty() && dev.frosthaven.ranchercompanion.item.Bags.sophisticated() && accessory.getItem() instanceof dev.frosthaven.ranchercompanion.item.DrekkiBag)
                    dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.tick(accessory, this);  /* its upgrades act where it is */
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
        tag.putInt("Horn", horn()); tag.putInt("Hat", hat()); tag.putInt("Barding", barding()); tag.putInt("Dye", dye()); tag.putInt("Dye2", dye2()); tag.putInt("Anim", entityData.get(ANIM)); tag.putFloat("Lane", lane());
        tag.put("Life", dev.frosthaven.ranchercompanion.life.LifeTag.save(life));
        if (!bardingItem.isEmpty()) tag.put("BardingItem", bardingItem.save(registryAccess()));
        if (!accessory.isEmpty()) tag.put("Accessory", accessory.save(registryAccess()));
        if (owner != null) { tag.putUUID("Owner", owner); tag.putUUID("DrekkiId", drekkiId); tag.putBoolean("Out", out); if (home != null) tag.putLong("Home", home.asLong()); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Life")) dev.frosthaven.ranchercompanion.life.LifeTag.load(life, tag.getCompound("Life"));

        caughtUp = false;
        if (tag.contains("Stage")) setLook(tag.getInt("Stage"), tag.getInt("Line"), tag.getInt("Diet"), tag.getInt("Astral"), tag.getInt("Horn"));
        if (tag.contains("Hat")) setGear(tag.getInt("Hat"), tag.getInt("Barding"), tag.getInt("Dye"));
        if (tag.contains("Dye2")) entityData.set(DYE2, tag.getInt("Dye2"));
        /* the showcase's drawn bag, without an item */
        if (tag.contains("Bag")) { entityData.set(BAG, tag.getInt("Bag")); entityData.set(BAG_DYE, tag.contains("BagDye") ? tag.getInt("BagDye") : -1); entityData.set(BAG_DYE2, tag.contains("BagDye2") ? tag.getInt("BagDye2") : -1); }
        if (tag.contains("BardingItem")) setBardingItem(net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("BardingItem")));
        if (tag.contains("Accessory")) setAccessory(net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("Accessory")));
        if (tag.contains("Anim")) setAnim(Anim.of(tag.getInt("Anim")), tag.getFloat("Lane"));
        applyStageHealth(false);
        if (tag.hasUUID("Owner")) bind(tag.getUUID("Owner"), tag.getUUID("DrekkiId"), tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null);
        syncLife();
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
