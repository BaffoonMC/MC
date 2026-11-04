package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deeper Still: what you become when you feed your own homunculus to Moloch. It wakes him at once. You're marked for
 * good: Speed II at night in the Overworld, two more hearts in Moloch's realm, and the Zombie Bell (it raises three
 * zombies to fight for you, then needs time). But the Angel Engine won't take you any more, you can never raise
 * another homunculus, and for a few days it still talks to you.
 */
final class DeeperStill implements Listener {
    private final Soulbound plugin;
    private final NamespacedKey markKey, whenKey, bellKey, bellUsedKey, hpKey, minionKey;
    private static final TextColor ROT = TextColor.color(0x7FA35A);

    DeeperStill(Soulbound plugin) {
        this.plugin = plugin;
        markKey = new NamespacedKey(plugin, "deeper_still");
        whenKey = new NamespacedKey(plugin, "deeper_still_at");
        bellKey = new NamespacedKey(plugin, "zombie_bell");
        bellUsedKey = new NamespacedKey(plugin, "zombie_bell_used");
        hpKey = new NamespacedKey(plugin, "deeper_still_hearts");
        minionKey = new NamespacedKey(plugin, "bell_zombie_of");
    }

    private final java.util.Set<UUID> raised = new java.util.HashSet<>();

    void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::watchRaised, 20L, 10L);
    }

    /** The dead won't walk into Moloch's temple while he's awake: they crumble at the edge of it. */
    private void watchRaised() {
        if (raised.isEmpty()) return;
        boolean awake = plugin.boss() != null && plugin.boss().active();
        raised.removeIf(id -> {
            Entity z = Bukkit.getEntity(id);
            if (z == null || !z.isValid()) return true;
            if (z instanceof Mob m && (m.getTarget() == null || !m.getTarget().isValid() || m.getTarget().isDead())) {
                UUID o = minionOwner(z);
                Player owner = o == null ? null : Bukkit.getPlayer(o);
                if (owner != null && owner.getWorld().equals(z.getWorld())) {
                    double d2 = owner.getLocation().distanceSquared(z.getLocation());
                    if (d2 > 40 * 40) m.teleport(owner.getLocation());
                    else if (d2 > 5 * 5) m.getPathfinder().moveTo(owner, 1.2);
                }
            }
            if (awake && plugin.zones() != null && plugin.zones().inTemple(z.getLocation())) {
                z.getWorld().spawnParticle(Particle.LARGE_SMOKE, z.getLocation().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.02);
                z.getWorld().spawnParticle(Particle.FLAME, z.getLocation().add(0, 0.6, 0), 10, 0.3, 0.4, 0.3, 0.02);
                z.getWorld().playSound(z.getLocation(), Sound.ENTITY_ZOMBIE_DEATH, 1f, 0.6f);
                z.remove();
                return true;
            }
            return false;
        });
    }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("deeper-still." + k, d); }

    boolean is(Player p) { return p.getPersistentDataContainer().has(markKey, PersistentDataType.BYTE); }

    /** Marked for good: the light goes out, the bell is theirs, and Heaven's door shuts. */
    void mark(Player p) {
        p.getPersistentDataContainer().set(markKey, PersistentDataType.BYTE, (byte) 1);
        p.getPersistentDataContainer().set(whenKey, PersistentDataType.LONG, System.currentTimeMillis());
        if (plugin.grace() != null && plugin.grace().is(p)) {
            plugin.grace().set(p, false);
            p.sendMessage(Component.text("The light goes out of you.", NamedTextColor.GRAY, TextDecoration.ITALIC));
        }
        for (ItemStack left : p.getInventory().addItem(bell()).values()) p.getWorld().dropItem(p.getLocation(), left);
        org.bukkit.advancement.Advancement a = Bukkit.getAdvancement(new NamespacedKey("sacrifice", "moloch/deeper_still"));
        if (a != null) { var pr = p.getAdvancementProgress(a); for (String c : pr.getRemainingCriteria()) pr.awardCriteria(c); }
        else Bukkit.broadcast(Component.text(p.getName() + " has gone ", NamedTextColor.DARK_PURPLE).append(Component.text("[Deeper Still]", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD)));
        tick();
    }

    /** Admin: take the mark off (the extra hearts go at once; the bell stays silent for them). */
    void clear(Player p) {
        p.getPersistentDataContainer().remove(markKey);
        p.getPersistentDataContainer().remove(whenKey);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && hp.getModifier(hpKey) != null) { hp.removeModifier(hpKey); if (p.getHealth() > hp.getValue()) p.setHealth(hp.getValue()); }
    }

    // ---------------------------------------------------------------- the Zombie Bell

    ItemStack bell() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Zombie Bell", ROT, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Ring it and the dead get up for you.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Raises " + (int) cfg("bell-zombies", 4) + " axe-wielding dead for " + (int) cfg("bell-zombie-seconds", 120) + "s. Rests " + (int) (cfg("bell-cooldown-seconds", 300) / 60) + " min after.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("They fight for you. Their bite starves.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Only for the inhuman who went Deeper Still.", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(new NamespacedKey("soulbound", "zombie_bell"));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(bellKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isBell(ItemStack it) { return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(bellKey, PersistentDataType.BYTE); }

    private boolean safeZone(Location l) {
        if (plugin.zones() != null && plugin.zones().inTemple(l)) return true;
        if (plugin.island() != null && plugin.island().inZone(l)) return true;
        Location sp = l.getWorld().getSpawnLocation();
        double r = plugin.getConfig().getDouble("inhuman.sun-safe-spawn-radius", 64);
        return l.getWorld().getEnvironment() == World.Environment.NORMAL && r > 0 && Math.abs(l.getX() - sp.getX()) <= r && Math.abs(l.getZ() - sp.getZ()) <= r;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRing(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isBell(e.getItem())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!is(p)) { p.sendActionBar(Component.text("The bell is silent in your hand.", NamedTextColor.GRAY)); return; }
        if (plugin.inhuman() == null || !plugin.inhuman().is(p)) {
            p.sendActionBar(Component.text("The dead won't answer a living heart. Eat the Heart of Darkness.", NamedTextColor.DARK_RED)); return;
        }
        ItemMeta held = e.getItem().getItemMeta();
        if (!java.util.Objects.equals(held.lore(), bell().getItemMeta().lore())) { held.lore(bell().getItemMeta().lore()); e.getItem().setItemMeta(held); }
        if (safeZone(p.getLocation())) { p.sendActionBar(Component.text("The dead won't rise here.", NamedTextColor.GRAY)); return; }
        if (bossNear(p.getLocation())) { p.sendActionBar(Component.text("The dead won't rise against him.", NamedTextColor.GRAY)); return; }
        long now = System.currentTimeMillis(), last = p.getPersistentDataContainer().getOrDefault(bellUsedKey, PersistentDataType.LONG, 0L);
        long cd = (long) (cfg("bell-cooldown-seconds", 300) * 1000);
        if (now - last < cd) {
            long s = (cd - (now - last)) / 1000;
            p.sendActionBar(Component.text("The bell needs rest. " + (s >= 60 ? (s / 60) + "m " + (s % 60) + "s" : s + "s"), NamedTextColor.GRAY));
            return;
        }
        p.getPersistentDataContainer().set(bellUsedKey, PersistentDataType.LONG, now);
        // the bell tolls three times, loud enough for everyone around to hear
        for (int t = 0; t < 3; t++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 3f, 0.5f);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.5f, 0.6f);
            }, t * 14L);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 1.5f, 0.6f);
        int n = (int) cfg("bell-zombies", 4);
        int life = (int) cfg("bell-zombie-seconds", 120) * 20;
        var rnd = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + rnd.nextDouble() * 0.6;
            Location at = p.getLocation().add(Math.cos(a) * 2.5, 0, Math.sin(a) * 2.5);
            at.setY(at.getWorld().getHighestBlockYAt(at.getBlockX(), at.getBlockZ(), org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
            if (Math.abs(at.getY() - p.getY()) > 4) at = p.getLocation();
            Location spot = at;
            at.getWorld().spawnParticle(Particle.BLOCK, spot, 30, 0.3, 0.1, 0.3, Material.DIRT.createBlockData());
            Zombie z = at.getWorld().spawn(spot, Zombie.class, x -> {
                x.setAdult();
                x.setShouldBurnInDay(false);
                x.setPersistent(false);
                x.setCanPickupItems(false);
                x.getEquipment().clear();
                // bigger, faster, harder-hitting than the wandering dead, each with an axe
                x.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_AXE));
                x.getEquipment().setItemInMainHandDropChance(0f);
                attr(x, Attribute.SCALE, cfg("bell-zombie-scale", 1.15));
                attr(x, Attribute.MOVEMENT_SPEED, 0.23 * cfg("bell-zombie-speed", 1.3));
                attr(x, Attribute.ATTACK_DAMAGE, cfg("bell-zombie-damage", 5));
                attr(x, Attribute.MAX_HEALTH, cfg("bell-zombie-health", 30));
                x.setHealth(cfg("bell-zombie-health", 30));
                attr(x, Attribute.SPAWN_REINFORCEMENTS, 0);
                x.customName(Component.text(p.getName() + "'s dead", ROT));
                x.getPersistentDataContainer().set(minionKey, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            raised.add(z.getUniqueId());
            LivingEntity target = nearestFoe(p, 16);
            if (target != null) z.setTarget(target);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!z.isValid()) return;
                z.getWorld().spawnParticle(Particle.LARGE_SMOKE, z.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
                z.remove();
            }, life);
        }
        p.sendActionBar(Component.text("The dead get up for you.", ROT));
    }

    private static void attr(LivingEntity e, Attribute a, double v) {
        AttributeInstance i = e.getAttribute(a);
        if (i != null) i.setBaseValue(v);
    }

    private UUID minionOwner(Entity e) {
        String s = e.getPersistentDataContainer().get(minionKey, PersistentDataType.STRING);
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException x) { return null; }
    }

    /** Whoever's nearest that isn't the bell-ringer or one of their own. */
    private LivingEntity nearestFoe(Player owner, double r) {
        LivingEntity best = null; double bd = Double.MAX_VALUE;
        for (Entity e : owner.getNearbyEntities(r, r / 2, r)) {
            if (!(e instanceof LivingEntity le) || e.equals(owner) || minionOwner(e) != null) continue;
            if (!(e instanceof Player) && !(e instanceof org.bukkit.entity.Monster)) continue;
            if (bossSide(e)) continue;
            if (e instanceof Player q && (q.getGameMode() == org.bukkit.GameMode.SPECTATOR || q.getGameMode() == org.bukkit.GameMode.CREATIVE)) continue;
            if (plugin.homunculus() != null && plugin.homunculus().is(e)) continue;
            double d = e.getLocation().distanceSquared(owner.getLocation());
            if (d < bd) { bd = d; best = le; }
        }
        return best;
    }

    /** Moloch's fight (anywhere in his realm while he's awake) or God's (anywhere in Heaven while He fights). */
    private boolean bossNear(Location l) {
        if (plugin.boss() != null && plugin.boss().active() && plugin.zones() != null && plugin.zones().inRealm(l)) return true;
        return plugin.god() != null && plugin.god().fighting() && l.getWorld().getEnvironment() == World.Environment.THE_END;
    }

    /** Moloch, God, or anything fighting for either of them. */
    private boolean bossSide(Entity e) {
        if (e == null) return false;
        if (plugin.boss() != null && plugin.boss().isFoe(e)) return true;
        if (e instanceof org.bukkit.entity.EnderDragon || e instanceof org.bukkit.entity.Boss) return true;
        return plugin.god() != null && plugin.god().fighting() && e.getWorld().getEnvironment() == World.Environment.THE_END && !(e instanceof Player);
    }

    /** They never turn on the one who rang the bell, on each other, or on Moloch or God (or their own). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMinionTarget(EntityTargetEvent e) {
        UUID o = minionOwner(e.getEntity());
        if (o == null || e.getTarget() == null) return;
        if (e.getTarget().getUniqueId().equals(o) || minionOwner(e.getTarget()) != null || bossSide(e.getTarget())) e.setCancelled(true);
    }

    /** ...and if one somehow lands a blow on them, it does nothing. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMinionHitBoss(EntityDamageByEntityEvent e) {
        if (minionOwner(e.getDamager()) != null && bossSide(e.getEntity())) e.setCancelled(true);
    }

    /** Whatever their master hits, they go for. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMasterHits(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (!(d instanceof Player p) || !(e.getEntity() instanceof LivingEntity v) || minionOwner(v) != null || bossSide(v)) return;
        for (Entity x : p.getNearbyEntities(20, 10, 20))
            if (x instanceof Mob m && p.getUniqueId().equals(minionOwner(x))) m.setTarget(v);
    }

    /** Their bite starves; and anyone who hurts their master becomes their target. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBiteOrDefend(EntityDamageByEntityEvent e) {
        if (minionOwner(e.getDamager()) != null && e.getEntity() instanceof Player v)
            v.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, (int) (cfg("bell-bite-hunger-seconds", 10) * 20), 1));
        if (!(e.getEntity() instanceof Player owner)) return;
        Entity d = e.getDamager();
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (!(d instanceof LivingEntity att) || att.equals(owner) || minionOwner(att) != null || bossSide(att)) return;
        for (Entity x : owner.getNearbyEntities(20, 10, 20))
            if (x instanceof Mob m && owner.getUniqueId().equals(minionOwner(x))) m.setTarget(att);
    }

    @EventHandler(ignoreCancelled = true)
    public void onMinionBurn(EntityCombustEvent e) { if (minionOwner(e.getEntity()) != null) e.setCancelled(true); }

    @EventHandler
    public void onMinionDeath(EntityDeathEvent e) {
        if (minionOwner(e.getEntity()) == null) return;
        e.getDrops().clear(); e.setDroppedExp(0);
    }

    // ---------------------------------------------------------------- what it costs, and what it gives

    /** The Angel Engine won't take them. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAngelEngine(PlayerItemConsumeEvent e) {
        if (plugin.grace() == null || !plugin.grace().isHeart(e.getItem()) || !is(e.getPlayer())) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("The light won't take you. Not after what you did.", NamedTextColor.GRAY));
    }

    private static final List<String> ECHOES = List.of("...it's dark in here.", "Did I do something wrong?", "I'm still waiting.",
            "You said you'd come back.", "It's warm in here. Too warm.", "I still love you. Is that okay?");

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
            boolean marked = is(p);
            boolean inRealm = marked && plugin.zones() != null && plugin.zones().inRealm(p.getLocation());
            if (hp != null) {
                boolean has = hp.getModifier(hpKey) != null;
                if (inRealm && !has) hp.addModifier(new AttributeModifier(hpKey, cfg("realm-extra-health", 4), AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
                else if (!inRealm && has) { hp.removeModifier(hpKey); if (p.getHealth() > hp.getValue()) p.setHealth(hp.getValue()); }
            }
            if (!marked) continue;
            World w = p.getWorld();
            long t = w.getTime();
            if (w.getEnvironment() == World.Environment.NORMAL && t >= 13000 && t < 23000)
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, (int) cfg("night-speed-level", 2) - 1, true, false, true));
            // for a few days, it still talks to you
            long at = p.getPersistentDataContainer().getOrDefault(whenKey, PersistentDataType.LONG, 0L);
            if (now - at < cfg("haunt-days", 3) * 86_400_000L && ThreadLocalRandom.current().nextInt(600) == 0)
                p.sendMessage(Component.text(ECHOES.get(ThreadLocalRandom.current().nextInt(ECHOES.size())), TextColor.color(0xFFE3EA), TextDecoration.ITALIC));
        }
    }
}
