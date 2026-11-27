package villagers;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.world.World;

/** Still beating. Use it to eat it, and stop being human. */
public class ItemHeartOfDarkness extends Item {
	public ItemHeartOfDarkness(String key, String texture, int id) {
		super(key, texture, id);
		setMaxStackSize(1);
	}

	@Override
	public ItemStack onUse(ItemStack stack, World world, Player player) {
		if (world.isClientSide) return stack;
		if (Inhuman.is(player)) {
			player.sendStatusMessage("There's nothing left in you to lose.");
			return stack;
		}
		stack.consumeItem(player);
		Inhuman.begin(player);
		return stack;
	}
}
