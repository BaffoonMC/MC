package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * /rtp (also /wild): a random safe spot in the overworld, the same place the Wild Drop hole sends you.
 * Stand still for a few seconds first; not while fighting, captured or dragging a captive, and not from the
 * Nether/Moloch's realm, the End/Heaven or the creative world. Cooldown between uses.
 */
final class Rtp implements Listener, CommandExecutor {
    private final Soulbound plugin;
    private final Map<UUID, Long> lastUsed = new HashMap<>();
    private final Map<UUID, Long> lastPvp = new HashMap<>();
    private final Map<UUID, BukkitTask> waiting = new HashMap<>();

    Rtp(Soulbound plugin) { this.plugin = plugin; }

    private int cfg(String k, int d) { return plugin.getConfig().getInt("rtp." + k, d); }

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!plugin.getConfig().getBoolean("rtp.enabled", true)) { p.sendMessage(Component.text("Random teleport is off.", NamedTextColor.GRAY)); return true; }
        if (plugin.wildDrop() == null) return true;
        String no = why(p);
        if (no != null) { p.sendMessage(Component.text(no, NamedTextColor.RED)); return true; }
        if (waiting.containsKey(p.getUniqueId())) { p.sendActionBar(Component.text("Already on your way. Stand still.", NamedTextColor.GRAY)); return true; }
        int secs = Math.max(0, cfg("warmup-seconds", 3));
        if (secs == 0 || p.hasPermission("soulbound.admin")) { go(p); return true; }
        Location start = p.getLocation().clone();
        int[] left = {secs * 20};
        p.sendActionBar(Component.text("Off to the wild in " + secs + "... stand still.", NamedTextColor.GREEN));
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { cancel(p, null); return; }
            Location now = p.getLocation();
            if (!now.getWorld().equals(start.getWorld()) || now.distanceSquared(start) > 0.6 * 0.6) { cancel(p, "You moved, so you're staying put."); return; }
            left[0] -= 2;
            if (left[0] % 20 == 0 && left[0] > 0) p.sendActionBar(Component.text("Off to the wild in " + (left[0] / 20) + "... stand still.", NamedTextColor.GREEN));
            p.getWorld().spawnParticle(Particle.PORTAL, now.clone().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.3);
            if (left[0] <= 0) { cancel(p, null); String late = why(p); if (late != null) p.sendMessage(Component.text(late, NamedTextColor.RED)); else go(p); }
        }, 2L, 2L);
        waiting.put(p.getUniqueId(), t);
        return true;
    }

    /** Why they can't go right now, or null if they can. */
    private String why(Player p) {
        if (p.hasPermission("soulbound.admin")) return null;
        World w = p.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL || creative(w)) return "You can only do that from the overworld or spawn.";
        if (plugin.captures() != null && plugin.captures().isCaptive(p)) return "You can't get away that easily.";
        if (plugin.captures() != null && plugin.captures().isCaptor(p)) return "Not while you're dragging someone.";
        long now = System.currentTimeMillis();
        long pvp = lastPvp.getOrDefault(p.getUniqueId(), 0L);
        int fight = cfg("combat-seconds", 30);
        if (now - pvp < fight * 1000L) return "You're in a fight. Wait " + ((fight * 1000L - (now - pvp)) / 1000 + 1) + "s.";
        long used = lastUsed.getOrDefault(p.getUniqueId(), 0L);
        int cd = cfg("cooldown-minutes", 10);
        if (now - used < cd * 60_000L) {
            long s = (cd * 60_000L - (now - used)) / 1000 + 1;
            return "You can do that again in " + (s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s") + ".";
        }
        return null;
    }

    private boolean creative(World w) {
        List<String> names = plugin.getConfig().getStringList("creative-world.worlds");
        for (String n : names.isEmpty() ? List.of("creative") : names) if (w.getName().equalsIgnoreCase(n)) return true;
        return false;
    }

    private void go(Player p) {
        lastUsed.put(p.getUniqueId(), System.currentTimeMillis());
        p.setFallDistance(0);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
        p.sendActionBar(Component.text("Off you go...", NamedTextColor.GREEN));
        plugin.wildDrop().sendToWild(p);
    }

    private void cancel(Player p, String msg) {
        BukkitTask t = waiting.remove(p.getUniqueId());
        if (t != null) t.cancel();
        if (msg != null && p.isOnline()) p.sendActionBar(Component.text(msg, NamedTextColor.RED));
    }

    /** Getting hurt while waiting stops it; any PvP starts the fight timer. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && waiting.containsKey(p.getUniqueId())) cancel(p, "You got hurt, so you're staying put.");
        if (!(e instanceof EntityDamageByEntityEvent d)) return;
        Entity src = d.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Entity sh) src = sh;
        if (src instanceof Player a && e.getEntity() instanceof Player v && !a.equals(v)) {
            long now = System.currentTimeMillis();
            lastPvp.put(a.getUniqueId(), now); lastPvp.put(v.getUniqueId(), now);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { cancel(e.getPlayer(), null); }
}
