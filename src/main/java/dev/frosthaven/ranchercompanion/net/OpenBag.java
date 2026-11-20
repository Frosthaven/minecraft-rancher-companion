package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Open the bag of the drekki the player looks at (client to server): the open-backpack key on a drekki. */
public record OpenBag(int entityId) implements CustomPacketPayload {
    public static final Type<OpenBag> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "open_bag"));
    public static final StreamCodec<ByteBuf, OpenBag> CODEC = ByteBufCodecs.VAR_INT.map(OpenBag::new, OpenBag::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
