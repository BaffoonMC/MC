package villagers;

import turniplabs.halplibe.helper.EnvironmentHelper;
import turniplabs.halplibe.helper.network.NetworkMessage;
import turniplabs.halplibe.helper.network.UniversalPacket;

/** Server to client: whether you are inhuman (so your own heart bar and food rules match the server's). */
public class InhumanSyncMsg implements NetworkMessage {
	boolean inhuman;

	public InhumanSyncMsg() {}
	public InhumanSyncMsg(boolean inhuman) { this.inhuman = inhuman; }

	@Override public void encodeToUniversalPacket(UniversalPacket buf) { buf.writeBoolean(inhuman); }
	@Override public void decodeFromUniversalPacket(UniversalPacket buf) { inhuman = buf.readBoolean(); }

	@Override
	public void handle(NetworkContext ctx) {
		if (EnvironmentHelper.isMultiplayerServer() || !EnvironmentHelper.isMultiplayerClient()) return;
		ClientHooks.setInhuman(inhuman);
	}
}
