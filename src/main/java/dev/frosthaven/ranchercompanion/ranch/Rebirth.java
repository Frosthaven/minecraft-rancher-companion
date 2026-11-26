package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.life.Life;
import dev.frosthaven.ranchercompanion.life.LifeTag;
import dev.frosthaven.ranchercompanion.ranch.Roster.Entry;
import dev.frosthaven.ranchercompanion.ranch.Roster.Where;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

/**
 * The way back from death (spec, Tombstone: the way back; Shane, 2026-11-26). A tombstone keeps its drekki's record. A
 * Drekki Lotus Blossom in one of its slots raises the ghost (it stays within 3 blocks, and a hatchery within 24 blocks
 * can pair it); its owner lights the candle with flint and steel, and one Minecraft day runs on the clock. Then the
 * candle is melted and its owner can reclaim the drekki: alive again, at its owner's side, travelling with them, with Soulflame (its
 * mood two bands down for a Minecraft day, on the clock). Reclaim needs the owner to have no drekki with them.
 */
public final class Rebirth {
    private Rebirth() {}

    public static final long DAY = Roster.DAY;

    public static boolean owner(Player p, TombstoneBlockEntity be) { return be.hasRecord() && p.getUUID().equals(be.recordOwner()); }

    /** the meter, 0 to 1 (0 with no blossom) */
    public static float progress(TombstoneBlockEntity be, long now) {
        if (be.reviveStart() == 0) return 0;
        return Math.max(0, Math.min(1, (now - be.reviveStart()) / (float) DAY));
    }

    /** its ghost is pairing at a hatchery now: the blossom stays in and Reclaim waits until the egg is laid or the pairing is
     *  cancelled (else the pairing would end with no egg; Shane, 2026-11-26) */
    public static boolean breeding(net.minecraft.server.MinecraftServer s, TombstoneBlockEntity be) {
        if (!be.hasRecord()) return false;
        Entry e = Ranch.roster(s).get(be.drekki().getUUID("DrekkiId"));
        return e != null && e.where == Where.TOMB && e.breedUntil != 0 && Roster.now(s) < e.breedUntil;
    }

    /** its ghost's pairing at a hatchery within reach is cancelled (the candle put out; the tonic back to the player) */
    public static void cancelGhostBreeding(ServerPlayer p, TombstoneBlockEntity be) {
        if (!breeding(p.server, be)) return;
        UUID id = be.drekki().getUUID("DrekkiId");
        BlockPos at = be.getBlockPos();
        int r = Pairing.GHOST_REACH;
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-r, -8, -r), at.offset(r, 8, r)))
            if (be.getLevel().getBlockEntity(q) instanceof dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity h && h.breeding()
                && (id.equals(h.breedA()) || id.equals(h.breedB()))) { Pairing.cancel(p, q.immutable(), h); return; }
    }

    /** why flint and steel lights nothing now, as a message key, or null */
    public static String lightWhy(ServerPlayer p, TombstoneBlockEntity be) {
        if (!be.hasRecord()) return "tomb.no_drekki";
        if (!owner(p, be)) return "tomb.not_owner";
        if (!be.onSoulSoil()) return "tomb.soul_soil";
        if (be.blossomSlot() < 0) return "tomb.need_lotus";
        if (be.reviveStart() != 0) return "tomb.lit";
        return null;
    }

    /** why Reclaim does nothing now, as a message key, or null */
    public static String reclaimWhy(ServerPlayer p, TombstoneBlockEntity be) {
        if (!be.hasRecord()) return "tomb.no_drekki";
        if (!owner(p, be)) return "tomb.not_owner";
        if (!be.onSoulSoil()) return "tomb.soul_soil";
        if (be.blossomSlot() < 0) return "tomb.need_lotus";
        if (be.reviveStart() == 0) return "tomb.not_lit";
        if (progress(be, Roster.now(p.server)) < 1) return "tomb.not_ready";
        if (breeding(p.server, be)) return "tomb.ghost_breeding";
        if (Ranch.roster(p.server).out(p.getUUID()) != null) return "tomb.one_out";
        return null;
    }

    /** the record with its life's ghost flag set */
    private static CompoundTag withGhost(CompoundTag rec, boolean ghost) {
        CompoundTag out = rec.copy();
        Life l = new Life();
        if (out.contains("Life")) LifeTag.load(l, out.getCompound("Life"));
        l.ghost = ghost;
        out.put("Life", LifeTag.save(l));
        return out;
    }

    /** the blossom went in: its ghost rises by the tombstone and stays within 3 blocks (Shane, 2026-11-26) */
    public static void raise(ServerLevel level, BlockPos pos, TombstoneBlockEntity be) {
        if (!be.hasRecord() || be.summoned()) return;
        CompoundTag rec = withGhost(be.drekki(), true);
        UUID id = rec.getUUID("DrekkiId");
        Roster r = Ranch.roster(level.getServer());
        if (r.get(id) != null) r.remove(id);  /* a stale record of the same drekki */
        Entry e = r.add(id, be.recordOwner(), rec, Where.TOMB, Roster.at(level, pos));
        be.setSummoned(true);
        spawnGhost(level, pos, e);
        level.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1f, 0.8f);
    }

    /** the blossom came out (or the tombstone was mined): the ghost is laid to rest; the record stays in the tombstone */
    public static void lay(ServerLevel level, BlockPos pos, TombstoneBlockEntity be) {
        if (!be.hasRecord()) return;
        UUID id = be.drekki().getUUID("DrekkiId");
        DrekkiEntity g = Ranch.liveOf(id);
        if (g != null) { level.sendParticles(ParticleTypes.SOUL, g.getX(), g.getY() + 0.5, g.getZ(), 12, 0.3, 0.4, 0.3, 0.02); g.discard(); }
        Roster r = Ranch.roster(level.getServer());
        Entry e = r.get(id);
        if (e != null && e.where == Where.TOMB) r.remove(id);
        be.setSummoned(false);
    }

    private static void spawnGhost(ServerLevel level, BlockPos pos, Entry e) {
        BlockPos at = new Ground(level).place(pos, Math.max(1, e.save.getInt("Stage")));
        DrekkiEntity d = Ranch.spawn(level, e, at != null ? at : pos.above(), pos);
        if (d == null) return;
        d.life.ghost = true;
        d.syncLife();  /* not ghostChanged: that re-sends an entity already in the level, and this one is not yet */
        Ranch.add(level, d);
    }

    /** every few seconds while summoned: a ghost that is missing (its chunk reloaded) comes back; one that left the roster
     *  (released) or came back to life leaves the tombstone unsummoned */
    public static void keep(ServerLevel level, BlockPos pos, TombstoneBlockEntity be) {
        if (!be.hasRecord()) { be.setSummoned(false); return; }
        Entry e = Ranch.roster(level.getServer()).get(be.drekki().getUUID("DrekkiId"));
        if (e == null || e.where != Where.TOMB || !Roster.at(level, pos).equals(e.home)) { be.setSummoned(false); return; }
        if (Ranch.liveOf(e.id) == null) spawnGhost(level, pos, e);
    }

    /** the day is out: the drekki is alive again, at its owner's side, travelling with them; the tombstone is plain again */
    public static void reclaim(ServerPlayer p, BlockPos pos, TombstoneBlockEntity be) {
        String why = reclaimWhy(p, be);
        if (why != null) {
            dev.frosthaven.ranchercompanion.RancherCompanion.LOG.info("[rebirth] reclaim at {} refused for {}: {}", pos, p.getName().getString(), why);
            Ranch.say(p, why);
            return;
        }
        ServerLevel level = p.serverLevel();
        Roster r = Ranch.roster(p.server);
        CompoundTag rec = be.drekki();
        UUID id = rec.getUUID("DrekkiId");
        Entry e = r.get(id);
        DrekkiEntity ghost = Ranch.liveOf(id);
        CompoundTag save = withGhost(ghost != null ? Ranch.save(ghost) : e != null ? e.save : rec, false);
        if (ghost != null) ghost.discard();
        if (e == null) e = r.add(id, p.getUUID(), save, Where.OUT, null);
        else { r.snapshot(e, save); r.move(e, Where.OUT, null); }
        BlockPos at = new Ground(level).place(p.blockPosition(), Math.max(1, save.getInt("Stage")));
        DrekkiEntity d = Ranch.spawn(level, e, at != null ? at : p.blockPosition(), null);
        if (d != null) {  /* its save already says alive (withGhost): no ghostChanged here, which re-syncs an entity not yet added */
            d.life.ghost = false;
            d.life.soulflameUntil = Roster.now(p.server) + Life.SOULFLAME;  /* Soulflame: its mood two bands down for a day */
            d.setHealth(d.getMaxHealth());
            d.syncLife();
            Ranch.add(level, d);
            r.snapshot(e, Ranch.save(d));
        }
        be.clearRecord();
        net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {  /* a strike on the tombstone, for show: no damage, no fire (Shane, 2026-11-26) */
            bolt.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.6, 0.5, 0.04);
        level.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
        p.sendSystemMessage(Component.translatable("message.rancher_companion.tomb.reclaimed", Ranch.label(e.save)).withStyle(ChatFormatting.GOLD));
    }
}
