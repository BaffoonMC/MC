package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Graced see their own hearts as the icy blue ones powder snow gives.
 *
 * Only their own game is told they're frozen (one entity-data packet, sent to them alone), so the server never freezes
 * them: no slowdown, no freeze damage, and nobody else sees them shiver. The game never thaws a player by itself, so the
 * hearts stay blue; a check every half second puts them back after a respawn, a world change or a real dip in powder
 * snow. The pack hides the snowflake frame that normally comes with them.
 *
 * Paper has no API for this, so it's done by reflection: if anything doesn't match the server, it logs one warning and
 * the hearts simply stay red.
 */
final class GraceHearts implements Listener {
    private static final int FROZEN = 140;
    private final Soulbound plugin;
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private boolean broken;
    private Method getHandle, send, create;
    private Field connection;
    private Object accessor;
    private Constructor<?> packet;

    GraceHearts(Soulbound plugin) { this.plugin = plugin; }

    private boolean on() { return plugin.getConfig().getBoolean("grace.frozen-hearts", true); }

    private boolean graced(Player p) { return plugin.grace() != null && plugin.grace().is(p); }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sync(p);
        }, 40L, 10L);
    }

    /** Blue hearts for the Graced; anyone who's lost their grace (or the feature turned off) gets their real value back. */
    void sync(Player p) {
        if (on() && graced(p) && !p.isDead()) {
            // a real freeze in powder snow is left to show as it is; ours takes over again once it thaws
            if (p.getFreezeTicks() > 0) return;
            if (send(p, FROZEN)) shown.add(p.getUniqueId());
        } else if (shown.remove(p.getUniqueId())) {
            send(p, p.getFreezeTicks());
        }
    }

    private void soon(Player p) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) sync(p); }, 2L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { soon(e.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent e) { soon(e.getPlayer()); }
    @EventHandler public void onWorld(PlayerChangedWorldEvent e) { soon(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { shown.remove(e.getPlayer().getUniqueId()); }

    // ---------------------------------------------------------------- the packet

    /** Tell only this player's game how frozen they are. */
    private boolean send(Player p, int ticks) {
        if (broken) return false;
        try {
            Object handle = handle(p);
            Object value = create.invoke(null, accessor, ticks);
            Object pkt = packet.newInstance(p.getEntityId(), List.of(value));
            send.invoke(connection.get(handle), pkt);
            return true;
        } catch (Throwable t) {
            broken = true;
            plugin.getLogger().warning("Graced blue hearts are off: this server build doesn't match (" + t + ").");
            return false;
        }
    }

    private Object handle(Player p) throws Exception {
        if (getHandle == null) {
            getHandle = p.getClass().getMethod("getHandle");
            Object h = getHandle.invoke(p);
            ClassLoader cl = h.getClass().getClassLoader();
            Class<?> entity = Class.forName("net.minecraft.world.entity.Entity", false, cl);
            accessor = entity.getField("DATA_TICKS_FROZEN").get(null);
            Class<?> dataValue = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue", false, cl);
            Class<?> accessorType = Class.forName("net.minecraft.network.syncher.EntityDataAccessor", false, cl);
            create = dataValue.getMethod("create", accessorType, Object.class);
            Class<?> pktType = Class.forName("net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket", false, cl);
            packet = pktType.getConstructor(int.class, List.class);
            connection = h.getClass().getField("connection");
            Class<?> packetIface = Class.forName("net.minecraft.network.protocol.Packet", false, cl);
            send = connection.getType().getMethod("send", packetIface);
            return h;
        }
        return getHandle.invoke(p);
    }
}
