package dev.frosthaven.ranchercompanion;

import dev.frosthaven.ranchercompanion.client.RancherCompanionClient;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.item.DrekkiItems;
import dev.frosthaven.ranchercompanion.ranch.Ranch;
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
    public static final DeferredHolder<EntityType<?>, EntityType<dev.frosthaven.ranchercompanion.combat.ElementalBall>> ELEMENTAL_BALL = ENTITIES.register("elemental_ball",
        () -> EntityType.Builder.<dev.frosthaven.ranchercompanion.combat.ElementalBall>of(dev.frosthaven.ranchercompanion.combat.ElementalBall::new, MobCategory.MISC)
            .sized(0.3f, 0.3f).clientTrackingRange(4).updateInterval(10).build("elemental_ball"));

    public RancherCompanion(IEventBus modBus, ModContainer container) {
        ENTITIES.register(modBus);
        dev.frosthaven.ranchercompanion.block.DrekkiBlocks.register(modBus);
        DrekkiSounds.SOUNDS.register(modBus);
        DrekkiItems.register(modBus);
        dev.frosthaven.ranchercompanion.item.DrekkiPotions.register(modBus);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent.class, dev.frosthaven.ranchercompanion.item.DrekkiPotions::brewing);
        dev.frosthaven.ranchercompanion.item.DrekkiRecipes.register(modBus);
        if (dev.frosthaven.ranchercompanion.item.Bags.sophisticated()) {
            dev.frosthaven.ranchercompanion.compat.sophisticated.SophMenus.register(modBus);
            dev.frosthaven.ranchercompanion.compat.sophisticated.SophBlocks.register(modBus);
        }
        dev.frosthaven.ranchercompanion.block.DrekkiMenus.register(modBus);
        modBus.addListener(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent.class, dev.frosthaven.ranchercompanion.net.DrekkiNet::register);
        /* owning drekki: the roster keeps each one in the world once (ranch/Ranch) */
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, Ranch::onJoin);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent.class, Ranch::onLeave);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, Ranch::onLogout);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Ranch::onLogin);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerRespawnEvent.class, Ranch::onRespawn);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, Ranch::onDimension);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent.class, Ranch::onPlayerJoinLevel);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Ranch::onServerTick);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.ChunkEvent.Load.class, Ranch::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangeGameModeEvent.class, Ranch::onGameMode);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.level.LevelEvent.Save.class, Ranch::onSave);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract.class, dev.frosthaven.ranchercompanion.entity.DrekkiInteract::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH, net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteractSpecific.class, dev.frosthaven.ranchercompanion.entity.DrekkiInteract::onEntityInteractSpecific);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, Ranch::onStopped);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, e -> dev.frosthaven.ranchercompanion.life.Feeding.clear());
        dev.frosthaven.ranchercompanion.life.LifeEvents.register(NeoForge.EVENT_BUS);
        dev.frosthaven.ranchercompanion.entity.FriendlyFire.register(NeoForge.EVENT_BUS);
        dev.frosthaven.ranchercompanion.trait.Learning.register(NeoForge.EVENT_BUS);
        dev.frosthaven.ranchercompanion.trait.Effects.register(NeoForge.EVENT_BUS);
        dev.frosthaven.ranchercompanion.trait.FightTraits.register(NeoForge.EVENT_BUS);
        dev.frosthaven.ranchercompanion.item.BardingEnchants.register(NeoForge.EVENT_BUS);
        dev.frosthaven.ranchercompanion.trait.WorldTraits.register(NeoForge.EVENT_BUS);
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.server.ServerStoppedEvent.class, e -> { dev.frosthaven.ranchercompanion.trait.FightTraits.clear(); dev.frosthaven.ranchercompanion.trait.WorldTraits.clear(); });
        modBus.addListener(EntityAttributeCreationEvent.class, e -> e.put(DREKKI.get(), DrekkiEntity.createAttributes().build()));
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class, DrekkiCommands::register);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, Showcase::onLogin);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, Showcase::onServerTick);
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, ShowcaseBuild::onStarted);
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, ShowcaseBuild::onTick);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, DrekkiConfig.SPEC);
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Loading.class, e -> FoodDiets.invalidate());
        modBus.addListener(net.neoforged.fml.event.config.ModConfigEvent.Reloading.class, e -> FoodDiets.invalidate());
        NeoForge.EVENT_BUS.addListener(net.neoforged.neoforge.event.TagsUpdatedEvent.class, e -> { FoodDiets.invalidate(); dev.frosthaven.ranchercompanion.life.Places.invalidate(); });
        /* Create's seats for the whistle's Go here, only with Create loaded */
        if (net.neoforged.fml.ModList.get().isLoaded("create")) dev.frosthaven.ranchercompanion.compat.create.CreateCompat.init();
        if (FMLEnvironment.dist.isClient()) RancherCompanionClient.init(modBus);
    }
}
