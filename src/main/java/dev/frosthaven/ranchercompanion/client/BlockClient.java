package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.ranchercompanion.block.DrekkiBlocks;
import dev.frosthaven.ranchercompanion.block.TroughBlock;
import dev.frosthaven.ranchercompanion.block.TroughBlockEntity;
import dev.frosthaven.ranchercompanion.look.LeatherDye;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** The blocks' client side: the leather tints (block and item) and the trough's food laid on its straw. */
public final class BlockClient {
    private BlockClient() {}

    private static int opaque(int rgb) { return FastColor.ARGB32.opaque(rgb); }

    /** tintindex 0 is leather; the hatchery and trough read their dye from the block state, Tufted Leather is fixed per block */
    public static void blockColors(RegisterColorHandlersEvent.Block e) {
        e.register((state, level, pos, tint) -> tint == 0 ? opaque(LeatherDye.tint(state.getValue(TroughBlock.COLOR))) : -1,
            DrekkiBlocks.HATCHERY.get(), DrekkiBlocks.TROUGH.get());
        DrekkiBlocks.TUFTED_COLOR.forEach((block, i) -> e.register((state, level, pos, tint) -> tint == 0 ? opaque(LeatherDye.tint(i)) : -1, block.get()));
    }

    /** a dyed hatchery or trough item carries its color in its block state component */
    public static void itemColors(RegisterColorHandlersEvent.Item e) {
        e.register((stack, tint) -> tint == 0 ? opaque(LeatherDye.tint(colorOf(stack))) : -1, DrekkiBlocks.HATCHERY.get(), DrekkiBlocks.TROUGH.get());
        DrekkiBlocks.TUFTED_COLOR.forEach((block, i) -> e.register((stack, tint) -> tint == 0 ? opaque(LeatherDye.tint(i)) : -1, block.get()));
    }

    private static int colorOf(ItemStack stack) {
        BlockItemStateProperties p = stack.get(DataComponents.BLOCK_STATE);
        if (p == null) return 0;
        String v = p.properties().get("color");
        try { return v == null ? 0 : Integer.parseInt(v); } catch (NumberFormatException x) { return 0; }
    }

    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerBlockEntityRenderer(DrekkiBlocks.TROUGH_ENTITY.get(), TroughRenderer::new);
        e.registerBlockEntityRenderer(DrekkiBlocks.HATCHERY_ENTITY.get(), EggRenderer::new);
    }

    /**
     * The egg in the hatchery's nest, standing in the straw (the nest's top is 12 px up) and turned toward the camera. A
     * Ready egg rocks now and then and its motes rise (HatcheryBlock.animateTick).
     */
    static final class EggRenderer implements BlockEntityRenderer<dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity> {
        EggRenderer(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public void render(dev.frosthaven.ranchercompanion.block.HatcheryBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            if (!be.hasEgg()) return;
            Minecraft mc = Minecraft.getInstance();
            double t = (be.getLevel().getGameTime() + partial) / 20.0;
            float rock = 0;
            if (dev.frosthaven.ranchercompanion.look.Incubation.ready(be.incubation())) {
                double ph = t % 4.0;  /* a quick double rock, then still, every 4 s */
                rock = ph < 0.8 ? (float) (Math.sin(ph / 0.8 * Math.PI * 4) * 12 * (1 - ph / 0.8)) : 0;
            }
            pose.pushPose();
            pose.translate(0.5, 11.0 / 16, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - mc.gameRenderer.getMainCamera().getYRot()));
            pose.mulPose(Axis.ZP.rotationDegrees(rock));
            pose.translate(0, 0.17, 0);
            pose.scale(0.5f, 0.5f, 0.5f);
            mc.getItemRenderer().renderStatic(be.egg(), ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong());
            pose.popPose();
        }
    }

    /**
     * The trough's food, lying flat on the straw (the straw's top is 7 px up): one spot per stack, in the four quarters
     * of the bed, turned a little each so they read as a heap; a fuller stack shows a second item on top.
     */
    static final class TroughRenderer implements BlockEntityRenderer<TroughBlockEntity> {
        private static final float[][] SPOTS = {{-3.2f, -3.2f, 20}, {3.2f, 3f, -35}, {3.4f, -3.1f, 65}, {-3f, 3.3f, -10}};

        TroughRenderer(BlockEntityRendererProvider.Context ctx) {}

        @Override
        public void render(TroughBlockEntity be, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            var items = be.items();
            var renderer = Minecraft.getInstance().getItemRenderer();
            for (int i = 0; i < items.size(); i++) {
                ItemStack s = items.get(i);
                if (s.isEmpty()) continue;
                float[] p = SPOTS[i];
                int copies = s.getCount() >= 16 ? 2 : 1;
                for (int c = 0; c < copies; c++) {
                    pose.pushPose();
                    pose.translate(0.5 + p[0] / 16, (7.6 + c * 0.9) / 16, 0.5 + p[1] / 16);
                    pose.mulPose(Axis.YP.rotationDegrees(p[2] + c * 40));
                    pose.mulPose(Axis.XP.rotationDegrees(90));
                    pose.scale(0.42f, 0.42f, 0.42f);
                    renderer.renderStatic(s, ItemDisplayContext.FIXED, light, overlay, pose, buffers, be.getLevel(), (int) be.getBlockPos().asLong() + i);
                    pose.popPose();
                }
            }
        }
    }
}
