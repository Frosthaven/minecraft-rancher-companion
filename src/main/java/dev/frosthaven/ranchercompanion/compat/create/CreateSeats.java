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

    /** a drekki's seat on a train: Create keeps trains in its global list in every level, so the seat can be found again */
    private record TrainSeat(java.util.UUID train, int carriage, int seat) {}
    /** employed drekki on train seats, by entity UUID (a reloaded copy keeps it); refreshed while seated (seated) */
    private static final java.util.Map<java.util.UUID, TrainSeat> TRAIN_SEATS = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void seated(Mob mob) {
        if (!(mob.getVehicle() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce) || cce.getContraption() == null) return;
        Integer seat = cce.getContraption().getSeatMapping().get(mob.getUUID());
        if (seat != null && cce.trainId != null) TRAIN_SEATS.put(mob.getUUID(), new TrainSeat(cce.trainId, cce.carriageIndex, seat));
    }

    @Override
    public java.util.UUID workplace(Mob mob) {
        return mob.getVehicle() instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce ? cce.trainId : null;
    }

    @Override
    public void returnTools(ServerPlayer p, java.util.UUID workplace, java.util.UUID rider) {
        if (workplace == null) return;
        var train = com.simibubi.create.Create.RAILWAYS.trains.get(workplace);
        if (train == null || train.runtime.getSchedule() == null) return;
        /* another mob in a conductor seat drives on: the schedule stays with the train */
        for (var carriage : train.carriages) {
            var ce = carriage.anyAvailableEntity();
            if (ce == null || !(ce.getContraption() instanceof com.simibubi.create.content.trains.entity.CarriageContraption cc)) continue;
            for (var en : cc.getSeatMapping().entrySet()) {
                if (en.getKey().equals(rider) || en.getValue() >= cc.getSeats().size() || !cc.conductorSeats.containsKey(cc.getSeats().get(en.getValue()))) continue;
                for (var passenger : ce.getPassengers())
                    if (passenger.getUUID().equals(en.getKey()) && !(passenger instanceof net.minecraft.world.entity.player.Player)) return;
            }
        }
        var stack = train.runtime.returnSchedule(p.registryAccess());
        if (stack.isEmpty()) return;
        p.getInventory().placeItemBackInInventory(stack);  /* at their feet when the inventory is full */
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("create.schedule.removed_from_train"), true);
    }

    /**
     * Back on its train seat. A train through an Immersive Portals portal: IP moves the riding player across itself and
     * the carriage's old entity is retired before the drekki's seat reaches the plane, so Create never carries the drekki
     * over and it is dropped beside the portal (2026-11-22, the live world's rail). Moved to the carriage's level first.
     */
    @Override
    public boolean reseat(Mob mob) {
        return remembered(mob) || nearestConductorSeat(mob);
    }

    /** how far a dropped employed drekki looks for a conductor seat when its own is not known */
    static final double NEAR_SEAT = 8;

    /**
     * Its seat is not remembered (kept in memory only: none after a server restart) or no longer there (the train was
     * taken apart and assembled again, a new train): a free conductor seat of the nearest train carriage within
     * {@value #NEAR_SEAT} blocks (Shane, 2026-11-26: George Benjamini stood beside his train, employed, after the
     * Courier was rebuilt). Without one it goes home as before (Ranch.workTick).
     */
    private static boolean nearestConductorSeat(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        com.simibubi.create.content.trains.entity.CarriageContraptionEntity best = null; BlockPos bestSeat = null; int bestIndex = -1;
        double bestD = NEAR_SEAT * NEAR_SEAT;
        for (var ce : level.getEntitiesOfClass(com.simibubi.create.content.trains.entity.CarriageContraptionEntity.class, mob.getBoundingBox().inflate(NEAR_SEAT))) {
            if (!(ce.getContraption() instanceof com.simibubi.create.content.trains.entity.CarriageContraption cc)) continue;
            for (BlockPos sp : cc.conductorSeats.keySet()) {
                int index = cc.getSeats().indexOf(sp);
                if (index < 0 || cc.getSeatMapping().containsValue(index)) continue;
                double dd = ce.toGlobalVector(Vec3.atBottomCenterOf(sp), 1).distanceToSqr(mob.position());
                if (dd < bestD) { bestD = dd; best = ce; bestSeat = sp; bestIndex = index; }
            }
        }
        if (best == null) return false;
        boolean sat = new OnContraption(best, bestSeat, bestIndex).sit(mob);
        if (sat) dev.frosthaven.ranchercompanion.RancherCompanion.LOG.info("[seats] {} took the free conductor seat of the nearest carriage ({} blocks): its own seat was not known",
            mob.getName().getString(), String.format("%.1f", Math.sqrt(bestD)));
        return sat;
    }

    /** its remembered train seat (Create keeps trains in every level), moved across a portal first when needed */
    private static boolean remembered(Mob mob) {
        TrainSeat ts = TRAIN_SEATS.get(mob.getUUID());
        if (ts == null || mob.getServer() == null) return false;
        var train = com.simibubi.create.Create.RAILWAYS.trains.get(ts.train);
        if (train == null || train.derailed || ts.carriage >= train.carriages.size()) { TRAIN_SEATS.remove(mob.getUUID()); return false; }
        var carriage = train.carriages.get(ts.carriage);
        var here = carriage.getDimensionalIfPresent(mob.level().dimension());
        AbstractContraptionEntity ce = here != null ? here.entity.get() : null;
        if (ce == null) ce = carriage.anyAvailableEntity();
        if (ce == null || ce.getContraption() == null || ts.seat >= ce.getContraption().getSeats().size()) return false;
        OnContraption seat = new OnContraption(ce, ce.getContraption().getSeats().get(ts.seat), ts.seat);
        var mapping = ce.getContraption().getSeatMapping();
        mapping.remove(mob.getUUID());  /* its own stale entry from the drop */
        if (!seat.free()) return false;
        Mob rider = mob;
        if (ce.level() != mob.level() && ce.level() instanceof ServerLevel to) {
            Vec3 at = seat.at();
            var moved = mob.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(to, at, Vec3.ZERO, mob.getYRot(), mob.getXRot(),
                net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
            if (!(moved instanceof Mob m)) return false;
            rider = m;
            TRAIN_SEATS.put(rider.getUUID(), ts);
        }
        return seat.sit(rider);
    }

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
            if (mob.getVehicle() != ce) return false;
            if (ce instanceof com.simibubi.create.content.trains.entity.CarriageContraptionEntity cce && cce.trainId != null)
                TRAIN_SEATS.put(mob.getUUID(), new TrainSeat(cce.trainId, cce.carriageIndex, index));
            return true;
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
