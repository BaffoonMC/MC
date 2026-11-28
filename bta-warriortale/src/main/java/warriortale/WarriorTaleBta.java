package warriortale;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.Items;
import net.minecraft.core.util.collection.NamespaceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryRegistry;
import turniplabs.halplibe.util.TomlConfigHandler;
import turniplabs.halplibe.util.dependency.Key;
import turniplabs.halplibe.util.toml.Toml;

/**
 * Warrior Tale for Better than Adventure: village folk and the tall Gharr
 * raid-folk. Folk carry genes; bread promises a pair; at night they walk to
 * a bed together, the bed creaks under the weight, hearts rise, and a child
 * is born with both parents' genes blended. No bed, no child.
 */
public class WarriorTaleBta implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("warriortale", true);
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static TomlConfigHandler cfg;
	public static Item eggFolk;
	public static Item eggGharr;

	@Override
	public void onInitialize() {
		Toml def = new Toml("WarriorTale");
		def.addEntry("egg_item_id", "first of 2 item ids in a row (folk egg, gharr egg). change if it clashes with another mod", 23620);
		cfg = new TomlConfigHandler(MOD_ID, def);

		EntityDispatcher.getInstance().addMapping(MobFolk.class, new NamespaceID(MOD_ID, "folk"), MobFolk::new,
			"entity.warriortale.folk.name");
		EntityDispatcher.getInstance().addMapping(MobGharr.class, new NamespaceID(MOD_ID, "gharr"), MobGharr::new,
			"entity.warriortale.gharr.name");
		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::items);
		LOG.info("Warrior Tale loaded. The Tide turns.");
	}

	private void items() {
		int id = cfg.getInt("egg_item_id");
		eggFolk = new ItemFolkEgg(MOD_ID + ".egg_folk", MOD_ID + ":item/egg_folk", id, false);
		eggGharr = new ItemFolkEgg(MOD_ID + ".egg_gharr", MOD_ID + ":item/egg_gharr", id + 1, true);
		CreativeInventoryRegistry.INSTANCE.register(eggGharr, new CreativeInventoryPlacement.After(() -> Items.EGG_CHICKEN));
		CreativeInventoryRegistry.INSTANCE.register(eggFolk, new CreativeInventoryPlacement.After(() -> Items.EGG_CHICKEN));
	}
}
