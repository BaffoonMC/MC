package net.soulbound;

import com.destroystokyo.paper.entity.ai.GoalType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
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
import java.util.concurrent.ConcurrentHashMap;

/**
 * Homunculi: a little wide-eyed thing grown in a flask, one per player.
 *
 * Crafted from a World Egg, a Tumour, a Tear of God and an Ember of Moloch: the Homunculus in a Flask. Use it on the
 * ground and it climbs out. It's a baby husk underneath (the resource pack redraws baby husks as homunculi, and wild
 * baby husks are swapped for baby zombies so the only ones in the world are these). It never attacks, monsters and
 * golems leave it alone, it doesn't drown into a zombie or burn, and it's silent except for its own little noises.
 *
 * Right-click it for its menu (a custom background, font soulbound:homunculus): clothes down the left like your own
 * inventory, its picture, health / hunger / name tag / flask buttons, a feeding bowl, its orders, and its nine pockets
 * along the bottom (they work whatever it's doing). Orders:
 *   Follow me  - comes with you
 *   Wait here  - sits where it is
 *   Farm here  - stays put, harvests and replants ripe crops in your claim, puts the harvest in the nearest chest
 *   Guard here - stays put; when anyone else comes near, it squeals and tells you who
 * Tumours and eggs heal it and count as a meal (right-click it holding one, or use the bowl). Unfed for a few days it
 * grows slow; longer and it rots away to a tumour.
 *
 * Naming: use a name tag on it like any mob, or the name tag in its menu and type the name in chat. The name floats
 * over its head, heads its menu, and stays with it in the flask.
 */
final class Homunculus implements Listener {
    /** Its orders, in menu order. POCKET is "Follow me" (the name is kept so older homunculi load). */
    enum Job { POCKET, WAIT, GARDENER, DOORMAN }

    private final Soulbound plugin;
    private final NamespacedKey flaskKey, ownerKey, jobKey, fedKey, homeKey, mineKey, iconKey, nameKey;
    private final NamespacedKey[] slotKeys = new NamespacedKey[9];
    private final Set<UUID> tamed = new HashSet<>();                // goals stripped this session
    private final Map<UUID, Long> seen = new HashMap<>();           // doorman: visitor -> when last reported
    private final Map<UUID, Block> aim = new HashMap<>();           // gardener: the crop or chest it's walking to
    private record Naming(UUID husk, long until) { }
    private final Map<UUID, Naming> naming = new ConcurrentHashMap<>();   // player -> the homunculus they're naming in chat
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final TextColor PINK = TextColor.color(0xF2A8B4);
    private int tick;

    Homunculus(Soulbound plugin) {
        this.plugin = plugin;
        flaskKey = new NamespacedKey(plugin, "homunculus_flask");
        ownerKey = new NamespacedKey(plugin, "homunculus_owner");
        jobKey = new NamespacedKey(plugin, "homunculus_job");
        fedKey = new NamespacedKey(plugin, "homunculus_fed");
        homeKey = new NamespacedKey(plugin, "homunculus_home");
        mineKey = new NamespacedKey(plugin, "my_homunculus");
        iconKey = new NamespacedKey(plugin, "homunculus_icon");
        nameKey = new NamespacedKey(plugin, "homunculus_name");
        for (int i = 0; i < 9; i++) slotKeys[i] = new NamespacedKey(plugin, "homunculus_slot_" + i);
    }

    void start() {
        registerRecipe();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("homunculus." + k, d); }

    // ---------------------------------------------------------------- the flask

    ItemStack flask() { return flask(null); }

    /** A flask; with a name, it's that homunculus back in its flask. */
    ItemStack flask(String name) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name == null ? "Homunculus in a Flask" : name + " in a Flask", PINK, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text(name == null ? "It's watching you through the glass." : name + " is watching you through the glass.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true),
                Component.text("Use it on the ground to let it out.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click it for its menu.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setItemModel(new NamespacedKey("soulbound", "homunculus_flask"));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(flaskKey, PersistentDataType.BYTE, (byte) 1);
        if (name != null) m.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, name);
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
        String name = e.getItem().getItemMeta().getPersistentDataContainer().get(nameKey, PersistentDataType.STRING);
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
            if (name != null) pdc.set(nameKey, PersistentDataType.STRING, name);
        });
        tame(h);
        showName(h);
        p.getPersistentDataContainer().set(mineKey, PersistentDataType.STRING, h.getUniqueId().toString());
        if (p.getGameMode() != GameMode.CREATIVE) p.getInventory().setItemInMainHand(null);
        at.getWorld().playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.2f);
        at.getWorld().playSound(at, Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, .6f);
        at.getWorld().spawnParticle(Particle.ITEM_SLIME, at.clone().add(0, .4, 0), 20, .3, .2, .3, .05);
        p.sendMessage(Component.text(name == null ? "It climbs out of the flask and looks up at you. It's yours." : name + " climbs out of the flask and looks up at you.", PINK, TextDecoration.ITALIC));
    }

    /** The player's homunculus, if it's loaded. */
    private Husk mine(Player p) {
        String s = p.getPersistentDataContainer().get(mineKey, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Bukkit.getEntity(UUID.fromString(s)) instanceof Husk h && h.isValid() && is(h) ? h : null; } catch (RuntimeException x) { return null; }
    }

    // ---------------------------------------------------------------- its name

    /** Its name, or null if it hasn't been given one. */
    private String name(Entity h) { return h.getPersistentDataContainer().get(nameKey, PersistentDataType.STRING); }

    /** "Pip", or "your homunculus" if it has no name. */
    private String called(Entity h) { String n = name(h); return n == null ? "your homunculus" : n; }

    private void showName(Husk h) {
        String n = name(h);
        h.customName(Component.text(n == null ? "Homunculus" : n, PINK));
        h.setCustomNameVisible(n != null);
    }

    /** Letters, numbers, spaces, ' - _ only; no colours; 16 characters at most. */
    private static String clean(String raw) {
        String s = raw.replaceAll("(?i)[\u00A7&][0-9a-fk-orx]", "").replaceAll("[^\\p{L}\\p{N} '_-]", "").trim().replaceAll(" +", " ");
        return s.length() > 16 ? s.substring(0, 16).trim() : s;
    }

    private boolean rename(Player p, Husk h, String raw) {
        String n = clean(raw);
        if (n.isEmpty()) { p.sendActionBar(Component.text("That isn't a name it can say.", NamedTextColor.RED)); return false; }
        h.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, n);
        showName(h);
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1f, 1.1f);
        h.getWorld().spawnParticle(Particle.HEART, h.getLocation().add(0, .9, 0), 3, .2, .1, .2);
        p.sendMessage(Component.text("It says \"" + n + "\" back to you, badly. It likes it.", PINK, TextDecoration.ITALIC));
        return true;
    }

    /** After the menu's name tag: the next thing they say in chat is its name. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        Naming w = naming.remove(e.getPlayer().getUniqueId());
        if (w == null || w.until() < System.currentTimeMillis()) return;
        e.setCancelled(true);
        String text = PLAIN.serialize(e.originalMessage()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (text.equalsIgnoreCase("cancel")) { p.sendActionBar(Component.text("It stays as it is.", NamedTextColor.GRAY)); return; }
            if (Bukkit.getEntity(w.husk()) instanceof Husk h && h.isValid() && is(h) && p.getUniqueId().equals(owner(h))) rename(p, h, text);
            else p.sendActionBar(Component.text("It's too far away to hear you.", NamedTextColor.GRAY));
        });
    }

    // ---------------------------------------------------------------- feeding, its menu, clothes, bottling

    /** Tumours and eggs: they heal it and count as a meal. */
    private double healOf(ItemStack it) {
        if (it == null || it.getType().isAir()) return 0;
        if (plugin.tumourCrop() != null && plugin.tumourCrop().isTumour(it)) return 6;
        String n = it.getType().name();
        return (n.equals("EGG") || n.equals("BROWN_EGG") || n.equals("BLUE_EGG")) && !it.hasItemMeta() ? 2 : 0;
    }

    private boolean feed(Husk h, ItemStack food) {
        double heal = healOf(food);
        if (heal <= 0) return false;
        food.setAmount(food.getAmount() - 1);
        h.getPersistentDataContainer().set(fedKey, PersistentDataType.LONG, System.currentTimeMillis());
        AttributeInstance hp = h.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) h.setHealth(Math.min(hp.getValue(), h.getHealth() + heal));
        AttributeInstance sp = h.getAttribute(Attribute.MOVEMENT_SPEED);
        if (sp != null) sp.setBaseValue(.23);
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_GENERIC_EAT, 1f, 1.6f);
        h.getWorld().spawnParticle(Particle.HEART, h.getLocation().add(0, .9, 0), 2, .2, .1, .2);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!is(e.getRightClicked()) || e.getHand() != EquipmentSlot.HAND) return;
        e.setCancelled(true);
        Husk h = (Husk) e.getRightClicked();
        Player p = e.getPlayer();
        if (!p.getUniqueId().equals(owner(h))) { p.sendActionBar(Component.text("It hides from you. It isn't yours.", NamedTextColor.GRAY)); return; }
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() == Material.NAME_TAG && hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {   // a name tag, like any mob
            if (rename(p, h, PLAIN.serialize(hand.getItemMeta().displayName())) && p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            return;
        }
        if (healOf(hand) > 0) {   // quick feed straight from your hand
            ItemStack one = hand.clone();
            if (feed(h, one) && p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
            p.sendActionBar(Component.text(name(h) == null ? "It eats it, slowly, staring at you." : name(h) + " eats it, slowly, staring at you.", PINK));
            return;
        }
        openMenu(p, h);
    }

    private long fed(Husk h) { Long t = h.getPersistentDataContainer().get(fedKey, PersistentDataType.LONG); return t == null ? System.currentTimeMillis() : t; }

    private void bottle(Player p, Husk h) {
        List<ItemStack> back = new ArrayList<>();
        for (ItemStack it : load(h)) if (it != null) back.add(it);
        for (EquipmentSlot s : WEAR) { ItemStack it = h.getEquipment().getItem(s); if (!it.getType().isAir()) back.add(it); }
        back.add(flask(name(h)));
        for (ItemStack it : back) for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        h.remove();
        p.getPersistentDataContainer().remove(mineKey);
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

    // ---- the menu: clothes along the top, a feeding bowl, its jobs, its pockets
    private static final EquipmentSlot[] WEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND};
    private static final String[] WEAR_NAME = {"Head", "Chest", "Legs", "Feet", "Hand"};
    // the layout (9 wide, 5 tall): clothes down column 0 (hand under the picture), picture in columns 1-2,
    // health / hunger / name / flask in column 3, the four orders across columns 4-8, pockets along the bottom row
    private static final int[] WEAR_SLOT = {0, 9, 18, 27, 28};
    private static final int HEALTH = 3, HUNGER = 12, NAME = 21, BOTTLE = 30, FEED = 29, POCKET0 = 36;
    private static final int[] PICTURE = {1, 2, 10, 11, 19, 20};
    private static final Key FONT = Key.key("soulbound", "homunculus");
    private static final String[][] ORDERS = {{"Follow me", "Comes with you.", "Its pockets come too."},
                                              {"Wait here", "Sits right where it is.", ""},
                                              {"Farm here", "Harvests and replants ripe crops in your claim.", "Puts the harvest in the nearest chest."},
                                              {"Guard here", "Stays here and watches.", "Tells you who comes by."}};
    private static final Material[] ORDER_ICON = {Material.LEAD, Material.ARMOR_STAND, Material.IRON_HOE, Material.BELL};

    static final class Menu implements InventoryHolder {
        final Husk husk; Inventory inv;
        Menu(Husk husk) { this.husk = husk; }
        @Override public Inventory getInventory() { return inv; }
    }

    private ItemStack icon(Material m, Component name, List<Component> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(name.decoration(TextDecoration.ITALIC, false));
        im.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        if (glow) im.setEnchantmentGlintOverride(true);
        im.getPersistentDataContainer().set(iconKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(im);
        return it;
    }

    private boolean isIcon(ItemStack it) { return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(iconKey, PersistentDataType.BYTE); }

    private void openMenu(Player p, Husk h) {
        Menu m = new Menu(h);
        Component title = Component.text().append(
                Component.text("\uF008\uE000\uF0A1").font(FONT).color(NamedTextColor.WHITE),
                Component.text(name(h) != null ? name(h) : "Homunculus", TextColor.color(0x404040))).build();
        m.inv = Bukkit.createInventory(m, 45, title);
        draw(m);
        ItemStack[] pocket = load(h);
        for (int i = 0; i < 9; i++) m.inv.setItem(POCKET0 + i, pocket[i]);
        p.openInventory(m.inv);
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, .8f, .7f);
    }

    private ItemStack widget(String model, Material fallback, Component name, List<Component> lore, boolean glow) {
        ItemStack it = icon(fallback, name, lore, glow);
        if (model != null) { ItemMeta im = it.getItemMeta(); im.setItemModel(new NamespacedKey("soulbound", model)); it.setItemMeta(im); }
        return it;
    }

    private static Component grey(String s) { return Component.text(s, NamedTextColor.GRAY); }
    private static Component dim(String s) { return Component.text(s, NamedTextColor.DARK_GRAY); }

    /** Everything but the pockets: redrawn whenever something changes. */
    private void draw(Menu m) {
        Husk h = m.husk;
        String n = name(h);
        AttributeInstance hp = h.getAttribute(Attribute.MAX_HEALTH);
        String health = "Health: " + (int) Math.ceil(h.getHealth()) + " / " + (int) (hp == null ? 10 : hp.getValue());
        long days = (System.currentTimeMillis() - fed(h)) / 86_400_000L;
        boolean hungry = days >= 1;
        // clothes: empty slots show the faded outline drawn in the background
        for (int i = 0; i < WEAR.length; i++) {
            ItemStack worn = h.getEquipment().getItem(WEAR[i]);
            m.inv.setItem(WEAR_SLOT[i], worn.getType().isAir() ? null : worn.clone());
        }
        // the picture: invisible, but hovering it says who it is
        ItemStack pic = widget("hmg_blank", Material.PAPER, Component.text(n == null ? "Your homunculus" : n, PINK),
                List.of(grey(health), grey("Order: " + ORDERS[job(h).ordinal()][0]), dim("Put clothes in the slots on the left.")), false);
        for (int s : PICTURE) m.inv.setItem(s, pic);
        m.inv.setItem(HEALTH, widget("hmg_heart", Material.PAPER, Component.text(health, NamedTextColor.RED),
                List.of(dim("Tumours heal 3 hearts, eggs 1.")), false));
        m.inv.setItem(HUNGER, hungry
                ? widget("hmg_hungry", Material.PAPER, Component.text("Hungry", NamedTextColor.RED), List.of(
                        grey("Last fed " + days + (days == 1 ? " day ago." : " days ago.")),
                        Component.text("It slows down after " + (int) cfg("starve-days", 3) + " days and rots after " + (int) cfg("rot-days", 5) + ".", days >= cfg("starve-days", 3) ? NamedTextColor.RED : NamedTextColor.DARK_GRAY)), false)
                : icon(Material.COOKED_CHICKEN, Component.text("Fed today", NamedTextColor.GREEN), List.of(dim("Feed it at least every few days.")), false));
        m.inv.setItem(NAME, icon(Material.NAME_TAG, Component.text(n == null ? "Name it" : "Rename " + n, PINK), List.of(
                grey(n == null ? "It doesn't have a name yet." : "Its name is " + n + "."), dim("Click, then type its name in chat.")), false));
        m.inv.setItem(BOTTLE, widget("homunculus_flask", Material.GLASS_BOTTLE, Component.text("Back in the flask", PINK),
                List.of(grey("It climbs back in."), dim("Its pockets and clothes come back to you.")), false));
        m.inv.setItem(FEED, widget(hungry ? "hmg_bowl" : null, Material.BOWL, Component.text("Feed", PINK),
                List.of(grey("Drop a tumour or an egg here."), dim("Or right-click it holding one.")), false));
        // orders: the chosen row turns green (five 18x18 pieces); the others show the icon and the drawn label
        Job j = job(h);
        for (int r = 0; r < 4; r++) {
            boolean on = j.ordinal() == r;
            List<Component> lore = new ArrayList<>();
            lore.add(grey(ORDERS[r][1]));
            if (!ORDERS[r][2].isEmpty()) lore.add(grey(ORDERS[r][2]));
            lore.add(on ? Component.text("Doing this now.", NamedTextColor.GREEN) : dim("Click to give this order."));
            Component title = Component.text(ORDERS[r][0], on ? NamedTextColor.GREEN : NamedTextColor.WHITE, TextDecoration.BOLD);
            for (int k = 0; k < 5; k++) {
                int slot = 4 + k + 9 * r;
                if (on) m.inv.setItem(slot, widget("hmg_sel_" + r + "_" + k, Material.PAPER, title, lore, false));
                else if (k == 0) m.inv.setItem(slot, icon(ORDER_ICON[r], title, lore, false));
                else m.inv.setItem(slot, widget("hmg_blank", Material.PAPER, title, lore, false));
            }
        }
    }

    /** The armour slot an item is made for (helmet, chestplate, ...), or null. */
    private static EquipmentSlot armourFor(ItemStack it) {
        if (it.hasItemMeta() && it.getItemMeta().hasEquippable()) return it.getItemMeta().getEquippable().getSlot();
        EquipmentSlot s = it.getType().getEquipmentSlot();
        return s == EquipmentSlot.HEAD || s == EquipmentSlot.CHEST || s == EquipmentSlot.LEGS || s == EquipmentSlot.FEET ? s : null;
    }

    private static boolean fits(EquipmentSlot s, ItemStack it) {
        if (s == EquipmentSlot.HEAD || s == EquipmentSlot.HAND) return true;   // anything on its head or in its hand
        if (it.hasItemMeta() && it.getItemMeta().hasEquippable()) return it.getItemMeta().getEquippable().getSlot() == s;
        return it.getType().getEquipmentSlot() == s;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        Player p = (Player) e.getWhoClicked();
        Husk h = m.husk;
        if (!h.isValid()) { e.setCancelled(true); p.closeInventory(); return; }
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < 45;
        if (!top) {
            if (e.isShiftClick() && e.getClickedInventory() == p.getInventory()) {   // from your inventory: food feeds it, everything else goes in its pockets
                e.setCancelled(true);
                int slot = e.getSlot();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack it = p.getInventory().getItem(slot);
                    if (it == null || it.getType().isAir() || !h.isValid()) return;
                    EquipmentSlot to = null;   // armour it isn't wearing yet goes straight on
                    for (int i = 0; i < 4 && to == null; i++)
                        if (it.getType() != Material.CARVED_PUMPKIN && armourFor(it) == WEAR[i] && h.getEquipment().getItem(WEAR[i]).getType().isAir()) to = WEAR[i];
                    if (healOf(it) > 0) feed(h, it);
                    else if (to != null) {
                        ItemStack one = it.clone(); one.setAmount(1);
                        h.getEquipment().setItem(to, one); h.getEquipment().setDropChance(to, 0f);
                        it.setAmount(it.getAmount() - 1);
                        p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, .8f, 1.3f);
                    }
                    else for (int i = POCKET0; i < POCKET0 + 9 && it.getAmount() > 0; i++) {
                        ItemStack there = m.inv.getItem(i);
                        if (there == null || there.getType().isAir()) { m.inv.setItem(i, it.clone()); it.setAmount(0); }
                        else if (there.isSimilar(it)) { int mv = Math.min(there.getMaxStackSize() - there.getAmount(), it.getAmount()); there.setAmount(there.getAmount() + mv); it.setAmount(it.getAmount() - mv); }
                    }
                    p.getInventory().setItem(slot, it.getAmount() > 0 ? it : null);
                    draw(m);
                });
            }
            return;
        }
        if (raw >= POCKET0 && raw < POCKET0 + 9) return;   // its pockets work like any chest
        e.setCancelled(true);
        int wear = -1;
        for (int i = 0; i < WEAR_SLOT.length; i++) if (WEAR_SLOT[i] == raw) wear = i;
        if (wear >= 0) {   // clothes: swap what's on your cursor with what it's wearing
            EquipmentSlot s = WEAR[wear];
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!h.isValid()) return;
                ItemStack cursor = p.getItemOnCursor();
                boolean holding = !cursor.getType().isAir();
                if (holding && !fits(s, cursor)) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .7f, .7f); return; }
                ItemStack worn = h.getEquipment().getItem(s);
                ItemStack back = worn.getType().isAir() ? null : worn.clone();
                if (!holding && back == null) return;
                ItemStack put = null, rest = null;
                if (holding) { put = cursor.clone(); put.setAmount(1); rest = cursor.clone(); rest.setAmount(cursor.getAmount() - 1); if (rest.getAmount() <= 0) rest = null; }
                h.getEquipment().setItem(s, put);
                h.getEquipment().setDropChance(s, 0f);
                if (rest == null) p.setItemOnCursor(back);
                else { p.setItemOnCursor(rest); if (back != null) for (ItemStack left : p.getInventory().addItem(back).values()) p.getWorld().dropItemNaturally(p.getLocation(), left); }
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, .8f, 1.3f);
                draw(m);
            });
            return;
        }
        if (raw == FEED) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                ItemStack cursor = p.getItemOnCursor();
                if (cursor.getType().isAir() || !h.isValid()) return;
                if (feed(h, cursor)) { p.setItemOnCursor(cursor.getAmount() > 0 ? cursor : null); draw(m); }
                else p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .7f, .7f);
            });
            return;
        }
        int col = raw % 9, r = raw / 9;
        if (col >= 4 && r < 4) {   // an order: anywhere on its row
            Job to = Job.values()[r];
            if (to == job(h)) return;
            h.getPersistentDataContainer().set(jobKey, PersistentDataType.STRING, to.name());
            Location l = h.getLocation();
            h.getPersistentDataContainer().set(homeKey, PersistentDataType.STRING, l.getX() + "," + l.getY() + "," + l.getZ());
            aim.remove(h.getUniqueId());
            h.getPathfinder().stopPathfinding();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .7f, 1.2f);
            h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, .7f, 1.3f);
            draw(m);
            return;
        }
        if (raw == NAME) {
            naming.put(p.getUniqueId(), new Naming(h.getUniqueId(), System.currentTimeMillis() + 60_000L));
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .7f, 1.2f);
            p.sendMessage(Component.text("Type its name in chat. ", PINK).append(Component.text("(16 letters at most, or type cancel)", NamedTextColor.DARK_GRAY)));
            return;
        }
        if (raw == BOTTLE) { savePocket(m); p.closeInventory(); bottle(p, h); }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onMenuDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu)) return;
        for (int raw : e.getRawSlots()) if (raw < 45 && (raw < POCKET0 || raw >= POCKET0 + 9)) { e.setCancelled(true); return; }
    }

    private void savePocket(Menu m) {
        ItemStack[] items = new ItemStack[9];
        for (int i = 0; i < 9; i++) items[i] = m.inv.getItem(POCKET0 + i);
        if (m.husk.isValid()) save(m.husk, items);
        for (int i = 0; i < 9; i++) m.inv.setItem(POCKET0 + i, null);
    }

    @EventHandler
    public void onMenuClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        if (m.husk.isValid()) { savePocket(m); return; }
        // it died or vanished while the menu was open: hand its pockets back
        for (int i = 0; i < 9; i++) {
            ItemStack it = m.inv.getItem(POCKET0 + i);
            if (it != null && !it.getType().isAir()) for (ItemStack left : e.getPlayer().getInventory().addItem(it).values()) e.getPlayer().getWorld().dropItemNaturally(e.getPlayer().getLocation(), left);
        }
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
        for (EquipmentSlot s : WEAR) { ItemStack it = h.getEquipment().getItem(s); if (!it.getType().isAir()) e.getDrops().add(it); }
        if (plugin.tumourCrop() != null) e.getDrops().add(plugin.tumourCrop().tumour(1));
        h.getWorld().playSound(h.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1f, .6f);
        UUID o = owner(h);
        Player p = o == null ? null : Bukkit.getPlayer(o);
        if (p != null) {
            p.sendMessage(Component.text((name(h) == null ? "Your homunculus" : name(h)) + " is dead. Something in you feels lighter.", NamedTextColor.DARK_RED, TextDecoration.ITALIC));
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
                    case WAIT -> stay(h);
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
        for (EquipmentSlot s : WEAR) { ItemStack it = h.getEquipment().getItem(s); if (!it.getType().isAir()) l.getWorld().dropItemNaturally(l, it); }
        if (plugin.tumourCrop() != null) l.getWorld().dropItemNaturally(l, plugin.tumourCrop().tumour(1));
        l.getWorld().spawnParticle(Particle.ITEM_SLIME, l.add(0, .3, 0), 25, .3, .2, .3, .05);
        l.getWorld().playSound(l, Sound.BLOCK_HONEY_BLOCK_BREAK, 1f, .6f);
        UUID o = owner(h);
        Player p = o == null ? null : Bukkit.getPlayer(o);
        if (p != null) p.sendMessage(Component.text("You forgot to feed " + called(h) + ". It's just a tumour now.", NamedTextColor.DARK_RED, TextDecoration.ITALIC));
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
            if (owner != null) owner.sendMessage(Component.text((name(h) == null ? "Your homunculus" : name(h)) + " squeals: ", PINK, TextDecoration.ITALIC)
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
