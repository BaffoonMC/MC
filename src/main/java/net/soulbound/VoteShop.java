package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Vote Shop: 1 vote = 1 point (votes come from NuVotifier), spent in /voteshop.
 * Stocked by hand: hold something and /voteshop add <price> [page]. /voteshop edit to take things out or change prices.
 * 45 things a page; as many pages as you like.
 */
final class VoteShop implements Listener, CommandExecutor, TabCompleter {
    private static final int PER_PAGE = 45, PREV = 45, INFO = 49, NEXT = 53;
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private record Entry(ItemStack item, int price, int page) {}
    private record PriceEdit(int index, long until) {}
    private static final class Shop implements InventoryHolder {
        final int page; final boolean edit; Inventory inv;
        final Map<Integer, Integer> slotToEntry = new HashMap<>();
        Shop(int page, boolean edit) { this.page = page; this.edit = edit; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final Soulbound plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private final Map<UUID, Integer> points = new HashMap<>();
    private final Map<String, Integer> pendingByName = new HashMap<>();   // votes from players the server hasn't seen yet
    private final Map<UUID, Integer> votes = new HashMap<>();            // lifetime votes, never spent: these climb the ranks
    private final Map<String, Integer> pendingVotes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> siteVotes = new HashMap<>();   // when each player last voted on each site
    private final Map<UUID, PriceEdit> priceEdits = new HashMap<>();
    private final Map<UUID, long[]> confirm = new HashMap<>();           // {entry index, until}
    private boolean stocked;                                             // the starter pages have been added

    VoteShop(Soulbound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "voteshop.yml");
        load();
    }

    void start() { hookVotifier(); }

    // ---------------------------------------------------------------- storage

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        entries.clear(); points.clear(); pendingByName.clear(); votes.clear(); pendingVotes.clear(); siteVotes.clear();
        for (Map<?, ?> m : y.getMapList("items")) {
            try {
                ItemStack it = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(m.get("item"))));
                entries.add(new Entry(it, ((Number) m.get("price")).intValue(), ((Number) m.get("page")).intValue()));
            } catch (RuntimeException ex) { plugin.getLogger().warning("Vote Shop: skipped an item that couldn't be read: " + ex.getMessage()); }
        }
        var ps = y.getConfigurationSection("points");
        if (ps != null) for (String k : ps.getKeys(false)) try { points.put(UUID.fromString(k), ps.getInt(k)); } catch (IllegalArgumentException ignored) {}
        stocked = y.getBoolean("starter-stocked", false);
        var vs = y.getConfigurationSection("votes");
        if (vs != null) for (String k : vs.getKeys(false)) try { votes.put(UUID.fromString(k), vs.getInt(k)); } catch (IllegalArgumentException ignored) {}
        var sv = y.getConfigurationSection("site-votes");
        if (sv != null) for (String k : sv.getKeys(false)) {
            var one = sv.getConfigurationSection(k);
            if (one == null) continue;
            try { Map<String, Long> m = new HashMap<>(); for (String site : one.getKeys(false)) m.put(site, one.getLong(site)); siteVotes.put(UUID.fromString(k), m); }
            catch (IllegalArgumentException ignored) {}
        }
        var pv = y.getConfigurationSection("pending-votes");
        if (pv != null) for (String k : pv.getKeys(false)) pendingVotes.put(k, pv.getInt(k));
        if (!y.getBoolean("votes-counted", false)) {
            // before ranks counted votes, the only record was points: start everyone's count from what they have
            int per = Math.max(1, plugin.getConfig().getInt("voteshop.points-per-vote", 1));
            for (var en : points.entrySet()) votes.putIfAbsent(en.getKey(), en.getValue() / per);
            for (var en : pendingByName.entrySet()) pendingVotes.putIfAbsent(en.getKey(), en.getValue() / per);
        }
        var pn = y.getConfigurationSection("pending");
        if (pn != null) for (String k : pn.getKeys(false)) pendingByName.put(k, pn.getInt(k));
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new HashMap<>();
            m.put("item", Base64.getEncoder().encodeToString(e.item().serializeAsBytes()));
            m.put("price", e.price()); m.put("page", e.page());
            list.add(m);
        }
        y.set("items", list);
        if (stocked) y.set("starter-stocked", true);
        y.set("votes-counted", true);
        for (var en : votes.entrySet()) y.set("votes." + en.getKey(), en.getValue());
        for (var en : pendingVotes.entrySet()) y.set("pending-votes." + en.getKey(), en.getValue());
        long old = System.currentTimeMillis() - 3L * 86_400_000L;   // only the last few days matter
        for (var en : siteVotes.entrySet()) for (var sv : en.getValue().entrySet())
            if (sv.getValue() > old) y.set("site-votes." + en.getKey() + "." + sv.getKey(), sv.getValue());
        for (var en : points.entrySet()) y.set("points." + en.getKey(), en.getValue());
        for (var en : pendingByName.entrySet()) y.set("pending." + en.getKey(), en.getValue());
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Couldn't save voteshop.yml: " + ex.getMessage()); }
    }

    int points(UUID id) { return points.getOrDefault(id, 0); }
    int votes(UUID id) { return votes.getOrDefault(id, 0); }
    /** When this player last voted on the /vote site with this label (0 = not that we know of). */
    long lastVote(UUID id, String siteLabel) { Map<String, Long> m = siteVotes.get(id); return m == null ? 0 : m.getOrDefault(siteKey(siteLabel), 0L); }
    static String siteKey(String label) { return label == null ? "" : label.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    void setVotes(UUID id, int n) { votes.put(id, Math.max(0, n)); save(); }
    void forget(UUID id) { boolean a = points.remove(id) != null, b = votes.remove(id) != null, c = siteVotes.remove(id) != null; if (a || b || c) save(); }
    void setPoints(UUID id, int n) { points.put(id, Math.max(0, n)); save(); }

    // ---------------------------------------------------------------- votes

    private void hookVotifier() {
        var vp = Bukkit.getPluginManager().getPlugin("Votifier");
        if (vp == null) vp = Bukkit.getPluginManager().getPlugin("NuVotifier");
        if (vp == null) { plugin.getLogger().info("Vote Shop: NuVotifier isn't here, so votes won't give points yet (you can still give them with /voteshop points)."); return; }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> ev = (Class<? extends Event>) Class.forName("com.vexsoftware.votifier.model.VotifierEvent", true, vp.getClass().getClassLoader());
            Bukkit.getPluginManager().registerEvent(ev, this, EventPriority.MONITOR, (l, e) -> {
                if (!ev.isInstance(e)) return;
                try {
                    Object vote = e.getClass().getMethod("getVote").invoke(e);
                    String name = String.valueOf(vote.getClass().getMethod("getUsername").invoke(vote));
                    String site;
                    try { site = String.valueOf(vote.getClass().getMethod("getServiceName").invoke(vote)); } catch (ReflectiveOperationException ex) { site = ""; }
                    String from = site;
                    Bukkit.getScheduler().runTask(plugin, () -> { voted(name, from); announce(name, from); });
                } catch (ReflectiveOperationException ex) { plugin.getLogger().warning("Vote Shop: couldn't read a vote: " + ex.getMessage()); }
            }, plugin, true);
            plugin.getLogger().info("Vote Shop: listening for votes from NuVotifier.");
        } catch (ClassNotFoundException ex) { plugin.getLogger().warning("Vote Shop: NuVotifier is here but its vote event wasn't found."); }
    }

    private void voted(String name, String service) {
        if (name == null || name.isBlank() || name.length() > 16) return;
        int add = Math.max(0, plugin.getConfig().getInt("voteshop.points-per-vote", 1));
        Player online = Bukkit.getPlayerExact(name);
        OfflinePlayer who = online != null ? online : Bukkit.getOfflinePlayerIfCached(name);
        if (who == null) {
            String key = name.toLowerCase(java.util.Locale.ROOT);
            if (add > 0) pendingByName.merge(key, add, Integer::sum);
            pendingVotes.merge(key, 1, Integer::sum);
            save(); return;
        }
        votes.merge(who.getUniqueId(), 1, Integer::sum);
        String site = siteKey(siteName(service));
        if (!site.isEmpty()) siteVotes.computeIfAbsent(who.getUniqueId(), k -> new HashMap<>()).put(site, System.currentTimeMillis());
        int now = add > 0 ? points.merge(who.getUniqueId(), add, Integer::sum) : points(who.getUniqueId());
        save();
        double cash = plugin.getConfig().getDouble("voteshop.money-per-vote", 100);
        boolean paid = cash > 0 && Money.give(who.getUniqueId(), cash);
        if (online == null) return;
        if (add > 0 || paid) {
            Component got = Component.text(add > 0 ? "+" + add + " point" + (add == 1 ? "" : "s") : "", NamedTextColor.GREEN);
            if (paid) got = got.append(Component.text((add > 0 ? " and " : "") + "+" + Money.fmt(cash), NamedTextColor.GREEN));
            online.sendMessage(Component.text("Thanks for voting! ", GOLD).append(got)
                    .append(Component.text(" (you have " + now + " point" + (now == 1 ? "" : "s") + ").  ", NamedTextColor.GRAY)).append(Component.text("/voteshop", NamedTextColor.YELLOW)));
            online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
        }
        if (plugin.voteRanks() != null) { plugin.voteRanks().check(online); plugin.voteRanks().refresh(online); }
    }

    /** Tell everyone online that someone voted, and on which site, so others remember to as well. */
    private void announce(String name, String service) {
        if (name == null || name.isBlank() || name.length() > 16 || !plugin.getConfig().getBoolean("vote-ranks.broadcast", true)) return;
        String site = siteName(service);
        Component msg = Component.text("✔ ", NamedTextColor.GREEN).append(Component.text(name, NamedTextColor.WHITE))
                .append(Component.text(" voted for the server" + (site.isEmpty() ? "" : " on " + site) + "! ", NamedTextColor.GRAY))
                .append(Component.text("/vote", NamedTextColor.YELLOW).clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/vote"))
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Vote too: points for the Vote Shop, and climb the ranks", NamedTextColor.GRAY))));
        Bukkit.broadcast(msg);
    }

    /** The list site's own name for itself (e.g. "Minecraft-MP.com"), matched to one of our /vote sites where possible. */
    private String siteName(String service) {
        if (service == null || service.isBlank() || service.equalsIgnoreCase("null")) return "";
        String key = service.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String line : plugin.getConfig().getStringList("vote-ranks.sites")) {
            String[] w = line.split("\\|", 2);
            if (w.length < 2) continue;
            String host = w[1].toLowerCase(java.util.Locale.ROOT).replaceAll("^https?://(www\\.)?", "").split("/")[0].replaceAll("[^a-z0-9]", "");
            String label = w[0].toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if ((!host.isEmpty() && (key.equals(host) || key.contains(host) || host.contains(key))) || (!label.isEmpty() && (key.contains(label) || label.equals(key)))) return w[0].trim();
        }
        return service.trim();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String key = e.getPlayer().getName().toLowerCase(java.util.Locale.ROOT);
        Integer owed = pendingByName.remove(key), cast = pendingVotes.remove(key);
        if (owed != null) points.merge(e.getPlayer().getUniqueId(), owed, Integer::sum);
        if (cast != null) {
            votes.merge(e.getPlayer().getUniqueId(), cast, Integer::sum);
            double cash = plugin.getConfig().getDouble("voteshop.money-per-vote", 100) * cast;   // votes cast before we'd ever seen them
            if (cash > 0 && Money.give(e.getPlayer().getUniqueId(), cash))
                e.getPlayer().sendMessage(Component.text("Thanks for voting! ", GOLD).append(Component.text("+" + Money.fmt(cash), NamedTextColor.GREEN))
                        .append(Component.text(" for " + cast + " vote" + (cast == 1 ? "" : "s") + " while you were away.", NamedTextColor.GRAY)));
        }
        if (owed != null || cast != null) save();
        Player p = e.getPlayer();   // LuckPerms needs a moment to load them before we can tell their rank
        if (plugin.voteRanks() != null) Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) plugin.voteRanks().check(p); }, 60L);
    }

    // ---------------------------------------------------------------- the shop

    private int pages() {
        int max = 1;
        for (Entry e : entries) max = Math.max(max, e.page());
        return max;
    }

    private List<Integer> onPage(int page) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).page() == page) l.add(i);
        return l;
    }

    void open(Player p, int page, boolean edit) {
        page = Math.max(1, Math.min(page, pages() + (edit ? 1 : 0)));
        Shop h = new Shop(page, edit);
        int pts = points(p.getUniqueId());
        Component title = edit ? Component.text("Vote Shop (editing) - page " + page, NamedTextColor.DARK_RED)
                : Component.text("Vote Shop  -  " + pts + " point" + (pts == 1 ? "" : "s"), NamedTextColor.DARK_GRAY);
        Inventory inv = Bukkit.createInventory(h, 54, title);
        h.inv = inv;
        int slot = 0;
        for (int idx : onPage(page)) {
            if (slot >= PER_PAGE) break;
            Entry e = entries.get(idx);
            inv.setItem(slot, display(e, pts, edit));
            h.slotToEntry.put(slot, idx);
            slot++;
        }
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);
        if (page > 1) inv.setItem(PREV, button(Material.ARROW, Component.text("Previous page", NamedTextColor.YELLOW), List.of(Component.text("Page " + (page - 1), NamedTextColor.GRAY))));
        if (page < pages() || (edit && page == pages() && !onPage(page).isEmpty()))
            inv.setItem(NEXT, button(Material.ARROW, Component.text(edit && page == pages() ? "New page" : "Next page", NamedTextColor.YELLOW), List.of(Component.text("Page " + (page + 1), NamedTextColor.GRAY))));
        if (edit) inv.setItem(INFO, button(Material.WRITABLE_BOOK, Component.text("Editing the Vote Shop", NamedTextColor.GOLD), List.of(
                line("Add: hold it, ", "/voteshop add <price> " + page),
                line("Left-click a thing: ", "take it out"),
                line("Right-click a thing: ", "change its price"),
                line("Shift-click a thing: ", "move it to the next page"))));
        else inv.setItem(INFO, button(Material.SUNFLOWER, Component.text("You have " + pts + " point" + (pts == 1 ? "" : "s"), GOLD), List.of(
                line("Vote with ", "/vote"),
                line("Each vote = ", plugin.getConfig().getInt("voteshop.points-per-vote", 1) + " point"),
                line("Page ", page + " of " + pages()))));
        p.openInventory(inv);
    }

    private static Component line(String a, String b) {
        return Component.text(a, NamedTextColor.GRAY).append(Component.text(b, NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack button(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        it.setItemMeta(meta);
        return MenuIcons.quiet(it);
    }

    private ItemStack display(Entry e, int pts, boolean edit) {
        ItemStack it = e.item().clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Cost: ", NamedTextColor.YELLOW).append(Component.text(String.format("%,d", e.price()) + " point" + (e.price() == 1 ? "" : "s"), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
        if (edit) lore.add(Component.text("Left: take out · Right: price · Shift: next page", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        else if (pts >= e.price()) lore.add(Component.text("Click to buy", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        else lore.add(Component.text("You need " + (e.price() - pts) + " more", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Shop) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Shop h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() != e.getInventory()) return;
        int slot = e.getRawSlot();
        if (slot == PREV && h.page > 1) { open(p, h.page - 1, h.edit); return; }
        if (slot == NEXT && e.getCurrentItem() != null && e.getCurrentItem().getType() == Material.ARROW) { open(p, h.page + 1, h.edit); return; }
        Integer idx = h.slotToEntry.get(slot);
        if (idx == null || idx >= entries.size()) return;
        Entry en = entries.get(idx);
        if (h.edit) {
            if (!p.hasPermission("soulbound.admin")) return;
            if (e.isShiftClick()) {
                if (onPage(en.page() + 1).size() >= PER_PAGE) { p.sendActionBar(Component.text("Page " + (en.page() + 1) + " is full.", NamedTextColor.RED)); return; }
                entries.set(idx, new Entry(en.item(), en.price(), en.page() + 1)); save();
                p.sendActionBar(Component.text("Moved to page " + (en.page() + 1) + ".", NamedTextColor.GREEN));
                open(p, h.page, true);
            } else if (e.isRightClick()) {
                priceEdits.put(p.getUniqueId(), new PriceEdit(idx, System.currentTimeMillis() + 60_000));
                p.closeInventory();
                p.sendMessage(Component.text("Type the new price in chat (or 'cancel'). It's " + en.price() + " now.", GOLD));
            } else {
                entries.remove((int) idx); save();
                for (ItemStack left : p.getInventory().addItem(en.item().clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                p.sendActionBar(Component.text("Taken out of the shop and given back to you.", NamedTextColor.YELLOW));
                open(p, h.page, true);
            }
            return;
        }
        buy(p, idx, en, h.page);
    }

    private void buy(Player p, int idx, Entry en, int page) {
        int pts = points(p.getUniqueId());
        if (pts < en.price()) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            p.sendActionBar(Component.text("You need " + (en.price() - pts) + " more point" + (en.price() - pts == 1 ? "" : "s") + ".", NamedTextColor.RED));
            return;
        }
        int ask = plugin.getConfig().getInt("voteshop.confirm-at", 100);
        long now = System.currentTimeMillis();
        long[] c = confirm.get(p.getUniqueId());
        if (en.price() >= ask && (c == null || c[0] != idx || c[1] < now)) {
            confirm.put(p.getUniqueId(), new long[]{idx, now + 4000});
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.4f);
            p.sendActionBar(Component.text("Click again to spend " + String.format("%,d", en.price()) + " points.", NamedTextColor.GOLD));
            return;
        }
        confirm.remove(p.getUniqueId());
        points.put(p.getUniqueId(), pts - en.price());
        save();
        for (ItemStack left : p.getInventory().addItem(en.item().clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
        Component name = en.item().getItemMeta().hasDisplayName() ? en.item().getItemMeta().displayName() : Component.translatable(en.item().translationKey());
        p.sendMessage(Component.text("Bought ", GOLD).append(Component.text(en.item().getAmount() + "x ", NamedTextColor.WHITE)).append(name.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text(" for " + String.format("%,d", en.price()) + " points.", GOLD)));
        open(p, page, false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent e) {
        PriceEdit w = priceEdits.remove(e.getPlayer().getUniqueId());
        if (w == null || w.until() < System.currentTimeMillis()) return;
        e.setCancelled(true);
        String text = PLAIN.serialize(e.originalMessage()).trim();
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (text.equalsIgnoreCase("cancel")) { p.sendMessage(Component.text("Price unchanged.", NamedTextColor.GRAY)); return; }
            int price;
            try { price = Integer.parseInt(text.replace(",", "")); } catch (NumberFormatException ex) { p.sendMessage(Component.text("That isn't a number. Price unchanged.", NamedTextColor.RED)); return; }
            if (price < 0 || w.index() >= entries.size()) { p.sendMessage(Component.text("Price unchanged.", NamedTextColor.RED)); return; }
            Entry en = entries.get(w.index());
            entries.set(w.index(), new Entry(en.item(), price, en.page())); save();
            p.sendMessage(Component.text("Price set to " + String.format("%,d", price) + ".", NamedTextColor.GREEN));
            open(p, en.page(), true);
        });
    }

    /** Spawn eggs bought here (or found anywhere) can't turn a mob spawner into something else. */
    @EventHandler(ignoreCancelled = true)
    public void onEggOnSpawner(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.SPAWNER) return;
        if (e.getItem() == null || !e.getItem().getType().name().endsWith("_SPAWN_EGG")) return;
        if (!plugin.getConfig().getBoolean("voteshop.block-eggs-on-spawners", true) || e.getPlayer().hasPermission("soulbound.admin")) return;
        e.setCancelled(true);
        e.getPlayer().sendActionBar(Component.text("Spawn eggs don't work on spawners here.", NamedTextColor.GRAY));
    }

    // ---------------------------------------------------------------- /voteshop

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        boolean admin = s.hasPermission("soulbound.admin");
        if (a.length == 0 || !admin && !a[0].equalsIgnoreCase("points")) {
            if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
            int page = 1;
            if (a.length > 0) try { page = Integer.parseInt(a[0]); } catch (NumberFormatException ignored) {}
            open(p, page, false);
            return true;
        }
        switch (a[0].toLowerCase(java.util.Locale.ROOT)) {
            case "add" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { s.sendMessage(Component.text("Hold the thing you want to sell (the whole stack is sold together).", NamedTextColor.RED)); return true; }
                if (a.length < 2) { s.sendMessage(Component.text("Usage: /voteshop add <price> [page]", NamedTextColor.RED)); return true; }
                int price, page;
                try { price = Math.max(0, Integer.parseInt(a[1].replace(",", ""))); } catch (NumberFormatException ex) { s.sendMessage(Component.text("The price has to be a number.", NamedTextColor.RED)); return true; }
                if (a.length > 2) {
                    try { page = Math.max(1, Integer.parseInt(a[2])); } catch (NumberFormatException ex) { s.sendMessage(Component.text("The page has to be a number.", NamedTextColor.RED)); return true; }
                    if (onPage(page).size() >= PER_PAGE) { s.sendMessage(Component.text("Page " + page + " is full (45 things).", NamedTextColor.RED)); return true; }
                } else {
                    int last = pages();
                    page = onPage(last).size() < PER_PAGE ? last : last + 1;   // the last page, or a new one when it's full
                }
                entries.add(new Entry(hand.clone(), price, page)); save();
                Component name = hand.getItemMeta().hasDisplayName() ? hand.getItemMeta().displayName() : Component.translatable(hand.translationKey());
                s.sendMessage(Component.text("Added ", NamedTextColor.GREEN).append(Component.text(hand.getAmount() + "x ", NamedTextColor.WHITE)).append(name.colorIfAbsent(NamedTextColor.WHITE))
                        .append(Component.text(" for " + String.format("%,d", price) + " points on page " + page + ".", NamedTextColor.GREEN)));
            }
            case "edit" -> {
                if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
                int page = 1;
                if (a.length > 1) try { page = Integer.parseInt(a[1]); } catch (NumberFormatException ignored) {}
                open(p, page, true);
            }
            case "points" -> {
                if (a.length < 2 || !admin) {
                    if (s instanceof Player p) s.sendMessage(Component.text("You have " + points(p.getUniqueId()) + " vote points. ", GOLD).append(Component.text("/voteshop", NamedTextColor.YELLOW)));
                    return true;
                }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) { s.sendMessage(Component.text("Never seen " + a[1] + " here.", NamedTextColor.RED)); return true; }
                if (a.length < 4) { s.sendMessage(Component.text(t.getName() + " has " + points(t.getUniqueId()) + " vote points.", GOLD)); return true; }
                int n;
                try { n = Integer.parseInt(a[3]); } catch (NumberFormatException ex) { s.sendMessage(Component.text("That isn't a number.", NamedTextColor.RED)); return true; }
                int was = points(t.getUniqueId());
                switch (a[2].toLowerCase(java.util.Locale.ROOT)) {
                    case "add", "give" -> setPoints(t.getUniqueId(), was + n);
                    case "take", "remove" -> setPoints(t.getUniqueId(), was - n);
                    case "set" -> setPoints(t.getUniqueId(), n);
                    default -> { s.sendMessage(Component.text("Usage: /voteshop points <player> [add|take|set] <amount>", NamedTextColor.RED)); return true; }
                }
                s.sendMessage(Component.text(t.getName() + " now has " + points(t.getUniqueId()) + " vote points.", NamedTextColor.GREEN));
            }
            case "votes" -> {
                if (a.length < 2) { s.sendMessage(Component.text("Usage: /voteshop votes <player> [add|take|set] <amount>", NamedTextColor.RED)); return true; }
                OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(a[1]);
                if (t == null) { s.sendMessage(Component.text("Never seen " + a[1] + " here.", NamedTextColor.RED)); return true; }
                if (a.length >= 4) {
                    int n;
                    try { n = Integer.parseInt(a[3]); } catch (NumberFormatException ex) { s.sendMessage(Component.text("That isn't a number.", NamedTextColor.RED)); return true; }
                    int was = votes(t.getUniqueId());
                    switch (a[2].toLowerCase(java.util.Locale.ROOT)) {
                        case "add", "give" -> setVotes(t.getUniqueId(), was + n);
                        case "take", "remove" -> setVotes(t.getUniqueId(), was - n);
                        case "set" -> setVotes(t.getUniqueId(), n);
                        default -> { s.sendMessage(Component.text("Usage: /voteshop votes <player> [add|take|set] <amount>", NamedTextColor.RED)); return true; }
                    }
                    if (t.getPlayer() != null && plugin.voteRanks() != null) plugin.voteRanks().check(t.getPlayer());
                }
                s.sendMessage(Component.text(t.getName() + " has voted " + votes(t.getUniqueId()) + " times (this is what ranks go by; points are separate).", GOLD));
            }
            case "reload" -> { load(); s.sendMessage(Component.text("Vote Shop reloaded.", NamedTextColor.GREEN)); }
            case "stock" -> stock(s, a.length > 1 && a[1].equalsIgnoreCase("force"));
            case "help" -> help(s);
            default -> {
                if (!(s instanceof Player p)) { help(s); return true; }
                int page = 1;
                try { page = Integer.parseInt(a[0]); } catch (NumberFormatException ex) { help(s); return true; }
                open(p, page, false);
            }
        }
        return true;
    }

    /**
     * Adds the ready-made pages from voteshop.starter in config.yml after the last page. Each line is "MATERIAL amount price";
     * a line starting with --- starts the next page. Only once, unless forced, so the pages don't end up in there twice.
     */
    private void stock(CommandSender s, boolean force) {
        if (stocked && !force) {
            s.sendMessage(Component.text("The starter pages are already in. /voteshop stock force adds them again.", NamedTextColor.RED));
            return;
        }
        List<String> lines = plugin.getConfig().getStringList("voteshop.starter");
        int page = onPage(pages()).isEmpty() ? pages() - 1 : pages();
        int added = 0, first = -1, onThis = PER_PAGE;
        for (String raw : lines) {
            String l = raw.trim();
            if (l.isEmpty()) continue;
            if (l.startsWith("---")) { page++; onThis = 0; if (first < 0) first = page; continue; }
            if (onThis >= PER_PAGE) { page++; onThis = 0; if (first < 0) first = page; }
            String[] w = l.split("\\s+");
            Material m = w.length >= 3 ? Material.matchMaterial(w[0]) : null;
            if (m == null || !m.isItem()) { s.sendMessage(Component.text("Skipped \"" + l + "\" (unknown item).", NamedTextColor.GRAY)); continue; }
            int amount, price;
            try { amount = Math.max(1, Math.min(m.getMaxStackSize(), Integer.parseInt(w[1]))); price = Math.max(0, Integer.parseInt(w[2])); }
            catch (NumberFormatException ex) { s.sendMessage(Component.text("Skipped \"" + l + "\" (amount and price must be numbers).", NamedTextColor.GRAY)); continue; }
            entries.add(new Entry(new ItemStack(m, amount), price, page));
            onThis++; added++;
        }
        stocked = true;
        save();
        s.sendMessage(Component.text("Added " + added + " things on pages " + first + " to " + page + ". /voteshop edit " + first + " to change prices.", NamedTextColor.GREEN));
    }

    private void help(CommandSender s) {
        s.sendMessage(Component.text("Vote Shop", GOLD, TextDecoration.BOLD));
        s.sendMessage(line("/voteshop [page]", "  open it"));
        s.sendMessage(line("/voteshop add <price> [page]", "  sell what you're holding"));
        s.sendMessage(line("/voteshop edit [page]", "  take out, re-price or move things"));
        s.sendMessage(line("/voteshop stock", "  add the ready-made pages of everyday things (config.yml)"));
        s.sendMessage(line("/voteshop points <player> [add|take|set] <n>", "  points to spend"));
        s.sendMessage(line("/voteshop votes <player> [add|take|set] <n>", "  lifetime votes, for ranks"));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!s.hasPermission("soulbound.admin")) return List.of();
        if (a.length == 1) return filter(List.of("add", "edit", "stock", "points", "votes", "reload", "help"), a[0]);
        if (a.length == 2 && a[0].equalsIgnoreCase("add")) return filter(List.of("10", "25", "50", "100", "150"), a[1]);
        if (a.length == 3 && a[0].equalsIgnoreCase("add")) { List<String> l = new ArrayList<>(); for (int i = 1; i <= pages() + 1; i++) l.add(String.valueOf(i)); return filter(l, a[2]); }
        if (a.length == 2 && a[0].equalsIgnoreCase("votes")) { List<String> l = new ArrayList<>(); for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName()); return filter(l, a[1]); }
        if (a.length == 3 && a[0].equalsIgnoreCase("votes")) return filter(List.of("add", "take", "set"), a[2]);
        if (a.length == 2 && a[0].equalsIgnoreCase("points")) { List<String> l = new ArrayList<>(); for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName()); return filter(l, a[1]); }
        if (a.length == 3 && a[0].equalsIgnoreCase("points")) return filter(List.of("add", "take", "set"), a[2]);
        return List.of();
    }

    private static List<String> filter(List<String> l, String pre) {
        List<String> out = new ArrayList<>();
        for (String x : l) if (x.toLowerCase(java.util.Locale.ROOT).startsWith(pre.toLowerCase(java.util.Locale.ROOT))) out.add(x);
        return out;
    }
}
