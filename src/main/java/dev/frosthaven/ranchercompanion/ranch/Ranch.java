package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.CarrierItem;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Incubation;
import dev.frosthaven.ranchercompanion.look.Lineage;
import dev.frosthaven.ranchercompanion.net.HatcheryView;
import dev.frosthaven.ranchercompanion.ranch.Roster.Entry;
import dev.frosthaven.ranchercompanion.ranch.Roster.Where;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The server's side of owning drekki (spec, Checkout and Pup release): hatching, checking in and out, carriers,
 * releasing, and keeping each drekki in the world exactly once. The {@link Roster} is the truth; a live entity that
 * disagrees with it (a stale copy from a chunk, a released or carried drekki) is removed when it joins a level.
 */
public final class Ranch {
    private Ranch() {}

    /** the live entity of each drekki, by roster id */
    private static final Map<UUID, DrekkiEntity> LIVE = new HashMap<>();

    public static Roster roster(MinecraftServer s) { return Roster.get(s); }

    private static void msg(Player p, String key, Object... args) {
        p.displayClientMessage(Component.translatable("message.rancher_companion." + key, args), true);
    }

    private static DrekkiEntity live(UUID id) {
        DrekkiEntity d = LIVE.get(id);
        return d != null && !d.isRemoved() ? d : null;
    }

    /** "Pip", or the bloodline for an unnamed one (spec, Pup release: Names) */
    public static Component label(CompoundTag save) {
        if (save.contains("CustomName")) {
            Component c = Component.Serializer.fromJson(save.getString("CustomName"), net.minecraft.core.RegistryAccess.EMPTY);
            if (c != null) return c;
        }
        return Component.literal(Lineage.of(save.getInt("Line")).name());
    }

    public static Component ownerName(DrekkiEntity d) {
        var server = d.level().getServer();
        if (server == null || d.owner() == null) return Component.literal("?");
        return server.getProfileCache() == null ? Component.literal("?")
            : Component.literal(server.getProfileCache().get(d.owner()).map(com.mojang.authlib.GameProfile::getName).orElse("?"));
    }

    private static CompoundTag save(DrekkiEntity d) {
        CompoundTag t = new CompoundTag();
        d.saveWithoutId(t);
        return t;
    }

    /** a name or hat change: keep the record in step */
    public static void touch(DrekkiEntity d) {
        if (d.level().isClientSide || d.drekkiId() == null) return;
        Entry e = roster(d.level().getServer()).get(d.drekkiId());
        if (e != null) roster(d.level().getServer()).snapshot(e, save(d));
    }

    /** a new entity from a record, bound to its owner, at a spot (not yet added to the level) */
    private static DrekkiEntity spawn(ServerLevel level, Entry e, BlockPos at, BlockPos home) {
        DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
        if (d == null) return null;
        d.load(e.save);
        d.setUUID(UUID.randomUUID());
        d.bind(e.owner, e.id, home);
        d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        d.setHealth(Math.max(d.getHealth(), 1));
        return d;
    }

    private static void add(ServerLevel level, DrekkiEntity d) {
        LIVE.put(d.drekkiId(), d);
        level.addFreshEntity(d);
    }

    private static void poof(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.POOF, x, y + 0.3, z, 12, 0.25, 0.25, 0.25, 0.02);
        level.playSound(null, x, y, z, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.8f, 1.2f);
    }

    // ---- the hatchery ----

    private static HatcheryBlockEntity hatchery(ServerPlayer p, BlockPos pos) {
        if (!(p.level().getBlockEntity(pos) instanceof HatcheryBlockEntity be)) return null;
        if (be.owner() == null) be.claim(p.getUUID());  /* a hatchery placed before 0.3.0 has no owner: the first user claims it */
        if (!be.owner().equals(p.getUUID())) {
            String who = p.server.getProfileCache() == null ? "?" : p.server.getProfileCache().get(be.owner()).map(com.mojang.authlib.GameProfile::getName).orElse("?");
            msg(p, "hatchery.not_yours", who);
            return null;
        }
        return be;
    }

    public static void putEgg(ServerPlayer p, BlockPos pos, ItemStack stack) {
        HatcheryBlockEntity be = hatchery(p, pos);
        if (be == null) return;
        if (be.hasEgg()) { msg(p, "hatchery.has_egg"); return; }
        be.putEgg(stack);
        stack.consume(1, p);
        p.level().playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.BLOCKS, 0.7f, 0.8f);
        msg(p, "hatchery.egg_in");
    }

    /** the hatch checks that fail now, as message keys (spec, Hatchery: Hatching) */
    public static List<String> hatchProblems(ServerLevel level, BlockPos pos) {
        List<String> out = new ArrayList<>();
        Ground g = new Ground(level);
        if (!g.ceiling(pos)) out.add("ceiling");
        if (!g.lit(pos)) out.add("light");
        if (roster(level.getServer()).at(Roster.at(level, pos)).size() >= HatcheryBlockEntity.CAP) out.add("full");
        else if (g.place(pos, 1) == null) out.add("room");
        return out;
    }

    public static void hatch(ServerPlayer p, BlockPos pos) {
        HatcheryBlockEntity be = hatchery(p, pos);
        if (be == null || !be.hasEgg() || !Incubation.ready(be.incubation())) return;
        ServerLevel level = p.serverLevel();
        List<String> bad = hatchProblems(level, pos);
        if (!bad.isEmpty()) {
            Component why = Component.translatable("message.rancher_companion.hatch." + bad.get(0));
            p.displayClientMessage(Component.translatable("message.rancher_companion.hatch.failed", why), true);
            return;
        }
        BlockPos at = new Ground(level).place(pos, 1);
        be.takeEgg();
        /* a trader egg: a random bloodline at its muted tier, random horns, dormant eyes (spec, Egg) */
        DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
        if (d == null) return;
        int fam = level.random.nextInt(Lineage.FAMILIES);
        d.setLook(1, fam * Lineage.TIERS, 0, Astral.DORMANT, level.random.nextInt(5));
        UUID id = UUID.randomUUID();
        d.bind(p.getUUID(), id, pos);
        d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        roster(p.server).add(id, p.getUUID(), save(d), Where.HOME, Roster.at(level, pos));
        add(level, d);
        poof(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
        level.playSound(null, pos, net.minecraft.sounds.SoundEvents.TURTLE_EGG_HATCH, SoundSource.BLOCKS, 1f, 1.1f);
        msg(p, "hatch.done");
        openHatchery(p, pos);
    }

    /** the screen's view to a player whose client has the mod's channel (a GameTest's mock player has none) */
    private static void send(ServerPlayer p, HatcheryBlockEntity be) {
        if (p.connection != null && p.connection.hasChannel(HatcheryView.TYPE)) PacketDistributor.sendToPlayer(p, HatcheryView.of(p, be));
    }

    public static void openHatchery(ServerPlayer p, BlockPos pos) {
        HatcheryBlockEntity be = hatchery(p, pos);
        if (be == null) return;
        send(p, be);
    }

    /** a broken hatchery: its drekki leave the world and go to No hatchery, kept as data (spec, Checkout) */
    public static void hatcheryBroken(ServerLevel level, BlockPos pos) {
        Roster r = roster(level.getServer());
        GlobalPos home = Roster.at(level, pos);
        for (Entry e : r.at(home)) {
            DrekkiEntity d = live(e.id);
            if (d != null) { r.snapshot(e, save(d)); poof(level, d.getX(), d.getY(), d.getZ()); d.discard(); }
        }
        r.orphan(home);
    }

    /** every few seconds: put back any of this hatchery's drekki that are missing (spec, Spawning) */
    public static void syncHatchery(ServerLevel level, BlockPos pos) {
        Roster r = roster(level.getServer());
        for (Entry e : r.at(Roster.at(level, pos))) {
            if (live(e.id) != null) continue;
            int stage = e.save.getInt("Stage");
            BlockPos at = new Ground(level).place(pos, Math.max(1, stage));
            if (at == null) continue;  /* no room: it waits as data */
            DrekkiEntity d = spawn(level, e, at, pos);
            if (d != null) add(level, d);
        }
    }

    // ---- the screen's actions ----

    public enum Action { CHECK_OUT, CHECK_IN, RELEASE, HATCH }

    public static void act(ServerPlayer p, BlockPos pos, Action a, UUID id) {
        HatcheryBlockEntity be = hatchery(p, pos);
        if (be == null) return;
        switch (a) {
            case CHECK_OUT -> checkOut(p, pos, id);
            case CHECK_IN -> checkIn(p, pos);
            case RELEASE -> release(p, id);
            case HATCH -> { hatch(p, pos); return; }
        }
        send(p, be);
    }

    /** the owner's checked-out drekki leaves the world (or its carrier) and is returned as data, or null when it cannot be */
    private static Entry pullOut(ServerPlayer p) {
        Roster r = roster(p.server);
        Entry out = r.out(p.getUUID());
        if (out == null) return null;
        if (out.where == Where.CARRIED) {
            ItemStack c = carrierOf(p, out.id);
            if (c == null) { msg(p, "checkout.carrier_missing"); return null; }
            clearCarrier(c);
        } else {
            DrekkiEntity d = live(out.id);
            if (d != null) { r.snapshot(out, save(d)); d.discard(); }
        }
        return out;
    }

    private static void checkOut(ServerPlayer p, BlockPos pos, UUID id) {
        Roster r = roster(p.server);
        ServerLevel level = p.serverLevel();
        GlobalPos home = Roster.at(level, pos);
        Entry e = r.get(id);
        if (e == null || !e.owner.equals(p.getUUID())) return;
        if (e.where == Where.HOME && !e.livesAt(home)) { msg(p, "checkout.elsewhere"); return; }
        if (e.where == Where.OUT || e.where == Where.CARRIED) return;
        Entry prev = r.out(p.getUUID());
        Ground g = new Ground(level);
        if (prev != null) {  /* a swap: the one out comes home here first (spec, Swaps) */
            if (g.place(pos, Math.max(1, prev.save.getInt("Stage"))) == null) { msg(p, "no_room"); return; }
            if (pullOut(p) == null) return;
            r.move(prev, Where.HOME, home);
            DrekkiEntity back = spawn(level, prev, g.place(pos, Math.max(1, prev.save.getInt("Stage"))), pos);
            if (back != null) add(level, back);
        }
        DrekkiEntity d = live(e.id);
        r.move(e, Where.OUT, null);
        if (d != null && d.level() == level) d.bind(p.getUUID(), e.id, null);
        else {
            if (d != null) d.discard();
            BlockPos at = g.place(pos, Math.max(1, e.save.getInt("Stage")));
            DrekkiEntity n = spawn(level, e, at != null ? at : p.blockPosition(), null);
            if (n != null) add(level, n);
        }
        msg(p, "checkout.done", label(e.save));
    }

    private static void checkIn(ServerPlayer p, BlockPos pos) {
        Roster r = roster(p.server);
        ServerLevel level = p.serverLevel();
        GlobalPos home = Roster.at(level, pos);
        Entry out = r.out(p.getUUID());
        if (out == null) return;
        if (r.at(home).size() >= HatcheryBlockEntity.CAP) { msg(p, "hatch.full"); return; }
        BlockPos at = new Ground(level).place(pos, Math.max(1, out.save.getInt("Stage")));
        if (at == null) { msg(p, "no_room"); return; }
        DrekkiEntity d = out.where == Where.OUT ? live(out.id) : null;
        if (d != null && d.level() == level) {
            d.bind(p.getUUID(), out.id, pos);
            d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, d.getYRot(), 0);
            d.getNavigation().stop();
            r.move(out, Where.HOME, home);
            r.snapshot(out, save(d));
        } else {
            if (pullOut(p) == null) return;
            r.move(out, Where.HOME, home);
            DrekkiEntity n = spawn(level, out, at, pos);
            if (n != null) add(level, n);
        }
        msg(p, "checkin.done", label(out.save));
    }

    /** released to the wild: it poofs on the spot and drops its hat; its record is gone (spec, Pup release) */
    private static void release(ServerPlayer p, UUID id) {
        Roster r = roster(p.server);
        Entry e = r.get(id);
        if (e == null || !e.owner.equals(p.getUUID())) return;
        Component name = label(e.save);
        int hat = e.save.getInt("Hat");
        DrekkiEntity d = live(id);
        if (d != null) {
            hat = d.hat();
            ServerLevel l = (ServerLevel) d.level();
            poof(l, d.getX(), d.getY(), d.getZ());
            if (hat > 0) d.spawnAtLocation(DrekkiItems.hatStack(hat));
            d.discard();
        } else {
            if (e.where == Where.CARRIED) { ItemStack c = carrierOf(p, id); if (c != null) clearCarrier(c); }
            if (hat > 0) p.getInventory().placeItemBackInInventory(DrekkiItems.hatStack(hat));
        }
        r.remove(id);
        LIVE.remove(id);
        msg(p, "release.done", name);
    }

    // ---- carriers ----

    private static ItemStack carrierOf(Player p, UUID id) {
        for (ItemStack s : p.getInventory().items) if (id.equals(s.get(DrekkiData.CARRIED.get()))) return s;
        for (ItemStack s : p.getInventory().offhand) if (id.equals(s.get(DrekkiData.CARRIED.get()))) return s;
        return null;
    }

    private static void clearCarrier(ItemStack c) {
        c.remove(DrekkiData.CARRIED.get());
        c.remove(DrekkiData.CARRIED_NAME.get());
    }

    public static void carry(ServerPlayer p, DrekkiEntity d, ItemStack carrier) {
        Roster r = roster(p.server);
        Entry e = r.get(d.drekkiId());
        if (e == null) return;
        r.snapshot(e, save(d));
        r.move(e, Where.CARRIED, null);
        carrier.set(DrekkiData.CARRIED.get(), e.id);
        carrier.set(DrekkiData.CARRIED_NAME.get(), label(e.save).getString());
        poof((ServerLevel) d.level(), d.getX(), d.getY(), d.getZ());
        p.level().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1f, 0.8f);
        d.discard();
    }

    public static void letOut(ServerPlayer p, ItemStack carrier, BlockPos at) {
        Roster r = roster(p.server);
        Entry e = r.get(carrier.get(DrekkiData.CARRIED.get()));
        if (e == null || e.where != Where.CARRIED) { clearCarrier(carrier); msg(p, "carrier.empty_now"); return; }
        if (!e.owner.equals(p.getUUID())) {
            String who = p.server.getProfileCache() == null ? "?" : p.server.getProfileCache().get(e.owner).map(com.mojang.authlib.GameProfile::getName).orElse("?");
            msg(p, "not_yours", who);
            return;
        }
        DrekkiEntity d = spawn(p.serverLevel(), e, at, null);
        if (d == null) return;
        if (!p.level().noCollision(d)) d.teleportNear(p.blockPosition());
        r.move(e, Where.OUT, null);
        clearCarrier(carrier);
        add(p.serverLevel(), d);
        p.level().playSound(null, at, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1f, 1f);
    }

    // ---- keeping each drekki in the world once ----

    /** a drekki entity joins a level: kept only when the roster says it belongs there and no other copy is live */
    public static void onJoin(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent ev) {
        if (ev.getLevel().isClientSide || !(ev.getEntity() instanceof DrekkiEntity d) || !d.owned()) return;
        Entry e = roster(ev.getLevel().getServer()).get(d.drekkiId());
        DrekkiEntity other = live(d.drekkiId());
        boolean ok = e != null && (other == null || other == d)
            && (e.where == Where.OUT && d.isOut() || e.where == Where.HOME && !d.isOut() && e.home != null
                && e.home.pos().equals(d.home()) && e.home.dimension().equals(ev.getLevel().dimension()));
        if (!ok) { ev.setCanceled(true); return; }
        LIVE.put(d.drekkiId(), d);
    }

    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent ev) {
        if (ev.getLevel().isClientSide || !(ev.getEntity() instanceof DrekkiEntity d) || !d.owned()) return;
        if (LIVE.get(d.drekkiId()) != d) return;
        LIVE.remove(d.drekkiId());
        if (d.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK || d.getRemovalReason() == Entity.RemovalReason.UNLOADED_WITH_PLAYER) touch(d);
    }

    public static void died(DrekkiEntity d) {
        Roster r = roster(d.level().getServer());
        Entry e = r.get(d.drekkiId());
        if (e == null) return;
        r.remove(e.id);
        LIVE.remove(e.id);
        Player owner = d.ownerPlayer();
        if (owner == null) owner = d.level().getServer().getPlayerList().getPlayer(e.owner);
        if (owner != null) owner.sendSystemMessage(Component.translatable("message.rancher_companion.died", label(e.save)));
    }

    /** a player leaves: the checked-out drekki leaves with them, kept as data */
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Roster r = roster(p.server);
        Entry out = r.out(p.getUUID());
        if (out == null || out.where != Where.OUT) return;
        DrekkiEntity d = live(out.id);
        if (d != null) { r.snapshot(out, save(d)); d.discard(); }
    }

    /** every second: a checked-out drekki that is not beside its owner (login, respawn, another dimension, an unloaded chunk) comes back next to them */
    public static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post ev) {
        MinecraftServer s = ev.getServer();
        if (s.getTickCount() % 20 != 0) return;
        Roster r = roster(s);
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator()) continue;
            Entry out = r.out(p.getUUID());
            if (out == null || out.where != Where.OUT) continue;
            DrekkiEntity d = live(out.id);
            if (d != null && d.level() == p.level()) continue;
            if (d != null) { r.snapshot(out, save(d)); d.discard(); }
            DrekkiEntity n = spawn(p.serverLevel(), out, p.blockPosition(), null);
            if (n == null) continue;
            n.teleportNear(p.blockPosition());
            add(p.serverLevel(), n);
        }
        if (s.getTickCount() % 600 == 0) for (DrekkiEntity d : List.copyOf(LIVE.values())) if (!d.isRemoved()) touch(d);
    }

    public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent ev) { LIVE.clear(); }
}
