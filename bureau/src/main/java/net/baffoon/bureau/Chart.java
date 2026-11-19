package net.baffoon.bureau;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Candlestick charts of the real Cold Hard Cash price, set flat into a wall: /bureau chart place [width] [height]
 * while looking at the wall, /bureau chart remove. Every candle is a slab-thin block display (green up, red down)
 * with a wick; the newest candle moves live as people trade. Prices are sampled every second.
 */
final class Chart {
    private static final TextColor AMBER = TextColor.color(0xFFB000), WHITE = TextColor.color(0xF2F2F2),
            UP = TextColor.color(0x35E86A), DOWN = TextColor.color(0xFF4A4A), DIM = TextColor.color(0x8A8F8A);

    private static final class Candle { double o, h, l, c; Candle(double p) { o = h = l = c = p; } }
    private static final class Board {
        String world; double x, y, z; BlockFace face; int width, height;
        final List<BlockDisplay> bodies = new ArrayList<>(), wicks = new ArrayList<>(), grid = new ArrayList<>();
        TextDisplay title, hi, lo;
        boolean active;
        String drawn = "";
        String key() { return world + "," + x + "," + y + "," + z + "," + face.name() + "," + width + "," + height; }
    }

    private final Bureau plugin;
    private final List<Board> boards = new ArrayList<>();
    private final List<Candle> candles = new ArrayList<>();
    private long candleStart;

    Chart(Bureau plugin) { this.plugin = plugin; }

    private double cfg(String k, double d) { return plugin.getConfig().getDouble("chart." + k, d); }

    // ---------------------------------------------------------------- placing and removing

    String place(Player p, int width, int height) {
        RayTraceResult r = p.rayTraceBlocks(10);
        if (r == null || r.getHitBlock() == null || r.getHitBlockFace() == null) return "Look at the wall you want it on (within 10 blocks).";
        BlockFace f = r.getHitBlockFace();
        if (f.getModY() != 0) return "Charts go on walls, not floors or ceilings.";
        Board b = new Board();
        b.world = p.getWorld().getName();
        b.x = r.getHitBlock().getX() + 0.5 + f.getModX() * 0.5;
        b.y = r.getHitBlock().getY() + 0.5;
        b.z = r.getHitBlock().getZ() + 0.5 + f.getModZ() * 0.5;
        b.face = f;
        b.width = Math.max(3, Math.min(48, width));
        b.height = Math.max(3, Math.min(24, height));
        boards.add(b);
        plugin.dirty();
        return "Chart placed: " + b.width + " wide, " + b.height + " tall, centred on that block. Black concrete behind it looks best.";
    }

    String removeNear(Player p) {
        Board best = null; double bd = 14 * 14;
        for (Board b : boards) {
            if (!b.world.equals(p.getWorld().getName())) continue;
            double d = p.getLocation().distanceSquared(new Location(p.getWorld(), b.x, b.y, b.z));
            if (d < bd) { bd = d; best = b; }
        }
        if (best == null) return "No chart within 14 blocks.";
        clear(best);
        boards.remove(best);
        plugin.dirty();
        return "Chart switched off and taken down.";
    }

    int count() { return boards.size(); }

    private void clear(Board b) {
        for (BlockDisplay d : b.bodies) d.remove();
        for (BlockDisplay d : b.wicks) d.remove();
        for (BlockDisplay d : b.grid) d.remove();
        b.bodies.clear(); b.wicks.clear(); b.grid.clear();
        for (TextDisplay t : new TextDisplay[]{b.title, b.hi, b.lo}) if (t != null) t.remove();
        b.title = b.hi = b.lo = null;
        b.drawn = "";
    }

    void endAll() { for (Board b : boards) clear(b); }

    // ---------------------------------------------------------------- candles

    /** Every second: fold the live price into the newest candle, and open a new one when its time is up. */
    void second() {
        double price = plugin.exchange().price;
        long now = System.currentTimeMillis(), period = (long) (cfg("candle-minutes", 30) * 60_000L);
        if (candles.isEmpty()) {
            // first run: build a back-story from the Exchange's half-hourly history
            List<Double> h = plugin.exchange().history;
            for (int i = 1; i < h.size(); i++) {
                Candle c = new Candle(h.get(i - 1));
                c.c = h.get(i); c.h = Math.max(c.o, c.c); c.l = Math.min(c.o, c.c);
                candles.add(c);
            }
            candles.add(new Candle(price));
            candleStart = now;
        }
        Candle cur = candles.get(candles.size() - 1);
        if (now - candleStart >= period) {
            cur = new Candle(cur.c);
            candles.add(cur);
            candleStart = now;
            while (candles.size() > 96) candles.remove(0);
            plugin.dirty();
        }
        cur.c = price; cur.h = Math.max(cur.h, price); cur.l = Math.min(cur.l, price);
        for (Board b : boards) draw(b);
    }

    private BlockDisplay slab(World w, Location at) {
        plugin.spawning = true;
        try {
            return w.spawn(at, BlockDisplay.class, e -> {
                e.setPersistent(false);
                e.setBrightness(new Display.Brightness(15, 15));
                e.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f()));
            });
        } finally { plugin.spawning = false; }
    }

    private TextDisplay label(World w, Location at) {
        plugin.spawning = true;
        try {
            return w.spawn(at, TextDisplay.class, e -> {
                e.setPersistent(false);
                e.setBillboard(Display.Billboard.FIXED);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setShadowed(false);
                e.setLineWidth(100000);
                e.setBrightness(new Display.Brightness(15, 15));
            });
        } finally { plugin.spawning = false; }
    }

    private static void shape(BlockDisplay d, Material m, double x, double y, double z, double sx, double sy, double sz) {
        if (d.getBlock().getMaterial() != m) d.setBlock(m.createBlockData());
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(10);
        d.setTransformation(new Transformation(new Vector3f((float) x, (float) y, (float) z), new AxisAngle4f(),
                new Vector3f((float) sx, (float) sy, (float) sz), new AxisAngle4f()));
    }

    private void draw(Board b) {
        World w = Bukkit.getWorld(b.world);
        boolean near = false;
        if (w != null && w.isChunkLoaded(((int) Math.floor(b.x)) >> 4, ((int) Math.floor(b.z)) >> 4))
            for (Player p : w.getPlayers()) if (p.getLocation().distanceSquared(new Location(w, b.x, b.y, b.z)) < 64 * 64) { near = true; break; }
        if (!near) { if (b.active) clear(b); b.active = false; return; }
        b.active = true;

        double cw = cfg("candle-width", 0.5);
        double W = b.width, H = b.height, top = 0.8, bottom = 0.3, right = 1.6;   // room for the title and the price labels
        int n = Math.min(candles.size(), (int) Math.floor((W - right - 0.2) / cw));
        List<Candle> view = candles.subList(candles.size() - n, candles.size());
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (Candle c : view) { lo = Math.min(lo, c.l); hi = Math.max(hi, c.h); }
        if (hi - lo < 0.05) { hi += 0.05; lo -= 0.05; }
        double pad = (hi - lo) * 0.08; double plo = lo - pad, phi = hi + pad;
        double y0 = -H / 2 + bottom, yh = H - top - bottom;

        // nothing changed since the last draw? then leave it
        Candle cur = view.get(n - 1);
        String sig = n + ":" + plo + ":" + phi + ":" + cur.c + ":" + cur.h + ":" + cur.l;
        if (sig.equals(b.drawn) && !b.bodies.isEmpty() && b.bodies.get(0).isValid()) return;
        b.drawn = sig;

        float yaw = switch (b.face) { case WEST -> 90f; case NORTH -> 180f; case EAST -> -90f; default -> 0f; };
        Location at = new Location(w, b.x, b.y, b.z, yaw, 0);
        double rx = b.face.getModZ(), rz = -b.face.getModX();

        while (b.grid.size() < 3) b.grid.add(slab(w, at));
        for (int i = 0; i < 3; i++)
            shape(b.grid.get(i), Material.GRAY_CONCRETE, -W / 2 + 0.2, y0 + yh * (i + 0.5) / 3.0, 0.004, W - right - 0.2, 0.025, 0.004);
        while (b.bodies.size() < n) { b.bodies.add(slab(w, at)); b.wicks.add(slab(w, at)); }
        while (b.bodies.size() > n) { b.bodies.remove(b.bodies.size() - 1).remove(); b.wicks.remove(b.wicks.size() - 1).remove(); }
        double bw = cw * 0.68, x0 = -W / 2 + 0.2 + (W - right - 0.2) - n * cw;      // newest candle against the right-hand labels
        for (int i = 0; i < n; i++) {
            Candle c = view.get(i);
            boolean up = c.c >= c.o;
            Material m = up ? Material.LIME_CONCRETE : Material.RED_CONCRETE;
            double yo = y0 + (c.o - plo) / (phi - plo) * yh, yc = y0 + (c.c - plo) / (phi - plo) * yh;
            double yl = y0 + (c.l - plo) / (phi - plo) * yh, yH = y0 + (c.h - plo) / (phi - plo) * yh;
            double x = x0 + i * cw + (cw - bw) / 2;
            shape(b.bodies.get(i), m, x, Math.min(yo, yc), 0.012, bw, Math.max(0.04, Math.abs(yc - yo)), 0.012);
            shape(b.wicks.get(i), m, x + bw / 2 - 0.03, yl, 0.008, 0.06, Math.max(0.04, yH - yl), 0.008);
        }

        // labels: the title top left, the high, low and last price down the right-hand side
        Exchange ex = plugin.exchange();
        boolean upDay = ex.change() >= 0;
        if (b.title == null || !b.title.isValid()) { b.title = label(w, at); b.hi = label(w, at); b.lo = label(w, at); }
        String t1 = "COLD HARD CASH ", t2 = Money.fmt(ex.price), t3 = (upDay ? " ▲ " : " ▼ ") + String.format(Locale.ROOT, "%.2f%%", Math.abs(ex.change() * 100));
        b.title.text(Component.text(t1, AMBER, TextDecoration.BOLD).append(Component.text(t2, WHITE).decoration(TextDecoration.BOLD, false))
                .append(Component.text(t3, upDay ? UP : DOWN).decoration(TextDecoration.BOLD, false)));
        double ts = 1.4, tw = (Ticker.px(t1, true) + Ticker.px(t2, false) + Ticker.px(t3, false)) * 0.025 * ts;
        place(b.title, w, b, rx, rz, yaw, -W / 2 + 0.3 + tw / 2, H / 2 - 0.62, ts);
        b.hi.text(Component.text(Money.fmt(hi), DIM));
        b.lo.text(Component.text(Money.fmt(lo), DIM));
        place(b.hi, w, b, rx, rz, yaw, W / 2 - right / 2, y0 + (hi - plo) / (phi - plo) * yh - 0.12, 1.0);
        place(b.lo, w, b, rx, rz, yaw, W / 2 - right / 2, y0 + (lo - plo) / (phi - plo) * yh - 0.12, 1.0);
    }

    private static void place(TextDisplay t, World w, Board b, double rx, double rz, float yaw, double x, double y, double scale) {
        float s = (float) scale;
        t.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f()));
        t.teleport(new Location(w, b.x + b.face.getModX() * 0.03 + rx * x, b.y + y, b.z + b.face.getModZ() * 0.03 + rz * x, yaw, 0));
    }

    // ---------------------------------------------------------------- saving

    void load(ConfigurationSection d) {
        endAll();
        boards.clear(); candles.clear();
        if (d == null) return;
        candleStart = d.getLong("started", System.currentTimeMillis());
        for (String s : d.getStringList("candles")) {
            String[] p = s.split(",");
            if (p.length != 4) continue;
            try { Candle c = new Candle(Double.parseDouble(p[0])); c.h = Double.parseDouble(p[1]); c.l = Double.parseDouble(p[2]); c.c = Double.parseDouble(p[3]); candles.add(c); }
            catch (NumberFormatException ignored) {}
        }
        for (String s : d.getStringList("boards")) {
            String[] p = s.split(",");
            if (p.length != 7) continue;
            try {
                Board b = new Board();
                b.world = p[0]; b.x = Double.parseDouble(p[1]); b.y = Double.parseDouble(p[2]); b.z = Double.parseDouble(p[3]);
                b.face = BlockFace.valueOf(p[4]); b.width = Integer.parseInt(p[5]); b.height = Integer.parseInt(p[6]);
                boards.add(b);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    void save(ConfigurationSection d) {
        d.set("started", candleStart);
        List<String> cs = new ArrayList<>();
        for (Candle c : candles) cs.add(c.o + "," + c.h + "," + c.l + "," + c.c);
        d.set("candles", cs);
        List<String> bs = new ArrayList<>();
        for (Board b : boards) bs.add(b.key());
        d.set("boards", bs);
    }
}
