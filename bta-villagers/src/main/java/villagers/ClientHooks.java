package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/** Client-only calls, kept apart so a dedicated server never loads screen classes. */
@Environment(EnvType.CLIENT)
public final class ClientHooks {
	private ClientHooks() {}

	public static void openTrade(int entityId, int profession, int pricePercent) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) return;
		mc.displayScreen(new ScreenVillagerTrade(entityId, profession, pricePercent));
	}

	public static void setInhuman(boolean inhuman) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc != null && mc.thePlayer instanceof InhumanHolder) ((InhumanHolder) mc.thePlayer).vil$setInhuman(inhuman);
	}
}
