package dev.troll;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrollPlugin extends JavaPlugin {

    private TrollManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        PackService pack = new PackService(this);
        pack.init();
        manager = new TrollManager(this, pack);
        TrollMenu menu = new TrollMenu(this, manager);

        PluginCommand command = getCommand("troll");
        if (command != null) {
            TrollCommand handler = new TrollCommand(this, manager, menu, pack);
            command.setExecutor(handler);
            command.setTabCompleter(handler);
        }

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new TrollListener(this, manager, pack), this);
        pm.registerEvents(menu, this);
        getLogger().info("Troll enabled. Grant troll.use with LuckPerms to whoever should have it.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
