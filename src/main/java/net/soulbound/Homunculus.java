package net.soulbound;

import com.destroystokyo.paper.entity.ai.GoalType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Homunculi: a little wide-eyed thing grown in a flask, one per player.
 *
 * Crafted from a World Egg, a Tumour, a Tear of God and an Ember of Moloch: the Homunculus in a Flask. Use it on the
 * ground and it climbs out. It's a baby husk underneath (the resource pack redraws baby husks as homunculi, and wild
 * baby husks are swapped for baby zombies so the only ones in the world are these). It never attacks, monsters and
 * golems leave it alone, it doesn't drown into a zombie or burn, and it's silent except for its own little noises.
 *
 * Its job is what you last fed it (right-click it with the item):
 *   leather  - Pocket: follows you; right-click it for 9 extra slots
 *   tumour   - Gardener: stays where you fed it, harvests and replants ripe crops in your claim, puts the harvest in a chest
 *   bone     - Doorman: stays where you fed it; when anyone else comes near, it squeals and tells you who
 *   sugar    - just a meal
 * Any of them feeds it. Unfed for a few days it grows slow; longer and it rots away to a tumour.
 * Sneak and right-click it with an empty hand to put it back in its flask.
 */
final class Homunculus implements Listener {
    enum Job { POCKET, GARDENER, DOORMAN }

    private final Soulbound plugin;
    private final NamespacedKey flaskKey, ownerKey, jobKey, fedKey, homeKey, mineKey;
    private final NamespacedKey[] slotKeys = new NamespacedKey[9];
    private final Set<UUID> tamed = new HashSet<>();                // goals stripped this session
    private final Map<UUID, Long> seen = new HashMap<>();           // doorman: visitor -> when last reported
    private final Map<UUID, Block> aim = new HashMap<>();           // gardener: the crop or chest it's walking to
    private int tick;

    Homunculus(Soulbound plugin) {
        this.plugin = plugin;
        flaskKey = new NamespacedKey(plugin, "homunculus_flask");
        ownerKey = new NamespacedKey(plugin, "homunculus_owner");
        jobKey = new NamespacedKey(plugin, "homunculus_job");
        fedKey = new NamespacedKey(plugin, "homunculus_fed");
        homeKey = new NamespacedKey(plugin, "homunculus_home");
        mineKey = new NamespacedKey(plugin, "my_homunculus");
        for (int i = 0; i < 9; i++) slotKeys[i] = new NamespacedKey(plugin, "homunculus_slot_" + i);
    }

    void start() {
        registerRecipe();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("homunculus." + k, d); }

    // ---------------------------------------------------------------- the flask

    ItemStack flask() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Homunculus in a Flask", TextColor.color(0xF2A8B4), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("It's watching you through the glass.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.text("Use it on the ground to let it out.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Feed it leather, a tumour or a bone to give it a job.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setItemModel(new NamespacedKey("soulbound", "homunculus_flask"));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(flaskKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isFlask(ItemStack it) { return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(flaskKey, PersistentDataType.BYTE); }

    private void registerRecipe() {
        NamespacedKey key = new NamespacedKey(plugin, "homunculus_flask");
        Bukkit.removeRecipe(key);
        if (plugin.tumourCrop() == null || plugin.quantum() == null) return;
        ShapelessRecipe r = new ShapelessRecipe(key, flask());
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.DRAGON_EGG));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.tumourCrop().tumour(1)));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.quantum().material(QuantumGear.Mat.TEAR)));
        r.addIngredient(new RecipeChoice.ExactChoice(plugin.quantum().material(QuantumGear.Mat.EMBER)));
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(key);
    }

    // ---------------------------------------------------------------- what is one

    boolean is(Entity e) { return e instanceof Husk && e.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING); }

    private UUID owner(Entity e) {
        try { return UUID.fromString(e.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING)); } catch (RuntimeException x) { return null; }
    }

    private Job job(Entity e) {
        try { return Job.valueOf(e.getPersistentDataContainer().get(jobKey, PersistentDataType.STRING)); } catch (RuntimeException x) { return Job.POCKET; }
    }

    private Location home(Husk h) {
        String s = h.getPersistentDataContainer().get(homeKey, PersistentDataType.STRING);
        if (s == null) return null;
        String[] a = s.split(",");
        try { return new Location(h.getWorld(), Double.parseDouble(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2])); } catch (RuntimeException x) { return null; }
    }

    private void tame(Husk h) {
        if (!tamed.add(h.getUniqueId())) return;
        Bukkit.getMobGoals().removeAllGoals(h, GoalType.TARGET);
        Bukkit.getMobGoals().removeAllGoals(h, GoalType.MOVE);
        h.setTarget(null);
        h.setSilent(true);
        h.setCanPickupItems(false);
        h.setPersistent(true);
        h.setRemoveWhenFarAway(false);
    }

    // ---------------------------------------------------------------- letting one out

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseFlask(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || !isFlask(e.getItem()) || e.getClickedBlock() == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Husk old = mine(p);
        if (old != null) { p.sendActionBar(Component.text("You already have one. It's jealous.", NamedTextColor.RED)); return; }
        Location at = e.getClickedBlock().getRelative(e.getBlockFace()).getLocation().add(.5, 0, .5);
        Husk h = at.getWorld().spawn(at, Husk.class, x -> {
            x.setBaby();
            x.getEquipment().clear();
            for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.HAND, EquipmentSlot.OFF_HAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
                x.getEquipment().setDropChance(s, 0f);
            x.customName(Component.text(p.getName() + "'s Homunculus", TextColor.color(0xF2A8B4)));
            x.setCustomNameVisible(false);
            AttributeInstance hp = x.getAttribute(Attribute.MAX_HEALTH);
            if (hp != null) { hp.setBaseValue(cfg("health", 10)); x.setHealth(hp.getValue()); }
            PersistentDataContainer pdc = x.getPersistentDataContainer();
            pdc.set(ownerKey, PersistentDataType.STRING, p.getUniqueId().toString());
            pdc.set(jobKey, PersistentDataType.STRING, Job.POCKET.name());
            pdc.set(fedKey, PersistentDataType.LONG, System.currentTimeMillis());
        });
        tame(h);
        p.getPersistentDataContainer().set(mineKey, PersistentDataType.STRING, h.getUniqueId().toString());
        if (p.getGameMode() != GameMode.CREATIVE) p.getInventory().setItemInMainHand(null);
        at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
        at.getWorld().playSound(at, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, .6f);
        at.getWorld().spawnParticle(Particle.ITEM_SLIME, at.clone().add(0, .4, 0), 20, .3, .2, .3, .05);
        p.sendMessage(Component.text("It climbs out of the flask and looks up at you. It's yours.", TextColor.color(0xF2A8B4), TextDecoration.ITALIC));
    }

    /** The player's homunculus, if it's loaded. */
    private Husk mine(Player p) {
        String s = p.getPersistentDataContainer().get(mineKey, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Bukkit.getEntity(UUID.fromString(s)) instanceof Husk h && h.isValid() && is(h) ? h : null; } catch (RuntimeException x) { return null; }
    }

    // ---------------------------------------------------------------- feeding, jobs, the pocket, bottling

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!is(e.getRightClicked()) || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        Husk h = (Husk) e.getRightClicked();
        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(owner(h))) { p.sendActionBar(Component.text("It hides from you. It isn't yours.", NamedTextColor.GRAY)); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Material m = hand.getType();
        boolean tumour = plugin.tumourCrop() != null && plugin.tumourCrop().isTumour(hand);
        Job to = tumour ? Job.GARDENER : m == Material.BONE ? Job.DOORMAN : m == Material.LEATHER ? Job.POCKET : null;
        if (to != null || m == Material.SUGAR) {
            if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            h.getPersistentDataContainer().set(fedKey, PersistentDataType.LONG, System.currentTimeMillis());
            AttributeInstance sp = h.getAttribute(Attribute.MOVEMENT_SPEED);
            if (sp != null) sp.setBaseValue(.23);
            h.getWorld().playSound(h.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1.6f);
            h.getWorld().spawnParticle(Particle.HEART, h.getLocation().add(0, .9, 0), 2, .2, .1, .2);
            if (to != null) {
                h.getPersistentDataContainer().set(jobKey, PersistentDataType.STRING, to.name());
                Location l = h.getLocation();
                h.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING, l.getX() + "," + l.getY() + "," + l.getZ());
                aim.remove(h.getUniqueId());
                p.sendActionBar(Component.text(switch (to) {
                    case POCKET -> "Pocket: it follows you. Right-click it to open its pockets.";
                    case GARDENER -> "Gardener: it tends the crops in your claim around here, and fills the nearest chest.";
                    case DOORMAN -> "Doorman: it stays right here, and tells you who comes by.";
                }, TextColor.color(0xF2A8B4)));
            } else p.sendActionBar(Component.text("It eats it, slowly, staring at you.", TextColor.color(0xF2A8B4)));
            return;
        }
        if (p.isSneaking() && hand.getType().isAir()) { bottle(p, h); return; }
        if (job(h) == Job.POCKET) { openPocket(p, h); return; }
        long days = (System.currentTimeMillis() - fed(h)) / 86_400_000L;
        p.sendActionBar(Component.text(job(h).name().charAt(0) + job(h).name().substring(1).toLowerCase() + ". Last fed " + (days == 0 ? "today." : days + (days == 1 ? " day ago." : " days ago.")), TextColor.color(0xF2A8B4)));
    }

    private long fed(Husk h) { Long t = h.getPersistentDataContainer().get(fedKey, PersistentDataType.LONG); return t == null ? System.currentTimeMillis() : t; }

    private void bottle(Player p, Husk h) {
        for (ItemStack it : load(h)) if (it != null) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        h.remove();
        p.getPersistentDataContainer().remove(mineKey);
        for (ItemStack left : p.getInventory().addItem(flask()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), Sound.ITEM_BOTTLE_FILL, 1f, .8f);
    }

    // its nine slots, kept on the entity itself
    private ItemStack[] load(Husk h) {
        ItemStack[] out = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            byte[] b = h.getPersistentDataContainer().get(slotKeys[i], PersistentDataType.BYTE_ARRAY);
            if (b != null) try { out[i] = ItemStack.deserializeBytes(b); } catch (RuntimeException ignored) { }
        }
        return out;
    }

    private void save(Husk h, ItemStack[] items) {
        for (int i = 0; i < 9; i++) {
            ItemStack it = i < items.length ? items[i] : null;
            if (it == null || it.getType().isAir()) h.getPersistentDataContainer().remove(slotKeys[i]);
            else h.getPersistentDataContainer().set(slotKeys[i], PersistentDataType.BYTE_ARRAY, it.serializeAsBytes());
        }
    }

    static final class Pocket implements InventoryHolder {
        final Husk husk; Inventory inv;
        Pocket(Husk husk) { this.husk = husk; }
        @Override public Inventory getInventory() { return inv; }
    }

    private void openPocket(Player p, Husk h) {
        Pocket pk = new Pocket(h);
        pk.inv = Bukkit.createInventory(pk, 9, Component.text("Its pockets"));
        pk.inv.setContents(load(h));
        p.openInventory(pk.inv);
        h.getWorld().playSound(h.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, .8f, 1.4f);
    }

    @EventHandler
    public void onPocketClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Pocket pk && pk.husk.isValid()) save(pk.husk, pk.inv.getContents());
    }

    // ---------------------------------------------------------------- keeping it gentle

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (is(e.getEntity()) || (e.getTarget() != null && is(e.getTarget()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) { if (is(e.getDamager())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(EntityCombustEvent e) { if (is(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHurt(EntityDamageEvent e) {
        if (is(e.getEntity())) e.getEntity().getWorld().playSound(e.getEntity().getLocation(), Sound.ENTITY_ALLAY_HURT, 1f, .7f);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        if (!is(e.getEntity())) return;
        Husk h = (Husk) e.getEntity();
        e.getDrops().clear(); e.setDroppedExp(0);
        for (ItemStack it : load(h)) if (it != null) e.getDrops().add(it);
        if (plugin.tumourCrop() != null) e.getDrops().add(plugin.tumourCrop().tumour(1));
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1f, .6f);
        UUID o = owner(h);
        Player p = o == null ? null : Bukkit.getPlayer(o);
        if (p != null) {
            p.sendMessage(Component.text("Your homunculus is dead. Something in you feels lighter.", NamedTextColor.DARK_RED, TextDecoration.ITALIC));
            String mine = p.getPersistentDataContainer().get(mineKey, PersistentDataType.STRING);
            if (h.getUniqueId().toString().equals(mine)) p.getPersistentDataContainer().remove(mineKey);
        }
    }

    /** No wild baby husks: those are homunculi now. A baby zombie turns up instead. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Husk h) || h.isAdult() || e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        if (!plugin.getConfig().getBoolean("homunculus.replace-wild-baby-husks", true)) return;
        e.setCancelled(true);
        Location l = e.getLocation();
        Bukkit.getScheduler().runTask(plugin, () -> l.getWorld().spawn(l, Zombie.class, z -> z.setBaby()));
    }

    // ---------------------------------------------------------------- every half second

    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        double starve = cfg("starve-days", 3) * 86_400_000L, rot = cfg("rot-days", 5) * 86_400_000L;
        for (World w : Bukkit.getWorlds()) {
            for (Husk h : w.getEntitiesByClass(Husk.class)) {
                if (!is(h) || !h.isValid()) continue;
                tame(h);
                long hungry = now - fed(h);
                if (hungry > rot) { rotAway(h); continue; }
                AttributeInstance sp = h.getAttribute(Attribute.MOVEMENT_SPEED);
                if (sp != null) sp.setBaseValue(hungry > starve ? .12 : .23);
                UUID o = owner(h);
                Player p = o == null ? null : Bukkit.getPlayer(o);
                if (p != null) {   // a newer one replaced it
                    String mine = p.getPersistentDataContainer().get(mineKey, PersistentDataType.STRING);
                    if (mine != null && !mine.equals(h.getUniqueId().toString())) { h.remove(); continue; }
                }
                switch (job(h)) {
                    case POCKET -> follow(h, p);
                    case DOORMAN -> { stay(h); if (tick % 4 == 0) watch(h, p, o, now); }
                    case GARDENER -> { if (tick % 2 == 0) garden(h, o); }
                }
                if (java.util.concurrent.ThreadLocalRandom.current().nextInt(160) == 0)
                    w.playSound(h.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, .7f, .55f);
            }
        }
    }

    private void rotAway(Husk h) {
        Location l = h.getLocation();
        for (ItemStack it : load(h)) if (it != null) l.getWorld().dropItemNaturally(l, it);
        if (plugin.tumourCrop() != null) l.getWorld().dropItemNaturally(l, plugin.tumourCrop().tumour(1));
        l.getWorld().spawnParticle(Particle.ITEM_SLIME, l.add(0, .3, 0), 25, .3, .2, .3, .05);
        l.getWorld().playSound(l, Sound.BLOCK_HONEY_BLOCK_BREAK, 1f, .6f);
        UUID o = owner(h);
        Player p = o == null ? null : Bukkit.getPlayer(o);
        if (p != null) p.sendMessage(Component.text("You forgot to feed your homunculus. It's just a tumour now.", NamedTextColor.DARK_RED, TextDecoration.ITALIC));
        h.remove();
    }

    private void follow(Husk h, Player p) {
        if (p == null || !p.getWorld().equals(h.getWorld()) || p.getGameMode() == GameMode.SPECTATOR) return;
        double d = p.getLocation().distanceSquared(h.getLocation());
        if (d > cfg("teleport-distance", 20) * cfg("teleport-distance", 20) && p.isOnGround()) { h.teleport(p.getLocation()); return; }
        if (d > 9) h.getPathfinder().moveTo(p, 1.2);
        else h.getPathfinder().stopPathfinding();
    }

    private void stay(Husk h) {
        Location home = home(h);
        if (home == null) return;
        if (home.distanceSquared(h.getLocation()) > 4) h.getPathfinder().moveTo(home, 1.0);
    }

    /** Doorman: anyone else near home gets reported (once every few minutes each). */
    private void watch(Husk h, Player owner, UUID ownerId, long now) {
        Location home = home(h);
        if (home == null) return;
        double r = cfg("doorman-radius", 12);
        for (Player v : home.getNearbyPlayers(r)) {
            if (v.getUniqueId().equals(ownerId) || v.getGameMode() == GameMode.SPECTATOR) continue;
            Long last = seen.get(v.getUniqueId());
            if (last != null && now - last < cfg("doorman-cooldown-minutes", 5) * 60_000L) continue;
            seen.put(v.getUniqueId(), now);
            h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_HURT, 1.2f, 1.7f);
            h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_HURT, 1.2f, 1.5f);
            h.lookAt(v);
            if (owner != null) owner.sendMessage(Component.text("Your homunculus squeals: ", TextColor.color(0xF2A8B4), TextDecoration.ITALIC)
                    .append(Component.text(v.getName() + " is at your home.", NamedTextColor.WHITE)));
        }
    }

    // ---------------------------------------------------------------- gardening

    private static final Set<Material> CROPS = Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART);

    private void garden(Husk h, UUID owner) {
        Location home = home(h);
        if (home == null) return;
        int r = (int) cfg("gardener-radius", 8);
        ItemStack[] basket = load(h);
        boolean full = true, any = false;
        for (ItemStack it : basket) { if (it == null) full = false; else any = true; }
        Block target = aim.get(h.getUniqueId());
        if (target != null && target.getLocation().distanceSquared(home) > (r + 2) * (r + 2)) target = null;
        // carrying a full basket (or anything, with no crop ready): take it to a chest
        if (full || (any && target == null && findCrop(home, r, owner) == null)) {
            Block chest = target != null && target.getState() instanceof Container ? target : findChest(home, r);
            if (chest == null) { stay(h); return; }
            aim.put(h.getUniqueId(), chest);
            if (h.getLocation().distanceSquared(chest.getLocation().add(.5, .5, .5)) > 2.6 * 2.6) { h.getPathfinder().moveTo(chest.getLocation().add(.5, 1, .5), 1.0); return; }
            Inventory inv = ((Container) chest.getState()).getInventory();
            for (int i = 0; i < basket.length; i++) if (basket[i] != null) { Map<Integer, ItemStack> left = inv.addItem(basket[i]); basket[i] = left.isEmpty() ? null : left.values().iterator().next(); }
            save(h, basket);
            aim.remove(h.getUniqueId());
            h.getWorld().playSound(chest.getLocation(), Sound.BLOCK_BARREL_CLOSE, .6f, 1.4f);
            return;
        }
        if (target == null || !(target.getBlockData() instanceof Ageable)) target = findCrop(home, r, owner);
        if (target == null) { aim.remove(h.getUniqueId()); stay(h); return; }
        aim.put(h.getUniqueId(), target);
        if (h.getLocation().distanceSquared(target.getLocation().add(.5, 0, .5)) > 1.8 * 1.8) { h.getPathfinder().moveTo(target.getLocation().add(.5, 0, .5), 1.0); return; }
        // harvest and replant
        if (!(target.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) { aim.remove(h.getUniqueId()); return; }
        List<ItemStack> drops = new ArrayList<>(target.getDrops());
        a.setAge(0);
        target.setBlockData(a);
        h.getWorld().playSound(target.getLocation(), Sound.BLOCK_CROP_BREAK, .8f, 1.2f);
        h.swingMainHand();
        for (ItemStack d : drops) {
            boolean placed = false;
            for (int i = 0; i < basket.length && !placed; i++) {
                if (basket[i] == null) { basket[i] = d; placed = true; }
                else if (basket[i].isSimilar(d) && basket[i].getAmount() + d.getAmount() <= basket[i].getMaxStackSize()) { basket[i].setAmount(basket[i].getAmount() + d.getAmount()); placed = true; }
            }
            if (!placed) h.getWorld().dropItemNaturally(h.getLocation(), d);
        }
        save(h, basket);
        aim.remove(h.getUniqueId());
    }

    private Block findCrop(Location home, int r, UUID owner) {
        Block best = null; double bd = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -2; dy <= 2; dy++) {
            Block b = home.getWorld().getBlockAt(home.getBlockX() + dx, home.getBlockY() + dy, home.getBlockZ() + dz);
            if (!CROPS.contains(b.getType()) || !(b.getBlockData() instanceof Ageable a) || a.getAge() < a.getMaximumAge()) continue;
            if (!ownersClaim(b.getLocation(), owner)) continue;
            double d = b.getLocation().distanceSquared(home);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    private Block findChest(Location home, int r) {
        Block best = null; double bd = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -2; dy <= 2; dy++) {
            Block b = home.getWorld().getBlockAt(home.getBlockX() + dx, home.getBlockY() + dy, home.getBlockZ() + dz);
            Material m = b.getType();
            if (m != Material.CHEST && m != Material.BARREL && m != Material.TRAPPED_CHEST) continue;
            double d = b.getLocation().distanceSquared(home);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    // GriefPrevention, found by name: a gardener only works in a claim its owner owns (anywhere, without GP)
    private Object dataStore; private Method getClaimAt, getOwnerId; private boolean gpTried;

    private boolean ownersClaim(Location l, UUID owner) {
        if (!gpTried) {
            gpTried = true;
            Plugin gp = Bukkit.getPluginManager().getPlugin("GriefPrevention");
            if (gp != null) try {
                Class<?> claimC = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, gp.getClass().getClassLoader());
                dataStore = gp.getClass().getField("dataStore").get(gp);
                getClaimAt = dataStore.getClass().getMethod("getClaimAt", Location.class, boolean.class, claimC);
                getOwnerId = claimC.getMethod("getOwnerID");
            } catch (ReflectiveOperationException | RuntimeException e) { dataStore = null; }
        }
        if (Bukkit.getPluginManager().getPlugin("GriefPrevention") == null) return true;
        if (dataStore == null) return false;
        try {
            Object claim = getClaimAt.invoke(dataStore, l, true, null);
            return claim != null && owner != null && owner.equals(getOwnerId.invoke(claim));
        } catch (ReflectiveOperationException | RuntimeException e) { return false; }
    }
}
