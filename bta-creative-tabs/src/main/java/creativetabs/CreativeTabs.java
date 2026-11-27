package creativetabs;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import turniplabs.halplibe.HalpLibe;
import turniplabs.halplibe.helper.network.NetworkHandler;

/** Category tabs for Better than Adventure's creative inventory. */
public class CreativeTabs implements ModInitializer {
	public static final String MOD_ID = HalpLibe.registerMod("creativetabs", true);
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		NetworkHandler.registerNetworkMessage(SetTabMsg::new);
		LOG.info("Creative Tabs loaded.");
	}
}
