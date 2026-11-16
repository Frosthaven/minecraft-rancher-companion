package dev.frosthaven.ranchercompanion.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The page's particle: a small unlit cube in one flat color (the page's MeshBasicMaterial, so full bright by day and
 * by night), see-through at the effect's opacity, not writing depth. It keeps the page's velocity, gravity and ground
 * stop, and its size follows the page's envelope over its life. The cube is squared to the drekki it came from, so
 * the glitch bars and the tall drips lie along the drekki's side and up, as on the page.
 */
final class DrekkiCube extends Particle {
    /** the page's size envelopes: the loop emitter's shrink, and the sparkle modes */
    enum Mode { SHRINK, FADE, SMOKE, BURST, EMBER, TWINKLE, GLITCH }

    /** flat color, alpha blended, no texture, no lighting, no depth write; culls the back faces so a cube is not doubled */
    static final ParticleRenderType CUBES = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator t, TextureManager tm) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            return t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        }

        @Override
        public String toString() { return "rancher_companion:cubes"; }
    };

    private final double[] v, side, fwd;
    private final double g, ground, size, w, h, seed;
    private final Mode mode;
    private final int r, gr, b, a;

    /**
     * @param v velocity in blocks per second; g gravity in blocks per second squared; ground the y it settles on
     * @param side the drekki's right, fwd its forward (unit, flat); size the cube side in blocks; w, h the page's bar and tall stretch
     */
    DrekkiCube(ClientLevel level, double x, double y, double z, double[] v, double g, double ground, double life, double size, int rgb, double opacity,
               Mode mode, double w, double h, double[] side, double[] fwd) {
        super(level, x, y, z);
        this.v = v; this.g = g; this.ground = ground; this.size = size; this.mode = mode; this.w = w; this.h = h; this.side = side; this.fwd = fwd;
        this.seed = Math.random() * 6.28;
        this.r = rgb >> 16 & 255; this.gr = rgb >> 8 & 255; this.b = rgb & 255; this.a = Math.round((float) opacity * 255);
        setLifetime(Math.max(1, (int) Math.round(life * 20)));
        hasPhysics = false;
        setSize(0.1f, 0.1f);
    }

    /** advance it by part of a tick, for a cube the page emitted earlier in the same tick */
    DrekkiCube preRun(double seconds) { move(seconds); xo = x; yo = y; zo = z; return this; }

    /** the page's step(): gravity, the move, the ground stop with its slide (0.85 a frame at 60 fps) */
    private void move(double dt) {
        v[1] -= g * dt;
        double nx = x + v[0] * dt, ny = y + v[1] * dt, nz = z + v[2] * dt;
        if (g > 0 && ny < ground + 0.04) { ny = ground + 0.04; v[1] = 0; double f = Math.pow(0.85, dt * 60); v[0] *= f; v[2] *= f; }
        setPos(nx, ny, nz);
    }

    @Override
    public void tick() {
        xo = x; yo = y; zo = z;
        if (age++ >= lifetime) { remove(); return; }
        move(0.05);
    }

    /** the page's scale over life f (0..1) at page time t seconds */
    private double envelope(double f, double t) {
        return switch (mode) {
            case SHRINK -> 1 - f * 0.7;
            case SMOKE -> Math.sin(Math.PI * f) * (0.5 + f * 1.2);
            case BURST -> 1 - f;
            case EMBER -> Math.sin(Math.PI * f) * (0.6 + 0.4 * Math.sin(t * 20 + seed));
            case TWINKLE -> Math.max(0, Math.sin(t * 6 + seed));
            case GLITCH -> Math.sin(t * 17 + seed) > 0.2 ? 1 : 0;
            case FADE -> Math.sin(Math.PI * f);
        };
    }

    @Override
    public void render(VertexConsumer vc, Camera cam, float pt) {
        double t = (level.getGameTime() + pt) / 20.0, k = envelope(Math.min(1, (age + pt) / lifetime), t);
        if (k <= 0.001) return;
        Vec3 c = cam.getPosition();
        double cx = Mth.lerp(pt, xo, x) - c.x, cy = Mth.lerp(pt, yo, y) - c.y, cz = Mth.lerp(pt, zo, z) - c.z, s = size * k / 2;
        double[][] ax = {side, {0, 1, 0}, fwd};
        double[] half = {s * w, s * h, s};
        for (int i = 0; i < 3; i++) for (int sg = -1; sg <= 1; sg += 2) {
            double[] n = mul(ax[i], sg), ua = ax[(i + 1) % 3], ub = cross(n, ua);  /* ua x ub = n: counter-clockwise seen from outside */
            double ha = half[(i + 1) % 3], hb = half[(i + 2) % 3], ox = cx + n[0] * half[i], oy = cy + n[1] * half[i], oz = cz + n[2] * half[i];
            corner(vc, ox, oy, oz, ua, -ha, ub, -hb); corner(vc, ox, oy, oz, ua, ha, ub, -hb);
            corner(vc, ox, oy, oz, ua, ha, ub, hb); corner(vc, ox, oy, oz, ua, -ha, ub, hb);
        }
    }

    private void corner(VertexConsumer vc, double x, double y, double z, double[] ua, double a1, double[] ub, double b1) {
        vc.addVertex((float) (x + ua[0] * a1 + ub[0] * b1), (float) (y + ua[1] * a1 + ub[1] * b1), (float) (z + ua[2] * a1 + ub[2] * b1)).setColor(r, gr, b, a);
    }

    private static double[] mul(double[] a, double k) { return new double[]{a[0] * k, a[1] * k, a[2] * k}; }
    private static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    @Override
    public ParticleRenderType getRenderType() { return CUBES; }
}
