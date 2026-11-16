package dev.frosthaven.ranchercompanion.client.rig;

import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** A group in the design page's rig: a position and an XYZ Euler rotation in three.js space (y up, z forward, pixels). */
public final class Node {
    public final Node parent;
    public final List<Node> children = new ArrayList<>();
    public final List<Box> boxes = new ArrayList<>();
    public float x, y, z, rx, ry, rz;
    /** the built pose, restored before every frame */
    public float px, py, pz, bx, by, bz;
    public boolean orderYXZ, visible = true;
    /** set by an animation that needs a free rotation, cleared by reset() */
    public Quaternionf quat;
    public ModelPart part;

    Node(Node parent, double x, double y, double z, double rx) {
        this.parent = parent;
        this.x = (float) x; this.y = (float) y; this.z = (float) z; this.rx = (float) rx;
        if (parent != null) parent.children.add(this);
    }

    public void rot(double rx, double ry, double rz) { this.rx = (float) rx; this.ry = (float) ry; this.rz = (float) rz; }

    void bake() { px = x; py = y; pz = z; bx = rx; by = ry; bz = rz; }

    public void reset() { x = px; y = py; z = pz; rx = bx; ry = by; rz = bz; visible = true; quat = null; orderYXZ = false; }

    /** A box centered at (x, y, z) in this group, optionally with its own rotation. kind: 0 fur, 1 eye, 2 lid. */
    public record Box(float w, float h, float d, float x, float y, float z, int color, float rx, float ry, float rz, int kind, int side) {
        boolean rotated() { return rx != 0 || ry != 0 || rz != 0; }
    }
}
