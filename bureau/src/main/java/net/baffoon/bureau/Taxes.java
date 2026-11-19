package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tax on Exchange profits. Each player's Cash has a cost basis (what they paid at the Exchange, on average); selling
 * above it is profit, and the Bureau wants profit-rate of it. The bill then runs:
 *
 *   owed       a notice: pay within notice-hours
 *   final      a Final Notice, interest added: pay within final-hours
 *   assigned   the Taxman is on the case (see Taxman)
 *
 * Paying (/tax pay) takes money first, then Cold Hard Cash (pockets, then vault) at the sell price. Anything the
 * Taxman impounded comes back the moment the bill is cleared.
 */
final class Taxes implements Listener {
    static final TextColor RED = TextColor.color(0xE05050), GOLD = TextColor.color(0xF0C85A);
    enum Stage { NONE, OWED, FINAL, ASSIGNED }

    static final class Case {
        double owed;           // in server money
        int basisUnits;        // notes bought at the Exchange, not yet sold
        double basisCost;      // what those cost
        long openedAt;         // when the current bill began
        Stage stage = Stage.NONE;
        long lastHunt;         // when the Taxman last went out after them
        final List<ItemStack> impound = new ArrayList<>();
    }

    private final Bureau plugin;
    private final Map<UUID, Case> cases = new HashMap<>();

    Taxes(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("tax." + k, d); }

    Case get(UUID id) { return cases.computeIfAbsent(id, k -> new Case()); }
    Case peek(UUID id) { return cases.get(id); }
    Iterable<Map.Entry<UUID, Case>> all() { return cases.entrySet(); }

    // ---------------------------------------------------------------- the Exchange reports in

    void bought(UUID id, int n, double cost) {
        Case c = get(id);
        c.basisUnits += n;
        c.basisCost += cost;
        plugin.dirty();
    }

    /** Selling n for got: tax on the profit over what those notes cost them. Notes they never bought have no basis (no profit counted). */
    void sold(Player p, int n, double got) {
        Case c = get(p.getUniqueId());
        int from = Math.min(n, c.basisUnits);
        if (from <= 0) return;
        double avg = c.basisCost / c.basisUnits;
        double profit = got * from / n - avg * from;
        c.basisUnits -= from;
        c.basisCost = Math.max(0, c.basisCost - avg * from);
        plugin.dirty();
        if (profit <= 0) return;
        double rate = plugin.items().carrying(p, "accountants_ledger") ? cfg("ledger-rate", 0.07) : cfg("profit-rate", 0.10);
        double tax = Exchange.round(profit * rate);
        if (tax < 0.01) return;
        add(p.getUniqueId(), tax);
        p.sendMessage(Component.text("✎ ", GOLD).append(Component.text("Profit of " + Money.fmt(profit) + ". The Bureau will be wanting "
                + Money.fmt(tax) + " of that. ", NamedTextColor.GRAY)).append(payButton()));
    }

    void add(UUID id, double amount) {
        Case c = get(id);
        if (c.owed <= 0) { c.openedAt = System.currentTimeMillis(); c.stage = Stage.OWED; }
        c.owed = Exchange.round(c.owed + amount);
        plugin.dirty();
    }

    void set(UUID id, double amount) {
        Case c = get(id);
        c.owed = Exchange.round(Math.max(0, amount));
        if (c.owed <= 0) clear(id);
        else { if (c.stage == Stage.NONE) { c.stage = Stage.OWED; c.openedAt = System.currentTimeMillis(); } plugin.dirty(); }
    }

    void assign(UUID id) {
        Case c = get(id);
        if (c.owed <= 0) return;
        c.stage = Stage.ASSIGNED;
        c.lastHunt = 0;
        plugin.dirty();
        Player p = Bukkit.getPlayer(id);
        if (p != null) assignedNotice(p, c);
    }

    void clear(UUID id) {
        Case c = get(id);
        c.owed = 0;
        c.stage = Stage.NONE;
        Player p = Bukkit.getPlayer(id);
        if (!c.impound.isEmpty() && p != null) {
            for (ItemStack it : c.impound) for (ItemStack l : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
            p.sendMessage(Component.text("The Bureau returns your impounded things.", NamedTextColor.GRAY));
            c.impound.clear();
        }
        plugin.taxman().endHunt(id, null);
        plugin.dirty();
    }

    boolean exempt(Player p, Case c) {
        if (c.owed < cfg("min-debt", 25)) return true;
        int hours = p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 3600;
        return hours < cfg("min-hours-played", 5);
    }

    // ---------------------------------------------------------------- paying

    Component payButton() {
        return Component.text("[Pay now]", NamedTextColor.GREEN, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/tax pay"))
                .hoverEvent(HoverEvent.showText(Component.text("Money first, then Cold Hard Cash", NamedTextColor.GRAY)));
    }

    /** Pay as much as they can: money, then Cash in pockets, then the vault. True once nothing's left owing. */
    boolean pay(Player p) {
        Case c = peek(p.getUniqueId());
        if (c == null || c.owed <= 0) { p.sendMessage(Component.text("You owe the Bureau nothing. For now.", NamedTextColor.GRAY)); return true; }
        double before = c.owed;
        collect(p, c, false);
        double paid = before - c.owed;
        if (c.owed <= 0.009) {
            clear(p.getUniqueId());
            p.sendMessage(Component.text("✦ ", GOLD).append(Component.text("Paid " + Money.fmt(paid) + ". Your account with the Bureau is settled.", NamedTextColor.GREEN)));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.5f);
            return true;
        }
        p.sendMessage(Component.text(paid > 0 ? "Paid " + Money.fmt(paid) + ". Still owing " + Money.fmt(c.owed) + "."
                : "You haven't the money or the Cash to pay. Still owing " + Money.fmt(c.owed) + ".", RED));
        return false;
    }

    /** Take what's owed: money, then Cash (pockets, vault) at the sell price; if seize, then backpack items into impound. */
    void collect(Player p, Case c, boolean seize) {
        UUID id = p.getUniqueId();
        double bal = Money.balance(id);
        if (bal > 0) {
            double take = Math.min(bal, c.owed);
            if (Money.take(id, take)) c.owed = Exchange.round(c.owed - take);
        }
        double note = Math.max(0.01, plugin.exchange().sellPrice());
        if (c.owed > 0) {
            int need = (int) Math.ceil(c.owed / note);
            int fromPockets = Math.min(need, Cash.count(p));
            if (fromPockets > 0 && Cash.take(p, fromPockets)) c.owed = Exchange.round(Math.max(0, c.owed - fromPockets * note));
        }
        if (c.owed > 0) {
            int need = (int) Math.ceil(c.owed / note);
            int fromVault = Math.min(need, plugin.vaults().held(id));
            if (fromVault > 0) { plugin.vaults().add(id, -fromVault); c.owed = Exchange.round(Math.max(0, c.owed - fromVault * note)); }
        }
        if (c.owed > 0 && seize) {
            // backpack slots only (9-35): never the hotbar, armour or off-hand
            int max = plugin.getConfig().getInt("taxman.impound-stacks", 3), took = 0;
            ItemStack[] inv = p.getInventory().getStorageContents();
            for (int i = 35; i >= 9 && took < max; i--) {
                if (inv[i] == null || inv[i].getType().isAir()) continue;
                c.impound.add(inv[i].clone());
                inv[i] = null;
                took++;
            }
            p.getInventory().setStorageContents(inv);
            if (took > 0) p.sendMessage(Component.text("The Taxman impounds " + took + " stack" + (took == 1 ? "" : "s") + " from your backpack. They come back when you pay.", RED));
        }
        plugin.dirty();
    }

    // ---------------------------------------------------------------- the clock: notices and the Final Notice

    void tick() {
        long now = System.currentTimeMillis();
        long notice = (long) (cfg("notice-hours", 72) * 3_600_000L), fin = (long) (cfg("final-hours", 48) * 3_600_000L);
        for (var e : cases.entrySet()) {
            Case c = e.getValue();
            if (c.owed <= 0 || c.stage == Stage.NONE) continue;
            if (c.stage == Stage.OWED && now - c.openedAt > notice) {
                c.owed = Exchange.round(c.owed * (1 + cfg("interest", 0.25)));
                c.stage = Stage.FINAL;
                plugin.dirty();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) finalNotice(p, c);
            } else if (c.stage == Stage.FINAL && now - c.openedAt > notice + fin) {
                assign(e.getKey());
            }
        }
    }

    private void finalNotice(Player p, Case c) {
        p.showTitle(Title.title(Component.text("FINAL NOTICE", RED, TextDecoration.BOLD), Component.text("You owe the Bureau " + Money.fmt(c.owed), NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
        p.sendMessage(Component.text("✉ FINAL NOTICE: ", RED, TextDecoration.BOLD).append(Component.text("interest has been added. You owe "
                + Money.fmt(c.owed) + ". Pay within " + (int) cfg("final-hours", 48) + " hours or the Taxman is assigned. ", NamedTextColor.GRAY)).append(payButton()));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
    }

    private void assignedNotice(Player p, Case c) {
        p.sendMessage(Component.text("☠ ", RED).append(Component.text("The Taxman has been assigned to your case. ", RED, TextDecoration.BOLD))
                .append(Component.text("He'll find you when you're out in the wild. Owed: " + Money.fmt(c.owed) + ". ", NamedTextColor.GRAY)).append(payButton()));
        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.4f, 1.4f);
    }

    /** Status line for /tax and the taxman lizard. */
    void status(Player p) {
        Case c = peek(p.getUniqueId());
        if (c == null || c.owed <= 0) {
            p.sendMessage(Component.text("✎ You owe the Bureau nothing.", NamedTextColor.GRAY)
                    .append(Component.text(" Profits on the Exchange are taxed at " + (int) Math.round(cfg("profit-rate", 0.10) * 100) + "%.", NamedTextColor.DARK_GRAY)));
            return;
        }
        long now = System.currentTimeMillis();
        long notice = (long) (cfg("notice-hours", 72) * 3_600_000L), fin = (long) (cfg("final-hours", 48) * 3_600_000L);
        String when = switch (c.stage) {
            case OWED -> "Pay within " + hm(c.openedAt + notice - now) + ", or interest is added.";
            case FINAL -> "FINAL NOTICE. Pay within " + hm(c.openedAt + notice + fin - now) + ", or the Taxman is assigned.";
            case ASSIGNED -> "The Taxman is on your case.";
            default -> "";
        };
        p.sendMessage(Component.text("✎ You owe the Bureau " + Money.fmt(c.owed) + ". ", c.stage == Stage.OWED ? NamedTextColor.YELLOW : RED)
                .append(Component.text(when + " ", NamedTextColor.GRAY)).append(payButton()));
        if (!c.impound.isEmpty()) p.sendMessage(Component.text(" " + c.impound.size() + " impounded stack" + (c.impound.size() == 1 ? "" : "s") + " come back when you pay.", NamedTextColor.DARK_GRAY));
    }

    private static String hm(long ms) {
        long m = Math.max(1, ms / 60_000), h = m / 60;
        return h > 0 ? h + "h " + (m % 60) + "m" : m + "m";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Case c = peek(e.getPlayer().getUniqueId());
        if (c == null || c.owed <= 0) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (e.getPlayer().isOnline()) status(e.getPlayer()); }, 80L);
    }

    // ---------------------------------------------------------------- saving

    void load(ConfigurationSection d) {
        cases.clear();
        if (d == null) return;
        for (String k : d.getKeys(false)) {
            ConfigurationSection s = d.getConfigurationSection(k);
            if (s == null) continue;
            try {
                Case c = new Case();
                c.owed = s.getDouble("owed");
                c.basisUnits = s.getInt("basis-units");
                c.basisCost = s.getDouble("basis-cost");
                c.openedAt = s.getLong("opened");
                c.lastHunt = s.getLong("last-hunt");
                try { c.stage = Stage.valueOf(s.getString("stage", "NONE")); } catch (IllegalArgumentException ignored) {}
                for (Object o : s.getList("impound", List.of())) if (o instanceof ItemStack it) c.impound.add(it);
                cases.put(UUID.fromString(k), c);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    void save(ConfigurationSection d) {
        for (var e : cases.entrySet()) {
            Case c = e.getValue();
            if (c.owed <= 0 && c.basisUnits <= 0 && c.impound.isEmpty()) continue;
            ConfigurationSection s = d.createSection(e.getKey().toString());
            s.set("owed", c.owed);
            s.set("basis-units", c.basisUnits);
            s.set("basis-cost", c.basisCost);
            s.set("opened", c.openedAt);
            s.set("stage", c.stage.name());
            s.set("last-hunt", c.lastHunt);
            s.set("impound", new ArrayList<>(c.impound));
        }
    }
}
