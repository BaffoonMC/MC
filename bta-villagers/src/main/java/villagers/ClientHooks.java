package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/** Client-only calls, kept apart so a dedicated server never loads screen classes. */
@Environment(EnvType.CLIENT)
public final class ClientHooks {
	private ClientHooks() {}

	public static void openTrade(int entityId, int profession) {
		Minecraft mc = Minecraft.getMinecraft();
		if (mc == null || mc.thePlayer == null) return;
		mc.displayScreen(new ScreenVillagerTrade(entityId, profession));
	}
}
