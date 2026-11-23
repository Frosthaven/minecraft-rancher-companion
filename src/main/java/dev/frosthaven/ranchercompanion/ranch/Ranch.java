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

    /** every live drekki, for the effects that look around them */
    /** the live entity of a drekki by roster id, or null */
    public static DrekkiEntity liveOf(UUID id) { return live(id); }

    public static java.util.Collection<DrekkiEntity> lives() { return java.util.Collections.unmodifiableCollection(LIVE.values()); }

    /** the other drekki of its base hatchery (spec, Mood: Kinship) */
    public static int kin(MinecraftServer s, UUID id) {
        Entry e = roster(s).get(id);
        return e == null ? 0 : roster(s).kin(e);
    }

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

    static CompoundTag save(DrekkiEntity d) {
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
    static DrekkiEntity spawn(ServerLevel level, Entry e, BlockPos at, BlockPos home) {
        DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
        if (d == null) return null;
        d.load(e.save);
        d.setUUID(UUID.randomUUID());
        d.bind(e.owner, e.id, home);
        d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        d.setHealth(Math.max(d.getHealth(), 1));
        return d;
    }

    static void add(ServerLevel level, DrekkiEntity d) {
        LIVE.put(d.drekkiId(), d);
        dev.frosthaven.ranchercompanion.trait.Traits.changed();
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
        if (be.egg().has(DrekkiData.CARRIED.get())) {  /* a Phoenix egg brings its drekki back (Death) */
            ItemStack phoenix = be.egg().copy();
            if (Death.reborn(p, level, pos, at, phoenix)) {
                be.takeEgg();
                poof(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5);
                openHatchery(p, pos);
            } else msg(p, "hatch.cold");
            return;
        }
        be.takeEgg();
        /* a trader egg: a random bloodline at its muted tier, random horns, dormant eyes (spec, Egg) */
        DrekkiEntity d = RancherCompanion.DREKKI.get().create(level);
        if (d == null) return;
        int fam = level.random.nextInt(Lineage.FAMILIES);
        d.setLook(1, fam * Lineage.TIERS, 0, Astral.DORMANT, level.random.nextInt(5));
        d.applyStageHealth(true);
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
            case CHECK_IN -> { if (id != null && recall(p, pos, id)) break; checkIn(p, pos); }
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
            /* the carrier need not be at hand (lost, given away): the roster brings the drekki home, and that carrier,
               wherever it is, no longer matches */
            ItemStack c = carrierOf(p, out);
            if (c != null) clearCarrier(c);
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
        if (e.where == Where.WORK && d != null) { r.snapshot(e, save(d)); d.discard(); d = null; }  /* off its job, to its owner */
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
        if (r.at(home).size() >= HatcheryBlockEntity.CAP) { msg(p, "hatchery.full"); return; }
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
            if (e.where == Where.CARRIED) { ItemStack c = carrierOf(p, e); if (c != null) clearCarrier(c); }
            if (hat > 0) p.getInventory().placeItemBackInInventory(DrekkiItems.hatStack(hat));
        }
        r.remove(id);
        LIVE.remove(id);
        dev.frosthaven.ranchercompanion.trait.Traits.changed();
        msg(p, "release.done", name);
    }

    // ---- carriers ----

    private static ItemStack carrierOf(Player p, Entry e) {
        for (ItemStack s : p.getInventory().items) if (holds(s, e)) return s;
        for (ItemStack s : p.getInventory().offhand) if (holds(s, e)) return s;
        return null;
    }

    /** the carrier holds this drekki now: the drekki is carried, by this carry (an older carrier's code no longer matches) */
    private static boolean holds(ItemStack c, Entry e) {
        return e != null && e.where == Where.CARRIED && e.id.equals(c.get(DrekkiData.CARRIED.get()))
            && java.util.Objects.equals(e.carry, c.get(DrekkiData.CARRY_TOKEN.get()));
    }

    private static void clearCarrier(ItemStack c) {
        c.remove(DrekkiData.CARRIED.get());
        c.remove(DrekkiData.CARRIED_NAME.get());
        c.remove(DrekkiData.CARRY_TOKEN.get());
        c.remove(DrekkiData.CARRIED_OWNER.get());
        c.remove(DrekkiData.CARRIED_OWNER_NAME.get());
    }

    public static void carry(ServerPlayer p, DrekkiEntity d, ItemStack carrier) {
        Roster r = roster(p.server);
        Entry e = r.get(d.drekkiId());
        if (e == null) return;
        r.snapshot(e, save(d));
        r.move(e, Where.CARRIED, null);
        e.carry = UUID.randomUUID();
        carrier.set(DrekkiData.CARRIED.get(), e.id);
        carrier.set(DrekkiData.CARRY_TOKEN.get(), e.carry);
        carrier.set(DrekkiData.CARRIED_OWNER.get(), e.owner);
        carrier.set(DrekkiData.CARRIED_OWNER_NAME.get(), p.getGameProfile().getName());
        carrier.set(DrekkiData.CARRIED_NAME.get(), label(e.save).getString());
        poof((ServerLevel) d.level(), d.getX(), d.getY(), d.getZ());
        p.level().playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1f, 0.8f);
        d.discard();
    }

    public static void letOut(ServerPlayer p, ItemStack carrier, BlockPos at) {
        Roster r = roster(p.server);
        Entry e = r.get(carrier.get(DrekkiData.CARRIED.get()));
        if (!holds(carrier, e)) { clearCarrier(carrier); msg(p, "carrier.empty_now"); return; }
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
            && ((e.where == Where.OUT || e.where == Where.WORK) && d.isOut() || e.where == Where.HOME && !d.isOut() && e.home != null
                && e.home.pos().equals(d.home()) && e.home.dimension().equals(ev.getLevel().dimension()));
        /* a checked-out copy a chunk loads (not one this class just made): never while its owner is offline, it left
           with them; one told to stay only at its stay spot, an older spot's copy is stale */
        if (ok && other != d && e.where == Where.OUT) {
            MinecraftServer s = ev.getLevel().getServer();
            if (s.getPlayerList().getPlayer(e.owner) == null) ok = false;
            else if (e.stay != null && !(e.stay.dimension().equals(ev.getLevel().dimension()) && e.stay.pos().distSqr(d.blockPosition()) <= 16 * 16)) {
                ok = false;
                later(s, () -> place(s, e));
            }
        }
        if (!ok) { dev.frosthaven.ranchercompanion.Debug.log("server: refused a copy, {} (roster {}, live copy {})", dev.frosthaven.ranchercompanion.Debug.of(d), e == null ? "none" : e.where, other != null); ev.setCanceled(true); return; }
        d.setStaying(e.stay != null);
        d.setJob(e.where == Where.WORK ? e.role : 0);
        LIVE.put(d.drekkiId(), d);
        dev.frosthaven.ranchercompanion.trait.Traits.changed();
    }

    public static void onLeave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent ev) {
        if (ev.getLevel().isClientSide || !(ev.getEntity() instanceof DrekkiEntity d) || !d.owned()) return;
        if (LIVE.get(d.drekkiId()) != d) return;
        ServerPlayer owner = d.isOut() && d.owner() != null ? d.getServer().getPlayerList().getPlayer(d.owner()) : null;
        if (d.getRemovalReason() == null) {
            /* no removal: its chunk stopped ticking but stays loaded (the owner went far; a chunk loader holds it). It is
               still this drekki's one copy, so it stays live and is moved to its owner; forgetting it here made a
               second copy when the owner came back */
            if (owner != null) later(owner.server, () -> bringBack(owner));
            return;
        }
        LIVE.remove(d.drekkiId());
        dev.frosthaven.ranchercompanion.trait.Traits.changed();
        if (d.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK || d.getRemovalReason() == Entity.RemovalReason.UNLOADED_WITH_PLAYER) {
            touch(d);
            /* a checked-out drekki left behind in a chunk that unloaded: back to its owner, once the unload is done */
            if (owner != null) later(owner.server, () -> bringBack(owner));
        }
    }

    static void forgetLive(UUID id) { LIVE.remove(id); dev.frosthaven.ranchercompanion.trait.Traits.changed(); dev.frosthaven.ranchercompanion.life.Feeding.forget(id); }

    /** a player leaves: the checked-out drekki leaves with them, kept as data */
    public static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p)) return;
        Roster r = roster(p.server);
        Entry out = r.out(p.getUUID());
        if (out == null || out.where != Where.OUT) return;
        DrekkiEntity d = live(out.id);
        if (d != null) { r.snapshot(out, save(d)); d.discard(); }
        STAYS.remove(p.getUUID());
        ARRIVALS.remove(p.getUUID());
    }

    /* a checked-out drekki comes back next to its owner on the events that part them, with no polling: login, respawn,
       a dimension change, leaving spectator mode, and its chunk unloading while the owner is online */
    public static void onLogin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent ev) { if (ev.getEntity() instanceof ServerPlayer p) { bringBack(p); Death.giveEggs(p); Tombs.onLogin(p); } }
    public static void onRespawn(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent ev) { if (ev.getEntity() instanceof ServerPlayer p) bringBack(p); }
    public static void onDimension(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent ev) { if (ev.getEntity() instanceof ServerPlayer p) follow(p); }

    /** the owner joins a level: their checked-out drekki comes along when it is loaded in another one. Some portal mods
     *  move the player without the game's dimension change event (Immersive Portals, inside the Haven pack's Aeronautics,
     *  moved an owner to the Nether and left the drekki behind, 2026-11-20), but every move into a level goes through
     *  ServerLevel.addPlayer, which posts this event. After the current work: the player is not in the level yet */
    public static void onPlayerJoinLevel(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent ev) {
        if (!(ev.getEntity() instanceof ServerPlayer p) || ev.getLevel().isClientSide()) return;
        later(p.server, () -> follow(p));
        /* once more a second later: a portal mod can add the player to the level before it sets their position there
           (Immersive Portals did), and the drekki then lands at the old coordinates, out of reach */
        RECHECK.put(p.getUUID(), p.server.getTickCount() + 20);
    }

    /** owners to look at once more after joining a level: the server tick that is due */
    private static final Map<UUID, Integer> RECHECK = new HashMap<>();

    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post ev) {
        if (!ARRIVALS.isEmpty()) {
            int tick = ev.getServer().getTickCount();
            ARRIVALS.entrySet().removeIf(en -> arrive(ev.getServer(), en.getKey(), en.getValue(), tick));
        }
        if (!DUE.isEmpty()) {
            int tick = ev.getServer().getTickCount();
            List<Runnable> run = new ArrayList<>();
            DUE.removeIf(en -> { if (en.getKey() > tick) return false; run.add(en.getValue()); return true; });
            run.forEach(Runnable::run);
        }
        if (RECHECK.isEmpty()) return;
        int now = ev.getServer().getTickCount();
        RECHECK.entrySet().removeIf(en -> {
            if (en.getValue() > now) return false;
            ServerPlayer p = ev.getServer().getPlayerList().getPlayer(en.getKey());
            if (p != null) {
                bringBack(p);  /* next to them when far, in whatever level they are in */
                Entry out = roster(p.server).out(p.getUUID());
                DrekkiEntity d = out == null ? null : live(out.id);
                if (d != null) dev.frosthaven.ranchercompanion.RancherCompanion.LOG.info("Drekki {} rechecked: at {} in {}, owner at {} in {}", out.id,
                    d.blockPosition().toShortString(), d.level().dimension().location(), p.blockPosition().toShortString(), p.level().dimension().location());
            }
            return true;
        });
    }

    /** the owner's checked-out drekki, when it is loaded in another level, brought to them */
    public static void follow(ServerPlayer p) {
        Entry out = roster(p.server).out(p.getUUID());
        if (out == null || out.where != Where.OUT) return;
        DrekkiEntity d = live(out.id);
        if (d == null || d.level() == p.level() || out.stay != null) return;
        /* it leaves the level it was in now, and waits as data for a spot its owner walks over here (arrive) */
        roster(p.server).snapshot(out, save(d));
        d.discard();
        ARRIVALS.put(p.getUUID(), new Arrival(p.level().dimension(), p.position(), p.server.getTickCount()));
        dev.frosthaven.ranchercompanion.Debug.log("server: {} arrived in {} at {}; the drekki waits for a spot they walk over", p.getName().getString(),
            p.level().dimension().location(), p.blockPosition().toShortString());
    }

    /** an owner who just changed level. Their drekki appears on a spot they stood on after it, once they are 2 blocks
     *  past that spot: wherever a player walked is a good place to stand, on the right side of any portal, in the open,
     *  in their path. Spots picked around the owner landed in the portal's frame, behind it and out of sight
     *  (2026-11-21). An owner who does not walk (stands still, falls, flies) gets it the old way after 3 seconds */
    private static final class Arrival {
        final net.minecraft.resources.ResourceKey<Level> level; final net.minecraft.world.phys.Vec3 from; final int tick;
        net.minecraft.world.phys.Vec3 step;
        Arrival(net.minecraft.resources.ResourceKey<Level> level, net.minecraft.world.phys.Vec3 from, int tick) { this.level = level; this.from = from; this.tick = tick; }
    }
    private static final Map<UUID, Arrival> ARRIVALS = new HashMap<>();
    public static final int ARRIVE_WAIT = 60;

    /** one waiting owner, each tick while they wait; true when done with them */
    private static boolean arrive(MinecraftServer s, UUID id, Arrival a, int now) {
        ServerPlayer p = s.getPlayerList().getPlayer(id);
        if (p == null) return true;
        Entry out = roster(s).out(id);
        if (out == null || out.where != Where.OUT || out.stay != null || live(out.id) != null || !p.isAlive() || p.isSpectator()
            || p.level().dimension() != a.level || now - a.tick > ARRIVE_WAIT) {
            later(s, () -> bringBack(p));  /* the old way, whatever still applies */
            return true;
        }
        if (a.step == null) {
            if (p.onGround() && Math.hypot(p.getX() - a.from.x, p.getZ() - a.from.z) >= 1.5) a.step = p.position();
            return false;
        }
        if (p.position().distanceToSqr(a.step) < 2 * 2) return false;
        DrekkiEntity n = spawn(p.serverLevel(), out, BlockPos.containing(a.step), null);
        if (n == null) return true;
        n.moveTo(a.step.x, a.step.y, a.step.z, p.getYRot(), 0);
        if (!p.level().noCollision(n)) { later(s, () -> bringBack(p)); return true; }  /* a taller drekki under a low ceiling */
        add(p.serverLevel(), n);
        dev.frosthaven.ranchercompanion.RancherCompanion.LOG.info("Drekki {} brought to {} in {} at {}, where they walked (owner at {})", out.id, p.getName().getString(),
            p.level().dimension().location(), n.blockPosition().toShortString(), p.blockPosition().toShortString());
        return true;
    }
    /** fires before the mode changes: look again on the next tick */
    public static void onGameMode(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent ev) {
        if (ev.getEntity() instanceof ServerPlayer p) later(p.server, () -> bringBack(p));
    }

    /** runs r after the current work: server.execute runs at once when called on the server thread, which is too soon
     *  in an unload or before a game mode applies; tell always queues */
    private static void later(MinecraftServer s, Runnable r) { s.tell(new net.minecraft.server.TickTask(s.getTickCount(), r)); }

    /** the owner's checked-out drekki (not in a carrier) beside them: kept when near, moved when far in the same level,
     *  otherwise saved and spawned again next to them */
    public static void bringBack(ServerPlayer p) {
        /* the game mode itself, not isSpectator(): the same for a real player, and true of GameTest's mock player too */
        if (!p.isAlive() || p.gameMode.getGameModeForPlayer() == net.minecraft.world.level.GameType.SPECTATOR || p.hasDisconnected()) return;
        Roster r = roster(p.server);
        Entry out = r.out(p.getUUID());
        if (out == null || out.where != Where.OUT) return;
        if (out.stay != null) { STAYS.put(p.getUUID(), out.stay); place(p.server, out); return; }  /* told to stay: at its spot, not with them */
        if (ARRIVALS.containsKey(p.getUUID())) return;  /* it waits for a spot its owner walks over */
        DrekkiEntity d = live(out.id);
        if (d != null && d.level() == p.level()) {
            if (d.distanceTo(p) > 12) d.teleportNear(p);
            return;
        }
        if (d != null) { r.snapshot(out, save(d)); d.discard(); }
        DrekkiEntity n = spawn(p.serverLevel(), out, p.blockPosition(), null);
        if (n == null) return;
        n.teleportNear(p);
        add(p.serverLevel(), n);
    }

    // ---- stay and follow (spec, Stay and follow) ----

    /** the owners online whose checked-out drekki stays, and where: a loading chunk looks here */
    private static final Map<UUID, GlobalPos> STAYS = new HashMap<>();
    /** within this of its owner a drekki is with them: the whistle and the screen toggle it; beyond, they call it first */
    static final double WITH = 30;
    /** called by the whistle from within this, it walks to its owner when a path reaches them */
    static final double CALL_WALK = 20;

    /** a staying drekki stands at its spot while that chunk is loaded and its owner is online; otherwise it is data */
    private static void place(MinecraftServer s, Entry out) {
        if (out.stay == null || out.where != Where.OUT || live(out.id) != null || s.getPlayerList().getPlayer(out.owner) == null) return;
        ServerLevel level = s.getLevel(out.stay.dimension());
        if (level == null || !level.isLoaded(out.stay.pos())) return;
        DrekkiEntity n = spawn(level, out, out.stay.pos(), null);
        if (n == null) return;
        n.setStaying(true);
        add(level, n);
        Seats.Seat seat = Seats.get().at(level, out.stay.pos());  /* sent to a seat (Go here): it sits there again */
        if (seat != null && seat.free()) seat.sit(n);
    }

    /** the chunk of a stay spot loads: its drekki is put there next tick, unless the chunk brings its own copy first */
    public static void onChunkLoad(net.neoforged.neoforge.event.level.ChunkEvent.Load ev) {
        if (STAYS.isEmpty() || !(ev.getLevel() instanceof ServerLevel level)) return;
        var cp = ev.getChunk().getPos();
        for (var en : STAYS.entrySet()) {
            GlobalPos g = en.getValue();
            if (!g.dimension().equals(level.dimension()) || g.pos().getX() >> 4 != cp.x || g.pos().getZ() >> 4 != cp.z) continue;
            UUID owner = en.getKey();
            MinecraftServer s = level.getServer();
            later(s, () -> { Entry out = roster(s).out(owner); if (out != null) place(s, out); });
        }
    }

    /** the whistle: a drekki with its owner changes between staying and following; one that is not comes to them and follows */
    public static boolean whistle(ServerPlayer p) {
        Entry out = roster(p.server).out(p.getUUID());
        return setStay(p, out != null && out.stay == null, true);
    }

    public static boolean setStay(ServerPlayer p, boolean stay) { return setStay(p, stay, false); }

    /** work for a later tick (the whistle's second note): the tick it is due, and what to do */
    private static final List<Map.Entry<Integer, Runnable>> DUE = new ArrayList<>();
    public static void after(MinecraftServer s, int ticks, Runnable r) { DUE.add(Map.entry(s.getTickCount() + ticks, r)); }

    /** the screen's Stay and Follow, and the whistle. Stay holds only for a drekki with its owner (loaded, the same
     *  level, within 30 blocks); otherwise this calls it to them, following. call (the whistle): following, it jumps to
     *  them from wherever it is, near too. Returns whether it stays now */
    private static boolean setStay(ServerPlayer p, boolean stay, boolean call) {
        Roster r = roster(p.server);
        Entry out = r.out(p.getUUID());
        if (out == null || out.where != Where.OUT) { msg(p, "stay.none"); return false; }
        DrekkiEntity d = live(out.id);
        if (d != null && d.ghost()) { msg(p, "stay.none"); return false; }
        stay &= d != null && d.level() == p.level() && d.distanceToSqr(p) <= WITH * WITH;
        if (stay) {
            r.stay(out, Roster.at(d.level(), d.blockPosition()));
            STAYS.put(p.getUUID(), out.stay);
            d.setStaying(true);
            d.disengage();  /* a stay command ends its fights (decided 2026-11-22) */
        } else {
            r.stay(out, null);
            STAYS.remove(p.getUUID());
            ARRIVALS.remove(p.getUUID());  /* called: it comes now */
            if (d != null) { d.setStaying(false); d.goHere = null; if (d.isPassenger()) d.stopRiding(); }  /* off its seat or train, to follow */
            if (call && d != null && d.level() == p.level()) {
                /* the whistle: it walks over when it can. It jumps to them only from more than 20 blocks or with no
                   way to walk there */
                var path = d.distanceToSqr(p) > CALL_WALK * CALL_WALK ? null : d.getNavigation().createPath(p, 1);
                if (path == null || !path.canReach()) {
                    d.teleportNear(p);
                    if (d.distanceToSqr(p) > 8 * 8) d.jumpTo(p.position());  /* no safe spot around them (they fly): onto their own, unless that is in lava or fire */
                }
            } else bringBack(p);
        }
        Component who = out.save.contains("CustomName") ? label(out.save) : Component.translatable("drekki.rancher_companion.yours");
        msg(p, stay ? "stay.on" : "stay.off", who);
        return stay;
    }

    // ---- go here (spec, Go here): entity/GoHere sends it, these keep the roster in step ----

    /** the owner's checked-out drekki when it is live with them (the same level, no ghost), else null */
    public static DrekkiEntity withOwner(ServerPlayer p) {
        Entry out = roster(p.server).out(p.getUUID());
        DrekkiEntity d = out == null || out.where != Where.OUT ? null : live(out.id);
        return d != null && !d.ghost() && d.level() == p.level() ? d : null;
    }

    /** the whistle's call: it comes to its owner and follows (from another level or an unloaded chunk too) */
    public static void call(ServerPlayer p) { setStay(p, false, true); }

    /** on its way somewhere: it stays nowhere and follows nobody until it gets there */
    public static void leaveStay(DrekkiEntity d) {
        Entry out = d.owner() == null ? null : roster(d.getServer()).out(d.owner());
        if (out != null && out.stay != null) roster(d.getServer()).stay(out, null);
        if (d.owner() != null) { STAYS.remove(d.owner()); ARRIVALS.remove(d.owner()); }
        d.setStaying(false);
    }

    /** it got where it was sent (or rides a seat that moves): it stays here, the roster keeps the spot */
    public static void stayHere(DrekkiEntity d) {
        Entry out = d.owner() == null ? null : roster(d.getServer()).out(d.owner());
        if (out == null || out.where != Where.OUT) return;
        roster(d.getServer()).stay(out, Roster.at(d.level(), d.blockPosition()));
        STAYS.put(d.owner(), out.stay);
        d.setStaying(true);
        d.disengage();
    }

    /** sat on a Create job seat (spec, Go here: Employed): no longer with its owner, who may take out another; it
     *  stays in the world while they are offline, and the hatchery screen recalls it */
    public static void employ(DrekkiEntity d, int role) {
        Roster r = roster(d.getServer());
        Entry out = d.owner() == null ? null : r.out(d.owner());
        if (out == null || !out.id.equals(d.drekkiId()) || out.where != Where.OUT) { stayHere(d); return; }
        r.move(out, Where.WORK, null);
        out.role = role;
        r.stay(out, Roster.at(d.level(), d.blockPosition()));
        STAYS.remove(d.owner());
        d.setStaying(true);
        d.setJob(role);
        /* at work it fights nothing: Passive, and its stance stays so until it is recalled (DrekkiMenu) */
        d.setStance(dev.frosthaven.ranchercompanion.life.Life.Stance.PASSIVE);
        touch(d);
    }

    /** once a second for a staying drekki. On a seat that moves (a train) its spot moves with it, so a chunk loading it
     *  on the way keeps it. Employed and off its seat for 2 seconds (the train taken apart, the seat broken): it goes
     *  home to its hatchery, or to No hatchery without one */
    public static void workTick(DrekkiEntity d) {
        if (d.tickCount % 20 != 0 || d.drekkiId() == null) return;
        Roster r = roster(d.getServer());
        Entry e = r.get(d.drekkiId());
        if (e == null || e.stay == null) return;
        if (e.where == Where.WORK && !d.isPassenger()) {
            if (Seats.get().reseat(d)) { d.offJob = 0; return; }  /* dropped at a portal: back on its train seat */
            if ((d.offJob += 20) < 40) return;
            r.snapshot(e, save(d));
            d.discard();
            r.move(e, e.base != null ? Where.HOME : Where.NONE, e.base);  /* its hatchery puts it back (syncHatchery) */
            ServerPlayer p = d.getServer().getPlayerList().getPlayer(e.owner);
            if (p != null) msg(p, "gohere.job_ended", label(e.save));
            return;
        }
        d.offJob = 0;
        if (e.where == Where.WORK) Seats.get().seated(d);
        if (d.isPassenger() && (!e.stay.dimension().equals(d.level().dimension()) || e.stay.pos().distSqr(d.blockPosition()) > 4 * 4))
            r.stay(e, Roster.at(d.level(), d.blockPosition()));
    }

    /** employed in a Create role (Where.WORK) */
    public static boolean employed(DrekkiEntity d) {
        Entry e = d.drekkiId() == null || d.getServer() == null ? null : roster(d.getServer()).get(d.drekkiId());
        return e != null && e.where == Where.WORK;
    }


    /** the drekki screen's Recall from job (spec, Go here: Employed): off its seat, checked out with its owner again and
     *  following; when they have another drekki out, home to its hatchery instead */
    public static void recallFromJob(ServerPlayer p, DrekkiEntity d) {
        Roster r = roster(p.server);
        Entry e = d.drekkiId() == null ? null : r.get(d.drekkiId());
        if (e == null || e.where != Where.WORK || !e.owner.equals(p.getUUID())) return;
        if (d.isPassenger()) d.stopRiding();
        d.setJob(0);
        d.goHere = null;
        if (r.out(p.getUUID()) != null) {  /* its owner has another out: home to its hatchery (syncHatchery puts it there) */
            r.snapshot(e, save(d));
            d.discard();
            r.move(e, e.base != null ? Where.HOME : Where.NONE, e.base);
            msg(p, e.base != null ? "gohere.recall_home" : "gohere.recall_none", label(e.save));
            return;
        }
        r.move(e, Where.OUT, null);  /* with its owner again; the move clears the stay spot */
        d.setStaying(false);
        msg(p, "stay.off", label(e.save));
    }

    /** the hatchery screen's Check in on an employed drekki: off its job, home here */
    private static boolean recall(ServerPlayer p, BlockPos pos, UUID id) {
        Roster r = roster(p.server);
        Entry e = r.get(id);
        if (e == null || !e.owner.equals(p.getUUID()) || e.where != Where.WORK) return false;
        ServerLevel level = p.serverLevel();
        GlobalPos home = Roster.at(level, pos);
        if (r.at(home).size() >= HatcheryBlockEntity.CAP) { msg(p, "hatchery.full"); return true; }
        BlockPos at = new Ground(level).place(pos, Math.max(1, e.save.getInt("Stage")));
        if (at == null) { msg(p, "no_room"); return true; }
        DrekkiEntity d = live(e.id);
        if (d != null) { r.snapshot(e, save(d)); d.discard(); }
        r.move(e, Where.HOME, home);
        DrekkiEntity n = spawn(level, e, at, pos);
        if (n != null) add(level, n);
        msg(p, "checkin.done", label(e.save));
        return true;
    }

    public static void tell(ServerPlayer p, String key, DrekkiEntity d) {
        Entry out = d != null && d.drekkiId() != null ? roster(p.server).get(d.drekkiId()) : roster(p.server).out(p.getUUID());
        Component who = out != null && out.save.contains("CustomName") ? label(out.save) : Component.translatable("drekki.rancher_companion.yours");
        msg(p, key, who);
    }

    /** the world saves: every live drekki's state goes to the roster first, so the roster saved with it is current */
    public static void onSave(net.neoforged.neoforge.event.level.LevelEvent.Save ev) {
        if (!(ev.getLevel() instanceof ServerLevel l) || l != l.getServer().overworld()) return;
        for (DrekkiEntity d : List.copyOf(LIVE.values())) if (!d.isRemoved()) touch(d);
    }

    public static void onStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent ev) { LIVE.clear(); }
}
