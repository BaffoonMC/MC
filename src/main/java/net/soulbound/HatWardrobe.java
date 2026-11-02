package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * The Hat Wardrobe (/wardrobe, /hats).
 * - Every player has a wardrobe that keeps their hats safe. Click a hat in your own inventory to put it away; click a
 *   hat in the wardrobe to take it out (left) or put it straight on (right). Your current hat goes back in.
 * - Hat Tokens (bought on the store) unlock hats: open the catalogue, pick one, click twice, and a brand-new hat is put
 *   in your wardrobe. Duplicates are allowed, so you can unlock one to sell.
 * - Hats stay real items. To sell one, hold it and /wardrobe sell <player> <price>: they get a [Buy] button, and the
 *   hat and the payment (diamonds by default) swap hands only when both sides still have them.
 * Nothing is ever copied: every move takes the item from one place before it appears in the other, and the wardrobe
 * is shut in creative mode (a creative player could clone a hat with middle-click).
 */
final class HatWardrobe implements Listener, CommandExecutor, TabCompleter {
    private static final TextColor GOLD = TextColor.color(0xF2C04A);
    private static final int PER_PAGE = 45;

    private final Soulbound plugin;
    private final NamespacedKey tokenKey, hatKey;
    private final File dir;
    private final Map<UUID, List<ItemStack>> cache = new HashMap<>();
    private final Map<UUID, Offer> offers = new HashMap<>();   // keyed by the buyer
    private Supplier<Map<String, SbCommand.Item>> registry = Map::of;

    record Offer(UUID seller, ItemStack hat, int price, long until) {}

    HatWardrobe(Soulbound plugin) {
        this.plugin = plugin;
        this.tokenKey = new NamespacedKey(plugin, "hat_token");
        this.hatKey = new NamespacedKey(plugin, "wardrobe_hat");
        this.dir = new File(plugin.getDataFolder(), "wardrobe");
    }

    void useRegistry(Supplier<Map<String, SbCommand.Item>> r) { this.registry = r; }

    private static Component t(String s, TextColor c) { return Component.text(s, c).decoration(TextDecoration.ITALIC, false); }
    private static void say(Player p, String s) { p.sendMessage(Component.text(s, NamedTextColor.RED)); }

    // ---------------------------------------------------------------- the token

    ItemStack token(int n) {
        ItemStack it = new ItemStack(Material.PAPER, Math.max(1, Math.min(64, n)));
        ItemMeta m = it.getItemMeta();
        m.displayName(t("Hat Token", GOLD).decoration(TextDecoration.BOLD, true));
        m.lore(List.of(t("Spend it in your /wardrobe on any hat.", NamedTextColor.GRAY),
                t("You can trade it or give it away too.", NamedTextColor.DARK_GRAY)));
        m.setItemModel(new NamespacedKey("soulbound", "hat_token"));
        m.setMaxStackSize(64);
        m.getPersistentDataContainer().set(tokenKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isToken(ItemStack it) {
        return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(tokenKey, PersistentDataType.BYTE);
    }

    private int tokens(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (isToken(it)) n += it.getAmount();
        return n;
    }

    private boolean takeToken(Player p) {
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) {
            if (!isToken(c[i])) continue;
            if (c[i].getAmount() > 1) c[i].setAmount(c[i].getAmount() - 1); else c[i] = null;
            p.getInventory().setStorageContents(c);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- what counts as a hat

    boolean isHat(ItemStack it) {
        if (it == null || it.getType().isAir() || !it.hasItemMeta()) return false;
        if (it.getItemMeta().getPersistentDataContainer().has(hatKey, PersistentDataType.BYTE)) return true;   // custom store hats
        Cute.Kind k = plugin.cute().kindOf(it);
        if (k != null) return k.slot == EquipmentSlot.HEAD;
        return plugin.parasite().isCrown(it);
    }

    /** The hats a token can unlock, in order: ids from the /sb items registry. */
    private List<String> catalogue() {
        List<String> ids = plugin.getConfig().getStringList("wardrobe.catalogue");
        if (ids.isEmpty()) ids = List.of("sinners_halo", "cortex_leech", "wizard_hat", "crocodile_mask", "baroque", "flower_crown", "bunny_ears",
                "dunce_cap", "furry_mask", "goldfish_bowl", "pigeon_pal", "party_lampshade", "the_visitor", "bag_man", "froggy", "load_of_bull",
                "head_first", "hypno_helm", "shroom_bloom", "smug_troll", "fat_steve_head", "villager_head", "umbrella_head", "vindicator_head", "villagerbrine", "spartan_helm", "caesars_laurels", "pharaohs_nemes", "guess_who");
        List<String> ok = new ArrayList<>();
        for (String id : ids) if (registry.get().containsKey(id.toLowerCase(Locale.ROOT))) ok.add(id.toLowerCase(Locale.ROOT));
        return ok;
    }

    private ItemStack make(String id) {
        SbCommand.Item item = registry.get().get(id);
        if (item == null) return null;
        List<ItemStack> l = item.make().get();
        return l.isEmpty() ? null : l.get(0);
    }

    private int max() { return Math.max(PER_PAGE, plugin.getConfig().getInt("wardrobe.max-hats", 135)); }

    // ---------------------------------------------------------------- storage (on disk after every change)

    private File file(UUID id) { return new File(dir, id + ".yml"); }

    private List<ItemStack> hats(UUID id) {
        return cache.computeIfAbsent(id, u -> {
            List<ItemStack> l = new ArrayList<>();
            String s = YamlConfiguration.loadConfiguration(file(u)).getString("hats");
            if (s != null && !s.isEmpty()) {
                try { for (ItemStack it : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s))) if (it != null && !it.getType().isAir()) l.add(it); }
                catch (Throwable e) { plugin.getLogger().severe("Wardrobe: couldn't read " + u + ".yml: " + e); }
            }
            return l;
        });
    }

    private boolean save(UUID id) {
        List<ItemStack> l = hats(id);
        YamlConfiguration y = new YamlConfiguration();
        y.set("hats", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(l.toArray(new ItemStack[0]))));
        try { dir.mkdirs(); y.save(file(id)); return true; }
        catch (IOException e) { plugin.getLogger().severe("Wardrobe: couldn't save " + id + ": " + e.getMessage()); return false; }
    }

    // ---------------------------------------------------------------- who may open it

    private boolean allowed(Player p) {
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            if (!p.hasPermission("soulbound.admin")) { say(p, "The wardrobe is shut in creative mode."); return false; }
        }
        var cm = plugin.captures();
        if (cm != null && cm.isCaptive(p)) { say(p, "You can't get to your wardrobe while you're being held."); return false; }
        return true;
    }

    // ---------------------------------------------------------------- the GUI

    private static final class View implements InventoryHolder {
        Inventory inv;
        boolean shop;
        int page;
        String confirm;       // the catalogue id clicked once, waiting for the second click
        long confirmUntil;
        final List<String> ids = new ArrayList<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private static ItemStack button(Material mat, String model, String name, TextColor c, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(t(name, c).decoration(TextDecoration.BOLD, true));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(t(s, NamedTextColor.GRAY));
        m.lore(l);
        if (model != null) m.setItemModel(new NamespacedKey("soulbound", model));
        m.setHideTooltip(false);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setHideTooltip(true);
        it.setItemMeta(m);
        return it;
    }

    void open(Player p) { open(p, false, 0, null); }

    private void open(Player p, boolean shop, int page, View reuse) {
        View v = new View();
        v.shop = shop;
        int tk = tokens(p);
        List<ItemStack> mine = hats(p.getUniqueId());
        int total = shop ? catalogue().size() : mine.size();
        int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
        v.page = Math.max(0, Math.min(page, pages - 1));
        if (reuse != null && shop) { v.confirm = reuse.confirm; v.confirmUntil = reuse.confirmUntil; }
        String title = shop ? "Unlock a hat · " + tk + " token" + (tk == 1 ? "" : "s")
                : "Wardrobe · " + mine.size() + " hat" + (mine.size() == 1 ? "" : "s") + (pages > 1 ? " (" + (v.page + 1) + "/" + pages + ")" : "");
        Inventory inv = Bukkit.createInventory(v, 54, Component.text(title, TextColor.color(0x5A3A1A)));
        v.inv = inv;
        for (int s = 45; s < 54; s++) inv.setItem(s, pane());
        if (v.page > 0) inv.setItem(45, button(Material.ARROW, null, "Previous page", NamedTextColor.YELLOW));
        if (v.page < pages - 1) inv.setItem(53, button(Material.ARROW, null, "Next page", NamedTextColor.YELLOW));

        if (!shop) {
            for (int i = v.page * PER_PAGE; i < Math.min(mine.size(), (v.page + 1) * PER_PAGE); i++) {
                ItemStack icon = mine.get(i).clone();
                ItemMeta m = icon.getItemMeta();
                List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(t("Left-click: take it out", NamedTextColor.YELLOW));
                lore.add(t("Right-click: put it on", NamedTextColor.YELLOW));
                m.lore(lore);
                icon.setItemMeta(m);
                inv.setItem(i - v.page * PER_PAGE, icon);
            }
            if (mine.isEmpty()) inv.setItem(22, button(Material.LEATHER_HELMET, null, "Your wardrobe is empty", NamedTextColor.WHITE,
                    "Click a hat in your inventory to put it away,", "or unlock a new one with a Hat Token."));
            inv.setItem(47, withAmount(button(Material.PAPER, "hat_token", "Unlock a hat", GOLD,
                    "You have " + tk + " Hat Token" + (tk == 1 ? "" : "s") + ".", "One token unlocks any hat.", "Tokens are on the store.", "", "Click to choose"), tk));
            ItemStack head = p.getInventory().getHelmet();
            if (isHat(head)) {
                ItemStack h = head.clone();
                ItemMeta m = h.getItemMeta();
                List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(t("You're wearing this.", NamedTextColor.WHITE));
                lore.add(t("Click to take it off and put it away", NamedTextColor.YELLOW));
                m.lore(lore);
                h.setItemMeta(m);
                inv.setItem(49, h);
            } else inv.setItem(49, button(Material.ARMOR_STAND, null, "Your head", NamedTextColor.WHITE,
                    "No hat on right now.", "Right-click a hat above to wear it."));
            inv.setItem(51, button(Material.WRITABLE_BOOK, null, "Selling a hat", NamedTextColor.AQUA,
                    "1. Take the hat out and hold it.", "2. /wardrobe sell <player> <price>",
                    "   (price in " + currencyName() + ")", "3. They click [Buy] and it's theirs.", "",
                    "Or just trade it by hand. Hats are real items."));
        } else {
            List<String> cat = catalogue();
            for (int i = v.page * PER_PAGE; i < Math.min(cat.size(), (v.page + 1) * PER_PAGE); i++) {
                String id = cat.get(i);
                ItemStack icon = make(id);
                if (icon == null) continue;
                ItemMeta m = icon.getItemMeta();
                List<Component> lore = m.hasLore() && m.lore() != null ? new ArrayList<>(m.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                int have = 0;
                for (ItemStack h : mine) if (h.isSimilar(icon)) have++;
                if (have > 0) lore.add(t("You have " + have + " in your wardrobe", NamedTextColor.DARK_GRAY));
                boolean armed = id.equals(v.confirm) && System.currentTimeMillis() < v.confirmUntil;
                if (tk <= 0) lore.add(t("Costs 1 Hat Token (you have none)", NamedTextColor.RED));
                else if (armed) { lore.add(t("Click again to spend 1 token", NamedTextColor.GREEN).decoration(TextDecoration.BOLD, true)); m.setEnchantmentGlintOverride(true); }
                else lore.add(t("Click to unlock for 1 Hat Token", NamedTextColor.YELLOW));
                m.lore(lore);
                icon.setItemMeta(m);
                inv.setItem(v.ids.size(), icon);
                v.ids.add(id);
            }
            inv.setItem(49, button(Material.OAK_DOOR, null, "Back to your wardrobe", NamedTextColor.WHITE));
            inv.setItem(47, withAmount(button(Material.PAPER, "hat_token", tk + " Hat Token" + (tk == 1 ? "" : "s"), GOLD,
                    "Get more on the store:", plugin.getConfig().getString("wardrobe.store-url", "store.playbaffoonmc.net")), tk));
        }
        p.openInventory(inv);
    }

    private static ItemStack withAmount(ItemStack it, int n) { it.setAmount(Math.max(1, Math.min(64, n))); return it; }

    @EventHandler
    public void onDrag(InventoryDragEvent e) { if (e.getInventory().getHolder() instanceof View) e.setCancelled(true); }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof View v)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClickedInventory() == null) return;
        if (!allowed(p)) { p.closeInventory(); return; }
        UUID id = p.getUniqueId();
        List<ItemStack> mine = hats(id);

        // their own inventory: put a hat away
        if (e.getClickedInventory() != e.getInventory()) {
            if (v.shop) return;
            ItemStack it = e.getCurrentItem();
            if (!isHat(it)) { if (it != null && !it.getType().isAir()) p.sendActionBar(Component.text("Only hats go in the wardrobe.", NamedTextColor.RED)); return; }
            if (mine.size() >= max()) { say(p, "Your wardrobe is full (" + max() + " hats)."); return; }
            ItemStack moved = it.clone();
            e.getClickedInventory().setItem(e.getSlot(), null);      // out of the inventory first
            for (int n = 0; n < moved.getAmount(); n++) { ItemStack one = moved.clone(); one.setAmount(1); mine.add(one); }
            save(id);
            p.playSound(p.getLocation(), "minecraft:item.armor.equip_leather", 0.6f, 1.2f);
            open(p, false, v.page, v);
            return;
        }

        int slot = e.getSlot();
        if (slot == 45 && v.page > 0) { open(p, v.shop, v.page - 1, v); return; }
        if (slot == 53) { open(p, v.shop, v.page + 1, v); return; }

        if (v.shop) {
            if (slot == 49) { open(p, false, 0, null); return; }
            if (slot < 0 || slot >= v.ids.size()) return;
            String hatId = v.ids.get(slot);
            if (tokens(p) <= 0) { say(p, "You need a Hat Token. They're on the store."); return; }
            if (!hatId.equals(v.confirm) || System.currentTimeMillis() > v.confirmUntil) {
                v.confirm = hatId; v.confirmUntil = System.currentTimeMillis() + 5000;
                p.playSound(p.getLocation(), "minecraft:ui.button.click", 0.5f, 1.4f);
                open(p, true, v.page, v);
                return;
            }
            if (mine.size() >= max()) { say(p, "Your wardrobe is full (" + max() + " hats). Take some out first."); return; }
            ItemStack hat = make(hatId);
            if (hat == null || !takeToken(p)) return;
            hat.setAmount(1);
            mine.add(hat);
            save(id);
            v.confirm = null;
            p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.6f, 1.6f);
            p.sendMessage(Component.text("Unlocked ", NamedTextColor.GREEN).append(hat.effectiveName()).append(Component.text(". It's in your wardrobe.", NamedTextColor.GREEN)));
            open(p, false, (mine.size() - 1) / PER_PAGE, null);
            return;
        }

        if (slot == 47) { open(p, true, 0, null); return; }
        if (slot == 49) {   // take the current hat off and put it away
            ItemStack head = p.getInventory().getHelmet();
            if (!isHat(head)) return;
            if (mine.size() >= max()) { say(p, "Your wardrobe is full."); return; }
            ItemStack moved = head.clone();
            p.getInventory().setHelmet(null);
            mine.add(moved);
            save(id);
            open(p, false, v.page, v);
            return;
        }
        if (slot >= PER_PAGE) return;
        int idx = v.page * PER_PAGE + slot;
        if (idx >= mine.size()) return;
        ItemStack hat = mine.get(idx);

        if (e.isRightClick()) {   // put it on; whatever was on the head goes back in, or to the inventory
            ItemStack head = p.getInventory().getHelmet();
            boolean headEmpty = head == null || head.getType().isAir();
            if (!headEmpty && !isHat(head) && p.getInventory().firstEmpty() < 0) { say(p, "Make room in your inventory for your helmet first."); return; }
            mine.remove(idx);
            if (!headEmpty) {
                if (isHat(head)) mine.add(idx, head.clone());
                else p.getInventory().addItem(head.clone());
            }
            p.getInventory().setHelmet(hat);
            save(id);
            p.playSound(p.getLocation(), "minecraft:item.armor.equip_leather", 0.7f, 1.1f);
        } else {                  // take it out
            if (p.getInventory().firstEmpty() < 0) { say(p, "Your inventory is full."); return; }
            mine.remove(idx);
            save(id);
            p.getInventory().addItem(hat);
            p.playSound(p.getLocation(), "minecraft:entity.item.pickup", 0.6f, 1.2f);
        }
        open(p, false, v.page, v);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        offers.remove(id);
        offers.values().removeIf(o -> o.seller().equals(id));
        cache.remove(id);
    }

    // ---------------------------------------------------------------- selling

    private Material currency() {
        Material m = Material.matchMaterial(plugin.getConfig().getString("wardrobe.sell-currency", "DIAMOND"));
        return m == null || m.isAir() ? Material.DIAMOND : m;
    }

    private String currencyName() {
        String n = currency().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return n.endsWith("s") ? n : n + "s";
    }

    private static int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m && !it.hasItemMeta()) n += it.getAmount();
        return n;
    }

    private static void take(Player p, Material m, int n) {
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length && n > 0; i++) {
            if (c[i] == null || c[i].getType() != m || c[i].hasItemMeta()) continue;
            int k = Math.min(n, c[i].getAmount());
            n -= k;
            if (k == c[i].getAmount()) c[i] = null; else c[i].setAmount(c[i].getAmount() - k);
        }
        p.getInventory().setStorageContents(c);
    }

    /** Takes one of these hats out of the seller's inventory (hand first). False if they don't have it any more. */
    private static boolean takeHat(Player p, ItemStack hat) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.isSimilar(hat)) {
            if (hand.getAmount() > 1) hand.setAmount(hand.getAmount() - 1); else p.getInventory().setItemInMainHand(null);
            return true;
        }
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length; i++) {
            if (c[i] == null || !c[i].isSimilar(hat)) continue;
            if (c[i].getAmount() > 1) c[i].setAmount(c[i].getAmount() - 1); else c[i] = null;
            p.getInventory().setStorageContents(c);
            return true;
        }
        return false;
    }

    private void sell(Player seller, String[] a) {
        if (a.length < 3) { say(seller, "Hold the hat, then: /wardrobe sell <player> <price in " + currencyName() + ">"); return; }
        ItemStack hand = seller.getInventory().getItemInMainHand();
        if (!isHat(hand)) { say(seller, "Hold the hat you want to sell. (Take it out of your wardrobe first.)"); return; }
        Player buyer = Bukkit.getPlayerExact(a[1]);
        if (buyer == null || !seller.canSee(buyer)) { say(seller, a[1] + " isn't online."); return; }
        if (buyer.equals(seller)) { say(seller, "You can't sell to yourself."); return; }
        int price;
        try { price = Integer.parseInt(a[2]); } catch (NumberFormatException ex) { say(seller, "The price is a number of " + currencyName() + "."); return; }
        if (price < 0 || price > 2304) { say(seller, "Pick a price between 0 and 2304 " + currencyName() + "."); return; }
        ItemStack hat = hand.clone(); hat.setAmount(1);
        long secs = plugin.getConfig().getLong("wardrobe.offer-seconds", 60);
        offers.put(buyer.getUniqueId(), new Offer(seller.getUniqueId(), hat, price, System.currentTimeMillis() + secs * 1000));
        String cost = price == 0 ? "for free" : "for " + price + " " + (price == 1 ? currency().name().toLowerCase(Locale.ROOT).replace('_', ' ') : currencyName());
        buyer.sendMessage(Component.text(seller.getName() + " offers you ", NamedTextColor.GOLD).append(hat.effectiveName().hoverEvent(hat.asHoverEvent()))
                .append(Component.text(" " + cost + ". ", NamedTextColor.GOLD))
                .append(Component.text("[Buy]", NamedTextColor.GREEN, TextDecoration.BOLD).clickEvent(ClickEvent.runCommand("/wardrobe accept"))
                        .hoverEvent(HoverEvent.showText(Component.text("It goes straight into your wardrobe"))))
                .append(Component.text("  "))
                .append(Component.text("[No thanks]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/wardrobe decline"))));
        seller.sendMessage(Component.text("Offered to " + buyer.getName() + " " + cost + ". It's yours until they buy it (" + secs + "s).", NamedTextColor.GRAY));
    }

    private void accept(Player buyer) {
        Offer o = offers.remove(buyer.getUniqueId());
        if (o == null || System.currentTimeMillis() > o.until()) { say(buyer, "That offer has run out."); return; }
        Player seller = Bukkit.getPlayer(o.seller());
        if (seller == null) { say(buyer, "The seller has gone offline."); return; }
        if (!allowed(buyer) || seller.getGameMode() == GameMode.CREATIVE) { say(buyer, "That can't go through right now."); return; }
        if (count(buyer, currency()) < o.price()) { say(buyer, "You need " + o.price() + " " + currencyName() + " in your inventory."); return; }
        List<ItemStack> mine = hats(buyer.getUniqueId());
        if (mine.size() >= max()) { say(buyer, "Your wardrobe is full."); return; }
        if (!takeHat(seller, o.hat())) { say(buyer, seller.getName() + " doesn't have that hat any more."); return; }
        take(buyer, currency(), o.price());
        mine.add(o.hat().clone());
        save(buyer.getUniqueId());
        int left = o.price();
        while (left > 0) {
            int k = Math.min(left, currency().getMaxStackSize());
            seller.getInventory().addItem(new ItemStack(currency(), k)).values().forEach(r -> seller.getWorld().dropItem(seller.getLocation(), r));
            left -= k;
        }
        buyer.sendMessage(Component.text("Bought ", NamedTextColor.GREEN).append(o.hat().effectiveName()).append(Component.text(" from " + seller.getName() + ". It's in your wardrobe.", NamedTextColor.GREEN)));
        seller.sendMessage(Component.text(buyer.getName() + " bought ", NamedTextColor.GREEN).append(o.hat().effectiveName())
                .append(Component.text(o.price() > 0 ? " for " + o.price() + " " + currencyName() + "." : ".", NamedTextColor.GREEN)));
        buyer.playSound(buyer.getLocation(), "minecraft:entity.player.levelup", 0.5f, 1.6f);
        seller.playSound(seller.getLocation(), "minecraft:entity.experience_orb.pickup", 0.7f, 1.0f);
        plugin.getLogger().info("Wardrobe sale: " + seller.getName() + " -> " + buyer.getName() + " (" + o.price() + " " + currency().name() + ")");
    }

    // ---------------------------------------------------------------- the command

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only. (Give tokens with /sb give <player> hat_token [amount].)"); return true; }
        String sub = a.length == 0 ? "" : a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "sell" -> { if (allowed(p)) sell(p, a); }
            case "accept", "buy" -> accept(p);
            case "decline", "no" -> { if (offers.remove(p.getUniqueId()) != null) p.sendMessage(Component.text("Offer declined.", NamedTextColor.GRAY)); }
            case "tokens" -> p.sendMessage(Component.text("You have " + tokens(p) + " Hat Tokens.", GOLD));
            default -> { if (allowed(p)) open(p); }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) { for (String o : List.of("sell", "accept", "decline", "tokens")) if (o.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(o); }
        else if (a.length == 2 && a[0].equalsIgnoreCase("sell")) {
            for (Player o : Bukkit.getOnlinePlayers()) if (o != s && o.getName().toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(o.getName());
        } else if (a.length == 3 && a[0].equalsIgnoreCase("sell")) out.addAll(List.of("1", "5", "10"));
        return out;
    }
}
