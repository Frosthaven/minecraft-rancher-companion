package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Dims;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Picks the baked model for the drekki's look, and draws the locked eyes full bright over it. */
public final class DrekkiRenderer extends MobRenderer<DrekkiEntity, DrekkiModel> {
    public DrekkiRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, DrekkiModels.fallback(), 0.9f);
        addLayer(new Glow(this));
        addLayer(new Sheen(this));
    }

    @Override
    public void render(DrekkiEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        model = DrekkiModels.get(e);
        DrekkiDebug.drawn(e);
        shadowRadius = Dims.of(e.stage()).shadow * Dims.of(e.stage()).scale;
        boolean bob = e.ghost();
        if (bob) { ps.pushPose(); ps.translate(0, 0.12 + Math.sin((e.tickCount + partial) / 20.0 * 1.6) * 0.08, 0); }  /* a ghost floats with a slow bob */
        super.render(e, yaw, partial, ps, buf, light);
        if (bob) ps.popPose();
        if (e.live() && !e.isInvisible()) moodIcon(e, partial, ps, buf, light);
    }

    /** the stage's model scale (Dims.scale), the vanilla hook baby mobs and slimes use: the model and its layers only */
    @Override
    protected void scale(DrekkiEntity e, PoseStack ps, float partial) {
        float s = Dims.of(e.stage()).scale;
        if (s != 1) ps.scale(s, s, s);
    }

    private static final ResourceLocation UNEASY = ResourceLocation.fromNamespaceAndPath(dev.frosthaven.ranchercompanion.RancherCompanion.ID, "textures/entity/mood/uneasy.png"),
        MISERABLE = ResourceLocation.fromNamespaceAndPath(dev.frosthaven.ranchercompanion.RancherCompanion.ID, "textures/entity/mood/miserable.png");

    /** Uneasy or Miserable: a camera-facing icon over its head, bobbing a little, as MineColonies floats its status
     *  icons (spec, Mood: Cue; the page's moodSprite). Nothing while Content or better */
    private void moodIcon(DrekkiEntity e, float partial, PoseStack ps, MultiBufferSource buf, int light) {
        var band = e.band();
        if (band != dev.frosthaven.ranchercompanion.life.Life.Band.UNEASY && band != dev.frosthaven.ranchercompanion.life.Life.Band.MISERABLE) return;
        ps.pushPose();
        ps.translate(0, e.getBbHeight() + 0.5 + Math.sin((e.tickCount + partial) / 20.0 * 2) * 0.04, 0);
        ps.mulPose(entityRenderDispatcher.cameraOrientation());
        ps.scale(0.7f, 0.7f, 0.7f);
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(band == dev.frosthaven.ranchercompanion.life.Life.Band.UNEASY ? UNEASY : MISERABLE));
        var pose = ps.last();
        float[][] q = {{-0.5f, -0.5f, 0, 1}, {0.5f, -0.5f, 1, 1}, {0.5f, 0.5f, 1, 0}, {-0.5f, 0.5f, 0, 0}};
        for (float[] v : q) vc.addVertex(pose, v[0], v[1], 0).setColor(255, 255, 255, 255).setUv(v[2], v[3])
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
        ps.popPose();
    }

    /** a see-through line (Bakeneko) draws translucent; its texture carries the alpha, so barding stays solid */
    @Override
    protected RenderType getRenderType(DrekkiEntity e, boolean visible, boolean translucent, boolean glowing) {
        return e.lineage().seeThrough() || e.ghost() ? RenderType.entityTranslucent(getTextureLocation(e)) : super.getRenderType(e, visible, translucent, glowing);
    }

    /** a ghost coming back to life shakes, as a curing zombie villager does */
    @Override
    protected boolean isShaking(DrekkiEntity e) { return super.isShaking(e) || e.reviving(); }

    @Override
    public ResourceLocation getTextureLocation(DrekkiEntity e) {
        return DrekkiModels.get(e).baked.texture;
    }

    /** the eyes again, unlit, like spider eyes */
    private static final class Glow extends RenderLayer<DrekkiEntity, DrekkiModel> {
        private static final int MINER_HELMET = 11;
        Glow(DrekkiRenderer parent) { super(parent); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, DrekkiEntity e, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            if (!Astral.glows(e.astral()) && e.hat() != MINER_HELMET) return;  /* the layer also carries the miner helmet's lamp */
            DrekkiModel m = getParentModel();
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucentEmissive(m.baked.eyes));
            m.renderToBuffer(ps, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        }
    }

    /** a rare line's sheen, added on top of the lit fur as the page's emissive: the same lift by day and by night, so no
     *  line looks brighter than the page shows it */
    private static final class Sheen extends RenderLayer<DrekkiEntity, DrekkiModel> {
        Sheen(DrekkiRenderer parent) { super(parent); }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int light, DrekkiEntity e, float limbSwing, float limbSwingAmount, float partial, float age, float yaw, float pitch) {
            DrekkiModel m = getParentModel();
            if (m.baked.sheen == null || e.isInvisible()) return;
            m.renderToBuffer(ps, buf.getBuffer(RenderType.eyes(m.baked.sheen)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, -1);
        }
    }
}
