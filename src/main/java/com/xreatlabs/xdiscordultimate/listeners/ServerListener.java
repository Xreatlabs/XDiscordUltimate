package com.xreatlabs.xdiscordultimate.listeners;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.PluginEnableEvent;

public class ServerListener implements Listener {
    
    private final XDiscordUltimate plugin;
    
    public ServerListener(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        String command = event.getCommand();
        
        // Log important server commands if debug is enabled
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Console command executed: " + command);
        }
        
        // Handle specific commands that might affect Discord integration
        if (command.toLowerCase().startsWith("stop") || 
            command.toLowerCase().startsWith("restart")) {
            // Notify Discord about server shutdown/restart
            plugin.getLogger().info("Server shutdown/restart detected via console command");
        }
    }
    
    @EventHandler
    public void onServerListPing(ServerListPingEvent event) {
        // Could be used to sync server status with Discord
        // For example, updating bot status with player count
    }
    
    @EventHandler
    public void onPluginEnable(PluginEnableEvent event) {
        String pluginName = event.getPlugin().getName();
        
        // Check for soft dependencies being enabled
        if (pluginName.equals("LuckPerms")) {
            plugin.getLogger().info("LuckPerms detected and enabled - reinitializing integration");
            // Reinitialize LuckPerms integration if needed
        } else if (pluginName.equals("PlaceholderAPI")) {
            plugin.getLogger().info("PlaceholderAPI detected and enabled - reinitializing integration");
            // Reinitialize PlaceholderAPI integration if needed
        }
    }
    
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        String pluginName = event.getPlugin().getName();
        
        // Log when soft dependencies are disabled
        if (pluginName.equals("LuckPerms")) {
            plugin.getLogger().warning("LuckPerms has been disabled - some features may not work");
        } else if (pluginName.equals("PlaceholderAPI")) {
            plugin.getLogger().warning("PlaceholderAPI has been disabled - placeholders will not work");
        }
    }
}