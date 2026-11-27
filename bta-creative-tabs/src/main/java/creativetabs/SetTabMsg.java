package creativetabs;

import net.minecraft.core.entity.player.Player;
import turniplabs.halplibe.helper.network.NetworkMessage;
import turniplabs.halplibe.helper.network.UniversalPacket;

/** Client to server: the player picked a creative tab, so the server's copy of the item pages matches the client's. */
public class SetTabMsg implements NetworkMessage {
	int tab;

	public SetTabMsg() {}
	public SetTabMsg(int tab) { this.tab = tab; }

	@Override public void encodeToUniversalPacket(UniversalPacket buf) { buf.writeInt(tab); }
	@Override public void decodeFromUniversalPacket(UniversalPacket buf) { tab = buf.readInt(); }

	@Override
	public void handle(NetworkContext ctx) {
		Player p = ctx.player;
		if (p == null || tab < 0 || tab >= Tabs.COUNT) return;
		if (p.inventoryMenu instanceof TabHolder && ((TabHolder) p.inventoryMenu).ct$tab() != tab) ((TabHolder) p.inventoryMenu).ct$applyTab(tab);
	}
}
