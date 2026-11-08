package net.soulbound;

import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Player;
import org.bukkit.entity.Shulker;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Bestiary: a book of everyone and everything in BaffoonMC, with pictures. Portraits and item icons are glyphs of
 * the resource pack's soulbound:bestiary font; the text beside a portrait is pushed right by an indent glyph as wide as
 * it. Creatures stay "???" (a dark silhouette) until you've been near one; the paths (Inhuman, Graced, Quantum) and
 * "How it works" are always open. Read it with the Bestiary item (a knowledge book) or /bestiary.
 */
final class Bestiary implements Listener, CommandExecutor {
    // generated from the resource pack's bestiary font (soulbound:bestiary)
    private static final Map<String, String> PORTRAIT = Map.ofEntries(Map.entry("god", "\uE100"), Map.entry("moloch", "\uE102"), Map.entry("priest", "\uE104"), Map.entry("peacekeeper", "\uE106"), Map.entry("archangel", "\uE108"), Map.entry("zephyr", "\uE10A"), Map.entry("champion", "\uE10C"), Map.entry("quantum", "\uE10E"), Map.entry("inhuman", "\uE10F"), Map.entry("graced", "\uE110"),
            Map.entry("laestrygonian", "\uE131"), Map.entry("creaking", "\uE133"), Map.entry("brimstone", "\uE135"), Map.entry("bloodmoon", "\uE137"), Map.entry("homunculus", "\uE139"), Map.entry("sleeper", "\uE13B"));
    private static final Map<String, Integer> PORTRAIT_HEIGHT = Map.ofEntries(Map.entry("god", 56), Map.entry("moloch", 56), Map.entry("priest", 56), Map.entry("peacekeeper", 40), Map.entry("archangel", 40), Map.entry("zephyr", 36), Map.entry("champion", 32), Map.entry("quantum", 56), Map.entry("inhuman", 36), Map.entry("graced", 36),
            Map.entry("laestrygonian", 56), Map.entry("creaking", 56), Map.entry("brimstone", 32), Map.entry("bloodmoon", 32), Map.entry("homunculus", 40), Map.entry("sleeper", 32));
    private static final Map<String, String> SILHOUETTE = Map.ofEntries(Map.entry("god", "\uE101"), Map.entry("moloch", "\uE103"), Map.entry("priest", "\uE105"), Map.entry("peacekeeper", "\uE107"), Map.entry("archangel", "\uE109"), Map.entry("zephyr", "\uE10B"), Map.entry("champion", "\uE10D"),
            Map.entry("laestrygonian", "\uE132"), Map.entry("creaking", "\uE134"), Map.entry("brimstone", "\uE136"), Map.entry("bloodmoon", "\uE138"), Map.entry("homunculus", "\uE13A"), Map.entry("sleeper", "\uE13C"));
    private static final Map<String, String> ICON = Map.ofEntries(Map.entry("halo", "\uE111"), Map.entry("tear_of_god", "\uE112"), Map.entry("vestments_helmet", "\uE113"), Map.entry("vestments_chestplate", "\uE114"), Map.entry("moloch_horns", "\uE115"), Map.entry("ember_of_moloch", "\uE116"), Map.entry("moloch_helmet", "\uE117"), Map.entry("moloch_chestplate", "\uE118"), Map.entry("sack_front", "\uE119"), Map.entry("cudgel", "\uE11A"), Map.entry("capture_leash", "\uE11B"), Map.entry("cultist_axe_3", "\uE11C"), Map.entry("blood_priest_hat", "\uE11D"), Map.entry("tumour_seed", "\uE11E"), Map.entry("blood_emerald", "\uE11F"), Map.entry("heart_of_light", "\uE120"), Map.entry("heart_of_darkness", "\uE121"), Map.entry("quantum_core", "\uE122"), Map.entry("quantum_chestplate", "\uE123"), Map.entry("cloud_cotton_seeds", "\uE124"), Map.entry("tumour", "\uE125"), Map.entry("potential_blade", "\uE126"),
            Map.entry("human_flesh", "\uE13D"), Map.entry("laestrygonian_helm", "\uE13E"), Map.entry("laestrygonian_longsword", "\uE13F"), Map.entry("iron_spear", "\uE140"), Map.entry("homunculus_flask", "\uE141"),
            Map.entry("mini_creaking", "\uE148"), Map.entry("mini_brimstone", "\uE149"), Map.entry("mini_bloodmoon", "\uE14A"), Map.entry("mini_sleeper", "\uE14B"));
    private static final Map<String, String> INDENT = Map.ofEntries(Map.entry("god", "\uE127"), Map.entry("moloch", "\uE128"), Map.entry("priest", "\uE129"), Map.entry("peacekeeper", "\uE12A"), Map.entry("archangel", "\uE12B"), Map.entry("zephyr", "\uE12C"), Map.entry("champion", "\uE12D"), Map.entry("quantum", "\uE12E"), Map.entry("inhuman", "\uE12F"), Map.entry("graced", "\uE130"),
            Map.entry("laestrygonian", "\uE142"), Map.entry("creaking", "\uE143"), Map.entry("brimstone", "\uE144"), Map.entry("bloodmoon", "\uE145"), Map.entry("homunculus", "\uE146"), Map.entry("sleeper", "\uE147"));

    private static final Key FONT = Key.key("soulbound", "bestiary");
    private static final TextColor DARK_RED = NamedTextColor.DARK_RED, LABEL = NamedTextColor.DARK_GRAY, LINK = NamedTextColor.DARK_AQUA, QUOTE = NamedTextColor.DARK_PURPLE;
    /** creatures you have to meet before their page opens, in book order */
    private static final List<String> CREATURES = List.of("god", "peacekeeper", "archangel", "zephyr", "brimstone", "moloch", "priest", "champion",
            "laestrygonian", "creaking", "bloodmoon", "sleeper", "homunculus");

    private final Soulbound plugin;
    private final NamespacedKey itemKey, seenKey, givenKey;
    private BukkitTask task;

    Bestiary(Soulbound plugin) {
        this.plugin = plugin;
        itemKey = new NamespacedKey(plugin, "bestiary");
        seenKey = new NamespacedKey(plugin, "bestiary_seen");
        givenKey = new NamespacedKey(plugin, "bestiary_given");
    }

    void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::scan, 60L, 40L); }
    void stop() { if (task != null) task.cancel(); }

    // ---------------------------------------------------------------- the item

    ItemStack item() {
        ItemStack it = new ItemStack(Material.KNOWLEDGE_BOOK);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Bestiary", TextColor.color(0xF5C351), TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Everyone and everything in BaffoonMC.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to read.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(itemKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    boolean isBook(ItemStack it) { return it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.BYTE); }

    /** Everyone gets a Bestiary once: new players when they first join, everyone else the next time they log in. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!plugin.getConfig().getBoolean("bestiary.give-on-join", true) || p.getPersistentDataContainer().has(givenKey, PersistentDataType.BYTE)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.getPersistentDataContainer().has(givenKey, PersistentDataType.BYTE)) return;
            p.getPersistentDataContainer().set(givenKey, PersistentDataType.BYTE, (byte) 1);
            boolean has = false;
            for (ItemStack it : p.getInventory().getContents()) if (isBook(it)) { has = true; break; }
            if (!has) for (ItemStack left : p.getInventory().addItem(item()).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
            p.sendMessage(Component.text("\u2726 You've been given a ", TextColor.color(0xF5C351))
                    .append(Component.text("Bestiary", TextColor.color(0xF5C351), TextDecoration.BOLD))
                    .append(Component.text(". Everything you meet goes in it. Lost it? ", NamedTextColor.GRAY))
                    .append(Component.text("/bestiary", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/bestiary"))));
            p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
        }, 60L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getHand() == EquipmentSlot.OFF_HAND ? e.getPlayer().getInventory().getItemInOffHand() : e.getPlayer().getInventory().getItemInMainHand();
        if (!isBook(it)) return;
        e.setCancelled(true);   // a knowledge book would be used up teaching recipes: this one opens instead
        if (e.getHand() == EquipmentSlot.HAND) open(e.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        open(p);
        return true;
    }

    // ---------------------------------------------------------------- who you've met

    Set<String> seen(Player p) {
        String s = p.getPersistentDataContainer().get(seenKey, PersistentDataType.STRING);
        return s == null || s.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(s.split(",")));
    }

    boolean knows(Player p, String id) { return !CREATURES.contains(id) || p.hasPermission("soulbound.bestiary.all") || seen(p).contains(id); }

    void meet(Player p, String id) {
        Set<String> s = seen(p);
        if (!s.add(id)) return;
        p.getPersistentDataContainer().set(seenKey, PersistentDataType.STRING, String.join(",", s));
        p.sendActionBar(Component.text("New Bestiary entry: ", NamedTextColor.GRAY).append(Component.text(NAMES.get(id), TextColor.color(0xF5C351))));
        p.sendMessage(Component.text("✦ New Bestiary entry: ", TextColor.color(0xF5C351)).append(Component.text(NAMES.get(id), NamedTextColor.WHITE))
                .append(Component.text("  [read]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/bestiary")).hoverEvent(HoverEvent.showText(Component.text("Open the Bestiary")))));
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
    }

    private static final Map<String, String> NAMES = Map.ofEntries(Map.entry("god", "God"), Map.entry("moloch", "Moloch"), Map.entry("priest", "The Blood Priest"),
            Map.entry("peacekeeper", "The Peacekeeper"), Map.entry("archangel", "Archangels"), Map.entry("zephyr", "Zephyrs"), Map.entry("champion", "Moloch's Champions"),
            Map.entry("brimstone", "Brimstone Cubes"), Map.entry("laestrygonian", "The Laestrygonians"), Map.entry("creaking", "The Giant Creaking"),
            Map.entry("bloodmoon", "Blood Moon Beasts"), Map.entry("sleeper", "The Sleeper"), Map.entry("homunculus", "Homunculi"),
            Map.entry("how", "How it works"), Map.entry("inhuman", "The Inhuman"), Map.entry("graced", "The Graced"), Map.entry("quantum", "Quantum Humanity"));

    /** every two seconds: has anyone come near something they haven't met? */
    private void scan() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Set<String> s = seen(p);
            if (s.containsAll(CREATURES)) continue;
            boolean end = p.getWorld().getEnvironment() == World.Environment.THE_END;
            for (Entity e : p.getNearbyEntities(end ? 96 : 32, end ? 96 : 24, end ? 96 : 32)) {
                String id = identify(e, end);
                if (id != null && !s.contains(id)) { meet(p, id); s.add(id); }
            }
        }
    }

    private String identify(Entity e, boolean end) {
        if (end && e instanceof EnderDragon) return "god";
        if (plugin.boss() != null && plugin.boss().isFoe(e)) return e instanceof Warden ? "moloch" : e instanceof PiglinBrute ? "champion" : null;
        if (plugin.priest() != null && plugin.priest().isPriest(e)) return "priest";
        if (plugin.peacekeeper() != null && plugin.peacekeeper().is(e)) return "peacekeeper";
        if (plugin.archangels() != null && plugin.archangels().isArchangel(e)) return "archangel";
        if (end && e instanceof Shulker) return "archangel";
        if (plugin.zephyrs() != null && plugin.zephyrs().is(e)) return "zephyr";
        if (plugin.brimstone() != null && plugin.brimstone().is(e)) return "brimstone";
        if (Laestrygonians.is(e)) return "laestrygonian";
        if (plugin.giantCreaking() != null && plugin.giantCreaking().is(e)) return "creaking";
        if (plugin.homunculus() != null && plugin.homunculus().is(e)) return "homunculus";
        if (plugin.sleeper() != null && plugin.sleeper().is(e)) return "sleeper";
        if (livestock(e) && plugin.boss() != null && plugin.boss().bloodMoon() && plugin.boss().bloodMoonWorld(e.getWorld())) return "bloodmoon";
        return null;
    }

    private static boolean livestock(Entity e) {
        return e instanceof org.bukkit.entity.Cow || e instanceof org.bukkit.entity.Pig || e instanceof org.bukkit.entity.Sheep || e instanceof org.bukkit.entity.Chicken
                || e instanceof org.bukkit.entity.Goat || e instanceof org.bukkit.entity.Rabbit || e instanceof org.bukkit.entity.AbstractHorse;
    }

    // ---------------------------------------------------------------- building pages

    private static Component glyph(String ch) { return Component.text(ch).font(FONT).color(NamedTextColor.WHITE); }
    private static Component t(String s) { return Component.text(s, NamedTextColor.BLACK); }
    private static Component t(String s, TextColor c) { return Component.text(s, c); }
    private static Component title(String s) { return Component.text(s, DARK_RED, TextDecoration.BOLD); }
    private static Component label(String s) { return Component.text(s, LABEL); }
    private static Component quote(String s) { return Component.text(s, QUOTE, TextDecoration.ITALIC); }
    private static Component icon(String id, String name, String... lines) {
        TextComponent.Builder h = Component.text().append(Component.text(name, NamedTextColor.YELLOW));
        for (String l : lines) h.append(Component.newline()).append(Component.text(l, NamedTextColor.GRAY));
        return glyph(ICON.get(id)).hoverEvent(HoverEvent.showText(h.build()));
    }
    private static Component row(Component... parts) { TextComponent.Builder b = Component.text(); for (Component c : parts) b.append(c); return b.build(); }

    /** a page with a picture top-left: side lines run beside it, the rest below, and a way back to the contents at the foot */
    private Component page(String pid, boolean locked, List<Component> side, List<Component> rest) {
        int rows = (int) Math.ceil(PORTRAIT_HEIGHT.get(pid) / 9.0);
        List<Component> lines = new ArrayList<>();
        String pic = locked && SILHOUETTE.containsKey(pid) ? SILHOUETTE.get(pid) : PORTRAIT.get(pid);
        for (int i = 0; i < rows; i++) {
            Component lead = i == 0 ? glyph(pic) : glyph(INDENT.get(pid));
            lines.add(row(lead, Component.text(" "), i < side.size() ? side.get(i) : Component.empty()));
        }
        lines.addAll(rest);
        return withFooter(lines, contentsPageOf(pid));
    }

    /** pad to the foot of the page (14 lines) and add the link back to the contents */
    private static Component withFooter(List<Component> lines, int contentsPage) {
        List<Component> l = new ArrayList<>(lines);
        while (l.size() < 13) l.add(Component.empty());
        if (l.size() < 14) l.add(Component.text("\u25C0 Contents", LINK).clickEvent(ClickEvent.changePage(contentsPage))
                .hoverEvent(HoverEvent.showText(Component.text("Back to the contents"))));
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(Component.newline()); b.append(l.get(i)); }
        return b.build();
    }

    private Component lockedPage(String pid, String hint1, String hint2) {
        return page(pid, true, List.of(t("???", LABEL).decorate(TextDecoration.BOLD), t("Not met yet.", LABEL)),
                List.of(Component.empty(), label("Hint"), t(hint1), t(hint2)));
    }

    // the contents, in sections; the entries follow in the same order
    private static final String[][][] SECTIONS = {
            {{"HEAVEN"}, {"god", "halo"}, {"peacekeeper", "heart_of_light"}, {"archangel", "tear_of_god"}, {"zephyr", "cloud_cotton_seeds"}, {"brimstone", "mini_brimstone"}},
            {{"MOLOCH'S REALM"}, {"moloch", "moloch_horns"}, {"priest", "blood_priest_hat"}, {"champion", "moloch_helmet"}},
            {{"THE WORLD"}, {"laestrygonian", "laestrygonian_helm"}, {"creaking", "mini_creaking"}, {"bloodmoon", "mini_bloodmoon"}, {"sleeper", "mini_sleeper"}, {"homunculus", "homunculus_flask"}},
            {{"PATHS"}, {"inhuman", "heart_of_darkness"}, {"graced", "heart_of_light"}, {"quantum", "quantum_core"}}};
    /** contents page 1 holds the first two sections, page 2 the rest */
    private static final int CONTENTS_SPLIT = 2;
    // book order: 1-2 contents, 3 how it works, then the entries
    private static final List<String> ORDER = order();
    private static List<String> order() {
        List<String> o = new ArrayList<>(List.of("how"));
        for (String[][] sec : SECTIONS) for (int i = 1; i < sec.length; i++) o.add(sec[i][0]);
        return o;
    }
    private static int pageOf(String id) { return ORDER.indexOf(id) + 3; }
    private static int contentsPageOf(String id) {
        for (int s = 0; s < SECTIONS.length; s++) for (int i = 1; i < SECTIONS[s].length; i++) if (SECTIONS[s][i][0].equals(id)) return s < CONTENTS_SPLIT ? 1 : 2;
        return 1;
    }

    void open(Player p) {
        List<Component> pages = new ArrayList<>();
        pages.add(contents(p, 0));
        pages.add(contents(p, 1));
        pages.add(how());
        for (String id : ORDER) if (!id.equals("how")) pages.add(entry(p, id));
        p.openBook(Book.book(Component.text("Bestiary"), Component.text("BaffoonMC"), pages));
    }

    private Component entry(Player p, String id) {
        boolean k = knows(p, id);
        return switch (id) {
            case "god" -> k ? god() : lockedPage(id, "Fly to the End.", "");
            case "moloch" -> k ? moloch() : lockedPage(id, "Feed the fire", "until he wakes.");
            case "priest" -> k ? priest() : lockedPage(id, "He waits in", "Moloch's realm.");
            case "peacekeeper" -> k ? peacekeeper() : lockedPage(id, "Climb to Heaven's", "gates.");
            case "archangel" -> k ? archangels() : lockedPage(id, "Wander the Seven", "Heavens.");
            case "zephyr" -> k ? zephyrs() : lockedPage(id, "Feel the wind on", "the outer islands.");
            case "brimstone" -> k ? brimstone() : lockedPage(id, "Something yellow", "hops on the islands.");
            case "champion" -> k ? champions() : lockedPage(id, "Wound Moloch", "badly.");
            case "laestrygonian" -> k ? laestrygonians() : lockedPage(id, "Giants walk when", "the staff say so.");
            case "creaking" -> k ? creaking() : lockedPage(id, "Walk to the edge", "of the world.");
            case "bloodmoon" -> k ? bloodMoon() : lockedPage(id, "Stay out under", "a red moon.");
            case "sleeper" -> k ? sleeper() : lockedPage(id, "Someone's asleep", "at spawn.");
            case "homunculus" -> k ? homunculus() : lockedPage(id, "Grow one in", "a flask.");
            case "inhuman" -> inhuman();
            case "graced" -> graced();
            default -> quantum();
        };
    }

    private Component contents(Player p, int half) {
        List<Component> l = new ArrayList<>();
        if (half == 0) {
            int met = 0;
            for (String c : CREATURES) if (seen(p).contains(c) || p.hasPermission("soulbound.bestiary.all")) met++;
            l.add(row(Component.text("BESTIARY", DARK_RED, TextDecoration.BOLD), t("  met ", LABEL),
                    t(met + "/" + CREATURES.size(), met == CREATURES.size() ? NamedTextColor.DARK_GREEN : NamedTextColor.BLACK)));
            l.add(Component.text("How it works", LINK, TextDecoration.UNDERLINED).clickEvent(ClickEvent.changePage(3)));
        } else {
            l.add(row(Component.text("BESTIARY", DARK_RED, TextDecoration.BOLD), t("  continued", LABEL)));
        }
        int from = half == 0 ? 0 : CONTENTS_SPLIT, to = half == 0 ? CONTENTS_SPLIT : SECTIONS.length;
        for (int s = from; s < to; s++) {
            l.add(Component.text(SECTIONS[s][0][0], LABEL, TextDecoration.BOLD));
            for (int i = 1; i < SECTIONS[s].length; i++) {
                String id = SECTIONS[s][i][0];
                int page = pageOf(id);
                boolean open = knows(p, id);
                Component name = open
                        ? Component.text(NAMES.get(id), LINK, TextDecoration.UNDERLINED).clickEvent(ClickEvent.changePage(page)).hoverEvent(HoverEvent.showText(Component.text("Turn to page " + page)))
                        : Component.text("???", LABEL).clickEvent(ClickEvent.changePage(page)).hoverEvent(HoverEvent.showText(Component.text("Not met yet. Turn to page " + page + " for a hint.")));
                l.add(row(open ? glyph(ICON.get(SECTIONS[s][i][1])) : Component.text("  "), Component.text(" "), name));
            }
        }
        if (half == 0) l.add(Component.text("Next page \u25B6", LINK).clickEvent(ClickEvent.changePage(2)));
        else {
            while (l.size() < 13) l.add(Component.empty());
            l.add(Component.text("\u25C0 Back", LINK).clickEvent(ClickEvent.changePage(1)));
        }
        TextComponent.Builder b = Component.text();
        for (int i = 0; i < l.size(); i++) { if (i > 0) b.append(Component.newline()); b.append(l.get(i)); }
        return b.build();
    }

    private Component how() {
        Component[] l = {
                title("HOW IT WORKS"), Component.empty(),
                t("PvP, kidnapping and"), t("stealing: allowed."), t("Griefing: not. Claim"), t("with a golden shovel."), Component.empty(),
                t("Moloch hungers. Fed,"), t("he wakes, and all of"), t("you fight him."), Component.empty(),
                t("God waits in the End."), t("Stay Human, or eat"), t("the Heart of Darkness.")};
        return withFooter(List.of(l), 1);
    }

    private Component god() {
        return page("god", false,
                List.of(title("GOD"), t("Disappointed", LABEL), t("Grandad", LABEL), Component.empty(), label("Where"), t("The End, on"), t("his throne")),
                List.of(label("Fight"), t("Seven Days of"), t("Creation. He grows"), t("with every fighter."),
                        row(label("Drops "), icon("halo", "God's Halo", "Floats above your head.", "For whoever hurt him most."), t(" "),
                                icon("tear_of_god", "Tear of God", "One for everyone who fought.", "Half a Quantum Core."), t(" "),
                                icon("vestments_helmet", "Vestments of the Ancient", "God's own robes over marble."), t(" "),
                                icon("potential_blade", "World Egg", "Two make the Blade of Potential.")),
                        quote("\"I was only using"), quote(" one hand.\"")));
    }

    private Component moloch() {
        return page("moloch", false,
                List.of(title("MOLOCH"), t("Bull of the", LABEL), t("Pyramid", LABEL), Component.empty(), label("Where"), t("Under the"), t("temple")),
                List.of(label("Wants"), t("His Hunger fed to"), t("100. Then he wakes."),
                        row(label("Drops "), icon("moloch_horns", "Moloch's Horns", "For whoever hurt him most."), t(" "),
                                icon("ember_of_moloch", "Ember of Moloch", "Half a Quantum Core."), t(" "),
                                icon("moloch_helmet", "Moloch's Regalia", "Charred plate, molten cracks.", "Full set: fireproof.")),
                        Component.empty(), quote("\"Moloch turns his"), quote(" eyes on you.\"")));
    }

    private Component priest() {
        return page("priest", false,
                List.of(title("BLOOD"), title("PRIEST"), t("Moloch's", LABEL), t("merchant", LABEL), Component.empty(), label("Where"), t("His realm")),
                List.of(label("Sells"),
                        row(icon("sack_front", "The Hood", "Hide your face; villagers", "trade with the Inhuman."), t(" "),
                                icon("cudgel", "Cultist's Cudgel", "Knock them down."), t(" "),
                                icon("capture_leash", "Cultist's Shackles", "Drag them off."), t(" "),
                                icon("cultist_axe_3", "Spiked Cultist Axe", "Takes heads."), t(" "),
                                icon("blood_priest_hat", "Blood Priest's Hat", "64 Blood Emeralds.", "Speed II in his realm."), t(" "),
                                icon("tumour_seed", "Tumour Seeds", "Plant on soul soil.")),
                        t("Pays in gold, wither"), t("skulls and Blood"), t("Emeralds."),
                        quote("\"It's yours now."), quote(" So is the debt.\"")));
    }

    private Component peacekeeper() {
        return page("peacekeeper", false,
                List.of(title("PEACE"), title("KEEPER"), t("Heaven's", LABEL), t("gatekeeper", LABEL), Component.empty()),
                List.of(label("Where"), t("By the Pearly Gates."),
                        row(label("Sells "), icon("heart_of_light", "Angel Engine", "Cures the Inhuman,", "or Graces anyone else."), t(" "),
                                icon("cloud_cotton_seeds", "Cloud Cotton Seeds", "Grow them on farmland,", "in Heaven only.")),
                        t("The Angel Engine is"), t("the only cure for"), t("the Inhuman. Bring"), t("it down to them."),
                        quote("\"Nobody comes back"), quote(" alone.\"")));
    }

    private Component archangels() {
        return page("archangel", false,
                List.of(title("ARCH-"), title("ANGELS"), t("God's host", LABEL), Component.empty(), Component.empty()),
                List.of(label("Where"), t("The Seven Heavens,"), t("out of sight."), t("One bolt of"), t("judgement kills."),
                        t("They weep Angel"), t("Tears. The Graced"), t("are spared.")));
    }

    private Component zephyrs() {
        return page("zephyr", false,
                List.of(title("ZEPHYRS"), t("Heaven's", LABEL), t("breezes", LABEL), Component.empty()),
                List.of(label("Where"), t("The outer islands."), t("Their wind lifts you"), t("with Levitation;"), t("Heaven's Slow Fall"), t("brings you down."),
                        Component.empty(), quote("\"Up is the only"), quote(" direction that"), quote(" matters here.\"")));
    }

    private Component champions() {
        return page("champion", false,
                List.of(title("MOLOCH'S"), title("CHAMPIONS"), t("Gilded guard", LABEL), Component.empty()),
                List.of(label("When"), t("At 75% health they"), t("march in and shield"), t("him. Kill them all"), t("to break his guard."),
                        Component.empty(), t("Then his Demons"), t("swoop from the dark.")));
    }

    private Component brimstone() {
        return page("brimstone", false,
                List.of(title("BRIMSTONE"), title("CUBES"), t("Hell's sulfur", LABEL), Component.empty()),
                List.of(label("Where"), t("Heaven's outer"), t("islands."), t("Touch one and it"), t("swallows you, burns"),
                        t("you, then spits you"), t("out, maybe over the"), t("void.")));
    }

    private Component laestrygonians() {
        return page("laestrygonian", false,
                List.of(title("LAESTRY-"), title("GONIANS"), t("Man-eating", LABEL), t("giants", LABEL), Component.empty(), label("Where"), t("The staff")),
                List.of(t("call up a band."), label("Fight"), t("Twice your size,"), t("fast and iron-hard."),
                        row(t("Spears "), icon("iron_spear", "Spear", "Most of the band."), t(", swords "), icon("laestrygonian_longsword", "Laestrygonian Longsword", "Every third, with a shield.")),
                        row(label("Drops "), icon("human_flesh", "Human Flesh", "Food. Bad food."), t(" "),
                                icon("laestrygonian_helm", "Laestrygonian Helm", "30% chance. Netherite-strong,", "10% bigger. Eat Human Flesh", "in it for Strength III."), t(" iron blocks"))));
    }

    private Component creaking() {
        return page("creaking", false,
                List.of(title("GIANT"), title("CREAKING"), t("The watcher", LABEL), Component.empty(), label("Where"), t("The world's"), t("edge")),
                List.of(t("It watches you and"), t("its eyes charge a"), t("beam. Hold its gaze"), t("and you die. Break"), t("its line of sight."),
                        t("Fire can't hurt it.")));
    }

    private Component bloodMoon() {
        return page("bloodmoon", false,
                List.of(title("BLOOD"), title("MOON"), t("Livestock,", LABEL), t("turned", LABEL)),
                List.of(label("When"), t("The moon goes red."), t("Cows, pigs, sheep,"), t("chickens, goats,"), t("rabbits and horses"), t("hunt you down."),
                        t("Nobody sleeps until"), t("it sets.")));
    }

    private Component sleeper() {
        return page("sleeper", false,
                List.of(title("SLEEPER"), t("Lives at", LABEL), t("spawn", LABEL), Component.empty()),
                List.of(t("Asleep on his"), t("cardboard. Poke him"), t("and he gets grumpier."), t("Four times and he'll"), t("get up and shove you."),
                        Component.empty(), quote("\"Five more"), quote(" minutes.\"")));
    }

    private Component homunculus() {
        return page("homunculus", false,
                List.of(title("HOMUN-"), title("CULUS"), t("Grown in", LABEL), t("a flask", LABEL), Component.empty()),
                List.of(row(label("Made "), icon("potential_blade", "World Egg"), t(" + "), icon("tumour", "Tumour"), t(" + "), icon("quantum_core", "Quantum Core"), t(" \u2192 "),
                                icon("homunculus_flask", "Homunculus in a Flask", "One per player, bound to its maker.")),
                        t("Right-click for its"), t("menu. It follows,"), t("waits, farms your"), t("claim or guards it,"), t("and carries nine"), t("pockets of things.")));
    }

    private Component inhuman() {
        return page("inhuman", false,
                List.of(title("INHUMAN"), t("Ate the Heart", LABEL), t("of Darkness", LABEL), Component.empty()),
                List.of(label("Gets"), t("+3 hearts. Monsters"), t("leave you be. Raw"), t("meat feeds you."), t("Fire Res and Speed"), t("in his realm."),
                        label("Costs"), t("Villagers flee. No"), t("sleep. The sun burns."), t("Heaven is closed.")));
    }

    private Component graced() {
        return page("graced", false,
                List.of(title("GRACED"), t("Ate the Angel", LABEL), t("Engine", LABEL), Component.empty()),
                List.of(label("Gets"), t("Smite the undead."), t("Archangels spare"), t("you. A Second Wind"), t("when you fall low."), t("+3 hearts in Heaven."),
                        label("Costs"), t("Eat the Heart of"), t("Darkness and the"), t("light goes out.")));
    }

    private Component quantum() {
        return page("quantum", false,
                List.of(title("QUANTUM"), title("HUMANITY"), t("Past good", LABEL), t("and evil", LABEL), Component.empty(), label("Made from"),
                        row(icon("ember_of_moloch", "Ember of Moloch", "From Moloch."), t(" + "), icon("tear_of_god", "Tear of God", "From God."))),
                List.of(t("The best armour."), t("Regalia + Vestments"), t("+ netherite, fused at"), t("a smithing table."),
                        t("Fireproof, no fall"), t("damage, Haste."),
                        row(label("Core "), icon("quantum_core", "Quantum Core", "Ember + Tear + netherite."))));
    }
}
