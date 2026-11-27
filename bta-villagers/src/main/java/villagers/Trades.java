package villagers;

import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.item.Items;

/** What each profession barters: hand over `want`, get `give`. Olivine is the villagers' money. */
public final class Trades {
	private Trades() {}

	public static final class Offer {
		public final Item want; public final int wantCount;
		public final Item give; public final int giveCount;
		Offer(Item want, int wantCount, Item give, int giveCount) { this.want = want; this.wantCount = wantCount; this.give = give; this.giveCount = giveCount; }
		public ItemStack wanted() { return new ItemStack(want, wantCount); }
		public ItemStack given() { return new ItemStack(give, giveCount); }
	}

	private static Offer[][] table;

	private static Offer o(Item want, int n, Item give, int m) { return new Offer(want, n, give, m); }

	/** Built on first use, once the game's items exist. */
	public static Offer[] of(int profession) {
		if (table == null) {
			Item coin = Items.OLIVINE;
			table = new Offer[][] {
				// farmer
				{ o(Items.WHEAT, 18, coin, 1), o(Items.SEEDS_WHEAT, 32, coin, 1), o(coin, 1, Items.FOOD_BREAD, 4), o(coin, 1, Items.FOOD_APPLE, 5), o(coin, 3, Items.FOOD_PUMPKIN_PIE, 1) },
				// librarian
				{ o(Items.PAPER, 24, coin, 1), o(Items.BOOK, 10, coin, 1), o(coin, 1, Items.BOOK, 2), o(coin, 4, Items.TOOL_COMPASS, 1), o(coin, 4, Items.TOOL_CLOCK, 1) },
				// priest
				{ o(Items.INGOT_GOLD, 8, coin, 1), o(Items.DUST_SUGAR, 20, coin, 1), o(coin, 6, Items.FOOD_APPLE_GOLD, 1), o(coin, 2, Items.STRING, 6) },
				// smith
				{ o(Items.COAL, 16, coin, 1), o(Items.INGOT_IRON, 8, coin, 1), o(coin, 3, Items.TOOL_PICKAXE_IRON, 1), o(coin, 2, Items.TOOL_SWORD_IRON, 1), o(coin, 5, Items.ARMOR_CHESTPLATE_IRON, 1), o(coin, 8, Items.DIAMOND, 1) },
				// butcher
				{ o(Items.FOOD_PORKCHOP_RAW, 12, coin, 1), o(Items.FOOD_FISH_RAW, 12, coin, 1), o(Items.LEATHER, 10, coin, 1), o(coin, 1, Items.FOOD_PORKCHOP_COOKED, 5), o(coin, 1, Items.FOOD_FISH_COOKED, 6) },
			};
		}
		return table[Math.floorMod(profession, table.length)];
	}
}
