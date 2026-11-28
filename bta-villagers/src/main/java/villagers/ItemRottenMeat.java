package villagers;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.DamageType;
import net.minecraft.core.world.World;

/** What comes out of the Fermenter. The inhuman thrive on it; it makes anyone else ill. */
public class ItemRottenMeat extends Item {
	public ItemRottenMeat(String key, String texture, int id) { super(key, texture, id); }

	@Override
	public ItemStack onUse(ItemStack stack, World world, Player player) {
		if (world.isClientSide) return stack;
		if (Inhuman.is(player)) {
			if (player.getHealth() >= player.getMaxHealth()) return stack;
			stack.consumeItem(player);
			player.heal(8);
			world.playSoundAtEntity(null, player, "random.bite", 0.6F, 0.7F);
		} else {
			stack.consumeItem(player);
			player.hurt(null, 3, DamageType.COMBAT);
			player.sendStatusMessage("It's rotten. Your stomach turns.");
			world.playSoundAtEntity(null, player, "random.bite", 0.6F, 0.7F);
		}
		return stack;
	}
}
