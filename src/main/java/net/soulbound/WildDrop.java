package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Wild Drop: a spot at spawn (the bottom of a hole, a pool, a portal frame...) where anyone who steps or falls in
 * is sent somewhere random with /tpr. Set it by standing in it: /sb world wilddrop [radius], /sb world wilddrop off.
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
            p.performCommand(plugin.getConfig().getString("wild-drop-command", "tpr"));
        }
        if (sent.size() > 200) sent.values().removeIf(t -> now - t > 60_000);
    }
}
