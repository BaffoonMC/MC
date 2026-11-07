package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The Market (/market): players sell to each other without trusting each other. A listed item is held by the Market
 * the moment it's listed, so buyers get exactly what they see; the money goes straight to the seller, online or not.
 * Wanted requests work the other way round: the buyer's money is held up front and anyone can fill them.
 * Unsold things, things bought with a full inventory, and money that couldn't be paid wait in your collection.
 */
final class Market implements Listener, CommandExecutor, TabCompleter {
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int PER_PAGE = 45;

    private static final class Listing { String id; UUID seller; String sellerName; ItemStack unit; int qty; double each; long at; }
    private static final class Wanted { String id; UUID buyer; String buyerName; ItemStack unit; int want, got; double each; long at; }
    private enum Mode { HOME, MAIN, ITEM, WANTED, MINE }
    private static final class View implements InventoryHolder {
        final Mode mode; final int page; final String key, filter; Inventory inv;
        final Map<Integer, String> act = new HashMap<>();
        View(Mode mode, int page, String key, String filter) { this.mode = mode; this.page = page; this.key = key; this.filter = filter; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final Soulbound plugin;
    private final File file;
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<String, Wanted> wanted = new LinkedHashMap<>();
    private final Map<UUID, List<ItemStack>> collection = new HashMap<>();
    private final Map<UUID, Double> owed = new HashMap<>();
    private final Map<UUID, List<String>> notes = new HashMap<>();
    private final Map<UUID, Long> searching = new HashMap<>();
    private final Map<UUID, long[]> confirm = new HashMap<>();
    private final Map<UUID, Integer> category = new HashMap<>();   // which shelf each player is browsing (0 = everything)

    // ---------------------------------------------------------------- shelves: tools, armour, blocks...
    private static final String[] CATS = {"Everything", "Blocks", "Tools & Weapons", "Armour", "Food", "Potions & Books", "Materials & Other"};
    private static final Material[] CAT_ICONS = {Material.CHEST, Material.BRICKS, Material.IRON_PICKAXE, Material.IRON_CHESTPLATE, Material.BREAD, Material.POTION, Material.IRON_INGOT};

    private static int catOf(ItemStack it) {
        Material m = it.getType();
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || n.endsWith("_HORSE_ARMOR")
                || m == Material.ELYTRA || m == Material.SHIELD || m == Material.TURTLE_HELMET || m == Material.WOLF_ARMOR) return 3;
        if (n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_SPEAR")
                || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT || m == Material.MACE || m == Material.FISHING_ROD
                || m == Material.SHEARS || m == Material.FLINT_AND_STEEL || m == Material.SPYGLASS || m == Material.BRUSH || n.endsWith("ARROW")) return 2;
        if (m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION || m == Material.ENCHANTED_BOOK
                || m == Material.EXPERIENCE_BOTTLE || m == Material.BOOK || m == Material.WRITTEN_BOOK) return 5;
        if (m.isEdible()) return 4;
        if (m.isBlock()) return 1;
        return 6;
    }

    Market(Soulbound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "market.yml");
        load();
    }

    void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::expire, 20L * 60, 20L * 600); }

    // ---------------------------------------------------------------- settings

    private double fee() { return Math.max(0, Math.min(50, plugin.getConfig().getDouble("market.fee-percent", 2))) / 100.0; }
    private int days() { return Math.max(1, plugin.getConfig().getInt("market.listing-days", 7)); }
    private int maxListings() { return Math.max(1, plugin.getConfig().getInt("market.max-listings", 10)); }
    private int maxWanted() { return Math.max(1, plugin.getConfig().getInt("market.max-wanted", 5)); }

    // ---------------------------------------------------------------- storage

    private static String enc(ItemStack it) { return Base64.getEncoder().encodeToString(it.serializeAsBytes()); }
    private static ItemStack dec(Object s) { return ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(s))); }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        listings.clear(); wanted.clear(); collection.clear(); owed.clear(); notes.clear();
        for (Map<?, ?> m : y.getMapList("listings")) try {
            Listing l = new Listing();
            l.id = String.valueOf(m.get("id")); l.seller = UUID.fromString(String.valueOf(m.get("seller"))); l.sellerName = String.valueOf(m.get("name"));
            l.unit = dec(m.get("item")); l.qty = ((Number) m.get("qty")).intValue(); l.each = ((Number) m.get("each")).doubleValue(); l.at = ((Number) m.get("at")).longValue();
            listings.put(l.id, l);
        } catch (RuntimeException ex) { plugin.getLogger().warning("Market: skipped a listing that couldn't be read: " + ex.getMessage()); }
        for (Map<?, ?> m : y.getMapList("wanted")) try {
            Wanted w = new Wanted();
            w.id = String.valueOf(m.get("id")); w.buyer = UUID.fromString(String.valueOf(m.get("buyer"))); w.buyerName = String.valueOf(m.get("name"));
            w.unit = dec(m.get("item")); w.want = ((Number) m.get("want")).intValue(); w.got = ((Number) m.get("got")).intValue();
            w.each = ((Number) m.get("each")).doubleValue(); w.at = ((Number) m.get("at")).longValue();
            wanted.put(w.id, w);
        } catch (RuntimeException ex) { plugin.getLogger().warning("Market: skipped a wanted request that couldn't be read: " + ex.getMessage()); }
        var cs = y.getConfigurationSection("collection");
        if (cs != null) for (String k : cs.getKeys(false)) try {
            List<ItemStack> l = new ArrayList<>();
            for (Object o : cs.getList(k, List.of())) l.add(dec(o));
            collection.put(UUID.fromString(k), l);
        } catch (RuntimeException ignored) {}
        var os = y.getConfigurationSection("owed");
        if (os != null) for (String k : os.getKeys(false)) try { owed.put(UUID.fromString(k), os.getDouble(k)); } catch (RuntimeException ignored) {}
        var ns = y.getConfigurationSection("notes");
        if (ns != null) for (String k : ns.getKeys(false)) try { notes.put(UUID.fromString(k), new ArrayList<>(ns.getStringList(k))); } catch (RuntimeException ignored) {}
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> ls = new ArrayList<>();
        for (Listing l : listings.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.id); m.put("seller", l.seller.toString()); m.put("name", l.sellerName); m.put("item", enc(l.unit));
            m.put("qty", l.qty); m.put("each", l.each); m.put("at", l.at);
            ls.add(m);
        }
        y.set("listings", ls);
        List<Map<String, Object>> ws = new ArrayList<>();
        for (Wanted w : wanted.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", w.id); m.put("buyer", w.buyer.toString()); m.put("name", w.buyerName); m.put("item", enc(w.unit));
            m.put("want", w.want); m.put("got", w.got); m.put("each", w.each); m.put("at", w.at);
            ws.add(m);
        }
        y.set("wanted", ws);
        for (var e : collection.entrySet()) if (!e.getValue().isEmpty()) y.set("collection." + e.getKey(), e.getValue().stream().map(Market::enc).toList());
        for (var e : owed.entrySet()) if (e.getValue() > 0) y.set("owed." + e.getKey(), e.getValue());
        for (var e : notes.entrySet()) if (!e.getValue().isEmpty()) y.set("notes." + e.getKey(), e.getValue());
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Couldn't save market.yml: " + ex.getMessage()); }
    }

    // ---------------------------------------------------------------- money, items and messages

    private void pay(UUID who, String name, double amount, String why) {
        if (amount <= 0) return;
        if (!Money.give(who, amount)) owed.merge(who, amount, Double::sum);
        tell(who, why);
    }

    private void tell(UUID who, String text) {
        Player p = Bukkit.getPlayer(who);
        if (p != null) {
            p.sendMessage(Component.text("Market ", GOLD, TextDecoration.BOLD).append(Component.text("> ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false))
                    .append(Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.BOLD, false)));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        } else {
            List<String> l = notes.computeIfAbsent(who, k -> new ArrayList<>());
            l.add(text);
            while (l.size() > 20) l.remove(0);
        }
    }

    /** Give them items: into their inventory, anything that doesn't fit into their collection. True if all fitted. */
    private boolean deliver(UUID who, ItemStack unit, int qty) {
        List<ItemStack> stacks = stacks(unit, qty);
        Player p = Bukkit.getPlayer(who);
        boolean all = true;
        for (ItemStack s : stacks) {
            if (p != null) for (ItemStack left : p.getInventory().addItem(s).values()) { collection.computeIfAbsent(who, k -> new ArrayList<>()).add(left); all = false; }
            else { collection.computeIfAbsent(who, k -> new ArrayList<>()).add(s); all = false; }
        }
        return all;
    }

    private static List<ItemStack> stacks(ItemStack unit, int qty) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, unit.getMaxStackSize());
        while (qty > 0) { ItemStack s = unit.clone(); int n = Math.min(max, qty); s.setAmount(n); out.add(s); qty -= n; }
        return out;
    }

    private static int count(Player p, ItemStack unit) {
        int n = 0;
        for (int i = 0; i < 36; i++) { ItemStack it = p.getInventory().getItem(i); if (it != null && it.isSimilar(unit)) n += it.getAmount(); }
        return n;
    }

    private static void removeItems(Player p, ItemStack unit, int qty) {
        for (int i = 0; i < 36 && qty > 0; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || !it.isSimilar(unit)) continue;
            int take = Math.min(qty, it.getAmount());
            it.setAmount(it.getAmount() - take);
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
            qty -= take;
        }
    }

    private static Component name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return m != null && m.hasDisplayName() && m.displayName() != null ? m.displayName() : Component.translatable(it.translationKey());
    }
    private static String plainName(ItemStack it) { return PLAIN.serialize(name(it)); }
    private static String key(ItemStack it) { return it.getType().name(); }
    private static String ago(long at) {
        long m = (System.currentTimeMillis() - at) / 60000;
        if (m < 1) return "just now";
        if (m < 60) return m + " min ago";
        if (m < 1440) return (m / 60) + " hour" + (m / 60 == 1 ? "" : "s") + " ago";
        return (m / 1440) + " day" + (m / 1440 == 1 ? "" : "s") + " ago";
    }
    private static Component line(String a, NamedTextColor ca, String b, TextColor cb) {
        return Component.text(a, ca).append(Component.text(b, cb)).decoration(TextDecoration.ITALIC, false);
    }
    private static Component plain(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    private static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        it.setItemMeta(meta);
        return MenuIcons.quiet(it);
    }

    private static ItemStack shown(ItemStack unit, int qty, List<Component> extra) {
        ItemStack it = unit.clone();
        it.setAmount(Math.max(1, Math.min(qty, Math.max(1, unit.getMaxStackSize()))));
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.addAll(extra);
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    private long mine(UUID id) { return listings.values().stream().filter(l -> l.seller.equals(id)).count(); }

    // ---------------------------------------------------------------- the views

    void open(Player p, Mode mode, int page, String key, String filter) {
        expire();
        if (mode == Mode.HOME) { openHome(p); return; }
        View v = new View(mode, Math.max(0, page), key, filter);
        String title = switch (mode) {
            case HOME -> "Market";
            case MAIN -> (filter == null ? "Market" : "Market - \"" + filter + "\"") + (category.getOrDefault(p.getUniqueId(), 0) == 0 ? "" : " > " + CATS[category.get(p.getUniqueId())]);
            case ITEM -> "Market > " + (key == null ? "" : pretty(key));
            case WANTED -> "Market > Wanted";
            case MINE -> "Market > Yours";
        };
        Inventory inv = Bukkit.createInventory(v, 54, Component.text(title, NamedTextColor.DARK_GRAY));
        v.inv = inv;
        List<ItemStack> shown = new ArrayList<>();
        List<String> acts = new ArrayList<>();
        switch (mode) {
            case HOME -> { }
            case MAIN -> fillMain(p, filter, shown, acts);
            case ITEM -> fillItem(p, key, shown, acts);
            case WANTED -> fillWanted(p, shown, acts);
            case MINE -> fillMine(p, shown, acts);
        }
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        int pg = Math.min(v.page, pages - 1);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = pg * PER_PAGE + i;
            if (idx >= shown.size()) break;
            inv.setItem(i, shown.get(idx));
            v.act.put(i, acts.get(idx));
        }
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);
        if (pg > 0) { inv.setItem(45, button(Material.ARROW, Component.text("Previous page", NamedTextColor.YELLOW), List.of(plain("Page " + pg + " of " + pages, NamedTextColor.GRAY)))); v.act.put(45, "page:" + (pg - 1)); }
        if (pg < pages - 1) { inv.setItem(53, button(Material.ARROW, Component.text("Next page", NamedTextColor.YELLOW), List.of(plain("Page " + (pg + 2) + " of " + pages, NamedTextColor.GRAY)))); v.act.put(53, "page:" + (pg + 1)); }
        double bal = Money.balance(p.getUniqueId());
        inv.setItem(49, button(Material.SUNFLOWER, Component.text("You have " + (bal < 0 ? "?" : Money.fmt(bal)), GOLD), List.of(plain("Page " + (pg + 1) + " of " + pages, NamedTextColor.DARK_GRAY))));
        if (mode == Mode.ITEM) { inv.setItem(46, button(Material.CHEST, Component.text("Back to the list", NamedTextColor.YELLOW), List.of())); v.act.put(46, "list"); }
        else { inv.setItem(46, button(Material.CHEST, Component.text("Back to the categories", NamedTextColor.YELLOW), List.of())); v.act.put(46, "home"); }
        if (mode == Mode.MAIN) {
            inv.setItem(47, button(Material.WRITABLE_BOOK, Component.text("Wanted", NamedTextColor.YELLOW), List.of(
                    plain("Things people want to buy.", NamedTextColor.GRAY), plain("Sell yours to them straight away.", NamedTextColor.GRAY)))); v.act.put(47, "wanted");
            inv.setItem(48, button(Material.COMPASS, Component.text("Search", NamedTextColor.YELLOW), List.of(plain("Click, then type what you're after.", NamedTextColor.GRAY)))); v.act.put(48, "search");
        }
        int waiting = collection.getOrDefault(p.getUniqueId(), List.of()).size();
        double due = owed.getOrDefault(p.getUniqueId(), 0.0);
        if (mode != Mode.MINE) {
            List<Component> lore = new ArrayList<>(List.of(plain("Your listings, wanted requests,", NamedTextColor.GRAY), plain("and things waiting for you.", NamedTextColor.GRAY)));
            if (waiting > 0 || due > 0) lore.add(plain("Something's waiting for you!", NamedTextColor.GREEN));
            inv.setItem(50, button(Material.BUNDLE, Component.text("Yours", NamedTextColor.YELLOW), lore)); v.act.put(50, "mine");
        }
        inv.setItem(51, button(Material.BOOK, Component.text("How it works", NamedTextColor.YELLOW), List.of(
                line("Sell: hold it, ", NamedTextColor.GRAY, "/market sell <price>", NamedTextColor.YELLOW),
                plain("  (the price for the whole stack)", NamedTextColor.DARK_GRAY),
                line("Want: hold one, ", NamedTextColor.GRAY, "/market want <amount> <price each>", NamedTextColor.YELLOW),
                plain("The Market holds the item (or the money)", NamedTextColor.GRAY),
                plain("so nobody can be scammed.", NamedTextColor.GRAY),
                line("Listings last ", NamedTextColor.GRAY, days() + " days", NamedTextColor.WHITE),
                line("Fee on each sale: ", NamedTextColor.GRAY, Math.round(fee() * 100) + "%", NamedTextColor.WHITE))));
        p.openInventory(inv);
    }

    /** The front page: pick a category, or go to Wanted, Search or Yours. */
    private void openHome(Player p) {
        View v = new View(Mode.HOME, 0, null, null);
        Inventory inv = Bukkit.createInventory(v, 54, Component.text("Market", NamedTextColor.DARK_GRAY));
        v.inv = inv;
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int s = 0; s < 54; s++) inv.setItem(s, filler);
        int[] count = new int[CATS.length], kinds = new int[CATS.length];
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Listing l : listings.values()) {
            int c = catOf(l.unit);
            count[0] += l.qty; count[c] += l.qty;
            if (seen.add(key(l.unit))) { kinds[0]++; kinds[c]++; }
        }
        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < CATS.length; i++) {
            List<Component> lore = new ArrayList<>();
            lore.add(plain(kinds[i] == 0 ? "Nothing for sale yet" : kinds[i] + " kind" + (kinds[i] == 1 ? "" : "s") + " of thing, " + count[i] + " for sale", kinds[i] == 0 ? NamedTextColor.DARK_GRAY : NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(plain("Click to browse", NamedTextColor.GREEN));
            inv.setItem(slots[i], button(CAT_ICONS[i], Component.text(CATS[i], i == 0 ? GOLD : NamedTextColor.YELLOW, TextDecoration.BOLD), lore));
            v.act.put(slots[i], "cat:" + i);
        }
        inv.setItem(29, button(Material.WRITABLE_BOOK, Component.text("Wanted", NamedTextColor.YELLOW, TextDecoration.BOLD), List.of(
                plain(wanted.size() + " request" + (wanted.size() == 1 ? "" : "s") + " up", NamedTextColor.GRAY),
                plain("Things people want to buy.", NamedTextColor.GRAY), plain("Sell yours to them straight away.", NamedTextColor.GRAY)))); v.act.put(29, "wanted");
        inv.setItem(31, button(Material.COMPASS, Component.text("Search", NamedTextColor.YELLOW, TextDecoration.BOLD), List.of(plain("Click, then type what you're after.", NamedTextColor.GRAY)))); v.act.put(31, "search");
        int waiting = collection.getOrDefault(p.getUniqueId(), List.of()).size();
        double due = owed.getOrDefault(p.getUniqueId(), 0.0);
        List<Component> yl = new ArrayList<>(List.of(plain("Your listings, wanted requests,", NamedTextColor.GRAY), plain("and things waiting for you.", NamedTextColor.GRAY)));
        if (waiting > 0 || due > 0) yl.add(plain("Something's waiting for you!", NamedTextColor.GREEN));
        inv.setItem(33, button(Material.BUNDLE, Component.text("Yours", NamedTextColor.YELLOW, TextDecoration.BOLD), yl)); v.act.put(33, "mine");
        double bal = Money.balance(p.getUniqueId());
        inv.setItem(49, button(Material.SUNFLOWER, Component.text("You have " + (bal < 0 ? "?" : Money.fmt(bal)), GOLD), List.of()));
        inv.setItem(51, button(Material.BOOK, Component.text("How it works", NamedTextColor.YELLOW), List.of(
                line("Sell: hold it, ", NamedTextColor.GRAY, "/market sell <price>", NamedTextColor.YELLOW),
                line("Want: hold one, ", NamedTextColor.GRAY, "/market want <amount> <price each>", NamedTextColor.YELLOW),
                plain("The Market holds the item (or the money)", NamedTextColor.GRAY),
                plain("so nobody can be scammed.", NamedTextColor.GRAY))));
        p.openInventory(inv);
    }

    private static String pretty(String key) {
        String s = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private void fillMain(Player p, String filter, List<ItemStack> shown, List<String> acts) {
        Map<String, List<Listing>> groups = new LinkedHashMap<>();
        listings.values().stream().sorted(Comparator.comparing(l -> pretty(key(l.unit))))
                .forEach(l -> groups.computeIfAbsent(key(l.unit), k -> new ArrayList<>()).add(l));
        String f = filter == null ? null : filter.toLowerCase(Locale.ROOT);
        int cat = category.getOrDefault(p.getUniqueId(), 0);
        for (var e : groups.entrySet()) {
            List<Listing> ls = e.getValue();
            if (cat != 0 && catOf(ls.get(0).unit) != cat) continue;
            if (f != null && ls.stream().noneMatch(l -> pretty(e.getKey()).toLowerCase(Locale.ROOT).contains(f) || plainName(l.unit).toLowerCase(Locale.ROOT).contains(f))) continue;
            Listing cheapest = ls.stream().min(Comparator.comparingDouble(l -> l.each)).orElseThrow();
            long sellers = ls.stream().map(l -> l.seller).distinct().count();
            int total = ls.stream().mapToInt(l -> l.qty).sum();
            boolean same = ls.stream().allMatch(l -> l.unit.isSimilar(cheapest.unit));
            ItemStack icon = same ? cheapest.unit.clone() : new ItemStack(cheapest.unit.getType());
            shown.add(shown(icon, 1, List.of(
                    line(sellers + " seller" + (sellers == 1 ? "" : "s"), NamedTextColor.YELLOW, " - " + total + " for sale", NamedTextColor.GRAY),
                    line("Cheapest: ", NamedTextColor.YELLOW, Money.fmt(cheapest.each) + " each", NamedTextColor.GREEN),
                    Component.empty(),
                    plain("Click to see every price", NamedTextColor.GREEN))));
            acts.add("item:" + e.getKey());
        }
    }

    private void fillItem(Player p, String key, List<ItemStack> shown, List<String> acts) {
        boolean admin = p.hasPermission("soulbound.admin");
        listings.values().stream().filter(l -> key(l.unit).equals(key))
                .sorted(Comparator.comparingDouble((Listing l) -> l.each).thenComparingLong(l -> l.at)).forEach(l -> {
                    List<Component> lore = new ArrayList<>();
                    lore.add(line("Seller: ", NamedTextColor.YELLOW, l.sellerName, NamedTextColor.WHITE));
                    lore.add(line("Price: ", NamedTextColor.YELLOW, Money.fmt(l.each * l.qty), NamedTextColor.GREEN)
                            .append(Component.text(l.qty > 1 ? " (" + Money.fmt(l.each) + " each)" : "", NamedTextColor.GRAY)));
                    lore.add(line("Amount: ", NamedTextColor.YELLOW, String.valueOf(l.qty), NamedTextColor.WHITE));
                    lore.add(plain("Listed " + ago(l.at), NamedTextColor.DARK_GRAY));
                    lore.add(Component.empty());
                    if (l.seller.equals(p.getUniqueId())) lore.add(plain("Yours - click to take it back", NamedTextColor.GOLD));
                    else {
                        lore.add(plain("Click: buy all " + l.qty, NamedTextColor.GREEN));
                        if (l.qty > 1) lore.add(plain("Shift-click: buy 1", NamedTextColor.GREEN));
                    }
                    if (admin && !l.seller.equals(p.getUniqueId())) lore.add(plain("Admin: right-click to remove (goes back to the seller)", NamedTextColor.RED));
                    shown.add(shown(l.unit, l.qty, lore));
                    acts.add("buy:" + l.id);
                });
    }

    private void fillWanted(Player p, List<ItemStack> shown, List<String> acts) {
        wanted.values().stream().sorted(Comparator.comparing((Wanted w) -> pretty(key(w.unit))).thenComparing(w -> -w.each)).forEach(w -> {
            int left = w.want - w.got;
            List<Component> lore = new ArrayList<>();
            lore.add(line("Buyer: ", NamedTextColor.YELLOW, w.buyerName, NamedTextColor.WHITE));
            lore.add(line("Wants: ", NamedTextColor.YELLOW, left + " more", NamedTextColor.WHITE).append(Component.text(w.got > 0 ? " (" + w.got + " of " + w.want + " done)" : "", NamedTextColor.GRAY)));
            lore.add(line("Pays: ", NamedTextColor.YELLOW, Money.fmt(w.each) + " each", NamedTextColor.GREEN));
            lore.add(plain("Asked " + ago(w.at), NamedTextColor.DARK_GRAY));
            lore.add(Component.empty());
            if (w.buyer.equals(p.getUniqueId())) lore.add(plain("Yours - click to cancel (money back)", NamedTextColor.GOLD));
            else {
                int have = count(p, w.unit);
                lore.add(have > 0 ? plain("Click: sell them " + Math.min(have, left) + " of yours", NamedTextColor.GREEN)
                        : plain("You don't have any (it must match exactly)", NamedTextColor.RED));
            }
            shown.add(shown(w.unit, left, lore));
            acts.add("fill:" + w.id);
        });
    }

    private void fillMine(Player p, List<ItemStack> shown, List<String> acts) {
        UUID id = p.getUniqueId();
        double due = owed.getOrDefault(id, 0.0);
        if (due > 0) {
            shown.add(button(Material.GOLD_INGOT, Component.text("Collect " + Money.fmt(due), NamedTextColor.GREEN), List.of(plain("Money from sales that's waiting for you.", NamedTextColor.GRAY))));
            acts.add("money");
        }
        List<ItemStack> col = collection.getOrDefault(id, List.of());
        for (int i = 0; i < col.size(); i++) {
            shown.add(shown(col.get(i), col.get(i).getAmount(), List.of(plain("Waiting for you - click to take it", NamedTextColor.GREEN))));
            acts.add("claim:" + i);
        }
        for (Listing l : listings.values()) if (l.seller.equals(id)) {
            long leftMs = l.at + days() * 86_400_000L - System.currentTimeMillis();
            shown.add(shown(l.unit, l.qty, List.of(
                    line("Selling for ", NamedTextColor.YELLOW, Money.fmt(l.each * l.qty), NamedTextColor.GREEN),
                    plain("Ends in " + Math.max(0, leftMs / 3_600_000) + " hours", NamedTextColor.DARK_GRAY),
                    plain("Click to take it back", NamedTextColor.GOLD))));
            acts.add("buy:" + l.id);
        }
        for (Wanted w : wanted.values()) if (w.buyer.equals(id)) {
            shown.add(shown(w.unit, w.want - w.got, List.of(
                    line("Wanted: ", NamedTextColor.YELLOW, (w.want - w.got) + " more at " + Money.fmt(w.each) + " each", NamedTextColor.WHITE),
                    plain("Click to cancel (money back)", NamedTextColor.GOLD))));
            acts.add("fill:" + w.id);
        }
    }

    // ---------------------------------------------------------------- clicks

    @EventHandler
    public void onDrag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof View) e.setCancelled(true); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof View v)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        String a = v.act.get(e.getRawSlot());
        if (a == null) return;
        if (a.startsWith("page:")) { open(p, v.mode, Integer.parseInt(a.substring(5)), v.key, v.filter); return; }
        switch (a) {
            case "home" -> { openHome(p); return; }
            case "list" -> { open(p, Mode.MAIN, 0, null, null); return; }
            case "wanted" -> { open(p, Mode.WANTED, 0, null, null); return; }
            case "mine" -> { open(p, Mode.MINE, 0, null, null); return; }
            case "search" -> {
                searching.put(p.getUniqueId(), System.currentTimeMillis() + 60_000);
                p.closeInventory();
                p.sendMessage(Component.text("Type what you're looking for in chat (or 'cancel').", GOLD));
                return;
            }
            case "money" -> {
                double due = owed.getOrDefault(p.getUniqueId(), 0.0);
                if (due > 0 && Money.give(p.getUniqueId(), due)) { owed.remove(p.getUniqueId()); save(); p.sendMessage(Component.text("Collected " + Money.fmt(due) + ".", NamedTextColor.GREEN)); }
                else p.sendMessage(Component.text("Couldn't pay that out right now. Try again in a bit.", NamedTextColor.RED));
                open(p, Mode.MINE, v.page, null, null);
                return;
            }
            default -> {}
        }
        if (a.startsWith("cat:")) { category.put(p.getUniqueId(), Integer.parseInt(a.substring(4))); open(p, Mode.MAIN, 0, null, null); return; }
        if (a.startsWith("item:")) { open(p, Mode.ITEM, 0, a.substring(5), null); return; }
        if (a.startsWith("claim:")) {
            List<ItemStack> col = collection.get(p.getUniqueId());
            int i = Integer.parseInt(a.substring(6));
            if (col == null || i >= col.size()) return;
            ItemStack it = col.remove(i);
            var left = p.getInventory().addItem(it).values();
            col.addAll(left);
            save();
            if (!left.isEmpty()) p.sendActionBar(Component.text("Your inventory's full.", NamedTextColor.RED));
            open(p, Mode.MINE, v.page, null, null);
            return;
        }
        if (a.startsWith("buy:")) {
            Listing l = listings.get(a.substring(4));
            if (l == null) { p.sendActionBar(Component.text("That's gone already.", NamedTextColor.RED)); open(p, v.mode, v.page, v.key, v.filter); return; }
            if (l.seller.equals(p.getUniqueId())) takeBack(p, l);
            else if (e.isRightClick() && !e.isShiftClick() && p.hasPermission("soulbound.admin")) adminRemove(p, l);
            else buy(p, l, e.isShiftClick() ? 1 : l.qty);
            open(p, v.mode, v.page, v.key, v.filter);
            return;
        }
        if (a.startsWith("fill:")) {
            Wanted w = wanted.get(a.substring(5));
            if (w == null) { p.sendActionBar(Component.text("That's gone already.", NamedTextColor.RED)); open(p, v.mode, v.page, v.key, v.filter); return; }
            if (w.buyer.equals(p.getUniqueId())) cancelWanted(w, "You cancelled your request for " + plainName(w.unit) + ".");
            else fill(p, w);
            open(p, v.mode, v.page, v.key, v.filter);
        }
    }

    private void buy(Player p, Listing l, int n) {
        n = Math.max(1, Math.min(n, l.qty));
        double cost = Math.round(l.each * n * 100) / 100.0;
        long now = System.currentTimeMillis();
        long[] c = confirm.get(p.getUniqueId());
        int ask = plugin.getConfig().getInt("market.confirm-at", 1000);
        if (cost >= ask && (c == null || c[0] != l.id.hashCode() || c[1] != n || c[2] < now)) {
            confirm.put(p.getUniqueId(), new long[]{l.id.hashCode(), n, now + 4000});
            p.sendActionBar(Component.text("Click again to pay " + Money.fmt(cost) + ".", NamedTextColor.GOLD));
            return;
        }
        confirm.remove(p.getUniqueId());
        if (!Money.take(p.getUniqueId(), cost)) {
            double bal = Money.balance(p.getUniqueId());
            p.sendActionBar(Component.text(bal < 0 ? "The Market can't reach your money right now." : "You need " + Money.fmt(cost - bal) + " more.", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        l.qty -= n;
        if (l.qty <= 0) listings.remove(l.id);
        boolean fitted = deliver(p.getUniqueId(), l.unit, n);
        double got = Math.round(cost * (1 - fee()) * 100) / 100.0;
        pay(l.seller, l.sellerName, got, p.getName() + " bought " + n + "x " + plainName(l.unit) + " from you. You got " + Money.fmt(got) + ".");
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        p.sendMessage(Component.text("Bought ", GOLD).append(Component.text(n + "x ", NamedTextColor.WHITE)).append(name(l.unit).colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" for " + Money.fmt(cost) + "." + (fitted ? "" : " Some didn't fit: it's in /market > Yours."), GOLD)));
    }

    private void takeBack(Player p, Listing l) {
        listings.remove(l.id);
        boolean fitted = deliver(p.getUniqueId(), l.unit, l.qty);
        save();
        p.sendActionBar(Component.text(fitted ? "Taken back." : "Taken back. Some didn't fit: it's in Yours.", NamedTextColor.YELLOW));
    }

    private void adminRemove(Player p, Listing l) {
        listings.remove(l.id);
        collection.computeIfAbsent(l.seller, k -> new ArrayList<>()).addAll(stacks(l.unit, l.qty));
        tell(l.seller, "A staff member took your " + plainName(l.unit) + " off the Market. It's waiting in /market > Yours.");
        save();
        p.sendActionBar(Component.text("Removed. It went back to " + l.sellerName + ".", NamedTextColor.YELLOW));
    }

    private void fill(Player p, Wanted w) {
        int have = count(p, w.unit), left = w.want - w.got;
        int n = Math.min(have, left);
        if (n <= 0) { p.sendActionBar(Component.text("You don't have any that match exactly.", NamedTextColor.RED)); return; }
        removeItems(p, w.unit, n);
        w.got += n;
        collection.computeIfAbsent(w.buyer, k -> new ArrayList<>()).addAll(stacks(w.unit, n));
        double earned = Math.round(w.each * n * (1 - fee()) * 100) / 100.0;
        if (!Money.give(p.getUniqueId(), earned)) owed.merge(p.getUniqueId(), earned, Double::sum);
        tell(w.buyer, p.getName() + " sold you " + n + "x " + plainName(w.unit) + ". It's waiting in /market > Yours.");
        if (w.got >= w.want) { wanted.remove(w.id); tell(w.buyer, "Your request for " + w.want + "x " + plainName(w.unit) + " is complete."); }
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        p.sendMessage(Component.text("Sold " + n + "x " + plainName(w.unit) + " to " + w.buyerName + " for " + Money.fmt(earned) + ".", GOLD));
    }

    private void cancelWanted(Wanted w, String why) {
        wanted.remove(w.id);
        double back = Math.round((w.want - w.got) * w.each * 100) / 100.0;
        pay(w.buyer, w.buyerName, back, why + (back > 0 ? " " + Money.fmt(back) + " is back with you." : ""));
        save();
    }

    /** Listings and requests past their time go home. */
    private void expire() {
        long cut = System.currentTimeMillis() - days() * 86_400_000L;
        boolean changed = false;
        for (Listing l : new ArrayList<>(listings.values())) if (l.at < cut) {
            listings.remove(l.id);
            collection.computeIfAbsent(l.seller, k -> new ArrayList<>()).addAll(stacks(l.unit, l.qty));
            tell(l.seller, "Your " + l.qty + "x " + plainName(l.unit) + " didn't sell. It's waiting in /market > Yours.");
            changed = true;
        }
        for (Wanted w : new ArrayList<>(wanted.values())) if (w.at < cut) { cancelWanted(w, "Your request for " + plainName(w.unit) + " ran out."); changed = true; }
        if (changed) save();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        Long until = searching.remove(e.getPlayer().getUniqueId());
        if (until == null || until < System.currentTimeMillis()) return;
        e.setCancelled(true);
        String text = PLAIN.serialize(e.originalMessage()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> { if (!text.equalsIgnoreCase("cancel") && !text.isEmpty()) { category.put(p.getUniqueId(), 0); open(p, Mode.MAIN, 0, null, text); } });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p == null) return;
            double due = owed.getOrDefault(id, 0.0);
            if (due > 0 && Money.give(id, due)) { owed.remove(id); save(); }
            List<String> n = notes.remove(id);
            if (n != null && !n.isEmpty()) {
                p.sendMessage(Component.text("While you were away, at the Market:", GOLD, TextDecoration.BOLD));
                for (String s : n) p.sendMessage(Component.text(" - " + s, NamedTextColor.GRAY));
                save();
            }
            if (!collection.getOrDefault(id, List.of()).isEmpty())
                p.sendMessage(Component.text("You've got things waiting at the Market: /market > Yours.", NamedTextColor.GREEN));
        }, 60L);
    }

    // ---------------------------------------------------------------- /market

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!plugin.getConfig().getBoolean("market.enabled", true)) { p.sendMessage(Component.text("The Market is closed.", NamedTextColor.GRAY)); return true; }
        if (!Money.available()) { p.sendMessage(Component.text("The Market can't reach the money system right now.", NamedTextColor.RED)); return true; }
        if (a.length == 0) { openHome(p); return true; }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "sell" -> sell(p, a);
            case "want", "buy" -> want(p, a);
            case "search", "find" -> {
                if (a.length < 2) { p.sendMessage(Component.text("Usage: /market search <what>", NamedTextColor.RED)); return true; }
                category.put(p.getUniqueId(), 0);
                open(p, Mode.MAIN, 0, null, String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length)));
            }
            case "wanted" -> open(p, Mode.WANTED, 0, null, null);
            case "mine", "yours", "collect" -> open(p, Mode.MINE, 0, null, null);
            default -> help(p);
        }
        return true;
    }

    private void sell(Player p, String[] a) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { p.sendMessage(Component.text("Hold what you want to sell (the whole stack is listed).", NamedTextColor.RED)); return; }
        if (a.length < 2) { p.sendMessage(Component.text("Usage: /market sell <price for the whole stack>  or  /market sell <price> each", NamedTextColor.RED)); return; }
        double price = Money.parse(a[1]);
        if (Double.isNaN(price) || price <= 0) { p.sendMessage(Component.text("The price has to be a number above 0.", NamedTextColor.RED)); return; }
        if (mine(p.getUniqueId()) >= maxListings()) { p.sendMessage(Component.text("You've got " + maxListings() + " things listed already. Take one back or wait for it to sell.", NamedTextColor.RED)); return; }
        int qty = hand.getAmount();
        boolean each = a.length > 2 && a[2].equalsIgnoreCase("each");
        Listing l = new Listing();
        l.id = UUID.randomUUID().toString().substring(0, 8);
        l.seller = p.getUniqueId(); l.sellerName = p.getName();
        l.unit = hand.clone(); l.unit.setAmount(1);
        l.qty = qty;
        l.each = each ? price : Math.round(price / qty * 10000) / 10000.0;
        l.at = System.currentTimeMillis();
        p.getInventory().setItemInMainHand(null);
        listings.put(l.id, l);
        save();
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.8f, 1.2f);
        p.sendMessage(Component.text("Listed ", GOLD).append(Component.text(qty + "x ", NamedTextColor.WHITE)).append(name(l.unit).colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" for " + Money.fmt(l.each * qty) + (qty > 1 ? " (" + Money.fmt(l.each) + " each)" : "") + ". It's in the Market for " + days() + " days.", GOLD)));
    }

    private void want(Player p, String[] a) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) { p.sendMessage(Component.text("Hold one of the thing you want, so the Market knows exactly what (you keep it).", NamedTextColor.RED)); return; }
        if (a.length < 3) { p.sendMessage(Component.text("Usage: /market want <amount> <price each>  (holding one)", NamedTextColor.RED)); return; }
        int amount;
        try { amount = Integer.parseInt(a[1]); } catch (NumberFormatException ex) { p.sendMessage(Component.text("The amount has to be a number.", NamedTextColor.RED)); return; }
        double each = Money.parse(a[2]);
        if (amount < 1 || amount > 2304 || Double.isNaN(each) || each <= 0) { p.sendMessage(Component.text("Amount 1 to 2,304, and a price above 0.", NamedTextColor.RED)); return; }
        if (wanted.values().stream().filter(w -> w.buyer.equals(p.getUniqueId())).count() >= maxWanted()) { p.sendMessage(Component.text("You've got " + maxWanted() + " requests up already.", NamedTextColor.RED)); return; }
        double total = Math.round(amount * each * 100) / 100.0;
        if (!Money.take(p.getUniqueId(), total)) { p.sendMessage(Component.text("That needs " + Money.fmt(total) + " held up front, and you don't have it.", NamedTextColor.RED)); return; }
        Wanted w = new Wanted();
        w.id = UUID.randomUUID().toString().substring(0, 8);
        w.buyer = p.getUniqueId(); w.buyerName = p.getName();
        w.unit = hand.clone(); w.unit.setAmount(1);
        w.want = amount; w.got = 0; w.each = each; w.at = System.currentTimeMillis();
        wanted.put(w.id, w);
        save();
        p.sendMessage(Component.text("Asked for " + amount + "x " + plainName(w.unit) + " at " + Money.fmt(each) + " each. " + Money.fmt(total) + " is held until it's filled (or you cancel).", GOLD));
    }

    private void help(Player p) {
        p.sendMessage(Component.text("The Market", GOLD, TextDecoration.BOLD));
        p.sendMessage(line("/market", NamedTextColor.YELLOW, "  browse everything for sale", NamedTextColor.GRAY));
        p.sendMessage(line("/market sell <price>", NamedTextColor.YELLOW, "  sell the stack you're holding (add 'each' for a price per item)", NamedTextColor.GRAY));
        p.sendMessage(line("/market want <amount> <price each>", NamedTextColor.YELLOW, "  ask to buy something, holding one", NamedTextColor.GRAY));
        p.sendMessage(line("/market search <what>", NamedTextColor.YELLOW, "  find something", NamedTextColor.GRAY));
        p.sendMessage(line("/market yours", NamedTextColor.YELLOW, "  your listings, requests and things waiting for you", NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (a.length == 1) return filter(List.of("sell", "want", "search", "wanted", "yours", "help"), a[0]);
        if (a.length == 3 && a[0].equalsIgnoreCase("sell")) return filter(List.of("each"), a[2]);
        return List.of();
    }

    private static List<String> filter(List<String> l, String pre) {
        List<String> out = new ArrayList<>();
        for (String x : l) if (x.startsWith(pre.toLowerCase(Locale.ROOT))) out.add(x);
        return out;
    }
}
