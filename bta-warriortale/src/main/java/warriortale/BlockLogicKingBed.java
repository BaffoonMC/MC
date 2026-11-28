package warriortale;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.entity.Mob;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePos;
import net.minecraft.core.world.pos.TilePosc;

/**
 * The King Size Bed: a true 2x2. The placed block is the north-west
 * quarter (data 0); placement claims east, south and south-east as
 * data 1, 2, 3. Pillows span the two north quarters. Breaking any
 * quarter removes the whole bed. The union anchors to the centre seam.
 */
public class BlockLogicKingBed extends BlockLogic {
	private static boolean removing = false;

	public BlockLogicKingBed(Block<?> block) {
		super(block, Materials.CLOTH);
		setBlockBounds(0.0, 0.0, 0.0, 1.0, 0.5625, 1.0);
	}

	@Override
	public void onPlacedByMob(World w, TilePosc pos, Side side, Mob mob, double xHit, double yHit) {
		super.onPlacedByMob(w, pos, side, mob, xHit, yHit);
		if (w.isClientSide) return;
		int x = pos.x(), y = pos.y(), z = pos.z();
		if (w.isAirBlock(x + 1, y, z) && w.isAirBlock(x, y, z + 1) && w.isAirBlock(x + 1, y, z + 1)) {
			int id = this.block.id();
			w.setBlockAndMetadataWithNotify(x + 1, y, z, id, 1);     // NE
			w.setBlockAndMetadataWithNotify(x, y, z + 1, id, 2);     // SW
			w.setBlockAndMetadataWithNotify(x + 1, y, z + 1, id, 3); // SE
		} else if (mob instanceof Player p) {
			p.sendStatusMessage("The king bed wants a clear 2x2 - it grows east and south, pillows north.");
		}
	}

	@Override
	public void onRemoved(World w, TilePosc pos, int data) {
		if (!removing) {
			removing = true;
			try {
				// data: bit 0 = east half, bit 1 = south half; walk back to NW
				int nwX = pos.x() - (data & 1);
				int nwZ = pos.z() - ((data >> 1) & 1);
				int y = pos.y();
				TilePos t = new TilePos();
				for (int dx = 0; dx <= 1; dx++)
					for (int dz = 0; dz <= 1; dz++) {
						t.x = nwX + dx; t.y = y; t.z = nwZ + dz;
						if (t.x == pos.x() && t.z == pos.z()) continue;
						if (w.getBlockType(t) == this.block) w.setBlockWithNotify(t.x, t.y, t.z, 0);
					}
			} finally {
				removing = false;
			}
		}
		super.onRemoved(w, pos, data);
	}

	@Override
	public boolean onInteracted(World w, TilePosc pos, Player p, Side side, double xHit, double yHit) {
		if (!w.isClientSide) p.sendStatusMessage("A king bed. Promised folk will come to it at night; the pillows face north.");
		return true;
	}
}
