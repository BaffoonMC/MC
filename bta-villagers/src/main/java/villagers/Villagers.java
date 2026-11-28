package villagers;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.entity.EntityDispatcher;
import net.minecraft.core.block.Block;
import net.minecraft.core.block.Blocks;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import turniplabs.halplibe.helper.BlockBuilder;
import turniplabs.halplibe.helper.RecipeBuilder;
import net.minecraft.core.item.Items;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryPlacement;
import turniplabs.halplibe.helper.creativeInventory.CreativeInventoryRegistry;
import net.minecraft.core.util.collection.NamespaceID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.event.defs.CommonEvents;
import turniplabs.halplibe.helper.network.NetworkHandler;
import turniplabs.halplibe.util.TomlConfigHandler;
import turniplabs.halplibe.util.dependency.Key;
import turniplabs.halplibe.util.toml.Toml;

/** Villagers for Better than Adventure: a wandering, bartering mob in five professions, with spawn eggs. */
public class Villagers implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("btavillagers", true);
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	/** Profession names, in skin-variant order (textures/entity/villager/0.png to 4.png). */
	public static final String[] PROFESSIONS = {"farmer", "librarian", "priest", "smith", "butcher"};

	public static TomlConfigHandler cfg;
	/** eggs[0] hatches a random villager; eggs[1 + n] hatches profession n. */
	public static final Item[] eggs = new Item[1 + PROFESSIONS.length];
	public static Item heart;
	public static Item rottenMeat;
	public static Block<BlockLogicFermenter> fermenter;

	@Override
	public void onInitialize() {
		Toml def = new Toml("Villagers");
		def.addEntry("egg_item_id", "first of 8 item ids in a row (six spawn eggs, the Heart of Darkness, Rotten Meat). change if it clashes with another mod", 23600);
		def.addEntry("fermenter_block_id", "the Fermenter block. change if it clashes with another mod", 2460);
		cfg = new TomlConfigHandler(MOD_ID, def);

		EntityDispatcher.getInstance().addMapping(MobVillager.class, new NamespaceID(MOD_ID, "villager"), MobVillager::new,
			"entity.btavillagers.villager.name");
		NetworkHandler.registerNetworkMessage(OpenTradeMsg::new);
		NetworkHandler.registerNetworkMessage(DoTradeMsg::new);
		NetworkHandler.registerNetworkMessage(InhumanSyncMsg::new);
		CommonEvents.BEFORE_GAME_START.listen(Key.of(MOD_ID), this::items);
		CommonEvents.RECIPES_READY.listen(Key.of(MOD_ID), this::recipes);
		LOG.info("Villagers loaded.");
	}

	private void recipes() {
		RecipeBuilder.Shapeless(MOD_ID).addInput(Items.INGOT_IRON).addInput(Items.INGOT_IRON).addInput(Items.INGOT_IRON).addInput(Items.INGOT_IRON)
			.addInput(Items.BUCKET).addInput(Blocks.GLASS).create("fermenter", new ItemStack(fermenter, 1));
	}

	private void items() {
		int id = cfg.getInt("egg_item_id");
		eggs[0] = new ItemVillagerEgg(MOD_ID + ".egg_villager", MOD_ID + ":item/egg_villager", id, -1);
		for (int i = 0; i < PROFESSIONS.length; i++)
			eggs[1 + i] = new ItemVillagerEgg(MOD_ID + ".egg_" + PROFESSIONS[i], MOD_ID + ":item/egg_" + PROFESSIONS[i], id + 1 + i, i);
		heart = new ItemHeartOfDarkness(MOD_ID + ".heart_of_darkness", MOD_ID + ":item/heart_of_darkness", id + 1 + PROFESSIONS.length);
		CreativeInventoryRegistry.INSTANCE.register(heart, new CreativeInventoryPlacement.After(() -> Items.EGG_CHICKEN));
		rottenMeat = new ItemRottenMeat(MOD_ID + ".rotten_meat", MOD_ID + ":item/rotten_meat", id + 2 + PROFESSIONS.length);
		fermenter = new BlockBuilder(MOD_ID).setHardness(2.0F).setResistance(6.0F).setTicking(true)
			.build("fermenter", "fermenter", cfg.getInt("fermenter_block_id"), BlockLogicFermenter::new);
		// creative menu: all six eggs after the chicken egg, in order
		for (int i = eggs.length - 1; i >= 0; i--)
			CreativeInventoryRegistry.INSTANCE.register(eggs[i], new CreativeInventoryPlacement.After(() -> Items.EGG_CHICKEN));
	}
}
