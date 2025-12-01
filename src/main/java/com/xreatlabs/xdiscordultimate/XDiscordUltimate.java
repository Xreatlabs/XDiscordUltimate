package com.xreatlabs.xdiscordultimate;

import com.xreatlabs.xdiscordultimate.commands.*;
import com.xreatlabs.xdiscordultimate.listeners.*;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.modules.ModuleManager;
import com.xreatlabs.xdiscordultimate.utils.*;
import com.xreatlabs.xdiscordultimate.database.DatabaseManager;
import com.xreatlabs.xdiscordultimate.discord.DiscordManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public class XDiscordUltimate extends JavaPlugin {
    
    private static XDiscordUltimate instance;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private AdminUtils adminUtils;
    private EmbedUtils embedUtils;
    private DatabaseManager databaseManager;
    private ModuleManager moduleManager;
    private DiscordManager discordManager;
    private HelpGUI helpGUI;
    private ConsoleAppender consoleAppender;
    private long startTime;

    private Object luckPerms;
    private boolean luckPermsEnabled;
    private boolean placeholderAPIEnabled;
    
    @Override
    public void onEnable() {
        instance = this;
        startTime = System.currentTimeMillis();
        
        // Display startup banner
        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║      XDiscordUltimate v" + getDescription().getVersion() + "          ║");
        getLogger().info("║   Premium Discord Integration Plugin   ║");
        getLogger().info("║     Supports: " + VersionUtils.getSupportedVersions() + "          ║");
        getLogger().info("╚════════════════════════════════════════╝");
        
        // Check server version compatibility
        if (!VersionUtils.isSupported()) {
            getLogger().warning("⚠ Server version " + VersionUtils.getVersionString() + " may not be fully supported!");
            getLogger().warning("⚠ Recommended versions: " + VersionUtils.getSupportedVersions());
        } else {
            getLogger().info("✓ Server version: " + VersionUtils.getVersionString() + " (Supported)");
        }
        
        // Load runtime dependencies first
        try {
            LibraryManager libraryManager = new LibraryManager(this);
            libraryManager.loadAllLibraries();
        } catch (Exception e) {
            getLogger().severe("Failed to load dependencies! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize configuration
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);
        
        // Initialize utilities
        adminUtils = new AdminUtils(this);
        embedUtils = new EmbedUtils(this);
        
        // Check optional dependencies
        checkOptionalDependencies();
        
        // Initialize database first
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize module manager (loads modules but Discord features wait for ready)
        moduleManager = new ModuleManager(this);
        moduleManager.loadModules();
        
        // Initialize Discord manager
        discordManager = new DiscordManager(this);
        discordManager.initialize().thenAccept(success -> {
            if (success) {
                // Notify modules that Discord is ready
                Bukkit.getScheduler().runTask(this, () -> {
                    moduleManager.onDiscordReady();
                    getLogger().info("All modules notified of Discord ready state");
                    
                    // Send server start message
                    com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule chatBridge = 
                        moduleManager.getModule(com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule.class);
                    if (chatBridge != null && chatBridge.isEnabled()) {
                        // Delay slightly to ensure webhook is set up
                        Bukkit.getScheduler().runTaskLater(this, () -> {
                            chatBridge.sendServerStartMessage();
                        }, 40L); // 2 seconds delay
                    }
                });
                
                // Attach console appender now that the bot is ready
                if (getConfig().getBoolean("discord-console.enabled", true)) {
                    consoleAppender = new ConsoleAppender(this);
                    consoleAppender.start();
                    org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
                    rootLogger.addAppender(consoleAppender);
                }
            } else {
                getLogger().severe("Failed to initialize Discord bot! Check your bot token and guild ID.");
            }
        });
        
        // Initialize help GUI
        helpGUI = new HelpGUI(this);
        
        // Register commands
        registerCommands();
        
        // Register listeners
        registerListeners();
        
        // Discord events are now handled by DiscordListener
        
        // Start metrics
        if (getConfig().getBoolean("advanced.metrics.enabled", true)) {
            int pluginId = getConfig().getInt("advanced.metrics.bstats-id", 12345);
            new Metrics(this, pluginId);
        }
        
        // Log server startup
        if (moduleManager != null) {
            Module loggingModule = moduleManager.getModule("server-logging");
            if (loggingModule != null && loggingModule.isEnabled()) {
                ((com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule) loggingModule).logServerStartup();
            }
        }
        
        getLogger().info("XDiscordUltimate v" + getDescription().getVersion() + " has been enabled!");
        
        // Check for updates
        if (getConfig().getBoolean("general.check-updates", true)) {
            new UpdateChecker(this).checkForUpdates();
        }
    }
    
    @Override
    public void onDisable() {
        // Detach console appender
        if (consoleAppender != null) {
            consoleAppender.stop();
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.removeAppender(consoleAppender);
        }

        // Send server stop message to Discord
        if (moduleManager != null) {
            com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule chatBridge = 
                moduleManager.getModule(com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule.class);
            if (chatBridge != null && chatBridge.isEnabled()) {
                chatBridge.sendServerStopMessage();
                // Small delay to ensure message is sent
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
            
            // Log server shutdown
            Module loggingModule = moduleManager.getModule("server-logging");
            if (loggingModule != null && loggingModule.isEnabled()) {
                ((com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule) loggingModule).logServerShutdown();
            }
        }
        
        // Cleanup help GUI
        if (helpGUI != null) {
            helpGUI.cleanup();
        }
        
        // Disable modules
        if (moduleManager != null) {
            moduleManager.disableModules();
        }
        
        // Shutdown Discord bot
        if (discordManager != null) {
            discordManager.shutdown();
        }
        
        // Close database
        if (databaseManager != null) {
            databaseManager.close();
        }
        
        getLogger().info("XDiscordUltimate has been disabled!");
    }
    
    
    private void registerCommands() {
        // Register main command
        getCommand("xdiscord").setExecutor(new XDiscordCommand(this));
        
        // Register feature commands
        getCommand("verify").setExecutor(new VerifyCommand(this));
        getCommand("support").setExecutor(new SupportCommand(this));
        getCommand("announce").setExecutor(new AnnounceCommand(this));
        getCommand("discordconsole").setExecutor(new DiscordConsoleCommand(this));
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("help").setExecutor(new HelpCommand(this));
        getCommand("stats").setExecutor(new StatsCommand(this));
    }
    
    private void registerListeners() {
        // Register Bukkit listeners
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ServerListener(this), this);
    }
    
    
    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
        moduleManager.reloadModules();
        getLogger().info("Configuration reloaded!");
    }
    
    private void checkOptionalDependencies() {
        // Check for LuckPerms (optional)
        try {
            if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
                Class<?> luckPermsClass = Class.forName("net.luckperms.api.LuckPerms");
                RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(luckPermsClass);
                if (provider != null) {
                    luckPerms = provider.getProvider();
                    luckPermsEnabled = true;
                    getLogger().info("LuckPerms found and hooked!");
                }
            }
        } catch (ClassNotFoundException e) {
            // LuckPerms not available
        }
        
        if (!luckPermsEnabled) {
            getLogger().warning("LuckPerms not found. Some features will be disabled.");
        }
        
        // Check for PlaceholderAPI (optional)
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIEnabled = true;
            // Register our expansion
            try {
                new com.xreatlabs.xdiscordultimate.hooks.PlaceholderAPIExpansion(this).register();
                getLogger().info("PlaceholderAPI found and expansion registered!");
            } catch (Exception e) {
                getLogger().warning("Failed to register PlaceholderAPI expansion: " + e.getMessage());
            }
        } else {
            getLogger().warning("PlaceholderAPI not found. Some features will be disabled.");
        }
    }
    
    // Getters
    public static XDiscordUltimate getInstance() {
        return instance;
    }
    
    public ConfigManager getConfigManager() {
        return configManager;
    }
    
    public MessageManager getMessageManager() {
        return messageManager;
    }
    
    public AdminUtils getAdminUtils() {
        return adminUtils;
    }
    
    public EmbedUtils getEmbedUtils() {
        return embedUtils;
    }
    
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public ModuleManager getModuleManager() {
        return moduleManager;
    }
    
    public DiscordManager getDiscordManager() {
        return discordManager;
    }
    
    public Object getLuckPerms() {
        return luckPerms;
    }
    
    public boolean isLuckPermsEnabled() {
        return luckPermsEnabled;
    }
    
    public boolean isPlaceholderAPIEnabled() {
        return placeholderAPIEnabled;
    }
    
    public String parsePlaceholders(String text, org.bukkit.entity.Player player) {
        if (placeholderAPIEnabled && player != null) {
            try {
                Class<?> placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                java.lang.reflect.Method setPlaceholders = placeholderAPIClass.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class);
                return (String) setPlaceholders.invoke(null, player, text);
            } catch (Exception e) {
                // PlaceholderAPI not available or error occurred
            }
        }
        return text;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public HelpGUI getHelpGUI() {
        return helpGUI;
    }
}