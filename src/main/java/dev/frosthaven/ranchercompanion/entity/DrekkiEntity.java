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
public class DrekkiEntity extends PathfinderMob implements net.minecraft.world.entity.HasCustomInventoryScreen {
    private static final EntityDataAccessor<Integer> STAGE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DIET = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ASTRAL = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HORN = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    /** in its home terrain now: its mood is one band up (the screen's mood reasons) */
    private static final EntityDataAccessor<Boolean> HOME = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** the second eye's astral (spec, Breeding: eyes), 0 when both eyes share {@link #ASTRAL} */
    private static final EntityDataAccessor<Integer> ASTRAL2 = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
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
    /** Soulflame's ticks left (game time; the clock is the overworld's day time), 0 for none */
    private static final EntityDataAccessor<Integer> SOULFLAME = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> KIN = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> GHOST = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** a ghost whose tombstone's candle burns: only then does it shed soul-fire flames (Shane, 2026-11-26) */
    private static final EntityDataAccessor<Boolean> GHOST_FLAME = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> STANCE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    /* Synced values are numbered in the order they are declared, and a game and a server must agree on the numbers.
       Declare a NEW value LAST, after every other one in this class: then a game one release ahead of its server still
       reads the server's drekki (players update minutes before the server restarts). MOVE went in the middle in 0.4.6,
       every later value moved up one, and each game on 0.4.6 lost its connection to the 0.4.5 server at the first
       drekki it was sent (2026-11-21). Never reorder or remove one for the same reason. */
    /** 0 checked in, 1 checked out and following, 2 checked out and staying (the drekki screen's Stay and Follow) */
    private static final EntityDataAccessor<Integer> MOVE = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    /** the traits tab: learned trait ids and counted progress */
    private static final EntityDataAccessor<CompoundTag> TRAITS = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.COMPOUND_TAG);
    /** 0.4.13: its job (spec, Go here: Employed) for the drekki screen: 0 not employed, else its role (Seats.EMPLOYED,
     *  CONDUCTOR, STOCK_KEEPER). Kept up by Ranch from the roster; not saved */
    private static final EntityDataAccessor<Integer> JOB = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    /** its barding is enchanted: the glint on the plates. Declared last: a synched value is never put in the middle
     *  (see CLAUDE.md, Release: network compatibility) */
    private static final EntityDataAccessor<Boolean> BARDING_GLINT = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** 0.5.0: a Mature wears a saddle (its saddle slot): drawn, and it can be ridden. Declared last, as above */
    private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.BOOLEAN);
    /** 0.5.0: the biome category it hatched in (life.Places.CATEGORIES index), which tints its feet for life. Declared last */
    private static final EntityDataAccessor<Integer> BIRTH = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.INT);
    /** the barding it wears, with its wear and enchantments, for every game (another mod's health bar reads its
     *  durability through bardingItem() on the client). Declared last: see CLAUDE.md, network compatibility */
    private static final EntityDataAccessor<net.minecraft.world.item.ItemStack> BARDING_STACK = SynchedEntityData.defineId(DrekkiEntity.class, EntityDataSerializers.ITEM_STACK);

    /** its life (server: the truth, saved under "Life"; client: unused, the synched values above stand in) */
    public final dev.frosthaven.ranchercompanion.life.Life life = new dev.frosthaven.ranchercompanion.life.Life();
    /** its equipment from stage 2 (spec, Drekki screen): barding of its stage, and its accessory (a bag) */
    private net.minecraft.world.item.ItemStack bardingItem = net.minecraft.world.item.ItemStack.EMPTY, accessory = net.minecraft.world.item.ItemStack.EMPTY,
        saddle = net.minecraft.world.item.ItemStack.EMPTY;
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
        /* its hitbox from its stage, not the entity type's size. A game is sent only the values that differ from the
           defaults, so for a Mature (stage 3, the default) no stage change ever came and its hitbox stayed the type's
           1.5 by 2.3 on that game, against 0.6 by 1.725 on the server: a ridden one was carried back at every block's
           lip, where the two sides met the step at different moments (2026-11-24) */
        refreshDimensions();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 30).add(Attributes.MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(STAGE, 3); b.define(LINE, 0); b.define(DIET, 0); b.define(ASTRAL, 1); b.define(ASTRAL2, 0); b.define(HOME, false); b.define(HORN, 0); b.define(HAT, 0); b.define(BARDING, 0); b.define(DYE, -1); b.define(DYE2, -1); b.define(BAG, 0); b.define(BAG_DYE, -1); b.define(BAG_DYE2, -1); b.define(ANIM, 0); b.define(LANE, 0f); b.define(OWNED, false); b.define(PET, 0); b.define(SLEEPING, false); b.define(OWNER, java.util.Optional.empty());
        b.define(MOVE, 0);
        b.define(FOOD, 10f); b.define(MOOD, 50f); b.define(BAND, 2); b.define(PET_LEFT, 0); b.define(SOULFLAME, 0); b.define(KIN, 0); b.define(GHOST, false); b.define(GHOST_FLAME, false); b.define(STANCE, 1); b.define(ACTION, 0);  b.define(TRAITS, new CompoundTag()); b.define(JOB, 0);
        b.define(BARDING_GLINT, false);
        b.define(SADDLED, false);
        b.define(BIRTH, dev.frosthaven.ranchercompanion.look.FootTint.PLAINS);
        b.define(BARDING_STACK, net.minecraft.world.item.ItemStack.EMPTY);
    }

    public int stage() { return entityData.get(STAGE); }
    public int line() { return entityData.get(LINE); }
    public int diet() { return entityData.get(DIET); }
    /** its astral: both eyes, or the first (left) eye of a two-eyed drekki */
    public int astral() { return entityData.get(ASTRAL); }
    /** a two-eyed drekki's second (right) eye, 0 for one astral; both are its home (spec, Breeding: eyes) */
    public int astral2() { return entityData.get(ASTRAL2); }
    public void setAstral2(int a) { entityData.set(ASTRAL2, a == astral() ? 0 : a); }
    /** one of its astrals: the one, or either eye of a two-eyed drekki */
    public boolean hasAstral(int a) { return a != dev.frosthaven.ranchercompanion.look.Astral.DORMANT && (a == astral() || a == astral2()); }
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
    /** the barding as every game has it (the synced copy; a game's bardingItem() is this) */
    public net.minecraft.world.item.ItemStack bardingSeen() { return entityData.get(BARDING_STACK); }
    public boolean bardingGlint() { return entityData.get(BARDING_GLINT); }
    public net.minecraft.world.item.ItemStack accessory() { return accessory; }

    /** the barding as it is now to every game that tracks this drekki. Called where the stack changes (put on, taken
     *  off, loaded, worn by a hit, mended), never on a timer: nothing is sent while nothing changes */
    public void syncBarding() { if (!level().isClientSide) entityData.set(BARDING_STACK, bardingItem.copy()); }

    private static final net.minecraft.resources.ResourceLocation BARDING_ARMOR = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(dev.frosthaven.ranchercompanion.RancherCompanion.ID, "barding");

    /** wears this barding (EMPTY for none); the look follows */
    public void setBardingItem(net.minecraft.world.item.ItemStack s) {
        s = dev.frosthaven.ranchercompanion.item.BardingItem.current(s);  /* a legacy Fledgling id becomes the one barding */
        bardingItem = s;
        syncBarding();
        if (!level().isClientSide) {
            /* its armor points with it (item/Barding.armor): a modifier that is not saved, put on again when the barding
               is (a load included), so none stacks; off with the barding */
            var armor = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR);
            if (armor != null) {
                armor.removeModifier(BARDING_ARMOR);
                /* by the wearer's stage (one barding for both stages since 0.6.0: the item holds the Mature's durability) */
                if (s.getItem() instanceof dev.frosthaven.ranchercompanion.item.BardingItem bi)
                    armor.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(BARDING_ARMOR, dev.frosthaven.ranchercompanion.item.Barding.armor(bi.tier.durability(stage())), net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
            }
        }
        int tier = s.getItem() instanceof dev.frosthaven.ranchercompanion.item.BardingItem b ? b.tier.gearIndex() : 0;
        entityData.set(BARDING, tier);
        entityData.set(BARDING_GLINT, s.isEnchanted());
        entityData.set(DYE, dev.frosthaven.ranchercompanion.item.Dyeing.main(s));
        entityData.set(DYE2, dev.frosthaven.ranchercompanion.item.Dyeing.trim(s));
    }

    /** the biome category it hatched in (spec, Biome affinity: the foot tint is set at birth) */
    public int birth() { return entityData.get(BIRTH); }
    public void setBirth(int category) { entityData.set(BIRTH, category); birthKnown = true; }
    /** server: false for a drekki saved before 0.5.0, which takes its hatchery's biome at its first tick */
    private boolean birthKnown;

    private void settleBirth() {
        birthKnown = true;
        if (drekkiId == null || getServer() == null) return;
        var e = dev.frosthaven.ranchercompanion.ranch.Ranch.roster(getServer()).get(drekkiId);
        net.minecraft.core.GlobalPos at = e == null ? null : e.base != null ? e.base : e.home;
        var where = at == null ? null : getServer().getLevel(at.dimension());
        setBirth(dev.frosthaven.ranchercompanion.life.Places.CATEGORIES.indexOf(where == null
            ? dev.frosthaven.ranchercompanion.life.Places.category(level(), blockPosition()) : dev.frosthaven.ranchercompanion.life.Places.category(where, at.pos())));
        dev.frosthaven.ranchercompanion.ranch.Ranch.touch(this);
    }

    public net.minecraft.world.item.ItemStack saddle() { return saddle; }
    /** a saddle is drawn: the one a Mature wears; a showcase drekki (no owner) shows the page's */
    public boolean saddled() { return stage() >= 3 && (entityData.get(SADDLED) || !live()); }

    /** wears this saddle (EMPTY for none; a Mature only): drawn, and its owner can ride it. Taken off under a rider, the rider gets down */
    public void setSaddle(net.minecraft.world.item.ItemStack s) {
        saddle = s;
        entityData.set(SADDLED, !s.isEmpty());
        if (s.isEmpty() && !level().isClientSide) ejectPassengers();
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
        if (entityData.get(ACTION) > 0) {
            Anim act = Anim.of(entityData.get(ACTION) - 1);
            /* an attack's loop stands still. The fight ends the loop when it ends (CombatGoals.Attack.stop); this is the
               net under it: one that travels moves its legs, so it never slides in a bite's pose (2026-11-24) */
            boolean stands = act == Anim.BITE || act == Anim.BALL || act == Anim.BREATH;
            if (!stands || speedAvg < 1 || isPassenger() || isVehicle()) return act;
            return speedAvg < 2.6 ? Anim.WALK : Anim.RUN;
        }
        if (entityData.get(SLEEPING)) return Anim.SLEEP;
        if (isVehicle() && rideable()) return Riding.loop(this, speedAvg, riseAvg);
        if (isPassenger()) return Anim.IDLE;  /* a train's speed is not its own: no walk or run (bobbing) on a moving seat */
        return speedAvg < 0.15 ? Anim.IDLE : speedAvg < 2.6 ? Anim.WALK : Anim.RUN;
    }

    public float food() { return entityData.get(FOOD); }
    public float mood() { return entityData.get(MOOD); }
    public dev.frosthaven.ranchercompanion.life.Life.Band band() { return dev.frosthaven.ranchercompanion.life.Life.Band.values()[entityData.get(BAND)]; }
    public int petLeft() { return entityData.get(PET_LEFT); }
    public int soulflameLeft() { return entityData.get(SOULFLAME); }
    public boolean homeLift() { return entityData.get(HOME); }
    public int kin() { return entityData.get(KIN); }
    public boolean ghost() { return entityData.get(GHOST); }

    /** a ghost and players pass through each other (Shane, 2026-11-26): players do not push it, it does not push them */
    @Override public void push(net.minecraft.world.entity.Entity by) { if (!(ghost() && by instanceof net.minecraft.world.entity.player.Player)) super.push(by); }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) { if (!(ghost() && e instanceof net.minecraft.world.entity.player.Player)) super.doPush(e); }
    /** a screen's stand-in model (never in the world): drawn as a ghost */
    public void setGhostView(boolean g) { entityData.set(GHOST, g); }
    public boolean ghostFlame() { return entityData.get(GHOST_FLAME); }
    public void setGhostFlame(boolean f) { if (entityData.get(GHOST_FLAME) != f) entityData.set(GHOST_FLAME, f); }
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
        /* home lifts the band one tier, as Recently pet does; a drekki Miserable before that lift has no home (spec, Mood) */
        boolean home = dev.frosthaven.ranchercompanion.life.Vitals.inHomeTerrain(this)
            && dev.frosthaven.ranchercompanion.life.Life.band(eff, pet, l.food) != dev.frosthaven.ranchercompanion.life.Life.Band.MISERABLE;
        long day = dev.frosthaven.ranchercompanion.ranch.Roster.now(getServer());
        boolean soul = l.soulflame(day);
        entityData.set(BAND, dev.frosthaven.ranchercompanion.life.Life.band(eff, pet, home, soul, l.food).ordinal());
        entityData.set(SOULFLAME, soul ? (int) Math.min(Integer.MAX_VALUE, l.soulflameUntil - day) : 0);
        entityData.set(HOME, home);
        entityData.set(PET_LEFT, pet ? (int) ((l.petUntil - System.currentTimeMillis()) / 1000) : 0);
        entityData.set(KIN, kin);
        entityData.set(STANCE, l.stance.ordinal());
        CompoundTag v = new CompoundTag();
        net.minecraft.nbt.ListTag t = new net.minecraft.nbt.ListTag();
        for (String id : l.traits) t.add(net.minecraft.nbt.StringTag.valueOf(id));
        v.put("traits", t);
        CompoundTag pr = new CompoundTag();
        l.progress.forEach(pr::putInt);
        v.put("progress", pr);
        if (l.offer != null) v.putString("offer", l.offer);
        if (l.horizons) v.putBoolean("horizons", true);
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
    /** sent again when it is the same action as the one still on (a second bite): each game starts the loop over */
    public void act(Anim a, int ticks) { entityData.set(ACTION, ticks > 0 ? a.ordinal() + 1 : 0, true); actionLeft = ticks; }
    private int actionLeft;
    /** ends this action's loop now, if it is the one playing (an attack whose target died: back to its normal loops) */
    public void endAct(Anim a) { if (actionLeft > 0 && entityData.get(ACTION) == a.ordinal() + 1) { actionLeft = 0; entityData.set(ACTION, 0); } }

    /** max health by stage, plus Hearty (spec: 10, 30, 50) */
    public void applyStageHealth(boolean heal) {
        var a = getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (a == null) return;
        double max = dev.frosthaven.ranchercompanion.life.Vitals.maxHealth(stage(), life);
        if (a.getBaseValue() != max) a.setBaseValue(max);
        if (heal || getHealth() > max) setHealth((float) max);
    }

    /** grown into a bigger hitbox: out of any wall it now pokes into */
    /** grown out of its barding (barding it no longer fits; none since 0.6.0, when both stages wear the same barding): it
     *  goes to its owner, or drops where it stands */
    public void outgrowBarding() {
        if (bardingItem.isEmpty()) return;
        if (DrekkiMenu.fitsBarding(this, bardingItem)) { setBardingItem(bardingItem); return; }  /* still worn: its armor points follow the new stage */
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
        this.owner = owner; this.drekkiId = id; this.home = home; this.out = home == null; this.tombPen = null;
        entityData.set(MOVE, out ? 1 : 0);
        if (!out && !level().isClientSide) ejectPassengers();  /* checked in, it carries no rider */
        entityData.set(OWNED, true); entityData.set(OWNER, java.util.Optional.of(owner)); setPersistenceRequired();
    }

    /** told to stay (spec, Stay and follow): it does not walk, follow or catch up; Ranch keeps the spot */
    public boolean isStaying() { return entityData.get(MOVE) == 2; }
    public int move() { return entityData.get(MOVE); }
    /** 0 not employed, else its role (Seats.EMPLOYED, CONDUCTOR, STOCK_KEEPER) */
    public int job() { return entityData.get(JOB); }
    /** at work it carries no rider */
    public void setJob(int j) { entityData.set(JOB, j); if (j > 0) ejectPassengers(); }
    public void setStaying(boolean on) { entityData.set(MOVE, !out ? 0 : on ? 2 : 1); if (on) getNavigation().stop(); }

    public net.minecraft.world.entity.player.Player ownerPlayer() { return owner == null ? null : level().getPlayerByUUID(owner); }

    /** the pen of its hatchery, or null when the hatchery is not loaded */
    public java.util.Set<Long> pen() {
        if (home == null || !level().isLoaded(home)) return null;
        var be = level().getBlockEntity(home);
        if (be instanceof dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity) return tombPen();  /* a ghost at its tombstone */
        return be instanceof dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity h ? h.pen(stage()) : null;
    }

    private java.util.Set<Long> tombPen;

    /** a summoned ghost's ground: within 3 blocks of its tombstone (spec, Tombstone: the way back) */
    private java.util.Set<Long> tombPen() {
        if (tombPen == null) {
            java.util.Set<Long> s = new java.util.HashSet<>();
            int r = dev.frosthaven.ranchercompanion.ranch.Ground.ANCHOR;
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -1; dy <= 1; dy++)
                s.add(dev.frosthaven.ranchercompanion.ranch.Flood.pack(home.getX() + dx, home.getY() + dy, home.getZ() + dz));
            for (int dy = -1; dy <= 2; dy++) s.remove(dev.frosthaven.ranchercompanion.ranch.Flood.pack(home.getX(), home.getY() + dy, home.getZ()));  /* never on the tombstone itself (Shane, 2026-11-26) */
            tombPen = s;
        }
        return tombPen;
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
        if (p != null) { moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), 0); sendFresh(); getNavigation().stop(); }
    }

    @Override
    public void startSeenByPlayer(net.minecraft.server.level.ServerPlayer p) {
        super.startSeenByPlayer(p);
        dev.frosthaven.ranchercompanion.Debug.log("server: {} sent to {} ({} blocks away)", dev.frosthaven.ranchercompanion.Debug.of(this), p.getName().getString(), Math.round(distanceTo(p)));
    }

    @Override
    public void stopSeenByPlayer(net.minecraft.server.level.ServerPlayer p) {
        super.stopSeenByPlayer(p);
        dev.frosthaven.ranchercompanion.Debug.log("server: {} taken from {}", dev.frosthaven.ranchercompanion.Debug.of(this), p.getName().getString());
    }

    /** onto an exact spot, with no check of the ground: the whistle's last resort, its owner's own position (in the
     *  air it falls unhurt) */
    /** set by a catch-up's landing, cleared once it has a way to walk to its owner: until then, with no way, it
     *  jumps again (DrekkiGoals.Follow) */
    public boolean landed;

    public void jumpTo(Vec3 at) {
        ejectPassengers();
        if (!fireproof() && hasLava(getBoundingBox().move(at.subtract(position())))) return;  /* not into lava or fire itself */
        moveTo(at.x, at.y, at.z, getYRot(), getXRot());
        sendFresh();
        resetFallDistance(); clearFire(); getNavigation().stop();
    }

    /** after a jump (a catch-up, a return to its pen): players get it as new, not as a move update. After a catch-up an
     *  owner's game showed no drekki while another player's did (after a waystone, and after outrunning it at home,
     *  2026-11-21). The cause on the owner's side is not known; a new copy does not depend on the old one */
    private void sendFresh() {
        if (!isAddedToLevel() || !(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        dev.frosthaven.ranchercompanion.Debug.log("server: {} jumped, sending it to players as new", dev.frosthaven.ranchercompanion.Debug.of(this));
        sl.getChunkSource().removeEntity(this);
        sl.getChunkSource().addEntity(this);
    }

    /** next to a block (its owner's feet): the first clear spot in a 5x5 around it, as tamed mobs teleport */
    /** to a safe spot 2 to 3 blocks from pos, then up to 6 when none is free there, or nowhere: it stays put rather than
     *  land somewhere that hurts it. It arrives unhurt: no fall carried over, no fire. */
    public boolean teleportNear(BlockPos pos) { return teleportNear(pos, null); }

    /** the same, next to who: when they stand in a portal (they just came out of it), only on their side of it */
    public boolean teleportNear(net.minecraft.world.entity.Entity who) { return teleportNear(who.blockPosition(), who); }

    private boolean teleportNear(BlockPos pos, net.minecraft.world.entity.Entity who) {
        var side = who == null ? null : portalSide(who);
        for (int i = 0; i < 30; i++) {
            int r = i < 10 ? 3 : 6;  /* a cramped portal room had no spot within 3, and the drekki stayed in the portal */
            int dx = random.nextInt(-r, r + 1), dz = random.nextInt(-r, r + 1), dy = random.nextInt(-1, 2);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
            BlockPos p = pos.offset(dx, dy, dz);
            if (side != null && !side.test(p)) { dev.frosthaven.ranchercompanion.Debug.log("server: spot {} skipped, the other side of the owner's portal", p.toShortString()); continue; }
            var box = getBoundingBox().move(p.subtract(blockPosition()));
            if (!safeAt(p, box)) continue;
            /* where they can see it, while such a spot turns up (the first 20 tries): one landed a block down behind
               a ledge, 3 blocks from its owner, too close to walk over, and the owner saw no drekki (2026-11-21) */
            if (who != null && i < 20 && level().clip(new net.minecraft.world.level.ClipContext(who.getEyePosition(), box.getCenter(),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, this)).getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                dev.frosthaven.ranchercompanion.Debug.log("server: spot {} skipped, out of the owner's sight", p.toShortString());
                continue;
            }
            dev.frosthaven.ranchercompanion.Debug.log("server: {} lands at {}", dev.frosthaven.ranchercompanion.Debug.of(this), p.toShortString());
            landed = true;
            moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
            sendFresh();
            resetFallDistance();
            clearFire();
            getNavigation().stop();
            return true;
        }
        return false;
    }

    /** a portal block: the game's own, and a portal mod's by its block's name (Immersive Portals fills a frame with
     *  immersive_portals:nether_portal_block, which is in no portal tag) */
    private static boolean portal(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.PORTALS) || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath().contains("portal");
    }

    /** who stands in a portal block: the spots on their side of the portal's surface (the block's middle), or null when
     *  they stand in none or the side cannot be told. A drekki landed behind the frame its owner had just walked out
     *  of, walled off from them (2026-11-21). The portal runs along the axis with a portal block next to this one, or
     *  for a portal one block wide, with the frame on both sides */
    private java.util.function.Predicate<BlockPos> portalSide(net.minecraft.world.entity.Entity who) {
        BlockPos b = who.blockPosition();
        if (!portal(level().getBlockState(b))) return null;
        var X = net.minecraft.core.Direction.Axis.X; var Z = net.minecraft.core.Direction.Axis.Z;
        boolean px = portal(level().getBlockState(b.east())) || portal(level().getBlockState(b.west()));
        boolean pz = portal(level().getBlockState(b.north())) || portal(level().getBlockState(b.south()));
        if (px == pz) {  /* one block wide: the frame is solid on both sides along the portal */
            px = !level().getBlockState(b.east()).getCollisionShape(level(), b.east()).isEmpty() && !level().getBlockState(b.west()).getCollisionShape(level(), b.west()).isEmpty();
            pz = !level().getBlockState(b.north()).getCollisionShape(level(), b.north()).isEmpty() && !level().getBlockState(b.south()).getCollisionShape(level(), b.south()).isEmpty();
            if (px == pz) return null;
        }
        var across = px ? Z : X;
        double surface = b.get(across) + 0.5, at = who.position().get(across) - surface;
        if (Math.abs(at) < 1e-3) return null;
        return p -> (p.get(across) + 0.5 - surface) * at >= 0;
    }

    /** room to stand, firm ground, and no lava or fire in the spot itself, unless fire cannot hurt it (immune, or Fire
     *  Resistance) */
    private boolean safeAt(BlockPos p, net.minecraft.world.phys.AABB box) {
        if (!level().noCollision(this, box)) return false;
        if (level().getBlockStates(box).anyMatch(DrekkiEntity::portal)) return false;  /* never in a portal */
        var type = net.minecraft.world.level.pathfinder.WalkNodeEvaluator.getPathTypeStatic(this, p);
        boolean fireproof = fireproof();
        if (fireproof && (type == net.minecraft.world.level.pathfinder.PathType.LAVA || type == net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE
            || type == net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE)) return true;
        /* firm ground that is not itself burning or under lava. Lava or fire next to the spot does not rule it out
           (DANGER_FIRE is the game's name for that): any block not on fire or in lava will do (decided 2026-11-21) */
        if (type != net.minecraft.world.level.pathfinder.PathType.WALKABLE && type != net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE) return false;
        return fireproof || !hasLava(box);
    }

    /** fire and lava are no danger to it: immune by nature, or under Fire Resistance (a trait's, a potion's) */
    /** where the whistle sent it (GoHere), server only and not saved: a restart leaves it where it got to */
    public GoHere.Target goHere;
    /** employed and off its seat this long (ticks); at 40 its job is over (Ranch.workTick) */
    public int offJob;

    /** room for it to stand on the block above the one under pos, out of lava and fire (Go here's exact spot) */
    public boolean fitsAt(BlockPos p) {
        var box = getDimensions(getPose()).makeBoundingBox(Vec3.atBottomCenterOf(p));
        return level().noCollision(this, box) && (fireproof() || !hasLava(box));
    }

    public boolean fireproof() { return fireImmune() || hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE); }

    /** it never walks into lava or fire, whatever the detour, unless they are no danger to it. Lava cannot hurt it
     *  (the safety rule), but it keeps out all the same (decided 2026-11-21) */
    @Override
    public float getPathfindingMalus(net.minecraft.world.level.pathfinder.PathType type) {
        boolean hot = type == net.minecraft.world.level.pathfinder.PathType.LAVA || type == net.minecraft.world.level.pathfinder.PathType.DAMAGE_FIRE
            || type == net.minecraft.world.level.pathfinder.PathType.DANGER_FIRE;
        if (hot && !isVehicle()) return fireproof() ? 0 : -1;
        return super.getPathfindingMalus(type);
    }

    /** lava or fire in the box */
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
    /** the part of a tick a loop time is into */
    public float partial(double t) { return (float) Math.max(0, Math.min(1, (t - phase) * 20 - tickCount)); }

    /** client side: the loop time a live drekki fell asleep at (NaN awake; far back when it was asleep as it came into view) */
    private double sleptAt = Double.NaN;

    /** Seconds into the sleep loop: a showcase drekki repeats the page's 8 s loop; a live one lies down once, then stays down. */
    /** client: the sit pose (DrekkiAnimations.sit), 0 standing to 1 sitting: told to stay, or on a seat, and awake. It
     *  eases in over SIT_S seconds and back out over STAND_S when it is told to follow or leaves the seat */
    private boolean sitting;
    private double sitFrom, sitAt = Double.NaN;
    private static final double SIT_S = 0.8, STAND_S = 0.5;

    public double sitBlend(double t) {
        boolean now = live() && (isStaying() || isPassenger()) && !entityData.get(SLEEPING);
        if (Double.isNaN(sitAt)) { sitting = now; sitFrom = now ? 1 : 0; sitAt = t; }  /* first seen: already in its pose */
        if (now != sitting) { sitFrom = sitAt(t); sitting = now; sitAt = t; }
        return sitAt(t);
    }

    private double sitAt(double t) {
        double u = Math.max(0, Math.min(1, (t - sitAt) / (sitting ? SIT_S : STAND_S))), e = 0.5 - 0.5 * Math.cos(u * Math.PI);
        return sitFrom + ((sitting ? 1 : 0) - sitFrom) * e;
    }

    public double sleepTime(double t) {
        if (!live()) return Anim.loop(t, 8) * 8;
        if (Double.isNaN(sleptAt)) sleptAt = t;
        return t - sleptAt;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (STAGE.equals(key)) refreshDimensions();
        if (BARDING_STACK.equals(key) && level().isClientSide) bardingItem = entityData.get(BARDING_STACK).copy();
        if (ACTION.equals(key) && level().isClientSide && entityData.get(ACTION) > 0) {
            phase = -tickCount / 20f; lastF = -1;
            /* eating: past the loop's walk up to the feeder, which the drekki has done on its own feet */
            if (entityData.get(ACTION) - 1 == Anim.FEED.ordinal()) phase += (float) Anim.FEED_W;
        }
        if (SLEEPING.equals(key) && level().isClientSide) sleptAt = !entityData.get(SLEEPING) ? Double.NaN : tickCount == 0 ? -1e9 : time(0);
    }

    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        var d = dev.frosthaven.ranchercompanion.look.Dims.of(stage());
        return EntityDimensions.scalable(d.hitW, d.hitH);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new net.minecraft.world.entity.ai.goal.FloatGoal(this));  /* knocked into lava it floats, to get out */
        goalSelector.addGoal(0, new GoHere.Walk(this));  /* the whistle's Go here: before following, eating and fights */
        goalSelector.addGoal(1, new dev.frosthaven.ranchercompanion.combat.CombatGoals.Attack(this));
        goalSelector.addGoal(2, new DrekkiGoals.Follow(this));
        goalSelector.addGoal(3, new EatGoal(this));
        goalSelector.addGoal(4, new DrekkiGoals.Sleep(this));
        goalSelector.addGoal(5, new DrekkiGoals.Stroll(this));
        targetSelector.addGoal(1, new dev.frosthaven.ranchercompanion.combat.CombatGoals.PickTarget(this));
        /* not at work on a train: Create turns it toward where the train goes */
        goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 6f) {
            @Override public boolean canUse() { return !steered() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !steered() && super.canContinueToUse(); }
        });
        goalSelector.addGoal(6, new net.minecraft.world.entity.ai.goal.RandomLookAroundGoal(this) {
            @Override public boolean canUse() { return !steered() && super.canUse(); }
            @Override public boolean canContinueToUse() { return !steered() && super.canContinueToUse(); }
        });
    }

    /** employed on a vehicle that turns its riders itself (Seats.steers) */
    public boolean steered() { return job() > 0 && getVehicle() != null && dev.frosthaven.ranchercompanion.ranch.Seats.get().steers(getVehicle()); }

    @Override
    protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level) { return new DrekkiGoals.PenNavigation(this, level); }

    /** barding as wolf armor (spec, Barding): while it holds, damage goes into its durability, one point per point of
     *  damage, and the drekki takes none; vanilla's bypasses_wolf_armor damage still reaches it. Done here, as
     *  Wolf.actuallyHurt does it, so a hit the barding takes moves nothing else (mood, events) */
    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (amount <= 0) return;  /* a player's hit: the push only */
        if (!bardingItem.isEmpty() && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_WOLF_ARMOR) && level() instanceof net.minecraft.server.level.ServerLevel sl) {
            boolean[] broke = {false};
            /* its enchantments (item/BardingEnchants): the protections cut what the hit costs it, Thorns hits back */
            int wear = dev.frosthaven.ranchercompanion.item.BardingEnchants.wear(bardingItem, source, amount) + dev.frosthaven.ranchercompanion.item.BardingEnchants.thorns(this, source);
            if (wear > 0 && bardingItem.getItem() instanceof dev.frosthaven.ranchercompanion.item.BardingItem bi) {
                /* one barding for both stages: on a Fledgling a point costs Mature/Fledgling durability (Barding.wearScale),
                   rounded at random so it lasts as many hits as Fledgling barding did */
                float scaled = wear * bi.tier.wearScale(stage());
                wear = (int) scaled + (getRandom().nextFloat() < scaled - (int) scaled ? 1 : 0);
            }
            bardingItem.hurtAndBreak(wear, sl, this, item -> {
                playSound(net.minecraft.sounds.SoundEvents.WOLF_ARMOR_BREAK);
                setBardingItem(net.minecraft.world.item.ItemStack.EMPTY);
                broke[0] = true;
            });
            if (broke[0]) dev.frosthaven.ranchercompanion.trait.FightTraits.bardingBroke(this, source);  /* Point Break */
            if (bardingItem.isEmpty() || bardingItem.getCount() == 0) setBardingItem(net.minecraft.world.item.ItemStack.EMPTY);
            else { playSound(net.minecraft.sounds.SoundEvents.WOLF_ARMOR_DAMAGE); syncBarding(); }
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
        /* a safety rule (2026-11-21): with no rider, lava and falls never hurt it, so following its owner cannot kill it */
        if (s.is(net.minecraft.world.damagesource.DamageTypes.FALL) || !lavaBurns() && s.is(net.minecraft.world.damagesource.DamageTypes.LAVA)) return true;  /* a fall never, ridden or not */
        /* nor any Create machine: a train running it over (it cannot ride, so its catch-up lands it beside its owner's
           moving train), a drill, a saw, crushing wheels, a roller, a fan's fire. By the damage type's namespace, Create's
           and its addons' ("create..."): Create is not a dependency */
        if (s.typeHolder().unwrapKey().map(k -> k.location().getNamespace().startsWith("create")).orElse(false)) return true;
        /* nor any mob that is not hostile (the red health bar's rule, Enemy): a village's guard or iron golem, a wolf */
        if (s.getEntity() instanceof net.minecraft.world.entity.LivingEntity a && !(a instanceof net.minecraft.world.entity.player.Player) && !(a instanceof net.minecraft.world.entity.monster.Enemy)) return true;
        /* it never suffocates, under water or with its head in a block (Shane, 2026-11-23) */
        if (s.is(net.minecraft.world.damagesource.DamageTypes.DROWN) || s.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return true;
        /* on a seat it cannot step out of a wall: an adult's head reaches past a carriage's roof into a tunnel's rock */
        if (isPassenger() && s.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)) return true;
        return super.isInvulnerableTo(s) || stage() == 1 && !byPlayer(s) && !s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    private static boolean byPlayer(net.minecraft.world.damagesource.DamageSource s) {
        return s.getEntity() instanceof net.minecraft.world.entity.player.Player || s.getDirectEntity() instanceof net.minecraft.world.entity.player.Player;
    }

    /** no player hurts a drekki, its owner or another, by hand, tool, arrow or spell, whatever the server's PvP or a
     *  mod's friendly fire says (2026-11-21: an owner's area spells kept hitting her own). The hit lands for no damage,
     *  so its push still moves the drekki, a pup too; nothing else of it counts (no barding wear, no grudge, no kill credit) */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource s, float amount) {
        if (level().isClientSide) return super.hurt(s, amount);
        if (!byPlayer(s) || s.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            if (s.getEntity() != null && !bardingItem.isEmpty()) braced = tickCount;  /* a mob's hit on barding: no push */
            return super.hurt(s, amount);
        }
        braced = -1;
        playerTouchedAt = tickCount;  /* a spell's or arrow's own debuffs this tick are refused (FriendlyFire) */
        boolean hit = super.hurt(s, 0);
        setLastHurtByMob(null);
        lastHurtByPlayer = null; lastHurtByPlayerTime = 0;
        return hit;
    }

    /** the tick a mob hit it while it wore barding: barding of every tier stops the push of an enemy's hit (the hit's
     *  own and a strong mob's extra shove, which follows in the same tick). A player's push still moves it */
    private int braced = -1;

    @Override
    public void knockback(double strength, double x, double z) { if (braced != tickCount) super.knockback(strength, x, z); }

    @Override
    /** a fall never hurts a drekki, ridden or not; a rider takes the fall as their own (decided 2026-11-23: only the rider is hurt) */
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource s) {
        if (Riding.holds(this, dev.frosthaven.ranchercompanion.trait.Trait.LEAP_OF_FAITH)) return false;  /* safe from any height */
        for (net.minecraft.world.entity.Entity rider : getPassengers()) rider.causeFallDamage(distance, multiplier, s);
        return false;
    }

    /** lava neither burns it nor sets it on fire */
    @Override
    public void lavaHurt() { if (lavaBurns()) super.lavaHurt(); }

    /** lava burns it only under a rider, and not a Lava Walker, which wades in it as a strider does */
    private boolean lavaBurns() { return isVehicle() && !Riding.holds(this, dev.frosthaven.ranchercompanion.trait.Trait.LAVA_WALKER); }

    /** falls cannot hurt it, so it takes a long drop to reach its owner (the game's mobs stop at 3 blocks) */
    @Override
    public int getMaxFallDistance() { return isVehicle() ? super.getMaxFallDistance() : 24; }

    /** nor a harmful effect from a player (a splash of poison) */
    @Override
    public boolean addEffect(net.minecraft.world.effect.MobEffectInstance e, net.minecraft.world.entity.Entity source) {
        if (source instanceof net.minecraft.world.entity.player.Player && e.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) return false;
        return super.addEffect(e, source);
    }

    /** a ghost that falls out of the world comes back (spec, Ghost drekki) instead of falling forever */
    @Override
    protected void onBelowWorld() {
        /* the void never kills a drekki, a ghost or not (spec, Traits): back to the last solid ground, its owner or home */
        if (!level().isClientSide && owned()) { dev.frosthaven.ranchercompanion.trait.WorldTraits.outOfTheVoid(this); return; }
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

    /** server: the hostile mob Go here told it to attack (GoHere.attack): fought in every stance until it dies, gets away
     *  or the drekki is disengaged (Passive, Stay, a new Go here) */
    public net.minecraft.world.entity.LivingEntity ordered;

    /** server: one of its attacks is landing: its barding stands in for a held weapon, so the kill's loot reads the
     *  barding's Looting (item/BardingEnchants.strike). Only then: a mob drops what its hands hold when it dies. */
    public boolean striking;

    @Override
    public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
        return striking && slot == net.minecraft.world.entity.EquipmentSlot.MAINHAND ? bardingItem : super.getItemBySlot(slot);
    }

    /** server: Live Wire's surge, struck by lightning: it attacks twice as fast until this tick (spec, Traits) */
    public int surgeUntil = Integer.MIN_VALUE / 2;
    public boolean surged() { return tickCount < surgeUntil; }

    /** server: the last solid ground it stood on, where the void puts it back (spec, Traits: the void never kills a drekki) */
    public net.minecraft.core.GlobalPos safeGround;

    /** server: the tick a player's hit or spell last landed on it (FriendlyFire: sourceless debuffs then are refused) */
    public int playerTouchedAt = Integer.MIN_VALUE / 2;

    /** server: after Go here it holds its spot until this tick, then follows again unless told to stay; -1 when not holding */
    public int heldUntil = -1;
    public boolean held() { return tickCount < heldUntil; }

    /** called off a fight by the whistle (spec, Stances: the whistle in a fight): on its way back to its owner, then
     *  2 more seconds; server only, not saved */
    public boolean coming;
    private int comingSince, truceUntil = -1;
    /** blocks from its owner at which a called drekki has caught up (its follow starts at 4), and the truce after, ticks */
    public static final double CAUGHT_UP = 5;
    public static final int TRUCE = 40;

    /** the whistle calls it out of a fight: the fight ends, and it takes no target until 2 seconds after it is with its owner */
    public void callOff() { disengage(); coming = true; comingSince = tickCount; }

    /** it does not fight now: called off, or on a Go here trip and its hold. Its stance applies again after */
    public boolean calm() { return coming || tickCount < truceUntil || goHere != null || held(); }

    /** an attack order ends every truce: it is an order to fight */
    public void endCalm() { coming = false; truceUntil = -1; heldUntil = -1; }

    private void truceTick() {
        if (!coming) return;
        net.minecraft.world.entity.player.Player o = ownerPlayer();
        if (o == null || o.level() != level() || isStaying() || tickCount - comingSince > 600) coming = false;  /* nobody to come to: no truce is left hanging */
        else if (distanceToSqr(o) <= CAUGHT_UP * CAUGHT_UP) { coming = false; truceUntil = tickCount + TRUCE; }
    }

    /** server: hits before this (its own tick, and its owner's) start no fight; see setStance */
    public int fightsFrom, fightsFromOwnerTick;
    public net.minecraft.world.entity.player.Player fightsFromOwner;

    /**
     * A stance change (spec, Stances). To Passive, every fight ends now ({@link #disengage}). Off Passive, it starts
     * fresh: only hits from now on, to it or its owner, and its owner's own attacks from now on, start a fight.
     */
    public void setStance(dev.frosthaven.ranchercompanion.life.Life.Stance s) {
        var passive = dev.frosthaven.ranchercompanion.life.Life.Stance.PASSIVE;
        var was = life.stance;
        life.stance = s;
        if (s == passive && was != passive) disengage();
        if (was == passive && s != passive) freshFromNow();
        if (s != was) setTarget(null);
        syncLife();
    }

    /**
     * Every fight ends now (told to stay, or set Passive): no target, it forgets who hit it, mobs that were angry at it
     * (a guard, a golem, a wolf) calm down toward it, and only hits from now on start a fight again.
     */
    public void disengage() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        ordered = null;
        setTarget(null);
        setLastHurtByMob(null);
        lastHurtByPlayer = null; lastHurtByPlayerTime = 0;
        for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(48), m -> m.getTarget() == this)) m.setTarget(null);
        for (var m : sl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, getBoundingBox().inflate(48), m -> m instanceof net.minecraft.world.entity.NeutralMob))
            if (getUUID().equals(((net.minecraft.world.entity.NeutralMob) m).getPersistentAngerTarget())) ((net.minecraft.world.entity.NeutralMob) m).stopBeingAngry();
        freshFromNow();
    }

    private void freshFromNow() {
        fightsFrom = tickCount;
        fightsFromOwner = ownerPlayer();
        fightsFromOwnerTick = fightsFromOwner != null ? fightsFromOwner.tickCount : 0;
    }

    @Override
    public void die(net.minecraft.world.damagesource.DamageSource cause) {
        if (!level().isClientSide && owned() && dev.frosthaven.ranchercompanion.ranch.Death.survives(this, cause)) return;
        super.die(cause);
        if (!level().isClientSide && owned()) dev.frosthaven.ranchercompanion.ranch.Death.died(this);
    }

    private double seenX = Double.NaN, seenZ;
    private long seenAt;

    // ---- riding (spec, Stage 3) ----

    /** a Mature's ability (combat.Abilities): the game time it is ready again, and ticks to the breath's hit */
    public long abilityAt;
    public int breathIn;

    final Riding.State ride = new Riding.State();
    /** server: its height a tick ago while ridden; every side: ticks it has been off the ground */
    double rideY;
    int airSeen;
    /** a ridden drekki's air state as every game sees it, eased tick by tick so its pose never snaps: how far into the
     *  air pose it is, its rise in blocks a tick, how far into the glide, how hard it beats its wings; the "was" values
     *  are last tick's, for the frames between */
    private float airAmt, airWas, riseAvg, riseWas, glideAmt, glideWas, flapAmt, flapWas;
    private static float mid(float was, float now, float partial) { return was + (now - was) * partial; }
    public float airAmt(float partial) { return mid(airWas, airAmt, partial); }
    public float rise(float partial) { return mid(riseWas, riseAvg, partial); }
    public float glideAmt(float partial) { return mid(glideWas, glideAmt, partial); }
    public float flapAmt(float partial) { return mid(flapWas, flapAmt, partial); }
    /** ticks in the air */
    public int airTicks() { return airSeen; }
    private void airTick() {
        airWas = airAmt; riseWas = riseAvg; glideWas = glideAmt; flapWas = flapAmt;
        boolean air = airSeen >= 2 && !isInLiquid() && isVehicle();  /* a step down is a tick in the air: no pose for it */
        riseAvg += ((float) (getY() - yo) - riseAvg) * 0.35f;
        airAmt += ((air ? 1 : 0) - airAmt) * 0.3f;
        boolean glide = air && Riding.glides(this, riseAvg);
        glideAmt += ((glide ? 1 : 0) - glideAmt) * 0.18f;
        flapAmt += ((glide && riseAvg > -0.03 ? 1 : 0) - flapAmt) * 0.15f;  /* climbing or holding its height: on wingbeats */
    }

    /** a Mature with a saddle in its saddle slot carries its owner (Shane, 2026-11-23). Not a ghost, not at work */
    /** for other mods' displays (read by reflection, client side): the Four Winds dash of the drekki this game steers.
     *  dashCooldown: 0 = ready, 1 = just dashed */
    public float dashCooldown() { return ride.dashIn / (float) dev.frosthaven.ranchercompanion.look.RideMath.DASH_COOLDOWN; }
    public boolean canDash() { return Riding.holds(this, dev.frosthaven.ranchercompanion.trait.Trait.FOUR_WINDS); }

    public boolean rideable() { return stage() >= 3 && live() && entityData.get(SADDLED) && !ghost() && job() == 0 && move() != 0; }  /* checked out only: not one that lives at a hatchery */

    @Override
    public net.minecraft.world.entity.LivingEntity getControllingPassenger() {
        return rideable() && getFirstPassenger() instanceof net.minecraft.world.entity.player.Player p ? p : super.getControllingPassenger();
    }

    /** under a rider it does nothing of its own: no goals, no fights, no catch-up */
    @Override
    protected boolean isImmobile() { return super.isImmobile() || !level().isClientSide && getControllingPassenger() instanceof net.minecraft.world.entity.player.Player; }

    @Override
    protected void tickRidden(net.minecraft.world.entity.player.Player rider, Vec3 input) {
        super.tickRidden(rider, input);
        setRot(rider.getYRot(), rider.getXRot() * 0.5f);
        yRotO = yBodyRot = yHeadRot = getYRot();
        Riding.steer(this, rider);
    }

    @Override
    protected Vec3 getRiddenInput(net.minecraft.world.entity.player.Player rider, Vec3 input) {
        float side = rider.xxa * 0.5f, ahead = rider.zza;
        return new Vec3(side, 0, ahead <= 0 ? ahead * 0.25f : ahead);
    }

    @Override
    protected float getRiddenSpeed(net.minecraft.world.entity.player.Player rider) {
        return (float) (dev.frosthaven.ranchercompanion.look.RideMath.SPEED * (Riding.holds(this, dev.frosthaven.ranchercompanion.trait.Trait.OPEN_ROAD) ? 1.1 : 1));
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(net.minecraft.world.entity.Entity rider, EntityDimensions dims, float scale) {
        return new Vec3(0, dims.height() * 0.74, -0.1 * scale);
    }

    @Override
    public boolean canStandOnFluid(net.minecraft.world.level.material.FluidState fluid) { return Riding.walksOn(this, fluid); }

    @Override
    protected int decreaseAirSupply(int air) { return air; }  /* it never runs out of air (Shane, 2026-11-23) */

    /** the inventory key while riding opens its screen, as a horse's */
    @Override
    public void openCustomInventoryScreen(net.minecraft.world.entity.player.Player p) {
        if (p instanceof net.minecraft.server.level.ServerPlayer sp && sp.getUUID().equals(owner())) DrekkiInteract.openScreen(sp, this, false);
    }

    /** client: its smoothed ground speed, from where it was at the last call. Called from its own tick and from the
     *  sound tick, once a game tick at most: Entity Culling gives a drekki the player cannot see a reduced tick that
     *  moves it but never runs {@link #tick}, so a speed kept only there froze at "walking" and its steps played on
     *  while it stood behind a player in a menu (2026-11-22) */
    private static final double TELEPORT = 8;
    private net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> seenLevel;

    public void trackSpeed() {
        long now = level().getGameTime(), dt = now - seenAt;
        if (dt <= 0) return;
        double step = Double.isNaN(seenX) ? 0 : Math.hypot(getX() - seenX, getZ() - seenZ);
        /* a portal crossing or a teleport is no speed: the Nether's coordinates are an eighth of the Overworld's, and the
           jump read as a dash after every crossing (2026-11-24). No drekki runs TELEPORT blocks a tick */
        if (!Double.isNaN(seenX) && level().dimension() == seenLevel && step <= TELEPORT * dt) speedAvg += (step * 20 / dt - speedAvg) * 0.3;
        seenX = getX(); seenZ = getZ(); seenAt = now; seenLevel = level().dimension();
    }

    @Override
    public void tick() {
        super.tick();
        airSeen = onGround() ? 0 : airSeen + 1;
        airTick();
        if (level().isClientSide) trackSpeed();
        else {
            if (getControllingPassenger() instanceof net.minecraft.server.level.ServerPlayer rider) Riding.serverTick(this, rider); else rideY = getY();
            if (petTicks() > 0) entityData.set(PET, petTicks() - 1);
            if (owned()) dev.frosthaven.ranchercompanion.trait.WorldTraits.standing(this);
            if (stage() == 1 && isAlive() && getHealth() < getMaxHealth()) setHealth(getMaxHealth());  /* a pup hurt before 0.3.2 */
            if (actionLeft > 0 && --actionLeft == 0) entityData.set(ACTION, 0);
            if (owned() && isAlive()) {
                if (!birthKnown) settleBirth();
                if (stage() >= 3) dev.frosthaven.ranchercompanion.combat.Abilities.tick(this);
                if (isStaying()) dev.frosthaven.ranchercompanion.ranch.Ranch.workTick(this);  /* on a seat that moves, or employed */
                truceTick();
                if (heldUntil >= 0 && !held()) {  /* Go here's hold is over: not told to stay, it gets up from a plain seat and follows */
                    heldUntil = -1;
                    if (!isStaying() && job() == 0 && isPassenger()) stopRiding();
                }
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
        tag.putInt("Stage", stage()); tag.putInt("Line", line()); tag.putInt("Diet", diet()); tag.putInt("Astral", astral()); if (astral2() != 0) tag.putInt("Astral2", astral2());
        tag.putInt("Horn", horn()); tag.putInt("Hat", hat()); tag.putInt("Barding", barding()); tag.putInt("Dye", dye()); tag.putInt("Dye2", dye2()); tag.putInt("Anim", entityData.get(ANIM)); tag.putFloat("Lane", lane());
        tag.put("Life", dev.frosthaven.ranchercompanion.life.LifeTag.save(life));
        if (!bardingItem.isEmpty()) tag.put("BardingItem", bardingItem.save(registryAccess()));
        if (!accessory.isEmpty()) tag.put("Accessory", accessory.save(registryAccess()));
        if (!saddle.isEmpty()) tag.put("Saddle", saddle.save(registryAccess()));
        if (birthKnown) tag.putInt("Birth", birth());
        if (owner != null) { tag.putUUID("Owner", owner); tag.putUUID("DrekkiId", drekkiId); tag.putBoolean("Out", out); if (home != null) tag.putLong("Home", home.asLong()); }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Life")) dev.frosthaven.ranchercompanion.life.LifeTag.load(life, tag.getCompound("Life"));

        caughtUp = false;
        if (tag.contains("Stage")) setLook(tag.getInt("Stage"), tag.getInt("Line"), tag.getInt("Diet"), tag.getInt("Astral"), tag.getInt("Horn"));
        entityData.set(ASTRAL2, tag.getInt("Astral2"));
        if (tag.contains("Hat")) setGear(tag.getInt("Hat"), tag.getInt("Barding"), tag.getInt("Dye"));
        if (tag.contains("Dye2")) entityData.set(DYE2, tag.getInt("Dye2"));
        /* the showcase's drawn bag, without an item */
        if (tag.contains("Bag")) { entityData.set(BAG, tag.getInt("Bag")); entityData.set(BAG_DYE, tag.contains("BagDye") ? tag.getInt("BagDye") : -1); entityData.set(BAG_DYE2, tag.contains("BagDye2") ? tag.getInt("BagDye2") : -1); }
        if (tag.contains("BardingItem")) setBardingItem(net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("BardingItem")));
        if (tag.contains("Accessory")) setAccessory(net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("Accessory")));
        birthKnown = false;
        if (tag.contains("Birth")) setBirth(tag.getInt("Birth"));
        if (tag.contains("Saddle")) setSaddle(net.minecraft.world.item.ItemStack.parseOptional(registryAccess(), tag.getCompound("Saddle")));
        if (tag.contains("Anim")) setAnim(Anim.of(tag.getInt("Anim")), tag.getFloat("Lane"));
        life.settleSlots(dev.frosthaven.ranchercompanion.trait.Trait.cap(stage(), false));
        applyStageHealth(false);
        if (tag.hasUUID("Owner")) bind(tag.getUUID("Owner"), tag.getUUID("DrekkiId"), tag.contains("Home") ? BlockPos.of(tag.getLong("Home")) : null);
        syncLife();
    }

    @Override
    public boolean removeWhenFarAway(double distance) { return false; }
}
