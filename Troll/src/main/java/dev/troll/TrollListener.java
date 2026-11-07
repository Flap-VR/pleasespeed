package dev.troll;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TrollListener implements Listener {

    private final TrollPlugin plugin;
    private final TrollManager manager;
    private final PackService pack;

    public TrollListener(TrollPlugin plugin, TrollManager manager, PackService pack) {
        this.plugin = plugin;
        this.manager = manager;
        this.pack = pack;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        manager.handleJoin(player);

        if (plugin.getConfig().getBoolean("resource-pack.send-on-join", true) && pack.configured()) {
            long delay = Math.max(1L, plugin.getConfig().getLong("resource-pack.send-delay-ticks", 20L));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) pack.send(player, plugin.getConfig().getBoolean("resource-pack.force", false));
            }, delay);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
        pack.markUnloaded(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPackStatus(PlayerResourcePackStatusEvent event) {
        if (!event.getID().equals(pack.id())) return;
        switch (event.getStatus()) {
            case SUCCESSFULLY_LOADED -> pack.markLoaded(event.getPlayer().getUniqueId());
            case DECLINED, FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> pack.markUnloaded(event.getPlayer().getUniqueId());
            default -> { }
        }
    }

    /** Disconnect troll: looking around still works, walking and jumping don't. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!manager.isFrozen(event.getPlayer().getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;

        Location stay = from.clone();
        stay.setYaw(to.getYaw());
        stay.setPitch(to.getPitch());
        event.setTo(stay);
    }

    @EventHandler(ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (manager.isFrozen(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    /** Jumpscare troll: they can't take the overlay item off before it's done. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (manager.isScared(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (manager.isScared(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    /** If they die mid-scare, the overlay item must never drop. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(manager::isScareItem);
        event.getItemsToKeep().removeIf(manager::isScareItem);
    }
}
