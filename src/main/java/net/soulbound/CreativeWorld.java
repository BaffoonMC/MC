package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * The Creative world is sealed off from survival. Nothing gets in or out:
 * - Inventories swap at the border: walking in, your survival inventory, armour, XP, health, hunger and effects are put
 *   away and your creative inventory comes out; walking out, it all swaps back. Saved to disk, so a crash or a logout
 *   in the middle can't lose anything. (Skipped if Multiverse-Inventories is installed, which does the same job.)
 * - No ender chests at all in there, including /ec.
 * - No portals: none can be lit, and nothing walks through one.
 * - Only a short list of commands works (getting around and chatting); everything else is blocked.
 * - You're always in creative mode in there, and back to survival when you leave.
 * - Captors and captives can't be taken in.
 * Staff with soulbound.creative.bypass are left alone.
 */
final class CreativeWorld implements Listener {
    private final Soulbound plugin;
    private final File dir;

    CreativeWorld(Soulbound plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "creative");
    }

    private boolean on() { return plugin.getConfig().getBoolean("creative-world.enabled", true); }

    boolean isCreative(World w) {
        if (w == null || !on()) return false;
        for (String n : plugin.getConfig().getStringList("creative-world.worlds").isEmpty() ? List.of("creative") : plugin.getConfig().getStringList("creative-world.worlds"))
            if (w.getName().equalsIgnoreCase(n)) return true;
        return false;
    }

    private static boolean bypass(Player p) { return p.hasPermission("soulbound.creative.bypass"); }

    private boolean swapping() {
        String mode = plugin.getConfig().getString("creative-world.swap-inventories", "auto").toLowerCase(Locale.ROOT);
        if (mode.equals("false") || mode.equals("off")) return false;
        if (mode.equals("true") || mode.equals("on")) return true;
        var mvi = plugin.getServer().getPluginManager().getPlugin("Multiverse-Inventories");
        return mvi == null || !mvi.isEnabled();
    }

    private static void say(Player p, String s) { p.sendActionBar(Component.text(s, NamedTextColor.RED)); }

    // ---------------------------------------------------------------- the border: swap everything

    private File file(Player p) { return new File(dir, p.getUniqueId() + ".yml"); }

    private YamlConfiguration load(Player p) { return YamlConfiguration.loadConfiguration(file(p)); }

    private void save(Player p, YamlConfiguration y) {
        try { dir.mkdirs(); y.save(file(p)); }
        catch (IOException e) { plugin.getLogger().severe("Creative world: couldn't save " + p.getName() + "'s inventories: " + e.getMessage()); }
    }

    private static String items(ItemStack[] it) {
        ItemStack[] safe = new ItemStack[it.length];
        for (int i = 0; i < it.length; i++) safe[i] = it[i] == null ? ItemStack.empty() : it[i];
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(safe));
    }
    private static ItemStack[] items(String s) { return s == null ? null : ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(s)); }

    /** Walking in: put survival away (all of it), bring out the creative inventory. */
    private void enter(Player p) {
        if (bypass(p)) return;
        if (swapping()) {
            YamlConfiguration y = load(p);
            if (!y.getBoolean("away", false)) {   // never overwrite a survival kit that's already stored
                y.set("survival.items", items(p.getInventory().getContents()));
                y.set("survival.level", p.getLevel());
                y.set("survival.exp", (double) p.getExp());
                y.set("survival.health", p.getHealth());
                y.set("survival.food", p.getFoodLevel());
                y.set("survival.saturation", (double) p.getSaturation());
                y.set("survival.fire", p.getFireTicks());
                y.set("survival.gamemode", p.getGameMode().name());
                List<PotionEffect> fx = new ArrayList<>(p.getActivePotionEffects());
                y.set("survival.effects", fx);
                y.set("away", true);
                save(p, y);    // on disk before anything is cleared
            }
            p.getInventory().clear();
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            ItemStack[] c = items(y.getString("creative.items"));
            if (c != null) p.getInventory().setContents(c);
            p.setFireTicks(0);
        }
        p.setGameMode(GameMode.CREATIVE);
        p.sendMessage(Component.text("You're in the Creative world. Nothing comes in or goes out with you.", NamedTextColor.AQUA));
    }

    /** Walking out: keep the creative inventory for next time, give survival back exactly as it was. */
    private void leave(Player p) {
        if (bypass(p)) return;
        GameMode back = GameMode.SURVIVAL;
        if (swapping()) {
            YamlConfiguration y = load(p);
            if (y.getBoolean("away", false)) {
                y.set("creative.items", items(p.getInventory().getContents()));
                p.getInventory().clear();
                for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
                ItemStack[] s = items(y.getString("survival.items"));
                if (s != null) p.getInventory().setContents(s);
                p.setLevel(y.getInt("survival.level", 0));
                p.setExp((float) Math.max(0, Math.min(0.999, y.getDouble("survival.exp", 0))));
                var mh = p.getAttribute(Attribute.MAX_HEALTH);
                double max = mh == null ? 20 : mh.getValue();
                p.setHealth(Math.max(1, Math.min(max, y.getDouble("survival.health", max))));
                p.setFoodLevel(y.getInt("survival.food", 20));
                p.setSaturation((float) y.getDouble("survival.saturation", 5));
                p.setFireTicks(y.getInt("survival.fire", 0));
                List<?> fx = y.getList("survival.effects");
                if (fx != null) for (Object o : fx) if (o instanceof PotionEffect e) p.addPotionEffect(e);
                try { back = GameMode.valueOf(y.getString("survival.gamemode", "SURVIVAL")); } catch (IllegalArgumentException ignored) {}
                if (back == GameMode.CREATIVE || back == GameMode.SPECTATOR) back = GameMode.SURVIVAL;
                y.set("survival", null);
                y.set("away", false);
                save(p, y);
            }
        }
        if (p.getGameMode() == GameMode.CREATIVE) p.setGameMode(back);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean from = isCreative(e.getFrom()), to = isCreative(p.getWorld());
        if (from && !to) leave(p);
        else if (!from && to) enter(p);
    }

    /** After a crash or a logout inside: make sure they're on the right side of the border. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || bypass(p)) return;
            boolean away = swapping() && load(p).getBoolean("away", false);
            if (isCreative(p.getWorld())) { if (!away) enter(p); else p.setGameMode(GameMode.CREATIVE); }
            else if (away) leave(p);
            else if (p.getGameMode() == GameMode.CREATIVE && on() && !p.isOp()) p.setGameMode(GameMode.SURVIVAL);
        }, 5L);
    }

    // ---------------------------------------------------------------- locked doors

    /** No ender chest in there: not the block, not /ec, nothing. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || bypass(p) || !isCreative(p.getWorld())) return;
        if (e.getInventory().getType() == InventoryType.ENDER_CHEST) { e.setCancelled(true); say(p, "Ender chests don't work in the Creative world."); }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClickEnderChest(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null || e.getClickedBlock().getType() != Material.ENDER_CHEST) return;
        Player p = e.getPlayer();
        if (bypass(p) || !isCreative(p.getWorld())) return;
        if (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) { e.setCancelled(true); say(p, "Ender chests don't work in the Creative world."); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent e) {
        if (isCreative(e.getFrom().getWorld()) && !bypass(e.getPlayer())) { e.setCancelled(true); say(e.getPlayer(), "Portals don't lead anywhere from the Creative world."); }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent e) { if (isCreative(e.getFrom().getWorld())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent e) { if (isCreative(e.getWorld())) e.setCancelled(true); }

    /** Nobody drags a captive in, and nobody escapes into creative while held. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();
        if (e.getTo() == null || bypass(p) || !isCreative(e.getTo().getWorld()) || isCreative(e.getFrom().getWorld())) return;
        if (!canEnter(p)) {   // a donor perk: /warp creative, /tpa to someone there, anything
            e.setCancelled(true);
            say(p, "The Creative world is for supporters. Any rank from the store unlocks it: store.playbaffoonmc.net");
            return;
        }
        var cm = plugin.captures();
        if (cm != null && (cm.isCaptive(p) || cm.heldBy(p) != null)) { e.setCancelled(true); say(p, "You can't go to the Creative world while a capture is going on."); }
    }

    /** Supporter, Patron or Chosen (or anyone given soulbound.creative.access). */
    private boolean canEnter(Player p) {
        if (!plugin.getConfig().getBoolean("creative-world.donors-only", true)) return true;
        if (p.hasPermission("soulbound.creative.access")) return true;
        for (String g : plugin.getConfig().getStringList("creative-world.donor-groups").isEmpty() ? List.of("supporter", "patron", "chosen") : plugin.getConfig().getStringList("creative-world.donor-groups"))
            if (p.hasPermission("group." + g)) return true;
        return false;
    }

    /** Only getting around and talking: everything else stays outside. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (bypass(p) || !isCreative(p.getWorld())) return;
        String label = e.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);
        Collection<String> ok = plugin.getConfig().getStringList("creative-world.allowed-commands");
        if (ok.isEmpty()) ok = List.of("spawn", "home", "homes", "tpa", "tpahere", "tpaccept", "tpyes", "tpdeny", "tpno", "tpacancel", "back", "warp", "warps",
                "msg", "tell", "w", "r", "reply", "me", "rules", "list", "help", "ping", "hat", "nick", "sethome", "delhome");
        for (String c : ok) if (c.equalsIgnoreCase(label)) return;
        e.setCancelled(true);
        say(p, "/" + label + " doesn't work in the Creative world.");
    }
}
