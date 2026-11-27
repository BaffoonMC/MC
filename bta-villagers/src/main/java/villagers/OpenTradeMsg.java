package villagers;

import turniplabs.halplibe.helper.EnvironmentHelper;
import turniplabs.halplibe.helper.network.NetworkMessage;
import turniplabs.halplibe.helper.network.UniversalPacket;

/** Server to client: open the trading window for this villager. */
public class OpenTradeMsg implements NetworkMessage {
	int entityId, profession;

	public OpenTradeMsg() {}
	public OpenTradeMsg(int entityId, int profession) { this.entityId = entityId; this.profession = profession; }

	@Override public void encodeToUniversalPacket(UniversalPacket buf) { buf.writeInt(entityId); buf.writeInt(profession); }
	@Override public void decodeFromUniversalPacket(UniversalPacket buf) { entityId = buf.readInt(); profession = buf.readInt(); }

	@Override
	public void handle(NetworkContext ctx) {
		if (EnvironmentHelper.isMultiplayerServer()) return;
		ClientHooks.openTrade(entityId, profession);
	}
}
