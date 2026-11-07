package dev.troll;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** All the actual trolls. The victim is never told anything: every message goes to whoever ran the troll. */
public final class TrollManager {

    private final TrollPlugin plugin;
    private final PackService pack;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> scared = new HashSet<>();
    private final Map<UUID, BukkitTask> ascending = new HashMap<>();

    private final NamespacedKey scareItemKey;
    private final NamespacedKey savedHelmetKey;
    private final NamespacedKey freezeSpeedKey;
    private final NamespacedKey freezeJumpKey;
    private final NamespacedKey reachEntityKey;
    private final NamespacedKey reachBlockKey;
    private final NamespacedKey overlayKey;

    public TrollManager(TrollPlugin plugin, PackService pack) {
        this.plugin = plugin;
        this.pack = pack;
        this.scareItemKey = new NamespacedKey(plugin, "scare_item");
        this.savedHelmetKey = new NamespacedKey(plugin, "saved_helmet");
        this.freezeSpeedKey = new NamespacedKey(plugin, "freeze_speed");
        this.freezeJumpKey = new NamespacedKey(plugin, "freeze_jump");
        this.reachEntityKey = new NamespacedKey(plugin, "reach_entity");
        this.reachBlockKey = new NamespacedKey(plugin, "reach_block");
        this.overlayKey = Objects.requireNonNull(NamespacedKey.fromString("troll:misc/jumpscare"));
    }

    // ------------------------------------------------------------------ helpers

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    public void send(CommandSender to, String key, String... pairs) {
        String raw = cfg().getString("messages.prefix", "") + cfg().getString("messages." + key, "<red>Missing message: " + key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = Pattern.compile(Pattern.quote(pairs[i]), Pattern.CASE_INSENSITIVE)
                    .matcher(raw).replaceAll(Matcher.quoteReplacement(pairs[i + 1]));
        }
        to.sendMessage(mm.deserialize(raw));
    }

    private void play(Player p, String soundKey, double volume, double pitch) {
        p.playSound(Sound.sound(Key.key(soundKey), Sound.Source.MASTER, (float) volume, (float) pitch), Sound.Emitter.self());
    }

    private AttributeModifier find(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier m : inst.getModifiers()) {
            if (key.equals(m.getKey())) return m;
        }
        return null;
    }

    private void addModifier(Player p, Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier old = find(inst, key);
        if (old != null) inst.removeModifier(old);
        inst.addModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    private void removeModifier(Player p, Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier m = find(inst, key);
        if (m != null) inst.removeModifier(m);
    }

    // ------------------------------------------------------------------ entry point

    public void run(CommandSender by, Player target, Troll troll) {
        if (target.hasPermission("troll.immune")) {
            send(by, "immune", "%player%", target.getName());
            return;
        }
        switch (troll) {
            case JUMPSCARE -> jumpscare(by, target);
            case DISCONNECT -> disconnect(by, target);
            case RESOURCE -> resource(by, target);
            case REACH -> reach(by, target);
            case ASCEND -> ascend(by, target);
        }
    }

    public void reset(CommandSender by, Player target) {
        unfreeze(target);
        removeModifier(target, Attribute.ENTITY_INTERACTION_RANGE, reachEntityKey);
        removeModifier(target, Attribute.BLOCK_INTERACTION_RANGE, reachBlockKey);
        stopAscend(target, false);
        scared.remove(target.getUniqueId());
        restoreHelmet(target);
        send(by, "reset-done", "%player%", target.getName());
    }

    // ------------------------------------------------------------------ jumpscare

    private void jumpscare(CommandSender by, Player target) {
        UUID id = target.getUniqueId();
        if (scared.contains(id)) {
            send(by, "already-scared", "%player%", target.getName());
            return;
        }
        if (!pack.isLoaded(target)) send(by, "warn-no-pack", "%player%", target.getName());

        scared.add(id);
        saveHelmet(target);
        target.getInventory().setHelmet(buildScareItem());

        double volume = cfg().getDouble("jumpscare.volume", 1.0);
        double pitch = cfg().getDouble("jumpscare.pitch", 1.0);
        int layers = Math.max(1, cfg().getInt("jumpscare.layers", 1));
        for (int i = 0; i < layers; i++) play(target, "troll:jumpscare", volume, pitch);

        long ticks = Math.max(10, cfg().getLong("jumpscare.duration-ticks", 60));
        Bukkit.getScheduler().runTaskLater(plugin, () -> endJumpscare(id), ticks);
        send(by, "ran-jumpscare", "%player%", target.getName());
    }

    private void endJumpscare(UUID id) {
        scared.remove(id);
        Player p = Bukkit.getPlayer(id);
        if (p != null) restoreHelmet(p);
    }

    /** An item that shows the jumpscare texture as a full screen overlay while it is on the head (like a pumpkin). */
    private ItemStack buildScareItem() {
        Material material = Material.matchMaterial(cfg().getString("jumpscare.item", "PAPER"));
        if (material == null) material = Material.PAPER;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        EquippableComponent equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HEAD);
        equippable.setCameraOverlay(overlayKey);
        meta.setEquippable(equippable);
        meta.getPersistentDataContainer().set(scareItemKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isScareItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(scareItemKey, PersistentDataType.BYTE);
    }

    public boolean isScared(UUID id) {
        return scared.contains(id);
    }

    private void saveHelmet(Player p) {
        ItemStack helmet = p.getInventory().getHelmet();
        byte[] bytes = (helmet == null || helmet.getType().isAir()) ? new byte[0] : helmet.serializeAsBytes();
        // Stored on the player so a crash mid-scare can't lose their real helmet.
        p.getPersistentDataContainer().set(savedHelmetKey, PersistentDataType.BYTE_ARRAY, bytes);
    }

    /** Puts the real helmet back (and removes the overlay item). Safe to call any time. */
    public void restoreHelmet(Player p) {
        PersistentDataContainer pdc = p.getPersistentDataContainer();
        byte[] bytes = pdc.get(savedHelmetKey, PersistentDataType.BYTE_ARRAY);
        boolean wearingScare = isScareItem(p.getInventory().getHelmet());
        if (wearingScare) {
            p.getInventory().setHelmet((bytes == null || bytes.length == 0) ? null : ItemStack.deserializeBytes(bytes));
        }
        if (bytes != null) pdc.remove(savedHelmetKey);
    }

    // ------------------------------------------------------------------ disconnect (fake keyboard disconnect)

    private void disconnect(CommandSender by, Player target) {
        if (frozen.contains(target.getUniqueId())) {
            unfreeze(target);
            send(by, "ran-disconnect-off", "%player%", target.getName());
            return;
        }
        frozen.add(target.getUniqueId());
        target.leaveVehicle();
        addModifier(target, Attribute.MOVEMENT_SPEED, freezeSpeedKey, -1.0, AttributeModifier.Operation.ADD_SCALAR);
        addModifier(target, Attribute.JUMP_STRENGTH, freezeJumpKey, -1.0, AttributeModifier.Operation.ADD_SCALAR);
        play(target, "troll:disconnect", cfg().getDouble("disconnect.volume", 1.0), 1.0);
        send(by, "ran-disconnect-on", "%player%", target.getName());
    }

    public void unfreeze(Player p) {
        frozen.remove(p.getUniqueId());
        removeModifier(p, Attribute.MOVEMENT_SPEED, freezeSpeedKey);
        removeModifier(p, Attribute.JUMP_STRENGTH, freezeJumpKey);
    }

    public boolean isFrozen(UUID id) {
        return frozen.contains(id);
    }

    // ------------------------------------------------------------------ resource pack reload

    private void resource(CommandSender by, Player target) {
        if (!pack.ready()) {
            send(by, "pack-not-ready");
            return;
        }
        int steps = Math.max(1, cfg().getInt("resource.times", 5)) * 2;
        long gap = Math.max(10L, cfg().getLong("resource.interval-ticks", 50L));
        boolean force = cfg().getBoolean("resource.force", true);
        UUID id = target.getUniqueId();

        new BukkitRunnable() {
            int step = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || step >= steps) {
                    cancel();
                    return;
                }
                // remove, then add again: every add makes the client download/apply and reload everything
                if (step % 2 == 0) {
                    pack.remove(p);
                } else {
                    pack.send(p, force);
                }
                step++;
            }
        }.runTaskTimer(plugin, 0L, gap);

        send(by, "ran-resource", "%player%", target.getName());
    }

    // ------------------------------------------------------------------ reach

    private void reach(CommandSender by, Player target) {
        AttributeInstance entity = target.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        boolean on = entity != null && find(entity, reachEntityKey) != null;

        if (on) {
            removeModifier(target, Attribute.ENTITY_INTERACTION_RANGE, reachEntityKey);
            removeModifier(target, Attribute.BLOCK_INTERACTION_RANGE, reachBlockKey);
            send(by, "ran-reach-off", "%player%", target.getName());
            return;
        }

        applyRange(target, Attribute.ENTITY_INTERACTION_RANGE, reachEntityKey, cfg().getDouble("reach.entity-range", 1.0));
        double block = cfg().getDouble("reach.block-range", 1.0);
        if (block >= 0) applyRange(target, Attribute.BLOCK_INTERACTION_RANGE, reachBlockKey, block);
        send(by, "ran-reach-on", "%player%", target.getName());
    }

    private void applyRange(Player p, Attribute attribute, NamespacedKey key, double wanted) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        addModifier(p, attribute, key, wanted - inst.getBaseValue(), AttributeModifier.Operation.ADD_NUMBER);
    }

    // ------------------------------------------------------------------ ascend

    private void ascend(CommandSender by, Player target) {
        UUID id = target.getUniqueId();
        if (ascending.containsKey(id)) {
            send(by, "already-ascending", "%player%", target.getName());
            return;
        }
        target.leaveVehicle();

        World world = target.getWorld();
        double ceiling = world.getMaxHeight() - Math.max(0.0, cfg().getDouble("ascend.ceiling-margin", 2.0));
        double climb = Math.max(1.0, ceiling - target.getLocation().getY());

        // Levitation (not raw velocity) so vanilla's anti-fly kick never triggers.
        double seconds = cfg().getDouble("ascend.duration-seconds", 9.4);
        int amplifier = seconds > 0
                ? (int) Math.ceil(climb / seconds) - 1
                : cfg().getInt("ascend.amplifier", 15);
        int level = Math.max(0, Math.min(255, amplifier));
        int maxTicks = Math.max(40, cfg().getInt("ascend.max-seconds", 40) * 20);

        target.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, maxTicks, level, false, false, false));
        play(target, "troll:ascending", cfg().getDouble("ascend.volume", 1.0), 1.0);

        BukkitTask task = new BukkitRunnable() {
            double lastY = target.getLocation().getY();
            int stuckTicks = 0;
            int age = 0;

            @Override
            public void run() {
                Player p = Bukkit.getPlayer(id);
                if (p == null || !p.isOnline() || p.isDead()) {
                    ascending.remove(id);
                    cancel();
                    return;
                }
                double y = p.getLocation().getY();
                age += 4;

                if (y - lastY <= 0.05) stuckTicks += 4; else stuckTicks = 0;
                lastY = y;

                boolean top = y >= ceiling;
                boolean blocked = stuckTicks >= 60;   // a ceiling (nether roof, a building...) is in the way
                if (top || blocked || age >= maxTicks) {
                    ascending.remove(id);
                    finishAscend(p);
                    cancel();
                    return;
                }
                if (!p.hasPotionEffect(PotionEffectType.LEVITATION)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, maxTicks - age, level, false, false, false));
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);

        ascending.put(id, task);
        send(by, "ran-ascend", "%player%", target.getName());
    }

    private void finishAscend(Player p) {
        p.removePotionEffect(PotionEffectType.LEVITATION);
        if (cfg().getBoolean("ascend.safe-landing", true)) {
            int ticks = Math.max(1, cfg().getInt("ascend.slow-falling-seconds", 25)) * 20;
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ticks, 0, false, false, false));
        }
    }

    /** Stops an ascent (reset or quit). */
    public void stopAscend(Player p, boolean landSafely) {
        BukkitTask task = ascending.remove(p.getUniqueId());
        if (task == null) return;
        task.cancel();
        p.removePotionEffect(PotionEffectType.LEVITATION);
        if (landSafely) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 25 * 20, 0, false, false, false));
        }
    }

    // ------------------------------------------------------------------ lifecycle

    /** Called when a player leaves: rejoining is what ends the disconnect troll. */
    public void handleQuit(Player p) {
        unfreeze(p);
        scared.remove(p.getUniqueId());
        restoreHelmet(p);
        stopAscend(p, true);
    }

    /** Called when a player joins: clears anything a crash might have left behind. */
    public void handleJoin(Player p) {
        unfreeze(p);
        restoreHelmet(p);
    }

    public void shutdown() {
        for (UUID id : new HashSet<>(frozen)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) unfreeze(p);
        }
        for (UUID id : new HashSet<>(scared)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) restoreHelmet(p);
        }
        for (UUID id : new HashSet<>(ascending.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) stopAscend(p, true);
        }
        frozen.clear();
        scared.clear();
        ascending.clear();
    }
}
