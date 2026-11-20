package dev.frosthaven.ranchercompanion.life;

import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.block.TroughBlock;
import dev.frosthaven.ranchercompanion.block.TroughBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.ranch.Flood;
import dev.frosthaven.ranchercompanion.ranch.Ground;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Troughs as a drekki eats from them (spec, Feeding at troughs; decided 2026-11-18). Each trough segment has a front spot
 * (any stage; the only one stages 1 and 2 reach), a back spot (stage 3 over the wall), and a line's two end segments an
 * end spot each (stage 3). One drekki per spot: a drekki claims it, walks there, eats one item and lets it go. Only the
 * owner's troughs count, and only food it eats; it picks among them at random.
 */
public final class Feeding {
    private Feeding() {}

    public record Spot(BlockPos trough, BlockPos at) {}

    /** claimed spots: the claiming drekki and the game time the claim lapses */
    private record Claim(UUID by, long until) {}
    private static final Map<BlockPos, Claim> CLAIMS = new HashMap<>();
    /** troughs a drekki could not path to, until a game time (spec: about a minute) */
    private static final Map<UUID, Map<BlockPos, Long>> UNREACHABLE = new HashMap<>();
    /** a checked-out drekki looks this far for troughs, and only while its owner stands still */
    public static final int OUT_RANGE = 16;

    public static List<BlockPos> spots(BlockState s, BlockPos pos, int stage) {
        Direction f = s.getValue(TroughBlock.FACING);
        List<BlockPos> out = new ArrayList<>();
        out.add(pos.relative(f));
        if (stage >= 3) {
            out.add(pos.relative(f.getOpposite()));
            if (!s.getValue(TroughBlock.LEFT)) out.add(pos.relative(TroughBlock.left(s)));
            if (!s.getValue(TroughBlock.RIGHT)) out.add(pos.relative(TroughBlock.right(s)));
        }
        return out;
    }

    /** the troughs this drekki may look at: its hatchery's when checked in, those near it when out */
    private static List<BlockPos> troughs(DrekkiEntity d) {
        Level level = d.level();
        if (!d.isOut()) {
            if (d.home() == null || !level.isLoaded(d.home()) || !(level.getBlockEntity(d.home()) instanceof HatcheryBlockEntity h)) return List.of();
            return h.troughs();
        }
        List<BlockPos> out = new ArrayList<>();
        BlockPos c = d.blockPosition();
        for (int cx = (c.getX() - OUT_RANGE) >> 4; cx <= (c.getX() + OUT_RANGE) >> 4; cx++)
            for (int cz = (c.getZ() - OUT_RANGE) >> 4; cz <= (c.getZ() + OUT_RANGE) >> 4; cz++) {
                var chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos p : chunk.getBlockEntitiesPos())
                    if (p.distSqr(c) <= OUT_RANGE * OUT_RANGE && chunk.getBlockEntity(p) instanceof TroughBlockEntity) out.add(p);
            }
        return out;
    }

    /** the owner's trough with food this drekki eats */
    private static boolean feeds(DrekkiEntity d, BlockPos p) {
        if (!(d.level().getBlockEntity(p) instanceof TroughBlockEntity be) || !java.util.Objects.equals(be.owner(), d.owner())) return false;
        int diet = d.level().getBlockState(p).getValue(TroughBlock.DIET);
        if (diet == 0 || d.stage() > 1 && diet - 1 != d.diet()) return false;
        for (BlockPos q : TroughBlock.line(d.level(), p))
            if (d.level().getBlockEntity(q) instanceof TroughBlockEntity t) for (ItemStack s : t.items()) if (Vitals.eats(d, s)) return true;
        return false;
    }

    private static boolean inArea(DrekkiEntity d, BlockPos spot) {
        if (d.isOut()) return spot.distSqr(d.blockPosition()) <= OUT_RANGE * OUT_RANGE;
        return d.inPen(spot);
    }

    /** a free spot at a random usable trough, claimed for this drekki; null when none */
    public static Spot claim(DrekkiEntity d) {
        long now = d.level().getGameTime();
        CLAIMS.values().removeIf(c -> c.until < now);
        Map<BlockPos, Long> bad = UNREACHABLE.getOrDefault(d.drekkiId(), Map.of());
        List<Spot> free = new ArrayList<>();
        Ground g = new Ground(d.level());
        for (BlockPos t : troughs(d)) {
            if (bad.getOrDefault(t, Long.MIN_VALUE) > now || !feeds(d, t)) continue;
            for (BlockPos at : spots(d.level().getBlockState(t), t, d.stage())) {
                Claim c = CLAIMS.get(at);
                if (c != null && !c.by.equals(d.drekkiId())) continue;
                if (!inArea(d, at) || !g.fits(Flood.pack(at.getX(), at.getY(), at.getZ()), d.stage())) continue;
                free.add(new Spot(t, at));
            }
        }
        if (free.isEmpty()) return null;
        Spot s = free.get(d.getRandom().nextInt(free.size()));
        CLAIMS.put(s.at(), new Claim(d.drekkiId(), now + 600));
        return s;
    }

    public static void release(DrekkiEntity d, Spot s) {
        if (s == null) return;
        Claim c = CLAIMS.get(s.at());
        if (c != null && c.by.equals(d.drekkiId())) CLAIMS.remove(s.at());
    }

    public static void unreachable(DrekkiEntity d, Spot s) {
        UNREACHABLE.computeIfAbsent(d.drekkiId(), k -> new HashMap<>()).put(s.trough(), d.level().getGameTime() + 1200);
        release(d, s);
    }

    /** one item it eats from the trough's line, taken out; empty when none is left */
    public static ItemStack take(DrekkiEntity d, BlockPos trough) {
        if (!feeds(d, trough)) return ItemStack.EMPTY;
        for (BlockPos q : TroughBlock.line(d.level(), trough))
            if (d.level().getBlockEntity(q) instanceof TroughBlockEntity t) {
                var c = t.container();
                for (int i = 0; i < TroughBlockEntity.SLOTS; i++) if (Vitals.eats(d, c.getItem(i))) {
                    ItemStack one = c.removeItem(i, 1);
                    TroughBlock.contentsChangedAt(d.level(), q);
                    return one;
                }
            }
        return ItemStack.EMPTY;
    }

    /** a meal from the bag it wears (stage 2 on): when it has no usable trough, or is out with its owner on the move.
     *  Only a bag with a feeding upgrade feeds it (Sophisticated Backpacks; spec, Accessory: bags), and only food its
     *  filter allows; a plain bag holds food but never feeds it */
    public static ItemStack fromPack(DrekkiEntity d) {
        var bag = d.bagStorage();
        if (bag == null || d.accessory().getItem() instanceof dev.frosthaven.ranchercompanion.item.BagItem) return ItemStack.EMPTY;
        for (int i = 0; i < bag.getSlots(); i++) {
            ItemStack s = bag.getStackInSlot(i);
            if (Vitals.eats(d, s) && dev.frosthaven.ranchercompanion.compat.sophisticated.SophBags.feeds(d.accessory(), s)) return bag.extractItem(i, 1, false);
        }
        return ItemStack.EMPTY;
    }

    public static void forget(UUID drekki) { UNREACHABLE.remove(drekki); CLAIMS.values().removeIf(c -> c.by.equals(drekki)); }

    public static void clear() { CLAIMS.clear(); UNREACHABLE.clear(); }
}
