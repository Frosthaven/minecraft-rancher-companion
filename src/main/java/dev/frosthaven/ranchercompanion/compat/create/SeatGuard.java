package dev.frosthaven.ranchercompanion.compat.create;

import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * A player's click on a Create seat a drekki sits on does nothing (spec, Go here: Seats). Create would throw any mob
 * off the seat and sit the player there, the drekki's job lost by a misclick. Seat blocks here, on both sides; a seat
 * of a contraption (a train) through client/SeatGuardClient, since Create handles that click on the client first.
 */
public final class SeatGuard {
    private SeatGuard() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock ev) {
        /* sneaking, Create leaves the seat alone too (blocks placed against it, items used) */
        if (ev.getEntity().isShiftKeyDown() || !(ev.getLevel().getBlockState(ev.getPos()).getBlock() instanceof SeatBlock)) return;
        for (SeatEntity seat : ev.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(ev.getPos())))
            if (seat.getPassengers().stream().anyMatch(p -> p instanceof DrekkiEntity)) {
                ev.setCanceled(true);
                ev.setCancellationResult(InteractionResult.FAIL);
                return;
            }
    }

    /** the player looks at a contraption seat within reach */
    public static boolean onLookedSeat(net.minecraft.world.entity.player.Player p, double reach) {
        return CreateSeats.onContraption(p, reach) != null;
    }

    /** a drekki sits on the contraption seat the player looks at within reach */
    public static boolean drekkiOnLookedSeat(net.minecraft.world.entity.player.Player p, double reach) {
        CreateSeats.OnContraption seat = CreateSeats.onContraption(p, reach);
        return seat != null && seat.sitter() instanceof DrekkiEntity;
    }
}
