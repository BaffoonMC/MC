package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Contracts: the Bureau's daily jobs, handed out by Mrs. Sssmith at reception (or /contracts).
 *
 * Each day a player is offered three jobs (four from Manager up), drawn from contracts.jobs in the config:
 *   deliver  bring N of an item and hand it in from the menu
 *   kill     N of a mob
 *   mine     N of a block (blocks players placed don't count)
 *   fish     N catches
 * Sign one to take it on; finish it for Cold Hard Cash (more at higher ranks; to your company's vault if you choose,
 * with a company bonus). Signed jobs left unfinished when the day turns are strikes; enough strikes in a week and
 * you're FIRED: escorted out, barred from the lift for a day, and dropped a rank.
 *
 * Ranks come from jobs done: Intern, Clerk, Manager, Regional Lizard, Director.
 */
final class Contracts implements Listener {
    private static final TextColor GOLD = TextColor.color(0xF0C85A), RED = TextColor.color(0xE05050), GREEN = TextColor.color(0x7BD36A), ICE = Cash.ICE;

    static final class Job {
        String kind, target;   // kind: deliver/kill/mine/fish; target: a Material or EntityType name, or ANY
        int amount, reward, progress;
        boolean signed, done;
    }

    static final class Desk {
        long day = -1;
        final List<Job> jobs = new ArrayList<>();
        int completed;
        final List<Long> strikes = new ArrayList<>();
        boolean payCompany;
        boolean firePending;
    }

    private final Bureau plugin;
    private final Map<UUID, Desk> desks = new HashMap<>();
    private final LinkedHashSet<Location> placed = new LinkedHashSet<>();   // blocks players put down: mining them doesn't count

    Contracts(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("contracts." + k, d); }

    // ---------------------------------------------------------------- ranks

    record Rank(String name, int at) {}

    List<Rank> ranks() {
        List<Rank> r = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("contracts.ranks")) {
            int sp = line.lastIndexOf(' ');
            if (sp < 0) continue;
            try { r.add(new Rank(line.substring(0, sp).trim(), Integer.parseInt(line.substring(sp + 1).trim()))); } catch (NumberFormatException ignored) {}
        }
        if (r.isEmpty()) r.add(new Rank("Intern", 0));
        r.sort((a, b) -> Integer.compare(a.at, b.at));
        return r;
    }

    int rankIndex(int completed) {
        List<Rank> r = ranks();
        int i = 0;
        for (int k = 0; k < r.size(); k++) if (completed >= r.get(k).at) i = k;
        return i;
    }

    String rankName(UUID id) { Desk d = desks.get(id); return ranks().get(rankIndex(d == null ? 0 : d.completed)).name; }

    // ---------------------------------------------------------------- the day's jobs

    Desk desk(UUID id) {
        Desk d = desks.computeIfAbsent(id, k -> new Desk());
        long today = plugin.exchange().today();
        if (d.day != today) rollover(id, d, today);
        return d;
    }

    /** A new day: unfinished signed jobs become strikes, then three (or four) fresh ones. */
    private void rollover(UUID id, Desk d, long today) {
        if (d.day >= 0) {
            long now = System.currentTimeMillis();
            int missed = 0;
            for (Job j : d.jobs) if (j.signed && !j.done) { d.strikes.add(now); missed++; }
            d.strikes.removeIf(t -> now - t > 7 * 86_400_000L);
            if (missed > 0 && d.strikes.size() >= (int) cfg("strikes-to-fire", 3)) d.firePending = true;
        }
        d.day = today;
        d.jobs.clear();
        List<String> pool = new ArrayList<>(plugin.getConfig().getStringList("contracts.jobs"));
        Random r = new Random(today * 31 + id.hashCode());
        int n = rankIndex(d.completed) >= 2 ? (int) cfg("manager-per-day", 4) : (int) cfg("per-day", 3);
        while (d.jobs.size() < n && !pool.isEmpty()) {
            String[] w = pool.remove(r.nextInt(pool.size())).trim().split("\\s+");
            if (w.length < 4) continue;
            try {
                Job j = new Job();
                j.kind = w[0].toLowerCase(Locale.ROOT);
                j.target = w[1].toUpperCase(Locale.ROOT);
                j.amount = Integer.parseInt(w[2]);
                j.reward = Integer.parseInt(w[3]);
                d.jobs.add(j);
            } catch (NumberFormatException ignored) {}
        }
        plugin.dirty();
        Player p = Bukkit.getPlayer(id);
        if (p != null && d.firePending) fireNow(p, d);
    }

    private void fireNow(Player p, Desk d) {
        d.firePending = false;
        d.strikes.clear();
        List<Rank> r = ranks();
        int i = rankIndex(d.completed);
        if (i > 0) d.completed = r.get(i - 1).at;   // down a rank
        plugin.dirty();
        plugin.security().fire(p, "Too many missed contracts. You're back to " + r.get(Math.max(0, i - 1)).name + ".");
    }

    /** Every minute: turn the day over for whoever's online. */
    void minute() {
        for (Player p : Bukkit.getOnlinePlayers()) desk(p.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = e.getPlayer();
            if (!p.isOnline()) return;
            Desk d = desk(p.getUniqueId());
            if (d.firePending) fireNow(p, d);
        }, 60L);
    }

    // ---------------------------------------------------------------- doing the work

    private static boolean matches(String target, String what) {
        return target.equals("ANY") || target.equals(what) || ("DEEPSLATE_" + target).equals(what);
    }

    private void progress(Player p, String kind, String what, int by) {
        Desk d = desk(p.getUniqueId());
        for (Job j : d.jobs) {
            if (!j.signed || j.done || !j.kind.equals(kind) || !matches(j.target, what)) continue;
            j.progress = Math.min(j.amount, j.progress + by);
            plugin.dirty();
            if (j.progress >= j.amount) complete(p, d, j);
            else if (j.progress % Math.max(1, j.amount / 4) == 0)
                p.sendActionBar(Component.text(describe(j) + ": " + j.progress + "/" + j.amount, GREEN));
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && !Walker.isPart(e.getEntity())) progress(k, "kill", e.getEntityType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        placed.add(e.getBlock().getLocation());
        if (placed.size() > 20_000) { var it = placed.iterator(); it.next(); it.remove(); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent e) {
        if (placed.remove(e.getBlock().getLocation())) return;
        progress(e.getPlayer(), "mine", e.getBlock().getType().name(), 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) progress(e.getPlayer(), "fish", "ANY", 1);
    }

    private void complete(Player p, Desk d, Job j) {
        j.done = true;
        d.completed++;
        int before = rankIndex(d.completed - 1), after = rankIndex(d.completed);
        double bonus = 1 + cfg("pay-bonus-per-rank", 0.10) * after;
        String co = plugin.companies().of(p.getUniqueId());
        boolean toCo = d.payCompany && co != null;
        if (toCo) bonus += cfg("company-bonus", 0.10);
        int pay = (int) Math.round(j.reward * bonus);
        if (toCo) plugin.companies().addVault(co, pay);
        else Cash.give(p, pay);
        plugin.log(p.getUniqueId(), "Contract: " + describe(j) + " +" + pay);
        plugin.dirty();
        p.sendMessage(Component.text("✔ Contract complete: ", GREEN).append(Component.text(describe(j), NamedTextColor.WHITE))
                .append(Component.text("  +" + pay + " Cold Hard Cash" + (toCo ? " to " + co : ""), ICE)));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
        if (after > before) {
            String name = ranks().get(after).name;
            p.sendMessage(Component.text("✦ Promoted to ", GOLD).append(Component.text(name, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .append(Component.text(". \"Don't let it go to your head.\"", NamedTextColor.GRAY)));
            Bukkit.broadcast(Component.text("✦ ", GOLD).append(Component.text(p.getName(), NamedTextColor.WHITE))
                    .append(Component.text(" has been promoted to " + name + " at the Bureau.", NamedTextColor.GRAY)));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    static String nice(String s) {
        String t = s.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    static String describe(Job j) {
        return switch (j.kind) {
            case "deliver" -> "Deliver " + j.amount + " " + nice(j.target);
            case "kill" -> "Pest control: " + j.amount + " " + nice(j.target) + (j.amount == 1 ? "" : "s");
            case "mine" -> "Quarry work: " + j.amount + " " + nice(j.target);
            case "fish" -> "Stock the Basking Lounge: " + j.amount + " catches";
            default -> j.kind + " " + j.target;
        };
    }

    private static Material icon(Job j) {
        Material m = switch (j.kind) {
            case "kill" -> Material.matchMaterial(j.target + "_SPAWN_EGG");
            case "fish" -> Material.FISHING_ROD;
            default -> Material.matchMaterial(j.target);
        };
        if (m == null || !m.isItem()) m = j.kind.equals("kill") ? Material.IRON_SWORD : j.kind.equals("mine") ? Material.IRON_PICKAXE : Material.PAPER;
        return m;
    }

    // ---------------------------------------------------------------- the menu

    static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Runnable> actions = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private static Component plain(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    private static ItemStack named(Material m, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.displayName(name.decoration(TextDecoration.ITALIC, false));
        im.lore(lore);
        it.setItemMeta(im);
        return MenuIcons.quiet(it);
    }

    void open(Player p) {
        Menu m = new Menu();
        m.inv = Bukkit.createInventory(m, 27, Component.text("Contracts · Mrs. Sssmith"));
        fill(p, m);
        p.openInventory(m.inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 1f);
    }

    private void refresh(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m) { m.inv.clear(); m.actions.clear(); fill(p, m); }
    }

    private void fill(Player p, Menu m) {
        Desk d = desk(p.getUniqueId());
        List<Rank> r = ranks();
        int ri = rankIndex(d.completed);
        Rank next = ri + 1 < r.size() ? r.get(ri + 1) : null;
        int strikesToFire = (int) cfg("strikes-to-fire", 3);
        m.inv.setItem(4, named(Material.NAME_TAG, plain("Your Employee File", GOLD), List.of(
                plain("Rank: ", NamedTextColor.GRAY).append(plain(r.get(ri).name, NamedTextColor.WHITE)),
                plain("Contracts done: " + d.completed + (next == null ? "" : " (" + next.name + " at " + next.at + ")"), NamedTextColor.GRAY),
                plain("Pay bonus: +" + Math.round(cfg("pay-bonus-per-rank", 0.10) * ri * 100) + "%", NamedTextColor.GRAY),
                plain("Strikes this week: " + d.strikes.size() + " of " + strikesToFire, d.strikes.isEmpty() ? NamedTextColor.GRAY : RED),
                Component.empty(),
                plain("Unfinished signed contracts are strikes.", NamedTextColor.DARK_GRAY),
                plain(strikesToFire + " in a week and you're fired.", NamedTextColor.DARK_GRAY))));
        int[] slots = d.jobs.size() >= 4 ? new int[]{10, 12, 14, 16} : new int[]{11, 13, 15};
        for (int i = 0; i < Math.min(slots.length, d.jobs.size()); i++) {
            Job j = d.jobs.get(i);
            int pay = (int) Math.round(j.reward * (1 + cfg("pay-bonus-per-rank", 0.10) * ri));
            List<Component> lore = new ArrayList<>();
            lore.add(plain("Pays " + pay + " Cold Hard Cash", ICE));
            lore.add(Component.empty());
            if (j.done) lore.add(plain("✔ Done. Nice work.", GREEN));
            else if (!j.signed) { lore.add(plain("Not signed yet.", NamedTextColor.GRAY)); lore.add(plain("» Click to sign it", NamedTextColor.GREEN)); }
            else if (j.kind.equals("deliver")) {
                Material mat = Material.matchMaterial(j.target);
                int have = 0;
                if (mat != null) for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == mat && plugin.items().id(it) == null && !Cash.is(it)) have += it.getAmount();
                lore.add(plain("Signed. You're carrying " + have + "/" + j.amount + ".", NamedTextColor.GRAY));
                lore.add(plain(have >= j.amount ? "» Click to hand it in" : "Come back when you have them all.", have >= j.amount ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            } else lore.add(plain("Signed. Progress: " + j.progress + "/" + j.amount, NamedTextColor.GRAY));
            ItemStack it = named(icon(j), plain(describe(j), j.done ? GREEN : NamedTextColor.WHITE), lore);
            if (j.done) { ItemMeta im = it.getItemMeta(); im.setEnchantmentGlintOverride(true); it.setItemMeta(im); }
            m.inv.setItem(slots[i], it);
            m.actions.put(slots[i], () -> click(p, j));
        }
        String co = plugin.companies().of(p.getUniqueId());
        if (co != null) {
            m.inv.setItem(22, named(d.payCompany ? Material.LIME_DYE : Material.GRAY_DYE, plain("Pay goes to: " + (d.payCompany ? co : "you"), GOLD), List.of(
                    plain("Working for your company pays " + Math.round(cfg("company-bonus", 0.10) * 100) + "% more,", NamedTextColor.GRAY),
                    plain("straight into its vault.", NamedTextColor.GRAY),
                    Component.empty(), plain("» Click to switch", NamedTextColor.GREEN))));
            m.actions.put(22, () -> { d.payCompany = !d.payCompany; plugin.dirty(); refresh(p); });
        }
        m.inv.setItem(26, named(Material.BARRIER, plain("Leave", RED), List.of(plain("New contracts every day.", NamedTextColor.DARK_GRAY))));
        m.actions.put(26, p::closeInventory);
    }

    private void click(Player p, Job j) {
        Desk d = desk(p.getUniqueId());
        if (!d.jobs.contains(j) || j.done) return;
        if (!j.signed) {
            j.signed = true;
            plugin.dirty();
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PUT, 1f, 1.2f);
            p.sendMessage(Component.text("Signed: ", GOLD).append(Component.text(describe(j), NamedTextColor.WHITE))
                    .append(Component.text(". Finish it today or it's a strike.", NamedTextColor.GRAY)));
        } else if (j.kind.equals("deliver")) {
            Material mat = Material.matchMaterial(j.target);
            if (mat == null) return;
            int need = j.amount;
            int have = 0;
            ItemStack[] inv = p.getInventory().getStorageContents();
            for (ItemStack it : inv) if (it != null && it.getType() == mat && plugin.items().id(it) == null && !Cash.is(it)) have += it.getAmount();
            if (have < need) { p.sendActionBar(Component.text("You need " + need + " " + nice(j.target) + " (you have " + have + ").", RED)); return; }
            for (int i = 0; i < inv.length && need > 0; i++) {
                ItemStack it = inv[i];
                if (it == null || it.getType() != mat || plugin.items().id(it) != null || Cash.is(it)) continue;
                int t = Math.min(need, it.getAmount());
                need -= t;
                if (t == it.getAmount()) inv[i] = null; else it.setAmount(it.getAmount() - t);
            }
            p.getInventory().setStorageContents(inv);
            j.progress = j.amount;
            complete(p, d, j);
        }
        refresh(p);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Runnable r = m.actions.get(e.getRawSlot());
        if (r != null) r.run();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) { if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true); }

    // ---------------------------------------------------------------- saving

    void load(ConfigurationSection s) {
        desks.clear();
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection c = s.getConfigurationSection(k);
            if (c == null) continue;
            try {
                Desk d = new Desk();
                d.day = c.getLong("day", -1);
                d.completed = c.getInt("completed");
                d.payCompany = c.getBoolean("pay-company");
                d.firePending = c.getBoolean("fire-pending");
                d.strikes.addAll(c.getLongList("strikes"));
                for (String line : c.getStringList("jobs")) {
                    String[] w = line.split(" ");
                    if (w.length < 7) continue;
                    Job j = new Job();
                    j.kind = w[0]; j.target = w[1]; j.amount = Integer.parseInt(w[2]); j.reward = Integer.parseInt(w[3]);
                    j.progress = Integer.parseInt(w[4]); j.signed = Boolean.parseBoolean(w[5]); j.done = Boolean.parseBoolean(w[6]);
                    d.jobs.add(j);
                }
                desks.put(UUID.fromString(k), d);
            } catch (RuntimeException ignored) {}
        }
    }

    void save(ConfigurationSection s) {
        for (var e : desks.entrySet()) {
            Desk d = e.getValue();
            ConfigurationSection c = s.createSection(e.getKey().toString());
            c.set("day", d.day);
            c.set("completed", d.completed);
            c.set("pay-company", d.payCompany);
            c.set("fire-pending", d.firePending);
            c.set("strikes", d.strikes);
            List<String> jobs = new ArrayList<>();
            for (Job j : d.jobs) jobs.add(j.kind + " " + j.target + " " + j.amount + " " + j.reward + " " + j.progress + " " + j.signed + " " + j.done);
            c.set("jobs", jobs);
        }
    }

    @SuppressWarnings("unused")
    private static boolean isMob(String s) { try { EntityType.valueOf(s); return true; } catch (IllegalArgumentException e) { return false; } }
}
