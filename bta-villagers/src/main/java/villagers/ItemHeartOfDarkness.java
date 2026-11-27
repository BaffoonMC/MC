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
			player.sendStatusMessage("It beats in time with your own. There is nothing left for it to take.");
			return stack;
		}
		stack.consumeItem(player);
		Inhuman.set(player, true);
		player.heal(Inhuman.EXTRA_HEALTH);
		world.playSoundAtEntity(null, player, "mob.ghast.moan", 0.7F, 0.5F);
		player.sendMessage("§4You are no longer human.");
		player.sendMessage("§7Monsters will leave you be. The sun, and the living, will not.");
		return stack;
	}
}
