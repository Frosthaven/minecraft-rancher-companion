package dev.frosthaven.ranchercompanion.client.rig;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** A rig baked into vanilla model parts, with its generated fur texture and the glow texture for the eyes. */
public final class Baked {
    public final Rig rig;
    public final ModelPart root;
    public final ResourceLocation texture, eyes;
    /** the sheen texture, drawn additively; null for a line without a sheen */
    public final ResourceLocation sheen;
    /** the mouth in the built pose, entity space blocks */
    public final Vector3f mouthRest;

    Baked(Rig rig, ModelPart root, ResourceLocation texture, ResourceLocation eyes, ResourceLocation sheen) {
        this.rig = rig; this.root = root; this.texture = texture; this.eyes = eyes; this.sheen = sheen;
        this.mouthRest = point(rig.head, rig.mouthLocal, new Vector3f());
    }

    /** The node's rotation as a quaternion in the page's space: XYZ Euler, YXZ for the sleeping neck, or an override. */
    public static Quaternionf quat(Node n, Quaternionf dest) {
        if (n.quat != null) return dest.set(n.quat);
        if (n.orderYXZ) return dest.rotationY(n.ry).rotateX(n.rx).rotateZ(n.rz);
        return dest.rotationX(n.rx).rotateY(n.ry).rotateZ(n.rz);
    }

    /** Copies every node's pose onto its model part. The page's space is y up and z forward; vanilla's is y down
     *  and z back, a half turn about x, so positions flip y and z and a rotation's axis flips the same way. */
    public void apply() {
        Quaternionf q = new Quaternionf();
        Vector3f e = new Vector3f();
        for (Node n : rig.all) {
            ModelPart p = n.part;
            p.visible = n.visible;
            p.x = n.x; p.y = n == rig.root ? 24 - n.y : -n.y; p.z = -n.z;
            quat(n, q);
            eulerZYX(q.x, -q.y, -q.z, q.w, e);
            p.xRot = e.x; p.yRot = e.y; p.zRot = e.z;
        }
    }

    /** Euler angles for vanilla's rotationZYX(z, y, x), which applies x first. */
    static void eulerZYX(float x, float y, float z, float w, Vector3f out) {
        out.x = (float) Math.atan2(2 * (w * x + y * z), 1 - 2 * (x * x + y * y));
        double sp = 2 * (w * y - z * x);
        out.y = (float) Math.asin(Math.max(-1, Math.min(1, sp)));
        out.z = (float) Math.atan2(2 * (w * z + x * y), 1 - 2 * (y * y + z * z));
    }

    /** A point in a node's space (pixels) in the page's entity space, blocks: x right, y up, z forward. */
    public Vector3f point(Node n, Vector3f local, Vector3f dest) {
        dest.set(local);
        Quaternionf q = new Quaternionf();
        for (Node c = n; c != null; c = c.parent) { quat(c, q).transform(dest); dest.add(c.x, c.y, c.z); }
        return dest.mul(1 / 16f);
    }
}
