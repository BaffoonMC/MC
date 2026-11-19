package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stock tickers: a strip of glowing text set flat into a wall (black concrete suits it), each quote gliding along
 * and fading at the ends. /bureau ticker place [width] while looking at the wall; /bureau ticker remove.
 * It shows the real Cold Hard Cash price, the richest companies, market events and a list of made-up lizard stocks.
 */
final class Ticker {
    private static final TextColor AMBER = TextColor.color(0xFFB000), WHITE = TextColor.color(0xF2F2F2),
            UP = TextColor.color(0x35E86A), DOWN = TextColor.color(0xFF4A4A), DIM = TextColor.color(0x8A8F8A);

    private static final class Quote { Component text; double blocks; }
    private static final class Item { TextDisplay e; double s, w; int alpha = -1; }
    private static final class Board {
        String world; double x, y, z; BlockFace face; int width;
        final List<Item> items = new ArrayList<>();
        boolean active;
        String key() { return world + "," + x + "," + y + "," + z + "," + face.name() + "," + width; }
    }
    private static final class Stock { String sym; double price, open; }

    private final Bureau plugin;
    private final List<Board> boards = new ArrayList<>();
    private final Map<String, Stock> stocks = new HashMap<>();
    private final List<Quote> feed = new ArrayList<>();
    private int feedAt, ticks;

    Ticker(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("ticker." + k, d); }

    // ---------------------------------------------------------------- placing and removing

    String place(Player p, int width) {
        RayTraceResult r = p.rayTraceBlocks(10);
        if (r == null || r.getHitBlock() == null || r.getHitBlockFace() == null) return "Look at the wall you want it on (within 10 blocks).";
        BlockFace f = r.getHitBlockFace();
        if (f.getModY() != 0) return "Tickers go on walls, not floors or ceilings.";
        Board b = new Board();
        b.world = p.getWorld().getName();
        b.x = r.getHitBlock().getX() + 0.5 + f.getModX() * 0.5;
        b.y = r.getHitBlock().getY() + 0.5;
        b.z = r.getHitBlock().getZ() + 0.5 + f.getModZ() * 0.5;
        b.face = f;
        b.width = Math.max(3, Math.min(64, width));
        boards.add(b);
        plugin.dirty();
        return "Ticker placed: " + b.width + " blocks wide, centred on that block. Black concrete behind it looks best.";
    }

    String removeNear(Player p) {
        Board best = null; double bd = 12 * 12;
        for (Board b : boards) {
            if (!b.world.equals(p.getWorld().getName())) continue;
            double d = p.getLocation().distanceSquared(new Location(p.getWorld(), b.x, b.y, b.z));
            if (d < bd) { bd = d; best = b; }
        }
        if (best == null) return "No ticker within 12 blocks.";
        clear(best);
        boards.remove(best);
        plugin.dirty();
        return "Ticker switched off and taken down.";
    }

    int count() { return boards.size(); }

    private void clear(Board b) {
        for (Item i : b.items) i.e.remove();
        b.items.clear();
    }

    void endAll() { for (Board b : boards) clear(b); }

    // ---------------------------------------------------------------- the feed

    private Stock stock(String sym) {
        return stocks.computeIfAbsent(sym, k -> {
            Stock s = new Stock();
            s.sym = k;
            s.price = 15 + Math.floorMod(k.hashCode(), 3800) / 10.0;
            s.open = s.price;
            return s;
        });
    }

    private static int px(String s, boolean bold) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int w = c == ' ' ? 4 : "i.:,;!|'".indexOf(c) >= 0 ? 2 : c == 'l' ? 3 : "It[]\"".indexOf(c) >= 0 ? 4 : "fk<>()*".indexOf(c) >= 0 ? 5 : c == '@' || c == '~' ? 7 : c > 0x2000 ? 9 : 6;
            n += w + (bold ? 1 : 0);
        }
        return n;
    }

    private Quote quote(String sym, String price, double change, boolean big) {
        boolean up = change >= 0;
        String ch = (up ? " ▲ " : " ▼ ") + String.format(Locale.ROOT, "%.2f%%", Math.abs(change * 100));
        Quote q = new Quote();
        q.text = Component.text(sym + " ", big ? AMBER : WHITE, TextDecoration.BOLD)
                .append(Component.text(price, WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text(ch, up ? UP : DOWN).decoration(TextDecoration.BOLD, false));
        q.blocks = px(sym + " ", true) + px(price, false) + px(ch, false);
        return q;
    }

    private Quote flash(String text, TextColor c) {
        Quote q = new Quote();
        q.text = Component.text(text, c, TextDecoration.BOLD);
        q.blocks = px(text, true);
        return q;
    }

    private void rebuildFeed() {
        feed.clear();
        Exchange ex = plugin.exchange();
        if ("CRASH".equals(ex.event)) feed.add(flash("*** MARKET CRASH ***", DOWN));
        if ("RALLY".equals(ex.event)) feed.add(flash("*** RALLY: BUSINESS IS BOOMING ***", UP));
        feed.add(quote("COLD HARD CASH", Money.fmt(ex.price), ex.change(), true));
        List<String> syms = plugin.getConfig().getStringList("ticker.stocks");
        List<Companies.Company> top = plugin.companies().richest(3);
        int ci = 0;
        for (int i = 0; i < syms.size(); i++) {
            Stock s = stock(syms.get(i).toUpperCase(Locale.ROOT));
            // a small random walk, pulled along by the real market's mood
            double mood = "CRASH".equals(ex.event) ? -0.012 : "RALLY".equals(ex.event) ? 0.012 : 0;
            s.price = Math.max(1, s.price * (1 + mood + ThreadLocalRandom.current().nextGaussian() * 0.006));
            if (Math.abs(s.price / s.open - 1) > 0.25) s.open = s.price;   // a new "day" once it has run far enough
            feed.add(quote(s.sym, String.format(Locale.ROOT, "%.2f", s.price), s.price / s.open - 1, false));
            if (i % 4 == 3 && ci < top.size()) {
                Companies.Company c = top.get(ci++);
                Quote q = new Quote();
                String name = c.name.toUpperCase(Locale.ROOT) + " ", v = c.vault + " CASH";
                q.text = Component.text(name, AMBER, TextDecoration.BOLD).append(Component.text(v, DIM).decoration(TextDecoration.BOLD, false));
                q.blocks = px(name, true) + px(v, false);
                feed.add(q);
            }
            if (i % 6 == 5) feed.add(quote("COLD HARD CASH", Money.fmt(ex.price), ex.change(), true));
        }
    }

    private Quote next() {
        if (feedAt >= feed.size()) { rebuildFeed(); feedAt = 0; }
        return feed.get(feedAt++);
    }

    // ---------------------------------------------------------------- every tick

    void tick() {
        ticks++;
        double scale = cfg("scale", 2.0), step = cfg("speed", 1.6) / 20.0, gap = cfg("gap", 1.2);
        for (Board b : boards) {
            World w = Bukkit.getWorld(b.world);
            if (ticks % 20 == 1 || w == null) {
                boolean near = false;
                if (w != null && w.isChunkLoaded(((int) Math.floor(b.x)) >> 4, ((int) Math.floor(b.z)) >> 4))
                    for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(new Location(w, b.x, b.y, b.z)) < 64 * 64) { near = true; break; }
                if (!near && b.active) clear(b);
                b.active = near;
            }
            if (!b.active || w == null) continue;
            // the viewer's right, along the wall
            double rx = b.face.getModZ(), rz = -b.face.getModX();
            double nx = b.face.getModX() * 0.02, nz = b.face.getModZ() * 0.02;
            float yaw = switch (b.face) { case WEST -> 90f; case NORTH -> 180f; case EAST -> -90f; default -> 0f; };
            double baseY = b.y - 0.125 * scale;

            Item last = b.items.isEmpty() ? null : b.items.get(b.items.size() - 1);
            if (last == null || last.s - last.w / 2 >= gap) {
                Quote q = next();
                Item it = new Item();
                it.w = q.blocks * 0.025 * scale;
                it.s = -it.w / 2;
                Location at = new Location(w, b.x + nx + rx * (b.width / 2.0 - it.s), baseY, b.z + nz + rz * (b.width / 2.0 - it.s), yaw, 0);
                plugin.spawning = true;
                try {
                    it.e = w.spawn(at, TextDisplay.class, e -> {
                        e.setPersistent(false);
                        e.text(q.text);
                        e.setBillboard(Display.Billboard.FIXED);
                        e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                        e.setShadowed(false);
                        e.setSeeThrough(false);
                        e.setLineWidth(100000);
                        e.setBrightness(new Display.Brightness(15, 15));
                        e.setTeleportDuration(2);
                        e.setTextOpacity((byte) 4);
                        e.setViewRange(1.2f);
                        float sc = (float) scale;
                        e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(sc, sc, sc), new AxisAngle4f()));
                    });
                } finally { plugin.spawning = false; }
                if (it.e.isValid()) b.items.add(it);
            }
            for (Iterator<Item> i = b.items.iterator(); i.hasNext(); ) {
                Item it = i.next();
                it.s += step;
                if (it.s - it.w / 2 > b.width || !it.e.isValid()) { it.e.remove(); i.remove(); continue; }
                double off = b.width / 2.0 - it.s;
                it.e.teleport(new Location(w, b.x + nx + rx * off, baseY, b.z + nz + rz * off, yaw, 0));
                // fade in at the right-hand end, out at the left
                double in = Math.min(it.s + it.w / 2, b.width - (it.s - it.w / 2)) / Math.max(0.5, Math.min(it.w, b.width / 2.0));
                int a = in >= 1 ? 255 : (int) (Math.max(0, in) * Math.max(0, in) * 255);
                if (a < 26) a = 4;
                if (Math.abs(a - it.alpha) >= 12 || (a == 255 && it.alpha != 255)) { it.alpha = a; it.e.setTextOpacity((byte) a); }
            }
        }
    }

    // ---------------------------------------------------------------- saving

    void load(List<String> list) {
        endAll();
        boards.clear();
        for (String s : list) {
            String[] p = s.split(",");
            if (p.length != 6) continue;
            try {
                Board b = new Board();
                b.world = p[0]; b.x = Double.parseDouble(p[1]); b.y = Double.parseDouble(p[2]); b.z = Double.parseDouble(p[3]);
                b.face = BlockFace.valueOf(p[4]); b.width = Integer.parseInt(p[5]);
                boards.add(b);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    List<String> save() {
        List<String> out = new ArrayList<>();
        for (Board b : boards) out.add(b.key());
        return out;
    }
}
