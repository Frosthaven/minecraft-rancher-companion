package dev.frosthaven.ranchercompanion.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Wings of Hope: the server's count of ticks a ridden mount has floated, which ends in "Flying is not enabled". */
@Mixin(ServerGamePacketListenerImpl.class)
public interface VehicleFloatAccess {
    @Accessor("aboveGroundVehicleTickCount") void rancher$setVehicleFloat(int ticks);
}
