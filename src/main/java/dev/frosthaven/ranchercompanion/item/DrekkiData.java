package dev.frosthaven.ranchercompanion.item;

import com.mojang.serialization.Codec;
import dev.frosthaven.ranchercompanion.RancherCompanion;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The mod's item data: an egg's incubation ticks, and which drekki a carrier holds (its roster id). */
public final class DrekkiData {
    private DrekkiData() {}

    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, RancherCompanion.ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> INCUBATION = COMPONENTS.registerComponentType("incubation",
        b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    /** the drekki in a carrier: its roster id; the drekki itself is the roster's record */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CARRIED = COMPONENTS.registerComponentType("carried",
        b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    /** this carry's one-time code, also on the roster record: only the newest carrier of a drekki matches it, so a carrier
     *  left behind (lost, given away, or from an earlier carry) is empty once the drekki has been checked in without it */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CARRY_TOKEN = COMPONENTS.registerComponentType("carry_token",
        b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    /** a full carrier's owner and their name: anyone else's use does nothing, and their tooltip says whose it is */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> CARRIED_OWNER = COMPONENTS.registerComponentType("carried_owner",
        b -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CARRIED_OWNER_NAME = COMPONENTS.registerComponentType("carried_owner_name",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** the trim's dye (barding rims, bag flaps and strap), beside vanilla's dyed color for the main part (spec, Dyeing) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> TRIM_COLOR = COMPONENTS.registerComponentType("trim_color",
        b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT));
    /** a tombstone's drekki and the date it died, MM-dd-yyyy (spec, Tombstone) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TOMB_NAME = COMPONENTS.registerComponentType("tomb_name",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TOMB_DATE = COMPONENTS.registerComponentType("tomb_date",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** a decorative tombstone's message (Shane, 2026-11-26): typed in its screen, shown by Jade and on the item */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> TOMB_MESSAGE = COMPONENTS.registerComponentType("tomb_message",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** a tombstone's drekki record (its look, life and traits; not its gear, which is in the tombstone), kept for later
     *  and never shown (spec, Tombstone) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>> TOMB_DREKKI = COMPONENTS.registerComponentType("tomb_drekki",
        b -> b.persistent(net.minecraft.nbt.CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));
    /** what the carrier shows on hover: the drekki's name or bloodline */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CARRIED_NAME = COMPONENTS.registerComponentType("carried_name",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    /** a bred egg's child (spec, Breeding): its line, horn and inherited traits, and the parents' names for the tooltip */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.nbt.CompoundTag>> BRED = COMPONENTS.registerComponentType("bred",
        b -> b.persistent(net.minecraft.nbt.CompoundTag.CODEC).networkSynchronized(ByteBufCodecs.COMPOUND_TAG));
}
