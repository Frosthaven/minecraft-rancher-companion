package dev.frosthaven.ranchercompanion.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The shared parts of the mod's screens (tools/screen_textures.py draws them; the numbers here match its sprites):
 * leather, steel and red buttons in four states, the roster row highlight and the scroll knob, and the thread text color.
 */
final class Art {
    private Art() {}

    static final int THREAD = 0xFFD8C79A, DIM = 0xFF9A8C70, GLOW = 0xFF7FF0DC, WARN = 0xFFE07A6A;
    static final int BTN_W = 60, BTN_H = 16, BTN_Y = 200;

    enum Btn { NORMAL, HOVER, DISABLED, DANGER }

    /** a button of any width (the sprite's left part and its 3 px right edge) with its label centered */
    static void button(GuiGraphics g, ResourceLocation tex, Font font, int x, int y, int w, Btn state, Component label) {
        int u = state.ordinal() * BTN_W;
        g.blit(tex, x, y, u, BTN_Y, w - 3, BTN_H);
        g.blit(tex, x + w - 3, y, u + BTN_W - 3, BTN_Y, 3, BTN_H);
        int c = state == Btn.DISABLED ? 0xFF8D939E : THREAD;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, c, false);
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
}
