package dev.frosthaven.ranchercompanion.ranch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Seats a drekki can be sent to with the whistle (spec, Go here). Create's are the only ones so far (compat/create):
 * a seat block, or a seat on a moving contraption such as a train. Without Create there are none.
 */
public interface Seats {
    /** one seat: where it is now (a contraption moves), and sitting a mob on it */
    interface Seat {
        Vec3 at();
        /** a free seat that still exists */
        boolean free();
        /** sits the mob on it; false when it could not */
        boolean sit(Mob mob);
        /** a seat with a Create role: a train's, or one beside a Stock Ticker. A drekki sat there is employed */
        boolean job();
    }

    /** the seat the player looks at within range blocks, before any block closer than blockDist, or null */
    Seat looked(ServerPlayer p, double range, double blockDist);

    /** the seat block at pos, free, or null: a staying drekki put back at its spot sits there again */
    Seat at(ServerLevel level, BlockPos pos);

    Seats NONE = new Seats() {
        @Override public Seat looked(ServerPlayer p, double range, double blockDist) { return null; }
        @Override public Seat at(ServerLevel level, BlockPos pos) { return null; }
    };

    /** set by compat/create when Create is loaded */
    final class Holder { public static Seats seats = NONE; private Holder() {} }

    static Seats get() { return Holder.seats; }
}
