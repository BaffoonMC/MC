package net.soulbound;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The inhuman's hearts are the sickly green ones Poison shows.
 *
 * They carry a hidden, endless Poison (no particles, no icon) purely for the look: its damage is cancelled, so their
 * hearts don't flicker and they lose nothing. Only they see it. Milk and totems leave it alone, it's back the moment
 * they respawn, and a check every second puts it back if anything else clears it. Cured, it's gone at once.
 *
 * Real Poison (spiders, witches, potions, pufferfish) still hurts them for as long as it lasts.
 */
final class InhumanPoison implements Listener {
    private final Soulbound plugin;
    /** When a real Poison (not ours) runs out, per player. */
    private final Map<UUID, Long> realUntil = new HashMap<>();
    private boolean applying;

    InhumanPoison(Soulbound plugin) { this.plugin = plugin; }

    private boolean on() { return plugin.getConfig().getBoolean("inhuman.poison-hearts", true); }

    private boolean inhuman(Player p) { return plugin.inhuman() != null && plugin.inhuman().is(p); }

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (on() && inhuman(p)) apply(p);
                else strip(p);
            }
        }, 40L, 20L);
    }

    private static boolean ours(PotionEffect e) {
        return e != null && e.getType().equals(PotionEffectType.POISON) && e.isInfinite() && !e.hasParticles() && !e.hasIcon();
    }

    private boolean realActive(Player p) {
        Long t = realUntil.get(p.getUniqueId());
        if (t == null) return false;
        if (t < System.currentTimeMillis()) { realUntil.remove(p.getUniqueId()); return false; }
        return true;
    }

    /** Give them the hidden Poison, unless they already have Poison of some kind. */
    void apply(Player p) {
        if (!on() || p.isDead() || p.hasPotionEffect(PotionEffectType.POISON)) return;
        applying = true;
        try { p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, PotionEffect.INFINITE_DURATION, 0, true, false, false)); }
        finally { applying = false; }
    }

    /** Take the hidden Poison away (cured, or the feature turned off). Real Poison is left alone. */
    void strip(Player p) {
        if (!ours(p.getPotionEffect(PotionEffectType.POISON))) return;
        applying = true;
        try { p.removePotionEffect(PotionEffectType.POISON); }
        finally { applying = false; }
    }

    /** Called when someone turns inhuman or is cured. */
    void changed(Player p, boolean inhuman) {
        if (inhuman) apply(p); else strip(p);
    }

    // ---------------------------------------------------------------- keeping it harmless

    /** The hidden Poison never hurts; real Poison still does while it lasts. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPoison(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.POISON || !(e.getEntity() instanceof Player p)) return;
        if (realActive(p)) return;
        if (ours(p.getPotionEffect(PotionEffectType.POISON))) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (applying || !(e.getEntity() instanceof Player p) || !PotionEffectType.POISON.equals(e.getModifiedType())) return;
        if (!on() || !inhuman(p)) return;
        var action = e.getAction();
        var cause = e.getCause();
        // real Poison arriving: let it hurt them for as long as it would have
        if ((action == EntityPotionEffectEvent.Action.ADDED || action == EntityPotionEffectEvent.Action.CHANGED)
                && cause != EntityPotionEffectEvent.Cause.PLUGIN && e.getNewEffect() != null && !e.getNewEffect().isInfinite()) {
            realUntil.put(p.getUniqueId(), System.currentTimeMillis() + e.getNewEffect().getDuration() * 50L);
            return;
        }
        // milk and totems don't wash the green out
        if ((action == EntityPotionEffectEvent.Action.CLEARED || action == EntityPotionEffectEvent.Action.REMOVED)
                && (cause == EntityPotionEffectEvent.Cause.MILK || cause == EntityPotionEffectEvent.Cause.TOTEM)) {
            if (ours(e.getOldEffect())) { realUntil.remove(p.getUniqueId()); e.setCancelled(true); return; }   // the real poison under it is cured
            // a real Poison is being washed off: put ours back straight after
            realUntil.remove(p.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline() && inhuman(p)) apply(p); });
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        realUntil.remove(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline() && inhuman(p)) apply(p); });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (on() && inhuman(p)) apply(p); else strip(p);
        });
    }
}
