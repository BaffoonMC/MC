package villagers;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePosc;

/** A spawn egg: use it on a block and a villager hatches on that face. profession -1 = a random one. */
public class ItemVillagerEgg extends Item {
	private final int profession;

	public ItemVillagerEgg(String key, String texture, int id, int profession) {
		super(key, texture, id);
		this.profession = profession;
	}

	@Override
	public boolean onUseOnBlock(ItemStack stack, World world, Player player, TilePosc pos, Side side, double xHit, double yHit) {
		if (world.isClientSide) return true;
		double x = pos.x() + side.offsetX() + 0.5, y = pos.y() + side.offsetY(), z = pos.z() + side.offsetZ() + 0.5;
		MobVillager v = new MobVillager(world);
		v.setPos(x, y, z);
		v.spawnInit();
		v.setSkinVariant(profession >= 0 ? profession : world.rand.nextInt(Villagers.PROFESSIONS.length));
		if (stack.hasCustomName()) v.setNickname(stack.getCustomName());
		world.entityJoinedWorld(v);
		if (player != null) stack.consumeItem(player);
		return true;
	}
}
