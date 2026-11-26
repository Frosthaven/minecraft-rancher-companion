package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.shaders.FogShape;
import dev.frosthaven.ranchercompanion.item.TonicFluids;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import org.joml.Vector3f;

/**
 * The tonic and Horizons fluids' look: Create's own potion fluid textures (grey, made to be tinted, more opaque than
 * water, seamless; Shane, 2026-11-26: our hand-drawn ones tiled) tinted with the color of the liquid in the item's icon,
 * and a fog of the same hue. Without Create, vanilla water's textures.
 */
public final class TonicFluidsClient {
    private TonicFluidsClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(TonicFluidsClient::extensions);
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            for (var t : TonicFluids.ALL) {
                ItemBlockRenderTypes.setRenderLayer(t.source().get(), RenderType.translucent());
                ItemBlockRenderTypes.setRenderLayer(t.flowing().get(), RenderType.translucent());
            }
        }));
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        boolean create = net.neoforged.fml.ModList.get().isLoaded("create");
        ResourceLocation still = create ? ResourceLocation.fromNamespaceAndPath("create", "fluid/potion_still") : ResourceLocation.withDefaultNamespace("block/water_still");
        ResourceLocation flow = create ? ResourceLocation.fromNamespaceAndPath("create", "fluid/potion_flow") : ResourceLocation.withDefaultNamespace("block/water_flow");
        for (var t : TonicFluids.ALL) {
            int tint = t.color();
            Vector3f fog = new Vector3f(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f, (tint & 0xFF) / 255f);
            event.registerFluidType(new IClientFluidTypeExtensions() {
                @Override public ResourceLocation getStillTexture() { return still; }
                @Override public ResourceLocation getFlowingTexture() { return flow; }
                @Override public ResourceLocation getOverlayTexture() { return ResourceLocation.withDefaultNamespace("block/water_overlay"); }
                @Override public int getTintColor() { return tint; }
                @Override public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) { return new Vector3f(fog); }
                @Override public void modifyFogRender(Camera camera, net.minecraft.client.renderer.FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
                    com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(0F);
                    com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(6F);
                }
            }, t.type().get());
        }
    }
}
