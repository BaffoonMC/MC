package net.baffoon.bureau;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.MemoryNPCDataStore;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * A walking lizard run by the Citizens plugin: a Mannequin NPC that really walks (Citizens moves it like a player),
 * still wearing the pack's lizard skin. Only loaded when Citizens is installed. With Sentinel too, a bodyguard
 * becomes a proper Sentinel guard.
 */
final class NpcBody {
    private static NPCRegistry registry;
    final NPC npc;
    private final double speed;
    boolean sentinel;

    private NpcBody(NPC npc, double speed) { this.npc = npc; this.speed = speed; }

    // ---------------------------------------------------------------- skins
    // Citizens' player NPCs need a Mojang-signed skin, so each lizard skin (shipped inside this plugin) is uploaded
    // once through Citizens' own skin generator and the signed result is kept in plugins/Bureau/skins.yml.
    private static final java.util.Map<String, String[]> SKINS = new java.util.concurrent.ConcurrentHashMap<>();   // skin -> {id, value, signature}
    private static volatile boolean generating;
    private static volatile String lastError = "";

    static String skinStatus() {
        return SKINS.size() + "/4 lizard skins ready" + (generating ? " (still uploading the rest)" : "") + (lastError.isEmpty() ? "." : ". Last problem: " + lastError);
    }

    static void prepareSkins(Bureau plugin, boolean force) {
        java.io.File f = new java.io.File(plugin.getDataFolder(), "skins.yml");
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        if (force) SKINS.clear();
        else for (String k : y.getKeys(false)) {
            String v = y.getString(k + ".value"), sig = y.getString(k + ".signature");
            if (v != null && sig != null) SKINS.put(k, new String[]{y.getString(k + ".id", "bureau_" + k), v, sig});
        }
        if (generating) return;
        generating = true;
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                var gen = Class.forName("net.citizensnpcs.util.MojangSkinGenerator").getMethod("generateFromPNG", byte[].class, boolean.class);
                for (String k : java.util.List.of("security", "taxman", "clerk", "director")) {
                    if (SKINS.containsKey(k)) continue;
                    try (var in = plugin.getResource("skins/lizard_" + k + ".png")) {
                        if (in == null) { lastError = "skin file for " + k + " is missing from the jar"; continue; }
                        Object data = gen.invoke(null, in.readAllBytes(), false);
                        if (!(data instanceof java.util.Map<?, ?> m) || !(m.get("texture") instanceof java.util.Map<?, ?> t)
                                || t.get("value") == null || t.get("signature") == null) { lastError = "the skin service gave no answer for " + k; continue; }
                        String id = m.get("uuid") != null ? String.valueOf(m.get("uuid")) : "bureau_" + k;
                        SKINS.put(k, new String[]{id, String.valueOf(t.get("value")), String.valueOf(t.get("signature"))});
                        y.set(k + ".id", id); y.set(k + ".value", String.valueOf(t.get("value"))); y.set(k + ".signature", String.valueOf(t.get("signature")));
                        y.save(f);
                        plugin.getLogger().info("Lizard skin ready: " + k);
                    } catch (Throwable t) {
                        lastError = k + ": " + (t.getCause() != null ? t.getCause() : t);
                        plugin.getLogger().warning("Couldn't prepare the " + k + " lizard skin: " + lastError);
                    }
                    try { Thread.sleep(6000); } catch (InterruptedException ignored) {}
                }
            } catch (Throwable t) {
                lastError = String.valueOf(t);
                plugin.getLogger().warning("Couldn't reach Citizens' skin generator: " + t);
            } finally { generating = false; }
        });
    }

    @SuppressWarnings("unchecked")
    private static void dress(NPC npc, Lizards.Role role) throws Exception {
        String[] s = SKINS.get(role.skin);
        if (s == null) return;
        Class<?> st = Class.forName("net.citizensnpcs.trait.SkinTrait");
        Object trait = npc.getOrAddTrait((Class<? extends net.citizensnpcs.api.trait.Trait>) st);
        st.getMethod("setSkinPersistent", String.class, String.class, String.class).invoke(trait, s[0], s[2], s[1]);
    }

    static NpcBody spawn(Bureau plugin, Location at, Lizards.Role role, Component name, double speed) {
        if (registry == null) registry = CitizensAPI.createAnonymousNPCRegistry(new MemoryNPCDataStore());
        // a player-type NPC: the kind Citizens and Sentinel are built around (walks, swings, takes knockback)
        NPC npc = registry.createNPC(EntityType.PLAYER, LegacyComponentSerializer.legacyAmpersand().serialize(name));
        npc.setProtected(true);
        try { dress(npc, role); } catch (Throwable t) { plugin.getLogger().warning("Couldn't put the lizard skin on an NPC: " + t); }
        plugin.spawning = true;
        try { npc.spawn(at); } finally { plugin.spawning = false; }
        if (!(npc.getEntity() instanceof LivingEntity m)) { npc.destroy(); return null; }
        m.getPersistentDataContainer().set(Walker.key, PersistentDataType.BYTE, (byte) 1);
        // the old husk walkers used the movement-speed attribute (about 0.25): turn that into a pace for Citizens
        float pace = (float) Math.max(0.5, Math.min(2.0, speed / 0.25));
        npc.getNavigator().getDefaultParameters().speedModifier(pace).range(64f).distanceMargin(1.5);
        return new NpcBody(npc, pace);
    }

    boolean valid() { return npc.isSpawned() && npc.getEntity() != null && npc.getEntity().isValid(); }
    Entity entity() { return npc.getEntity(); }
    Location at() { Entity e = npc.getEntity(); return e != null ? e.getLocation() : npc.getStoredLocation(); }

    void walkTo(LivingEntity target, double mul) {
        var nav = npc.getNavigator();
        if (nav.isNavigating() && nav.getEntityTarget() != null && target.equals(nav.getEntityTarget().getTarget())) return;
        nav.setTarget(target, false);
        nav.getLocalParameters().speedModifier((float) (speed * mul));
    }

    void walkTo(Location to, double mul) {
        npc.getNavigator().setTarget(to);
        npc.getNavigator().getLocalParameters().speedModifier((float) (speed * mul));
    }

    void stop() { if (npc.getNavigator().isNavigating()) npc.getNavigator().cancelNavigation(); }
    void teleport(Location l) { npc.teleport(l, PlayerTeleportEvent.TeleportCause.PLUGIN); }
    void face(Location l) { if (!npc.getNavigator().isNavigating()) npc.faceLocation(l); }
    void remove() { npc.destroy(); }

    void target(java.util.UUID who) { if (sentinel) try { SentinelHook.target(npc, who); } catch (Throwable ignored) {} }

    /** With Sentinel installed: guard this player and fight monsters. False if Sentinel isn't there or refused. */
    boolean guard(Player client, double damage, double health, boolean players) {
        try {
            SentinelHook.guard(npc, client, damage, health, players);
            sentinel = true;
            if (npc.getEntity() != null) npc.getEntity().getPersistentDataContainer().set(Walker.key, PersistentDataType.BYTE, (byte) 2);
        } catch (Throwable t) { sentinel = false; }
        return sentinel;
    }
}
