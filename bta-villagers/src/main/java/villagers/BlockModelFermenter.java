package villagers;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.block.model.BlockModelDispatcher;
import net.minecraft.client.render.block.model.generic.BlockModelGeneric;
import net.minecraft.core.block.Block;
import org.useless.dragonfly.models.block.StaticBlockModel;

/** The Fermenter's three looks (empty, working, done), one model file each: models/block/fermenter/0.json to 2.json. */
@Environment(EnvType.CLIENT)
public class BlockModelFermenter extends BlockModelGeneric<BlockLogicFermenter> {
	private final StaticBlockModel[] states = new StaticBlockModel[3];

	public BlockModelFermenter(Block<BlockLogicFermenter> block, String base) {
		super(block, BlockModelDispatcher.loadDataModel(base + "0"));
		for (int i = 0; i < states.length; i++) states[i] = BlockModelDispatcher.loadDataModel(base + i).asModel();
	}

	@Override
	public StaticBlockModel getModelFromData(int data) { return states[Math.min(data & 3, states.length - 1)]; }
}
