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

    /** a cube's face: its outward normal, its plane's distance from the origin, and its four corners */
    private record Face(Node.Box box, Vector3f n, float d, Vector3f[] corners) {}

    private static void collect(Node n, Matrix4f parent, List<Face> out) {
        Matrix4f m = new Matrix4f(parent).translate(n.x, n.y, n.z).rotateX(n.rx).rotateY(n.ry).rotateZ(n.rz);
        for (Node.Box b : n.boxes) {
            Matrix4f bm = new Matrix4f(m).translate(b.x(), b.y(), b.z()).rotateX(b.rx()).rotateY(b.ry()).rotateZ(b.rz());
            float[] h = {b.w() / 2, b.h() / 2, b.d() / 2};
            for (int axis = 0; axis < 3; axis++) for (int sign = -1; sign <= 1; sign += 2) {
                Vector3f nrm = bm.transformDirection(new Vector3f(axis == 0 ? sign : 0, axis == 1 ? sign : 0, axis == 2 ? sign : 0)).normalize();
                Vector3f[] c4 = new Vector3f[4];
                int k = 0;
                for (int s = -1; s <= 1; s += 2) for (int t = -1; t <= 1; t += 2) {
                    float[] c = new float[3];
                    c[axis] = sign * h[axis];
                    c[(axis + 1) % 3] = s * h[(axis + 1) % 3];
                    c[(axis + 2) % 3] = t * h[(axis + 2) % 3];
                    c4[k++] = bm.transformPosition(new Vector3f(c[0], c[1], c[2]));
                }
                out.add(new Face(b, nrm, nrm.dot(c4[0]), c4));
            }
        }
        for (Node c : n.children) collect(c, m, out);
    }

    /** the face's extent in the plane, on axes u and v of that plane: {u0, v0, u1, v1} */
    private static float[] rect(Face f, Vector3f u, Vector3f v) {
        float u0 = 1e9f, v0 = 1e9f, u1 = -1e9f, v1 = -1e9f;
        for (Vector3f p : f.corners) {
            float a = u.dot(p), b = v.dot(p);
            u0 = Math.min(u0, a); u1 = Math.max(u1, a); v0 = Math.min(v0, b); v1 = Math.max(v1, b);
        }
        return new float[]{u0, v0, u1, v1};
    }

    /**
     * Every pair of distinct cubes with clashing faces, each pair once. Faces at any angle: the feathered wings of the
     * Seed and Root diets are panels turned out of the axes, and two of them in one plane flickered (Shane, 2026-11-28);
     * until then only axis-aligned faces were compared. Overlap is measured on two axes of the shared plane (the
     * bounding rectangles there: a pair turned against each other in the plane counts when the rectangles overlap,
     * which at worst grows a cube that did not need it).
     */
    public static List<Node.Box[]> clashes(Rig rig) {
        List<Face> f = new ArrayList<>();
        collect(rig.root, new Matrix4f(), f);
        List<Node.Box[]> out = new ArrayList<>();
        for (int i = 0; i < f.size(); i++) for (int j = i + 1; j < f.size(); j++) {
            Face a = f.get(i), b = f.get(j);
            if (a.box == b.box || a.n.dot(b.n) < 0.999f || Math.abs(a.d - b.d) > 0.01f) continue;
            Vector3f u = new Vector3f(a.n).cross(Math.abs(a.n.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f v = new Vector3f(a.n).cross(u).normalize();
            float[] ra = rect(a, u, v), rb = rect(b, u, v);
            float w = Math.min(ra[2], rb[2]) - Math.max(ra[0], rb[0]), h = Math.min(ra[3], rb[3]) - Math.max(ra[1], rb[1]);
            if (w > 0.05f && h > 0.05f) out.add(new Node.Box[]{a.box, b.box});
        }
        return out;
    }

    /** of {@link #clashes}, the pairs whose faces are turned out of the axes (the ones a check of axis faces alone missed) */
    public static int angledClashes(Rig rig) {
        List<Face> f = new ArrayList<>();
        collect(rig.root, new Matrix4f(), f);
        int n = 0;
        for (int i = 0; i < f.size(); i++) for (int j = i + 1; j < f.size(); j++) {
            Face a = f.get(i), b = f.get(j);
            if (a.box == b.box || a.n.dot(b.n) < 0.999f || Math.abs(a.d - b.d) > 0.01f) continue;
            boolean aligned = Math.abs(a.n.x) > 0.999f || Math.abs(a.n.y) > 0.999f || Math.abs(a.n.z) > 0.999f;
            if (aligned) continue;
            Vector3f u = new Vector3f(a.n).cross(Math.abs(a.n.y) < 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(1, 0, 0)).normalize();
            Vector3f v = new Vector3f(a.n).cross(u).normalize();
            float[] ra = rect(a, u, v), rb = rect(b, u, v);
            if (Math.min(ra[2], rb[2]) - Math.max(ra[0], rb[0]) > 0.05f && Math.min(ra[3], rb[3]) - Math.max(ra[1], rb[1]) > 0.05f) n++;
        }
        return n;
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
