package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The Bureau's goods. Each is an ordinary item underneath (paper, a chest, a bundle) with the Bureau's mark and a
 * picture from the pack (bureau:<id>). What they do:
 *
 *  settlement_papers   right-click: up to 60 minutes off Moloch's Curse. Once a day, never mid-fight. Three in a week
 *                      may get you audited.
 *  life_insurance      carried when you die: you keep everything. Used up. Void while cursed by Moloch.
 *  pet_insurance       right-click your tamed pet: if it dies, it comes back at your home once.
 *  strongbox           a chest only its owner can open or break; hoppers and explosions can't touch it.
 *  tax_receipt         carried when the Taxman catches you: he reads it and leaves for the day. Used up.
 *  briefcase           a bundle that stays with you when you die (thieves can't take it).
 *  lift_pass           right-click: the lift down to the Bureau from anywhere. Once a day; not from Moloch's realm.
 *  sunglasses          a hat; Darkness lasts half as long on you.
 *  market_report       right-click: which way Cold Hard Cash will move at the next half hour (right 70% of the time).
 *  expense_card        right-click: 24 hours of a 3% Exchange cut instead of 5%.
 *  accountants_ledger  carried: profit tax 7% instead of 10%.
 *  pink_slip           right-click a player: a fake YOU'RE FIRED on their screen, signed by you.
 *  desk_lizard         right-click: a little lizard rides on your shoulder (others see it), and mutters office lines.
 *  golden_stapler      does nothing. The server hears when you buy one.
 *  fun_ticket          right-click: a five-minute party at the Bureau; everyone there gets a little XP.
 */
final class BureauItems implements Listener {
    private static final TextColor GOLD = TextColor.color(0xF0C85A), RED = TextColor.color(0xE05050), GREEN = TextColor.color(0x7BD36A), ICE = Cash.ICE;

    record Def(String id, Material base, String name, TextColor color, int stack, List<String> lore) {}

    static final Map<String, Def> DEFS = new LinkedHashMap<>();
    private static void def(String id, Material base, String name, TextColor c, int stack, String... lore) { DEFS.put(id, new Def(id, base, name, c, stack, List.of(lore))); }
    static {
        def("settlement_papers", Material.PAPER, "Settlement Papers", RED, 16, "An out-of-court settlement with Moloch.", "Right-click: up to 60 minutes off his Curse.", "Once a day. Not during his fight.");
        def("life_insurance", Material.PAPER, "Life Insurance Policy", GOLD, 1, "Carry it when you die and you keep everything.", "Used up when it pays out.", "Void while cursed by Moloch.");
        def("pet_insurance", Material.PAPER, "Pet Insurance", GOLD, 16, "Right-click your tamed pet to insure it.", "If it dies, it comes back at your home. Once.");
        def("strongbox", Material.CHEST, "Strongbox", GOLD, 64, "A chest only you can open or break.", "Hoppers and explosions can't touch it.", "Must stand alone (no double chests).");
        def("tax_receipt", Material.PAPER, "Tax Receipt", GREEN, 16, "Carry it: if the Taxman catches you,", "he reads it and leaves for the day.", "Doesn't clear what you owe. Used up.");
        def("briefcase", Material.BUNDLE, "Bureau Briefcase", GOLD, 1, "Holds a bundle's worth of things.", "Stays with you when you die:", "thieves can't take it.");
        def("lift_pass", Material.PAPER, "Lift Pass", GOLD, 1, "Right-click: ride the lift down to the Bureau", "from anywhere. Once a day.", "Not from Moloch's realm.");
        def("sunglasses", Material.PAPER, "Sunglasses", NamedTextColor.WHITE, 1, "Wear them: Darkness lasts half as long.", "Looks expensive.");
        def("market_report", Material.PAPER, "Market Report", NamedTextColor.WHITE, 16, "Right-click: where Cold Hard Cash goes", "at the next half hour. Right most of the time.");
        def("expense_card", Material.PAPER, "Expense Account Card", ICE, 16, "Right-click: for 24 hours the Exchange", "takes 3% instead of 5%.");
        def("accountants_ledger", Material.PAPER, "Accountant's Ledger", GREEN, 1, "Carry it: profit tax is 7% instead of 10%.", "The lizards admire good bookkeeping.");
        def("pink_slip", Material.PAPER, "Pink Slip", TextColor.color(0xF5A0B8), 16, "Right-click a friend: they're FIRED.", "(Not really. Signed by you.)");
        def("desk_lizard", Material.PAPER, "Desk Lizard", GREEN, 1, "Right-click: he rides on your shoulder.", "Right-click again: he goes back in your pocket.", "Wears a tiny tie.");
        def("golden_stapler", Material.PAPER, "Golden Stapler", GOLD, 1, "It does nothing.", "Everyone knows you have one.");
        def("fun_ticket", Material.PAPER, "Mandatory Fun Ticket", TextColor.color(0xFFD54F), 16, "Right-click: a five-minute party at the Bureau.", "Everyone who turns up gets a little XP.", "Attendance will be noted.");
    }

    private final Bureau plugin;
    private final NamespacedKey idKey, strongboxKey, petKey;
    private final Map<UUID, List<Long>> settlements = new HashMap<>();
    private final Map<UUID, Long> lifeBought = new HashMap<>(), liftUsed = new HashMap<>(), expenseUntil = new HashMap<>();
    private final Set<UUID> lizardOn = new HashSet<>();
    private final Map<UUID, ItemDisplay> lizards = new HashMap<>();
    private final Map<UUID, List<ItemStack>> keepOnRespawn = new HashMap<>();
    private long lastParty, partyUntil;
    private Location partyAt;
    private boolean darknessFix;
    private int ticks;

    BureauItems(Bureau plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "item");
        this.strongboxKey = new NamespacedKey(plugin, "strongbox_owner");
        this.petKey = new NamespacedKey(plugin, "pet_insured");
    }

    // ---------------------------------------------------------------- making and recognising them

    ItemStack make(String id, int n) {
        Def d = DEFS.get(id);
        if (d == null) return null;
        ItemStack it = new ItemStack(d.base, Math.max(1, Math.min(d.stack, n)));
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(d.name, d.color).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        for (String l : d.lore) lore.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("The Bureau of Cold-Blooded Affairs", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        m.lore(lore);
        m.setItemModel(new NamespacedKey("bureau", id));
        if (d.base != Material.CHEST) m.setMaxStackSize(d.stack);
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        if (id.equals("sunglasses")) {
            EquippableComponent eq = m.getEquippable();
            eq.setSlot(EquipmentSlot.HEAD);
            eq.setModel(null);
            m.setEquippable(eq);
        }
        it.setItemMeta(m);
        return it;
    }

    String id(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    boolean carrying(Player p, String id) {
        for (ItemStack it : p.getInventory().getContents()) if (id.equals(id(it))) return true;
        return false;
    }

    /** Use up one from anywhere in their inventory. */
    boolean takeOne(Player p, String id) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (!id.equals(id(inv[i]))) continue;
            if (inv[i].getAmount() > 1) inv[i].setAmount(inv[i].getAmount() - 1); else inv[i] = null;
            p.getInventory().setContents(inv);
            return true;
        }
        return false;
    }

    private void useHand(Player p, EquipmentSlot hand) {
        ItemStack it = hand == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1);
        else if (hand == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(null);
        else p.getInventory().setItemInMainHand(null);
    }

    /** The Exchange cut for this player right now. */
    double spread(Player p) {
        double base = plugin.getConfig().getDouble("exchange.spread", 0.05);
        return expenseUntil.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis() ? Math.min(base, plugin.getConfig().getDouble("items.expense-spread", 0.03)) : base;
    }

    /** Can they buy this from the shop right now? Null if yes, the reason if not. */
    String canBuy(Player p, String id) {
        if (id.equals("life_insurance")) {
            if (carrying(p, id)) return "You already carry a policy. One at a time.";
            long wait = (long) (plugin.getConfig().getDouble("items.life-insurance-days", 3) * 86_400_000L);
            long since = System.currentTimeMillis() - lifeBought.getOrDefault(p.getUniqueId(), 0L);
            if (since < wait) return "Ssal won't sell you another policy for " + ((wait - since) / 3_600_000L + 1) + " hours.";
        }
        return null;
    }

    void bought(Player p, String id) {
        if (id.equals("life_insurance")) { lifeBought.put(p.getUniqueId(), System.currentTimeMillis()); plugin.dirty(); }
        if (id.equals("golden_stapler"))
            Bukkit.broadcast(Component.text("✦ ", GOLD).append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" bought a Golden Stapler. It does nothing. They are very rich.", GOLD)));
    }

    private static void line(Player p, String who, TextColor c, String text) {
        p.sendMessage(Component.text("<", NamedTextColor.DARK_GRAY).append(Component.text(who, c)).append(Component.text("> ", NamedTextColor.DARK_GRAY))
                .append(Component.text(text, NamedTextColor.WHITE)));
    }

    private static void no(Player p, String msg) { p.sendActionBar(Component.text(msg, RED)); p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.5f, 0.8f); }

    // ---------------------------------------------------------------- right-click uses

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() == null) return;
        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        String id = id(it);
        if (id == null) return;
        switch (id) {
            case "strongbox", "briefcase", "sunglasses" -> { return; }   // placing, bundle use and wearing work as normal
            default -> e.setCancelled(true);
        }
        switch (id) {
            case "settlement_papers" -> settle(p, e.getHand());
            case "lift_pass" -> liftPass(p);
            case "market_report" -> { useHand(p, e.getHand()); report(p); }
            case "expense_card" -> {
                useHand(p, e.getHand());
                long now = System.currentTimeMillis(), until = Math.max(now, expenseUntil.getOrDefault(p.getUniqueId(), 0L)) + 86_400_000L;
                expenseUntil.put(p.getUniqueId(), until);
                plugin.dirty();
                line(p, "Mr. Coldwell", GREEN, "Expense account opened. 3% at the Exchange until this time tomorrow. Don't tell the Director.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.6f);
            }
            case "desk_lizard" -> toggleLizard(p);
            case "golden_stapler" -> { p.playSound(p.getLocation(), Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.8f, 1.8f); p.sendActionBar(Component.text("*ka-chunk*", GOLD)); }
            case "fun_ticket" -> party(p, e.getHand());
            case "pink_slip" -> p.sendActionBar(Component.text("Right-click a player to fire them. (Not really.)", NamedTextColor.GRAY));
            case "pet_insurance" -> p.sendActionBar(Component.text("Right-click your tamed pet to insure it.", NamedTextColor.GRAY));
            default -> { }
        }
    }

    private void settle(Player p, EquipmentSlot hand) {
        int left = SoulboundHook.curseSeconds(p);
        if (left <= 0) { no(p, "You're not cursed. The Bureau doesn't settle hypotheticals."); return; }
        if (SoulboundHook.molochFighting()) { no(p, "Nobody negotiates with Moloch mid-rampage."); return; }
        List<Long> times = settlements.computeIfAbsent(p.getUniqueId(), k -> new ArrayList<>());
        long now = System.currentTimeMillis();
        times.removeIf(t -> now - t > 7 * 86_400_000L);
        if (!times.isEmpty() && now - times.get(times.size() - 1) < plugin.getConfig().getDouble("items.settle-cooldown-hours", 20) * 3_600_000L) {
            no(p, "One settlement a day. Moloch's lawyers need time."); return;
        }
        int cut = plugin.getConfig().getInt("items.settle-minutes", 60) * 60;
        if (!SoulboundHook.reduceCurse(p, cut)) { no(p, "The settlement didn't go through (Soulbound isn't answering)."); return; }
        useHand(p, hand);
        times.add(now);
        plugin.dirty();
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.6f);
        p.playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.35f, 0.5f);
        line(p, "Settlements", GREEN, left <= cut ? "Settled. Moloch has been compensated. Your curse is lifted."
                : "Settled in part. " + (cut / 60) + " minutes taken off. Moloch remains... aggrieved.");
        // three in a week: the Bureau starts asking where the money came from
        if (times.size() >= 3 && ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("items.audit-chance", 0.25)) {
            double fine = plugin.getConfig().getDouble("items.audit-fine", 50);
            plugin.taxes().add(p.getUniqueId(), fine);
            p.sendMessage(Component.text("✎ ", RED).append(Component.text("The Bureau has opened an audit into your frequent settlements. Fine: "
                    + Money.fmt(fine) + ". ", RED)).append(plugin.taxes().payButton()));
        }
    }

    private void liftPass(Player p) {
        Location to = plugin.lift().bottom;
        if (to == null) { no(p, "The lift isn't running yet."); return; }
        if (plugin.security().fired(p.getUniqueId())) { no(p, "Security won't let you down. You were fired."); return; }
        if (inMolochRealm(p)) { no(p, "The lift doesn't reach Moloch's realm."); return; }
        long now = System.currentTimeMillis(), last = liftUsed.getOrDefault(p.getUniqueId(), 0L);
        long cool = (long) (plugin.getConfig().getDouble("items.lift-pass-hours", 20) * 3_600_000L);
        if (now - last < cool) { no(p, "Your pass works again in " + ((cool - (now - last)) / 3_600_000L + 1) + "h."); return; }
        liftUsed.put(p.getUniqueId(), now);
        plugin.dirty();
        plugin.lift().ride(p, to, true);
    }

    private static boolean inMolochRealm(Player p) {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) return false;
        try { return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(p, "%soulbound_realm%").toLowerCase(java.util.Locale.ROOT).contains("moloch"); }
        catch (Throwable t) { return false; }
    }

    private void report(Player p) {
        Exchange ex = plugin.exchange();
        String dir;
        if (!ex.event.isEmpty()) dir = ex.event.equals("RALLY") ? "RISE" : "FALL";
        else {
            double next = ex.nextMove();
            boolean truth = ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("items.report-accuracy", 0.7);
            boolean up = truth == (next >= 0);
            dir = up ? "RISE" : "FALL";
        }
        p.sendMessage(Component.text("✉ MARKET REPORT: ", GOLD).append(Component.text("Cold Hard Cash looks set to ", NamedTextColor.GRAY))
                .append(Component.text(dir, dir.equals("RISE") ? NamedTextColor.GREEN : RED, TextDecoration.BOLD))
                .append(Component.text(" at the next half hour. (Now " + Money.fmt(ex.price) + ". The Bureau accepts no liability.)", NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
    }

    // ---------------------------------------------------------------- right-clicking a creature: pets and pink slips

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseOn(PlayerInteractEntityEvent e) {
        Player p = e.getPlayer();
        ItemStack it = e.getHand() == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        String id = id(it);
        if (id == null) return;
        Entity t = e.getRightClicked();
        if (id.equals("pet_insurance")) {
            e.setCancelled(true);
            if (!(t instanceof Tameable pet) || pet.getOwner() == null || !pet.getOwner().getUniqueId().equals(p.getUniqueId())) { no(p, "That's not your pet."); return; }
            if (pet.getPersistentDataContainer().has(petKey, PersistentDataType.STRING)) { no(p, "Already insured."); return; }
            useHand(p, e.getHand());
            pet.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, p.getUniqueId().toString());
            line(p, "Ssal", GREEN, "Insured. Accidents happen. Especially to the uninsured.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.3f);
        } else if (id.equals("pink_slip") && t instanceof Player victim) {
            e.setCancelled(true);
            useHand(p, e.getHand());
            victim.showTitle(Title.title(Component.text("YOU'RE FIRED", RED, TextDecoration.BOLD), Component.text("Signed, " + p.getName(), NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofMillis(2500), Duration.ofMillis(600))));
            victim.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) victim.sendActionBar(Component.text("...just kidding. A Pink Slip from " + p.getName() + ".", TextColor.color(0xF5A0B8)));
            }, 70L);
            p.sendActionBar(Component.text("You fired " + victim.getName() + ". (Not really.)", TextColor.color(0xF5A0B8)));
        }
    }

    @EventHandler
    public void onPetDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        String owner = dead.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (owner == null || !(dead instanceof Tameable)) return;
        EntitySnapshot snap = dead.createSnapshot();
        if (snap == null) return;
        UUID oid;
        try { oid = UUID.fromString(owner); } catch (IllegalArgumentException ex) { return; }
        var op = Bukkit.getOfflinePlayer(oid);
        Location home = op.getRespawnLocation();
        if (home == null) home = Bukkit.getWorlds().get(0).getSpawnLocation();
        Location at = home.clone();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.spawning = true;
            Entity back;
            try { back = snap.createEntity(at); } finally { plugin.spawning = false; }
            if (back instanceof LivingEntity le) {
                var hp = le.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (hp != null) le.setHealth(hp.getValue());
                le.getPersistentDataContainer().remove(petKey);   // one claim per policy
                le.setFireTicks(0);
            }
            Player p = Bukkit.getPlayer(oid);
            if (p != null) line(p, "Ssal", GREEN, "Your pet's claim has been paid. They're waiting for you at home.");
        }, 100L);
    }

    // ---------------------------------------------------------------- deaths: life insurance, the briefcase

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        if (e.getKeepInventory()) return;
        if (carrying(p, "life_insurance")) {
            if (SoulboundHook.curseSeconds(p) > 0) {
                p.sendMessage(Component.text("Your Life Insurance is void: you were cursed by Moloch. Read the small print.", RED));
            } else {
                takeOne(p, "life_insurance");
                e.setKeepInventory(true);
                e.getDrops().clear();
                Bukkit.getScheduler().runTask(plugin, () -> line(p, "Ssal", GREEN, "Your policy has paid out. Everything you carried is still with you."));
                return;
            }
        }
        List<ItemStack> keep = new ArrayList<>();
        e.getDrops().removeIf(it -> { if ("briefcase".equals(id(it))) { keep.add(it); return true; } return false; });
        if (!keep.isEmpty()) keepOnRespawn.put(p.getUniqueId(), keep);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        List<ItemStack> keep = keepOnRespawn.remove(e.getPlayer().getUniqueId());
        if (keep == null) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (ItemStack it : keep) for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        });
    }

    // ---------------------------------------------------------------- the strongbox

    private UUID boxOwner(Block b) {
        if (!(b.getState() instanceof Chest c)) return null;
        String s = c.getPersistentDataContainer().get(strongboxKey, PersistentDataType.STRING);
        try { return s == null ? null : UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }

    private boolean nextToChest(Block b) {
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Material t = b.getRelative(f).getType();
            if (t == Material.CHEST || t == Material.TRAPPED_CHEST) return true;
        }
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Block b = e.getBlockPlaced();
        boolean strongbox = "strongbox".equals(id(e.getItemInHand()));
        if (strongbox) {
            if (nextToChest(b)) { e.setCancelled(true); no(e.getPlayer(), "A Strongbox must stand alone."); return; }
            if (b.getState() instanceof Chest c) {
                c.getPersistentDataContainer().set(strongboxKey, PersistentDataType.STRING, e.getPlayer().getUniqueId().toString());
                c.customName(Component.text(e.getPlayer().getName() + "'s Strongbox", GOLD));
                c.update();
            }
            return;
        }
        if (b.getType() == Material.CHEST || b.getType() == Material.TRAPPED_CHEST)
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
                if (boxOwner(b.getRelative(f)) != null) { e.setCancelled(true); no(e.getPlayer(), "You can't join a chest onto a Strongbox."); return; }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onOpenBox(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        UUID owner = boxOwner(e.getClickedBlock());
        if (owner == null || owner.equals(e.getPlayer().getUniqueId()) || e.getPlayer().hasPermission("bureau.admin")) return;
        e.setCancelled(true);
        no(e.getPlayer(), "Locked. This Strongbox isn't yours.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakBox(BlockBreakEvent e) {
        UUID owner = boxOwner(e.getBlock());
        if (owner == null) return;
        if (!owner.equals(e.getPlayer().getUniqueId()) && !e.getPlayer().hasPermission("bureau.admin")) { e.setCancelled(true); no(e.getPlayer(), "This Strongbox isn't yours."); return; }
        // it drops as a Strongbox again, not a plain chest
        e.setDropItems(false);
        if (e.getBlock().getState() instanceof Chest c) {
            for (ItemStack it : c.getBlockInventory().getContents()) if (it != null) e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), it);
            c.getBlockInventory().clear();
        }
        e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), make("strongbox", 1));
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> boxOwner(b) != null); }

    @EventHandler(ignoreCancelled = true)
    public void onHopper(InventoryMoveItemEvent e) {
        if (e.getSource().getLocation() != null && boxOwner(e.getSource().getLocation().getBlock()) != null) e.setCancelled(true);
    }

    // ---------------------------------------------------------------- sunglasses

    @EventHandler(ignoreCancelled = true)
    public void onDarkness(EntityPotionEffectEvent e) {
        if (darknessFix || !(e.getEntity() instanceof Player p) || e.getNewEffect() == null) return;
        if (!e.getNewEffect().getType().equals(PotionEffectType.DARKNESS)) return;
        if (!"sunglasses".equals(id(p.getInventory().getHelmet()))) return;
        PotionEffect half = new PotionEffect(PotionEffectType.DARKNESS, Math.max(20, e.getNewEffect().getDuration() / 2), e.getNewEffect().getAmplifier(),
                e.getNewEffect().isAmbient(), e.getNewEffect().hasParticles(), e.getNewEffect().hasIcon());
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> { darknessFix = true; try { p.addPotionEffect(half); } finally { darknessFix = false; } });
    }

    // ---------------------------------------------------------------- the desk lizard

    private void toggleLizard(Player p) {
        if (lizardOn.remove(p.getUniqueId())) {
            ItemDisplay d = lizards.remove(p.getUniqueId());
            if (d != null) d.remove();
            p.sendActionBar(Component.text("Your desk lizard climbs back into your pocket.", GREEN));
        } else {
            lizardOn.add(p.getUniqueId());
            spawnLizard(p);
            p.sendActionBar(Component.text("Your desk lizard climbs onto your shoulder.", GREEN));
            p.playSound(p.getLocation(), Sound.ENTITY_FROG_AMBIENT, 0.6f, 1.6f);
        }
        plugin.dirty();
    }

    private void spawnLizard(Player p) {
        ItemDisplay old = lizards.remove(p.getUniqueId());
        if (old != null) old.remove();
        plugin.spawning = true;
        try {
            ItemDisplay d = p.getWorld().spawn(shoulder(p), ItemDisplay.class, x -> {
                x.setItemStack(make("desk_lizard", 1));
                x.setPersistent(false);
                x.setBillboard(Display.Billboard.VERTICAL);
                x.setTeleportDuration(2);
                x.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.45f, 0.45f, 0.45f), new Quaternionf()));
            });
            p.hideEntity(plugin, d);   // the owner doesn't need him in their face
            lizards.put(p.getUniqueId(), d);
        } finally { plugin.spawning = false; }
    }

    private static Location shoulder(Player p) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(p.getBodyYaw());
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        return l.clone().add(right.multiply(0.42)).add(0, p.isSneaking() ? 1.15 : 1.42, 0);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { if (lizardOn.contains(e.getPlayer().getUniqueId())) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) spawnLizard(e.getPlayer()); }, 40L); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { ItemDisplay d = lizards.remove(e.getPlayer().getUniqueId()); if (d != null) d.remove(); }

    private static final List<String> MUTTERS = List.of("Is it Friday yet?", "I need that report by five.", "Has anyone seen my stapler?",
            "Synergy.", "Per my last email...", "Let's circle back.", "*hisses at the printer*", "The coffee here is cold. Perfect.");

    // ---------------------------------------------------------------- the party

    private void party(Player p, EquipmentSlot hand) {
        long now = System.currentTimeMillis();
        if (now < partyUntil) { no(p, "There's already a party on."); return; }
        long cool = (long) (plugin.getConfig().getDouble("items.party-cooldown-minutes", 60) * 60_000L);
        if (now - lastParty < cool) { no(p, "Fun is rationed. The next party can start in " + ((cool - (now - lastParty)) / 60_000L + 1) + " minutes."); return; }
        useHand(p, hand);
        lastParty = now;
        partyUntil = now + (long) (plugin.getConfig().getDouble("items.party-minutes", 5) * 60_000L);
        partyAt = plugin.lift().bottom != null ? plugin.lift().bottom.clone() : p.getLocation().clone();
        Bukkit.broadcast(Component.text("♫ MANDATORY FUN at the Bureau! ", TextColor.color(0xFFD54F), TextDecoration.BOLD)
                .append(Component.text("Thrown by " + p.getName() + ". Five minutes. Attendance will be noted. ", NamedTextColor.GRAY))
                .append(Component.text("[Go to the party]", GREEN, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/bureau party"))));
    }

    /** /bureau party: ride the lift down while a party's on. */
    void goParty(Player p) {
        if (System.currentTimeMillis() > partyUntil || partyAt == null) { no(p, "There's no party right now."); return; }
        if (plugin.security().fired(p.getUniqueId())) { no(p, "Security won't let you in. You were fired."); return; }
        if (inMolochRealm(p) || plugin.taxman().hunting(p.getUniqueId())) { no(p, "You can't leave right now."); return; }
        plugin.lift().ride(p, partyAt, true);
    }

    private void partyTick() {
        if (partyAt == null) return;
        long now = System.currentTimeMillis();
        if (now > partyUntil) {
            // the end: XP for everyone who turned up
            int xp = plugin.getConfig().getInt("items.party-xp", 150);
            for (Player p : partyAt.getWorld().getPlayers()) if (p.getLocation().distanceSquared(partyAt) < 40 * 40) {
                p.giveExp(xp);
                p.sendMessage(Component.text("♫ The party's over. Your attendance has been noted (+" + xp + " XP).", TextColor.color(0xFFD54F)));
            }
            partyAt = null;
            return;
        }
        if (ticks % 40 == 0) {
            ThreadLocalRandom r = ThreadLocalRandom.current();
            Location l = partyAt.clone().add(r.nextDouble(-6, 6), 1, r.nextDouble(-6, 6));
            plugin.spawning = true;
            try {
                Firework fw = l.getWorld().spawn(l, Firework.class, f -> {
                    FireworkMeta m = f.getFireworkMeta();
                    m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL).withColor(Color.fromRGB(0x7BD36A), Color.fromRGB(0xF0C85A), Color.fromRGB(0xE05050)).withFlicker().build());
                    m.setPower(0);
                    f.setFireworkMeta(m);
                });
                Bukkit.getScheduler().runTaskLater(plugin, fw::detonate, 12L);
            } finally { plugin.spawning = false; }
            Sound[] notes = {Sound.BLOCK_NOTE_BLOCK_BIT, Sound.BLOCK_NOTE_BLOCK_BELL, Sound.BLOCK_NOTE_BLOCK_CHIME};
            for (Player p : partyAt.getWorld().getPlayers()) if (p.getLocation().distanceSquared(partyAt) < 40 * 40)
                p.playSound(p.getLocation(), notes[r.nextInt(notes.length)], 0.6f, (float) (0.6 + r.nextInt(12) / 10.0));
        }
    }

    // ---------------------------------------------------------------- every tick

    void tick() {
        ticks++;
        for (var en : List.copyOf(lizards.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            ItemDisplay d = en.getValue();
            if (p == null || !d.isValid()) { lizards.remove(en.getKey()); continue; }
            if (d.getWorld() != p.getWorld()) { spawnLizard(p); continue; }
            d.teleport(shoulder(p));
            if (ticks % 12_000 == Math.floorMod(en.getKey().hashCode(), 1000) + 6000)
                p.sendActionBar(Component.text("Desk Lizard: \"" + MUTTERS.get(ThreadLocalRandom.current().nextInt(MUTTERS.size())) + "\"", GREEN));
        }
        partyTick();
    }

    void endAll() { for (ItemDisplay d : lizards.values()) d.remove(); lizards.clear(); }

    /** Bureau goods are paper underneath: no folding them into books and maps either. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) if (id(it) != null && !"strongbox".equals(id(it))) { e.getInventory().setResult(null); return; }
    }

    // ---------------------------------------------------------------- saving

    void load(ConfigurationSection d) {
        settlements.clear(); lifeBought.clear(); liftUsed.clear(); expenseUntil.clear(); lizardOn.clear();
        if (d == null) return;
        ConfigurationSection s = d.getConfigurationSection("settlements");
        if (s != null) for (String k : s.getKeys(false)) try { settlements.put(UUID.fromString(k), new ArrayList<>(s.getLongList(k))); } catch (IllegalArgumentException ignored) {}
        loadTimes(d.getConfigurationSection("life-bought"), lifeBought);
        loadTimes(d.getConfigurationSection("lift-used"), liftUsed);
        loadTimes(d.getConfigurationSection("expense-until"), expenseUntil);
        for (String k : d.getStringList("lizard-on")) try { lizardOn.add(UUID.fromString(k)); } catch (IllegalArgumentException ignored) {}
        lastParty = d.getLong("last-party");
    }

    private static void loadTimes(ConfigurationSection s, Map<UUID, Long> into) {
        if (s != null) for (String k : s.getKeys(false)) try { into.put(UUID.fromString(k), s.getLong(k)); } catch (IllegalArgumentException ignored) {}
    }

    void save(ConfigurationSection d) {
        ConfigurationSection s = d.createSection("settlements");
        for (var e : settlements.entrySet()) if (!e.getValue().isEmpty()) s.set(e.getKey().toString(), e.getValue());
        saveTimes(d.createSection("life-bought"), lifeBought);
        saveTimes(d.createSection("lift-used"), liftUsed);
        saveTimes(d.createSection("expense-until"), expenseUntil);
        List<String> on = new ArrayList<>(); for (UUID u : lizardOn) on.add(u.toString());
        d.set("lizard-on", on);
        d.set("last-party", lastParty);
    }

    private static void saveTimes(ConfigurationSection s, Map<UUID, Long> from) { for (var e : from.entrySet()) s.set(e.getKey().toString(), e.getValue()); }
}
