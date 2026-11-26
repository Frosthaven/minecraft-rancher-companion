package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.LotusCropBlock;
import dev.frosthaven.ranchercompanion.block.TombstoneBlock;
import dev.frosthaven.ranchercompanion.block.TombstoneBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Drekki Lotus Blossom in a tombstone (Shane, 2026-11-26): the grown lotus's own 3D model, small, laid over the
 * plinth's front lip (front left; the candle stands front right) at an angle, its bloom leaning out over the edge.
 */
public final class TombLotusRenderer implements BlockEntityRenderer<TombstoneBlockEntity> {
    /** where it lies in the north-facing model, in pixels: on the plinth (2 px up), in front of the stone */
    private static final float X = 5.5f, Y = 2f, Z = 5.25f, SCALE = 0.3f, LEAN = 35f, TURN = 25f;

    public TombLotusRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(TombstoneBlockEntity be, float partial, PoseStack ps, MultiBufferSource buf, int light, int overlay) {
        if (be.blossomSlot() < 0) return;
        BlockState st = be.getBlockState();
        if (!st.hasProperty(TombstoneBlock.FACING)) return;
        BlockState lotus = DrekkiBlocks.LOTUS.get().defaultBlockState().setValue(LotusCropBlock.AGE, 3);
        double ang = Math.toRadians(-st.getValue(TombstoneBlock.FACING).toYRot() + 180);  /* the blockstate's y turn, as the candle's flame */
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation((float) -ang));
        ps.translate(X / 16 - 0.5, Y / 16, Z / 16 - 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(TURN));
        ps.mulPose(Axis.XP.rotationDegrees(-LEAN));  /* the bloom leans out over the front edge */
        ps.scale(SCALE, SCALE, SCALE);
        ps.translate(-0.5, 0, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(lotus, ps, buf, light, overlay);
        ps.popPose();
    }
}
