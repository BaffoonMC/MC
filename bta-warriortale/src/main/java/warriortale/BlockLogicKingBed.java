package warriortale;

import net.minecraft.core.block.Block;
import net.minecraft.core.block.BlockLogic;
import net.minecraft.core.block.material.Materials;
import net.minecraft.core.entity.player.Player;
import net.minecraft.core.util.helper.Side;
import net.minecraft.core.world.World;
import net.minecraft.core.world.pos.TilePosc;

/**
 * The King Size Bed: the union bed of the folk. One block, pillows always
 * at its north edge, so the lying axis and head end are fixed by the block
 * itself - no metadata to decode. Promised folk seek it before any
 * ordinary bed.
 */
public class BlockLogicKingBed extends BlockLogic {
	public BlockLogicKingBed(Block<?> block) {
		super(block, Materials.CLOTH);
		setBlockBounds(0.0, 0.0, 0.0, 1.0, 0.5625, 1.0);
	}

	@Override
	public boolean onInteracted(World w, TilePosc pos, Player p, Side side, double xHit, double yHit) {
		if (!w.isClientSide) p.sendStatusMessage("A king bed. Promised folk will come to it at night; the pillows face north.");
		return true;
	}
}
