package dev.troll;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Two screens: pick a player (paged heads), then pick a troll for that player. */
public final class TrollMenu implements Listener {

    private static final int PER_PAGE = 45;

    private static final class PickerHolder implements InventoryHolder {
        Inventory inventory;
        int page;
        final List<UUID> shown = new ArrayList<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class TrollHolder implements InventoryHolder {
        Inventory inventory;
        UUID target;
        final Map<Integer, String> slots = new HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final TrollPlugin plugin;
    private final TrollManager manager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public TrollMenu(TrollPlugin plugin, TrollManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private Component text(String raw, String... pairs) {
        String s = raw == null ? "" : raw;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = Pattern.compile(Pattern.quote(pairs[i]), Pattern.CASE_INSENSITIVE)
                    .matcher(s).replaceAll(Matcher.quoteReplacement(pairs[i + 1]));
        }
        return mm.deserialize(s).decoration(TextDecoration.ITALIC, false);
    }

    private ItemStack item(ConfigurationSection s, Material fallback) {
        Material m = s == null ? null : Material.matchMaterial(s.getString("material", ""));
        ItemStack item = new ItemStack(m != null ? m : fallback);
        if (s == null) return item;
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(s.getString("name", "")));
        List<Component> lore = new ArrayList<>();
        for (String line : s.getStringList("description")) lore.add(text(line));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void click(Player p) {
        p.playSound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 0.8f, 1.2f));
    }

    // ------------------------------------------------------------------ screen 1: players

    public void openPicker(Player viewer, int page) {
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (int) Math.ceil(players.size() / (double) PER_PAGE));
        int current = Math.max(0, Math.min(page, pages - 1));

        PickerHolder holder = new PickerHolder();
        holder.page = current;
        Inventory inv = Bukkit.createInventory(holder, 54, text(cfg().getString("menu.picker-title", "Troll")));
        holder.inventory = inv;

        List<Component> lore = new ArrayList<>();
        for (String line : cfg().getStringList("menu.player-lore")) lore.add(text(line));

        int start = current * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < players.size(); i++) {
            Player p = players.get(start + i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(p);
            meta.displayName(text("<green>" + p.getName()));
            meta.lore(lore);
            head.setItemMeta(meta);
            inv.setItem(i, head);
            holder.shown.add(p.getUniqueId());
        }

        if (current > 0) inv.setItem(45, item(cfg().getConfigurationSection("menu.prev"), Material.ARROW));
        inv.setItem(49, item(cfg().getConfigurationSection("menu.close"), Material.BARRIER));
        if (current < pages - 1) inv.setItem(53, item(cfg().getConfigurationSection("menu.next"), Material.ARROW));

        viewer.openInventory(inv);
    }

    // ------------------------------------------------------------------ screen 2: trolls

    public void openTrolls(Player viewer, Player target) {
        TrollHolder holder = new TrollHolder();
        holder.target = target.getUniqueId();
        Inventory inv = Bukkit.createInventory(holder, 27, text(cfg().getString("menu.trolls-title", "Troll"), "%player%", target.getName()));
        holder.inventory = inv;

        for (Troll troll : Troll.values()) {
            ConfigurationSection s = cfg().getConfigurationSection("menu.trolls." + troll.id());
            if (s == null) continue;
            int slot = s.getInt("slot", -1);
            if (slot < 0 || slot >= inv.getSize()) continue;
            holder.slots.put(slot, troll.id());
            inv.setItem(slot, item(s, Material.STONE));
        }
        placeButton(inv, holder, "reset", Material.MILK_BUCKET);
        placeButton(inv, holder, "back", Material.ARROW);

        viewer.openInventory(inv);
    }

    private void placeButton(Inventory inv, TrollHolder holder, String id, Material fallback) {
        ConfigurationSection s = cfg().getConfigurationSection("menu." + id);
        if (s == null) return;
        int slot = s.getInt("slot", -1);
        if (slot < 0 || slot >= inv.getSize()) return;
        holder.slots.put(slot, id);
        inv.setItem(slot, item(s, fallback));
    }

    // ------------------------------------------------------------------ clicks

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (!(holder instanceof PickerHolder) && !(holder instanceof TrollHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) return;

        if (!player.hasPermission("troll.use")) {
            player.closeInventory();
            return;
        }

        int slot = event.getSlot();
        if (holder instanceof PickerHolder picker) {
            clickPicker(player, picker, slot);
        } else if (holder instanceof TrollHolder trolls) {
            clickTrolls(player, trolls, slot);
        }
    }

    private void clickPicker(Player player, PickerHolder picker, int slot) {
        if (slot == 49) {
            click(player);
            player.closeInventory();
            return;
        }
        if (slot == 45 && picker.page > 0) {
            click(player);
            openPicker(player, picker.page - 1);
            return;
        }
        if (slot == 53 && picker.inventory.getItem(53) != null) {
            click(player);
            openPicker(player, picker.page + 1);
            return;
        }
        if (slot >= 0 && slot < picker.shown.size()) {
            Player target = Bukkit.getPlayer(picker.shown.get(slot));
            click(player);
            if (target == null) {
                manager.send(player, "player-left");
                openPicker(player, picker.page);
            } else {
                openTrolls(player, target);
            }
        }
    }

    private void clickTrolls(Player player, TrollHolder trolls, int slot) {
        String id = trolls.slots.get(slot);
        if (id == null) return;
        click(player);

        if (id.equals("back")) {
            openPicker(player, 0);
            return;
        }
        Player target = Bukkit.getPlayer(trolls.target);
        if (target == null) {
            manager.send(player, "player-left");
            openPicker(player, 0);
            return;
        }
        if (id.equals("reset")) {
            manager.reset(player, target);
            return;
        }
        Troll troll = Troll.byId(id);
        if (troll != null) manager.run(player, target, troll);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof PickerHolder || holder instanceof TrollHolder) {
            event.setCancelled(true);
        }
    }
}
