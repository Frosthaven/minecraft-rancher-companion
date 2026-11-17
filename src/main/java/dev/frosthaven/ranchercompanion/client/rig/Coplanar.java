package dev.frosthaven.ranchercompanion.client.rig;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Faces that would z-fight: two cubes of the rig with faces in the same plane, facing the same way and overlapping (in
 * the rest pose; joints of equal width and trim set flush do this). {@link #grow} gives every such cube a growth step so
 * no two clashing cubes share one: a step moves each face 0.04 px out, enough for the depth buffer at any distance a
 * drekki is drawn, too little to see. Pure geometry (JOML only), so it is unit tested.
 */
public final class Coplanar {
    private Coplanar() {}

    public static final float STEP = 0.04f;

    private record Face(Node.Box box, Vector3f n, float d, float[] rect, int drop) {}

    private static void collect(Node n, Matrix4f parent, List<Face> out) {
        Matrix4f m = new Matrix4f(parent).translate(n.x, n.y, n.z).rotateX(n.rx).rotateY(n.ry).rotateZ(n.rz);
        for (Node.Box b : n.boxes) {
            Matrix4f bm = new Matrix4f(m).translate(b.x(), b.y(), b.z()).rotateX(b.rx()).rotateY(b.ry()).rotateZ(b.rz());
            float[] h = {b.w() / 2, b.h() / 2, b.d() / 2};
            for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
                Vector3f nrm = bm.transformDirection(new Vector3f(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0)).normalize();
                int drop = Math.abs(nrm.x) > 0.999f ? 0 : Math.abs(nrm.y) > 0.999f ? 1 : Math.abs(nrm.z) > 0.999f ? 2 : -1;
                if (drop < 0) continue;  /* a face at an angle cannot share a plane with an axis-aligned one */
                float u0 = 1e9f, v0 = 1e9f, u1 = -1e9f, v1 = -1e9f, d = 0;
                for (int s = -1; s <= 1; s += 2) for (int t = -1; t <= 1; t += 2) {
                    float[] c = new float[3];
                    c[axis] = sign * h[axis];
                    c[(axis + 1) % 3] = s * h[(axis + 1) % 3];
                    c[(axis + 2) % 3] = t * h[(axis + 2) % 3];
                    Vector3f p = bm.transformPosition(new Vector3f(c[0], c[1], c[2]));
                    d = nrm.dot(p);
                    float u = drop == 0 ? p.y : p.x, v = drop == 2 ? p.y : p.z;
                    u0 = Math.min(u0, u); u1 = Math.max(u1, u); v0 = Math.min(v0, v); v1 = Math.max(v1, v);
                }
                out.add(new Face(b, nrm, d, new float[]{u0, v0, u1, v1}, drop));
            }
        }
        for (Node c : n.children) collect(c, m, out);
    }

    /** every pair of distinct cubes with clashing faces, each pair once */
    public static List<Node.Box[]> clashes(Rig rig) {
        List<Face> f = new ArrayList<>();
        collect(rig.root, new Matrix4f(), f);
        List<Node.Box[]> out = new ArrayList<>();
        for (int i = 0; i < f.size(); i++) for (int j = i + 1; j < f.size(); j++) {
            Face a = f.get(i), b = f.get(j);
            if (a.box == b.box || a.drop != b.drop || a.n.dot(b.n) < 0.999f || Math.abs(a.d - b.d) > 0.01f) continue;
            float w = Math.min(a.rect[2], b.rect[2]) - Math.max(a.rect[0], b.rect[0]), h = Math.min(a.rect[3], b.rect[3]) - Math.max(a.rect[1], b.rect[1]);
            if (w > 0.05f && h > 0.05f) out.add(new Node.Box[]{a.box, b.box});
        }
        return out;
    }

    /** a growth (in steps) for each cube: clashing cubes get different steps; eyes and lids keep 0 (their own offsets) */
    public static Map<Node.Box, Integer> grow(Rig rig) {
        Map<Node.Box, List<Node.Box>> near = new IdentityHashMap<>();
        for (Node.Box[] p : clashes(rig)) {
            near.computeIfAbsent(p[0], k -> new ArrayList<>()).add(p[1]);
            near.computeIfAbsent(p[1], k -> new ArrayList<>()).add(p[0]);
        }
        Map<Node.Box, Integer> step = new IdentityHashMap<>();
        for (Node n : rig.all) for (Node.Box b : n.boxes) if (b.kind() == 1 || b.kind() == 2) step.put(b, 0);
        for (Node n : rig.all) for (Node.Box b : n.boxes) {
            if (step.containsKey(b)) continue;
            java.util.Set<Integer> taken = new java.util.HashSet<>();
            for (Node.Box o : near.getOrDefault(b, List.of())) if (step.containsKey(o)) taken.add(step.get(o));
            int s = 0;
            while (taken.contains(s)) s++;
            step.put(b, s);
        }
        return step;
    }
}
