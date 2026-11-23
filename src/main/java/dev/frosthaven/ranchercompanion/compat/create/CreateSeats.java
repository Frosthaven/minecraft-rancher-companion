package dev.frosthaven.ranchercompanion.compat.create;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import dev.frosthaven.ranchercompanion.ranch.Seats;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Create's seats for the whistle's Go here: a seat block (a stock keeper's seat, a train's seat before it is assembled)
 * and a seat on a contraption (an assembled train). A drekki sits on them through Create's own calls, as a leashed mob
 * does, so Create gives it a role and a hat as it would any mob. Create's own gate (SeatBlock.canBePickedUp) is not asked:
 * it refuses every mob in create:ignore_seat, which takes all of c:capturing_not_supported, and the drekki is in that tag
 * to keep it out of capture items. Create reads the gate only when a mob lands on a seat or is led to one on a leash.
 */
final class CreateSeats implements Seats {
    /** steps along the look ray through a contraption's blocks */
    private static final double STEP = 0.2;

    @Override
    public Seat looked(ServerPlayer p, double range, double blockDist) { return onContraption(p, Math.min(range, blockDist)); }

    /** the contraption seat the player looks at within reach (blocks), or null; on either side */
    static OnContraption onContraption(net.minecraft.world.entity.player.Player p, double reach) {
        Vec3 eye = p.getEyePosition(), dir = p.getViewVector(1);
        OnContraption best = null;
        double bestD = reach;
        AABB area = p.getBoundingBox().expandTowards(dir.scale(reach)).inflate(2);
        for (AbstractContraptionEntity ce : p.level().getEntitiesOfClass(AbstractContraptionEntity.class, area)) {
            var c = ce.getContraption();
            if (c == null) continue;
            for (double t = 0; t < bestD; t += STEP) {
                BlockPos lp = BlockPos.containing(ce.toLocalVector(eye.add(dir.scale(t)), 1));
                var info = c.getBlocks().get(lp);
                if (info == null || info.state().isAir()) continue;
                if (info.state().getBlock() instanceof SeatBlock) {
                    int index = c.getSeats().indexOf(lp);
                    if (index >= 0) { best = new OnContraption(ce, lp, index); bestD = t; }
                }
                break;  /* the first block of it the ray meets: a seat, or a wall in front of one */
            }
        }
        return best;
    }

    @Override
    public boolean steers(net.minecraft.world.entity.Entity vehicle) { return vehicle instanceof AbstractContraptionEntity; }

    @Override
    public Seat at(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SeatBlock ? new Block(level, pos) : null;
    }

    /** a seat block in the world */
    record Block(ServerLevel level, BlockPos pos) implements Seat {
        @Override public Vec3 at() { return Vec3.atBottomCenterOf(pos); }
        @Override public boolean free() { return level.getBlockState(pos).getBlock() instanceof SeatBlock && !SeatBlock.isSeatOccupied(level, pos); }
        @Override public boolean sit(Mob mob) {
            if (!free()) return false;
            mob.stopRiding();
            SeatBlock.sitDown(level, pos, mob);
            return mob.isPassenger();
        }
        /** Create's stock keeper rule (EntityHats.getLogisticsHatFor): a Stock Ticker beside the seat, level or one up */
        @Override public int role() {
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int y = 0; y <= 1; y++)
                if (level.getBlockState(pos.relative(d).above(y)).getBlock() instanceof com.simibubi.create.content.logistics.stockTicker.StockTickerBlock) return STOCK_KEEPER;
            return NO_ROLE;
        }
    }

    /** a seat of a contraption: it moves with it */
    record OnContraption(AbstractContraptionEntity ce, BlockPos local, int index) implements Seat {
        @Override public Vec3 at() { return ce.toGlobalVector(Vec3.atBottomCenterOf(local), 1); }
        @Override public boolean free() { return ce.isAlive() && ce.getContraption() != null && !ce.getContraption().getSeatMapping().containsValue(index); }
        @Override public boolean sit(Mob mob) {
            if (!free()) return false;
            mob.stopRiding();
            ce.addSittingPassenger(mob, index);
            return mob.getVehicle() == ce;
        }
        /** who sits there now, or null */
        net.minecraft.world.entity.Entity sitter() {
            var c = ce.getContraption();
            if (c == null) return null;
            for (var en : c.getSeatMapping().entrySet())
                if (en.getValue() == index) for (var e : ce.getPassengers()) if (e.getUUID().equals(en.getKey())) return e;
            return null;
        }
        /** a seat of a train's carriage: Create makes a mob there its conductor once the train has a schedule */
        @Override public int role() { return ce instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity ? CONDUCTOR : NO_ROLE; }
    }
}
