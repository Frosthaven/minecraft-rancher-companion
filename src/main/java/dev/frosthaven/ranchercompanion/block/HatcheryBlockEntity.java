package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.look.Incubation;
import dev.frosthaven.ranchercompanion.ranch.Ground;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A hatchery's state (spec, Hatchery and Checkout): its owner (who placed or first used it), its anvil name, the egg in
 * the nest and how long it has incubated, and its drekki's pens. Every few seconds it puts back any of its drekki that
 * are missing from the world (spec, Spawning).
 */
public class HatcheryBlockEntity extends BlockEntity {
    /** up to 20 drekki live at one hatchery (spec, Hatchery cap) */
    public static final int CAP = 20;
    private static final int SYNC_EVERY = 100, PEN_EVERY = 100;

    private UUID owner;
    private Component name;
    private ItemStack egg = ItemStack.EMPTY;
    /** the egg's incubation so far, and the game and day time it was last counted at */
    private long eggBase, eggSince, eggSinceDay;
    private final Map<Integer, Set<Long>> pens = new HashMap<>(), beds = new HashMap<>();
    /** when the pens were last walked (pensFresh false: walk now). Not a Long.MIN_VALUE start: game time minus it
     *  overflows, and the pens then never refreshed after the first walk */
    private boolean pensFresh;
    private long pensAt;

    public HatcheryBlockEntity(BlockPos pos, BlockState state) {
        super(DrekkiBlocks.HATCHERY_ENTITY.get(), pos, state);
    }

    /** a pairing in progress (spec, Breeding: Pairing): its two parents and the overworld day time it started; null when none */
    private UUID breedA, breedB;
    private long breedStart;

    public boolean breeding() { return breedA != null; }
    public UUID breedA() { return breedA; }
    public UUID breedB() { return breedB; }
    public long breedStart() { return breedStart; }
    /** the tonic Pair took from the screen (spec, Breeding: tonics): it acts when the egg is laid, and goes back on a cancel */
    private ItemStack breedTonic = ItemStack.EMPTY;
    public ItemStack breedTonic() { return breedTonic; }
    public void startBreeding(UUID a, UUID b, long now) { breedA = a; breedB = b; breedStart = now; changed(); }
    /** the Heirloom Tonic's trait for this breeding, "" for none */
    private String breedKeep = "";
    public String breedKeep() { return breedKeep; }
    public void startBreeding(UUID a, UUID b, long now, ItemStack tonic) { startBreeding(a, b, now, tonic, ""); }
    public void startBreeding(UUID a, UUID b, long now, ItemStack tonic, String keep) { breedTonic = tonic.copy(); breedKeep = keep == null ? "" : keep; startBreeding(a, b, now); }
    /** ends the breeding; returns the tonic it held (empty for none) */
    public ItemStack stopBreeding() { ItemStack t = breedTonic; breedTonic = ItemStack.EMPTY; breedKeep = ""; breedA = null; breedB = null; breedStart = 0; changed(); return t; }

    public UUID owner() { return owner; }
    public void claim(UUID player) { owner = player; changed(); }
    public Component name() { return name; }
    public void setName(Component n) { name = n; changed(); }
    public ItemStack egg() { return egg; }
    public boolean hasEgg() { return !egg.isEmpty(); }

    /** ticks the egg has incubated (counted by {@link #count}; on the client, as last synched) */
    public long incubation() { return egg.isEmpty() ? 0 : eggBase; }

    /**
     * Adds what passed since the last count: the larger of the game ticks (which run on while the chunk is unloaded) and
     * how far the day clock moved forward (a night slept through, /time add). A clock set back adds nothing, so
     * incubation never goes down. Syncs to clients when the stage changes.
     */
    private void count() {
        long g = level.getGameTime(), d = level.getDayTime();
        long add = Math.max(g - eggSince, d - eggSinceDay);
        eggSince = g; eggSinceDay = d;
        if (add <= 0) return;
        int before = Incubation.stage(eggBase);
        eggBase += add;
        setChanged();
        if (Incubation.stage(eggBase) != before) changed();
    }

    /** the egg as an item that carries its incubation (for the screen's hover and when it leaves the nest) */
    public ItemStack eggWithProgress() {
        if (egg.isEmpty()) return ItemStack.EMPTY;
        if (level != null && !level.isClientSide) count();
        ItemStack s = egg.copy();
        s.set(DrekkiData.INCUBATION.get(), incubation());
        return s;
    }

    public void putEgg(ItemStack one) {
        egg = one.copyWithCount(1);
        Long t = egg.get(DrekkiData.INCUBATION.get());
        eggBase = t == null ? 0 : t;
        eggSince = level.getGameTime();
        eggSinceDay = level.getDayTime();
        changed();
    }

    public ItemStack takeEgg() {
        ItemStack s = eggWithProgress();
        egg = ItemStack.EMPTY; eggBase = 0;
        changed();
        return s;
    }

    /** the pen a drekki of this stage may use, refreshed every few seconds (spec, Wandering) */
    public Set<Long> pen(int stage) {
        if (level == null) return Set.of();
        if (!pensFresh || level.getGameTime() - pensAt > PEN_EVERY) { pens.clear(); beds.clear(); troughs = null; pensAt = level.getGameTime(); pensFresh = true; }
        return pens.computeIfAbsent(stage, s -> new Ground(level).pen(worldPosition, s));
    }

    private java.util.List<BlockPos> troughs;

    /** the troughs within the leash (loaded ones), refreshed with the pens; the feeding rules check owner, food and spots */
    public java.util.List<BlockPos> troughs() {
        if (level == null) return java.util.List.of();
        pen(1);
        if (troughs == null) {
            java.util.List<BlockPos> out = new java.util.ArrayList<>();
            int r = dev.frosthaven.ranchercompanion.ranch.Ground.LEASH + 1, up = dev.frosthaven.ranchercompanion.ranch.Ground.PEN_UP + 2;
            for (int cx = (worldPosition.getX() - r) >> 4; cx <= (worldPosition.getX() + r) >> 4; cx++)
                for (int cz = (worldPosition.getZ() - r) >> 4; cz <= (worldPosition.getZ() + r) >> 4; cz++) {
                    var chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue;
                    for (BlockPos p : chunk.getBlockEntitiesPos())
                        if (Math.abs(p.getY() - worldPosition.getY()) <= up && Math.abs(p.getX() - worldPosition.getX()) <= r
                            && Math.abs(p.getZ() - worldPosition.getZ()) <= r && chunk.getBlockEntity(p) instanceof TroughBlockEntity) out.add(p);
                }
            troughs = out;
        }
        return troughs;
    }

    /** the pen's bedding spots for this stage */
    public Set<Long> bedding(int stage) {
        Set<Long> pen = pen(stage);
        return beds.computeIfAbsent(stage, s -> new Ground(level).bedding(pen));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, HatcheryBlockEntity be) {
        if (be.hasEgg()) be.count();
        if (be.breeding() && (level.getGameTime() + pos.asLong()) % 20 == 0) dev.frosthaven.ranchercompanion.ranch.Pairing.tick((ServerLevel) level, pos, be);
        if ((level.getGameTime() + pos.asLong()) % SYNC_EVERY == 0) Ranch.syncHatchery((ServerLevel) level, pos);
    }

    private void changed() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        if (owner != null) tag.putUUID("Owner", owner);
        if (name != null) tag.putString("CustomName", Component.Serializer.toJson(name, reg));
        if (breedA != null) { tag.putUUID("BreedA", breedA); tag.putUUID("BreedB", breedB); tag.putLong("BreedStart", breedStart);
            if (!breedTonic.isEmpty()) tag.put("BreedTonic", breedTonic.save(reg));
            if (!breedKeep.isEmpty()) tag.putString("BreedKeep", breedKeep); }
        if (!egg.isEmpty()) { tag.put("Egg", egg.save(reg)); tag.putLong("EggBase", eggBase); tag.putLong("EggSince", eggSince); tag.putLong("EggSinceDay", eggSinceDay); }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        name = tag.contains("CustomName") ? Component.Serializer.fromJson(tag.getString("CustomName"), reg) : null;
        egg = tag.contains("Egg") ? ItemStack.parseOptional(reg, tag.getCompound("Egg")) : ItemStack.EMPTY;
        breedA = tag.hasUUID("BreedA") ? tag.getUUID("BreedA") : null;
        breedB = tag.hasUUID("BreedB") ? tag.getUUID("BreedB") : null;
        if (breedB == null) breedA = null;
        breedStart = tag.getLong("BreedStart");
        breedKeep = tag.getString("BreedKeep");
        breedTonic = tag.contains("BreedTonic") ? ItemStack.parseOptional(reg, tag.getCompound("BreedTonic")) : ItemStack.EMPTY;
        eggBase = tag.getLong("EggBase"); eggSince = tag.getLong("EggSince"); eggSinceDay = tag.getLong("EggSinceDay");
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        name = input.get(DataComponents.CUSTOM_NAME);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder b) {
        super.collectImplicitComponents(b);
        if (name != null) b.set(DataComponents.CUSTOM_NAME, name);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider reg) { return saveCustomOnly(reg); }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
