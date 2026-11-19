package dev.frosthaven.ranchercompanion.mixin;

import dev.frosthaven.ranchercompanion.client.screen.Art;
import dev.frosthaven.ranchercompanion.trait.Tips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A chat hover made by {@link Tips#hover} (a trait, an astral affinity) draws as the drekki screen's tooltips do:
 *  its title, then the lines wrapped to at most 4 rows. Any other hover is left to the game. */
@Mixin(GuiGraphics.class)
abstract class HoverTipMixin {
    @Inject(method = "renderComponentHoverEffect", at = @At("HEAD"), cancellable = true)
    private void rancher$tip(Font font, Style style, int x, int y, CallbackInfo ci) {
        if (style == null || style.getHoverEvent() == null) return;
        Component text = style.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
        var lines = text == null ? null : Tips.lines(text);
        if (lines == null || lines.isEmpty()) return;
        Art.tip((GuiGraphics) (Object) this, font, lines, true, x, y);
        ci.cancel();
    }
}
