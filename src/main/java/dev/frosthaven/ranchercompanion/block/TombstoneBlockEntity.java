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
 * A drekki's tombstone (spec, Tombstone): its name, the date it died, and the gear it wore (hat, barding, bag). A
 * tombstone a death placed vanishes {@link #VANISH} ticks after it is emptied, as the Corpse mod's corpses do by default;
 * one a player placed stays.
 */
public final class TombstoneBlockEntity extends BlockEntity {
    public static final int SLOTS = 3;
    /** the Corpse mod's default despawn time of an empty corpse (corpse-server.toml, corpse.despawn.time) */
    public static final int VANISH = 600;

    private final SimpleContainer items = new SimpleContainer(SLOTS);
    private String name = "", date = "";
    /** the drekki's record when it died: look, life, traits (spec, Tombstone; kept for later, never shown or sent) */
    private CompoundTag drekki = new CompoundTag();
    /** placed by a death: it vanishes once empty */
    private boolean natural;
    private long emptySince = -1;

    public TombstoneBlockEntity(BlockPos pos, BlockState state) {
        super(DrekkiBlocks.TOMBSTONE_ENTITY.get(), pos, state);
        items.addListener(c -> { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); });
    }

    public SimpleContainer items() { return items; }
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
        if (!be.natural) return;
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
        tag.putBoolean("Natural", natural);
        tag.putLong("EmptySince", emptySince);
        if (!drekki.isEmpty()) tag.put("Drekki", drekki);
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) list.set(i, items.getItem(i));
        ContainerHelper.saveAllItems(tag, list, reg);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        name = tag.getString("Name");
        date = tag.getString("Date");
        natural = tag.getBoolean("Natural");
        emptySince = tag.contains("EmptySince") ? tag.getLong("EmptySince") : -1;
        drekki = tag.getCompound("Drekki");
        NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, reg);
        for (int i = 0; i < SLOTS; i++) items.setItem(i, list.get(i));
    }

    /** a mined tombstone keeps its name and date; placed again, it reads them back */
    @Override
    protected void applyImplicitComponents(DataComponentInput in) {
        super.applyImplicitComponents(in);
        name = in.getOrDefault(DrekkiData.TOMB_NAME.get(), "");
        date = in.getOrDefault(DrekkiData.TOMB_DATE.get(), "");
        drekki = in.getOrDefault(DrekkiData.TOMB_DREKKI.get(), new CompoundTag()).copy();
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder b) {
        super.collectImplicitComponents(b);
        if (!name.isEmpty()) b.set(DrekkiData.TOMB_NAME.get(), name);
        if (!date.isEmpty()) b.set(DrekkiData.TOMB_DATE.get(), date);
        if (!drekki.isEmpty()) b.set(DrekkiData.TOMB_DREKKI.get(), drekki.copy());
    }

    /** the client knows the name, date and items, for the screen and Jade; not the drekki's record */
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider reg) { CompoundTag t = saveWithoutMetadata(reg); t.remove("Drekki"); return t; }
    @Override public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
