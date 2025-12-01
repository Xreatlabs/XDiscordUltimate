package com.xreatlabs.xdiscordultimate.modules;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule;
import com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule;
import com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule;
import com.xreatlabs.xdiscordultimate.modules.servercontrol.ServerControlModule;
import com.xreatlabs.xdiscordultimate.modules.adminalerts.AdminAlertsModule;
import com.xreatlabs.xdiscordultimate.modules.tickets.TicketModule;
import com.xreatlabs.xdiscordultimate.modules.moderation.ModerationModule;
import com.xreatlabs.xdiscordultimate.modules.botconsole.BotConsoleModule;
import com.xreatlabs.xdiscordultimate.modules.announcements.AnnouncementModule;
import com.xreatlabs.xdiscordultimate.modules.stats.PlayerStatsModule;
import com.xreatlabs.xdiscordultimate.modules.serverstatus.ServerStatusModule;
import com.xreatlabs.xdiscordultimate.modules.boosters.BoosterPerksModule;
import com.xreatlabs.xdiscordultimate.modules.welcomedm.WelcomeDMModule;
import com.xreatlabs.xdiscordultimate.modules.leaderboard.LeaderboardModule;
import com.xreatlabs.xdiscordultimate.modules.activityroles.ActivityRolesModule;
import com.xreatlabs.xdiscordultimate.modules.voicechannel.VoiceChannelModule;
import com.xreatlabs.xdiscordultimate.modules.economy.EconomyBridgeModule;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ModuleManager {
    
    private final XDiscordUltimate plugin;
    private final Map<String, Module> modules;
    
    public ModuleManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.modules = new HashMap<>();
    }
    
    /**
     * Load all modules
     */
    public void loadModules() {
        // Register core modules
        registerModule("chat-bridge", new ChatBridgeModule(plugin));
        registerModule("server-logging", new ServerLoggingModule(plugin));
        
        // Register other modules
        registerModule("verification", new VerificationModule(plugin));
        registerModule("server-control", new ServerControlModule(plugin));
        registerModule("admin-alerts", new AdminAlertsModule(plugin));
        registerModule("tickets", new TicketModule(plugin));
        registerModule("moderation", new ModerationModule(plugin));
        registerModule("bot-console", new BotConsoleModule(plugin));
        registerModule("announcements", new AnnouncementModule(plugin));
        registerModule("player-stats", new PlayerStatsModule(plugin));
        registerModule("server-status", new ServerStatusModule(plugin));
        registerModule("booster-perks", new BoosterPerksModule(plugin));
        registerModule("welcome-dm", new WelcomeDMModule(plugin));
        
        // New advanced modules
        registerModule("leaderboard", new LeaderboardModule(plugin));
        registerModule("activity-roles", new ActivityRolesModule(plugin));
        registerModule("voice-channels", new VoiceChannelModule(plugin));
        registerModule("economy-bridge", new EconomyBridgeModule(plugin));
        
        // Enable modules based on configuration
        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            String moduleName = entry.getKey();
            Module module = entry.getValue();
            
            if (isModuleEnabled(moduleName)) {
                try {
                    module.enable();
                    plugin.getLogger().info("Enabled module: " + module.getName());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + module.getName(), e);
                }
            } else {
                plugin.getLogger().info("Module disabled in config: " + module.getName());
            }
        }
    }
    
    /**
     * Register a module
     */
    private void registerModule(String key, Module module) {
        modules.put(key, module);
    }
    
    /**
     * Check if a module is enabled in config
     */
    private boolean isModuleEnabled(String moduleName) {
        // Module name already matches config path (e.g., "chat-bridge" -> "features.chat-bridge")
        return plugin.getConfigManager().isFeatureEnabled(moduleName);
    }
    
    /**
     * Get a module by key
     */
    public Module getModule(String key) {
        return modules.get(key);
    }
    
    /**
     * Get a module by class
     */
    @SuppressWarnings("unchecked")
    public <T extends Module> T getModule(Class<T> moduleClass) {
        for (Module module : modules.values()) {
            if (moduleClass.isInstance(module)) {
                return (T) module;
            }
        }
        return null;
    }
    
    /**
     * Reload all modules
     */
    public void reloadModules() {
        // Disable all modules
        disableModules();
        
        // Clear module list
        modules.clear();
        
        // Reload modules
        loadModules();
    }
    
    /**
     * Disable all modules
     */
    public void disableModules() {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.disable();
                    plugin.getLogger().info("Disabled module: " + module.getName());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + module.getName(), e);
                }
            }
        }
    }
    
    /**
     * Called when Discord is ready
     */
    public void onDiscordReady() {
        for (Module module : modules.values()) {
            if (module.isEnabled()) {
                try {
                    module.onDiscordReady();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, 
                        "Error in onDiscordReady for module: " + module.getName(), e);
                }
            }
        }
    }
    
    /**
     * Get all loaded modules
     */
    public Map<String, Module> getModules() {
        return new HashMap<>(modules);
    }
    
    /**
     * Check if a module is loaded and enabled
     */
    public boolean isModuleActive(String key) {
        Module module = modules.get(key);
        return module != null && module.isEnabled();
    }
}