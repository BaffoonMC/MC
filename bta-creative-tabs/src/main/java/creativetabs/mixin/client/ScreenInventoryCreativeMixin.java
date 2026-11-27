package creativetabs.mixin.client;

import creativetabs.SetTabMsg;
import creativetabs.TabHolder;
import creativetabs.Tabs;
import net.minecraft.client.gui.container.ScreenInventory;
import net.minecraft.client.gui.container.ScreenInventoryCreative;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.menu.MenuInventoryCreative;
import net.minecraft.core.sound.SoundCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import turniplabs.halplibe.helper.network.NetworkHandler;

/**
 * Draws the tabs round the creative item panel (the right-hand part of the screen, 120 wide): four above it and four
 * below, each with an item from its category as its icon. Clicking one narrows the item pages to that category;
 * searching still works inside a tab. The tab you were on is remembered while the game is running.
 */
@Mixin(ScreenInventoryCreative.class)
public abstract class ScreenInventoryCreativeMixin extends ScreenInventory {
	@Shadow protected MenuInventoryCreative container;
	@Shadow protected void updatePageSwitcher() {}

	@Unique private static final int PANEL_X = 178, TAB_W = 28, TAB_H = 26, PER_ROW = 4, GAP = 2;
	@Unique private static int ct$remembered = Tabs.ALL;
	@Unique private static final ItemStack[] ct$icons = new ItemStack[Tabs.COUNT];

	private ScreenInventoryCreativeMixin(Player player) { super(player); }

	@Unique private int ct$left() { return (this.width - this.xSize) / 2; }
	@Unique private int ct$top() { return (this.height - this.ySize) / 2; }
	@Unique private int ct$tabX(int tab) { return ct$left() + PANEL_X + (tab % PER_ROW) * (TAB_W + GAP); }
	@Unique private int ct$tabY(int tab) { return tab < PER_ROW ? ct$top() - TAB_H + 2 : ct$top() + this.ySize - 2; }

	@Unique
	private int ct$tabAt(int mx, int my) {
		for (int t = 0; t < Tabs.COUNT; t++) {
			int x = ct$tabX(t), y = ct$tabY(t);
			if (mx >= x && mx < x + TAB_W && my >= y && my < y + TAB_H) return t;
		}
		return -1;
	}

	@Unique
	private ItemStack ct$icon(int tab) {
		if (ct$icons[tab] == null && MenuInventoryCreative.creativeContents != null) {
			if (tab == Tabs.ALL) {
				for (ItemStack s : MenuInventoryCreative.creativeContents)
					if (s != null && String.valueOf(s.getItemKey()).toLowerCase().contains("compass")) { ct$icons[tab] = s; break; }
			}
			if (ct$icons[tab] == null)
				for (ItemStack s : MenuInventoryCreative.creativeContents)
					if (s != null && (tab == Tabs.ALL || Tabs.classify(s) == tab)) { ct$icons[tab] = s; break; }
		}
		return ct$icons[tab];
	}

	@Unique
	private void ct$select(int tab, boolean tellServer) {
		if (!(container instanceof TabHolder)) return;
		ct$remembered = tab;
		((TabHolder) container).ct$applyTab(tab);
		updatePageSwitcher();
		if (tellServer) NetworkHandler.sendToServer(new SetTabMsg(tab));
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void ct$init(CallbackInfo ci) {
		if (container instanceof TabHolder && ((TabHolder) container).ct$tab() != ct$remembered) ct$select(ct$remembered, true);
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void ct$click(int mx, int my, int button, CallbackInfo ci) {
		if (button != 0) return;
		int tab = ct$tabAt(mx, my);
		if (tab < 0) return;
		if (!(container instanceof TabHolder) || ((TabHolder) container).ct$tab() != tab) {
			ct$select(tab, true);
			this.mc.sndManager.playSound("random.click", SoundCategory.GUI_SOUNDS, 1.0F, 1.0F);
		}
		ci.cancel();
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void ct$tabs(int mx, int my, float partialTick, CallbackInfo ci) {
		int current = container instanceof TabHolder ? ((TabHolder) container).ct$tab() : Tabs.ALL;
		int hover = ct$tabAt(mx, my);
		for (int t = 0; t < Tabs.COUNT; t++) {
			int x = ct$tabX(t), y = ct$tabY(t);
			boolean on = t == current, top = t < PER_ROW;
			int face = on ? 0xFFC6C6C6 : (t == hover ? 0xFFA6A6B6 : 0xFF8B8B8B);
			drawRect(x, y, x + TAB_W, y + TAB_H, 0xFF000000);
			drawRect(x + 1, y + 1, x + TAB_W - 1, y + TAB_H - 1, 0xFFFFFFFF);
			drawRect(x + 2, y + 2, x + TAB_W - 1, y + TAB_H - 1, 0xFF555555);
			drawRect(x + 2, y + 2, x + TAB_W - 2, y + TAB_H - 2, face);
			// the selected tab runs into the panel instead of sitting behind it
			if (on) { if (top) drawRect(x + 2, y + TAB_H - 2, x + TAB_W - 2, y + TAB_H, face); else drawRect(x + 2, y, x + TAB_W - 2, y + 2, face); }
		}
		for (int t = 0; t < Tabs.COUNT; t++) {
			ItemStack icon = ct$icon(t);
			if (icon != null && this.itemElement != null) this.itemElement.render(icon, ct$tabX(t) + 6, ct$tabY(t) + 5);
		}
		if (hover >= 0) {
			String tip = Tabs.NAMES[hover];
			int tw = tip.length() * 6;
			int tx = Math.min(mx + 10, this.width - tw - 8), ty = my - 14;
			drawRect(tx - 3, ty - 3, tx + tw + 3, ty + 10, 0xF0100010);
			drawStringShadow(this.fontRenderer, tip, tx, ty, 0xFFFFFF);
		}
	}
}
