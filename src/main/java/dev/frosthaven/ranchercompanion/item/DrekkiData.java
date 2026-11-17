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
    /** what the carrier shows on hover: the drekki's name or bloodline */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> CARRIED_NAME = COMPONENTS.registerComponentType("carried_name",
        b -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
}
