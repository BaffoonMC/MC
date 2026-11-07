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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The server shop (/shop): where money comes into the game. Players sell crops, drops and ores to the server (with a
 * daily limit per item, so farms can't print money) and buy basics from it. Stocked in game like the Vote Shop:
 * hold the item (the amount you hold is one lot) and /shop add <buy> <sell> [daily limit] [page]; /shop edit.
 */
final class ServerShop implements Listener, CommandExecutor, TabCompleter {
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final int PER_PAGE = 45, PREV = 45, INFO = 49, NEXT = 53;

    private static final class Entry { String id; ItemStack lot; double buy, sell; int limit, page; }
    private record PriceEdit(String id, long until) {}
    private static final class View implements InventoryHolder {
        final int page; final boolean edit; Inventory inv;
        final Map<Integer, String> slotToId = new HashMap<>();
        View(int page, boolean edit) { this.page = page; this.edit = edit; }
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private final Soulbound plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private String soldDay = "";
    private final Map<String, Integer> soldToday = new HashMap<>();   // "<player uuid>:<entry id>" -> items sold today
    private final Map<UUID, PriceEdit> priceEdits = new HashMap<>();

    ServerShop(Soulbound plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "servershop.yml");
        load();
    }

    // ---------------------------------------------------------------- storage

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        entries.clear(); soldToday.clear();
        for (Map<?, ?> m : y.getMapList("items")) try {
            Entry e = new Entry();
            e.id = String.valueOf(m.get("id"));
            e.lot = ItemStack.deserializeBytes(Base64.getDecoder().decode(String.valueOf(m.get("item"))));
            e.buy = ((Number) m.get("buy")).doubleValue(); e.sell = ((Number) m.get("sell")).doubleValue();
            e.limit = ((Number) m.get("limit")).intValue(); e.page = ((Number) m.get("page")).intValue();
            entries.add(e);
        } catch (RuntimeException ex) { plugin.getLogger().warning("Server shop: skipped an item that couldn't be read: " + ex.getMessage()); }
        soldDay = y.getString("sold-day", "");
        var s = y.getConfigurationSection("sold");
        if (s != null) for (String k : s.getKeys(false)) soldToday.put(k, s.getInt(k));
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id); m.put("item", Base64.getEncoder().encodeToString(e.lot.serializeAsBytes()));
            m.put("buy", e.buy); m.put("sell", e.sell); m.put("limit", e.limit); m.put("page", e.page);
            list.add(m);
        }
        y.set("items", list);
        y.set("sold-day", soldDay);
        for (var en : soldToday.entrySet()) y.set("sold." + en.getKey(), en.getValue());
        try { plugin.getDataFolder().mkdirs(); y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Couldn't save servershop.yml: " + ex.getMessage()); }
    }

    private int sold(UUID p, Entry e) {
        String today = LocalDate.now().toString();
        if (!today.equals(soldDay)) { soldDay = today; soldToday.clear(); }
        return soldToday.getOrDefault(p + ":" + e.id, 0);
    }

    // ---------------------------------------------------------------- the shop

    private int pages() { int max = 1; for (Entry e : entries) max = Math.max(max, e.page); return max; }
    private List<Entry> onPage(int page) { List<Entry> l = new ArrayList<>(); for (Entry e : entries) if (e.page == page) l.add(e); return l; }
    private Entry byId(String id) { for (Entry e : entries) if (e.id.equals(id)) return e; return null; }

    void open(Player p, int page, boolean edit) {
        page = Math.max(1, Math.min(page, pages() + (edit ? 1 : 0)));
        View v = new View(page, edit);
        double bal = Money.balance(p.getUniqueId());
        Component title = edit ? Component.text("Server Shop (editing) - page " + page, NamedTextColor.DARK_RED)
                : Component.text("Server Shop  -  " + (bal < 0 ? "?" : Money.fmt(bal)), NamedTextColor.DARK_GRAY);
        Inventory inv = Bukkit.createInventory(v, 54, title);
        v.inv = inv;
        int slot = 0;
        for (Entry e : onPage(page)) {
            if (slot >= PER_PAGE) break;
            inv.setItem(slot, display(p, e, edit));
            v.slotToId.put(slot, e.id);
            slot++;
        }
        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "), List.of());
        for (int s = 45; s < 54; s++) inv.setItem(s, filler);
        if (page > 1) inv.setItem(PREV, button(Material.ARROW, Component.text("Previous page", NamedTextColor.YELLOW), List.of()));
        if (page < pages() || (edit && page == pages() && !onPage(page).isEmpty()))
            inv.setItem(NEXT, button(Material.ARROW, Component.text(edit && page == pages() ? "New page" : "Next page", NamedTextColor.YELLOW), List.of()));
        if (edit) inv.setItem(INFO, button(Material.WRITABLE_BOOK, Component.text("Editing the Server Shop", NamedTextColor.GOLD), List.of(
                plain("Add: hold it (the amount you hold is one lot),", NamedTextColor.GRAY),
                plain("  /shop add <buy> <sell> [daily limit] " + page, NamedTextColor.YELLOW),
                plain("  (0 = can't be bought / can't be sold)", NamedTextColor.DARK_GRAY),
                plain("Left-click: take it out", NamedTextColor.GRAY),
                plain("Right-click: change its prices", NamedTextColor.GRAY),
                plain("Shift-click: move it to the next page", NamedTextColor.GRAY))));
        else inv.setItem(INFO, button(Material.SUNFLOWER, Component.text("You have " + (bal < 0 ? "?" : Money.fmt(bal)), GOLD), List.of(
                plain("Left-click: buy one lot", NamedTextColor.GRAY),
                plain("Shift-left: buy a stack's worth", NamedTextColor.GRAY),
                plain("Right-click: sell one lot", NamedTextColor.GRAY),
                plain("Shift-right: sell all you can", NamedTextColor.GRAY),
                plain("Player trading: /market", NamedTextColor.YELLOW))));
        p.openInventory(inv);
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

    private ItemStack display(Player p, Entry e, boolean edit) {
        ItemStack it = e.lot.clone();
        ItemMeta m = it.getItemMeta();
        List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(e.buy > 0 ? plain("Buy: ", NamedTextColor.YELLOW).append(Component.text(Money.fmt(e.buy), NamedTextColor.GREEN)).append(Component.text("  left-click", NamedTextColor.DARK_GRAY))
                : plain("Can't be bought here", NamedTextColor.DARK_GRAY));
        if (e.sell > 0) {
            lore.add(plain("Sell: ", NamedTextColor.YELLOW).append(Component.text(Money.fmt(e.sell), NamedTextColor.GREEN)).append(Component.text("  right-click", NamedTextColor.DARK_GRAY)));
            if (e.limit > 0) {
                int left = Math.max(0, e.limit - sold(p.getUniqueId(), e));
                lore.add(plain(left > 0 ? "You can sell " + left + " more today" : "You've sold all you can today", left > 0 ? NamedTextColor.GRAY : NamedTextColor.RED));
            }
        } else lore.add(plain("The server doesn't buy this", NamedTextColor.DARK_GRAY));
        if (edit) lore.add(plain("Left: take out · Right: prices · Shift: next page", NamedTextColor.DARK_GRAY));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof View) e.setCancelled(true); }

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof View v)) return;
        ev.setCancelled(true);
        if (!(ev.getWhoClicked() instanceof Player p) || ev.getClickedInventory() != ev.getInventory()) return;
        int slot = ev.getRawSlot();
        if (slot == PREV && v.page > 1) { open(p, v.page - 1, v.edit); return; }
        if (slot == NEXT && ev.getCurrentItem() != null && ev.getCurrentItem().getType() == Material.ARROW) { open(p, v.page + 1, v.edit); return; }
        String id = v.slotToId.get(slot);
        Entry e = id == null ? null : byId(id);
        if (e == null) return;
        if (v.edit) {
            if (!p.hasPermission("soulbound.admin")) return;
            if (ev.isShiftClick()) {
                if (onPage(e.page + 1).size() >= PER_PAGE) { p.sendActionBar(Component.text("That page is full.", NamedTextColor.RED)); return; }
                e.page++; save(); open(p, v.page, true);
            } else if (ev.isRightClick()) {
                priceEdits.put(p.getUniqueId(), new PriceEdit(e.id, System.currentTimeMillis() + 60_000));
                p.closeInventory();
                p.sendMessage(Component.text("Type the new prices in chat: <buy> <sell> [daily limit]  (now " + e.buy + " " + e.sell + " " + e.limit + "), or 'cancel'.", GOLD));
            } else {
                entries.remove(e); save();
                for (ItemStack left : p.getInventory().addItem(e.lot.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
                open(p, v.page, true);
            }
            return;
        }
        if (ev.isRightClick()) sell(p, e, ev.isShiftClick());
        else buy(p, e, ev.isShiftClick());
        open(p, v.page, false);
    }

    private void buy(Player p, Entry e, boolean stack) {
        if (e.buy <= 0) { p.sendActionBar(Component.text("The server doesn't sell this.", NamedTextColor.RED)); return; }
        int lots = stack ? Math.max(1, Math.max(1, e.lot.getMaxStackSize()) / Math.max(1, e.lot.getAmount())) : 1;
        double cost = e.buy * lots;
        if (!Money.take(p.getUniqueId(), cost)) {
            double bal = Money.balance(p.getUniqueId());
            p.sendActionBar(Component.text(bal < 0 ? "The shop can't reach your money right now." : "You need " + Money.fmt(cost - bal) + " more.", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.7f, 1f);
            return;
        }
        for (int i = 0; i < lots; i++) for (ItemStack left : p.getInventory().addItem(e.lot.clone()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        p.sendActionBar(Component.text("Bought " + (e.lot.getAmount() * lots) + "x " + PLAIN.serialize(name(e.lot)) + " for " + Money.fmt(cost) + ".", NamedTextColor.GREEN));
    }

    private void sell(Player p, Entry e, boolean all) {
        if (e.sell <= 0) { p.sendActionBar(Component.text("The server doesn't buy this.", NamedTextColor.RED)); return; }
        ItemStack unit = e.lot.clone(); unit.setAmount(1);
        int per = Math.max(1, e.lot.getAmount());
        int have = 0;
        for (int i = 0; i < 36; i++) { ItemStack it = p.getInventory().getItem(i); if (it != null && it.isSimilar(unit)) have += it.getAmount(); }
        int lots = have / per;
        if (!all) lots = Math.min(lots, 1);
        if (e.limit > 0) lots = Math.min(lots, Math.max(0, e.limit - sold(p.getUniqueId(), e)) / per);
        if (lots <= 0) {
            p.sendActionBar(Component.text(have < per ? "You need " + per + " to sell (matching exactly)." : "You've sold all you can of this today.", NamedTextColor.RED));
            return;
        }
        int remove = lots * per;
        for (int i = 0; i < 36 && remove > 0; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || !it.isSimilar(unit)) continue;
            int take = Math.min(remove, it.getAmount());
            it.setAmount(it.getAmount() - take);
            p.getInventory().setItem(i, it.getAmount() <= 0 ? null : it);
            remove -= take;
        }
        double got = e.sell * lots;
        if (!Money.give(p.getUniqueId(), got)) {
            for (ItemStack left : p.getInventory().addItem(stack(unit, lots * per)).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            p.sendActionBar(Component.text("The shop can't reach your money right now.", NamedTextColor.RED));
            return;
        }
        soldToday.merge(p.getUniqueId() + ":" + e.id, lots * per, Integer::sum);
        save();
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
        p.sendActionBar(Component.text("Sold " + (lots * per) + "x " + PLAIN.serialize(name(e.lot)) + " for " + Money.fmt(got) + ".", NamedTextColor.GREEN));
    }

    private static ItemStack[] stack(ItemStack unit, int qty) {
        List<ItemStack> out = new ArrayList<>();
        int max = Math.max(1, unit.getMaxStackSize());
        while (qty > 0) { ItemStack s = unit.clone(); int n = Math.min(max, qty); s.setAmount(n); out.add(s); qty -= n; }
        return out.toArray(new ItemStack[0]);
    }

    private static Component name(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        return m != null && m.hasDisplayName() && m.displayName() != null ? m.displayName() : Component.translatable(it.translationKey());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent ev) {
        PriceEdit w = priceEdits.remove(ev.getPlayer().getUniqueId());
        if (w == null || w.until() < System.currentTimeMillis()) return;
        ev.setCancelled(true);
        String[] parts = PLAIN.serialize(ev.originalMessage()).trim().split("\\s+");
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Entry e = byId(w.id());
            if (e == null || parts.length == 0 || parts[0].equalsIgnoreCase("cancel")) { p.sendMessage(Component.text("Prices unchanged.", NamedTextColor.GRAY)); return; }
            double buy = Money.parse(parts[0]), sell = parts.length > 1 ? Money.parse(parts[1]) : e.sell;
            int limit = e.limit;
            if (parts.length > 2) try { limit = Math.max(0, Integer.parseInt(parts[2])); } catch (NumberFormatException ignored) { buy = Double.NaN; }
            if (Double.isNaN(buy) || Double.isNaN(sell)) { p.sendMessage(Component.text("Those aren't numbers. Prices unchanged.", NamedTextColor.RED)); return; }
            e.buy = buy; e.sell = sell; e.limit = limit; save();
            p.sendMessage(Component.text("Now: buy " + Money.fmt(buy) + ", sell " + Money.fmt(sell) + (limit > 0 ? ", up to " + limit + " a day" : ", no daily limit") + ".", NamedTextColor.GREEN));
            open(p, e.page, true);
        });
    }

    // ---------------------------------------------------------------- /shop

    @Override
    public boolean onCommand(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        boolean admin = p.hasPermission("soulbound.admin");
        if (!Money.available()) { p.sendMessage(Component.text("The shop can't reach the money system right now.", NamedTextColor.RED)); return true; }
        if (a.length == 0 || !admin) {
            int page = 1;
            if (a.length > 0) try { page = Integer.parseInt(a[0]); } catch (NumberFormatException ignored) {}
            open(p, page, false);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "add" -> {
                ItemStack hand = p.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { p.sendMessage(Component.text("Hold the item (the amount you hold is one lot).", NamedTextColor.RED)); return true; }
                if (a.length < 3) { p.sendMessage(Component.text("Usage: /shop add <buy price> <sell price> [daily sell limit] [page]   (0 = not bought / not sold)", NamedTextColor.RED)); return true; }
                double buy = Money.parse(a[1]), sell = Money.parse(a[2]);
                if (Double.isNaN(buy) || Double.isNaN(sell)) { p.sendMessage(Component.text("The prices have to be numbers.", NamedTextColor.RED)); return true; }
                if (sell > buy && buy > 0) p.sendMessage(Component.text("Careful: it sells for more than it costs, so anyone can buy and sell it for free money.", NamedTextColor.GOLD));
                int limit = 0, page;
                try { if (a.length > 3) limit = Math.max(0, Integer.parseInt(a[3])); } catch (NumberFormatException ex) { p.sendMessage(Component.text("The daily limit has to be a number.", NamedTextColor.RED)); return true; }
                if (a.length > 4) {
                    try { page = Math.max(1, Integer.parseInt(a[4])); } catch (NumberFormatException ex) { p.sendMessage(Component.text("The page has to be a number.", NamedTextColor.RED)); return true; }
                    if (onPage(page).size() >= PER_PAGE) { p.sendMessage(Component.text("Page " + page + " is full.", NamedTextColor.RED)); return true; }
                } else { int last = pages(); page = onPage(last).size() < PER_PAGE ? last : last + 1; }
                Entry e = new Entry();
                e.id = UUID.randomUUID().toString().substring(0, 8);
                e.lot = hand.clone(); e.buy = buy; e.sell = sell; e.limit = limit; e.page = page;
                entries.add(e); save();
                p.sendMessage(Component.text("Added " + hand.getAmount() + "x " + PLAIN.serialize(name(hand)) + ": buy " + Money.fmt(buy) + ", sell " + Money.fmt(sell)
                        + (limit > 0 ? ", up to " + limit + " a day" : "") + " (page " + page + ").", NamedTextColor.GREEN));
            }
            case "edit" -> {
                int page = 1;
                if (a.length > 1) try { page = Integer.parseInt(a[1]); } catch (NumberFormatException ignored) {}
                open(p, page, true);
            }
            case "reload" -> { load(); p.sendMessage(Component.text("Server shop reloaded.", NamedTextColor.GREEN)); }
            case "help" -> {
                p.sendMessage(Component.text("Server Shop", GOLD, TextDecoration.BOLD));
                p.sendMessage(Component.text("/shop [page]  open it", NamedTextColor.GRAY));
                p.sendMessage(Component.text("/shop add <buy> <sell> [daily limit] [page]  hold it; the amount you hold is one lot; 0 = off", NamedTextColor.GRAY));
                p.sendMessage(Component.text("/shop edit [page]  take out, re-price, move", NamedTextColor.GRAY));
            }
            default -> {
                int page = 1;
                try { page = Integer.parseInt(a[0]); } catch (NumberFormatException ignored) {}
                open(p, page, false);
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender s, @NotNull Command cmd, @NotNull String label, String @NotNull [] a) {
        if (!s.hasPermission("soulbound.admin")) return List.of();
        if (a.length == 1) return List.of("add", "edit", "reload", "help").stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
