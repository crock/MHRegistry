package com.domaincord.MHRegistry;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;

public class MHRegistry extends JavaPlugin implements Listener {
    private FileConfiguration config;
    private boolean worldGuardEnabled;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        config = getConfig();

        // Check for WorldGuard
        worldGuardEnabled = getServer().getPluginManager().getPlugin("WorldGuard") != null;
        if (worldGuardEnabled) {
            getLogger().info("WorldGuard detected! Region integration enabled.");
        } else {
            getLogger().warning("WorldGuard not found. Region features disabled.");
        }

        // Register command
        getCommand("submitbuild").setExecutor(new SubmitBuildCommand(this));

        // Register event listener
        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("MHRegistry enabled!");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage(Component.text("Hello, " + event.getPlayer().getName() + "!"));
    }

    @Override
    public void onDisable() {
        getLogger().info("MHRegistry disabled!");
    }

    public FileConfiguration getPluginConfig() {
        return config;
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

}