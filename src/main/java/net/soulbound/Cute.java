package net.soulbound;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cute head pieces anyone can craft. Cat Ears with a bow (they twitch now and then; creepers leave you alone, like
 * they do cats), Bunny Ears (they splay out when you hop; Jump Boost), a Flower Crown (bees never sting you, and
 * petals drift off it).
 */
final class Cute implements Listener {
    enum Kind {
        CAT("Cat Ears", "cat_ears", "cat_ears_twitch", "Creepers leave you alone, like they do cats."),
        BUNNY("Bunny Ears", "bunny_ears", "bunny_ears_hop", "Jump Boost I. Hop hop."),
        CROWN("Flower Crown", "flower_crown", null, "Bees will never sting you."),
        GUT("Bubble Gut", "bubble_gut", null, "Hard to push over. Harder to see your feet.", EquipmentSlot.CHEST, 0),
        BRAIN("Big Brain", "big_brain_f0", null, "Brain power: +50% experience."),
        CHEF("Chef's Hat", "chef_hat", null, "Everything you eat fills you up for longer."),
        WIZARD("Wizard's Hat", "wizard_hat", null, "Enchanting costs one level less."),
        TOPHAT("Top Hat", "top_hat", null, "Villagers give a gentleman their best prices."),
        PROSPECTOR("Prospector's Hat", "prospector_hat", null, "Ores sometimes give a little extra."),
        TOMATO("Tomato Helmet", "tomato_helmet", null, "Get hit and you go splat. Hit a player and they become a Tomato too."),
        BAROQUE("Baroque", "baroque", null, "A bauta and a tricorn, for the masquerade."),
        CROC("Crocodile Mask", "croc_mask", null, "It hasn't decided whether to swallow you."),
        PROPELLER("Propeller Cap", "propeller_cap_f0", null, "Sprint and the propeller spins."),
        BP_HAT("Blood Priest's Hat", "blood_priest_hat", null, "Speed II in Moloch's realm, except while he's being fought."),
        SINNER("Sinner's Halo", "sinners_halo", null, "Too wicked for Heaven, too holy for the furnace. Cosmetic only."),
        BALLOON("Balloon Bonnet", "balloon_bonnet", null, "Proof you were here when it all began."),
        // token-only hats and masks (no recipes): cosmetic, each with a line of nonsense
        DUNCE("Dunce Cap", "dunce_cap", null, "Sit in the corner and think about what you did."),
        FOX("Furry Fox Mask", "furry_mask", null, "OwO. It's not a phase."),
        BOWL("Goldfish Bowl", "goldfish_bowl", null, "He doesn't remember you. He's happy."),
        PIGEON("Pigeon Pal", "pigeon_pal", null, "He lives here now."),
        LAMPSHADE("Party Lampshade", "party_lampshade", null, "Someone had a good night."),
        VISITOR("The Visitor", "the_visitor", null, "Take me to your leader. Or your fridge."),
        BAG("Bag Man", "bag_man", null, "He's smiling. He's always smiling."),
        FROG("Froggy", "froggy", null, "Ribbit. (He means it.)"),
        BULL("Load of Bull", "load_of_bull", null, "Everything he says is a lie. Including this."),
        HEADFIRST("Head First", "head_first", null, "He went in head first. He's not coming out."),
        HYPNO("Hypno Helm", "hypno_helm", null, "You will wear this hat. You will like this hat."),
        SHROOM("Shroom Bloom", "shroom_bloom", null, "It's growing on you."),
        TROLL("Smug Troll", "smug_troll", null, "u mad?"),
        FATSTEVE("Fat Steve Head", "fat_steve_head", null, "Big head. Bigger ideas."),
        VILLAGER("Villager Head", "villager_head", null, "Hrmm. Hrmmm."),
        VINDICATOR("Vindicator Head", "vindicator_head", null, "Here's Johnny."),
        HATCEPTION("Hatception", "hatception", null, "A hat, on a head, on your head."),
        VILLAGERBRINE("Villagerbrine", "villagerbrine", null, "Removed in a future update. He's still here."),
        SPARTAN("Spartan Helm", "spartan_helm", null, "Come and take it."),
        LAURELS("Caesar's Laurels", "caesars_laurels", null, "I came, I saw, I wore a hat."),
        PHARAOH("Pharaoh's Nemes", "pharaohs_nemes", null, "Build a pyramid. Then another."),
        GUESSWHO("Guess Who", "guess_who", null, "Guess who! (It's Steve. It's always Steve.)"),
        FLYLLAGER("Fly-llager", "flyllager", null, "Hrmm. Bzzzz. Hrmm."),
        BALACLAVA("Balaclava", "balaclava", null, "For cold days. And other things."),
        BANDANA("Bandana", "bandana", null, "Stick 'em up."),
        // found, never made: two in every stronghold's chests
        GATEKEEPER("The Gatekeeper's Crown", "gatekeepers_crown", null, "Twelve eyes, and somehow still not watching where you're going."),
        // found, never made: for whoever hurt Jotun most
        JOTUN("Jotun's Crown", "jotuns_crown", null, "Twice your size. Your blocks go down 2x2 (sneak to place one)."),
        // found, never made: worn by the Laestrygonians
        LAESTRYGONIAN("Laestrygonian Helm", "laestrygonian_helm", null, "As strong as netherite, and 10% bigger. Eat Human Flesh in it for Strength III.");
        final String title, model, alt, perk;
        final EquipmentSlot slot;
        final int armor;
        Kind(String title, String model, String alt, String perk) { this(title, model, alt, perk, EquipmentSlot.HEAD, 0); }
        Kind(String title, String model, String alt, String perk, EquipmentSlot slot, int armor) {
            this.title = title; this.model = model; this.alt = alt; this.perk = perk; this.slot = slot; this.armor = armor;
        }
        boolean priest() { return false; }
    }
    private static final TextColor PINK = TextColor.color(0xF48FB1);
    private static Map<Kind, TextColor> colours(Object... kv) {
        Map<Kind, TextColor> m = new java.util.EnumMap<>(Kind.class);
        for (int i = 0; i < kv.length; i += 2) m.put((Kind) kv[i], (TextColor) kv[i + 1]);
        return m;
    }
    private static final Map<Kind, TextColor> NAME_COLOURS = colours(
            Kind.DUNCE, TextColor.color(0xECE6D4), Kind.FOX, TextColor.color(0xE9792B), Kind.BOWL, TextColor.color(0x7FD3EA),
            Kind.PIGEON, TextColor.color(0x9AA3B4), Kind.LAMPSHADE, TextColor.color(0xE36B8A), Kind.VISITOR, TextColor.color(0x9FD39A),
            Kind.BAG, TextColor.color(0xC49A63), Kind.FROG, TextColor.color(0x7CC56A), Kind.BULL, TextColor.color(0xB07A4E),
            Kind.HEADFIRST, TextColor.color(0x3B4FA6), Kind.HYPNO, TextColor.color(0xA45BE6), Kind.SHROOM, TextColor.color(0xC42A2A), Kind.TROLL, TextColor.color(0x7E9A6A), Kind.FATSTEVE, TextColor.color(0x6FB6C8), Kind.VILLAGER, TextColor.color(0x5BA04A), Kind.VINDICATOR, TextColor.color(0x8A9A9C), Kind.HATCEPTION, TextColor.color(0xB3122E), Kind.VILLAGERBRINE, TextColor.color(0xE8F4FF), Kind.SPARTAN, TextColor.color(0xDFA84E), Kind.LAURELS, TextColor.color(0xF2C24A), Kind.PHARAOH, TextColor.color(0x2A5CC0), Kind.GUESSWHO, TextColor.color(0x2FA7A3), Kind.FLYLLAGER, TextColor.color(0x9BD23A), Kind.BALACLAVA, TextColor.color(0x3A3A44), Kind.BANDANA, TextColor.color(0xC4232C), Kind.GATEKEEPER, TextColor.color(0xE2B33C), Kind.JOTUN, TextColor.color(0xFF9A3A), Kind.LAESTRYGONIAN, TextColor.color(0xD9DDE6));

    private final Soulbound plugin;
    private final NamespacedKey key;
    private NamespacedKey tomatoKey;
    private final Map<UUID, Integer> altUntil = new HashMap<>();
    /** Which of the eight propeller positions each wearer's cap is showing. */
    private final Map<UUID, Integer> propFrame = new HashMap<>();
    private BukkitTask task;
    private int tick;

    Cute(Soulbound plugin) { this.plugin = plugin; this.key = new NamespacedKey(plugin, "cute"); this.tomatoKey = new NamespacedKey(plugin, "tomato_infected"); }

    ItemStack create(Kind k) {
        // the Blood Priest's Hat is real armour (a Quantum Helm's worth), so it sits on a netherite helmet: enchantable,
        // repairable, damageable. The equippable has no armour layer, so its own 3D model is what's worn.
        ItemStack it = new ItemStack(k == Kind.BP_HAT ? Material.NETHERITE_HELMET : k == Kind.LAESTRYGONIAN ? Material.NETHERITE_HELMET : Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(k.title, NAME_COLOURS.containsKey(k) ? NAME_COLOURS.get(k) : k == Kind.BAROQUE ? TextColor.color(0xD6AC48) : k == Kind.CROC ? TextColor.color(0x7FA84A) : k == Kind.SINNER ? TextColor.color(0xF2C04A) : k == Kind.BALLOON ? TextColor.color(0x3FA2EC)
                : (k == Kind.BP_HAT || k.priest()) ? TextColor.color(0xB3122E) : PINK, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        String where = switch (k.slot) { case CHEST -> "Worn on the chest"; case LEGS -> "Worn on the legs"; case FEET -> "Worn on the feet"; default -> "Worn on the head"; };
        List<Component> lore = new java.util.ArrayList<>(List.of(Component.text(where, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false),
                Component.text(k.perk, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        if (k == Kind.JOTUN) lore.add(Component.text("Its eyes are still open. Try not to blink first.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        if (k == Kind.LAESTRYGONIAN) lore.add(Component.text("Pried off a man-eating giant. Still smells of supper.", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, true));
        m.lore(lore);
        m.setItemModel(new NamespacedKey("soulbound", k.model));
        if (k != Kind.BP_HAT) m.setMaxStackSize(1);
        EquippableComponent eq = m.getEquippable();
        eq.setSlot(k.slot);
        if (k.priest()) {
            eq.setModel(new NamespacedKey("soulbound", "blood_priest"));
            org.bukkit.inventory.EquipmentSlotGroup g = switch (k.slot) { case CHEST -> org.bukkit.inventory.EquipmentSlotGroup.CHEST;
                case LEGS -> org.bukkit.inventory.EquipmentSlotGroup.LEGS; default -> org.bukkit.inventory.EquipmentSlotGroup.FEET; };
            m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR, new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(plugin, k.model + "_armor"), k.armor, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
        }
        if (k == Kind.BP_HAT) {
            eq.setModel(null);
            // the Quantum Helm's numbers: 4 armour, 4 toughness, 15% knockback resistance, 600 durability
            org.bukkit.inventory.EquipmentSlotGroup g = org.bukkit.inventory.EquipmentSlotGroup.HEAD;
            m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "blood_priest_hat_armor"), 4, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "blood_priest_hat_toughness"), 4, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "blood_priest_hat_kb"), 0.15, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
            if (m instanceof org.bukkit.inventory.meta.Damageable d) d.setMaxDamage(600);
        }
        if (k == Kind.LAESTRYGONIAN) {
            // real armour, a netherite helmet's worth, on a netherite helmet: enchantable, repairable, wears down, survives lava. Its own 3D model is what's worn.
            eq.setModel(null);
            org.bukkit.inventory.EquipmentSlotGroup g = org.bukkit.inventory.EquipmentSlotGroup.HEAD;
            m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "laestrygonian_helm_armor"), 3, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.ARMOR_TOUGHNESS, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "laestrygonian_helm_toughness"), 3, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "laestrygonian_helm_kb"), 0.1, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, g));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.SCALE, new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin, "laestrygonian_helm_scale"),
                    plugin.getConfig().getDouble("laestrygonians.helm-scale", 0.1), org.bukkit.attribute.AttributeModifier.Operation.ADD_SCALAR, g));   // a touch of the giant: 10% bigger
        }
        if (k == Kind.JOTUN && jotunScale() > 1.0001) {
            // a giant's body, not just a giant's look: jumps, steps, falls and building reach scale with you (attack reach and speed don't, for fair fights)
            double f = jotunScale(), x = f - 1.0;
            var head = org.bukkit.inventory.EquipmentSlotGroup.HEAD;
            var add = org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER;
            m.addAttributeModifier(org.bukkit.attribute.Attribute.SCALE, new org.bukkit.attribute.AttributeModifier(jotunScaleKey(), x, add, head));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.JUMP_STRENGTH, new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(plugin, "jotun_crown_jump"), plugin.getConfig().getDouble("jotun-crown.jump-bonus", 0.2) * x, add, head));     // 0.42 -> 0.62: twice as high
            m.addAttributeModifier(org.bukkit.attribute.Attribute.STEP_HEIGHT, new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(plugin, "jotun_crown_step"), 0.6 * x, add, head));                                                          // walk straight up a block
            m.addAttributeModifier(org.bukkit.attribute.Attribute.SAFE_FALL_DISTANCE, new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(plugin, "jotun_crown_fall"), 3.0 * x, add, head));                                                          // twice the drop before it hurts
            m.addAttributeModifier(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE, new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(plugin, "jotun_crown_reach"), plugin.getConfig().getDouble("jotun-crown.block-reach-bonus", 4.5) * x, add, head));   // build at a giant's arm's length (hitting people stays normal)
        }
        if (k == Kind.GUT) {
            eq.setModel(new NamespacedKey("soulbound", "bubble_gut"));
            m.addAttributeModifier(org.bukkit.attribute.Attribute.KNOCKBACK_RESISTANCE, new org.bukkit.attribute.AttributeModifier(
                    new NamespacedKey(plugin, "bubble_gut_kb"), 0.5, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.CHEST));
        }
        eq.setEquipSound(Sound.INTENTIONALLY_EMPTY);
        eq.setSwappable(true);
        eq.setDispensable(true);
        m.setEquippable(eq);
        m.getPersistentDataContainer().set(key, PersistentDataType.STRING, k.name());
        it.setItemMeta(m);
        return it;
    }

    /** Has the 10% size boost (helms dropped before it don't). */
    private boolean laestrygonianUpToDate(ItemStack it) {
        var mods = it.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.SCALE);
        if (mods == null) return false;
        for (var am : mods) if (am.getKey().getKey().equals("laestrygonian_helm_scale")) return true;
        return false;
    }

    Kind kindOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (s == null) return null;
        try { return Kind.valueOf(s); } catch (IllegalArgumentException e) { return null; }
    }

    void registerRecipes() {
        add(Kind.CAT, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.PINK_DYE, Material.STRING);
        add(Kind.BUNNY, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.PINK_DYE, Material.RABBIT_HIDE);
        add(Kind.CROWN, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.ALLIUM, Material.VINE);
        add(Kind.GUT, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.CAKE);
        add(Kind.BRAIN, Material.PINK_WOOL, Material.PINK_WOOL, Material.BOOK, Material.LAPIS_LAZULI);
        add(Kind.CHEF, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.WHITE_WOOL, Material.BOWL);
        add(Kind.WIZARD, Material.BLUE_WOOL, Material.BLUE_WOOL, Material.BLUE_WOOL, Material.GLOWSTONE_DUST);
        add(Kind.TOPHAT, Material.BLACK_WOOL, Material.BLACK_WOOL, Material.BLACK_WOOL, Material.RED_DYE, Material.EMERALD);
        add(Kind.PROSPECTOR, Material.YELLOW_WOOL, Material.YELLOW_WOOL, Material.YELLOW_WOOL, Material.BLACK_DYE, Material.DIAMOND);
    }

    private void add(Kind k, Material... in) {
        NamespacedKey rk = new NamespacedKey(plugin, k.model);
        Bukkit.removeRecipe(rk);
        ShapelessRecipe r = new ShapelessRecipe(rk, create(k));
        for (Material mat : in) r.addIngredient(new RecipeChoice.MaterialChoice(mat));
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(rk);
    }

    String adminGive(Player t, String what) {
        Kind k = switch (what.toLowerCase(Locale.ROOT)) {
            case "catears", "cat" -> Kind.CAT;
            case "bunnyears", "bunny" -> Kind.BUNNY;
            case "flowercrown", "crown" -> Kind.CROWN;
            case "bubblegut", "gut" -> Kind.GUT;
            case "bigbrain", "brain" -> Kind.BRAIN;
            case "chefhat", "chef" -> Kind.CHEF;
            case "wizardhat", "wizard" -> Kind.WIZARD;
            case "tophat", "top" -> Kind.TOPHAT;
            case "prospectorhat", "prospector", "minerhat", "miner" -> Kind.PROSPECTOR;
            case "tomatohelmet", "tomato" -> Kind.TOMATO;
            case "baroque", "bauta", "tricorn", "masquerade" -> Kind.BAROQUE;
            case "crocodilemask", "crocodile", "croc", "crocmask" -> Kind.CROC;
            case "propellercap", "propeller", "beanie", "propellerhat" -> Kind.PROPELLER;
            case "priesthat", "bloodpriesthat" -> Kind.BP_HAT;
            case "sinnershalo", "sinner", "sinnerhalo", "brokenhalo" -> Kind.SINNER;
            case "balloonbonnet", "balloon", "balloons" -> Kind.BALLOON;
            case "duncecap", "dunce" -> Kind.DUNCE;
            case "furrymask", "furry", "fox", "foxmask" -> Kind.FOX;
            case "goldfishbowl", "fishbowl", "bowl", "goldfish" -> Kind.BOWL;
            case "pigeonpal", "pigeon" -> Kind.PIGEON;
            case "partylampshade", "lampshade", "lamp" -> Kind.LAMPSHADE;
            case "thevisitor", "visitor", "alien" -> Kind.VISITOR;
            case "bagman", "bag", "paperbag" -> Kind.BAG;
            case "froggy", "frog" -> Kind.FROG;
            case "loadofbull", "bull" -> Kind.BULL;
            case "headfirst", "legs", "stuck" -> Kind.HEADFIRST;
            case "hypnohelm", "hypno", "mindcontrol" -> Kind.HYPNO;
            case "shroombloom", "shroom", "fungus", "mushroom" -> Kind.SHROOM;
            case "smugtroll", "troll" -> Kind.TROLL;
            case "fatstevehead", "fatsteve", "steve", "bighead" -> Kind.FATSTEVE;
            case "villagerhead", "villager", "longhead" -> Kind.VILLAGER;
            case "vindicatorhead", "vindicator", "johnny" -> Kind.VINDICATOR;
            case "hatception", "priesthead", "bloodpriesthead" -> Kind.HATCEPTION;
            case "villagerbrine", "brine", "zombievillager" -> Kind.VILLAGERBRINE;
            case "spartanhelm", "spartan", "helm", "corinthian" -> Kind.SPARTAN;
            case "caesarslaurels", "laurels", "laurel", "caesar", "wreath" -> Kind.LAURELS;
            case "pharaohsnemes", "pharaoh", "nemes" -> Kind.PHARAOH;
            case "guesswho", "peekaboo", "ministeve" -> Kind.GUESSWHO;
            case "flyllager", "fly", "villagerfly" -> Kind.FLYLLAGER;
            case "balaclava", "skimask" -> Kind.BALACLAVA;
            case "bandana", "outlaw" -> Kind.BANDANA;
            case "gatekeeperscrown", "gatekeeper", "gatekeepercrown", "portalcrown" -> Kind.GATEKEEPER;
            case "jotunscrown", "jotun", "jotuncrown" -> Kind.JOTUN;
            case "laestrygonianhelm", "laestrygonian", "silverspartan" -> Kind.LAESTRYGONIAN;
            default -> null;
        };
        if (k == null) {
            for (Kind each : Kind.values()) t.getInventory().addItem(create(each));
            return "Gave " + t.getName() + " all the cosmetics.";
        }
        t.getInventory().addItem(create(k)).values().forEach(i -> t.getWorld().dropItem(t.getLocation(), i));
        return "Gave " + t.getName() + " " + k.title + ".";
    }

    void start() { task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 2L); }
    void stop() { if (task != null) task.cancel(); }

    private void tick() {
        tick += 2;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ItemStack h = p.getInventory().getHelmet();
            Kind k = kindOf(h);
            if (k == Kind.GUT) k = null;
            if (k == null) { altUntil.remove(p.getUniqueId()); continue; }
            UUID id = p.getUniqueId();
            if (k == Kind.BP_HAT && h.getType() == Material.ECHO_SHARD) {   // an old cosmetic hat: swap it for the real one
                h = create(Kind.BP_HAT); p.getInventory().setHelmet(h);
            }
            if (k == Kind.JOTUN && tick % 20 == 0 && !jotunUpToDate(h)) { h = create(Kind.JOTUN); p.getInventory().setHelmet(h); }   // crowns from before it made you big
            if (k == Kind.LAESTRYGONIAN && tick % 20 == 0 && (h.getType() != Material.NETHERITE_HELMET || !laestrygonianUpToDate(h))) {   // older helms: remade, keeping their enchantments
                ItemStack fresh = create(Kind.LAESTRYGONIAN);
                h.getEnchantments().forEach((en, lvl) -> fresh.addUnsafeEnchantment(en, lvl));
                h = fresh; p.getInventory().setHelmet(h);
            }
            boolean alt = altUntil.getOrDefault(id, 0) > tick;
            switch (k) {
                case CAT -> { if (!alt && r.nextInt(100) == 0) { altUntil.put(id, tick + 6); alt = true; } }   // a twitch every few seconds
                case BUNNY -> {
                    if (!p.isOnGround() && p.getVelocity().getY() > 0.25) { altUntil.put(id, tick + 8); alt = true; }   // a hop
                    if (tick % 20 == 0) p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 60, 0, true, false, true));
                }
                case BP_HAT -> {
                    if (tick % 20 == 0 && plugin.zones() != null && plugin.zones().inRealm(p.getLocation())
                            && (plugin.boss() == null || !plugin.boss().active()))
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 1, true, false, true));
                }
                case CROWN -> { if (r.nextInt(25) == 0) p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, p.getEyeLocation().add(0, 0.45, 0), 1, 0.25, 0.05, 0.25, 0); }
            }
            if (k == Kind.PROPELLER && (p.isSprinting() || p.isGliding())) propFrame.merge(id, 3, (a, b) -> (a + b) % 8);   // whirr
            String want = k == Kind.BRAIN ? "big_brain_f" + ((tick / 4) % 4)      // it throbs
                    : k == Kind.PROPELLER ? "propeller_cap_f" + propFrame.getOrDefault(id, 0)
                    : alt && k.alt != null ? k.alt : k.model;
            ItemMeta m = h.getItemMeta();
            NamespacedKey wk = new NamespacedKey("soulbound", want);
            if (!wk.equals(m.getItemModel())) { m.setItemModel(wk); h.setItemMeta(m); p.getInventory().setHelmet(h); }
        }
    }

    // ---------------------------------------------------------------- Jotun's Crown: twice your size, and you build big

    private double jotunScale() { return Math.max(1.0, plugin.getConfig().getDouble("jotun-crown.scale", 2.0)); }
    private NamespacedKey jotunScaleKey() { return new NamespacedKey(plugin, "jotun_crown_scale"); }

    /** Does this crown carry the current size? (Ones handed out before, or after a config change, get remade.) */
    private boolean jotunUpToDate(ItemStack h) {
        var jm = h.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        if (jotunScale() > 1.0001 && (jm == null || jm.isEmpty())) return false;
        var mods = h.getItemMeta().getAttributeModifiers(org.bukkit.attribute.Attribute.SCALE);
        double want = jotunScale() - 1.0;
        if (mods == null || mods.isEmpty()) return want < 0.0001;
        for (var mod : mods) if (mod.getKey().equals(jotunScaleKey())) return Math.abs(mod.getAmount() - want) < 0.0001;
        return false;
    }

    private final java.util.Set<UUID> bigPlacing = new java.util.HashSet<>();

    /**
     * Wearing Jotun's Crown, a full block goes down as a 2x2 slab of four (one block high), filling the 2x2 square of a
     * fixed grid that the block lands in, so giant blocks always line up when stacked or set side by side. Every extra block costs one from the stack in your hand (creative: free), it only fills air, water or
     * grass, and each extra block goes through the normal place checks, so claims, protections and logging all apply.
     * Sneak to place just the one.
     */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onJotunPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (bigPlacing.contains(p.getUniqueId()) || p.isSneaking() || !plugin.getConfig().getBoolean("jotun-crown.build-2x2", true)) return;
        if (kindOf(p.getInventory().getHelmet()) != Kind.JOTUN) return;
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL && p.getGameMode() != org.bukkit.GameMode.CREATIVE) return;
        org.bukkit.block.Block b = e.getBlockPlaced();
        Material type = b.getType();
        if (!type.isSolid() || !type.isOccluding() || b.getState() instanceof org.bukkit.block.TileState) return;
        org.bukkit.block.data.BlockData data = b.getBlockData().clone();
        EquipmentSlot hand = e.getHand();
        org.bukkit.block.Block against = e.getBlockAgainst();
        // a giant's blocks sit on a 2x2 grid, so they always line up: stack them, or put them side by side, and they meet flush
        int cx = Math.floorDiv(b.getX(), 2) * 2, cz = Math.floorDiv(b.getZ(), 2) * 2;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || b.getType() != type) return;
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                ItemStack held = hand == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
                boolean free = p.getGameMode() == org.bukkit.GameMode.CREATIVE;
                if (!free && (held == null || held.getType() != type || held.getAmount() < 1)) return;   // out of blocks
                org.bukkit.block.Block at = b.getWorld().getBlockAt(cx + o[0], b.getY(), cz + o[1]);
                if (at.equals(b)) continue;
                if (!at.isReplaceable() || !at.getWorld().getWorldBorder().isInside(at.getLocation())) continue;
                if (!at.getWorld().getNearbyEntities(at.getBoundingBox().expand(-0.01), en -> en instanceof org.bukkit.entity.LivingEntity).isEmpty()) continue;   // not inside anyone
                org.bukkit.block.BlockState was = at.getState();
                at.setBlockData(data, true);
                bigPlacing.add(p.getUniqueId());
                org.bukkit.event.block.BlockPlaceEvent ev;
                try {
                    ev = new org.bukkit.event.block.BlockPlaceEvent(at, was, against, held == null ? new ItemStack(type) : held, p, true, hand);
                    Bukkit.getPluginManager().callEvent(ev);
                } finally { bigPlacing.remove(p.getUniqueId()); }
                if (ev.isCancelled() || !ev.canBuild()) { was.update(true, false); continue; }
                if (!free) {
                    held.setAmount(held.getAmount() - 1);
                    if (hand == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(held.getAmount() > 0 ? held : null);
                    else p.getInventory().setItemInMainHand(held.getAmount() > 0 ? held : null);
                }
            }
        });
    }

    /** Tomatoes spread: a player wearing the Tomato Helmet who hits another player turns them into a Tomato too. They get
     *  their own helmet, put straight on their head, and the "Tomato Monster" advancement (baffoon:tomato_monster). Once per player, ever, so two
     *  Tomatoes can't farm helmets off each other. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onTomatoHit(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player a) || !(e.getEntity() instanceof Player v) || a.equals(v)) return;
        if (kindOf(a.getInventory().getHelmet()) != Kind.TOMATO) return;
        if (v.getPersistentDataContainer().has(tomatoKey, PersistentDataType.BYTE)) return;
        v.getPersistentDataContainer().set(tomatoKey, PersistentDataType.BYTE, (byte) 1);
        // straight onto their head; whatever they were wearing goes back into their inventory (or at their feet if it's full)
        ItemStack was = v.getInventory().getHelmet();
        v.getInventory().setHelmet(create(Kind.TOMATO));
        if (was != null && !was.getType().isAir())
            for (ItemStack left : v.getInventory().addItem(was).values()) v.getWorld().dropItemNaturally(v.getLocation(), left);
        org.bukkit.advancement.Advancement adv = Bukkit.getAdvancement(new NamespacedKey("baffoon", "tomato_monster"));
        if (adv != null) { var pr = v.getAdvancementProgress(adv); for (String c : pr.getRemainingCriteria()) pr.awardCriteria(c); }
        v.sendActionBar(Component.text(a.getName() + " got you. You're a Tomato now.", TextColor.color(0xD62E1E)));
        a.sendActionBar(Component.text(v.getName() + " is a Tomato now.", TextColor.color(0xD62E1E)));
        v.getWorld().playSound(v.getLocation(), Sound.ENTITY_SLIME_SQUISH, 1f, 0.8f);
    }

    /** The Tomato Helmet: take a hit and tomato goes everywhere. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onSplat(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || kindOf(p.getInventory().getHelmet()) != Kind.TOMATO) return;
        org.bukkit.Location at = p.getEyeLocation().add(0, 0.3, 0);
        p.getWorld().spawnParticle(Particle.ITEM, at, 18, 0.35, 0.3, 0.35, 0.12, new ItemStack(Material.BEETROOT));
        p.getWorld().spawnParticle(Particle.DUST, at, 14, 0.4, 0.3, 0.4, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(0xD62E1E), 1.4f));
        p.getWorld().playSound(at, Sound.BLOCK_SLIME_BLOCK_BREAK, 0.8f, 1.3f);
        p.getWorld().playSound(at, Sound.ENTITY_SLIME_SQUISH, 0.6f, 1.5f);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (!(e.getTarget() instanceof Player p)) return;
        Kind k = kindOf(p.getInventory().getHelmet());
        if (k == Kind.CAT && e.getEntity() instanceof Creeper) e.setCancelled(true);
        if (k == Kind.CROWN && e.getEntity() instanceof Bee) e.setCancelled(true);
    }

    /** Chef's Hat: food keeps you full longer (a little extra saturation from every meal). */
    @EventHandler(ignoreCancelled = true)
    public void onEat(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (kindOf(p.getInventory().getHelmet()) != Kind.CHEF || !e.getItem().getType().isEdible()) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + 3f)));
    }

    /** Wizard's Hat: a level back after every enchantment. */
    @EventHandler(ignoreCancelled = true)
    public void onEnchant(org.bukkit.event.enchantment.EnchantItemEvent e) {
        Player p = e.getEnchanter();
        if (kindOf(p.getInventory().getHelmet()) != Kind.WIZARD) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.giveExpLevels(1);
            p.getWorld().spawnParticle(Particle.ENCHANT, p.getEyeLocation().add(0, 0.6, 0), 30, 0.4, 0.4, 0.4, 0.5);
        });
    }

    /** Top Hat: talk to a villager and they treat you like the Hero of the Village. */
    @EventHandler(ignoreCancelled = true)
    public void onVillager(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof org.bukkit.entity.AbstractVillager)) return;
        Player p = e.getPlayer();
        if (kindOf(p.getInventory().getHelmet()) != Kind.TOPHAT) return;
        PotionEffect cur = p.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
        if (cur == null || cur.getAmplifier() < 1)
            p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 200, 1, true, false, false));
    }

    /** Prospector's Hat: a 15% chance of a doubled haul from ores. */
    @EventHandler(ignoreCancelled = true)
    public void onOre(org.bukkit.event.block.BlockDropItemEvent e) {
        Player p = e.getPlayer();
        if (kindOf(p.getInventory().getHelmet()) != Kind.PROSPECTOR) return;
        String t = e.getBlockState().getType().name();
        if (!t.endsWith("_ORE") && !t.equals("ANCIENT_DEBRIS")) return;
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= 0.15) return;
        var loc = e.getBlock().getLocation().add(0.5, 0.5, 0.5);
        for (org.bukkit.entity.Item it : e.getItems()) loc.getWorld().dropItemNaturally(loc, it.getItemStack().clone());
        if (!e.getItems().isEmpty()) loc.getWorld().spawnParticle(Particle.WAX_OFF, loc, 12, 0.3, 0.3, 0.3, 0.5);
    }

    /** Big Brain: more experience from every orb. */
    @EventHandler
    public void onXp(org.bukkit.event.player.PlayerExpChangeEvent e) {
        if (e.getAmount() > 0 && kindOf(e.getPlayer().getInventory().getHelmet()) == Kind.BRAIN)
            e.setAmount((int) Math.round(e.getAmount() * 1.5));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        for (Kind k : Kind.values()) e.getPlayer().discoverRecipe(new NamespacedKey(plugin, k.model));
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) openingGift(p); }, 40L);   // after any starter kit lands
    }

    /** Grand opening week: everyone who joins gets the Balloon Bonnet once, on their head if it's free. */
    private void openingGift(Player p) {
        var c = plugin.getConfig();
        if (!c.getBoolean("opening-gift.enabled", true)) return;
        java.time.LocalDate start;
        try { start = java.time.LocalDate.parse(c.getString("opening-gift.start", "2026-11-06")); }
        catch (java.time.format.DateTimeParseException ex) { return; }
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of(c.getString("opening-gift.timezone", "Europe/London")));
        if (today.isBefore(start) || !today.isBefore(start.plusDays(Math.max(1, c.getInt("opening-gift.days", 7))))) return;
        NamespacedKey got = new NamespacedKey(plugin, "opening_gift");
        if (p.getPersistentDataContainer().has(got, PersistentDataType.BYTE)) return;
        p.getPersistentDataContainer().set(got, PersistentDataType.BYTE, (byte) 1);
        ItemStack hat = create(Kind.BALLOON);
        org.bukkit.inventory.PlayerInventory inv = p.getInventory();
        boolean worn = inv.getHelmet() == null || inv.getHelmet().getType().isAir();
        if (worn) inv.setHelmet(hat);
        else inv.addItem(hat).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        p.sendMessage(Component.text("Welcome to the grand opening! ", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text(worn ? "Your Balloon Bonnet is on your head. " : "Your Balloon Bonnet is in your inventory. ", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false))
                .append(Component.text("Proof you were here when it all began.", NamedTextColor.GRAY, TextDecoration.ITALIC).decoration(TextDecoration.BOLD, false)));
        p.playSound(p.getLocation(), "minecraft:entity.player.levelup", 0.7f, 1.4f);
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        for (ItemStack it : e.getInventory().getMatrix()) if (kindOf(it) != null) { e.getInventory().setResult(null); return; }
    }
}
