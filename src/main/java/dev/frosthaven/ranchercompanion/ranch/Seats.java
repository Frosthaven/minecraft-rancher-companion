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
        /** the Create role a mob sat here takes (ROLE_*): a train's conductor, a stock keeper beside a Stock Ticker, or
         *  NO_ROLE. A drekki sat in a role is employed */
        int role();
    }

    /** a drekki's job, kept in the roster (Entry.role) and synced (DrekkiEntity.job): EMPLOYED when the role is not known */
    int NO_ROLE = 0, EMPLOYED = 1, CONDUCTOR = 2, STOCK_KEEPER = 3;

    /** the job's title: "Conductor", "Stock keeper", or "Employed" */
    static net.minecraft.network.chat.Component title(int role) {
        return net.minecraft.network.chat.Component.translatable(switch (role) {
            case CONDUCTOR -> "role.rancher_companion.conductor";
            case STOCK_KEEPER -> "role.rancher_companion.stock_keeper";
            default -> "screen.rancher_companion.where.employed";
        });
    }

    /** the seat the player looks at within range blocks, before any block closer than blockDist, or null */
    Seat looked(ServerPlayer p, double range, double blockDist);

    /** the seat block at pos, free, or null: a staying drekki put back at its spot sits there again */
    Seat at(ServerLevel level, BlockPos pos);

    /** a vehicle that turns its riders itself (Create's contraptions face them where they go): an employed drekki on it
     *  does not look around, so the two do not fight */
    default boolean steers(net.minecraft.world.entity.Entity vehicle) { return false; }

    /** an employed drekki on its seat: remember the seat (a train's: its train, carriage and seat) for reseat */
    default void seated(Mob mob) {}

    /** an employed drekki off its seat: back on the seat it had, in whichever level that seat's carriage is now; false
     *  when it cannot (the train taken apart, the seat taken) */
    default boolean reseat(Mob mob) { return false; }

    Seats NONE = new Seats() {
        @Override public Seat looked(ServerPlayer p, double range, double blockDist) { return null; }
        @Override public Seat at(ServerLevel level, BlockPos pos) { return null; }
    };

    /** set by compat/create when Create is loaded */
    final class Holder { public static Seats seats = NONE; private Holder() {} }

    static Seats get() { return Holder.seats; }
}
