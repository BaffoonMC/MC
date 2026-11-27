package villagers;

import net.minecraft.core.entity.Entity;
import net.minecraft.core.entity.player.Player;
import turniplabs.halplibe.helper.network.NetworkMessage;
import turniplabs.halplibe.helper.network.UniversalPacket;

/** Client to server: the player pressed Trade on this offer. The server checks everything again before it happens. */
public class DoTradeMsg implements NetworkMessage {
	int entityId, offer;

	public DoTradeMsg() {}
	public DoTradeMsg(int entityId, int offer) { this.entityId = entityId; this.offer = offer; }

	@Override public void encodeToUniversalPacket(UniversalPacket buf) { buf.writeInt(entityId); buf.writeInt(offer); }
	@Override public void decodeFromUniversalPacket(UniversalPacket buf) { entityId = buf.readInt(); offer = buf.readInt(); }

	@Override
	public void handle(NetworkContext ctx) {
		Player p = ctx.player;
		if (p == null || p.world == null || p.world.isClientSide) return;
		Entity e = p.world.getEntityByID(entityId);
		if (!(e instanceof MobVillager) || !e.isAlive() || p.distanceToSqr(e) > 8 * 8) return;
		((MobVillager) e).trade(p, offer);
	}
}
