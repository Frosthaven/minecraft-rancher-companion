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
 * A drekki's death (spec, Growth release: Death; Ghost drekki; Phoenix Heart). Phoenix Heart always wins: it turns into
 * its egg, which brings it back at a hatchery after 4 days. Otherwise a Fledgling or older has a 33% chance to return
 * as a ghost (which cannot die again; only /kill removes it); a drekki whose roll fails is gone for good. Each death is a red line to the owner.
 */
public final class Death {
    private Death() {}

    public static final double GHOST_CHANCE = 0.33;
    /** a Phoenix egg incubates 4 days: it starts a day behind an egg's 3 */
    public static final long PHOENIX_START = -Incubation.DAY;

    /** true when it does not die: it became its egg or a ghost (the entity stays alive or leaves the world itself) */
    public static boolean survives(DrekkiEntity d, DamageSource cause) {
        Entry e = Ranch.roster(d.getServer()).get(d.drekkiId());
        if (e == null) return false;
        if (!d.life.ghost && d.life.traits.contains(Trait.PHOENIX_HEART.id)) { phoenix(d, e); return true; }
        if (!d.life.ghost && d.stage() >= 2 && d.getRandom().nextDouble() < GHOST_CHANCE) { turnGhost(d); return true; }
        return false;
    }

    /** a ghost wears nothing (spec, Ghost drekki): its hat, barding and bag go into a tombstone where it died, with the
     *  owner's death marker, as a real death leaves them */
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
        tell(d.getServer(), d.owner(), Component.translatable("message.rancher_companion.died_ghost", Vitals.who(d)).withStyle(ChatFormatting.RED));
    }

    private static void phoenix(DrekkiEntity d, Entry e) {
        d.setHealth(d.getMaxHealth());
        d.clearFire();
        d.removeAllEffects();
        Roster r = Ranch.roster(d.getServer());
        r.snapshot(e, Ranch.save(d));
        r.move(e, Where.NONE, null);
        e.phoenix = Entry.EGG_OWED;
        r.setDirty();
        if (d.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FLAME, d.getX(), d.getY() + 0.5, d.getZ(), 30, 0.3, 0.5, 0.3, 0.05);
            sl.playSound(null, d.blockPosition(), SoundEvents.FIRECHARGE_USE, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1.2f);
        }
        tell(d.getServer(), e.owner, Component.translatable("message.rancher_companion.died_phoenix", Vitals.who(d)).withStyle(ChatFormatting.GOLD));
        d.discard();
        ServerPlayer owner = d.getServer().getPlayerList().getPlayer(e.owner);
        if (owner != null) giveEggs(owner);
    }

    /** the egg of each Phoenix drekki the owner is owed: to the inventory when there is room, else on the next login */
    public static void giveEggs(ServerPlayer p) {
        Roster r = Ranch.roster(p.server);
        for (Entry e : r.of(p.getUUID())) {
            if (e.phoenix != Entry.EGG_OWED || p.getInventory().getFreeSlot() < 0) continue;
            p.getInventory().add(egg(e));
            e.phoenix = Entry.EGG_GIVEN;
            r.setDirty();
        }
    }

    /** an egg that carries its drekki's id: a hatchery brings that drekki back */
    public static ItemStack egg(Entry e) {
        ItemStack s = new ItemStack(DrekkiItems.INTRIGUING_EGG.get());
        s.set(DrekkiData.CARRIED.get(), e.id);
        s.set(DrekkiData.CARRIED_NAME.get(), Ranch.label(e.save).getString());
        s.set(DrekkiData.INCUBATION.get(), PHOENIX_START);
        s.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.translatable("item.rancher_companion.phoenix_egg", Ranch.label(e.save))
            .withStyle(s1 -> s1.withItalic(false).withColor(ChatFormatting.GOLD)));
        return s;
    }

    /** a hatched Phoenix egg: its drekki lives at this hatchery again, at full health; false when the egg is not owed */
    public static boolean reborn(ServerPlayer p, ServerLevel level, BlockPos hatchery, BlockPos at, ItemStack eggStack) {
        Roster r = Ranch.roster(p.server);
        Entry e = r.get(eggStack.get(DrekkiData.CARRIED.get()));
        if (e == null || e.phoenix != Entry.EGG_GIVEN || !e.owner.equals(p.getUUID())) return false;
        e.phoenix = 0;
        r.move(e, Where.HOME, Roster.at(level, hatchery));
        DrekkiEntity d = Ranch.spawn(level, e, at, hatchery);
        if (d == null) return false;
        d.setHealth(d.getMaxHealth());
        Ranch.add(level, d);
        tell(p.server, e.owner, Component.translatable("message.rancher_companion.reborn", Ranch.label(e.save)).withStyle(ChatFormatting.GOLD));
        return true;
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
        /* nothing worn: no tombstone (an empty one would only vanish), the marker still shows where */
        BlockPos tomb = gear.stream().allMatch(ItemStack::isEmpty) ? null : Tombs.place(level, at, d.getDirection().getOpposite(), name, gear, record);
        if (tomb == null) gear.stream().filter(s -> !s.isEmpty()).forEach(d::spawnAtLocation);  /* no free spot: as before */
        Tombs.mark(d.getServer(), d.owner(), name, level, tomb != null ? tomb : at);
    }

    private static void tell(net.minecraft.server.MinecraftServer s, java.util.UUID owner, Component msg) {
        ServerPlayer p = owner == null ? null : s.getPlayerList().getPlayer(owner);
        if (p != null) p.sendSystemMessage(msg);
    }
}
