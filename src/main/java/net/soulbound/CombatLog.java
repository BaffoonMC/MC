package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Combat logging. Hitting a player, or being hit by one, puts both of you "in combat" for a few seconds
 * (combat-log.seconds). While in combat:
 *   - escape commands (/spawn, /home, /tpa, /ec, /market...) don't work (combat-log.blocked-commands)
 *   - logging out kills you where you stand, and everything drops, as if the other player had killed you
 * Server restarts and kicks don't count. Captives are left to the capture rules, the sacrificed to the fire.
 */
final class CombatLog implements Listener {
    private final Soulbound plugin;
    private final Map<UUID, Long> until = new HashMap<>();
    private final Map<UUID, UUID> foe = new HashMap<>();
    private final Set<UUID> loggingOut = new HashSet<>();
    private BukkitTask ticker;

    CombatLog(Soulbound plugin) { this.plugin = plugin; }

    private boolean enabled() { return plugin.getConfig().getBoolean("combat-log.enabled", true); }
    private int seconds() { return Math.max(1, plugin.getConfig().getInt("combat-log.seconds", 15)); }

    void start() {
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            var it = until.entrySet().iterator();
            while (it.hasNext()) {
                var en = it.next();
                Player p = Bukkit.getPlayer(en.getKey());
                if (en.getValue() <= now) {
                    it.remove(); foe.remove(en.getKey());
                    if (p != null) p.sendActionBar(Component.text("You're out of combat.", NamedTextColor.GREEN));
                } else if (p != null) {
                    long left = (en.getValue() - now + 999) / 1000;
                    p.sendActionBar(Component.text("In combat: " + left + "s", NamedTextColor.RED)
                            .append(Component.text("  -  logging out now drops everything", NamedTextColor.GRAY)));
                }
            }
        }, 20L, 20L);
    }

    void stop() { if (ticker != null) ticker.cancel(); }

    boolean inCombat(Player p) {
        Long t = until.get(p.getUniqueId());
        return t != null && t > System.currentTimeMillis();
    }

    /** True while a combat logger's death is being handled: DeathDrops drops everything even with no killer. */
    boolean isLoggingOut(Player p) { return loggingOut.contains(p.getUniqueId()); }

    /** The player behind a hit: the archer, the TNT lighter, the potion thrower, the wolf's owner... */
    private static Player attacker(EntityDamageByEntityEvent e) {
        if (e.getDamageSource().getCausingEntity() instanceof Player p) return p;
        Entity src = e.getDamager();
        if (src instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (src instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        if (src instanceof AreaEffectCloud c && c.getSource() instanceof Player p) return p;
        if (src instanceof Tameable t && t.isTamed()) { AnimalTamer o = t.getOwner(); if (o instanceof Player p) return p; }
        return src instanceof Player p ? p : null;
    }

    private static boolean counts(Player p) { return p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE; }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!enabled() || !(e.getEntity() instanceof Player victim)) return;
        Player a = attacker(e);
        if (a == null || a.equals(victim) || !counts(a) || !counts(victim)) return;
        long end = System.currentTimeMillis() + seconds() * 1000L;
        for (Player p : List.of(a, victim)) {
            boolean fresh = !inCombat(p);
            until.put(p.getUniqueId(), end);
            foe.put(p.getUniqueId(), (p == a ? victim : a).getUniqueId());
            if (fresh) p.sendMessage(Component.text("You're in combat. Don't log out for " + seconds() + " seconds, or everything you're carrying drops.", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!enabled() || !inCombat(p) || p.hasPermission("soulbound.combatlog.bypass")) return;
        String cmd = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = cmd.indexOf(':');
        if (colon >= 0) cmd = cmd.substring(colon + 1);
        List<String> blocked = plugin.getConfig().getStringList("combat-log.blocked-commands");
        if (blocked.isEmpty()) blocked = DEFAULT_BLOCKED;
        if (!blocked.contains(cmd)) return;
        e.setCancelled(true);
        long left = (until.get(p.getUniqueId()) - System.currentTimeMillis() + 999) / 1000;
        p.sendMessage(Component.text("Not in the middle of a fight. " + left + "s to go.", NamedTextColor.RED));
    }

    static final List<String> DEFAULT_BLOCKED = List.of(
            "spawn", "home", "homes", "back", "warp", "warps", "tpa", "tpahere", "tpaccept", "tpyes", "call", "tpask",
            "rtp", "wild", "randomtp", "tpr", "ec", "echest", "enderchest", "fly", "god", "creative", "hub", "lobby",
            "market", "ah", "auction", "marketplace", "shop", "servershop", "sellshop", "voteshop", "trash", "disposal");

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        boolean was = inCombat(p);
        UUID other = foe.remove(id);
        until.remove(id);
        if (!enabled() || !was || p.isDead() || Bukkit.isStopping() || !counts(p)) return;
        if (p.hasPermission("soulbound.combatlog.bypass")) return;
        PlayerQuitEvent.QuitReason why = e.getReason();
        if (why == PlayerQuitEvent.QuitReason.KICKED) return;   // restarts, staff kicks
        if (why == PlayerQuitEvent.QuitReason.TIMED_OUT && !plugin.getConfig().getBoolean("combat-log.punish-timeouts", true)) return;
        if (plugin.captures() != null && plugin.captures().isCaptive(p)) return;          // the capture rules deal with it
        if (plugin.sacrifices() != null && plugin.sacrifices().beingSacrificed(id)) return;
        Player killer = other == null ? null : Bukkit.getPlayer(other);
        loggingOut.add(id);
        try {
            if (killer != null) p.setKiller(killer);
            p.setHealth(0.0);
        } finally {
            loggingOut.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        UUID id = e.getEntity().getUniqueId();
        until.remove(id); foe.remove(id);
    }
}
