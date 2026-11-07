package net.baffoon.brood;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Brood (test plugin). Wear the Brood Skin: you shrink to a one-heart black tick that can't hurt anything,
 * climbs walls, and can burrow into animals and steer them from the inside.
 */
public final class Brood extends JavaPlugin implements Listener {

    private NamespacedKey skinKey;
    private final Map<UUID, Bug> bugs = new HashMap<>();
    private final Map<UUID, Host> hosts = new HashMap<>();          // parasite -> host it lives in
    private final Map<UUID, UUID> hostOf = new HashMap<>();         // host entity -> parasite
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> leaving = new HashSet<>();
    private final Map<UUID, Location> lastPos = new HashMap<>();
    private Set<EntityType> allowed = new HashSet<>();
    private long tick;

    private static final class Host {
        final LivingEntity body;
        int fed;
        Host(LivingEntity body) { this.body = body; }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        skinKey = new NamespacedKey(this, "brood_skin");
        readHosts();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::loop, 1L, 1L);
    }

    @Override
    public void onDisable() {
        for (UUID id : new ArrayList<>(hosts.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) leave(p, false);
        }
        for (Bug b : bugs.values()) b.remove();
        bugs.clear();
        for (Player p : Bukkit.getOnlinePlayers()) dropInvisibility(p);
    }

    private void readHosts() {
        allowed = new HashSet<>();
        for (String s : getConfig().getStringList("hosts")) {
            try { allowed.add(EntityType.valueOf(s.toUpperCase(Locale.ROOT))); }
            catch (IllegalArgumentException e) { getLogger().warning("Unknown host type in config: " + s); }
        }
    }

    // ------------------------------------------------------------------ the item

    ItemStack skin() {
        FileConfiguration c = getConfig();
        ItemStack it = new ItemStack(Material.INK_SAC);
        ItemMeta m = it.getItemMeta();
        m.customName(Component.text("Brood Skin", NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                line("A wet black husk that still twitches.", NamedTextColor.DARK_GRAY),
                Component.empty(),
                line("Wear it to become the Brood:", NamedTextColor.GRAY),
                line(" Tiny, one heart, harmless.", NamedTextColor.GRAY),
                line(" Climb walls by walking into them.", NamedTextColor.GRAY),
                line(" Right-click an animal to crawl inside.", NamedTextColor.GRAY),
                line(" Steer it, feed on it, sneak to burst out.", NamedTextColor.GRAY),
                line(" Press F inside to make it call out.", NamedTextColor.GRAY),
                Component.empty(),
                line("Milk flushes a parasite out of its host.", NamedTextColor.DARK_GRAY)));
        String model = c.getString("item-model", "");
        if (model != null && model.contains(":")) m.setItemModel(NamespacedKey.fromString(model));
        EquippableComponent eq = m.getEquippable();
        eq.setSlot(EquipmentSlot.CHEST);          // no asset: nothing renders on the body, the bug does that
        eq.setEquipSound(Sound.ENTITY_SILVERFISH_AMBIENT);
        m.setEquippable(eq);
        double scale = c.getDouble("scale", 0.2);
        mod(m, Attribute.SCALE, "scale", scale - 1, AttributeModifier.Operation.ADD_NUMBER);
        mod(m, Attribute.MAX_HEALTH, "health", c.getDouble("health", 2) - 20, AttributeModifier.Operation.ADD_NUMBER);
        mod(m, Attribute.ATTACK_DAMAGE, "damage", -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        mod(m, Attribute.BLOCK_BREAK_SPEED, "dig", -1, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        mod(m, Attribute.SAFE_FALL_DISTANCE, "fall", c.getDouble("safe-fall", 8), AttributeModifier.Operation.ADD_NUMBER);
        mod(m, Attribute.MOVEMENT_SPEED, "speed", c.getDouble("speed-bonus", 0.1), AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        m.setEnchantmentGlintOverride(false);
        m.getPersistentDataContainer().set(skinKey, PersistentDataType.BYTE, (byte) 1);
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }

    private static Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private void mod(ItemMeta m, Attribute a, String key, double amount, AttributeModifier.Operation op) {
        m.addAttributeModifier(a, new AttributeModifier(new NamespacedKey(this, "brood_" + key), amount, op, EquipmentSlotGroup.CHEST));
    }

    private boolean isSkin(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(skinKey, PersistentDataType.BYTE);
    }

    boolean isBrood(Player p) {
        return isSkin(p.getInventory().getChestplate());
    }

    // ------------------------------------------------------------------ main loop

    private void loop() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            boolean brood = isBrood(p) && !p.isDead();
            if (!brood) {
                if (hosts.containsKey(id)) leave(p, true);
                Bug b = bugs.remove(id);
                if (b != null) { b.remove(); dropInvisibility(p); }
                lastPos.remove(id);
                continue;
            }
            Host h = hosts.get(id);
            if (h != null) { steer(p, h); continue; }
            crawl(p);
        }
    }

    /** Out in the open: keep the bug on, animate it, let the player climb walls. */
    private void crawl(Player p) {
        UUID id = p.getUniqueId();
        if (!p.hasPotionEffect(PotionEffectType.INVISIBILITY))
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false, false));
        Location now = p.getLocation();
        Bug b = bugs.get(id);
        if (b == null || !b.intact(now)) {
            if (b != null) b.remove();
            b = new Bug(p, (float) getConfig().getDouble("bug-lift", 0));
            bugs.put(id, b);
        }
        Location was = lastPos.put(id, now);
        boolean walking = was != null && was.getWorld() == now.getWorld()
                && (Math.abs(was.getX() - now.getX()) + Math.abs(was.getZ() - now.getZ())) > 0.01;
        b.follow(now, p.getBodyYaw());
        if (tick % 2 == 0) b.pose(walking);
        if (walking && tick % 6 == 0)
            p.getWorld().playSound(now, Sound.ENTITY_SILVERFISH_STEP, SoundCategory.PLAYERS, 0.12f, 1.8f);

        // climbing: walk into a wall and you scuttle up it
        if (getConfig().getBoolean("climb", true) && p.getCurrentInput().isForward() && !p.isFlying()) {
            Vector f = now.getDirection().setY(0);
            if (f.lengthSquared() > 1e-4) {
                f.normalize().multiply(0.16);
                Block feet = now.clone().add(f).getBlock();
                Block head = now.clone().add(f).add(0, 0.3, 0).getBlock();
                if (feet.isSolid() || head.isSolid()) {
                    Vector v = p.getVelocity();
                    p.setVelocity(new Vector(v.getX(), p.getCurrentInput().isSneak() ? 0 : 0.17, v.getZ()));
                    p.setFallDistance(0);
                    if (tick % 4 == 0) p.getWorld().playSound(now, Sound.ENTITY_SPIDER_STEP, SoundCategory.PLAYERS, 0.08f, 2f);
                }
            }
        }
    }

    // ------------------------------------------------------------------ infesting

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClickAnimal(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        Entity t = e.getRightClicked();

        // anyone can flush a parasite out with a bucket of milk
        if (!isBrood(p) && hostOf.containsKey(t.getUniqueId())) {
            ItemStack hand = p.getInventory().getItem(e.getHand());
            if (hand.getType() == Material.MILK_BUCKET) {
                e.setCancelled(true);
                Player para = Bukkit.getPlayer(hostOf.get(t.getUniqueId()));
                if (para != null) {
                    leave(para, true);
                    para.sendActionBar(Component.text("Someone flushed you out with milk!", NamedTextColor.RED));
                }
                if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) p.getInventory().setItem(e.getHand(), new ItemStack(Material.BUCKET));
                p.getWorld().playSound(t.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
                p.sendActionBar(Component.text("You flushed the parasite out.", NamedTextColor.GREEN));
            }
            return;
        }
        if (!isBrood(p) || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        if (!(t instanceof LivingEntity host) || !allowed.contains(t.getType())) {
            p.sendActionBar(Component.text("You can't get inside that.", NamedTextColor.GRAY));
            return;
        }
        if (hosts.containsKey(p.getUniqueId()) || p.getVehicle() != null) return;
        if (hostOf.containsKey(t.getUniqueId()) || !t.getPassengers().isEmpty()) {
            p.sendActionBar(Component.text("Something is already in there.", NamedTextColor.GRAY));
            return;
        }
        if (t instanceof Tameable tm && tm.isTamed() && !p.getUniqueId().equals(tm.getOwnerUniqueId())) {
            p.sendActionBar(Component.text("Someone loves this one. Leave it be.", NamedTextColor.GRAY));
            return;
        }
        if (saddled(host)) {
            p.sendActionBar(Component.text("Too much leather in the way.", NamedTextColor.GRAY));
            return;
        }
        long left = cooldown.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        if (left > 0) {
            p.sendActionBar(Component.text("Catch your breath first...", NamedTextColor.GRAY));
            return;
        }
        infest(p, host);
    }

    private static boolean saddled(LivingEntity e) {
        if (e instanceof Steerable s && s.hasSaddle()) return true;
        if (e instanceof AbstractHorse h && h.getInventory().getSaddle() != null) return true;
        return false;
    }

    private void infest(Player p, LivingEntity host) {
        Bug b = bugs.remove(p.getUniqueId());
        if (b != null) b.remove();
        Location at = host.getLocation().add(0, host.getHeight() * 0.5, 0);
        host.getWorld().spawnParticle(Particle.SQUID_INK, at, 14, 0.2, 0.2, 0.2, 0.02);
        host.getWorld().spawnParticle(Particle.BLOCK, at, 20, 0.25, 0.25, 0.25, 0, Material.BLACK_CONCRETE.createBlockData());
        host.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH_SMALL, 1f, 0.6f);
        host.getWorld().playSound(at, Sound.ENTITY_SILVERFISH_HURT, 0.6f, 0.5f);
        host.playHurtAnimation(p.getLocation().getYaw());
        if (host instanceof Mob m) m.setAware(false);
        if (!host.addPassenger(p)) {
            if (host instanceof Mob m) m.setAware(true);
            p.sendActionBar(Component.text("It shook you off.", NamedTextColor.GRAY));
            return;
        }
        hosts.put(p.getUniqueId(), new Host(host));
        hostOf.put(host.getUniqueId(), p.getUniqueId());
        p.sendActionBar(Component.text("You're in. WASD to walk it, space to jump, sneak to burst out.", NamedTextColor.DARK_PURPLE));
    }

    /** Inside a host: steer it with the player's keys, feed slowly, leave a trail of ink. */
    private void steer(Player p, Host h) {
        LivingEntity host = h.body;
        if (!host.isValid() || host.isDead() || !host.getPassengers().contains(p)) { leave(p, true); return; }
        FileConfiguration c = getConfig();
        org.bukkit.Input in = p.getCurrentInput();
        float yaw = p.getLocation().getYaw();
        host.setRotation(yaw, 0);
        host.setBodyYaw(yaw);

        double rad = Math.toRadians(yaw);
        Vector fwd = new Vector(-Math.sin(rad), 0, Math.cos(rad));
        Vector side = new Vector(Math.cos(rad), 0, Math.sin(rad));
        double f = (in.isForward() ? 1 : 0) - (in.isBackward() ? 1 : 0);
        double s = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        Vector v = host.getVelocity();
        boolean ground = host.isOnGround();
        if (f != 0 || s != 0) {
            AttributeInstance sp = host.getAttribute(Attribute.MOVEMENT_SPEED);
            double speed = sp == null ? 0.2 : sp.getValue();
            speed = Math.max(c.getDouble("host-speed-min", 0.13), Math.min(c.getDouble("host-speed-max", 0.3), speed * c.getDouble("host-speed", 1.0)));
            if (in.isSprint()) speed *= c.getDouble("host-sprint", 1.45);
            if (f < 0) speed *= 0.6;
            Vector dir = fwd.clone().multiply(f).add(side.clone().multiply(s)).normalize().multiply(speed);
            if (!ground) dir.multiply(0.5).add(new Vector(v.getX(), 0, v.getZ()).multiply(0.5));
            v.setX(dir.getX()).setZ(dir.getZ());
            // walk into a block and the host hops up it on its own
            if (ground && f > 0) {
                Location ahead = host.getLocation().add(fwd.clone().multiply(host.getWidth() / 2 + 0.35));
                Block b = ahead.getBlock();
                if (b.isSolid() && !b.getRelative(0, 1, 0).isSolid()) v.setY(0.42);
            }
        }
        if (in.isJump() && ground) v.setY(jumpPower(host));
        host.setVelocity(v);

        // the host gives itself away: ink drips, and now and then it twitches
        if (tick % 20 == 0) {
            Location at = host.getLocation().add((Math.random() - 0.5) * host.getWidth(), host.getHeight() * (0.3 + Math.random() * 0.5), (Math.random() - 0.5) * host.getWidth());
            host.getWorld().spawnParticle(Particle.SQUID_INK, at, 1, 0, 0, 0, 0.005);
            host.getWorld().spawnParticle(Particle.DUST, at, 2, 0.05, 0.05, 0.05, 0, new Particle.DustOptions(Color.fromRGB(0x1A0A24), 0.8f));
        }
        if (tick % 140 == 0 && Math.random() < 0.5) {
            host.playHurtAnimation((float) (Math.random() * 360));
            host.getWorld().playSound(host.getLocation(), Sound.ENTITY_SILVERFISH_AMBIENT, SoundCategory.NEUTRAL, 0.3f, 0.6f);
        }

        // feeding
        h.fed++;
        int every = Math.max(20, c.getInt("feed-every-ticks", 100));
        double floor = c.getDouble("host-min-health", 4);
        boolean hungry = p.getFoodLevel() < 20 || p.getHealth() < maxHealth(p);
        if (h.fed >= every && hungry && host.getHealth() > floor) {
            h.fed = 0;
            host.setHealth(Math.max(floor, host.getHealth() - c.getDouble("feed-drain", 1)));
            host.playHurtAnimation(yaw);
            p.setFoodLevel(Math.min(20, p.getFoodLevel() + c.getInt("feed-food", 2)));
            p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 1.5f));
            p.setHealth(Math.min(maxHealth(p), p.getHealth() + 1));
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.5f, 0.7f);
        }
        if (tick % 10 == 0) {
            String name = host.customName() != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(host.customName())
                    : pretty(host.getType());
            int hp = (int) Math.ceil(host.getHealth());
            int max = (int) Math.ceil(maxHealth(host));
            Component bar = Component.text("Inside: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(name, NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text("  ❤ " + hp + "/" + max, host.getHealth() <= floor ? NamedTextColor.DARK_RED : NamedTextColor.RED))
                    .append(Component.text(host.getHealth() <= floor ? "  drained" : (hungry ? "  feeding" : "  full"), NamedTextColor.GRAY))
                    .append(Component.text("  ·  sneak to burst out", NamedTextColor.DARK_GRAY));
            p.sendActionBar(bar);
        }
    }

    private static double jumpPower(LivingEntity host) {
        if (host instanceof AbstractHorse) {
            AttributeInstance j = host.getAttribute(Attribute.JUMP_STRENGTH);
            if (j != null) return Math.max(0.42, j.getValue() * 0.9);
        }
        if (host.getType() == EntityType.RABBIT || host.getType() == EntityType.GOAT) return 0.55;
        return 0.42;
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? 20 : a.getValue();
    }

    private static String pretty(EntityType t) {
        String s = t.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Get out of the host, with or without the messy bit. */
    void leave(Player p, boolean burst) {
        Host h = hosts.remove(p.getUniqueId());
        if (h == null) return;
        LivingEntity host = h.body;
        hostOf.remove(host.getUniqueId());
        leaving.add(p.getUniqueId());
        try { if (host.getPassengers().contains(p)) host.removePassenger(p); }
        finally { leaving.remove(p.getUniqueId()); }
        if (host.isValid() && host instanceof Mob m) m.setAware(true);
        cooldown.put(p.getUniqueId(), System.currentTimeMillis() + getConfig().getLong("reinfest-cooldown-seconds", 3) * 1000L);
        if (!burst) return;
        Location at = host.getLocation().add(0, host.getHeight() * 0.6, 0);
        host.getWorld().spawnParticle(Particle.SQUID_INK, at, 22, 0.25, 0.25, 0.25, 0.06);
        host.getWorld().spawnParticle(Particle.BLOCK, at, 30, 0.3, 0.3, 0.3, 0, Material.BLACK_CONCRETE.createBlockData());
        host.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 0.9f, 1.4f);
        host.getWorld().playSound(at, Sound.ENTITY_TURTLE_EGG_CRACK, 0.8f, 0.7f);
        if (host.isValid()) {
            host.playHurtAnimation(p.getLocation().getYaw());
            host.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, false));
        }
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            p.setVelocity(p.getLocation().getDirection().setY(0).multiply(0.25).setY(0.3));
            p.setFallDistance(0);
        });
    }

    @EventHandler
    public void onDismount(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p) || leaving.contains(p.getUniqueId())) return;
        Host h = hosts.get(p.getUniqueId());
        if (h == null || !h.body.equals(e.getDismounted())) return;
        // sneaking (or anything else) pops you out; tidy up after the dismount has happened
        Bukkit.getScheduler().runTask(this, () -> {
            if (hosts.get(p.getUniqueId()) == h) leave(p, true);
        });
    }

    @EventHandler
    public void onHostDeath(EntityDeathEvent e) {
        UUID para = hostOf.get(e.getEntity().getUniqueId());
        if (para == null) return;
        Player p = Bukkit.getPlayer(para);
        if (p != null) { leave(p, true); p.sendActionBar(Component.text("Your host died. Find another.", NamedTextColor.DARK_RED)); }
    }

    @EventHandler
    public void onSwapInside(PlayerSwapHandItemsEvent e) {
        Host h = hosts.get(e.getPlayer().getUniqueId());
        if (h == null) return;
        e.setCancelled(true);
        Sound s = h.body instanceof Mob m ? m.getAmbientSound() : null;
        if (s == null) s = Sound.ENTITY_SILVERFISH_AMBIENT;
        h.body.getWorld().playSound(h.body.getLocation(), s, SoundCategory.NEUTRAL, 1f, 0.8f + (float) Math.random() * 0.3f);
    }

    // ------------------------------------------------------------------ harmless

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (d instanceof Player p && isBrood(p)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (isBrood(e.getPlayer())) { e.setCancelled(true); e.getPlayer().sendActionBar(Component.text("Your tiny mouth can't chew through that.", NamedTextColor.GRAY)); }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (isBrood(e.getPlayer())) { e.setCancelled(true); e.getPlayer().sendActionBar(Component.text("You're far too small to build.", NamedTextColor.GRAY)); }
    }

    // ------------------------------------------------------------------ cleanup

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        leave(p, false);
        Bug b = bugs.remove(p.getUniqueId());
        if (b != null) b.remove();
        dropInvisibility(p);
        lastPos.remove(p.getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        leave(p, false);
        Bug b = bugs.remove(p.getUniqueId());
        if (b != null) b.remove();
    }

    private void dropInvisibility(Player p) {
        PotionEffect fx = p.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (fx != null && fx.isInfinite() && !fx.hasParticles()) p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ------------------------------------------------------------------ command

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "give" -> {
                if (!sender.hasPermission("brood.admin")) { sender.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true; }
                Player to = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player sp ? sp : null;
                if (to == null) { sender.sendMessage(Component.text("Who? /brood give <player>", NamedTextColor.RED)); return true; }
                to.getInventory().addItem(skin()).values().forEach(x -> to.getWorld().dropItem(to.getLocation(), x));
                sender.sendMessage(Component.text("Gave a Brood Skin to " + to.getName() + ".", NamedTextColor.DARK_PURPLE));
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) return true;
                if (hosts.containsKey(p.getUniqueId())) leave(p, true);
                else sender.sendMessage(Component.text("You aren't inside anything.", NamedTextColor.GRAY));
            }
            case "reload" -> {
                if (!sender.hasPermission("brood.admin")) return true;
                reloadConfig();
                readHosts();
                sender.sendMessage(Component.text("Brood config reloaded. Old skins keep their old stats; /brood give a new one.", NamedTextColor.DARK_PURPLE));
            }
            default -> sender.sendMessage(Component.text("/brood give [player] · /brood leave · /brood reload", NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) return List.of("give", "leave", "reload");
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return null;
        return List.of();
    }
}
