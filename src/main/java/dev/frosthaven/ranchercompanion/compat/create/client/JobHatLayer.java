package dev.frosthaven.ranchercompanion.compat.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.frosthaven.ranchercompanion.client.DrekkiAnimations;
import dev.frosthaven.ranchercompanion.client.DrekkiModel;
import dev.frosthaven.ranchercompanion.client.rig.Node;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayDeque;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Create's job hats on a drekki (spec, Go here): the train hat of a conductor and the stock keeper's hat of a seat next
 * to a Stock Ticker. Create puts its hat layer only on renderers with a vanilla head part, which the drekki's rig has
 * not, so the drekki draws Create's hat itself, on its own crown (the spot its own hats sit on), at every stage, and
 * shows it only on a bare head: a drekki wearing a hat of its own keeps it. Create decides when a hat shows ({@code EntityHats.getHatFor}); that method returns a
 * Flywheel type, so it and the model are reached by method handles (no Flywheel at compile time). The flip and the tilt
 * are Create's own ({@code CreateHatArmorLayer}); Create's 2.25 px sink into a vanilla head is left out: the crown is
 * already a pixel under the top of the drekki's shorter head, and with the sink the 3.3 px cap sat at its eyes.
 */
public final class JobHatLayer extends RenderLayer<DrekkiEntity, DrekkiModel> {
    private static MethodHandle hatFor, modelOf;
    private static boolean failed;

    public JobHatLayer(RenderLayerParent<DrekkiEntity, DrekkiModel> parent) { super(parent); }

    /** the hat model Create would put on it now, or null */
    static BakedModel hat(LivingEntity e) {
        if (failed) return null;
        try {
            if (hatFor == null) {
                var lookup = MethodHandles.publicLookup();
                Class<?> hats = Class.forName("com.simibubi.create.content.equipment.hats.EntityHats");
                Class<?> partial = Class.forName("dev.engine_room.flywheel.lib.model.baked.PartialModel");
                hatFor = lookup.findStatic(hats, "getHatFor", MethodType.methodType(partial, LivingEntity.class));
                modelOf = lookup.findVirtual(partial, "get", MethodType.methodType(BakedModel.class));
            }
            Object p = hatFor.invoke(e);
            return p == null ? null : (BakedModel) modelOf.invoke(p);
        } catch (Throwable t) {
            failed = true;
            dev.frosthaven.ranchercompanion.RancherCompanion.LOG.error("Create's job hats cannot be read; a drekki in a Create role wears none", t);
            return null;
        }
    }

    /** registers the hide hook: its own hat goes while Create's shows */
    static void install() { DrekkiAnimations.JOB_HAT = e -> e.hat() == 0 && hat(e) != null; }

    @Override
    public void render(PoseStack ps, MultiBufferSource buf, int light, DrekkiEntity e, float limb, float limbAmount, float partial, float age, float yaw, float pitch) {
        if (e.isInvisible() || e.hat() > 0) return;  /* a hat of its own stays on: Create's only on a bare head */
        BakedModel hat = hat(e);
        if (hat == null) return;
        var baked = getParentModel().baked;
        Node crown = baked.rig.crown;
        ArrayDeque<Node> chain = new ArrayDeque<>();
        for (Node n = crown; n != null; n = n.parent) chain.addFirst(n);
        ps.pushPose();
        baked.root.translateAndRotate(ps);
        for (Node n : chain) n.part.translateAndRotate(ps);
        float k = (float) baked.rig.crownK;
        ps.scale(k, k, k);
        ps.scale(1, -1, -1);
        ps.mulPose(Axis.XP.rotationDegrees(-8.5f));
        Minecraft.getInstance().getBlockRenderer().getModelRenderer()
            .renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, hat, 1, 1, 1, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
