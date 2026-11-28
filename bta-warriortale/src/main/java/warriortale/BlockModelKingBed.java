package warriortale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import org.useless.dragonfly.models.block.StaticBlockModel;

@Environment(EnvType.CLIENT)
public class BlockModelKingBed extends BlockModelGeneric<BlockLogicKingBed> {
	private final StaticBlockModel[] quarters = new StaticBlockModel[4];

	public BlockModelKingBed(Block<BlockLogicKingBed> block, String base) {
		super(block, BlockModelDispatcher.loadDataModel(base + "0"));
		for (int i = 0; i < 4; i++) quarters[i] = BlockModelDispatcher.loadDataModel(base + i).asModel();
	}

	@Override
	public StaticBlockModel getModelFromData(int data) { return quarters[data & 3]; }
}
