package dev.frosthaven.ranchercompanion.block;

import dev.frosthaven.ranchercompanion.ranch.Roster;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Drekki Lotus's growth on the clock (Shane, 2026-11-26), so it grows in an unloaded chunk too: the time it has grown
 * (overworld day time, counted only while a tombstone touches it) and the day time it was last counted. A tombstone
 * cannot come or go while the chunk is unloaded, so the time away counts as the plant stood when it was last seen.
 */
public final class LotusBlockEntity extends BlockEntity {
    /** a stage: a third of a Minecraft day, so it is grown a day after planting */
    public static final long STAGE = Roster.DAY / 3;

    private long grown, last;

    public LotusBlockEntity(BlockPos pos, BlockState state) { super(DrekkiBlocks.LOTUS_ENTITY.get(), pos, state); }

    public long grown() { return grown; }

    /** tests: last counted this long ago, as after a day unloaded */
    public void countedAgo(long ticks) { last -= ticks; setChanged(); }

    /** brings its growth up to the clock and its stage up to its growth */
    public void count(ServerLevel level) {
        long now = Roster.now(level.getServer());
        if (last == 0) last = now;
        long dt = Math.max(0, now - last);
        last = now;
        if (dt > 0 && LotusCropBlock.byTomb(level, worldPosition)) grown += dt;
        setChanged();
        BlockState st = getBlockState();
        if (st.getBlock() instanceof LotusCropBlock crop) {
            int age = (int) Math.min(crop.getMaxAge(), grown / STAGE);
            if (age != st.getValue(LotusCropBlock.AGE)) level.setBlock(worldPosition, crop.getStateForAge(age), 2);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LotusBlockEntity be) {
        if ((level.getGameTime() + pos.asLong()) % 20 == 0) be.count((ServerLevel) level);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putLong("Grown", grown);
        tag.putLong("Last", last);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        grown = tag.getLong("Grown");
        last = tag.getLong("Last");
    }
}
