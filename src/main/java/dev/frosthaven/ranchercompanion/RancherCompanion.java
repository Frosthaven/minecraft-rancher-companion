package dev.frosthaven.ranchercompanion;

import dev.frosthaven.ranchercompanion.client.RancherCompanionClient;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.showcase.Showcase;
import dev.frosthaven.ranchercompanion.showcase.ShowcaseBuild;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(RancherCompanion.ID)
public final class RancherCompanion {
    public static final String ID = "rancher_companion";
    public static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();

    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    public static final DeferredHolder<EntityType<?>, EntityType<DrekkiEntity>> DREKKI = ENTITIES.register("drekki",
        () -> EntityType.Builder.of(DrekkiEntity::new, MobCategory.CREATURE).sized(1.5f, 2.3f).clientTrackingRange(16).build("drekki"));

    public RancherCompanion(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        dev.frosthaven.ranchercompanion.block.DrekkiBlocks.register(modBus);
        DrekkiSounds.SOUNDS.register(modBus);
        DrekkiItems.register(modBus);
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(DREKKI.get(), DrekkiEntity.createAttributes().build()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, DrekkiCommands::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Showcase::onLogin);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Showcase::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, ShowcaseBuild::onStarted);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ShowcaseBuild::onTick);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, DrekkiConfig.SPEC);
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, e -> FoodDiets.invalidate());
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class, e -> FoodDiets.invalidate());
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TagsUpdatedEvent.class, e -> FoodDiets.invalidate());
        if (FMLEnvironment.dist.isClient()) RancherCompanionClient.init(modBus);
    }
}
