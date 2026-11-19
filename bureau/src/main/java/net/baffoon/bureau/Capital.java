package net.baffoon.bureau;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cold-Blooded Capital: the Exchange menu. A 6-row chest painted over by the pack (bureau:gui font, glyph ), with a
 * live ticker written into the painted strip (bureau:ticker font, drawn 88 px lower).
 *
 *   top row      chart panel (0-2) · the Director's eye (4) · treasury panel (6-8)
 *   left 3x3     buy 1/8/64 · sell 1/8/all · vault: deposit all, withdraw 8, withdraw all
 *   middle       the ledger (12-14, 21-23, 30-32)
 *   right 3x3    the lizard shop (from config)
 *   bottom row   leave (45) · notices (49) · your money (53)
 */
final class Capital implements Listener {
    private static final TextColor GOLD = TextColor.color(0xF0C85A), ICE = Cash.ICE;
    private static final NamespacedKey INVISIBLE = new NamespacedKey("bureau", "invisible");
    private static final int[] SHOP = {15, 16, 17, 24, 25, 26, 33, 34, 35};
    private final Bureau plugin;

    Capital(Bureau plugin) { this.plugin = plugin; }

    static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<Integer, Runnable> actions = new HashMap<>();
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    private static Component plain(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }

    private Component title() {
        Exchange ex = plugin.exchange();
        Key gui = Key.key("bureau", "gui"), tick = Key.key("bureau", "ticker");
        double ch = ex.change() * 100;
        Component t = Component.text("").font(gui).color(NamedTextColor.WHITE)
                .append(Component.text("CASH ", GOLD).font(tick))
                .append(Component.text(Money.fmt(ex.price) + " ", NamedTextColor.WHITE).font(tick))
                .append(Component.text(String.format(Locale.ROOT, "%+.1f%%", ch), ch >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED).font(tick));
        if (!ex.event.isEmpty()) t = t.append(Component.text("  " + ex.event, ex.event.equals("RALLY") ? NamedTextColor.GREEN : NamedTextColor.RED).font(tick));
        else t = t.append(Component.text("  H" + (int) Math.round(ex.high()) + " L" + (int) Math.round(ex.low()), NamedTextColor.GRAY).font(tick));
        return t;
    }

    /** A slot over painted art: draws nothing, but still has a tooltip. */
    private static ItemStack hover(Component name, List<Component> lore) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.setItemModel(INVISIBLE);
        m.setMaxStackSize(1);
        it.setItemMeta(m);
        return MenuIcons.quiet(it);
    }

    private static ItemStack button(Material mat, int amount, Component name, List<Component> lore) {
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(64, amount)));
        ItemMeta m = it.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        it.setItemMeta(m);
        return MenuIcons.quiet(it);
    }

    private static ItemStack cashButton(int amount, Component name, List<Component> lore) {
        ItemStack it = Cash.item(amount);
        ItemMeta m = it.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        it.setItemMeta(m);
        return MenuIcons.quiet(it);
    }

    void open(Player p) {
        Menu menu = new Menu();
        menu.inv = Bukkit.createInventory(menu, 54, title());
        fill(p, menu);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.7f, 0.8f);
    }

    private void refresh(Player p) {
        if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu m) { m.inv.clear(); m.actions.clear(); fill(p, m); }
    }

    private void fill(Player p, Menu m) {
        Exchange ex = plugin.exchange();
        Vaults vaults = plugin.vaults();
        var id = p.getUniqueId();
        int pockets = Cash.count(p), held = vaults.held(id);
        double buy = ex.buyPrice(), sell = ex.sellPrice();
        int left = plugin.buyLeft(id);

        // top row: the chart, the eye, the treasury
        List<Component> chart = List.of(
                plain(ex.sparkline(24), NamedTextColor.GREEN),
                plain("Last 12 hours, every half hour.", NamedTextColor.DARK_GRAY),
                Component.empty(),
                plain("High " + Money.fmt(ex.high()) + "   Low " + Money.fmt(ex.low()), NamedTextColor.GRAY));
        for (int s : new int[]{0, 1, 2}) m.inv.setItem(s, hover(plain("The Chart", GOLD), chart));
        String mood = ex.event.equals("RALLY") ? "Delighted. Business is booming." : ex.event.equals("CRASH") ? "Furious. Somebody will be fired."
                : ex.change() > 0.05 ? "Pleased. Do not get comfortable." : ex.change() < -0.05 ? "Displeased. Very displeased." : "Cold. As always.";
        m.inv.setItem(4, hover(plain("Director Sssterling", GOLD), List.of(
                plain("The eye sees all.", NamedTextColor.DARK_GRAY),
                Component.empty(),
                plain("Mood: ", NamedTextColor.GRAY).append(plain(mood, NamedTextColor.WHITE)),
                plain("Cold Hard Cash: ", NamedTextColor.GRAY).append(plain(Money.fmt(ex.price), ICE)),
                plain("Today: ", NamedTextColor.GRAY).append(plain(String.format(Locale.ROOT, "%+.1f%%", ex.change() * 100), ex.change() >= 0 ? NamedTextColor.GREEN : NamedTextColor.RED)),
                plain("You buy at " + Money.fmt(buy) + ", sell at " + Money.fmt(sell) + ".", NamedTextColor.DARK_GRAY))));
        List<Component> treasury = List.of(
                plain("Cash in the Bureau's vaults: ", NamedTextColor.GRAY).append(plain(String.valueOf(vaults.total()), ICE)),
                plain("The lizards' cut: 5% on every trade.", NamedTextColor.DARK_GRAY));
        for (int s : new int[]{6, 7, 8}) m.inv.setItem(s, hover(plain("The Treasury", GOLD), treasury));

        // buy
        int[][] buys = {{9, 1}, {10, 8}, {11, 64}};
        for (int[] b : buys) {
            int n = b[1];
            m.inv.setItem(b[0], cashButton(n, plain("Buy " + n + " Cold Hard Cash", NamedTextColor.GREEN), List.of(
                    plain("Costs " + Money.fmt(buy * n), NamedTextColor.GRAY),
                    plain("You can buy " + left + " more today.", NamedTextColor.DARK_GRAY),
                    Component.empty(), plain("» Click to buy", NamedTextColor.GREEN))));
            m.actions.put(b[0], () -> buy(p, n));
        }
        // sell
        int[][] sells = {{18, 1}, {19, 8}, {20, -1}};
        for (int[] s : sells) {
            int n = s[1] < 0 ? pockets : s[1];
            String label = s[1] < 0 ? "Sell all (" + pockets + ")" : "Sell " + n;
            m.inv.setItem(s[0], button(Material.GOLD_INGOT, Math.max(1, Math.min(64, n)), plain(label, NamedTextColor.YELLOW), List.of(
                    plain("Get " + Money.fmt(sell * n), NamedTextColor.GRAY),
                    plain("You're carrying " + pockets + ".", NamedTextColor.DARK_GRAY),
                    Component.empty(), plain("» Click to sell", NamedTextColor.YELLOW))));
            m.actions.put(s[0], () -> sell(p, s[1] < 0 ? Cash.count(p) : s[1]));
        }
        // vault
        double fee = plugin.getConfig().getDouble("vault.withdraw-fee", 0.02);
        m.inv.setItem(27, button(Material.CHEST, 1, plain("Deposit all", ICE), List.of(
                plain("Lock your Cash in the Bureau's freezer.", NamedTextColor.GRAY),
                plain("Nobody can steal it from there.", NamedTextColor.DARK_GRAY),
                Component.empty(), plain("In the vault: " + held, ICE), plain("» Click to deposit " + pockets, NamedTextColor.AQUA))));
        m.actions.put(27, () -> deposit(p));
        int[][] outs = {{28, 8}, {29, -1}};
        for (int[] o : outs) {
            int n = o[1] < 0 ? Vaults.maxWithdraw(held, fee) : o[1];
            m.inv.setItem(o[0], button(Material.HOPPER, Math.max(1, Math.min(64, n)), plain(o[1] < 0 ? "Withdraw all" : "Withdraw " + n, ICE), List.of(
                    plain("A " + (int) Math.round(fee * 100) + "% handling fee, paid from the vault.", NamedTextColor.GRAY),
                    Component.empty(), plain("In the vault: " + held, ICE), plain("» Click to withdraw " + n, NamedTextColor.AQUA))));
            m.actions.put(o[0], () -> withdraw(p, o[1] < 0 ? Vaults.maxWithdraw(plugin.vaults().held(id), fee) : o[1]));
        }

        // the ledger
        List<Component> lines = new ArrayList<>();
        lines.add(plain("Your last dealings:", NamedTextColor.GRAY));
        List<String> led = plugin.ledger(id);
        if (led.isEmpty()) lines.add(plain(" nothing yet", NamedTextColor.DARK_GRAY));
        for (String l : led) lines.add(plain(" " + l, NamedTextColor.WHITE));
        for (int s : new int[]{12, 13, 14, 21, 22, 23, 30, 31, 32}) m.inv.setItem(s, hover(plain("The Ledger", GOLD), lines));

        // the shop
        List<String> shop = plugin.getConfig().getStringList("shop");
        for (int i = 0; i < Math.min(SHOP.length, shop.size()); i++) {
            String[] w = shop.get(i).trim().split("\\s+");
            if (w.length < 3) continue;
            Material mat = Material.matchMaterial(w[0]);
            if (mat == null || !mat.isItem()) continue;
            int amount, price;
            try { amount = Integer.parseInt(w[1]); price = Integer.parseInt(w[2]); } catch (NumberFormatException e) { continue; }
            ItemStack show = new ItemStack(mat, Math.max(1, Math.min(mat.getMaxStackSize(), amount)));
            ItemMeta im = show.getItemMeta();
            im.lore(List.of(
                    plain("Price: ", NamedTextColor.GRAY).append(plain(price + " Cold Hard Cash", ICE)),
                    plain("Paid from your pockets, then your vault.", NamedTextColor.DARK_GRAY),
                    Component.empty(), plain("» Click to buy", NamedTextColor.GREEN)));
            show.setItemMeta(im);
            m.inv.setItem(SHOP[i], show);
            int amt = amount, pr = price;
            m.actions.put(SHOP[i], () -> shopBuy(p, mat, amt, pr));
        }

        // bottom row
        m.inv.setItem(45, button(Material.BARRIER, 1, plain("Leave", NamedTextColor.RED), List.of(plain("Mind the door.", NamedTextColor.DARK_GRAY))));
        m.actions.put(45, p::closeInventory);
        m.inv.setItem(49, button(Material.PAPER, 1, plain("Bureau Notices", GOLD), List.of(
                plain("Contracts, companies and the Taxman", NamedTextColor.GRAY),
                plain("are coming to the Bureau soon.", NamedTextColor.GRAY),
                Component.empty(), plain("\"Prepare your paperwork.\"", NamedTextColor.DARK_GRAY))));
        double bal = Money.balance(id);
        m.inv.setItem(53, cashButton(Math.max(1, Math.min(64, pockets)), plain("Your Money", GOLD), List.of(
                plain("Pockets: ", NamedTextColor.GRAY).append(plain(pockets + " Cold Hard Cash", ICE)),
                plain("Vault: ", NamedTextColor.GRAY).append(plain(held + " Cold Hard Cash", ICE)),
                plain("Balance: ", NamedTextColor.GRAY).append(plain(bal < 0 ? "?" : Money.fmt(bal), NamedTextColor.GREEN)),
                Component.empty(),
                plain("Worth about " + Money.fmt((pockets + held) * sell) + " at today's price.", NamedTextColor.DARK_GRAY))));
    }

    // ---------------------------------------------------------------- the deals

    private void no(Player p, String msg) {
        p.sendMessage(Component.text(msg, NamedTextColor.RED));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.6f, 0.7f);
    }

    private void ok(Player p, String msg) {
        p.sendMessage(Component.text("✦ ", GOLD).append(Component.text(msg, NamedTextColor.GRAY)));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }

    private void buy(Player p, int n) {
        if (!Money.available()) { no(p, "The Exchange is closed (no money plugin)."); return; }
        int left = plugin.buyLeft(p.getUniqueId());
        if (left <= 0) { no(p, "That's all the Cash you can buy today. The lizards have limits. Come back tomorrow."); return; }
        n = Math.min(n, left);
        double cost = Exchange.round(plugin.exchange().buyPrice() * n);
        if (!Money.take(p.getUniqueId(), cost)) { no(p, "You need " + Money.fmt(cost) + " for that."); return; }
        Cash.give(p, n);
        plugin.bought(p.getUniqueId(), n);
        plugin.taxes().bought(p.getUniqueId(), n, cost);
        plugin.exchange().pressure(n, true);
        plugin.log(p.getUniqueId(), "Bought " + n + " for " + Money.fmt(cost));
        ok(p, "Bought " + n + " Cold Hard Cash for " + Money.fmt(cost) + ".");
        refresh(p);
    }

    private void sell(Player p, int n) {
        if (!Money.available()) { no(p, "The Exchange is closed (no money plugin)."); return; }
        if (n <= 0 || Cash.count(p) < n) { no(p, "You're not carrying that much Cold Hard Cash."); return; }
        double got = Exchange.round(plugin.exchange().sellPrice() * n);
        if (!Cash.take(p, n)) return;
        if (!Money.give(p.getUniqueId(), got)) { Cash.give(p, n); no(p, "The teller couldn't pay you. Try again later."); return; }
        plugin.exchange().pressure(n, false);
        plugin.taxes().sold(p, n, got);
        plugin.log(p.getUniqueId(), "Sold " + n + " for " + Money.fmt(got));
        ok(p, "Sold " + n + " Cold Hard Cash for " + Money.fmt(got) + ".");
        refresh(p);
    }

    private void deposit(Player p) {
        int n = Cash.count(p);
        if (n <= 0) { no(p, "You've no Cold Hard Cash on you to deposit."); return; }
        if (!Cash.take(p, n)) return;
        plugin.vaults().add(p.getUniqueId(), n);
        plugin.log(p.getUniqueId(), "Deposited " + n);
        ok(p, "Deposited " + n + " Cold Hard Cash. It's in the freezer now.");
        p.playSound(p.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 0.8f);
        refresh(p);
    }

    private void withdraw(Player p, int n) {
        double feeRate = plugin.getConfig().getDouble("vault.withdraw-fee", 0.02);
        int held = plugin.vaults().held(p.getUniqueId());
        int fee = Vaults.fee(n, feeRate);
        if (n <= 0 || n + fee > held) { no(p, "Your vault doesn't hold enough for that (" + held + " inside, " + fee + " fee)."); return; }
        plugin.vaults().add(p.getUniqueId(), -(n + fee));
        Cash.give(p, n);
        plugin.log(p.getUniqueId(), "Withdrew " + n + " (fee " + fee + ")");
        ok(p, "Withdrew " + n + " Cold Hard Cash" + (fee > 0 ? " (the lizards kept " + fee + ")." : "."));
        refresh(p);
    }

    private void shopBuy(Player p, Material mat, int amount, int price) {
        int pockets = Cash.count(p), held = plugin.vaults().held(p.getUniqueId());
        if (pockets + held < price) { no(p, "That costs " + price + " Cold Hard Cash. You have " + (pockets + held) + "."); return; }
        int fromPockets = Math.min(pockets, price);
        Cash.take(p, fromPockets);
        if (price - fromPockets > 0) plugin.vaults().add(p.getUniqueId(), -(price - fromPockets));
        for (ItemStack l : p.getInventory().addItem(new ItemStack(mat, amount)).values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        plugin.log(p.getUniqueId(), "Bought " + amount + " " + mat.getKey().getKey().replace('_', ' ') + " for " + price);
        ok(p, "Bought " + amount + " " + mat.getKey().getKey().replace('_', ' ') + " for " + price + " Cold Hard Cash.");
        refresh(p);
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu m)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        Runnable r = m.actions.get(e.getRawSlot());
        if (r != null && e.getWhoClicked() instanceof Player) r.run();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true);
    }

    /** Cash is paper underneath: don't let anyone fold it into books or maps. */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) if (Cash.is(it)) { e.getInventory().setResult(null); return; }
    }
}
