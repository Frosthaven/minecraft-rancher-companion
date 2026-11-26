package dev.frosthaven.ranchercompanion.net;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.ranch.Pairing;
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
 * (its incubation rides on the item, for the hover), the hatch checks that fail now, the viewer's whole roster, and what
 * stops a pairing here ("" for nothing; spec, Breeding: Pairing), and the breeding in progress: its parents (null for none)
 * and the overworld day time it started (the screen counts its day from the client's day time), and the coats the viewer
 * has ever had (a bit per Lineage index: the child's preview shows only those and the muted ones).
 */
public record HatcheryView(BlockPos pos, Component title, boolean named, ItemStack egg, boolean ready, List<String> problems, int here, List<Row> rows, String pairBlock, UUID breedA, UUID breedB, long breedStart, long coats, boolean breedTwin) implements CustomPacketPayload {
    /** where a drekki is, as the roster column says it */
    /** EMPLOYED (0.4.11): a game before it shows such a row as No hatchery */
    public static final int HERE = 0, WITH_YOU = 1, CARRIED = 2, ELSEWHERE = 3, NO_HATCHERY = 4, EMPLOYED = 5, TOMB = 6;

    /** rest: days of rest left after a pairing (0 none); breeding: pairing now; fit: it may be picked to pair here now; traits: what it can pass on */
    public record Row(UUID id, Component name, int line, int horn, int hat, int stage, int where, Component place, int rest, boolean breeding, boolean fit, List<String> traits, int astral, int astral2, int band, boolean ghost) {
        static final StreamCodec<RegistryFriendlyByteBuf, Row> CODEC = StreamCodec.of((b, r) -> {
            UUIDUtil.STREAM_CODEC.encode(b, r.id); ComponentSerialization.STREAM_CODEC.encode(b, r.name);
            b.writeVarInt(r.line); b.writeVarInt(r.horn); b.writeVarInt(r.hat); b.writeVarInt(r.stage); b.writeVarInt(r.where);
            ComponentSerialization.STREAM_CODEC.encode(b, r.place);
            b.writeVarInt(r.rest); b.writeBoolean(r.breeding); b.writeBoolean(r.fit); ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(b, r.traits);
            b.writeVarInt(r.astral); b.writeVarInt(r.astral2); b.writeVarInt(r.band); b.writeBoolean(r.ghost);
        }, b -> new Row(UUIDUtil.STREAM_CODEC.decode(b), ComponentSerialization.STREAM_CODEC.decode(b), b.readVarInt(), b.readVarInt(),
            b.readVarInt(), b.readVarInt(), b.readVarInt(), ComponentSerialization.STREAM_CODEC.decode(b),
            b.readVarInt(), b.readBoolean(), b.readBoolean(), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(b), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
    }

    public static final Type<HatcheryView> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "hatchery_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HatcheryView> CODEC = StreamCodec.of((b, v) -> {
        b.writeBlockPos(v.pos); ComponentSerialization.STREAM_CODEC.encode(b, v.title); b.writeBoolean(v.named); ItemStack.OPTIONAL_STREAM_CODEC.encode(b, v.egg);
        b.writeBoolean(v.ready); ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(b, v.problems); b.writeVarInt(v.here);
        Row.CODEC.apply(ByteBufCodecs.list()).encode(b, v.rows); ByteBufCodecs.STRING_UTF8.encode(b, v.pairBlock); b.writeLong(v.coats);
        b.writeBoolean(v.breedA != null);
        if (v.breedA != null) { UUIDUtil.STREAM_CODEC.encode(b, v.breedA); UUIDUtil.STREAM_CODEC.encode(b, v.breedB); b.writeVarLong(v.breedStart); b.writeBoolean(v.breedTwin); }
    }, b -> new HatcheryView(b.readBlockPos(), ComponentSerialization.STREAM_CODEC.decode(b), b.readBoolean(), ItemStack.OPTIONAL_STREAM_CODEC.decode(b),
        b.readBoolean(), ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(b), b.readVarInt(), Row.CODEC.apply(ByteBufCodecs.list()).decode(b),
        ByteBufCodecs.STRING_UTF8.decode(b), null, null, 0, b.readLong(), false).breeding(b));

    /** the breeding part, read after the rest */
    private HatcheryView breeding(RegistryFriendlyByteBuf b) {
        if (!b.readBoolean()) return this;
        return new HatcheryView(pos, title, named, egg, ready, problems, here, rows, pairBlock, UUIDUtil.STREAM_CODEC.decode(b), UUIDUtil.STREAM_CODEC.decode(b), b.readVarLong(), coats, b.readBoolean());
    }

    public boolean breeding() { return breedA != null; }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private static Component title(HatcheryBlockEntity be) {
        return be.name() != null ? be.name() : Component.translatable("block.rancher_companion.hatchery");
    }

    private static Component where(ServerPlayer p, Roster.Entry e) {
        if (e.home == null) return Component.empty();
        if (p.level().dimension().equals(e.home.dimension()) && p.level().isLoaded(e.home.pos())
            && p.level().getBlockEntity(e.home.pos()) instanceof HatcheryBlockEntity h && h.name() != null) return h.name();
        return Component.translatable("block.rancher_companion.hatchery");  /* unnamed, or too far to read its name: the plain name, never coordinates */
    }

    public static HatcheryView of(ServerPlayer p, HatcheryBlockEntity be) {
        var home = Roster.at(p.level(), be.getBlockPos());
        Roster r = Ranch.roster(p.server);
        long now = Roster.now(p.server);
        List<Row> rows = new ArrayList<>();
        for (Roster.Entry e : r.of(p.getUUID())) {
            int where = switch (e.where) {
                case OUT -> WITH_YOU;
                case CARRIED -> CARRIED;
                case NONE -> NO_HATCHERY;
                case WORK -> EMPLOYED;
                case TOMB -> TOMB;
                case HOME -> e.livesAt(home) ? HERE : ELSEWHERE;
            };
            rows.add(new Row(e.id, Ranch.label(e.save), e.save.getInt("Line"), e.save.getInt("Horn"), e.save.getInt("Hat"),
                Pairing.stage(e), where, where == ELSEWHERE ? where(p, e) : where == EMPLOYED ? dev.frosthaven.ranchercompanion.ranch.Seats.title(e.role) : Component.empty(),
                e.breeding(now) ? 0 : e.restDays(now), e.breeding(now), Pairing.fit(e, home, now), Pairing.traits(e),
                e.save.getInt("Astral"), e.save.getInt("Astral2"), Pairing.band(e), Pairing.ghost(e)));
        }
        boolean ready = be.hasEgg() && dev.frosthaven.ranchercompanion.look.Incubation.ready(be.incubation());
        List<String> problems = ready ? Ranch.hatchProblems(p.serverLevel(), be.getBlockPos()) : List.of();
        String block = Pairing.blocked(r, home, be);
        return new HatcheryView(be.getBlockPos(), title(be), be.name() != null, be.eggWithProgress(), ready, problems, r.at(home).size(), rows, block == null ? "" : block,
            be.breedA(), be.breedB(), be.breedStart(), r.coats(p.getUUID()), Pairing.starlight(be.breedTonic()));
    }
}
