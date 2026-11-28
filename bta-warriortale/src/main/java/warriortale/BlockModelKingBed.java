package warriortale;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import org.useless.dragonfly.models.block.StaticBlockModel;

@Environment(EnvType.CLIENT)
public class BlockModelKingBed extends BlockModelGeneric<BlockLogicKingBed> {
	private final StaticBlockModel model;

	public BlockModelKingBed(Block<BlockLogicKingBed> block, String base) {
		super(block, BlockModelDispatcher.loadDataModel(base + "0"));
		model = BlockModelDispatcher.loadDataModel(base + "0").asModel();
	}

	@Override
	public StaticBlockModel getModelFromData(int data) { return model; }
}
