package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The lift between the bank at spawn (top) and the Bureau (bottom). Crouch on either lift spot to ride it: a ding,
 * a moment of darkness, and you step out at the other end.
 */
final class Lift implements Listener {
    private static final TextColor GOLD = TextColor.color(0xF0C85A);
    private final Bureau plugin;
    Location top, bottom;
    private final Map<UUID, Long> riding = new HashMap<>();

    Lift(Bureau plugin) { this.plugin = plugin; }

    void load(ConfigurationSection d) {
        top = d == null ? null : d.getLocation("top");
        bottom = d == null ? null : d.getLocation("bottom");
    }

    void save(ConfigurationSection d) { d.set("top", top); d.set("bottom", bottom); }

    private static boolean near(Location a, Location b) {
        return a != null && b != null && a.getWorld() == b.getWorld() && a.distanceSquared(b) <= 1.5 * 1.5;
    }

    @EventHandler(ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Player p = e.getPlayer();
        Location to;
        boolean down;
        if (near(p.getLocation(), top)) { to = bottom; down = true; }
        else if (near(p.getLocation(), bottom)) { to = top; down = false; }
        else return;
        if (to == null) return;
        if (down && plugin.security().fired(p.getUniqueId())) {
            long h = plugin.security().firedLeft(p.getUniqueId()) / 3_600_000L + 1;
            p.sendActionBar(Component.text("Security won't let you down. You were fired. Come back in about " + h + "h.", TextColor.color(0xE05050)));
            return;
        }
        ride(p, to, down);
    }

    /** The ride itself: a ding, a moment of darkness, and they step out at dest. */
    void ride(Player p, Location to, boolean down) {
        long now = System.currentTimeMillis();
        if (now - riding.getOrDefault(p.getUniqueId(), 0L) < 4000) return;
        riding.put(p.getUniqueId(), now);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.2f);
        p.showTitle(Title.title(Component.empty(), Component.text(down ? "Going down... the Bureau" : "Going up... the bank", GOLD),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1200), Duration.ofMillis(400))));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
        Location dest = to.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            p.teleport(dest);
            p.playSound(dest, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
            p.sendActionBar(Component.text(down ? "Welcome to the Bureau of Cold-Blooded Affairs." : "Mind the gap.", NamedTextColor.GRAY));
        }, 30L);
    }
}
