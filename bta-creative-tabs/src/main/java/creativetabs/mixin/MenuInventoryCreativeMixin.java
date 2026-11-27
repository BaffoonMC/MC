package creativetabs.mixin;

import creativetabs.TabHolder;
import creativetabs.Tabs;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.player.inventory.menu.MenuInventoryCreative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** The creative menu builds its pages from the search text; this narrows that list to the chosen tab as well. */
@Mixin(MenuInventoryCreative.class)
public abstract class MenuInventoryCreativeMixin implements TabHolder {
	@Shadow public int page;
	@Shadow protected List<ItemStack> searchedItems;
	@Shadow private void refillSearchedItemsMatchingCurrentQuery() {}
	@Shadow protected void updatePage() {}

	@Unique private int ct$current = Tabs.ALL;

	@Override public int ct$tab() { return ct$current; }

	@Override
	public void ct$applyTab(int tab) {
		ct$current = tab;
		page = 0;
		refillSearchedItemsMatchingCurrentQuery();
		updatePage();
	}

	@Inject(method = "refillSearchedItemsMatchingCurrentQuery", at = @At("TAIL"))
	private void ct$narrow(CallbackInfo ci) {
		if (ct$current == Tabs.ALL || searchedItems == null) return;
		final int tab = ct$current;
		searchedItems.removeIf(s -> !Tabs.shows(tab, s));
	}
}
