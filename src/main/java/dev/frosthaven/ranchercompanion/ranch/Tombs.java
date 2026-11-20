package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.TombstoneBlock;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.net.DeathMark;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A drekki's tombstone and its map marker (spec, Tombstone). Where it died, a tombstone takes its gear; the owner gets a
 * death marker (the client draws it on JourneyMap when that is installed), at the next login when not online.
 */
public final class Tombs {
    private Tombs() {}

    public static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** the tombstone for a drekki that died, its gear in it; where it stands, or null when no spot is free */
    public static BlockPos place(ServerLevel level, BlockPos at, Direction facing, String name, List<ItemStack> gear, CompoundTag record) {
        BlockPos p = spot(level, at);
        if (p == null) return null;
        level.setBlock(p, DrekkiBlocks.TOMBSTONE.get().defaultBlockState().setValue(TombstoneBlock.FACING, facing), 3);
        if (!(level.getBlockEntity(p) instanceof TombstoneBlockEntity be)) return null;
        be.fill(name, LocalDate.now().format(DATE), gear, record);
        return p;
    }

    /** the death spot or the nearest free one around and above it, inside the world, on something solid when it can be */
    static BlockPos spot(ServerLevel level, BlockPos at) {
        int y = Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 2, at.getY()));
        BlockPos base = new BlockPos(at.getX(), y, at.getZ()), loose = null;
        for (int dy = 0; dy <= 6; dy++)
            for (int r = 0; r <= 2; r++)
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos p = base.offset(dx, dy, dz);
                        if (!level.getBlockState(p).canBeReplaced() || level.getBlockEntity(p) != null) continue;
                        if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) return p;
                        if (loose == null) loose = p;
                    }
        return loose;
    }

    /** the owner's marker: now when online, else at the next login */
    public static void mark(MinecraftServer server, UUID owner, String name, ServerLevel level, BlockPos pos) {
        DeathMark m = new DeathMark(name, level.dimension().location().toString(), pos);
        ServerPlayer p = server.getPlayerList().getPlayer(owner);
        if (p != null) send(p, m);
        else { Pending d = pending(server); d.marks.computeIfAbsent(owner, k -> new ArrayList<>()).add(m); d.setDirty(); }
    }

    public static void onLogin(ServerPlayer p) {
        Pending d = pending(p.server);
        List<DeathMark> l = d.marks.remove(p.getUUID());
        if (l == null) return;
        l.forEach(m -> send(p, m));
        d.setDirty();
    }

    /** only to a connection that takes the packet (a fake player's does not) */
    private static void send(ServerPlayer p, DeathMark m) {
        if (p.connection != null && p.connection.hasChannel(DeathMark.TYPE)) PacketDistributor.sendToPlayer(p, m);
    }

    private static Pending pending(MinecraftServer s) {
        return s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Pending::new, Pending::load), "rancher_companion_death_marks");
    }

    /** markers owed to owners who were away when their drekki died */
    static final class Pending extends SavedData {
        final java.util.Map<UUID, List<DeathMark>> marks = new java.util.HashMap<>();

        static Pending load(CompoundTag tag, HolderLookup.Provider reg) {
            Pending d = new Pending();
            for (Tag t : tag.getList("Marks", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                d.marks.computeIfAbsent(c.getUUID("Owner"), k -> new ArrayList<>())
                    .add(new DeathMark(c.getString("Name"), c.getString("Dim"), BlockPos.of(c.getLong("Pos"))));
            }
            return d;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
            ListTag list = new ListTag();
            marks.forEach((owner, l) -> l.forEach(m -> {
                CompoundTag c = new CompoundTag();
                c.putUUID("Owner", owner); c.putString("Name", m.name()); c.putString("Dim", m.dimension()); c.putLong("Pos", m.pos().asLong());
                list.add(c);
            }));
            tag.put("Marks", list);
            return tag;
        }
    }
}
