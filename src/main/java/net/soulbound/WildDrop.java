package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Wild Drop: a spot at spawn (the bottom of a hole, a pool, a portal frame...) where anyone who steps or falls in
 * is sent to a random safe spot in the overworld (config wild-drop.*). Set it by standing in it:
 * /sb world wilddrop [radius], /sb world wilddrop off.
 */
final class WildDrop {
    private final Soulbound plugin;
    private final Map<UUID, Long> sent = new HashMap<>();

    WildDrop(Soulbound plugin) { this.plugin = plugin; }

    void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 4L); }

    String set(Player p, double radius) {
        Location l = p.getLocation();
        Store st = plugin.store();
        st.set("wild-drop.world", l.getWorld().getName());
        st.set("wild-drop.x", l.getBlockX() + 0.5); st.set("wild-drop.y", (double) l.getBlockY()); st.set("wild-drop.z", l.getBlockZ() + 0.5);
        st.set("wild-drop.radius", radius);
        st.save();
        return "Wild Drop set here (" + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + "), " + radius + " blocks across each way. Anyone who falls in goes somewhere random.";
    }

    String off() {
        plugin.store().set("wild-drop", null); plugin.store().save();
        return "Wild Drop removed.";
    }

    private void tick() {
        var d = plugin.store().data;
        String wn = d.getString("wild-drop.world");
        if (wn == null) return;
        World w = Bukkit.getWorld(wn);
        if (w == null) return;
        double x = d.getDouble("wild-drop.x"), y = d.getDouble("wild-drop.y"), z = d.getDouble("wild-drop.z"), r = d.getDouble("wild-drop.radius", 2);
        long now = System.currentTimeMillis();
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - x) > r || Math.abs(l.getZ() - z) > r || l.getY() < y - 1.5 || l.getY() > y + 2) continue;
            if (p.getGameMode() == GameMode.SPECTATOR || now - sent.getOrDefault(p.getUniqueId(), 0L) < 5000) continue;
            sent.put(p.getUniqueId(), now);
            p.setFallDistance(0);
            w.spawnParticle(Particle.PORTAL, l.clone().add(0, 1, 0), 60, 0.5, 1, 0.5, 0.4);
            w.playSound(l, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
            p.sendActionBar(Component.text("Off you go...", NamedTextColor.GREEN));
            String cmd = plugin.getConfig().getString("wild-drop.command", "");
            if (cmd != null && !cmd.isBlank()) p.performCommand(cmd.replace("{player}", p.getName()));
            else send(p, 0);
        }
        if (sent.size() > 200) sent.values().removeIf(t -> now - t > 60_000);
    }

    /** The world people are dropped into: wild-drop.world, or the main overworld. */
    private World target() {
        String n = plugin.getConfig().getString("wild-drop.world", "");
        World w = n == null || n.isBlank() ? null : Bukkit.getWorld(n);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    private void send(Player p, int attempt) {
        if (!p.isOnline()) return;
        var c = plugin.getConfig();
        World w = target();
        if (attempt >= 12) {
            p.teleportAsync(w.getSpawnLocation());
            p.sendMessage(Component.text("Couldn't find a safe spot out there, so here's the world spawn instead.", NamedTextColor.GRAY));
            return;
        }
        double min = c.getDouble("wild-drop.min-range", 500), max = Math.max(min + 1, c.getDouble("wild-drop.max-range", 5000));
        double ang = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        double dist = Math.sqrt(ThreadLocalRandom.current().nextDouble(min * min, max * max));
        int x = (int) Math.floor(c.getDouble("wild-drop.center-x", 0) + Math.cos(ang) * dist);
        int z = (int) Math.floor(c.getDouble("wild-drop.center-z", 0) + Math.sin(ang) * dist);
        w.getChunkAtAsync(x >> 4, z >> 4).thenAccept(ch -> {
            Block top = w.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Location to = new Location(w, x + 0.5, top.getY() + 1, z + 0.5, p.getLocation().getYaw(), 0);
            if (!safe(w, top) || !w.getWorldBorder().isInside(to)) { send(p, attempt + 1); return; }
            p.setFallDistance(0);
            p.teleportAsync(to).thenAccept(ok -> {
                if (!ok) return;
                p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                p.sendActionBar(Component.text("Welcome to the wild.", NamedTextColor.GREEN));
            });
        });
    }

    private static boolean safe(World w, Block top) {
        if (top.getY() <= w.getMinHeight() + 1 || top.getY() >= w.getMaxHeight() - 3) return false;
        Material m = top.getType();
        if (top.isLiquid() || !m.isSolid()) return false;
        switch (m) {
            case MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE, POWDER_SNOW, SWEET_BERRY_BUSH, POINTED_DRIPSTONE, ICE, BLUE_ICE, PACKED_ICE -> { return false; }
            default -> { }
        }
        Block a = top.getRelative(0, 1, 0), b = top.getRelative(0, 2, 0);
        return a.isPassable() && !a.isLiquid() && b.isPassable() && !b.isLiquid();
    }
}
