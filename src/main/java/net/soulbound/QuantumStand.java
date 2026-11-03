package net.soulbound;

import net.kyori.adventure.key.Key;
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
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Quantum Stand: alchemy past good and evil.
 *
 * In the world it's an invisible barrier block with the stand's model (stone plinth, Moloch's horns, God's halo, the
 * blue quantum core) drawn on an item display. Place it like a block; right-click to open it; hit it three times
 * quickly to pick it back up (creative: once). Placing and breaking go through the normal block events, so claims and
 * protection plugins get their say.
 *
 * The menu is one big circle, its background drawn by the resource pack (a font glyph in the title). Moloch's side
 * (left two points of the star) takes fire, God's side (right two) takes grace, the top point takes a vessel. The
 * needle at the bottom shows which side is heavier; the Fuse button only lights when they're balanced. Fusing takes a
 * few seconds, the centre filling up blue, then the result comes out on the right. Everything left in the stand goes
 * back to you when you close it.
 *
 * Recipes are deliberately few for now (see RECIPES): the Quantum Core from an Ember of Moloch and a Tear of God.
 */
final class QuantumStand implements Listener {
    // menu slots (a 5-row chest)
    static final int VESSEL = 3, FIRE_A = 10, FIRE_B = 38, GRACE_A = 14, GRACE_B = 40, CENTRE = 21, NEEDLE = 39, RESULT = 25, FUSE = 43;
    private static final int[] INPUTS = {VESSEL, FIRE_A, FIRE_B, GRACE_A, GRACE_B};
    private static final Key FONT = Key.key("soulbound", "quantum");
    private static final int FUSE_TICKS = 100;

    private final Soulbound plugin;
    private final NamespacedKey itemKey, standKey;
    private final Map<UUID, int[]> hits = new HashMap<>();   // player -> {x, y, z, count, tick}

    QuantumStand(Soulbound plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "quantum_stand_item");
        this.standKey = new NamespacedKey(plugin, "quantum_stand");
    }

    // ---------------------------------------------------------------- the item

    ItemStack item() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Quantum Stand", TextColor.color(0x7FC4FF), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Fire on one side, grace on the other.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Balance them, and fuse something new.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Neither his nor His.", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, true)));
        m.setItemModel(new NamespacedKey("soulbound", "quantum_stand"));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isItem(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE);
    }

    // ---------------------------------------------------------------- placing, opening, breaking

    private ItemDisplay standAt(Block b) {
        if (b.getType() != Material.BARRIER) return null;
        for (ItemDisplay d : b.getWorld().getNearbyEntitiesByType(ItemDisplay.class, b.getLocation().add(.5, .5, .5), .45))
            if (d.getPersistentDataContainer().has(standKey, PersistentDataType.BYTE)) return d;
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Player p = e.getPlayer();

        // hitting a stand: three quick hits pick it up
        if (e.getAction() == Action.LEFT_CLICK_BLOCK && standAt(clicked) != null) {
            e.setCancelled(true);
            if (p.getGameMode() == GameMode.CREATIVE) { breakStand(p, clicked); return; }
            int now = Bukkit.getCurrentTick();
            int[] h = hits.get(p.getUniqueId());
            if (h == null || h[0] != clicked.getX() || h[1] != clicked.getY() || h[2] != clicked.getZ() || now - h[4] > 30) h = new int[]{clicked.getX(), clicked.getY(), clicked.getZ(), 0, now};
            h[3]++; h[4] = now; hits.put(p.getUniqueId(), h);
            clicked.getWorld().playSound(clicked.getLocation().add(.5, .5, .5), Sound.BLOCK_STONE_HIT, 1f, .8f + h[3] * .1f);
            if (h[3] >= 3) { hits.remove(p.getUniqueId()); breakStand(p, clicked); }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // right-clicking a stand opens it
        if (standAt(clicked) != null) {
            e.setCancelled(true);
            if (e.getHand() != EquipmentSlot.HAND) return;
            open(p, clicked);
            return;
        }

        // placing one
        if (e.getHand() != EquipmentSlot.HAND || !isItem(e.getItem())) return;
        e.setCancelled(true);
        Block target = clicked.isReplaceable() ? clicked : clicked.getRelative(e.getBlockFace());
        if (!target.isReplaceable() || target.getY() >= target.getWorld().getMaxHeight() - 1) return;
        if (!target.getWorld().getNearbyEntities(target.getBoundingBox(), en -> en instanceof org.bukkit.entity.LivingEntity).isEmpty()) return;
        BlockState before = target.getState();
        target.setType(Material.BARRIER, false);
        BlockPlaceEvent ev = new BlockPlaceEvent(target, before, clicked, e.getItem(), p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled() || !ev.canBuild()) { before.update(true, false); return; }
        float yaw = Math.round(p.getYaw() / 90f) * 90f + 180f;
        target.getWorld().spawn(target.getLocation().add(.5, .5, .5), ItemDisplay.class, d -> {
            ItemStack look = new ItemStack(Material.PAPER);
            ItemMeta lm = look.getItemMeta(); lm.setItemModel(new NamespacedKey("soulbound", "quantum_stand")); look.setItemMeta(lm);
            d.setItemStack(look);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setRotation(yaw, 0);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(standKey, PersistentDataType.BYTE, (byte) 1);
        });
        if (p.getGameMode() != GameMode.CREATIVE) { ItemStack hand = p.getInventory().getItemInMainHand(); hand.setAmount(hand.getAmount() - 1); }
        target.getWorld().playSound(target.getLocation().add(.5, .5, .5), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, .7f);
        target.getWorld().playSound(target.getLocation().add(.5, .5, .5), Sound.BLOCK_BEACON_ACTIVATE, .4f, 1.6f);
    }

    private void breakStand(Player p, Block b) {
        BlockBreakEvent ev = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return;
        remove(b, p.getGameMode() != GameMode.CREATIVE);
    }

    /** A creative player breaking the barrier directly: tidy up the model. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBarrierBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.BARRIER) return;
        ItemDisplay d = standAt(e.getBlock());
        if (d != null) d.remove();
    }

    private void remove(Block b, boolean drop) {
        ItemDisplay d = standAt(b);
        if (d != null) d.remove();
        // anyone using it gets their things back first
        for (Player o : Bukkit.getOnlinePlayers())
            if (o.getOpenInventory().getTopInventory().getHolder() instanceof Menu m && m.block.equals(b)) o.closeInventory();
        b.setType(Material.AIR);
        Location c = b.getLocation().add(.5, .5, .5);
        b.getWorld().playSound(c, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, .7f);
        b.getWorld().spawnParticle(Particle.END_ROD, c, 12, .3, .3, .3, .02);
        if (drop) b.getWorld().dropItemNaturally(c, item());
    }

    // ---------------------------------------------------------------- the menu

    static final class Menu implements InventoryHolder {
        final Block block;
        Inventory inv;
        BukkitTask fusing;
        Menu(Block block) { this.block = block; }
        @Override public Inventory getInventory() { return inv; }
    }

    private void open(Player p, Block b) {
        Menu m = new Menu(b);
        Component title = Component.text().append(
                Component.text("").font(FONT).color(NamedTextColor.WHITE),
                Component.text("Quantum Stand", TextColor.color(0x9FD4FF))).build();
        m.inv = Bukkit.createInventory(m, 45, title);
        refresh(m);
        p.openInventory(m.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, .6f, 1.4f);
    }

    private static boolean isInput(int slot) {
        for (int s : INPUTS) if (s == slot) return true;
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR) { e.setCancelled(true); return; }
        int raw = e.getRawSlot();
        boolean top = raw >= 0 && raw < m.inv.getSize();
        if (m.fusing != null) { if (top || e.isShiftClick()) e.setCancelled(true); return; }
        if (!top) {
            if (e.isShiftClick()) { e.setCancelled(true); shiftIn(m, e.getCurrentItem()); later(m); }
            return;
        }
        if (raw == FUSE) { e.setCancelled(true); tryFuse((Player) e.getWhoClicked(), m); return; }
        if (raw == RESULT) {
            // take only: no putting things into the result slot
            ItemStack cur = e.getCursor();
            boolean placing = cur != null && !cur.getType().isAir() && e.getClick() != ClickType.SHIFT_LEFT && e.getClick() != ClickType.SHIFT_RIGHT;
            if (placing || e.getClick() == ClickType.NUMBER_KEY && !isEmpty(e.getWhoClicked().getInventory().getItem(e.getHotbarButton()))) e.setCancelled(true);
            later(m);
            return;
        }
        if (!isInput(raw)) { e.setCancelled(true); return; }
        later(m);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        for (int raw : e.getRawSlots())
            if (raw < m.inv.getSize() && (m.fusing != null || !isInput(raw))) { e.setCancelled(true); return; }
        later(m);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu m)) return;
        if (m.fusing != null) { m.fusing.cancel(); m.fusing = null; }
        Player p = (Player) e.getPlayer();
        for (int s : new int[]{VESSEL, FIRE_A, FIRE_B, GRACE_A, GRACE_B, RESULT}) {
            ItemStack it = m.inv.getItem(s);
            if (isEmpty(it)) continue;
            m.inv.setItem(s, null);
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    /** Shift-clicking from your inventory: fire goes left, grace goes right, anything else to the vessel. */
    private void shiftIn(Menu m, ItemStack it) {
        if (isEmpty(it)) return;
        int[] order = fire(it) > 0 ? new int[]{FIRE_A, FIRE_B} : grace(it) > 0 ? new int[]{GRACE_A, GRACE_B} : new int[]{VESSEL};
        for (int s : order) {
            ItemStack there = m.inv.getItem(s);
            if (isEmpty(there)) { m.inv.setItem(s, it.clone()); it.setAmount(0); return; }
            if (there.isSimilar(it)) {
                int room = there.getMaxStackSize() - there.getAmount(); int move = Math.min(room, it.getAmount());
                there.setAmount(there.getAmount() + move); it.setAmount(it.getAmount() - move);
                if (it.getAmount() <= 0) return;
            }
        }
    }

    private void later(Menu m) { Bukkit.getScheduler().runTask(plugin, () -> refresh(m)); }

    // ---------------------------------------------------------------- balance

    /** How much fire (Moloch) an item carries. */
    private int fire(ItemStack it) {
        if (isEmpty(it)) return 0;
        int per = 0;
        if (plugin.quantum() != null && plugin.quantum().matOf(it) == QuantumGear.Mat.EMBER) per = 3;
        else if (plugin.inhuman() != null && plugin.inhuman().isHeart(it)) per = 5;
        else if (plugin.bloodEmeralds() != null && plugin.bloodEmeralds().is(it)) per = 1;
        else if (plugin.tumourCrop() != null && plugin.tumourCrop().isTumour(it)) per = 1;
        return per * it.getAmount();
    }

    /** How much grace (God) an item carries. */
    private int grace(ItemStack it) {
        if (isEmpty(it)) return 0;
        int per = 0;
        if (plugin.quantum() != null && plugin.quantum().matOf(it) == QuantumGear.Mat.TEAR) per = 3;
        else if (plugin.grace() != null && plugin.grace().isHeart(it)) per = 5;
        else if (plugin.cloudCotton() != null && plugin.cloudCotton().isCotton(it)) per = 1;
        return per * it.getAmount();
    }

    private int totalFire(Menu m) { int t = 0; for (int s : INPUTS) t += fire(m.inv.getItem(s)); return t; }
    private int totalGrace(Menu m) { int t = 0; for (int s : INPUTS) t += grace(m.inv.getItem(s)); return t; }

    private static boolean balanced(int f, int g) { return f > 0 && g > 0 && f == g; }

    private void refresh(Menu m) {
        if (m.fusing != null) return;
        int f = totalFire(m), g = totalGrace(m);
        int needle;
        String say; NamedTextColor col;
        if (f == 0 && g == 0) { needle = 2; say = "Empty. Fire on the left, grace on the right."; col = NamedTextColor.GRAY; }
        else if (balanced(f, g)) { needle = 2; say = "Balanced."; col = NamedTextColor.AQUA; }
        else {
            double lean = (g - f) / (double) (g + f);
            needle = lean <= -.5 ? 0 : lean < 0 ? 1 : lean < .5 ? 3 : 4;
            say = lean < 0 ? (lean <= -.5 ? "Far too much fire." : "Too much fire. Add grace.") : (lean >= .5 ? "Far too much grace." : "Too much grace. Add fire.");
            col = lean < 0 ? NamedTextColor.RED : NamedTextColor.GOLD;
        }
        m.inv.setItem(NEEDLE, widget("qsw_needle_" + needle, Component.text(say, col), List.of(
                Component.text("Fire: " + f + "   Grace: " + g, NamedTextColor.DARK_GRAY))));
        Recipe r = balanced(f, g) ? match(m) : null;
        boolean ready = r != null && fits(m, r.out.get());
        m.inv.setItem(FUSE, widget(ready ? "qsw_fuse_on" : "qsw_fuse_off",
                ready ? Component.text("Fuse", NamedTextColor.AQUA, TextDecoration.BOLD) : Component.text("Fuse", NamedTextColor.DARK_GRAY),
                List.of(ready ? Component.text("Makes: " + r.name, NamedTextColor.GRAY)
                        : Component.text(balanced(f, g) ? "Nothing fuses from this. Yet." : "Balance fire and grace first.", NamedTextColor.DARK_GRAY))));
        m.inv.setItem(CENTRE, widget("qsw_prog_0", Component.text("The core", TextColor.color(0x7FC4FF)), List.of()));
    }

    private ItemStack widget(String model, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.setItemModel(new NamespacedKey("soulbound", model));
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return it;
    }

    // ---------------------------------------------------------------- recipes and fusing

    /** What goes in (checked against the five input slots) and what comes out. Add more here. */
    private record Recipe(String name, java.util.function.Predicate<Menu> needs, java.util.function.Consumer<Menu> take, java.util.function.Supplier<ItemStack> out) { }

    private final List<Recipe> RECIPES = List.of(
            new Recipe("Quantum Core",
                    m -> count(m, QuantumGear.Mat.EMBER) >= 1 && count(m, QuantumGear.Mat.TEAR) >= 1,
                    m -> { takeOne(m, QuantumGear.Mat.EMBER); takeOne(m, QuantumGear.Mat.TEAR); },
                    this::core));

    private ItemStack core() { return plugin.quantum().material(QuantumGear.Mat.CORE); }

    private int count(Menu m, QuantumGear.Mat k) {
        int n = 0;
        for (int s : INPUTS) { ItemStack it = m.inv.getItem(s); if (!isEmpty(it) && plugin.quantum().matOf(it) == k) n += it.getAmount(); }
        return n;
    }

    private void takeOne(Menu m, QuantumGear.Mat k) {
        for (int s : INPUTS) {
            ItemStack it = m.inv.getItem(s);
            if (!isEmpty(it) && plugin.quantum().matOf(it) == k) { it.setAmount(it.getAmount() - 1); m.inv.setItem(s, it.getAmount() > 0 ? it : null); return; }
        }
    }

    private Recipe match(Menu m) {
        if (plugin.quantum() == null) return null;
        for (Recipe r : RECIPES) if (r.needs.test(m)) return r;
        return null;
    }

    private boolean fits(Menu m, ItemStack out) {
        ItemStack there = m.inv.getItem(RESULT);
        return isEmpty(there) || (there.isSimilar(out) && there.getAmount() + out.getAmount() <= there.getMaxStackSize());
    }

    private void tryFuse(Player p, Menu m) {
        int f = totalFire(m), g = totalGrace(m);
        Recipe r = balanced(f, g) ? match(m) : null;
        if (r == null || !fits(m, r.out.get())) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, .8f, .6f); return; }
        Location c = m.block.getLocation().add(.5, 1.2, .5);
        int[] t = {0};
        m.fusing = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            t[0] += 2;
            int frame = Math.min(8, t[0] * 8 / FUSE_TICKS);
            m.inv.setItem(CENTRE, widget("qsw_prog_" + frame, Component.text("Fusing... " + (t[0] * 100 / FUSE_TICKS) + "%", NamedTextColor.AQUA), List.of()));
            if (t[0] % 10 == 0) {
                c.getWorld().playSound(c, Sound.BLOCK_BEACON_AMBIENT, .6f, .8f + t[0] / (float) FUSE_TICKS);
                c.getWorld().spawnParticle(Particle.FLAME, c.clone().add(-.35, 0, 0), 3, .05, .1, .05, .01);
                c.getWorld().spawnParticle(Particle.END_ROD, c.clone().add(.35, 0, 0), 3, .05, .1, .05, .01);
            }
            if (t[0] < FUSE_TICKS) return;
            m.fusing.cancel(); m.fusing = null;
            // the inputs could have changed hands only through us, but check the recipe once more before taking anything
            if (r.needs.test(m) && fits(m, r.out.get())) {
                r.take.accept(m);
                ItemStack out = r.out.get(), there = m.inv.getItem(RESULT);
                if (isEmpty(there)) m.inv.setItem(RESULT, out); else there.setAmount(there.getAmount() + out.getAmount());
                c.getWorld().playSound(c, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, .8f, 1.4f);
                c.getWorld().playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                c.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, c, 20, .2, .3, .2, .02);
                p.sendActionBar(Component.text("Neither his nor His.", TextColor.color(0x9FD4FF), TextDecoration.ITALIC));
            }
            refresh(m);
        }, 2L, 2L);
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, .8f, 1.2f);
    }

    private static boolean isEmpty(ItemStack it) { return it == null || it.getType().isAir() || it.getAmount() <= 0; }
}
