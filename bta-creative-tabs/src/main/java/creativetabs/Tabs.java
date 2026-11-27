package creativetabs;

import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.block.ItemBlock;

import java.util.Locale;

/**
 * The tabs and which one each item belongs under. Better than Adventure doesn't sort its items into categories, so
 * this goes by what kind of item it is and by words in its name key (so other mods' items are sorted too).
 */
public final class Tabs {
	private Tabs() {}

	public static final int ALL = 0, BUILDING = 1, NATURAL = 2, FUNCTIONAL = 3, TOOLS = 4, COMBAT = 5, FOOD = 6, MATERIALS = 7;
	public static final String[] NAMES = {"All Items", "Building Blocks", "Natural Blocks", "Functional and Redstone", "Tools", "Combat", "Food", "Materials and Misc"};
	public static final int COUNT = NAMES.length;

	private static final String[] FUNCTIONAL_WORDS = {"redstone", "piston", "lever", "button", "pressure", "rail", "repeater", "dispenser", "activator",
		"sensor", "noteblock", "note.", "tnt", "door", "trapdoor", "gate", "lamp", "furnace", "chest", "workbench", "bed", "ladder", "torch", "sign",
		"jukebox", "spawner", "trommel", "crate", "basket", "motion", "spikes", "conveyor", "seat", "lantern", "flag", "painting", "minecart", "boat",
		"saddle", "pumpkin.carved", "jar", "dynamite", "cannon"};
	private static final String[] NATURAL_WORDS = {"grass", "dirt", "sand", "gravel", "log", "leaves", "sapling", "flower", "ore", "stone", "basalt",
		"limestone", "granite", "permafrost", "snow", "ice", "clay", "mud", "moss", "cactus", "mushroom", "algae", "pumpkin", "sugarcane", "crops",
		"bedrock", "obsidian", "netherrack", "soulsand", "glowstone", "sponge", "cobweb", "web", "tallgrass", "fern", "bush", "scoria", "marble",
		"slate", "peat", "layer", "farmland", "path", "thorn", "cherry", "vine"};
	private static final String[] WORKED_WORDS = {"brick", "polished", "carved", "pillar", "slab", "stairs", "cobble", "planks", "fence", "wall",
		"capstone", "chiseled", "block.", "glass", "wool", "lamp"};
	private static final String[] COMBAT_WORDS = {"sword", "armor", "bow", "arrow", "quiver", "handcannon", "shield", "helmet", "chestplate",
		"leggings", "boots", "ammo"};
	private static final String[] TOOL_WORDS = {"tool", "pickaxe", "axe", "shovel", "hoe", "shears", "fishing", "firestriker", "bucket", "compass",
		"clock", "wand", "label", "map", "rope", "calendar", "sickle"};

	private static boolean has(String key, String[] words) {
		for (String w : words) if (key.contains(w)) return true;
		return false;
	}

	public static int classify(ItemStack stack) {
		Item item = stack.getItem();
		String key = String.valueOf(stack.getItemKey()).toLowerCase(Locale.ROOT);
		boolean block = item instanceof ItemBlock || key.startsWith("tile.");
		if (item instanceof ItemFood || key.contains(".food")) return FOOD;
		if (has(key, FUNCTIONAL_WORDS)) return FUNCTIONAL;
		if (block) return has(key, NATURAL_WORDS) && !has(key, WORKED_WORDS) ? NATURAL : BUILDING;
		if (has(key, COMBAT_WORDS)) return COMBAT;
		if (has(key, TOOL_WORDS)) return TOOLS;
		return MATERIALS;
	}

	public static boolean shows(int tab, ItemStack stack) {
		if (tab == ALL) return true;
		return stack != null && classify(stack) == tab;
	}
}
