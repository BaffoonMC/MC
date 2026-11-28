package warriortale;

import net.minecraft.core.entity.player.Player;
import net.minecraft.core.item.Item;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePosc;

/** A spawn egg: use it on a block and a folk (or a Gharr) steps out. */
public class ItemFolkEgg extends Item {
	private final boolean gharr;

	public ItemFolkEgg(String key, String texture, int id, boolean gharr) {
		super(key, texture, id);
		this.gharr = gharr;
	}

	@Override
	public boolean onUseOnBlock(ItemStack stack, World world, Player player, TilePosc pos, Side side, double xHit, double yHit) {
		if (world.isClientSide) return true;
		double x = pos.x() + side.offsetX() + 0.5, y = pos.y() + side.offsetY(), z = pos.z() + side.offsetZ() + 0.5;
		MobFolk m = gharr ? new MobGharr(world) : new MobFolk(world);
		m.setPos(x, y, z);
		m.spawnInit();
		if (stack.hasCustomName()) m.setNickname(stack.getCustomName());
		world.entityJoinedWorld(m);
		if (player != null) stack.consumeItem(player);
		return true;
	}
}
