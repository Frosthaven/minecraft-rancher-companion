package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.frosthaven.ranchercompanion.client.rig.Baked;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;

/** One baked rig as a vanilla entity model; the loop runs off the entity's time. */
public final class DrekkiModel extends EntityModel<DrekkiEntity> {
    public final Baked baked;

    public DrekkiModel(Baked baked) {
        super(RenderType::entityCutoutNoCull);
        this.baked = baked;
    }

    @Override
    public void setupAnim(DrekkiEntity e, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        DrekkiAnimations.pose(baked, e, ageInTicks / 20.0 + e.phase);
    }

    @Override
    public void renderToBuffer(PoseStack ps, VertexConsumer vc, int light, int overlay, int color) {
        baked.root.render(ps, vc, light, overlay, color);
    }
}
