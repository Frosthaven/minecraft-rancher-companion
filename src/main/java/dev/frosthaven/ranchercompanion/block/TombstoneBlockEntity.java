package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.item.DrekkiData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A drekki's tombstone (spec, Tombstone): its name, the date it died, the gear it wore (hat, barding, bag, saddle) and its
 * record. The way back (Shane, 2026-11-26): a Drekki Lotus Blossom in one of its slots raises the drekki's ghost by it;
 * its owner lights the candle with flint and steel, and a day later (on the clock) the candle is melted down and its
 * owner can reclaim the drekki. Taking the blossom out puts the candle out, stops the day and lays the ghost to rest. A death's tombstone
 * without a record vanishes {@link #VANISH} ticks after it is emptied; one that keeps a drekki, or a player placed, stays.
 */
public final class TombstoneBlockEntity extends BlockEntity {
    public static final int SLOTS = 4;  /* hat, barding, bag, saddle (3 before 0.5.0) */
    /** the Corpse mod's default despawn time of an empty corpse (corpse-server.toml, corpse.despawn.time) */
    public static final int VANISH = 600;

    private final SimpleContainer items = new SimpleContainer(SLOTS);
    /** the overworld day time the blossom went in (the meter's start), 0 for none */
    private long reviveStart;
    /** its ghost is risen (the roster holds it at this tombstone) while the blossom's day runs and after, until the reclaim */
    private boolean summoned;
    private String name = "", date = "";
    /** a decorative tombstone's message (no record: no inscription), up to {@link #MESSAGE_MAX} characters */
    private String message = "";
    public static final int MESSAGE_MAX = 32;
    /** the drekki's record when it died: look, life, traits (spec, Tombstone; kept for later, never shown or sent) */
    private CompoundTag drekki = new CompoundTag();
    /** placed by a death: it vanishes once empty */
    private boolean natural;
    private long emptySince = -1;

    public TombstoneBlockEntity(BlockPos pos, BlockState state) {
        super(DrekkiBlocks.TOMBSTONE_ENTITY.get(), pos, state);
        items.addListener(c -> lotusChanged());
    }

    public SimpleContainer items() { return items; }
    /** the slot holding a Drekki Lotus Blossom, or -1 */
    public int blossomSlot() {
        for (int i = 0; i < SLOTS; i++) if (items.getItem(i).is(dev.frosthaven.ranchercompanion.item.DrekkiItems.LOTUS_BLOSSOM.get())) return i;
        return -1;
    }
    public long reviveStart() { return reviveStart; }
    public boolean summoned() { return summoned; }
    /** tests: the blossom's day as if it had passed */
    public void setReviveStart(long t) { reviveStart = t; changed(); }
    public void setSummoned(boolean s) { summoned = s; changed(); }
    /** it holds a fallen drekki's record: the ghost, the lotus and Reclaim work only then */
    public boolean hasRecord() { return !drekki.isEmpty() && drekki.hasUUID("DrekkiId") && drekki.hasUUID("Owner"); }
    public java.util.UUID recordOwner() { return hasRecord() ? drekki.getUUID("Owner") : null; }
    /** client side: it holds a record (the screen's buttons) */
    private boolean clientRecord;
    public boolean showsRecord() { return level != null && level.isClientSide ? clientRecord : hasRecord(); }

    /** the drekki came back (Shane, 2026-11-26): an inert tombstone now, decoration only: no record (no second rebirth), no
     *  inscription (name and date gone), no ghost, no blossom, and it stays where it is (it no longer vanishes when emptied) */
    public void clearRecord() {
        drekki = new CompoundTag(); summoned = false; reviveStart = 0; name = ""; date = ""; natural = false;
        loading = true; int b = blossomSlot(); if (b >= 0) items.setItem(b, ItemStack.EMPTY); loading = false;  /* the blossom is used up */
        BlockState st = getBlockState();
        if (level != null && !level.isClientSide && st.hasProperty(TombstoneBlock.CRACKED))  /* used: cracked; its candle whole again, unlit */
            level.setBlock(worldPosition, st.setValue(TombstoneBlock.CRACKED, true).setValue(TombstoneBlock.CANDLE, TombstoneBlock.Candle.UNLIT), 3);
        changed();
    }

    private void changed() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }

    /** a blossom in starts the day and raises the ghost; out stops it and lays the ghost to rest (server; the clock is the
     *  overworld's day time, as breeding's) */
    private boolean loading;

    /** it stands on soul soil: only then do the rebirth functions work (ghost, candle, day, Reclaim; Shane, 2026-11-26);
     *  anywhere else it is decoration */
    public boolean onSoulSoil() { return level != null && level.getBlockState(worldPosition.below()).is(net.minecraft.world.level.block.Blocks.SOUL_SOIL); }

    /** the soul soil under it at the last check (null before the first) */
    private Boolean soilSeen;

    private void lotusChanged() {
        if (loading) return;
        if (level == null || level.isClientSide || !hasRecord()) { changed(); return; }
        soilSeen = onSoulSoil();  /* the soil as of this change: a later change of soil is seen by the tick */
        boolean in = blossomSlot() >= 0 && soilSeen;
        var sl = (net.minecraft.server.level.ServerLevel) level;
        if (in && !summoned) dev.frosthaven.ranchercompanion.ranch.Rebirth.raise(sl, worldPosition, this);  /* the blossom alone raises the ghost */
        else if (!in && (summoned || reviveStart != 0)) {
            reviveStart = 0;
            dev.frosthaven.ranchercompanion.ranch.Rebirth.lay(sl, worldPosition, this);
        }
        candle();
        changed();
    }

    /** flint and steel on it (Shane, 2026-11-26): with a blossom in and the candle not yet lit, the candle lights and the
     *  day starts (server; on the clock, the overworld's day time); false when there is nothing to light */
    public boolean light() {
        if (level == null || level.isClientSide || !hasRecord() || blossomSlot() < 0 || reviveStart != 0 || !onSoulSoil()) return false;
        reviveStart = Math.max(1, dev.frosthaven.ranchercompanion.ranch.Roster.now(level.getServer()));
        candle();
        changed();
        return true;
    }

    /** a decorative tombstone (no record): its candle lit as a plain light source (no day, no cyan); false if it already burns */
    public boolean lightDecor() {
        if (level == null || level.isClientSide || hasRecord()) return false;
        BlockState st = getBlockState();
        if (!st.hasProperty(TombstoneBlock.CANDLE) || st.getValue(TombstoneBlock.CANDLE) == TombstoneBlock.Candle.LIT) return false;
        level.setBlock(worldPosition, st.setValue(TombstoneBlock.CANDLE, TombstoneBlock.Candle.LIT), 3);
        return true;
    }

    /** sneak, empty hand (Shane, 2026-11-26): the candle goes out. With a drekki's record that cancels the day (the ghost
     *  stays while the blossom is in) and its ghost's pairing at a hatchery, if any */
    public void putOut(net.minecraft.server.level.ServerPlayer p) {
        if (level == null || level.isClientSide) return;
        if (!hasRecord()) {
            BlockState st = getBlockState();
            if (st.hasProperty(TombstoneBlock.CANDLE)) level.setBlock(worldPosition, st.setValue(TombstoneBlock.CANDLE, TombstoneBlock.Candle.UNLIT), 3);
            return;
        }
        reviveStart = 0;
        dev.frosthaven.ranchercompanion.ranch.Rebirth.cancelGhostBreeding(p, this);
        candle();
        changed();
    }

    /** the candle: lit while the day runs, melted once it is done, unlit otherwise */
    public void candle() {
        if (level == null || level.isClientSide || !hasRecord()) return;  /* a decorative one's candle is the players' to light and put out */
        TombstoneBlock.Candle c = reviveStart == 0 ? TombstoneBlock.Candle.UNLIT
            : dev.frosthaven.ranchercompanion.ranch.Rebirth.progress(this, dev.frosthaven.ranchercompanion.ranch.Roster.now(level.getServer())) < 1
            ? TombstoneBlock.Candle.LIT : TombstoneBlock.Candle.MELTED;
        BlockState st = getBlockState();
        if (st.hasProperty(TombstoneBlock.CANDLE) && st.getValue(TombstoneBlock.CANDLE) != c) level.setBlock(worldPosition, st.setValue(TombstoneBlock.CANDLE, c), 3);
        if (summoned && hasRecord()) {  /* its ghost sheds soul-fire flames only while the candle burns */
            dev.frosthaven.ranchercompanion.entity.DrekkiEntity g = dev.frosthaven.ranchercompanion.ranch.Ranch.liveOf(drekki.getUUID("DrekkiId"));
            if (g != null) g.setGhostFlame(c == TombstoneBlock.Candle.LIT);
        }
    }
    public String message() { return message; }

    /** a message typed in the screen: decorative tombstones only (one with a record keeps its inscription), formatting
     *  codes stripped, cut to {@link #MESSAGE_MAX}; false when refused */
    public boolean setMessage(String text) {
        if (hasRecord() || text == null) return false;
        String clean = net.minecraft.util.StringUtil.filterText(net.minecraft.ChatFormatting.stripFormatting(text)).strip();
        message = clean.length() > MESSAGE_MAX ? clean.substring(0, MESSAGE_MAX) : clean;
        changed();
        return true;
    }

    /** the packet's handler: within reach of a decorative tombstone */
    public static void editMessage(net.minecraft.server.level.ServerPlayer p, BlockPos pos, String text) {
        if (!p.blockPosition().closerThan(pos, 8)) return;
        if (p.level().getBlockEntity(pos) instanceof TombstoneBlockEntity be) be.setMessage(text);
    }

    public String name() { return name; }
    public String date() { return date; }
    public boolean natural() { return natural; }
    public CompoundTag drekki() { return drekki.copy(); }
    public boolean holdsItems() { return !items.isEmpty(); }

    /** a death's tombstone: what it wore goes in, and it vanishes once taken */
    public void fill(String name, String date, java.util.List<ItemStack> gear, CompoundTag record) {
        this.name = name;
        this.date = date;
        this.drekki = record.copy();
        this.natural = true;
        for (int i = 0; i < Math.min(SLOTS, gear.size()); i++) items.setItem(i, gear.get(i));
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TombstoneBlockEntity be) {
        if (be.summoned && (level.getGameTime() + pos.asLong()) % 100 == 0) dev.frosthaven.ranchercompanion.ranch.Rebirth.keep((net.minecraft.server.level.ServerLevel) level, pos, be);
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) {
            boolean soil = be.onSoulSoil();
            if (be.soilSeen != null && be.soilSeen != soil && be.hasRecord()) be.lotusChanged();  /* the soul soil came or went: the ghost and the day follow */
            be.soilSeen = soil;
            be.candle();
        }
        if (!be.natural || be.hasRecord()) return;  /* one that keeps a drekki's record never vanishes */
        if (be.holdsItems()) { be.emptySince = -1; return; }
        long now = level.getGameTime();
        if (be.emptySince < 0) { be.emptySince = now; be.setChanged(); }
        else if (now - be.emptySince >= VANISH) TombstoneBlock.vanish(level, pos);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putString("Name", name);
        tag.putString("Date", date);
        if (!message.isEmpty()) tag.putString("Message", message);
        tag.putBoolean("Natural", natural);
        tag.putLong("EmptySince", emptySince);
        if (!drekki.isEmpty()) tag.put("Drekki", drekki);
        tag.putLong("ReviveStart", reviveStart);
        tag.putBoolean("Summoned", summoned);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list, reg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        name = tag.getString("Name");
        date = tag.getString("Date");
        message = tag.getString("Message");
        natural = tag.getBoolean("Natural");
        emptySince = tag.contains("EmptySince") ? tag.getLong("EmptySince") : -1;
        drekki = tag.getCompound("Drekki");
        reviveStart = tag.getLong("ReviveStart");
        summoned = tag.getBoolean("Summoned");
        clientRecord = tag.getBoolean("HasRecord");
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, reg);
        loading = true;
        for (int i = 0; i < SLOTS; i++) items.setItem(i, list.get(i));
        loading = false;
    }

    /** a mined tombstone keeps its name, date and drekki (not the day's progress); placed again, it reads them back */
    @Override
    protected void applyImplicitComponents(DataComponentInput in) {
        super.applyImplicitComponents(in);
        name = in.getOrDefault(DrekkiData.TOMB_NAME.get(), "");
        date = in.getOrDefault(DrekkiData.TOMB_DATE.get(), "");
        drekki = in.getOrDefault(DrekkiData.TOMB_DREKKI.get(), new CompoundTag()).copy();
        message = in.getOrDefault(DrekkiData.TOMB_MESSAGE.get(), "");
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder b) {
        super.collectImplicitComponents(b);
        if (!name.isEmpty()) b.set(DrekkiData.TOMB_NAME.get(), name);
        if (!date.isEmpty()) b.set(DrekkiData.TOMB_DATE.get(), date);
        if (!drekki.isEmpty()) b.set(DrekkiData.TOMB_DREKKI.get(), drekki.copy());
        if (!message.isEmpty()) b.set(DrekkiData.TOMB_MESSAGE.get(), message);
    }

    /** the client knows the name, date and items, for the screen and Jade; not the drekki's record */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider reg) { CompoundTag t = saveWithoutMetadata(reg); t.remove("Drekki"); t.putBoolean("HasRecord", hasRecord()); return t; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
