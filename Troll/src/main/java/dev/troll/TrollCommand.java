package dev.troll;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TrollCommand implements TabExecutor {

    private final TrollPlugin plugin;
    private final TrollManager manager;
    private final TrollMenu menu;
    private final PackService pack;

    public TrollCommand(TrollPlugin plugin, TrollManager manager, TrollMenu menu, PackService pack) {
        this.plugin = plugin;
        this.manager = manager;
        this.menu = menu;
        this.pack = pack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("troll.use")) {
            // Look exactly like a command that doesn't exist.
            sender.sendMessage(MiniMessage.miniMessage().deserialize(
                    plugin.getConfig().getString("messages.no-permission", "<red>Unknown command.")));
            return true;
        }

        if (args.length == 0) {
            if (sender instanceof Player player) {
                menu.openPicker(player, 0);
            } else {
                manager.send(sender, "usage");
            }
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            pack.init();
            manager.send(sender, "reloaded");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            manager.send(sender, "player-not-found", "%player%", args[0]);
            return true;
        }

        if (args.length == 1) {
            if (sender instanceof Player player) {
                menu.openTrolls(player, target);
            } else {
                manager.send(sender, "usage");
            }
            return true;
        }

        String what = args[1].toLowerCase(Locale.ROOT);
        if (what.equals("reset")) {
            manager.reset(sender, target);
            return true;
        }
        Troll troll = Troll.byId(what);
        if (troll == null) {
            manager.send(sender, "usage");
            return true;
        }
        manager.run(sender, target, troll);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("troll.use")) return out;

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(prefix)) out.add("reload");
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(p.getName());
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Troll t : Troll.values()) {
                if (t.id().startsWith(prefix)) out.add(t.id());
            }
            if ("reset".startsWith(prefix)) out.add("reset");
        }
        return out;
    }
}
