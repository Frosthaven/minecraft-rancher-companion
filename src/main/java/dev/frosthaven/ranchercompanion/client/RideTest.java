package dev.frosthaven.ranchercompanion.client;

import dev.frosthaven.ranchercompanion.RancherCompanion;
import dev.frosthaven.ranchercompanion.entity.DrekkiEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The ride on a dedicated server (-Drancher_companion.ridetest=true; ./gradlew runRideTestServer, then
 * runRideTestClient). Single player hides what a server does with its rider's moves: there the two sides never
 * disagree. The player (an operator, with a saddled Mature checked out) builds a stepped pyramid of full blocks and
 * rides over it eight times, each pass at another angle, with the real keys. It logs how often the server carried the
 * drekki back; the server's log has a "moved wrongly" line for each move it refused.
 */
public final class RideTest {
    private RideTest() {}
    public static final boolean ON = Boolean.getBoolean("rancher_companion.ridetest");
    private static final int CX = 4300, CZ = 300, Y = -60, PASS = 260, PASSES = 8;
    private static int t, backs, steps, downs, air;
    private static double lastY;

    private static void cmd(String c) { Minecraft.getInstance().player.connection.sendCommand(c); }

    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) { t = 0; return; }
        t++;
        if (t == 100) { cmd("gamemode creative"); cmd("tp @s " + (CX - 34) + " " + Y + " " + CZ + " -90 0"); }
        if (t == 160) for (int i = 1; i <= 12; i++) cmd("fill " + (CX - 26 + 2 * i) + " " + (Y + i - 1) + " " + (CZ - 26 + 2 * i) + " " + (CX + 26 - 2 * i) + " " + (Y + i - 1) + " " + (CZ + 26 - 2 * i) + " stone");
        if (t == 300) {
            DrekkiEntity d = SelfTest.pup();
            RancherCompanion.LOG.info("[ridetest] drekki: {}", d == null ? "none" : "rideable " + d.rideable() + ", hitbox " + d.getBbWidth() + " by " + d.getBbHeight());
            if (d != null) mc.gameMode.interact(mc.player, d, InteractionHand.MAIN_HAND);
        }
        int r = t - 340;
        if (r < 0 || !(mc.player.getVehicle() instanceof DrekkiEntity d)) {
            if (t == 340) RancherCompanion.LOG.info("[ridetest] FAIL not mounted");
            if (t == 400 && r >= 0) mc.stop();
            return;
        }
        if (r >= PASS * PASSES) {
            mc.options.keyUp.setDown(false);
            if (r == PASS * PASSES) { RancherCompanion.LOG.info("[ridetest] {} steps up, {} steps down in one tick, {} ticks in the air, carried back {} ticks: {}", steps, downs, air, backs, backs == 0 && air <= downs + 8 ? "PASS" : "FAIL"); cmd("fill " + (CX - 26) + " " + Y + " " + (CZ - 26) + " " + (CX + 26) + " " + (Y + 12) + " " + (CZ + 26) + " air"); }
            if (r == PASS * PASSES + 40) mc.stop();
            return;
        }
        /* each pass: turned toward the pyramid's middle, off by an angle that changes with the pass, then straight on */
        if (r % PASS == 0) {
            float off = new float[]{0, 12, -20, 25, -8, 18, -25, 10}[r / PASS];
            mc.player.setYRot((float) Math.toDegrees(Math.atan2(-(CX + 0.5 - d.getX()), CZ + 0.5 - d.getZ())) + off);
        }
        mc.options.keyUp.setDown(r % PASS > 5);
        /* carried back: it moved against the way it faces */
        double fx = -Math.sin(Math.toRadians(d.getYRot())), fz = Math.cos(Math.toRadians(d.getYRot()));
        if (r % PASS > 20 && (d.getX() - d.xo) * fx + (d.getZ() - d.zo) * fz < -0.02) backs++;
        if (d.getY() - lastY > 0.9 && d.getY() - lastY < 1.1) steps++;
        if (lastY - d.getY() > 0.9 && lastY - d.getY() < 1.1) downs++;
        if (!d.onGround()) air++;
        lastY = d.getY();
    }
}
