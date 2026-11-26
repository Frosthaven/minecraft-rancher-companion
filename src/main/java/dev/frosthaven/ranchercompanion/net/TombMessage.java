package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A decorative tombstone's message, typed in its screen (client to server; Shane, 2026-11-26). */
public record TombMessage(BlockPos pos, String text) implements CustomPacketPayload {
    public static final Type<TombMessage> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "tomb_message"));
    public static final StreamCodec<FriendlyByteBuf, TombMessage> CODEC = StreamCodec.of(
        (buf, m) -> { buf.writeBlockPos(m.pos()); buf.writeUtf(m.text(), 256); },
        buf -> new TombMessage(buf.readBlockPos(), buf.readUtf(256)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
