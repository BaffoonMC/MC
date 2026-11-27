package villagers.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.PlayerLocal;
import net.minecraft.client.gui.hud.HudIngame;
import net.minecraft.client.gui.hud.component.HudComponent;
import net.minecraft.client.gui.hud.component.HudComponentHealthBar;
import net.minecraft.client.render.texture.stitcher.TextureRegistry;
import net.minecraft.core.player.gamemode.Gamemode;
import net.minecraft.core.player.gamemode.Gamemodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import villagers.Inhuman;

/**
 * The inhuman's hearts. The whole bar is drawn with the game's own hardcore hearts (the bar asks which game mode you
 * are in to pick its heart style; for the inhuman the answer is "hardcore"). And since the bar only ever draws ten
 * hearts, the three extra ones are drawn here in the same style, in a short row just above its left end. They fill
 * last and empty first.
 */
@Mixin(HudComponentHealthBar.class)
public abstract class HudComponentHealthBarMixin {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/entity/player/PlayerLocal;getGamemode()Lnet/minecraft/core/player/gamemode/Gamemode;"))
	private Gamemode vil$style(PlayerLocal player) {
		return Inhuman.is(player) ? Gamemodes.HARDCORE : player.getGamemode();
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
			int hx = x + i * 8;
			hud.drawGuiIcon(hx, y, 9, 9, TextureRegistry.getTexture("minecraft:gui/hud/heart/container"));
			int halves = extra - i * 2;
			if (halves >= 2) hud.drawGuiIcon(hx, y, 9, 9, TextureRegistry.getTexture("minecraft:gui/hud/heart/hardcore/full"));
			else if (halves == 1) hud.drawGuiIcon(hx, y, 9, 9, TextureRegistry.getTexture("minecraft:gui/hud/heart/hardcore/half"));
		}
	}
}
