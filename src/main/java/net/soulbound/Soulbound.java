package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class Soulbound extends JavaPlugin {
    private Store store;
    private Zones zones;
    private LeashItem leash;
    private CaptureManager captures;
    private CurseManager curses;
    private HungerMeter hunger;
    private MolochBoss boss;
    private SacrificeManager sacrifices;
    private PortalManager portals;
    private SummitDrums drums;
    private MolochMusic music;
    private GodMusic godMusic;
    private StrongholdGate gate;
    private ArenaSeal seal;
    private Parasite parasite;
    private BloodMoonBeasts beasts;
    private Tartare tartare;
    private TumourPie tumourPie;
    private CloudCandy cloudCandy;
    private RaptureCrossbow raptureCrossbow;
    private DeeperStill deeperStill;
    private WildDrop wildDrop;
    private AccountReset accountReset;
    private Sleeper sleeper;
    private GiantCreaking giantCreaking;
    private SafeZones safeZones;
    private Broadcasts broadcasts;
    private CombatLog combatLog;
    private FesteringFlesh festering;
    private ThiefKey thiefKey;
    private KrakenBeard kraken;
    private Cute cute;
    private Laestrygonians laestrygonians;
    private ChatFix chatFix;
    private NewPlayerPing newPlayerPing;
    private GodGear godGear;
    private QuantumGear quantum;
    private PotentialBlade potentialBlade;
    private CultistAxe cultistAxe;
    private BloodEmeralds bloodEmeralds;
    private TumourCrop tumourCrop;
    private CloudCotton cloudCotton;
    private GrowthPotions growthPotions;
    private Bestiary bestiary;
    private GodManager god;
    private HeavenAir heavenAir;
    private HeavenBuilder heaven;
    private HaloItem halo;
    private MolochGear gear;
    private BloodPriest priest;
    private Archangels archangels;
    private Inhuman inhuman;
    private InhumanHearts inhumanHearts;
    private InhumanHunger inhumanHunger;
    private InhumanPoison inhumanPoison;
    private QuantumStand quantumStand;
    private Homunculus homunculus;
    private LoreFixer loreFixer;
    private Grace grace;
    private EdenMask eden;
    private Zephyrs zephyrs;
    private BrimstoneCubes brimstone;
    private SulfurStrawberry sulfurStrawberry;
    private HatWardrobe wardrobe;
    private Peacekeeper peacekeeper;
    private IslandGuard island;
    private ScreenCrack cracks;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // one-off: his scream's nausea went from 6 to 11 seconds; bump configs still on the old default
        // one-off: Moloch's Regalia no longer makes you bigger (a saved 1.5 goes back to normal size)
        if (!getConfig().getBoolean("migrated.regalia-no-grow", false)) {
            if (getConfig().getDouble("boss.regalia-set-scale", 1.0) == 1.5) getConfig().set("boss.regalia-set-scale", 1.0);
            getConfig().set("migrated.regalia-no-grow", true);
            getConfig().set("migrated.regalia-scale-15", true);   // and the older "grow again" one-off must never run after this
            saveConfig();
        }
        // one-off: an older one-off could put the Regalia back to 1.5x after the one above; normal size, for good
        if (!getConfig().getBoolean("migrated.regalia-normal-size", false)) {
            if (getConfig().getDouble("boss.regalia-set-scale", 1.0) > 1.0) getConfig().set("boss.regalia-set-scale", 1.0);
            getConfig().set("migrated.regalia-normal-size", true);
            saveConfig();
        }
        if (!getConfig().getBoolean("migrated.scream-nausea-11", false)) {
            if (getConfig().getInt("boss.scream-nausea-seconds", 6) == 6) getConfig().set("boss.scream-nausea-seconds", 11);
            getConfig().set("migrated.scream-nausea-11", true);
            saveConfig();
        }
        // one-off: Archangel ghasts were too rare
        if (!getConfig().getBoolean("migrated.archangel-ghasts-more", false)) {
            if (getConfig().getDouble("archangels.ghasts.chance", 0.2) == 0.08) getConfig().set("archangels.ghasts.chance", 0.2);
            if (getConfig().getInt("archangels.ghasts.max-per-player", 3) == 2) getConfig().set("archangels.ghasts.max-per-player", 3);
            getConfig().set("migrated.archangel-ghasts-more", true);
            saveConfig();
        }
        // one-off: ten new hats and masks join the Hat Token catalogue (only if the list was never customised)
        if (!getConfig().getBoolean("migrated.wardrobe-ten-more", false)) {
            if (getConfig().isSet("wardrobe.catalogue") && getConfig().getStringList("wardrobe.catalogue").equals(List.of("sinners_halo", "cortex_leech", "wizard_hat", "crocodile_mask", "baroque", "flower_crown", "bunny_ears"))) {
                List<String> l = new java.util.ArrayList<>(getConfig().getStringList("wardrobe.catalogue"));
                l.addAll(List.of("dunce_cap", "furry_mask", "goldfish_bowl", "pigeon_pal", "party_lampshade", "the_visitor", "bag_man", "froggy", "load_of_bull", "oni_mask"));
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-ten-more", true);
            saveConfig();
        }
        // one-off: five more (Head First, Hypno Helm, Shroom Bloom, Hehe, Smug Troll) join a saved catalogue that has the ten
        if (!getConfig().getBoolean("migrated.wardrobe-five-more", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("oni_mask") && !cat.contains("head_first")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.addAll(List.of("head_first", "hypno_helm", "shroom_bloom", "hehe_mask", "smug_troll"));
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-five-more", true);
            saveConfig();
        }
        // one-off: bastions (new in Moloch's realm) keep away from the temple like fortresses do, in a saved list too
        if (!getConfig().getBoolean("migrated.bastion-keep-away", false)) {
            if (getConfig().isList("realm-structures.keep-away")) {
                List<String> l = new java.util.ArrayList<>(getConfig().getStringList("realm-structures.keep-away"));
                if (l.contains("sacrifice:fortress") && !l.contains("sacrifice:bastion")) { l.add("sacrifice:bastion"); getConfig().set("realm-structures.keep-away", l); }
            }
            getConfig().set("migrated.bastion-keep-away", true);
            saveConfig();
        }
        // one-off: the Judge's Wig and Star Stalker join a saved catalogue that already has the earlier additions
        if (!getConfig().getBoolean("migrated.wardrobe-two-more", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("smug_troll") && !cat.contains("judges_wig")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.addAll(List.of("judges_wig", "star_stalker"));
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-two-more", true);
            saveConfig();
        }
        // one-off: the Fat Steve Head and Villager Head join a saved catalogue that already has the earlier additions
        if (!getConfig().getBoolean("migrated.wardrobe-heads", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("star_stalker") && !cat.contains("fat_steve_head")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.addAll(List.of("fat_steve_head", "villager_head"));
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-heads", true);
            saveConfig();
        }
        // one-off: Umbrella Head, Vindicator Head and Hatception join a saved catalogue that already has the earlier additions
        if (!getConfig().getBoolean("migrated.wardrobe-three-more", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("villager_head") && !cat.contains("umbrella_head")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.addAll(List.of("vindicator_head", "hatception"));
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-three-more", true);
            saveConfig();
        }
        // one-off: Villagerbrine joins a saved catalogue that already has the earlier additions
        if (!getConfig().getBoolean("migrated.wardrobe-brine", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("hatception") && !cat.contains("villagerbrine")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.add("villagerbrine");
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-brine", true);
            saveConfig();
        }
        // one-off: Hatception is the Blood Priest's own now, so it comes off a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-no-hatception", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("hatception")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.remove("hatception");
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-no-hatception", true);
            saveConfig();
        }
        // one-off: Hehe, the Judge's Wig and the Star Stalker are retired, so they come off a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-retire-three", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue")) {
                List<String> l = new java.util.ArrayList<>(cat);
                if (l.removeAll(List.of("hehe_mask", "judges_wig", "star_stalker"))) getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-retire-three", true);
            saveConfig();
        }
        // one-off: the Spartan Helm, Caesar's Laurels and Pharaoh's Nemes join a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-classics", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("villagerbrine") && !cat.contains("spartan_helm")) {
                List<String> l = new java.util.ArrayList<>(cat);
                l.addAll(List.of("spartan_helm", "caesars_laurels", "pharaohs_nemes"));
                getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-classics", true);
            saveConfig();
        }
        // one-off: the Oni Mask is retired, so it comes off a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-retire-oni", false)) {
            List<String> l = new java.util.ArrayList<>(getConfig().getStringList("wardrobe.catalogue"));
            if (getConfig().isSet("wardrobe.catalogue") && l.remove("oni_mask")) getConfig().set("wardrobe.catalogue", l);
            getConfig().set("migrated.wardrobe-retire-oni", true);
            saveConfig();
        }
        // one-off: Guess Who joins a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-guess-who", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("pharaohs_nemes") && !cat.contains("guess_who")) {
                List<String> l = new java.util.ArrayList<>(cat); l.add("guess_who"); getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-guess-who", true);
            saveConfig();
        }
        // one-off: the Fly-llager joins a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-flyllager", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("guess_who") && !cat.contains("flyllager")) {
                List<String> l = new java.util.ArrayList<>(cat); l.add("flyllager"); getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-flyllager", true);
            saveConfig();
        }
        // one-off: the Yeti, Balaclava and Bandana join a saved token catalogue
        if (!getConfig().getBoolean("migrated.wardrobe-yeti-three", false)) {
            List<String> cat = getConfig().getStringList("wardrobe.catalogue");
            if (getConfig().isSet("wardrobe.catalogue") && cat.contains("guess_who") && !cat.contains("balaclava")) {
                List<String> l = new java.util.ArrayList<>(cat); l.addAll(List.of("balaclava", "bandana")); getConfig().set("wardrobe.catalogue", l);
            }
            getConfig().set("migrated.wardrobe-yeti-three", true);
            saveConfig();
        }
        // one-off: the Zombie Bell raises 4 for 2 minutes now
        if (!getConfig().getBoolean("migrated.bell-4-120", false)) {
            if (getConfig().getInt("deeper-still.bell-zombies", 3) == 3) getConfig().set("deeper-still.bell-zombies", 4);
            if (getConfig().getInt("deeper-still.bell-zombie-seconds", 60) == 60) getConfig().set("deeper-still.bell-zombie-seconds", 120);
            getConfig().set("migrated.bell-4-120", true);
            saveConfig();
        }
        // one-off: the broadcast tag reads "BaffoonMC >"
        if ("<gold><bold>BAFFOON</bold></gold> <dark_gray>»</dark_gray> ".equals(getConfig().getString("broadcasts.prefix"))) {
            getConfig().set("broadcasts.prefix", "<gold><bold>BaffoonMC</bold></gold> <dark_gray>></dark_gray> ");
            saveConfig();
        }
        // one-off: a player kill drops everything, not just the backpack
        if (!getConfig().getBoolean("migrated.pvp-drops-all", false)) {
            if ("backpack".equalsIgnoreCase(getConfig().getString("death.pvp-drops", "backpack"))) getConfig().set("death.pvp-drops", "all");
            getConfig().set("migrated.pvp-drops-all", true);
            saveConfig();
        }
        // one-off: the Rapture Crossbow loads in 0.4 s instead of 0.25 s
        if (!getConfig().getBoolean("migrated.rapture-load-8", false)) {
            if (getConfig().getInt("rapture-crossbow.load-ticks", 5) == 5) getConfig().set("rapture-crossbow.load-ticks", 8);
            getConfig().set("migrated.rapture-load-8", true);
            saveConfig();
        }
        // one-off: the Umbrella Head and the Yeti were taken out of the game
        if (!getConfig().getBoolean("migrated.wardrobe-drop-umbrella-yeti", false)) {
            List<String> cat = new java.util.ArrayList<>(getConfig().getStringList("wardrobe.catalogue"));
            if (getConfig().isSet("wardrobe.catalogue") && cat.removeAll(List.of("umbrella_head", "yeti_mask"))) getConfig().set("wardrobe.catalogue", cat);
            getConfig().set("migrated.wardrobe-drop-umbrella-yeti", true);
            saveConfig();
        }
        // one-off: blood moons went from 24 hours to 1
        if (!getConfig().getBoolean("migrated.blood-moon-1h", false)) {
            if (getConfig().getInt("boss.blood-moon-hours", 24) == 24) getConfig().set("boss.blood-moon-hours", 1);
            getConfig().set("migrated.blood-moon-1h", true);
            saveConfig();
        }
        // one-off: no more cooldown before a freed player can be captured again
        if (!getConfig().getBoolean("migrated.capture-no-immunity", false)) {
            getConfig().set("capture.immunity-seconds", 0);
            getConfig().set("migrated.capture-no-immunity", true);
            saveConfig();
        }
        // one-off: a human sacrifice now feeds 40% of Moloch's hunger (was 12%)
        if (!getConfig().getBoolean("migrated.sacrifice-feeds-40", false)) {
            getConfig().set("hunger.per-sacrifice", 40);
            getConfig().set("migrated.sacrifice-feeds-40", true);
            saveConfig();
        }
        // one-off: offering points are now 0.1% each (the data pack scaled its points up to match; redstone got cheaper)
        if (!getConfig().getBoolean("migrated.offer-points-0.1", false)) {
            if (getConfig().getDouble("hunger.per-offering-point", 0.1) == 0.5) getConfig().set("hunger.per-offering-point", 0.1);
            getConfig().set("migrated.offer-points-0.1", true);
            saveConfig();
        }
        // one-off: no more Demons (burning phantoms) in Moloch's fight
        if (!getConfig().getBoolean("migrated.no-demons", false)) {
            getConfig().set("boss.demons", false);
            getConfig().set("migrated.no-demons", true);
            saveConfig();
        }
        // one-off: at most 2 Archangel ghasts per player (was 3)
        if (!getConfig().getBoolean("migrated.archangels-2", false)) {
            if (getConfig().getInt("archangels.ghasts.max-per-player", 2) == 3) getConfig().set("archangels.ghasts.max-per-player", 2);
            getConfig().set("migrated.archangels-2", true);
            saveConfig();
        }
        // one-off: 3 minutes before an escaped player can be captured again
        if (!getConfig().getBoolean("migrated.capture-immunity-180", false)) {
            if (getConfig().getInt("capture.immunity-seconds", 180) == 0) getConfig().set("capture.immunity-seconds", 180);
            getConfig().set("migrated.capture-immunity-180", true);
            saveConfig();
        }
        // one-off: Brimstone Cubes burn twice as hard inside (1 heart a second -> 2)
        if (!getConfig().getBoolean("migrated.brimstone-damage-4", false)) {
            if (getConfig().getDouble("brimstone-cubes.damage-per-second", 4) == 2) getConfig().set("brimstone-cubes.damage-per-second", 4);
            getConfig().set("migrated.brimstone-damage-4", true);
            saveConfig();
        }
        // one-off: fewer Zephyrs (one at a time, about one a minute)
        if (!getConfig().getBoolean("migrated.zephyrs-fewer", false)) {
            if (getConfig().getDouble("zephyrs.chance", 0.08) == 0.25) getConfig().set("zephyrs.chance", 0.08);
            if (getConfig().getInt("zephyrs.max-per-player", 1) == 2) getConfig().set("zephyrs.max-per-player", 1);
            getConfig().set("migrated.zephyrs-fewer", true);
            saveConfig();
        }
        // one-off: Moloch's Regalia drops more often (15% -> 35%)
        if (!getConfig().getBoolean("migrated.regalia-35", false)) {
            if (getConfig().getDouble("boss.regalia-drop-chance", 0.35) == 0.15) getConfig().set("boss.regalia-drop-chance", 0.35);
            getConfig().set("migrated.regalia-35", true);
            saveConfig();
        }
        // one-off: the capture leash is now the Cultist's Shackles
        if (!getConfig().getBoolean("migrated.cultists-shackles", false)) {
            if ("Bondage Leash".equals(getConfig().getString("leash.name", "Bondage Leash"))) getConfig().set("leash.name", "Cultist's Shackles");
            getConfig().set("migrated.cultists-shackles", true);
            saveConfig();
        }
        store = new Store(this);
        zones = new Zones(this);
        leash = new LeashItem(this);
        leash.registerRecipe();
        captures = new CaptureManager(this, leash);
        curses = new CurseManager(this, store);
        boss = new MolochBoss(this, store, zones);
        hunger = new HungerMeter(this, store);
        sacrifices = new SacrificeManager(this, store, captures, curses, hunger, zones);
        portals = new PortalManager(this, store, zones, captures);
        drums = new SummitDrums(this, zones);
        music = new MolochMusic(this);
        halo = new HaloItem(this, store);
        gear = new MolochGear(this, store);
        priest = new BloodPriest(this, captures, leash);
        cracks = new ScreenCrack(this);
        god = new GodManager(this, store);
        heavenAir = new HeavenAir(this, god);
        heaven = new HeavenBuilder(this, store);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(captures, this);
        pm.registerEvents(priest, this);
        pm.registerEvents(curses, this);
        pm.registerEvents(hunger, this);
        pm.registerEvents(boss, this);
        pm.registerEvents(sacrifices, this);
        pm.registerEvents(portals, this);
        pm.registerEvents(god, this);
        pm.registerEvents(heaven, this);
        pm.registerEvents(halo, this);
        pm.registerEvents(gear, this);
        godGear = new GodGear(this, store);
        pm.registerEvents(godGear, this);
        godGear.start();
        quantum = new QuantumGear(this);
        pm.registerEvents(quantum, this);
        quantum.registerRecipes();
        quantum.start();
        potentialBlade = new PotentialBlade(this);
        pm.registerEvents(potentialBlade, this);
        potentialBlade.registerRecipe();
        cultistAxe = new CultistAxe(this);
        pm.registerEvents(cultistAxe, this);
        bloodEmeralds = new BloodEmeralds(this);
        pm.registerEvents(bloodEmeralds, this);
        bloodEmeralds.start();
        tumourCrop = new TumourCrop(this);
        pm.registerEvents(tumourCrop, this);
        tumourCrop.start();
        cloudCotton = new CloudCotton(this);
        pm.registerEvents(cloudCotton, this);
        cloudCotton.start();
        growthPotions = new GrowthPotions(this);
        pm.registerEvents(growthPotions, this);
        growthPotions.start();
        bestiary = new Bestiary(this);
        pm.registerEvents(bestiary, this);
        bestiary.start();
        LongArms.start(this);
        if (getCommand("bestiary") != null) getCommand("bestiary").setExecutor(bestiary);
        gate = new StrongholdGate(this, store);
        pm.registerEvents(gate, this);
        gate.start();
        seal = new ArenaSeal(this, store);
        pm.registerEvents(seal, this);
        seal.start();
        parasite = new Parasite(this);
        pm.registerEvents(parasite, this);
        parasite.start();
        beasts = new BloodMoonBeasts(this);
        pm.registerEvents(beasts, this);
        beasts.start();
        tartare = new Tartare(this);
        pm.registerEvents(tartare, this);
        tartare.registerRecipe();
        tumourPie = new TumourPie(this);
        pm.registerEvents(tumourPie, this);
        tumourPie.registerRecipe();
        cloudCandy = new CloudCandy(this);
        pm.registerEvents(cloudCandy, this);
        cloudCandy.registerRecipe();
        raptureCrossbow = new RaptureCrossbow(this);
        pm.registerEvents(raptureCrossbow, this);
        deeperStill = new DeeperStill(this);
        pm.registerEvents(deeperStill, this);
        deeperStill.start();
        wildDrop = new WildDrop(this);
        wildDrop.start();
        pm.registerEvents(new Welcome(this), this);
        accountReset = new AccountReset(this);
        pm.registerEvents(accountReset, this);
        Rtp rtp = new Rtp(this);
        pm.registerEvents(rtp, this);
        if (getCommand("rtp") != null) getCommand("rtp").setExecutor(rtp);
        if (getCommand("store") != null) getCommand("store").setExecutor(new StoreLink(this));
        Market market = new Market(this);
        pm.registerEvents(market, this);
        market.start();
        if (getCommand("market") != null) { getCommand("market").setExecutor(market); getCommand("market").setTabCompleter(market); }
        ServerShop serverShop = new ServerShop(this);
        pm.registerEvents(serverShop, this);
        if (getCommand("shop") != null) { getCommand("shop").setExecutor(serverShop); getCommand("shop").setTabCompleter(serverShop); }
        broadcasts = new Broadcasts(this);
        broadcasts.start();
        sleeper = new Sleeper(this);
        pm.registerEvents(sleeper, this);
        sleeper.start();
        giantCreaking = new GiantCreaking(this);
        pm.registerEvents(giantCreaking, this);
        giantCreaking.start();
        festering = new FesteringFlesh(this);
        pm.registerEvents(festering, this);
        thiefKey = new ThiefKey(this);
        pm.registerEvents(thiefKey, this);
        kraken = new KrakenBeard(this);
        pm.registerEvents(kraken, this);
        kraken.start();
        cute = new Cute(this);
        laestrygonians = new Laestrygonians(this);
        chatFix = new ChatFix(this);
        newPlayerPing = new NewPlayerPing(this);
        pm.registerEvents(newPlayerPing, this);
        pm.registerEvents(chatFix, this);
        pm.registerEvents(laestrygonians, this);
        pm.registerEvents(cute, this);
        cute.registerRecipes();
        cute.start();
        archangels = new Archangels(this);
        pm.registerEvents(archangels, this);
        archangels.start();
        new IslandClaims(this).start();
        island = new IslandGuard(this);
        pm.registerEvents(island, this);
        island.start();
        pm.registerEvents(new HeavenBeds(this), this);
        pm.registerEvents(new RealmStructures(this), this);
        pm.registerEvents(new RoadGuard(this), this);
        pm.registerEvents(new HitHealth(this), this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try { new SoulboundPlaceholders(this).register(); getLogger().info("PlaceholderAPI found: %soulbound_...% placeholders registered."); }
            catch (Throwable t) { getLogger().warning("Couldn't register placeholders: " + t); }
        }
        loreFixer = new LoreFixer(this);
        pm.registerEvents(loreFixer, this);
        OuterDaylight outerDay = new OuterDaylight(this);
        pm.registerEvents(outerDay, this);
        outerDay.start();
        // the glowstone (Aether-style) portal is switched off: water in a glowstone frame does nothing, and any made before are closed
        GlowPortal glowPortal = new GlowPortal(this);
        if (getConfig().getBoolean("glow-portal.enabled", false)) { pm.registerEvents(glowPortal, this); glowPortal.start(); }
        else getServer().getScheduler().runTaskLater(this, () -> { int n = glowPortal.dismantleAll(); if (n > 0) getLogger().info("Closed " + n + " old glowstone portal(s)."); }, 40L);
        pm.registerEvents(new WorldEgg(this), this);
        inhumanHearts = new InhumanHearts(this);   // off for now: not started
        inhuman = new Inhuman(this, captures);
        pm.registerEvents(inhuman, this);
        inhuman.start();
        inhumanHunger = new InhumanHunger(this);
        pm.registerEvents(inhumanHunger, this);
        inhumanHunger.start();
        inhumanPoison = new InhumanPoison(this);
        pm.registerEvents(inhumanPoison, this);
        inhumanPoison.start();
        quantumStand = new QuantumStand(this);
        pm.registerEvents(quantumStand, this);
        quantumStand.start();
        pm.registerEvents(new DeathDrops(this), this);
        pm.registerEvents(new EndShips(this), this);
        pm.registerEvents(new NoHunger(this), this);
        pm.registerEvents(new CommandHider(this), this);
        pm.registerEvents(new Motd(this), this);
        combatLog = new CombatLog(this);
        pm.registerEvents(combatLog, this);
        combatLog.start();
        safeZones = new SafeZones(this);
        pm.registerEvents(safeZones, this);
        homunculus = new Homunculus(this);
        pm.registerEvents(homunculus, this);
        homunculus.start();
        grace = new Grace(this);
        eden = new EdenMask(this);
        zephyrs = new Zephyrs(this);
        pm.registerEvents(zephyrs, this);
        zephyrs.start();
        brimstone = new BrimstoneCubes(this);
        pm.registerEvents(brimstone, this);
        brimstone.start();
        sulfurStrawberry = new SulfurStrawberry(this);
        pm.registerEvents(new CreativeWorld(this), this);
        pm.registerEvents(sulfurStrawberry, this);
        peacekeeper = new Peacekeeper(this);
        pm.registerEvents(peacekeeper, this);
        peacekeeper.start();
        pm.registerEvents(grace, this);
        wardrobe = new HatWardrobe(this);
        pm.registerEvents(wardrobe, this);
        Flight flight = new Flight(this);
        pm.registerEvents(flight, this);
        flight.start();
        if (getCommand("claimfly") != null) getCommand("claimfly").setExecutor(flight);
        if (getCommand("wardrobe") != null) { getCommand("wardrobe").setExecutor(wardrobe); getCommand("wardrobe").setTabCompleter(wardrobe); }
        SbCommand sb = new SbCommand(this);
        pm.registerEvents(sb, this);
        wardrobe.useRegistry(sb::items);
        if (getCommand("soulbound") != null) { getCommand("soulbound").setExecutor(sb); getCommand("soulbound").setTabCompleter(sb); }
        pm.registerEvents(new Listener() {
            @EventHandler public void onJoin(PlayerJoinEvent e) { leash.unlockFor(e.getPlayer()); }
        }, this);
        for (Player p : getServer().getOnlinePlayers()) leash.unlockFor(p);

        captures.start();
        curses.start();
        boss.start();
        hunger.start();
        sacrifices.start();
        portals.start();
        drums.start();
        music.start();
        god.start();
        heavenAir.start();
        godMusic = new GodMusic(this, god);
        godMusic.start();
        heaven.start();
        halo.start();
        gear.start();
        priest.start();
        // Moloch's realm: a lower monster cap than the vanilla 70, so the terraces aren't swarming
        getServer().getScheduler().runTaskTimer(this, () -> {
            org.bukkit.World realm = zones.world();
            if (realm == null) return;
            int cap = getConfig().getInt("moloch.monster-limit", 30);
            if (cap >= 0 && realm.getSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER) != cap) realm.setSpawnLimit(org.bukkit.entity.SpawnCategory.MONSTER, cap);
        }, 40L, 600L);
        if (zones.world() == null) getLogger().warning("Moloch's realm '" + zones.worldKey + "' isn't loaded. Install the Sacrifice data pack, or set moloch.world in config.yml.");
        getLogger().info("Soulbound ready: captures, sacrifices, Moloch's curse, the Hunger meter and Moloch himself.");
    }

    @Override
    public void onDisable() {
        if (brimstone != null) brimstone.stop();
        if (combatLog != null) combatLog.stop();
        if (giantCreaking != null) giantCreaking.stop();
        if (sacrifices != null) sacrifices.stop();
        if (bloodEmeralds != null) bloodEmeralds.stop();
        if (tumourCrop != null) tumourCrop.stop();
        if (cloudCotton != null) cloudCotton.stop();
        if (growthPotions != null) growthPotions.stop();
        if (bestiary != null) bestiary.stop();
        if (drums != null) drums.stop();
        if (music != null) music.stop();
        if (gate != null) gate.stop();
        if (seal != null) seal.stop();
        if (parasite != null) parasite.stop();
        if (beasts != null) beasts.stop();
        if (kraken != null) kraken.stop();
        if (cute != null) cute.stop();
        if (godGear != null) godGear.stop();
        if (quantum != null) quantum.stop();
        if (godMusic != null) godMusic.stop();
        if (god != null) god.stop();
        if (heavenAir != null) heavenAir.stop();
        if (heaven != null) heaven.stop();
        if (halo != null) halo.stop();
        if (gear != null) gear.stop();
        if (captures != null) captures.stop();
        if (boss != null) boss.stop();
        if (hunger != null) hunger.stop();
        if (curses != null) curses.stop();
        if (store != null) store.save();
    }

    Zones zones() { return zones; }
    HungerMeter hunger() { return hunger; }
    MolochBoss boss() { return boss; }
    HaloItem halo() { return halo; }
    MolochGear gear() { return gear; }
    BloodPriest priest() { return priest; }
    Tartare tartare() { return tartare; }
    TumourPie tumourPie() { return tumourPie; }
    CloudCandy cloudCandy() { return cloudCandy; }
    RaptureCrossbow raptureCrossbow() { return raptureCrossbow; }
    DeeperStill deeperStill() { return deeperStill; }
    WildDrop wildDrop() { return wildDrop; }
    FesteringFlesh festering() { return festering; }
    ThiefKey thiefKey() { return thiefKey; }
    Store store() { return store; }
    GodManager god() { return god; }
    Archangels archangels() { return archangels; }
    Inhuman inhuman() { return inhuman; }
    InhumanHearts inhumanHearts() { return inhumanHearts; }
    InhumanHunger inhumanHunger() { return inhumanHunger; }
    InhumanPoison inhumanPoison() { return inhumanPoison; }
    QuantumStand quantumStand() { return quantumStand; }
    Homunculus homunculus() { return homunculus; }
    AccountReset accountReset() { return accountReset; }
    Sleeper sleeper() { return sleeper; }
    GiantCreaking giantCreaking() { return giantCreaking; }
    SafeZones safeZones() { return safeZones; }
    Broadcasts broadcasts() { return broadcasts; }
    CombatLog combatLog() { return combatLog; }
    LoreFixer lore() { return loreFixer; }
    Grace grace() { return grace; }
    EdenMask eden() { return eden; }
    Zephyrs zephyrs() { return zephyrs; }
    BrimstoneCubes brimstone() { return brimstone; }
    SulfurStrawberry sulfurStrawberry() { return sulfurStrawberry; }
    HatWardrobe wardrobe() { return wardrobe; }
    Peacekeeper peacekeeper() { return peacekeeper; }
    IslandGuard island() { return island; }
    CaptureManager captures() { return captures; }
    SacrificeManager sacrifices() { return sacrifices; }
    LeashItem leash() { return leash; }
    Cute cute() { return cute; }
    Laestrygonians laestrygonians() { return laestrygonians; }
    ChatFix chatFix() { return chatFix; }
    NewPlayerPing newPlayerPing() { return newPlayerPing; }
    KrakenBeard kraken() { return kraken; }
    Parasite parasite() { return parasite; }
    CurseManager curses() { return curses; }
    GodGear godGear() { return godGear; }
    QuantumGear quantum() { return quantum; }
    PotentialBlade potentialBlade() { return potentialBlade; }
    CultistAxe cultistAxe() { return cultistAxe; }
    BloodEmeralds bloodEmeralds() { return bloodEmeralds; }
    TumourCrop tumourCrop() { return tumourCrop; }
    CloudCotton cloudCotton() { return cloudCotton; }
    GrowthPotions growthPotions() { return growthPotions; }
    Bestiary bestiary() { return bestiary; }
    public ScreenCrack cracks() { return cracks; }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("moloch")) {
            String hint = args.length > 0 ? NEW_FORM.get(args[0].toLowerCase(java.util.Locale.ROOT)) : null;
            if (hint != null && sender.hasPermission("soulbound.admin"))
                sender.sendMessage(Component.text("Tip: this lives under " + hint + " now (the old command still works).", NamedTextColor.DARK_GRAY));
            return moloch(sender, args);
        }
        if (command.getName().equalsIgnoreCase("god")) {
            if (sender instanceof Player p && args.length > 0) god.choose(p, args[0]);
            return true;
        }
        return soulbound(sender, args);
    }

    private boolean soulbound(CommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "release" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Only players can release a captive."); return true; }
                Capture cap = captures.heldBy(p);
                if (cap == null) { p.sendMessage(Component.text("You're not holding anyone.", NamedTextColor.GRAY)); return true; }
                if (cap.ritual) { p.sendMessage(Component.text("The ritual has begun. It can't be undone now.", NamedTextColor.RED)); return true; }
                captures.end(cap, CaptureManager.End.LET_GO);
                return true;
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                reload();
                sender.sendMessage(Component.text("Soulbound config reloaded.", NamedTextColor.GREEN));
                return true;
            }
            case "give" -> {
                if (!admin(sender)) return true;
                Player target = args.length > 1 ? getServer().getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (target == null) { sender.sendMessage(Component.text("Who? /soulbound give <player> [amount]", NamedTextColor.GRAY)); return true; }
                int n = 1;
                if (args.length > 2) try { n = Math.max(1, Math.min(64, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) {}
                for (int i = 0; i < n; i++) target.getInventory().addItem(leash.create(1));
                sender.sendMessage(Component.text("Gave " + n + " " + getConfig().getString("leash.name", "Cultist's Shackles") + " to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("/soulbound release  ·  let your captive go", NamedTextColor.GRAY));
                if (sender.hasPermission("soulbound.admin")) {
                    sender.sendMessage(Component.text("/soulbound give <player> [amount]  ·  give capture leashes", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("/soulbound reload  ·  reload config.yml", NamedTextColor.GRAY));
                }
                return true;
            }
        }
    }

    private static final java.util.Map<String, String> NEW_FORM = java.util.Map.ofEntries(
            java.util.Map.entry("awaken", "/sb moloch awaken"), java.util.Map.entry("stop", "/sb moloch stop"),
            java.util.Map.entry("hunger", "/sb moloch hunger"), java.util.Map.entry("bloodmoon", "/sb moloch bloodmoon"),
            java.util.Map.entry("curse", "/sb player <name> curse"), java.util.Map.entry("uncurse", "/sb player <name> uncurse"),
            java.util.Map.entry("inhuman", "/sb player <name> inhuman"), java.util.Map.entry("resetlimits", "/sb player <name> resetlimits"),
            java.util.Map.entry("gear", "/sb give <player> <item>  (or /sb items)"), java.util.Map.entry("halo", "/sb give <player> halo"),
            java.util.Map.entry("priest", "/sb world priest"), java.util.Map.entry("gate", "/sb world gate"), java.util.Map.entry("temple", "/sb world temple"),
            java.util.Map.entry("setarrival", "/sb world setarrival"), java.util.Map.entry("god", "/sb god test"), java.util.Map.entry("heaven", "/sb god heaven"),
            java.util.Map.entry("reload", "/sb reload"));

    boolean legacyMoloch(CommandSender sender, String[] args) { return moloch(sender, args); }

    /** /sb release (and the old /soulbound forms) for players. */
    boolean legacySoulbound(CommandSender sender, String[] args) { return soulbound(sender, args); }

    private boolean moloch(CommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "leave" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                seal.leave(p);
                return true;
            }
            case "awaken" -> {
                if (!admin(sender)) return true;
                if (boss.active()) { sender.sendMessage(Component.text("He's already awake.", NamedTextColor.GRAY)); return true; }
                String no = boss.cantBegin();
                if (no != null) { sender.sendMessage(Component.text(no, NamedTextColor.RED)); return true; }
                boss.begin();
                return true;
            }
            case "stop" -> {
                if (!admin(sender)) return true;
                boolean was = boss.active(), booked = hunger.awakenAt() > 0 || hunger.value() >= 100;
                if (was) boss.abort();
                if (was || booked) {
                    hunger.reset();   // back to sleep: the bar empties (offerings made during the fight still count for next time)
                    if (!was) getServer().broadcast(Component.text("Moloch's awakening was called off.", NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("Stopped. His hunger is back to " + Math.round(hunger.value()) + "%.", NamedTextColor.GREEN));
                } else sender.sendMessage(Component.text("He isn't awake or about to rise. (/moloch hunger <0-100> sets the meter.)", NamedTextColor.GRAY));
                return true;
            }
            case "hunger" -> {
                if (!admin(sender)) return true;
                if (args.length < 2) { sender.sendMessage(Component.text("/moloch hunger <0-100>", NamedTextColor.GRAY)); return true; }
                try { hunger.set(Double.parseDouble(args[1])); } catch (NumberFormatException ex) { sender.sendMessage(Component.text("A number from 0 to 100.", NamedTextColor.RED)); return true; }
                sender.sendMessage(Component.text("Hunger set to " + Math.round(hunger.value()) + ".", NamedTextColor.GREEN));
                return true;
            }
            case "curse", "uncurse" -> {
                if (!admin(sender)) return true;
                Player t = args.length > 1 ? getServer().getPlayerExact(args[1]) : null;
                if (t == null) { sender.sendMessage(Component.text("/moloch " + sub + " <player>" + (sub.equals("curse") ? " [minutes]" : ""), NamedTextColor.GRAY)); return true; }
                if (sub.equals("uncurse")) {
                    curses.lift(t);
                    sacrifices.forgetVictim(t.getUniqueId());   // and they can be offered again straight away
                    sender.sendMessage(Component.text("Lifted " + t.getName() + "'s curse; they can be offered again.", NamedTextColor.GREEN));
                    return true;
                }
                int mins = getConfig().getInt("curse.minutes", 45);
                if (args.length > 2) try { mins = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                curses.curse(t, mins);
                sender.sendMessage(Component.text("Cursed " + t.getName() + " for " + mins + " minutes.", NamedTextColor.GREEN));
                return true;
            }
            case "resetlimits" -> {
                if (!admin(sender)) return true;
                if (args.length > 1) {
                    org.bukkit.OfflinePlayer t = getServer().getOfflinePlayerIfCached(args[1]);
                    if (t == null) { sender.sendMessage(Component.text("No player called " + args[1] + ".", NamedTextColor.RED)); return true; }
                    sacrifices.resetLimits(t.getUniqueId());
                    if (captures != null) captures.clearEscapeProtection(t.getUniqueId());
                    sender.sendMessage(Component.text("Cleared " + t.getName() + "'s escape protection, sacrifice cooldown and daily count.", NamedTextColor.GREEN));
                } else {
                    sacrifices.resetLimits(null);
                    if (captures != null) captures.clearEscapeProtection(null);
                    sender.sendMessage(Component.text("Cleared everyone's escape protection, every sacrifice cooldown and today's limits.", NamedTextColor.GREEN));
                }
                return true;
            }
            case "priest" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) { sender.sendMessage(Component.text("Stand where he should stand.", NamedTextColor.RED)); return true; }
                boolean remove = args.length > 1 && args[1].equalsIgnoreCase("remove");
                sender.sendMessage(Component.text(remove ? priest.removeNear(p) : priest.spawn(p), NamedTextColor.GREEN));
                return true;
            }
            case "inhuman" -> {
                if (!admin(sender)) return true;
                Player t = args.length > 1 ? getServer().getPlayerExact(args[1]) : null;
                if (t == null) { sender.sendMessage(Component.text("/moloch inhuman <player> [off]", NamedTextColor.GRAY)); return true; }
                boolean off = args.length > 2 && args[2].equalsIgnoreCase("off");
                inhuman.set(t, !off);
                sender.sendMessage(Component.text(t.getName() + (off ? " is human again." : " is no longer human."), NamedTextColor.GREEN));
                return true;
            }
            case "temple" -> {
                if (!admin(sender)) return true;
                sender.sendMessage(Component.text(portals.templeWorks(), NamedTextColor.GREEN));
                return true;
            }
            case "gate" -> {
                if (!admin(sender)) return true;
                int x = getConfig().getInt("portals.gate.x", 0), z = getConfig().getInt("portals.gate.z", -120);
                if (args.length > 1 && args[1].equalsIgnoreCase("here")) {
                    if (!(sender instanceof Player p) || !zones.inRealm(p.getLocation())) { sender.sendMessage(Component.text("Stand in Moloch's realm to use /moloch gate here.", NamedTextColor.RED)); return true; }
                    x = p.getLocation().getBlockX(); z = p.getLocation().getBlockZ();
                    getConfig().set("portals.gate.x", x); getConfig().set("portals.gate.z", z); saveConfig();
                }
                sender.sendMessage(Component.text(portals.buildGate(x, z), NamedTextColor.GREEN));
                return true;
            }
            case "gear" -> {
                if (!admin(sender)) return true;
                if (args.length >= 6 && args[1].equalsIgnoreCase("@brew")) {   // test hook: /moloch gear @brew <stage> x y z fills a brewing stand
                    org.bukkit.block.Block b = (sender instanceof Player sp ? sp.getWorld() : getServer().getWorlds().get(0)).getBlockAt(Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
                    sender.sendMessage(Component.text(growthPotions.fillStand(b, Integer.parseInt(args[2])), NamedTextColor.GREEN)); return true;
                }
                Player t = args.length > 1 ? getServer().getPlayerExact(args[1]) : null;
                if (t == null || args.length < 3) { sender.sendMessage(Component.text("/moloch gear <player> <horns|skull|viking|hood|cudgel|chains|cute [catears|bunnyears|flowercrown|bubblegut|brain|chef|wizard|tophat|prospector|lab|labcoat|labpants|labshoes]|parasite|cortex|tartare|set|helmet|chestplate|leggings|boots|vestments [piece]|quantum [piece|tools|sword|pickaxe|axe|shovel|hoe|core|ember|tear]|potential>", NamedTextColor.GRAY)); return true; }
                if (args[2].equalsIgnoreCase("hood") || args[2].equalsIgnoreCase("cudgel")) { sender.sendMessage(Component.text(captures.items.adminGive(t, args[2]), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("chains")) { for (int i = 0; i < 4; i++) t.getInventory().addItem(leash.create(1)); sender.sendMessage(Component.text("Gave " + t.getName() + " 4 " + getConfig().getString("leash.name", "Cultist's Shackles") + ".", NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("cute")) { sender.sendMessage(Component.text(cute.adminGive(t, args.length > 3 ? args[3] : "all"), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("heart")) { t.getInventory().addItem(inhuman.heart()); sender.sendMessage(Component.text("Gave " + t.getName() + " the Heart of Darkness.", NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("tartare")) { sender.sendMessage(Component.text(tartare.adminGive(t, 16), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("cortex")) { sender.sendMessage(Component.text(parasite.adminGiveCrown(t), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("parasite")) { sender.sendMessage(Component.text(parasite.adminGive(t), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("quantum")) { sender.sendMessage(Component.text(quantum.adminGive(t, args.length > 3 ? args[3] : "set"), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("potential")) { sender.sendMessage(Component.text(potentialBlade.adminGive(t), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("growth") || args[2].equalsIgnoreCase("potions")) { sender.sendMessage(Component.text(growthPotions.adminGive(t), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("cotton") || args[2].equalsIgnoreCase("cloudcotton")) { sender.sendMessage(Component.text(cloudCotton.adminGive(t, 16), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("tumours") || args[2].equalsIgnoreCase("tumourseeds")) { sender.sendMessage(Component.text(tumourCrop.adminGive(t, 16), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("bloodemeralds") || args[2].equalsIgnoreCase("blood_emeralds")) { sender.sendMessage(Component.text(bloodEmeralds.adminGive(t, args.length > 3 ? Math.max(1, Math.min(64, Integer.parseInt(args[3].replaceAll("\\D", "0")))) : 64), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("axe") || args[2].equalsIgnoreCase("cultist_axe")) { sender.sendMessage(Component.text(cultistAxe.adminGive(t), NamedTextColor.GREEN)); return true; }
                if (args[2].equalsIgnoreCase("vestments")) { sender.sendMessage(Component.text(godGear.adminGive(t, args.length > 3 ? args[3] : "set"), NamedTextColor.GREEN)); return true; }
                sender.sendMessage(Component.text(gear.adminGive(t, args[2]), NamedTextColor.GREEN));
                return true;
            }
            case "carry" -> {
                if (!admin(sender)) return true;
                java.util.List<String> keys = java.util.List.of("pose", "height", "sideways", "forward", "turn");
                if (args.length < 3 || !keys.contains(args[1].toLowerCase(java.util.Locale.ROOT))) {
                    StringBuilder now = new StringBuilder("/moloch carry <pose|height|sideways|forward|turn> <value>  ·  now:");
                    for (String k : keys) now.append(' ').append(k).append('=').append(getConfig().get("capture.carry." + k, "default"));
                    sender.sendMessage(Component.text(now.toString(), NamedTextColor.GRAY));
                    return true;
                }
                String k = args[1].toLowerCase(java.util.Locale.ROOT);
                if (k.equals("pose")) getConfig().set("capture.carry.pose", args[2].toUpperCase(java.util.Locale.ROOT));
                else try { getConfig().set("capture.carry." + k, Double.parseDouble(args[2])); }
                     catch (NumberFormatException ex) { sender.sendMessage(Component.text("That needs a number.", NamedTextColor.RED)); return true; }
                saveConfig();
                captures.loadSettings();
                sender.sendMessage(Component.text("Carry " + k + " set to " + args[2] + ". Pick someone up again to see it.", NamedTextColor.GREEN));
                return true;
            }
            case "bloodmoon" -> {
                if (!admin(sender)) return true;
                double h = getConfig().getInt("boss.blood-moon-hours", 1);
                if (args.length > 1) { if (args[1].equalsIgnoreCase("off")) h = 0; else try { h = Double.parseDouble(args[1]); } catch (NumberFormatException ignored) {} }
                boss.setBloodMoon(h);
                sender.sendMessage(Component.text(h > 0 ? "Blood moon for " + h + " hours." : "Blood moon ended.", NamedTextColor.GREEN));
                return true;
            }
            case "halo" -> {
                if (!admin(sender)) return true;
                Player t = args.length > 1 ? getServer().getPlayerExact(args[1]) : (sender instanceof Player p ? p : null);
                if (t == null) { sender.sendMessage(Component.text("/moloch halo <player>", NamedTextColor.GRAY)); return true; }
                halo.give(t, null);
                sender.sendMessage(Component.text("Gave " + t.getName() + " a Halo.", NamedTextColor.GREEN));
                return true;
            }
            case "heaven" -> {
                if (!admin(sender)) return true;
                sender.sendMessage(Component.text(heaven.rebuild(), NamedTextColor.GREEN));
                return true;
            }
            case "god" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                sender.sendMessage(Component.text(god.test(p), NamedTextColor.GREEN));
                return true;
            }
            case "setarrival" -> {
                if (!admin(sender)) return true;
                if (!(sender instanceof Player p) || !zones.inRealm(p.getLocation())) { sender.sendMessage(Component.text("Stand where players should arrive, in Moloch's realm, facing the way they should face.", NamedTextColor.RED)); return true; }
                portals.setArrival(p.getLocation());
                sender.sendMessage(Component.text("Portals now lead here.", NamedTextColor.GREEN));
                return true;
            }
            case "reload" -> {
                if (!admin(sender)) return true;
                reload();
                sender.sendMessage(Component.text("Moloch config reloaded.", NamedTextColor.GREEN));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Moloch is " + boss.status() + ".", NamedTextColor.DARK_RED));
                sender.sendMessage(Component.text("Hunger: " + Math.round(hunger.value()) + " / 100", NamedTextColor.RED));
                if (hunger.awakenAt() > 0) sender.sendMessage(Component.text("He rises " + hunger.whenText() + ".", NamedTextColor.RED));
                if (sender instanceof Player p && curses.secondsLeft(p) > 0)
                    sender.sendMessage(Component.text("You're cursed for " + CurseManager.clock(curses.secondsLeft(p)) + " more.", NamedTextColor.DARK_PURPLE));
                if (sender.hasPermission("soulbound.admin"))
                    sender.sendMessage(Component.text("Admin: /moloch awaken | stop | hunger <n> | curse <player> [min] | uncurse <player> | resetlimits [player] | priest [remove] | inhuman <player> [off] | gate [here] | setarrival | reload", NamedTextColor.GRAY));
                return true;
            }
        }
    }

    private void reload() {
        reloadConfig();
        if (broadcasts != null) broadcasts.start();
        zones.load();
        captures.loadSettings();
        curses.loadSettings();
        hunger.loadSettings();
        boss.loadSettings();
        sacrifices.loadSettings();
        portals.loadSettings();
        drums.loadSettings();
        god.loadSettings();
        gear.loadSettings();
        seal.loadSettings();
        parasite.loadSettings();
        beasts.loadSettings();
        leash.load();
        priest.load();
        leash.registerRecipe();
    }

    private boolean admin(CommandSender s) {
        if (s.hasPermission("soulbound.admin")) return true;
        s.sendMessage(Component.text("You can't do that.", NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean admin = sender.hasPermission("soulbound.admin");
        if (command.getName().equalsIgnoreCase("god")) return args.length == 1 ? List.of("challenge", "lost", "forgive", "look") : List.of();
        if (command.getName().equalsIgnoreCase("moloch")) {
            if (args.length == 1) return admin ? List.of("awaken", "stop", "hunger", "curse", "uncurse", "resetlimits", "priest", "inhuman", "gate", "temple", "carry", "setarrival", "god", "heaven", "halo", "gear", "bloodmoon", "leave", "reload") : List.of("leave");
            return null;   // player names
        }
        if (args.length == 1) return admin ? List.of("release", "give", "reload") : List.of("release");
        return List.of();
    }
}
