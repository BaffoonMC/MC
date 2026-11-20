package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Staff who wander: /bureau roam <role> [radius] [name] puts a lizard here who strolls about within that many blocks,
 * stops to look at anyone who comes close, and says a line when clicked. /bureau roam remove takes the nearest away.
 * They only exist while a player is near, and are remembered across restarts.
 */
final class Roamers implements Listener {
    private static final TextColor GREEN = TextColor.color(0x7BD36A);

    private static final class Roamer {
        String world, name; double x, y, z; int radius; Lizards.Role role;
        Walker w;
        int wait, ticks;
        String key() { return world + "," + x + "," + y + "," + z + "," + radius + "," + role.name() + "," + (name == null ? "" : name.replace(",", " ")); }
    }

    private final Bureau plugin;
    private final List<Roamer> roamers = new ArrayList<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    Roamers(Bureau plugin) { this.plugin = plugin; }

    String place(Player p, Lizards.Role role, int radius, String name) {
        Roamer r = new Roamer();
        Location l = p.getLocation();
        r.world = l.getWorld().getName(); r.x = l.getX(); r.y = l.getY(); r.z = l.getZ();
        r.radius = Math.max(2, Math.min(40, radius));
        r.role = role;
        r.name = name == null || name.isBlank() ? null : name;
        roamers.add(r);
        plugin.dirty();
        return "A " + role.name().toLowerCase(Locale.ROOT) + " lizard will wander within " + r.radius + " blocks of here.";
    }

    String removeNear(Player p) {
        Roamer best = null; double bd = 12 * 12;
        for (Roamer r : roamers) {
            if (!r.world.equals(p.getWorld().getName())) continue;
            double d = p.getLocation().distanceSquared(home(r, p.getWorld()));
            if (r.w != null && r.w.valid() && r.w.at().getWorld() == p.getWorld()) d = Math.min(d, p.getLocation().distanceSquared(r.w.at()));
            if (d < bd) { bd = d; best = r; }
        }
        if (best == null) return "No wandering lizard within 12 blocks.";
        if (best.w != null) best.w.remove();
        roamers.remove(best);
        plugin.dirty();
        return "The wandering lizard clocks out for good.";
    }

    int count() { return roamers.size(); }

    private static Location home(Roamer r, World w) { return new Location(w, r.x, r.y, r.z); }

    void endAll() { for (Roamer r : roamers) if (r.w != null) { r.w.remove(); r.w = null; } }

    /** A spot to stand within the radius: solid underfoot, two blocks of air, near the home's height. */
    private Location spot(Roamer r, World w) {
        var rnd = ThreadLocalRandom.current();
        for (int tries = 0; tries < 8; tries++) {
            double a = rnd.nextDouble(Math.PI * 2), d = rnd.nextDouble(1.5, r.radius);
            int bx = (int) Math.floor(r.x + Math.cos(a) * d), bz = (int) Math.floor(r.z + Math.sin(a) * d);
            for (int by = (int) Math.floor(r.y) + 2; by >= (int) Math.floor(r.y) - 3; by--) {
                Block feet = w.getBlockAt(bx, by, bz);
                if (feet.isPassable() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid())
                    return new Location(w, bx + 0.5, by, bz + 0.5);
            }
        }
        return null;
    }

    void tick() {
        for (Roamer r : roamers) {
            World w = Bukkit.getWorld(r.world);
            r.ticks++;
            if (r.ticks % 20 == 1 || w == null) {
                boolean near = false;
                if (w != null && w.isChunkLoaded(((int) Math.floor(r.x)) >> 4, ((int) Math.floor(r.z)) >> 4))
                    for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(home(r, w)) < 48 * 48) { near = true; break; }
                if (!near) { if (r.w != null) { r.w.remove(); r.w = null; } continue; }
                if (r.w == null || !r.w.valid()) {
                    if (r.w != null) r.w.remove();
                    r.w = Walker.spawn(plugin, home(r, w), r.role, Component.text(r.name != null ? r.name : r.role.name, GREEN), plugin.getConfig().getDouble("roamers.speed", 0.2));
                    r.wait = 40;
                }
            }
            if (r.w == null || !r.w.valid() || w == null) continue;
            Location at = r.w.at();
            if (at.getWorld() != w || at.distanceSquared(home(r, w)) > (r.radius + 8.0) * (r.radius + 8.0)) { r.w.teleport(home(r, w)); r.wait = 40; continue; }
            // someone close? stop and look at them
            Player close = null; double bd = 3.5 * 3.5;
            for (Player p : w.getPlayers()) { double d = p.getLocation().distanceSquared(at); if (d < bd) { bd = d; close = p; } }
            if (close != null) {
                if (r.ticks % 10 == 0) r.w.stop();
                r.w.tick(close.getLocation());
                r.wait = Math.max(r.wait, 30);
                continue;
            }
            r.w.tick(null);
            if (--r.wait > 0) continue;
            Location to = spot(r, w);
            if (to != null) r.w.walkTo(to, 1.0);
            r.wait = ThreadLocalRandom.current().nextInt(100, 280);
        }
    }

    @EventHandler
    public void onTalk(PlayerInteractEntityEvent e) {
        for (Roamer r : roamers) {
            if (r.w == null || !r.w.owns(e.getRightClicked())) continue;
            e.setCancelled(true);
            Player p = e.getPlayer();
            long now = System.currentTimeMillis();
            if (now - cooldown.getOrDefault(p.getUniqueId(), 0L) < 800) return;
            cooldown.put(p.getUniqueId(), now);
            String key = switch (r.role) { case TELLER -> "teller"; case RECEPTIONIST -> "receptionist"; case DIRECTOR -> "director"; case SECURITY -> "security"; case TAXMAN -> "taxman"; case VOTES -> "votes"; default -> "clerk"; };
            if (!plugin.getConfig().getStringList("dialogue.roaming").isEmpty() && ThreadLocalRandom.current().nextBoolean()) key = "roaming";
            plugin.lizards().say(p, e.getRightClicked(), r.role, key);
            return;
        }
    }

    void load(List<String> list) {
        endAll();
        roamers.clear();
        for (String s : list) {
            String[] p = s.split(",", 7);
            if (p.length < 6) continue;
            try {
                Roamer r = new Roamer();
                r.world = p[0]; r.x = Double.parseDouble(p[1]); r.y = Double.parseDouble(p[2]); r.z = Double.parseDouble(p[3]);
                r.radius = Integer.parseInt(p[4]); r.role = Lizards.Role.valueOf(p[5]);
                r.name = p.length > 6 && !p[6].isBlank() ? p[6] : null;
                roamers.add(r);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    List<String> save() {
        List<String> out = new ArrayList<>();
        for (Roamer r : roamers) out.add(r.key());
        return out;
    }
}
