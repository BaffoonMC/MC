package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Laestrygonians: man-eating giants out of the Odyssey, summoned by staff (/sb summon laestrygonians [count]).
 * Zombies twice a player's size in silver Spartan helms and iron armour, fast, strong and hard to kill; most carry spears, every third
 * a sword and shield. They never hurt or turn on each other. They drop iron blocks, Human Flesh and their helm.
 */
final class Laestrygonians implements Listener {
    static final String TAG = "laestrygonian";
    private static final TextColor SILVER = TextColor.color(0xD9DDE6);
    private final Soulbound plugin;
    private final NamespacedKey fleshKey;

    Laestrygonians(Soulbound plugin) {
        this.plugin = plugin;
        this.fleshKey = new NamespacedKey(plugin, "human_flesh");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickBars, 20L, 10L);
    }

    // ---------------------------------------------------------------- the war band's bar

    /** One summoning: who's in it, and the bar everyone nearby sees ("Laestrygonians · 4 of 6 left"). */
    private static final class Band {
        final List<UUID> members = new ArrayList<>();
        final Set<UUID> dead = new HashSet<>();
        final Set<UUID> viewers = new HashSet<>();
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        org.bukkit.World world;
        Location last;
    }
    private final List<Band> bands = new ArrayList<>();

    private void tickBars() {
        double r = plugin.getConfig().getDouble("laestrygonians.bar-radius", 96), r2 = r * r;
        for (Band b : new ArrayList<>(bands)) {
            List<Location> alive = new ArrayList<>();
            for (UUID id : b.members) {
                if (b.dead.contains(id)) continue;
                Entity e = Bukkit.getEntity(id);
                if (e != null && (!e.isValid() || e.isDead())) { if (e.isDead()) b.dead.add(id); continue; }
                if (e != null) alive.add(e.getLocation());
                else if (b.last != null) alive.add(b.last);   // in unloaded land: still out there
            }
            int left = b.members.size() - b.dead.size();
            if (left <= 0 || alive.isEmpty()) {   // the band is beaten (or every one of them is gone)
                for (UUID v : b.viewers) { Player p = Bukkit.getPlayer(v); if (p != null) p.hideBossBar(b.bar); }
                if (left <= 0 && b.last != null)
                    for (Player p : b.world.getPlayers()) if (p.getLocation().distanceSquared(b.last) < r2)
                        p.sendMessage(Component.text("The last Laestrygonian falls. The mountains are quiet again.", SILVER, TextDecoration.ITALIC));
                bands.remove(b);
                continue;
            }
            b.last = alive.get(0);
            b.bar.name(Component.text("Laestrygonians", SILVER, TextDecoration.BOLD).append(Component.text("  ·  " + left + " of " + b.members.size() + " left", NamedTextColor.GRAY)));
            b.bar.progress(Math.max(0f, Math.min(1f, left / (float) b.members.size())));
            for (Player p : Bukkit.getOnlinePlayers()) {
                boolean near = false;
                if (p.getWorld().equals(b.world)) for (Location l : alive) if (l.getWorld() == p.getWorld() && l.distanceSquared(p.getLocation()) < r2) { near = true; break; }
                if (near && b.viewers.add(p.getUniqueId())) p.showBossBar(b.bar);
                if (!near && b.viewers.remove(p.getUniqueId())) p.hideBossBar(b.bar);
            }
        }
    }

    @EventHandler
    public void onQuitBar(org.bukkit.event.player.PlayerQuitEvent e) { for (Band b : bands) b.viewers.remove(e.getPlayer().getUniqueId()); }

    static boolean is(Entity e) { return e != null && e.getScoreboardTags().contains(TAG); }

    // ---------------------------------------------------------------- Human Flesh

    ItemStack flesh(int n) {
        ItemStack it = new ItemStack(Material.ROTTEN_FLESH, Math.max(1, n));   // like rotten flesh, it can sicken the living
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Human Flesh", TextColor.color(0xC9605F)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Still warm. The Laestrygonians weren't fussy eaters.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true),
                Component.text("Filling, but it may turn a human's stomach.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Inhumans relish it.", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false)));
        m.setItemModel(new NamespacedKey("soulbound", "human_flesh"));
        FoodComponent food = m.getFood();
        food.setNutrition(plugin.getConfig().getInt("laestrygonians.flesh-hunger", 6));
        food.setSaturation((float) plugin.getConfig().getDouble("laestrygonians.flesh-saturation", 4.0));
        m.setFood(food);
        m.getPersistentDataContainer().set(fleshKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isFlesh(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(fleshKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- summoning

    /** Six (or {@code count}) of them in a ring around {@code at}. */
    String summon(Location at, int count) {
        World w = at.getWorld();
        if (w == null) return "That world isn't loaded.";
        count = Math.max(1, Math.min(30, count));
        double r = Math.max(3, count * 0.8);
        Band band = new Band();
        band.world = w; band.last = at.clone();
        for (int i = 0; i < count; i++) {
            double a = Math.PI * 2 * i / count;
            Location l = at.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            l.setY(Math.max(at.getY(), w.getHighestBlockYAt(l) + 1 > at.getY() + 4 ? at.getY() : w.getHighestBlockYAt(l) + 1));
            if (!l.getBlock().isPassable() || !l.clone().add(0, 1, 0).getBlock().isPassable()) l = at.clone();
            l.setYaw((float) Math.toDegrees(Math.atan2(at.getX() - l.getX(), l.getZ() - at.getZ())));
            band.members.add(spawn(l, (i + 1) % 3 == 0).getUniqueId());
            w.spawnParticle(Particle.CLOUD, l.clone().add(0, 1, 0), 30, 0.6, 1.2, 0.6, 0.02);
            w.spawnParticle(Particle.BLOCK, l, 40, 0.8, 0.2, 0.8, 0, Material.STONE.createBlockData());
        }
        bands.add(band);
        w.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 2.5f, 0.6f);
        w.playSound(at, Sound.EVENT_RAID_HORN, 3f, 0.8f);
        for (Player p : w.getPlayers())
            if (p.getLocation().distanceSquared(at) < 120 * 120)
                p.sendMessage(Component.text("The Laestrygonians have come down from the mountains, and they are hungry.", SILVER, TextDecoration.ITALIC));
        return "Summoned " + count + " Laestrygonian" + (count == 1 ? "" : "s") + ".";
    }

    private Zombie spawn(Location l, boolean shieldBearer) {
        FileConfiguration c = plugin.getConfig();
        return l.getWorld().spawn(l, Zombie.class, z -> {
            z.setAdult();
            z.setShouldBurnInDay(false);
            z.setCanPickupItems(false);
            z.setRemoveWhenFarAway(false);
            z.addScoreboardTag(TAG);
            z.customName(Component.text("Laestrygonian", SILVER));
            z.setCustomNameVisible(false);
            set(z, Attribute.SCALE, c.getDouble("laestrygonians.scale", 2.0));
            set(z, Attribute.MAX_HEALTH, c.getDouble("laestrygonians.health", 120));
            set(z, Attribute.ATTACK_DAMAGE, c.getDouble("laestrygonians.damage", 9));
            set(z, Attribute.MOVEMENT_SPEED, c.getDouble("laestrygonians.speed", 0.31));
            set(z, Attribute.ARMOR, c.getDouble("laestrygonians.armor", 12));
            set(z, Attribute.ARMOR_TOUGHNESS, 4);
            set(z, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            set(z, Attribute.FOLLOW_RANGE, 48);
            set(z, Attribute.STEP_HEIGHT, 1.1);
            set(z, Attribute.SPAWN_REINFORCEMENTS, 0);
            z.setHealth(c.getDouble("laestrygonians.health", 120));
            EntityEquipment eq = z.getEquipment();
            eq.setHelmet(plugin.cute().create(Cute.Kind.LAESTRYGONIAN));
            eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));   // iron armour you can see (the toughness is set above)
            eq.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            eq.setBoots(new ItemStack(Material.IRON_BOOTS));
            if (shieldBearer) {
                eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                eq.setItemInOffHand(new ItemStack(Material.SHIELD));
            } else {
                Material spear = Material.matchMaterial("iron_spear");
                eq.setItemInMainHand(new ItemStack(spear != null ? spear : Material.TRIDENT));
            }
            for (EquipmentSlot s : EquipmentSlot.values()) try { eq.setDropChance(s, 0f); } catch (IllegalArgumentException ignored) { }
        });
    }

    private static void set(LivingEntity e, Attribute a, double v) {
        AttributeInstance in = e.getAttribute(a);
        if (in != null) in.setBaseValue(v);
    }

    // ---------------------------------------------------------------- behaviour

    /** Brothers in hunger: they never hurt each other, even by accident. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile p && p.getShooter() instanceof Entity sh) d = sh;
        if (is(d) && is(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent e) { if (is(e.getEntity()) && is(e.getTarget())) e.setCancelled(true); }

    /** Daylight doesn't trouble them, and water doesn't make them drowned. */
    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent e) { if (is(e.getEntity()) && !(e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent)) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!is(e.getEntity())) return;
        for (Band b : bands) if (b.members.contains(e.getEntity().getUniqueId())) { b.dead.add(e.getEntity().getUniqueId()); b.last = e.getEntity().getLocation(); }
        FileConfiguration c = plugin.getConfig();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        e.getDrops().clear();
        int iron = between(r, c.getInt("laestrygonians.drops.iron-blocks-min", 1), c.getInt("laestrygonians.drops.iron-blocks-max", 3));
        int meat = between(r, c.getInt("laestrygonians.drops.flesh-min", 2), c.getInt("laestrygonians.drops.flesh-max", 4));
        if (iron > 0) e.getDrops().add(new ItemStack(Material.IRON_BLOCK, iron));
        if (meat > 0) e.getDrops().add(flesh(meat));
        if (r.nextDouble() < c.getDouble("laestrygonians.drops.helm-chance", 1.0)) e.getDrops().add(plugin.cute().create(Cute.Kind.LAESTRYGONIAN));
        e.setDroppedExp(c.getInt("laestrygonians.drops.xp", 40));
        Location l = e.getEntity().getLocation();
        l.getWorld().playSound(l, Sound.ENTITY_RAVAGER_DEATH, 1.5f, 0.7f);
    }

    private static int between(ThreadLocalRandom r, int lo, int hi) { return hi <= lo ? Math.max(0, lo) : r.nextInt(Math.max(0, lo), hi + 1); }
}
