package villagers;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.entity.TileEntity;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.enums.EnumDropCause;
import net.minecraft.core.item.ItemFood;
import net.minecraft.core.item.ItemStack;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.WorldSource;
import net.minecraft.core.world.pos.TilePosc;
import org.joml.primitives.AABBd;
import org.joml.primitives.AABBdc;

import java.util.Random;

/**
 * The Fermenter: a steel tank on legs that rots meat. Put a piece of raw meat or fish in (use it on the tank);
 * it goes off over the next few minutes; use the tank again to take the Rotten Meat out.
 * Its state is the block's data: 0 empty, 1 working, 2 done.
 */
public class BlockLogicFermenter extends BlockLogic {
	public static final int EMPTY = 0, WORKING = 1, DONE = 2;

	public BlockLogicFermenter(Block<?> block) {
		super(block, Materials.METAL);
		setBlockBounds(0.125, 0.0, 0.125, 0.875, 1.0, 0.875);
	}

	private static boolean rawMeat(ItemStack s) {
		return s != null && s.getItem() instanceof ItemFood && String.valueOf(s.getItemKey()).toLowerCase().contains("raw");
	}

	@Override
	public boolean onInteracted(World w, TilePosc pos, Player p, Side side, double xHit, double yHit) {
		int state = w.getBlockData(pos) & 3;
		ItemStack held = p.getHeldItem();
		if (state == EMPTY) {
			if (!rawMeat(held)) {
				if (!w.isClientSide) p.sendStatusMessage("The Fermenter is empty. Put raw meat or fish in it.");
				return true;
			}
			if (!w.isClientSide) {
				held.consumeItem(p);
				w.setBlockDataNotify(pos, WORKING);
				w.playSoundAtEntity(null, p, "random.splash", 0.5F, 1.4F);
			}
			return true;
		}
		if (state == WORKING) {
			if (!w.isClientSide) p.sendStatusMessage("It's still turning. Give it a few minutes.");
			return true;
		}
		if (!w.isClientSide) {
			w.setBlockDataNotify(pos, EMPTY);
			w.dropItem(pos, new ItemStack(Villagers.rottenMeat, 1));
			w.playSoundAtEntity(null, p, "item.pickup", 1.0F, 0.7F);
		}
		return true;
	}

	/** Each random tick there is a one-in-three chance the meat has turned: a few minutes, give or take. */
	@Override
	public void updateTick(World w, TilePosc pos, Random rand, boolean randomTick) {
		if (!randomTick || w.isClientSide) return;
		if ((w.getBlockData(pos) & 3) == WORKING && rand.nextInt(3) == 0) w.setBlockDataNotify(pos, DONE);
	}

	@Override
	public ItemStack[] getBreakResult(World w, EnumDropCause cause, int data, TileEntity te) {
		ItemStack self = new ItemStack(this.block, 1);
		if (cause != EnumDropCause.PICK_BLOCK && (data & 3) == DONE) return new ItemStack[]{self, new ItemStack(Villagers.rottenMeat, 1)};
		return new ItemStack[]{self};
	}

	@Override
	public AABBdc getBoundsFromState(WorldSource src, TilePosc pos) { return new AABBd(0.125, 0.0, 0.125, 0.875, 1.0, 0.875); }

	@Override public boolean isCubeShaped() { return false; }
	@Override public boolean isSolidRender() { return false; }
}
