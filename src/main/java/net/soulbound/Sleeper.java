package net.soulbound;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Sleeper: a homeless man asleep at spawn. Poke him (click or hit) and he gets grumpier each time; the fourth time
 * he gets up, shoves you away (no damage), grumbles, and lies back down. He forgets you after half a minute.
 * /sb world sleeper: look at a mannequin to make it him, or stand where he should lie. /sb world sleeper remove.
 */
final class Sleeper implements Listener {
    private static final TextColor SPEECH = TextColor.color(0xE8DCC8);
    private static final List<String> ASLEEP = List.of("Zzz... five more minutes...", "Mmh... not now, mum...", "Zzz... no... not the cudgel...", "*snores loudly*");
    private static final List<String> WAKING = List.of("Oi. I'm sleeping.", "Do I look like a shop? Move along.", "This is my spot. Get your own.", "I'm not homeless, I'm free-range.");
    private static final List<String> FED_UP = List.of("I SAID I'M SLEEPING.", "Spare a diamond? No? Then piss off.", "Touch me again and I'll bite you.", "Go jump in the hole and leave me alone.");
    private static final List<String> JOKES = List.of("I was a Chosen once. Then I spent it all on hats.", "Rent's free here. Unlike your claim blocks.",
            "Shh. I'm dreaming about God. He's disappointed in me too.", "Vote for the server, they said. It'll be fun, they said.", "Got any raw meat? ...No? Bugger off then.");
    private static final List<String> SHOVE = List.of("RIGHT. FUCK OFF.", "I've got nothing. Moloch took the lot. FUCK. OFF.", "THAT'S IT.");
    private static final List<String> AFTER = List.of("...bloody tourists.", "...every single day.", "...and stay off.");
    private static final List<String> STILL_ANGRY = List.of("Oh, piss off.", "Don't even think about it.", "*glares*");

    private record Poke(int stage, long last, long shovedAt) {}

    private final Soulbound plugin;
    private final NamespacedKey key;
    private final Map<UUID, Map<UUID, Poke>> pokes = new HashMap<>();   // sleeper -> player -> how annoyed
    private final Map<UUID, TextDisplay> bubbles = new HashMap<>();
    private final Set<UUID> up = new HashSet<>();                       // standing up to shove someone
    private int tick;
    private boolean placing;

    private final NamespacedKey hitboxKey, hitboxOf;

    Sleeper(Soulbound plugin) {
        this.plugin = plugin; this.key = new NamespacedKey(plugin, "sleeper");
        this.hitboxKey = new NamespacedKey(plugin, "sleeper_hitbox"); this.hitboxOf = new NamespacedKey(plugin, "sleeper_of");
    }

    void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::snore, 100L, 20L); }

    boolean is(Entity e) { return e instanceof Mannequin && e.getPersistentDataContainer().has(key, PersistentDataType.BYTE); }

    /** The Sleeper behind a click: him, or the invisible hitbox lying over him (asleep, his own hitbox shrinks to a speck). */
    private Mannequin of(Entity e) {
        if (is(e)) return (Mannequin) e;
        if (!(e instanceof org.bukkit.entity.Interaction)) return null;
        String s = e.getPersistentDataContainer().get(hitboxOf, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Bukkit.getEntity(UUID.fromString(s)) instanceof Mannequin m && is(m) ? m : null; } catch (IllegalArgumentException x) { return null; }
    }

    /** Make sure he has a hitbox over his body you can click. */
    private void ensureHitbox(Mannequin m) {
        String s = m.getPersistentDataContainer().get(hitboxKey, PersistentDataType.STRING);
        if (s != null) try { if (Bukkit.getEntity(UUID.fromString(s)) instanceof org.bukkit.entity.Interaction i && i.isValid()) return; } catch (IllegalArgumentException ignored) {}
        Location at = m.getLocation().clone(); at.setPitch(0);
        placing = true;
        try {
            org.bukkit.entity.Interaction box = m.getWorld().spawn(at.add(0, -0.05, 0), org.bukkit.entity.Interaction.class, i -> {
                i.setInteractionWidth(2.6f);
                i.setInteractionHeight(0.75f);
                i.setResponsive(true);
                i.setPersistent(true);
                i.getPersistentDataContainer().set(hitboxOf, PersistentDataType.STRING, m.getUniqueId().toString());
            });
            if (box.isValid()) m.getPersistentDataContainer().set(hitboxKey, PersistentDataType.STRING, box.getUniqueId().toString());
        } finally { placing = false; }
    }

    private void removeHitbox(Mannequin m) {
        String s = m.getPersistentDataContainer().get(hitboxKey, PersistentDataType.STRING);
        if (s != null) try { Entity i = Bukkit.getEntity(UUID.fromString(s)); if (i != null) i.remove(); } catch (IllegalArgumentException ignored) {}
    }

    // ---------------------------------------------------------------- setting him up

    String set(Player p) {
        Entity target = p.getTargetEntity(6);
        if (of(target) != null) target = of(target);
        Mannequin m;
        if (target instanceof Mannequin mq) {
            m = mq;
            if (is(m)) { m.setProfile(profile()); m.setPose(Pose.SLEEPING, true); ensureHitbox(m); return "He's already the Sleeper (skin refreshed)."; }
        } else {
            Location l = p.getLocation();
            l.setPitch(0);
            placing = true;   // spawn's no-mob-spawning rule doesn't apply to him: an admin is putting him there on purpose
            try {
                m = l.getWorld().spawn(l, Mannequin.class, x -> {
                    x.setProfile(profile());
                    x.setPersistent(true);
                    x.setRemoveWhenFarAway(false);
                    x.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                });
            } finally { placing = false; }
            if (!m.isValid()) return "Something stopped him appearing here (another plugin cancelled it).";
        }
        m.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        m.setProfile(profile());
        m.setInvulnerable(true);
        m.setImmovable(true);
        m.setDescription(null);
        m.setPose(Pose.SLEEPING, true);
        ensureHitbox(m);
        return target instanceof Mannequin ? "He lies down. Poke him and see what happens." : "A Sleeper lies where you stood.";
    }

    /** His skin: the pack's soulbound:entity/sleeper, or a player's skin if sleeper.skin names one. */
    private ResolvableProfile profile() {
        String skin = plugin.getConfig().getString("sleeper.skin", "");
        if (skin != null && !skin.isBlank()) return ResolvableProfile.resolvableProfile().name(skin).build();
        return ResolvableProfile.resolvableProfile()
                .skinPatch(s -> s.body(new NamespacedKey("soulbound", "entity/sleeper")).model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC))
                .build();
    }

    String removeNear(Player p) {
        Entity best = null; double bd = 8 * 8;
        for (Mannequin m : p.getWorld().getEntitiesByClass(Mannequin.class)) {
            if (!is(m)) continue;
            double d = m.getLocation().distanceSquared(p.getLocation());
            if (d < bd) { bd = d; best = m; }
        }
        if (best == null) return "No Sleeper within 8 blocks.";
        clearBubble(best.getUniqueId());
        removeHitbox((Mannequin) best);
        best.remove();
        return "The Sleeper is gone.";
    }

    /** Let him appear even where mobs can't spawn (WorldGuard's mob-spawning deny at spawn). Runs last, only for him. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(org.bukkit.event.entity.EntitySpawnEvent e) {
        if (placing && e.isCancelled() && (e.getEntity() instanceof Mannequin || e.getEntity() instanceof org.bukkit.entity.Interaction)) e.setCancelled(false);
    }

    // ---------------------------------------------------------------- poking

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEntityEvent e) {
        Mannequin m = of(e.getRightClicked());
        if (m == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        poke(m, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageEvent e) {
        if (!is(e.getEntity())) return;
        e.setCancelled(true);
        if (e instanceof EntityDamageByEntityEvent d && d.getDamager() instanceof Player p) poke((Mannequin) e.getEntity(), p);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        Mannequin m = of(e.getAttacked());
        if (m == null) return;
        e.setCancelled(true);
        poke(m, e.getPlayer());
    }

    private void poke(Mannequin m, Player p) {
        if (up.contains(m.getUniqueId())) return;
        long now = System.currentTimeMillis();
        Map<UUID, Poke> mine = pokes.computeIfAbsent(m.getUniqueId(), k -> new HashMap<>());
        Poke was = mine.get(p.getUniqueId());
        if (was != null && now - was.last() < 700) return;   // one poke at a time
        long forget = plugin.getConfig().getLong("sleeper.forget-seconds", 30) * 1000;
        long shoveGap = plugin.getConfig().getLong("sleeper.shove-cooldown-seconds", 120) * 1000;
        int stage = was == null || now - was.last() > forget ? 1 : was.stage() + 1;
        long shovedAt = was == null ? 0 : was.shovedAt();
        mine.put(p.getUniqueId(), new Poke(stage, now, shovedAt));
        ThreadLocalRandom r = ThreadLocalRandom.current();

        if (stage >= 4) {
            if (now - shovedAt < shoveGap) { say(m, pick(STILL_ANGRY), Sound.ENTITY_VILLAGER_NO, 0.6f); return; }
            mine.put(p.getUniqueId(), new Poke(0, now, now));
            shove(m, p);
            return;
        }
        switch (stage) {
            case 1 -> say(m, pick(ASLEEP), Sound.ENTITY_FOX_SLEEP, 0.5f);
            case 2 -> say(m, r.nextInt(4) == 0 ? pick(JOKES) : pick(WAKING), Sound.ENTITY_VILLAGER_AMBIENT, 0.6f);
            default -> say(m, r.nextInt(4) == 0 ? pick(JOKES) : pick(FED_UP), Sound.ENTITY_VILLAGER_NO, 0.6f);
        }
    }

    /** He gets up, shoves them away (no damage), grumbles, and lies back down. */
    private void shove(Mannequin m, Player p) {
        UUID id = m.getUniqueId();
        up.add(id);
        float sleepYaw = m.getLocation().getYaw(), sleepBody = m.getBodyYaw();
        m.setPose(Pose.STANDING, true);
        Vector to = p.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
        if (to.lengthSquared() < 0.01) to = p.getLocation().getDirection().multiply(-1).setY(0);
        float face = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
        m.setRotation(face, 0); m.setBodyYaw(face);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid()) { up.remove(id); return; }
            m.swingMainHand();
            say(m, pick(SHOVE), Sound.ENTITY_VILLAGER_NO, 0.5f);
            if (p.isOnline() && p.getWorld().equals(m.getWorld()) && p.getLocation().distanceSquared(m.getLocation()) < 36) {
                Vector away = p.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
                if (away.lengthSquared() < 0.01) away = new Vector(0, 0, 1);
                double push = plugin.getConfig().getDouble("sleeper.shove-strength", 1.6);
                p.setVelocity(away.normalize().multiply(push).setY(0.45));
                p.playHurtAnimation(0);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 1);
            }
        }, 6L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.isValid()) say(m, pick(AFTER), Sound.ENTITY_VILLAGER_AMBIENT, 0.5f); }, 50L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            up.remove(id);
            if (!m.isValid()) return;
            m.setRotation(sleepYaw, 0); m.setBodyYaw(sleepBody);
            m.setPose(Pose.SLEEPING, true);
            m.getWorld().playSound(m.getLocation(), Sound.ENTITY_FOX_SLEEP, 0.8f, 0.5f);
        }, 90L);
    }

    // ---------------------------------------------------------------- talking and snoring

    private static String pick(List<String> l) { return l.get(ThreadLocalRandom.current().nextInt(l.size())); }

    private void say(Mannequin m, String line, Sound sound, float pitch) {
        clearBubble(m.getUniqueId());
        boolean lying = !up.contains(m.getUniqueId());
        Location at = m.getLocation().add(0, lying ? 0.9 : 2.25, 0);
        TextDisplay d = m.getWorld().spawn(at, TextDisplay.class, t -> {
            t.text(Component.text(line, SPEECH));
            t.setBillboard(Display.Billboard.CENTER);
            t.setPersistent(false);
            t.setSeeThrough(false);
            t.setShadowed(true);
            t.setLineWidth(160);
        });
        bubbles.put(m.getUniqueId(), d);
        m.getWorld().playSound(m.getLocation(), sound, 1f, pitch);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (bubbles.get(m.getUniqueId()) == d) clearBubble(m.getUniqueId()); }, 60L);
    }

    private void clearBubble(UUID id) {
        TextDisplay old = bubbles.remove(id);
        if (old != null && old.isValid()) old.remove();
    }

    /** A snore now and then when someone's near. */
    private void snore() {
        if (++tick % 6 != 0) return;
        for (World w : Bukkit.getWorlds()) {
            if (w.getPlayers().isEmpty()) continue;
            for (Mannequin m : w.getEntitiesByClass(Mannequin.class)) {
                if (!is(m)) continue;
                ensureHitbox(m);
                if (up.contains(m.getUniqueId())) continue;
                if (m.getLocation().getNearbyPlayers(10).isEmpty()) continue;
                w.playSound(m.getLocation(), Sound.ENTITY_FOX_SLEEP, 0.7f, 0.55f);
                w.spawnParticle(Particle.WHITE_ASH, m.getLocation().add(0, 0.6, 0), 4, 0.2, 0.1, 0.2, 0);
            }
        }
        long now = System.currentTimeMillis();
        pokes.values().forEach(mm -> mm.values().removeIf(pk -> now - pk.last() > 300_000));
    }
}
