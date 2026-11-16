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
        shadowRadius = Dims.of(e.stage()).shadow;
        super.render(e, yaw, partial, ps, buf, light);
    }

    /** a see-through line (Bakeneko) draws translucent; its texture carries the alpha, so barding stays solid */
    @Override
    protected RenderType getRenderType(DrekkiEntity e, boolean visible, boolean translucent, boolean glowing) {
        return e.lineage().seeThrough() ? RenderType.entityTranslucent(getTextureLocation(e)) : super.getRenderType(e, visible, translucent, glowing);
    }

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
