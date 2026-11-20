package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Where the owner's drekki died (spec, Tombstone): the client marks it on its map mod. */
public record DeathMark(String name, String dimension, BlockPos pos) implements CustomPacketPayload {
    public static final Type<DeathMark> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "death_mark"));
    public static final StreamCodec<ByteBuf, DeathMark> CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, DeathMark::name, ByteBufCodecs.STRING_UTF8, DeathMark::dimension, BlockPos.STREAM_CODEC, DeathMark::pos, DeathMark::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
