package dev.frosthaven.ranchercompanion.ranch;

import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiData;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.life.Vitals;
import dev.frosthaven.ranchercompanion.look.Incubation;
import dev.frosthaven.ranchercompanion.ranch.Roster.Entry;
import dev.frosthaven.ranchercompanion.ranch.Roster.Where;
import dev.frosthaven.ranchercompanion.trait.Trait;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A drekki's death (spec, Death and the tombstone; Shane, 2026-11-26: no Phoenix Heart, no ghost at death). A drekki
 * that dies is gone from the roster; it always leaves a tombstone that keeps its record, and only that tombstone brings
 * it back (its ghost, a Drekki Lotus Blossom and a day: {@link Tombs}). Each death is a red line to the owner.
 */
public final class Death {
    private Death() {}

    /** true when it does not die. Nothing saves a drekki at death any more (no Phoenix Heart, no ghost roll) */
    public static boolean survives(DrekkiEntity d, DamageSource cause) { return false; }

    /** tests only: makes a living drekki a ghost, gear to a tombstone (in play a ghost rises only at its tombstone, Rebirth.raise) */
    public static void turnGhost(DrekkiEntity d) {
        gearToTomb(d);
        d.life.ghost = true;
        d.setHealth(d.getMaxHealth());
        d.clearFire();
        d.removeAllEffects();
        d.setTarget(null);
        if (d.getY() < d.level().getMinBuildHeight()) bringBack(d);  /* fell out of the world: back where it belongs */
        d.syncLife();
        d.ghostChanged();
        Ranch.touch(d);
        /* the soul leaving is seen and heard by its owner only, as the ghost is */
        ServerPlayer owner = d.getServer().getPlayerList().getPlayer(d.owner());
        if (owner != null && owner.level() == d.level()) {
            ((ServerLevel) d.level()).sendParticles(owner, ParticleTypes.SOUL, false, d.getX(), d.getY() + 0.5, d.getZ(), 20, 0.3, 0.4, 0.3, 0.02);
            owner.playNotifySound(SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 0.8f);
        }
    }

    /** gone for good: its record is deleted; a tombstone where it fell holds its hat, barding and bag (the bag keeps what it
     *  holds; spec, Tombstone), and the owner's map gets a death marker */
    public static void died(DrekkiEntity d) {
        Roster r = Ranch.roster(d.getServer());
        Entry e = r.get(d.drekkiId());
        if (e == null) return;
        gearToTomb(d);
        r.remove(e.id);
        Ranch.forgetLive(e.id);
        tell(d.getServer(), e.owner, Component.translatable("message.rancher_companion.died",
            Vitals.who(d)).withStyle(ChatFormatting.RED));
    }

    /** out of the void: to its owner when it travels with them here, else home */
    public static void bringBack(DrekkiEntity d) {
        d.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        d.resetFallDistance();
        Player p = d.ownerPlayer();
        if (d.isOut() && p != null && p.level() == d.level()) d.teleportNear(p.blockPosition()); else d.goHome();
    }

    /** its hat, barding and bag into a tombstone where it is (dropped when no spot is free), and the owner's marker */
    private static void gearToTomb(DrekkiEntity d) {
        if (d.owner() == null) return;
        java.util.List<ItemStack> gear = new java.util.ArrayList<>(java.util.List.of(
            d.hat() > 0 ? DrekkiItems.hatStack(d.hat()) : ItemStack.EMPTY,
            dev.frosthaven.ranchercompanion.item.BardingEnchants.vanishes(d.bardingItem()) ? ItemStack.EMPTY : d.bardingItem(), d.accessory(), d.saddle()));  /* Curse of Vanishing */
        /* its record as it fell, look and all (kept for later; spec, Tombstone): the worn items are in the tombstone, not in it */
        net.minecraft.nbt.CompoundTag record = Ranch.save(d);
        record.remove("BardingItem");
        record.remove("Accessory");
        record.remove("Saddle");
        if (d.hat() > 0) d.setGear(0, d.barding(), d.dye());
        d.setBardingItem(ItemStack.EMPTY);
        d.setAccessory(ItemStack.EMPTY);
        d.setSaddle(ItemStack.EMPTY);
        String name = Ranch.label(Ranch.save(d)).getString();
        ServerLevel level = (ServerLevel) d.level();
        BlockPos at = d.blockPosition();
        /* always a tombstone: it keeps the record its ghost and its return come from (Shane, 2026-11-26) */
        BlockPos tomb = Tombs.place(level, at, d.getDirection().getOpposite(), name, gear, record);
        if (tomb == null) gear.stream().filter(s -> !s.isEmpty()).forEach(d::spawnAtLocation);  /* no free spot: as before */
        Tombs.mark(d.getServer(), d.owner(), name, level, tomb != null ? tomb : at);
    }

    private static void tell(net.minecraft.server.MinecraftServer s, java.util.UUID owner, Component msg) {
        ServerPlayer p = owner == null ? null : s.getPlayerList().getPlayer(owner);
        if (p != null) p.sendSystemMessage(msg);
    }
}
