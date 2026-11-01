package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * God's island can't be claimed. Any GriefPrevention claim that would touch the island's protected circle (island.radius
 * from the End's centre) is refused, whether it's being made or resized into it. Claims out in the Seven Heavens are
 * fine. Admins with soulbound.island.build can still claim there.
 *
 * GriefPrevention isn't a build dependency: its events are found by name when the server starts, so the plugin works
 * the same with or without it.
 */
final class IslandClaims implements Listener {
    private static final String PKG = "me.ryanhamshire.GriefPrevention.events.";
    private final Soulbound plugin;

    IslandClaims(Soulbound plugin) { this.plugin = plugin; }

    void start() {
        Plugin gp = plugin.getServer().getPluginManager().getPlugin("GriefPrevention");
        if (gp == null) return;
        // new claims
        boolean created = hook("ClaimCreatedEvent", new String[]{"getClaim"}, new String[]{"getCreator"});
        // resizing an existing claim (newer GriefPrevention: ClaimResizeEvent / ClaimChangeEvent; older: ClaimModifiedEvent)
        boolean resized = hook("ClaimResizeEvent", new String[]{"getTo"}, new String[]{"getModifier"})
                || hook("ClaimChangeEvent", new String[]{"getTo"}, new String[]{"getModifier"})
                || hook("ClaimModifiedEvent", new String[]{"getTo", "getClaim"}, new String[]{"getModifier"});
        if (created && resized) plugin.getLogger().info("God's island: GriefPrevention claims there are refused (new and resized).");
        else plugin.getLogger().warning("God's island: couldn't fully hook GriefPrevention (new claims " + created + ", resizes " + resized + ").");
    }

    @SuppressWarnings("unchecked")
    private boolean hook(String event, String[] claimGetters, String[] whoGetters) {
        Class<? extends Event> type;
        try {
            Class<?> c = Class.forName(PKG + event, true, plugin.getServer().getPluginManager().getPlugin("GriefPrevention").getClass().getClassLoader());
            if (!Event.class.isAssignableFrom(c) || !Cancellable.class.isAssignableFrom(c)) return false;
            type = (Class<? extends Event>) c;
        } catch (ClassNotFoundException e) {
            return false;
        }
        plugin.getServer().getPluginManager().registerEvent(type, this, EventPriority.HIGH, (l, e) -> {
            if (!type.isInstance(e) || ((Cancellable) e).isCancelled()) return;
            if (!plugin.getConfig().getBoolean("island.no-claims", true)) return;
            Object claim = call(e, claimGetters);
            if (claim == null || !touchesIsland(claim)) return;
            Object who = call(e, whoGetters);
            if (who instanceof CommandSender s && s.hasPermission("soulbound.island.build")) return;
            ((Cancellable) e).setCancelled(true);
            if (who instanceof CommandSender s) s.sendMessage(Component.text("God's island can't be claimed.", NamedTextColor.RED));
        }, plugin, false);
        return true;
    }

    private static Object call(Object o, String[] names) {
        for (String n : names) {
            try {
                Method m = o.getClass().getMethod(n);
                return m.invoke(o);
            } catch (ReflectiveOperationException ignored) {}
        }
        return null;
    }

    /** Does the claim's rectangle reach into the island's circle? */
    private boolean touchesIsland(Object claim) {
        Object a = call(claim, new String[]{"getLesserBoundaryCorner"}), b = call(claim, new String[]{"getGreaterBoundaryCorner"});
        if (!(a instanceof Location lo) || !(b instanceof Location hi)) return false;
        World w = lo.getWorld();
        if (w == null || w.getEnvironment() != World.Environment.THE_END) return false;
        double r = plugin.getConfig().getDouble("island.radius", 200);
        double minX = Math.min(lo.getX(), hi.getX()), maxX = Math.max(lo.getX(), hi.getX()) + 1;
        double minZ = Math.min(lo.getZ(), hi.getZ()), maxZ = Math.max(lo.getZ(), hi.getZ()) + 1;
        double nx = Math.max(minX, Math.min(0, maxX)), nz = Math.max(minZ, Math.min(0, maxZ));   // the claim's nearest point to the centre
        return nx * nx + nz * nz < r * r;
    }
}
