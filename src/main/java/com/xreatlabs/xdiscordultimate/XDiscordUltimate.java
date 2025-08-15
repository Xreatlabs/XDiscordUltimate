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
        
        getLogger().info("XDiscordUltimate starting up...");
        
        // Load runtime dependencies first
        try {
            LibraryManager libraryManager = new LibraryManager(this);
            libraryManager.loadAllLibraries();
        } catch (Exception e) {
            getLogger().severe("Failed to load dependencies! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize configuration
        try {
            saveDefaultConfig();
            configManager = new ConfigManager(this);
            messageManager = new MessageManager(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize configuration! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize utilities
        try {
            adminUtils = new AdminUtils(this);
            embedUtils = new EmbedUtils(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize utilities! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Check optional dependencies
        checkOptionalDependencies();
        
        // Initialize Discord manager
        try {
            discordManager = new DiscordManager(this);
            discordManager.initialize().thenAccept(success -> {
                if (success) {
                    // Attach console appender now that the bot is ready
                    if (getConfig().getBoolean("discord-console.enabled", true)) {
                        try {
                            consoleAppender = new ConsoleAppender(this);
                            consoleAppender.start();
                            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
                            rootLogger.addAppender(consoleAppender);
                        } catch (Exception e) {
                            getLogger().warning("Failed to initialize console appender: " + e.getMessage());
                        }
                    }
                } else {
                    getLogger().severe("Failed to initialize Discord bot!");
                }
            }).exceptionally(throwable -> {
                getLogger().severe("Discord bot initialization failed with exception: " + throwable.getMessage());
                return null;
            });
        } catch (Exception e) {
            getLogger().severe("Failed to create Discord manager! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize database
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize module manager
        try {
            moduleManager = new ModuleManager(this);
            moduleManager.loadModules();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize module manager! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Initialize help GUI
        try {
            helpGUI = new HelpGUI(this);
        } catch (Exception e) {
            getLogger().warning("Failed to initialize help GUI: " + e.getMessage());
            helpGUI = null;
        }
        
        // Register commands
        try {
            registerCommands();
        } catch (Exception e) {
            getLogger().severe("Failed to register commands! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Register listeners
        try {
            registerListeners();
        } catch (Exception e) {
            getLogger().severe("Failed to register listeners! Plugin will be disabled.");
            getLogger().severe("Error: " + e.getMessage());
            if (getConfigManager() != null && getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Discord events are now handled by DiscordListener
        
        // Start metrics
        if (getConfig().getBoolean("advanced.metrics.enabled", true)) {
            try {
                int pluginId = getConfig().getInt("advanced.metrics.bstats-id", 12345);
                new Metrics(this, pluginId);
            } catch (Exception e) {
                getLogger().warning("Failed to start metrics: " + e.getMessage());
            }
        }
        
        // Log server startup
        if (moduleManager != null) {
            try {
                Module loggingModule = moduleManager.getModule("server-logging");
                if (loggingModule != null && loggingModule.isEnabled()) {
                    ((com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule) loggingModule).logServerStartup();
                }
            } catch (Exception e) {
                getLogger().warning("Failed to log server startup: " + e.getMessage());
            }
        }
        
        getLogger().info("XDiscordUltimate v" + getDescription().getVersion() + " has been enabled!");
        
        // Check for updates
        if (getConfig().getBoolean("general.check-updates", true)) {
            try {
                new UpdateChecker(this).checkForUpdates();
            } catch (Exception e) {
                getLogger().warning("Failed to check for updates: " + e.getMessage());
            }
        }
    }
    
    @Override
    public void onDisable() {
        getLogger().info("XDiscordUltimate shutting down...");
        
        // Detach console appender
        if (consoleAppender != null) {
            try {
                consoleAppender.stop();
                org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
                rootLogger.removeAppender(consoleAppender);
            } catch (Exception e) {
                getLogger().warning("Error detaching console appender: " + e.getMessage());
            }
        }

        // Log server shutdown
        if (moduleManager != null) {
            try {
                Module loggingModule = moduleManager.getModule("server-logging");
                if (loggingModule != null && loggingModule.isEnabled()) {
                    ((com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule) loggingModule).logServerShutdown();
                }
            } catch (Exception e) {
                getLogger().warning("Failed to log server shutdown: " + e.getMessage());
            }
        }
        
        // Cleanup help GUI
        if (helpGUI != null) {
            try {
                helpGUI.cleanup();
            } catch (Exception e) {
                getLogger().warning("Error cleaning up help GUI: " + e.getMessage());
            }
        }
        
        // Disable modules
        if (moduleManager != null) {
            try {
                moduleManager.disableModules();
            } catch (Exception e) {
                getLogger().warning("Error disabling modules: " + e.getMessage());
            }
        }
        
        // Shutdown Discord bot
        if (discordManager != null) {
            try {
                discordManager.shutdown();
            } catch (Exception e) {
                getLogger().warning("Error shutting down Discord bot: " + e.getMessage());
            }
        }
        
        // Close database
        if (databaseManager != null) {
            try {
                databaseManager.close();
            } catch (Exception e) {
                getLogger().warning("Error closing database: " + e.getMessage());
            }
        }
        
        // Clear instance reference
        instance = null;
        
        getLogger().info("XDiscordUltimate has been disabled!");
    }
    
    
    private void registerCommands() {
        // Register main command
        getCommand("xdiscord").setExecutor(new XDiscordCommand(this));
        
        // Register feature commands
        getCommand("verify").setExecutor(new VerifyCommand(this));
        getCommand("support").setExecutor(new SupportCommand(this));
        getCommand("embed").setExecutor(new EmbedCommand(this));
        getCommand("announce").setExecutor(new AnnounceCommand(this));
        getCommand("discordconsole").setExecutor(new DiscordConsoleCommand(this));
        getCommand("report").setExecutor(new ReportCommand(this));
        getCommand("help").setExecutor(new HelpCommand(this));
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
            getLogger().info("PlaceholderAPI found and hooked!");
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
}