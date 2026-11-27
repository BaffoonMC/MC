package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.ButtonElement;
import net.minecraft.client.gui.ItemElement;
import net.minecraft.client.gui.Screen;
import net.minecraft.core.entity.Entity;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.sound.SoundCategory;
import turniplabs.halplibe.helper.network.NetworkHandler;

/**
 * The trading window, laid out like the classic one: the profession as the title, one offer at a time with arrows
 * either side to flip through them, then the row where the trade happens (what you are carrying of the price on the
 * left, the goods on the right - click the goods to take them), and your inventory underneath.
 * The window is drawn from plain rectangles, so it needs no texture file.
 */
@Environment(EnvType.CLIENT)
public class ScreenVillagerTrade extends Screen {
	private static final int W = 176, H = 166;
	private static final int PRICE_X = 36, GOODS_X = 120, OFFER_Y = 24, TRADE_Y = 53;
	private final int entityId, profession;
	private final Trades.Offer[] offers;
	private ItemElement items;
	private ButtonElement prev, next;
	private int left, top, shown;

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
		left = (width - W) / 2;
		top = (height - H) / 2;
		buttons.clear();
		prev = add(new ButtonElement(100, left + 8, top + OFFER_Y - 1, 14, 20, "<"));
		next = add(new ButtonElement(101, left + W - 22, top + OFFER_Y - 1, 14, 20, ">"));
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

	/** An inventory slot: a sunken grey square, 18 across, with the item drawn at x+1, y+1. */
	private void slot(int x, int y) {
		drawRect(x, y, x + 18, y + 18, 0xFFFFFFFF);
		drawRect(x, y, x + 17, y + 17, 0xFF373737);
		drawRect(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
	}

	/** The fat grey arrow between price and goods; crossed out in red when the trade can't be made. */
	private void arrow(int x, int y, boolean crossed) {
		int c = 0xFF8B8B8B;
		drawRect(x, y + 6, x + 14, y + 10, c);
		for (int i = 0; i < 7; i++) drawRect(x + 14 + i, y + 1 + i, x + 15 + i, y + 15 - i, c);
		if (crossed) for (int i = 0; i < 12; i++) {
			drawRect(x + 4 + i, y + 2 + i, x + 6 + i, y + 4 + i, 0xFFC02020);
			drawRect(x + 14 - i, y + 2 + i, x + 16 - i, y + 4 + i, 0xFFC02020);
		}
	}

	@Override
	public void render(int mx, int my, float partialTick) {
		renderBackground();
		shown = Math.floorMod(shown, offers.length);
		Trades.Offer o = offers[shown];
		boolean can = carried(o.want) >= o.wantCount;
		prev.enabled = shown > 0;
		next.enabled = shown < offers.length - 1;

		// the panel, with the usual light top-left and dark bottom-right edges
		drawRect(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF000000);
		drawRect(left, top, left + W, top + H, 0xFF555555);
		drawRect(left, top, left + W - 2, top + H - 2, 0xFFFFFFFF);
		drawRect(left + 2, top + 2, left + W - 2, top + H - 2, 0xFFC6C6C6);

		String p = Villagers.PROFESSIONS[Math.floorMod(profession, Villagers.PROFESSIONS.length)];
		drawStringCenteredNoShadow(fontRenderer, Character.toUpperCase(p.charAt(0)) + p.substring(1), left + W / 2, top + 6, 0x404040);
		drawStringNoShadow(fontRenderer, (shown + 1) + "/" + offers.length, left + W - 24, top + 6, 0x808080);

		arrow(left + 80, top + OFFER_Y + 1, false);                       // the offer itself: price -> goods
		slot(left + PRICE_X, top + TRADE_Y);                              // the trade row
		slot(left + PRICE_X + 26, top + TRADE_Y);
		arrow(left + 80, top + TRADE_Y + 1, !can);
		drawRect(left + GOODS_X - 5, top + TRADE_Y - 5, left + GOODS_X + 23, top + TRADE_Y + 23, 0xFFFFFFFF);
		drawRect(left + GOODS_X - 5, top + TRADE_Y - 5, left + GOODS_X + 22, top + TRADE_Y + 22, 0xFF373737);
		drawRect(left + GOODS_X - 4, top + TRADE_Y - 4, left + GOODS_X + 22, top + TRADE_Y + 22, 0xFF8B8B8B);
		boolean overGoods = mx >= left + GOODS_X - 4 && mx < left + GOODS_X + 22 && my >= top + TRADE_Y - 4 && my < top + TRADE_Y + 22;
		if (can && overGoods) drawRect(left + GOODS_X - 4, top + TRADE_Y - 4, left + GOODS_X + 22, top + TRADE_Y + 22, 0xFFB8D8B0);

		drawStringNoShadow(fontRenderer, "Inventory", left + 8, top + 73, 0x404040);
		for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) slot(left + 7 + c * 18, top + 83 + r * 18);
		for (int c = 0; c < 9; c++) slot(left + 7 + c * 18, top + 141);

		super.render(mx, my, partialTick);                                // the two arrow buttons

		items.render(o.wanted(), left + PRICE_X + 1, top + OFFER_Y + 1);
		items.render(o.given(), left + GOODS_X + 1, top + OFFER_Y + 1);
		int have = carried(o.want);
		if (have > 0) items.render(new ItemStack(o.want, Math.min(have, 64)), left + PRICE_X + 1, top + TRADE_Y + 1);
		if (can) items.render(o.given(), left + GOODS_X + 1, top + TRADE_Y + 1);
		if (mc.thePlayer != null) {
			ItemStack[] inv = mc.thePlayer.inventory.mainInventory;
			for (int i = 0; i < inv.length && i < 36; i++) {
				if (inv[i] == null) continue;
				int x = left + 8 + (i % 9) * 18, y = i < 9 ? top + 142 : top + 84 + (i / 9 - 1) * 18;
				items.render(inv[i], x, y);
			}
		}
		if (overGoods) {
			String tip = can ? "Click to trade" : "You need " + o.wantCount + " " + o.wanted().getDisplayName();
			int tw = fontRenderer.getStringWidth(tip);
			drawRect(mx + 8, my - 14, mx + 14 + tw, my - 1, 0xF0100010);
			drawStringShadow(fontRenderer, tip, mx + 11, my - 11, can ? 0xFFFFFF : 0xFF8080);
		}
	}

	@Override
	public void mouseClicked(int mx, int my, int button) {
		super.mouseClicked(mx, my, button);
		if (button != 0) return;
		boolean overGoods = mx >= left + GOODS_X - 4 && mx < left + GOODS_X + 22 && my >= top + TRADE_Y - 4 && my < top + TRADE_Y + 22;
		Trades.Offer o = offers[Math.floorMod(shown, offers.length)];
		if (overGoods && carried(o.want) >= o.wantCount) {
			mc.sndManager.playSound("random.click", SoundCategory.GUI_SOUNDS, 1.0F, 1.0F);
			NetworkHandler.sendToServer(new DoTradeMsg(entityId, shown));
		}
	}

	@Override
	protected void buttonClicked(ButtonElement button) {
		if (button.id == 100 && shown > 0) shown--;
		if (button.id == 101 && shown < offers.length - 1) shown++;
	}

	@Override
	public void keyPressed(char c, int key, int mx, int my) {
		if (key == 1 || c == 'e' || c == 'E') { mc.displayScreen(null); return; }
		super.keyPressed(c, key, mx, my);
	}

	@Override public boolean isPauseScreen() { return false; }
}
