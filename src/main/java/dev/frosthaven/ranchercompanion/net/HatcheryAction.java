package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A button on the hatchery screen (client to server): check out, check in, release or hatch. */
public record HatcheryAction(BlockPos pos, int action, UUID id) implements CustomPacketPayload {
    public static final Type<HatcheryAction> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "hatchery_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HatcheryAction> CODEC = StreamCodec.of(
        (b, a) -> { b.writeBlockPos(a.pos); b.writeVarInt(a.action); UUIDUtil.STREAM_CODEC.encode(b, a.id); },
        b -> new HatcheryAction(b.readBlockPos(), b.readVarInt(), UUIDUtil.STREAM_CODEC.decode(b)));

    public static final UUID NONE = new UUID(0, 0);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public Ranch.Action kind() { return Ranch.Action.values()[Math.floorMod(action, Ranch.Action.values().length)]; }
}
