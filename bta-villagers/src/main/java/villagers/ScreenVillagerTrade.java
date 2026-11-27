package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.ItemElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import turniplabs.halplibe.helper.network.NetworkHandler;

/**
 * The trading window: one row per offer - what the villager wants, what it gives, and a Trade button that is only
 * live while you carry enough. Pressing it asks the server to do the trade; the window stays open for more.
 */
@Environment(EnvType.CLIENT)
public class ScreenVillagerTrade extends Screen {
	private static final int W = 264, ROW = 24;
	private final int entityId, profession;
	private final Trades.Offer[] offers;
	private ItemElement items;
	private int left, top, panelH;

	public ScreenVillagerTrade(int entityId, int profession) {
		super();
		this.entityId = entityId;
		this.profession = profession;
		this.offers = Trades.of(profession);
	}

	@Override
	public void init() {
		super.init();
		items = new ItemElement(mc);
		panelH = 30 + offers.length * ROW + 18;
		left = (width - W) / 2;
		top = (height - panelH) / 2;
		buttons.clear();
		for (int i = 0; i < offers.length; i++) add(new ButtonElement(i, left + W - 58, top + 24 + i * ROW, 50, 20, "Trade"));
	}

	private int carried(Item item) {
		if (mc.thePlayer == null) return 0;
		int n = 0;
		for (ItemStack s : mc.thePlayer.inventory.mainInventory) if (s != null && s.getItem() == item) n += s.stackSize;
		return n;
	}

	@Override
	public void tick() {
		super.tick();
		Entity e = mc.currentWorld == null ? null : mc.currentWorld.getEntityByID(entityId);
		if (e == null || !e.isAlive() || mc.thePlayer == null || mc.thePlayer.distanceToSqr(e) > 8 * 8) mc.displayScreen(null);
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		renderBackground();
		// the panel: a dark edge, a light face, a darker strip per row
		drawRect(left - 2, top - 2, left + W + 2, top + panelH + 2, 0xFF2B2B2B);
		drawRect(left, top, left + W, top + panelH, 0xFFC6C6C6);
		String p = Villagers.PROFESSIONS[Math.floorMod(profession, Villagers.PROFESSIONS.length)];
		drawStringNoShadow(fontRenderer, Character.toUpperCase(p.charAt(0)) + p.substring(1), left + 8, top + 8, 0x404040);
		for (int i = 0; i < offers.length; i++) {
			Trades.Offer o = offers[i];
			int y = top + 24 + i * ROW;
			boolean can = carried(o.want) >= o.wantCount;
			drawRect(left + 4, y - 1, left + W - 62, y + 21, can ? 0xFFB4C8B0 : 0xFFA8A8A8);
			ItemStack want = o.wanted(), give = o.given();
			drawStringNoShadow(fontRenderer, o.wantCount + " " + want.getDisplayName(), left + 28, y + 6, can ? 0x204020 : 0x602020);
			drawStringNoShadow(fontRenderer, ">", left + 106, y + 6, 0x404040);
			drawStringNoShadow(fontRenderer, o.giveCount + " " + give.getDisplayName(), left + 138, y + 6, 0x303030);
			if (i < buttons.size()) buttons.get(i).enabled = can;
		}
		drawStringNoShadow(fontRenderer, "Green rows are ones you can afford. Olivine is their money.", left + 8, top + panelH - 13, 0x606060);
		super.render(mx, my, partialTick);
		for (int i = 0; i < offers.length; i++) {
			int y = top + 24 + i * ROW;
			items.render(offers[i].wanted(), left + 8, y + 2);
			items.render(offers[i].given(), left + 118, y + 2);
		}
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		if (button.id >= 0 && button.id < offers.length) NetworkHandler.sendToServer(new DoTradeMsg(entityId, button.id));
	}

	@Override
	public void keyPressed(char c, int key, int mx, int my) {
		if (key == 1 || c == 'e' || c == 'E') { mc.displayScreen(null); return; }
		super.keyPressed(c, key, mx, my);
	}

	@Override public boolean isPauseScreen() { return false; }
}
