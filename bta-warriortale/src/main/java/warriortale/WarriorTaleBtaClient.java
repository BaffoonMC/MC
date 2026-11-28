package warriortale;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.render.item.model.ItemModelStandard;
import turniplabs.halplibe.event.defs.ClientEvents;
import turniplabs.halplibe.util.dependency.Key;

public class WarriorTaleBtaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientEvents.AFTER_CLIENT_START.listen(Key.of(WarriorTaleBta.MOD_ID), () -> {
			try { net.minecraft.client.sound.SoundRepository.namespaceAdded(WarriorTaleBta.MOD_ID); }
			catch (Throwable t) { WarriorTaleBta.LOG.warn("WarriorTale: couldn't load the mod's sounds: {}", t.toString()); }
		});
		ClientEvents.ENTITY_RENDERER_RELOAD.listen(Key.of(WarriorTaleBta.MOD_ID), d -> {
			d.assignRenderer(MobFolk.class, new MobRendererFolk("geometry.warriortale.folk"));
			d.assignRenderer(MobGharr.class, new MobRendererFolk("geometry.warriortale.gharr"));
		});
		ClientEvents.BLOCK_MODEL_RELOAD.listen(Key.of(WarriorTaleBta.MOD_ID), d -> {
			if (WarriorTaleBta.kingBed != null)
				d.addDispatch(new BlockModelKingBed(WarriorTaleBta.kingBed, WarriorTaleBta.MOD_ID + ":block/king_bed/"));
		});
		ClientEvents.ITEM_MODEL_RELOAD.listen(Key.of(WarriorTaleBta.MOD_ID), d -> {
			if (WarriorTaleBta.eggFolk != null) d.addDispatch(new ItemModelStandard(WarriorTaleBta.eggFolk));
			if (WarriorTaleBta.eggGharr != null) d.addDispatch(new ItemModelStandard(WarriorTaleBta.eggGharr));
		});
	}
}
