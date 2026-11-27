package villagers;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.item.model.ItemModelStandard;
import net.minecraft.core.item.Item;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

public class VillagersClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		VanillaTextures.findInBackground();
		ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(Villagers.MOD_ID), d -> d.assignRenderer(MobVillager.class, new MobRendererVillager()));
		ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(Villagers.MOD_ID), d -> {
			for (Item egg : Villagers.eggs) if (egg != null) d.addDispatch(new ItemModelStandard(egg));
			if (Villagers.heart != null) d.addDispatch(new ItemModelStandard(Villagers.heart));
		});
	}
}
