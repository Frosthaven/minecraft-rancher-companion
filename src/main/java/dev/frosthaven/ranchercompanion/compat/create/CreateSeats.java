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
 * does, so Create gives it a role and a hat as it would any mob.
 */
final class CreateSeats implements Seats {
    /** steps along the look ray through a contraption's blocks */
    private static final double STEP = 0.2;

    @Override
    public Seat looked(ServerPlayer p, double range, double blockDist) {
        Vec3 eye = p.getEyePosition(), dir = p.getViewVector(1);
        double reach = Math.min(range, blockDist);
        Seat best = null;
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
        if (best != null) return best;
        return null;
    }

    @Override
    public Seat at(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock() instanceof SeatBlock ? new Block(level, pos) : null;
    }

    /** a seat block in the world */
    record Block(ServerLevel level, BlockPos pos) implements Seat {
        @Override public Vec3 at() { return Vec3.atBottomCenterOf(pos); }
        @Override public boolean free() { return level.getBlockState(pos).getBlock() instanceof SeatBlock && !SeatBlock.isSeatOccupied(level, pos); }
        @Override public boolean sit(Mob mob) {
            if (!free() || !SeatBlock.canBePickedUp(mob)) return false;
            mob.stopRiding();
            SeatBlock.sitDown(level, pos, mob);
            return mob.isPassenger();
        }
        /** Create's stock keeper rule (EntityHats.getLogisticsHatFor): a Stock Ticker beside the seat, level or one up */
        @Override public boolean job() {
            for (var d : net.minecraft.core.Direction.Plane.HORIZONTAL) for (int y = 0; y <= 1; y++)
                if (level.getBlockState(pos.relative(d).above(y)).getBlock() instanceof com.simibubi.create.content.logistics.stockTicker.StockTickerBlock) return true;
            return false;
        }
    }

    /** a seat of a contraption: it moves with it */
    record OnContraption(AbstractContraptionEntity ce, BlockPos local, int index) implements Seat {
        @Override public Vec3 at() { return ce.toGlobalVector(Vec3.atBottomCenterOf(local), 1); }
        @Override public boolean free() { return ce.isAlive() && ce.getContraption() != null && !ce.getContraption().getSeatMapping().containsValue(index); }
        @Override public boolean sit(Mob mob) {
            if (!free() || !SeatBlock.canBePickedUp(mob)) return false;
            mob.stopRiding();
            ce.addSittingPassenger(mob, index);
            return mob.getVehicle() == ce;
        }
        /** a seat of a train's carriage: Create makes a mob there its conductor once the train has a schedule */
        @Override public boolean job() { return ce instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity; }
    }
}
