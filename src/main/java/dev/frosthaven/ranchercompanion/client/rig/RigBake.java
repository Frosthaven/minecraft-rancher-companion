package dev.frosthaven.ranchercompanion.client.rig;

import com.mojang.blaze3d.platform.NativeImage;
import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.look.Astral;
import dev.frosthaven.ranchercompanion.look.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a rig into a baked vanilla model. Every box gets its own patch of a generated texture painted in its color
 * with the page's fur grain, so the model stays one draw with solid colored cubes, exactly as the page renders it.
 */
public final class RigBake {
    private RigBake() {}

    private static final int EYE_W = 0xf3f3ee, EYE_WD = 0xcdcdc6;

    private record Cell(Node node, Node.Box box, int u, int v, int w, int h) {}

    /** with the showcase flag, every generated sheet is also written to run/debug_textures for inspection */
    private static final boolean DUMP = Boolean.getBoolean("rancher_companion.showcase");

    private static void dump(NativeImage img, String key) {
        try { java.nio.file.Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("debug_textures"); java.nio.file.Files.createDirectories(dir); img.writeToFile(dir.resolve(key + ".png")); }
        catch (java.io.IOException e) { throw new RuntimeException(e); }
    }

    public static Baked bake(Rig rig, String key) {
        /* one cell per box: the cube's net, 2(d+w) by (d+h), with a one pixel margin all round */
        List<Cell> cells = new ArrayList<>();
        for (Node n : rig.all) for (Node.Box b : n.boxes) cells.add(new Cell(n, b, 0, 0, (int) Math.ceil(2 * (b.w() + b.d())) + 2, (int) Math.ceil(b.h() + b.d()) + 2));
        cells.sort(Comparator.comparingInt((Cell c) -> -c.h));
        int texW = rig.look.stage() == 3 ? 512 : 256, x = 0, y = 0, rowH = 0;
        List<Cell> placed = new ArrayList<>();
        for (Cell c : cells) {
            if (x + c.w > texW) { x = 0; y += rowH; rowH = 0; }
            placed.add(new Cell(c.node, c.box, x, y, c.w, c.h)); x += c.w; rowH = Math.max(rowH, c.h);
        }
        int texH = 1; while (texH < y + rowH) texH <<= 1;

        NativeImage fur = new NativeImage(NativeImage.Format.RGBA, texW, texH, true), glow = new NativeImage(NativeImage.Format.RGBA, texW, texH, true),
            sheen = rig.look.sheen() > 0 ? new NativeImage(NativeImage.Format.RGBA, texW, texH, true) : null;
        fur.fillRect(0, 0, texW, texH, 0); glow.fillRect(0, 0, texW, texH, 0); if (sheen != null) sheen.fillRect(0, 0, texW, texH, 0);
        Map<Integer, int[]> tiles = new HashMap<>();
        Map<Node.Box, Cell> cellOf = new java.util.IdentityHashMap<>();  /* by identity: two boxes in different groups can be equal records */
        int i = 0;
        for (Cell c : placed) {
            cellOf.put(c.box, c);
            Node.Box b = c.box;
            if (b.kind() == 1) paintEye(fur, Astral.glows(rig.look.astral()) ? glow : fur, c);  /* a Dormant eye stays off the glow layer, which a lamp hat may draw */
            else if (b.kind() == 3) { int px0 = abgr(b.color()); fur.fillRect(c.u, c.v, c.w, c.h, px0); glow.fillRect(c.u, c.v, c.w, c.h, px0); }  /* a lamp: full bright through the eye layer */
            else { int kind = b.kind(); int[] tile = kind >= BardingRig.KIND ? tiles.computeIfAbsent(b.color() * 31 + kind, k -> BardingTex.tile(b.color(), kind - BardingRig.KIND)) : tiles.computeIfAbsent(b.color(), RigBake::tile);
                int ox = (i * 7) & 15, oy = (i * 13) & 15;
                boolean fade = rig.look.see() && kind < BardingRig.KIND;  /* a see-through line: its texels at 65% alpha, barding solid, as on the page */
                for (int py = 0; py < c.h; py++) for (int px = 0; px < c.w; px++) { int t = tile[((py + oy) & 15) * 16 + ((px + ox) & 15)];
                    fur.setPixelRGBA(c.u + px, c.v + py, fade ? (t & 0x00ffffff) | (166 << 24) : t); }
                if (sheen != null && kind == 0 && rig.sheenColors.contains(b.color())) sheen.fillRect(c.u, c.v, c.w, c.h, sheenTexel(rig.look)); }  /* the page's emissive, added on top of the lit fur */
            i++;
        }

        MeshDefinition mesh = new MeshDefinition();
        PartDefinition vroot = mesh.getRoot().addOrReplaceChild("root", CubeListBuilder.create(), PartPose.offset(0, 24, 0));
        Map<Node, PartDefinition> defs = new HashMap<>();
        Map<Node, String> names = new HashMap<>();
        int[] count = {0};
        Map<Node.Box, Integer> order = new java.util.IdentityHashMap<>();
        for (Node n : rig.all) for (Node.Box b : n.boxes) order.put(b, order.size());
        build(rig.root, vroot, defs, names, cellOf, order, count, true);
        ModelPart baked = LayerDefinition.create(mesh, texW, texH).bakeRoot();
        ModelPart root = baked.getChild("root");
        link(rig.root, root, names);

        ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "dynamic/" + key);
        ResourceLocation eyes = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "dynamic/" + key + "_eyes");
        if (DUMP) dump(fur, key);
        Minecraft.getInstance().getTextureManager().register(tex, new DynamicTexture(fur));
        Minecraft.getInstance().getTextureManager().register(eyes, new DynamicTexture(glow));
        ResourceLocation sheenTex = null;
        if (sheen != null) { sheenTex = ResourceLocation.fromNamespaceAndPath(RancherCompanion.ID, "dynamic/" + key + "_sheen"); Minecraft.getInstance().getTextureManager().register(sheenTex, new DynamicTexture(sheen)); }
        return new Baked(rig, baked, tex, eyes, sheenTex);
    }

    private static void build(Node n, PartDefinition pd, Map<Node, PartDefinition> defs, Map<Node, String> names, Map<Node.Box, Cell> cellOf, Map<Node.Box, Integer> order, int[] count, boolean isRoot) {
        PartDefinition mine = pd;
        if (!isRoot) {
            String name = "n" + count[0]++;
            names.put(n, name);
            CubeListBuilder cb = CubeListBuilder.create();
            for (Node.Box b : n.boxes) if (!b.rotated()) addBox(cb, b, cellOf.get(b), 0, 0, 0, order.get(b));
            Vector3f e = rot(n);
            mine = pd.addOrReplaceChild(name, cb, PartPose.offsetAndRotation(n.x, -n.y, -n.z, e.x, e.y, e.z));
        } else {
            mine = pd;
        }
        int bi = 0;
        for (Node.Box b : n.boxes) if (b.rotated()) {
            Quaternionf q = new Quaternionf().rotationX(b.rx()).rotateY(b.ry()).rotateZ(b.rz());
            Vector3f e = new Vector3f(); Baked.eulerZYX(q.x, -q.y, -q.z, q.w, e);
            CubeListBuilder cb = CubeListBuilder.create(); addBox(cb, b, cellOf.get(b), -b.x(), -b.y(), -b.z(), order.get(b));
            mine.addOrReplaceChild("b" + bi++, cb, PartPose.offsetAndRotation(b.x(), -b.y(), -b.z(), e.x, e.y, e.z));
        }
        defs.put(n, mine);
        for (Node c : n.children) build(c, mine, defs, names, cellOf, order, count, false);
    }

    /** a box centered at (x, y, z) in the page's space becomes a vanilla cube from its min corner, y and z flipped. Each cube is
     *  inflated a little by its place in the whole rig's build order (the page's cubeCount), so neighbours in different groups
     *  get different amounts and faces that share a plane do not fight, as on the page. Eyes and lids keep their own
     *  part-pixel offsets; fur, lamps, hats and barding all inflate */
    private static void addBox(CubeListBuilder cb, Node.Box b, Cell c, float dx, float dy, float dz, int index) {
        float x = b.x() + dx, y = b.y() + dy, z = b.z() + dz;
        cb.texOffs(c.u + 1, c.v + 1).addBox(x - b.w() / 2, -y - b.h() / 2, -z - b.d() / 2, b.w(), b.h(), b.d(), new CubeDeformation(b.kind() == 1 || b.kind() == 2 ? 0 : 0.015f * (index % 5))  /* small: the rig's own offsets (eyes, lids, nostrils) are a fraction of a pixel */);
    }

    private static void link(Node n, ModelPart part, Map<Node, String> names) {
        n.part = part;
        for (Node c : n.children) link(c, part.getChild(names.get(c)), names);
    }

    private static Vector3f rot(Node n) {
        Quaternionf q = Baked.quat(n, new Quaternionf());
        Vector3f e = new Vector3f(); Baked.eulerZYX(q.x, -q.y, -q.z, q.w, e); return e;
    }

    /** the page's fur grain: a 16 by 16 tile of the color jittered per texel, a tenth of them darker, seeded by the color */
    private static int[] tile(int color) {
        int[] t = new int[256]; int[] seed = {color};
        int r = Palette.r(color), g = Palette.g(color), b = Palette.b(color);
        for (int i = 0; i < 256; i++) { double j = (rnd(seed) - 0.5) * 20; if (rnd(seed) < 0.1) j -= 14; t[i] = abgr(Palette.rgb(r + j, g + j, b + j)); }
        return t;
    }

    /** the sheen as an added texel: the page's emissive, the glint color in linear light times the line's strength, back to sRGB.
     *  Drawn additively it lights a dark fur by exactly the page's amount, the same by day and by night */
    private static int sheenTexel(Rig.Look o) {
        int c = o.sheenHex(); double k = o.sheen();
        java.util.function.IntUnaryOperator ch = v -> (int) Math.round(Math.pow(Math.pow(v / 255.0, 2.2) * k, 1 / 2.2) * 255);
        return abgr(Palette.rgb(ch.applyAsInt(Palette.r(c)), ch.applyAsInt(Palette.g(c)), ch.applyAsInt(Palette.b(c))));
    }

    static double rnd(int[] s) {
        s[0] += 0x6D2B79F5; int t = (s[0] ^ (s[0] >>> 15)) * (1 | s[0]);
        t = (t + ((t ^ (t >>> 7)) * (61 | t))) ^ t;
        return ((t ^ (t >>> 14)) & 0xffffffffL) / 4294967296.0;
    }

    static int abgr(int rgb) { return 0xff000000 | (Palette.b(rgb) << 16) | (Palette.g(rgb) << 8) | Palette.r(rgb); }

    /** the page's eye sheet on a one pixel thick box: the side faces carry the eye (four columns, pupil at the front edge, then
     *  iris, then the white sclera at the back, fewer columns on a smaller eye; three rows, the bottom one darker), the thin rim faces
     *  take the pupil's dark. The net is 2(w + d) by (h + d): west at u [0, d), north [d, d + w), east [d + w, 2d + w), south after,
     *  top and bottom in the first d rows. On the west face u runs south to north, on the east face north to south */
    private static void paintEye(NativeImage fur, NativeImage glow, Cell c) {
        Node.Box b = c.box; int iris = b.color(), id = Palette.shade(iris, -.32), pu = Palette.shade(iris, -.62);
        int[][] cols = {{iris, pu, id}, {iris, iris, id}, {iris, iris, id}, {EYE_W, EYE_W, EYE_WD}};
        int w = Math.round(b.w()), h = Math.round(b.h()), d = Math.round(b.d()), u0 = c.u + 1, v0 = c.v + 1;
        int[] pick = switch (d) { case 1 -> new int[]{0}; case 2 -> new int[]{0, 3}; case 3 -> new int[]{0, 1, 3}; default -> new int[]{0, 1, 2, 3}; };
        int rim = abgr(pu);
        for (int py = 0; py < d + h; py++) for (int px = 0; px < 2 * (w + d); px++) { fur.setPixelRGBA(u0 + px, v0 + py, rim); }
        for (int r = 0; r < h; r++) { int ri = Math.min(2, 3 * r / h);
            for (int k = 0; k < d; k++) { int ci = pick[Math.min(pick.length - 1, (int) ((long) k * pick.length / d))];
                int west = abgr(cols[pick[pick.length - 1 - Math.min(pick.length - 1, (int) ((long) k * pick.length / d))]][ri]), east = abgr(cols[ci][ri]);
                fur.setPixelRGBA(u0 + k, v0 + d + r, west); glow.setPixelRGBA(u0 + k, v0 + d + r, west);
                fur.setPixelRGBA(u0 + d + w + k, v0 + d + r, east); glow.setPixelRGBA(u0 + d + w + k, v0 + d + r, east); } }
    }
}
