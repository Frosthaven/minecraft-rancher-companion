package dev.frosthaven.ranchercompanion.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The shared parts of the mod's screens (tools/screen_textures.py draws them; the numbers here match its sprites):
 * leather, steel and red buttons in four states, the roster row highlight and the scroll knob, and the thread text color.
 */
public final class Art {
    private Art() {}

    static final int THREAD = 0xFFD8C79A, DIM = 0xFF9A8C70, GLOW = 0xFF7FF0DC, WARN = 0xFFE07A6A, ON = 0xFF6AA83E;
    static final int BTN_W = 60, BTN_H = 16, BTN_Y = 200;

    enum Btn { NORMAL, HOVER, DISABLED, DANGER }

    /** a button of any width (the sprite's left part and its 3 px right edge) with its label centered */
    static void button(GuiGraphics g, ResourceLocation tex, Font font, int x, int y, int w, Btn state, Component label) {
        int u = state.ordinal() * BTN_W, left = Math.min(w - 3, BTN_W - 3);
        g.blit(tex, x, y, u, BTN_Y, left, BTN_H);
        for (int dx = left; dx < w - 3; dx += BTN_W - 6) g.blit(tex, x + dx, y, u + 3, BTN_Y, Math.min(BTN_W - 6, w - 3 - dx), BTN_H);  /* wider than the sprite: its middle again */
        g.blit(tex, x + w - 3, y, u + BTN_W - 3, BTN_Y, 3, BTN_H);
        int c = state == Btn.DISABLED ? 0xFF8D939E : THREAD;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, c, false);
    }

    /** the narrowest tooltip width that keeps every line to 4 rows; wider only when a line needs it */
    static final int TIP_W = 170, TIP_MAX_W = 320, TIP_ROWS = 4;

    /**
     * A tooltip at the game's own text size: with a title, the first line as is; every other line wrapped to the
     * narrowest width from {@link #TIP_W} that keeps it to {@link #TIP_ROWS} rows. No line is shortened.
     */
    public static void tip(GuiGraphics g, Font font, java.util.List<Component> lines, boolean title, int mx, int my) {
        int from = title ? 1 : 0, w = TIP_W;
        while (w < TIP_MAX_W && rows(font, lines, from, w) > TIP_ROWS) w += 10;
        java.util.List<net.minecraft.util.FormattedCharSequence> out = new java.util.ArrayList<>();
        for (int i = 0; i < lines.size(); i++)
            if (i < from) out.add(lines.get(i).getVisualOrderText()); else out.addAll(font.split(lines.get(i), w));
        g.renderTooltip(font, out, mx, my);
    }

    private static int rows(Font font, java.util.List<Component> lines, int from, int w) {
        int most = 0;
        for (int i = from; i < lines.size(); i++) most = Math.max(most, font.split(lines.get(i), w).size());
        return most;
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    /** the eye icon's rows (9 x 7): R rim, I iris, P pupil; the rig's eye in small */
    private static final String[] EYE = {"..RRRRR..", ".RIIIIIR.", "RIIIPIIIR", "RIIIPIIIR", "RIIIPIIIR", ".RIIIIIR.", "..RRRRR.."};
    static final int EYE_W = 9, EYE_H = 7;

    /** an astral as an eye icon in its iris color (Dormant: its matte brown); a two-eyed drekki's two astrals as one icon
     *  split down the pupil, the first astral on the left half, the second on the right (a2 0: one astral) */
    static void eye(GuiGraphics g, int x, int y, int a, int a2) {
        for (int r = 0; r < EYE_H; r++)
            for (int c = 0; c < EYE_W; c++) {
                char ch = EYE[r].charAt(c);
                if (ch == '.') continue;
                int iris = dev.frosthaven.ranchercompanion.look.Astral.iris(a2 != 0 && c > 4 ? a2 : a);
                int col = ch == 'I' ? iris : dev.frosthaven.ranchercompanion.look.Palette.shade(iris, ch == 'P' ? -.7 : -.5);
                g.fill(x + c, y + r, x + c + 1, y + r + 1, 0xFF000000 | col);
            }
    }

    /** a gear glyph, 8 x 8 (the tonic settings button) */
    private static final String[] GEAR = {"...GG...", ".G.GG.G.", "..GGGG..", "GGG..GGG", "GGG..GGG", "..GGGG..", ".G.GG.G.", "...GG..."};

    static void gear(GuiGraphics g, int x, int y, int color) {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) if (GEAR[r].charAt(c) == 'G') g.fill(x + c, y + r, x + c + 1, y + r + 1, color);
    }
}
