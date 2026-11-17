package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
import dev.frosthaven.ranchercompanion.ranch.Roster;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * What the hatchery screen shows (server to client; opens the screen or refreshes it): the hatchery's title, the egg
 * (its incubation rides on the item, for the hover), the hatch checks that fail now, and the viewer's whole roster.
 */
public record HatcheryView(BlockPos pos, Component title, ItemStack egg, boolean ready, List<String> problems, int here, List<Row> rows) implements CustomPacketPayload {
    /** where a drekki is, as the roster column says it */
    public static final int HERE = 0, WITH_YOU = 1, CARRIED = 2, ELSEWHERE = 3, NO_HATCHERY = 4;

    public record Row(UUID id, Component name, int line, int horn, int hat, int stage, int where, Component place) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.of((b, r) -> {
            UUIDUtil.STREAM_CODEC.encode(b, r.id); ComponentSerialization.STREAM_CODEC.encode(b, r.name);
            b.writeVarInt(r.line); b.writeVarInt(r.horn); b.writeVarInt(r.hat); b.writeVarInt(r.stage); b.writeVarInt(r.where);
            ComponentSerialization.STREAM_CODEC.encode(b, r.place);
        }, b -> new Row(UUIDUtil.STREAM_CODEC.decode(b), ComponentSerialization.STREAM_CODEC.decode(b), b.readVarInt(), b.readVarInt(),
            b.readVarInt(), b.readVarInt(), b.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(b)));
    }

    public static final Type<HatcheryView> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "hatchery_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HatcheryView> CODEC = StreamCodec.of((b, v) -> {
        b.writeBlockPos(v.pos); ComponentSerialization.STREAM_CODEC.encode(b, v.title); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, v.egg);
        b.writeBoolean(v.ready); ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(b, v.problems); b.writeVarInt(v.here);
        Row.CODEC.apply(ByteBufCodecs.list()).encode(b, v.rows);
    }, b -> new HatcheryView(b.readBlockPos(), ComponentSerialization.STREAM_CODEC.decode(b), ItemStack.OPTIONAL_STREAM_CODEC.decode(b),
        b.readBoolean(), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(b), b.readVarInt(), Row.CODEC.apply(ByteBufCodecs.list()).decode(b)));

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static Component title(HatcheryBlockEntity be) {
        return be.name() != null ? be.name() : Component.translatable("block.rancher_companion.hatchery");
    }

    private static Component where(ServerPlayer p, Roster.Entry e) {
        if (e.home == null) return Component.empty();
        if (p.level().dimension().equals(e.home.dimension()) && p.level().isLoaded(e.home.pos())
            && p.level().getBlockEntity(e.home.pos()) instanceof HatcheryBlockEntity h && h.name() != null) return h.name();
        BlockPos b = e.home.pos();
        return Component.literal(b.getX() + ", " + b.getY() + ", " + b.getZ());
    }

    public static HatcheryView of(ServerPlayer p, HatcheryBlockEntity be) {
        var home = Roster.at(p.level(), be.getBlockPos());
        Roster r = Ranch.roster(p.server);
        List<Row> rows = new ArrayList<>();
        for (Roster.Entry e : r.of(p.getUUID())) {
            int where = switch (e.where) {
                case OUT -> WITH_YOU;
                case CARRIED -> CARRIED;
                case NONE -> NO_HATCHERY;
                case HOME -> e.livesAt(home) ? HERE : ELSEWHERE;
            };
            rows.add(new Row(e.id, Ranch.label(e.save), e.save.getInt("Line"), e.save.getInt("Horn"), e.save.getInt("Hat"),
                Math.max(1, e.save.getInt("Stage")), where, where == ELSEWHERE ? where(p, e) : Component.empty()));
        }
        boolean ready = be.hasEgg() && dev.frosthaven.ranchercompanion.look.Incubation.ready(be.incubation());
        List<String> problems = ready ? Ranch.hatchProblems(p.serverLevel(), be.getBlockPos()) : List.of();
        return new HatcheryView(be.getBlockPos(), title(be), be.eggWithProgress(), ready, problems, r.at(home).size(), rows);
    }
}
