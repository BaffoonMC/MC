package villagers.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.hud.HudIngame;
import net.minecraft.client.gui.hud.component.HudComponent;
import net.minecraft.client.gui.hud.component.HudComponentHealthBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villagers.Inhuman;

/**
 * The game's health bar only ever draws ten hearts, so the inhuman's three extra ones are drawn here: a short row of
 * dark hearts just above the left end of the bar. They fill last and empty first.
 */
@Mixin(HudComponentHealthBar.class)
public abstract class HudComponentHealthBarMixin {
	@Unique private static final String[] SHAPE = {".XX.XX.", "XXXXXXX", "XXXXXXX", ".XXXXX.", "..XXX..", "...X..."};

	@Unique
	private static void vil$heart(HudIngame hud, int x, int y, int color, int columns) {
		for (int r = 0; r < SHAPE.length; r++)
			for (int c = 0; c < 7 && c < columns; c++)
				if (SHAPE[r].charAt(c) == 'X') hud.drawRect(x + c, y + r, x + c + 1, y + r + 1, color);
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void vil$extraHearts(HudIngame hud, int xSizeScreen, int ySizeScreen, float partialTick, CallbackInfo ci) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null || !Inhuman.is(mc.thePlayer)) return;
		HudComponent self = (HudComponent) (Object) this;
		int x = self.getLayout().getComponentX(self, xSizeScreen);
		int y = self.getLayout().getComponentY(self, ySizeScreen) - 10;
		int extra = Math.max(0, Math.min(Inhuman.EXTRA_HEALTH, mc.thePlayer.getHealth() - 20));
		for (int i = 0; i < Inhuman.EXTRA_HEALTH / 2; i++) {
			int hx = x + 1 + i * 8, hy = y + 1;
			for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) if (dx != 0 || dy != 0) vil$heart(hud, hx + dx, hy + dy, 0xFF000000, 7);
			vil$heart(hud, hx, hy, 0xFF3A3A3A, 7);
			int halves = extra - i * 2;
			if (halves >= 2) vil$heart(hud, hx, hy, 0xFF7A0C18, 7);
			else if (halves == 1) vil$heart(hud, hx, hy, 0xFF7A0C18, 4);
			if (halves >= 1) hud.drawRect(hx + 1, hy + 1, hx + 2, hy + 2, 0xFFC84058);
		}
	}
}
