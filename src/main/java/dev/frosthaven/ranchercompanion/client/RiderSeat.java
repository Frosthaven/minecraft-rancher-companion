package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.frosthaven.ranchercompanion.client.rig.Baked;
import dev.frosthaven.ranchercompanion.client.rig.Node;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The rider of a Mature as the design page draws it (buildCreature, o.rider): the hips on the saddle, moving and
 * turning with the animated body, the hands at the reins and the legs astride. The game seats a rider at one fixed
 * point of the hitbox, so without this the body moves under a rider that does not.
 */
public final class RiderSeat {
    private RiderSeat() {}

    /** a drawn player's hips above its feet, blocks */
    private static final float HIP = 0.7f;
    /** the page's rider, left (page s = 1) then right: arms -40 degrees forward and 8 in, legs -72 forward and 22 out */
    private static final Vector3f[] ARM = {euler(-40, -8), euler(-40, 8)}, LEG = {euler(-72, 22), euler(-72, -22)};

    /** the page's Euler XYZ (x then z here) as the game's model angles */
    private static Vector3f euler(double rx, double rz) {
        Quaternionf q = new Quaternionf().rotationX((float) Math.toRadians(rx)).rotateZ((float) Math.toRadians(rz));
        Vector3f e = new Vector3f();
        Baked.eulerZYX(q.x, -q.y, -q.z, q.w, e);
        return e;
    }

    static DrekkiEntity mount(LivingEntity e) {
        return e instanceof Player && e.getVehicle() instanceof DrekkiEntity d && d.rideable() ? d : null;
    }

    /** before a rider is drawn: onto the saddle of the body as it is posed this frame, and turned with it */
    public static void onRender(RenderPlayerEvent.Pre ev) {
        Player p = ev.getEntity();
        DrekkiEntity d = mount(p);
        if (d == null) return;
        float pt = ev.getPartialTick();
        Baked b = DrekkiModels.get(d).baked;
        DrekkiAnimations.pose(b, d, d.time(pt));
        Node body = b.rig.body;
        float yaw = -Mth.rotLerp(pt, d.yBodyRotO, d.yBodyRot) * Mth.DEG_TO_RAD;
        /* the page's seat: on the saddle, just behind its middle. Page space (z forward) turns into the world by its yaw */
        Vector3f seat = b.point(body, new Vector3f(0, b.rig.D.body[1] / 2 + 1.4f, b.rig.D.saddleZ + 0.2f), new Vector3f()).rotateY(yaw);
        Quaternionf turn = new Quaternionf().rotationY(yaw);
        Quaternionf lean = turn.mul(chain(body, false).mul(chain(body, true).invert()), new Quaternionf()).mul(turn.invert(new Quaternionf()));
        PoseStack ps = ev.getPoseStack();  /* the renderer's caller pops it: no push here, so a cancelled draw leaves nothing behind */
        ps.translate(seat.x - (Mth.lerp(pt, p.xOld, p.getX()) - Mth.lerp(pt, d.xOld, d.getX())),
            seat.y - (Mth.lerp(pt, p.yOld, p.getY()) - Mth.lerp(pt, d.yOld, d.getY())),
            seat.z - (Mth.lerp(pt, p.zOld, p.getZ()) - Mth.lerp(pt, d.zOld, d.getZ())));
        ps.mulPose(lean);
        ps.translate(0, -HIP, 0);
    }

    /** the body's rotation in the page's entity space, now or in the built pose */
    private static Quaternionf chain(Node n, boolean rest) {
        Quaternionf q = new Quaternionf(), one = new Quaternionf();
        for (Node c = n; c != null; c = c.parent) q.premul(rest ? one.rotationX(c.bx).rotateY(c.by).rotateZ(c.bz) : Baked.quat(c, one));
        return q;
    }

    /** after the game and other mods posed a player: a drekki's rider holds the reins (mixin.PlayerModelMixin). A
     *  swinging or item-using arm stays the game's */
    public static void pose(PlayerModel<?> m, LivingEntity e) {
        if (mount(e) == null) return;
        set(m.leftLeg, LEG[0]); set(m.rightLeg, LEG[1]);
        if (m.attackTime <= 0 && !e.isUsingItem()) { set(m.leftArm, ARM[0]); set(m.rightArm, ARM[1]); }
        m.leftPants.copyFrom(m.leftLeg); m.rightPants.copyFrom(m.rightLeg); m.leftSleeve.copyFrom(m.leftArm); m.rightSleeve.copyFrom(m.rightArm);
    }

    private static void set(ModelPart p, Vector3f e) { p.xRot = e.x; p.yRot = e.y; p.zRot = e.z; }
}
