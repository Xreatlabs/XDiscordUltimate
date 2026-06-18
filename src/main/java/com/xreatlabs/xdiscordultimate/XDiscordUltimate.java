package com.xreatlabs.xdiscordultimate;

import com.xreatlabs.xdiscordultimate.commands.*;
import com.xreatlabs.xdiscordultimate.commands.LanguageCommand;
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
    private java.lang.reflect.Method cachedSetPlaceholdersMethod;
    
    @Override
    public void onEnable() {
        instance = this;
        startTime = System.currentTimeMillis();

        getLogger().info("╔════════════════════════════════════════╗");
        getLogger().info("║      XDiscordUltimate v" + getDescription().getVersion() + "          ║");
        getLogger().info("║   Premium Discord Integration Plugin   ║");
        getLogger().info("║     Supports: " + VersionUtils.getSupportedVersions() + "          ║");
        getLogger().info("╚════════════════════════════════════════╝");

        if (!VersionUtils.isSupported()) {
            getLogger().warning("⚠ Server version " + VersionUtils.getVersionString() + " may not be fully supported!");
            getLogger().warning("⚠ Recommended versions: " + VersionUtils.getSupportedVersions());
        } else {
            getLogger().info("✓ Server version: " + VersionUtils.getVersionString() + " (Supported)");
        }

        try {
            LibraryManager libraryManager = new LibraryManager(this);
            libraryManager.loadAllLibraries();
        } catch (Exception e) {
            getLogger().severe("Failed to load dependencies! Plugin will be disabled.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        adminUtils = new AdminUtils(this);
        embedUtils = new EmbedUtils(this);

        checkOptionalDependencies();

        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to initialize database!", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        moduleManager = new ModuleManager(this);
        moduleManager.loadModules();

        discordManager = new DiscordManager(this);
        discordManager.initialize().thenAccept(success -> {
            if (success) {
                Bukkit.getScheduler().runTask(this, () -> {
                    moduleManager.onDiscordReady();
                    getLogger().info("All modules notified of Discord ready state");

                    com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule chatBridge =
                        moduleManager.getModule(com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule.class);
                    if (chatBridge != null && chatBridge.isEnabled()) {
                        Bukkit.getScheduler().runTaskLater(this, chatBridge::sendServerStartMessage, 40L);
                    }
                });

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

        helpGUI = new HelpGUI(this);

        registerCommands();
        registerListeners();

        if (getConfig().getBoolean("advanced.metrics.enabled", true)) {
            int pluginId = getConfig().getInt("advanced.metrics.bstats-id", 12345);
            new Metrics(this, pluginId);
        }

        if (moduleManager != null) {
            Module loggingModule = moduleManager.getModule("server-logging");
            if (loggingModule != null && loggingModule.isEnabled()) {
                ((com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule) loggingModule).logServerStartup();
            }
        }

        getLogger().info("XDiscordUltimate v" + getDescription().getVersion() + " has been enabled!");

        if (getConfig().getBoolean("general.check-updates", true)) {
            new UpdateChecker(this).checkForUpdates();
        }
    }

    @Override
    public void onDisable() {
        if (consoleAppender != null) {
            consoleAppender.stop();
            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.removeAppender(consoleAppender);
        }

        if (moduleManager != null) {
            com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule chatBridge =
                moduleManager.getModule(com.xreatlabs.xdiscordultimate.modules.chatbridge.ChatBridgeModule.class);
            if (chatBridge != null && chatBridge.isEnabled()) {
                chatBridge.sendServerStopMessage();
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            Module loggingModule = moduleManager.getModule("server-logging");
            if (loggingModule != null && loggingModule.isEnabled()) {
                ((com.xreatlabs.xdiscordultimate.modules.logging.ServerLoggingModule) loggingModule).logServerShutdown();
            }
        }

        if (helpGUI != null) {
            helpGUI.cleanup();
        }

        if (moduleManager != null) {
            moduleManager.disableModules();
        }

        if (discordManager != null) {
            discordManager.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("XDiscordUltimate has been disabled!");
    }
    
    
    private void registerCommands() {
        registerCommand("xdiscord", new XDiscordCommand(this));
        registerCommand("verify", new VerifyCommand(this));
        registerCommand("unlink", new UnlinkCommand(this));
        registerCommand("support", new SupportCommand(this));
        registerCommand("announce", new AnnounceCommand(this));
        registerCommand("discordconsole", new DiscordConsoleCommand(this));
        registerCommand("report", new ReportCommand(this));
        registerCommand("help", new HelpCommand(this));
        registerCommand("stats", new StatsCommand(this));
        registerCommand("discord", new DiscordCommand(this));
        registerCommand("embed", new EmbedCommand(this));
        registerCommand("welcomedm", new WelcomeDMCommand(this));
        registerCommand("language", new LanguageCommand(this));
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().warning("Command '" + name + "' is not defined in plugin.yml; skipping registration.");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof org.bukkit.command.TabCompleter) {
            command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
        }
    }
    
    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new ServerListener(this), this);
    }
    
    
    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
        moduleManager.reloadModuleConfigs();

        if (discordManager != null && discordManager.isReady()) {
            discordManager.updateActivity();
            getLogger().info("Discord bot activity updated.");
        }

        if (adminUtils != null) {
            adminUtils.reload();
        }

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
        }

        if (!luckPermsEnabled) {
            getLogger().warning("LuckPerms not found. Some features will be disabled.");
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderAPIEnabled = true;
            try {
                Class<?> placeholderAPIClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                cachedSetPlaceholdersMethod = placeholderAPIClass.getMethod("setPlaceholders", org.bukkit.entity.Player.class, String.class);
            } catch (Exception e) {
                getLogger().warning("Failed to cache PlaceholderAPI method: " + e.getMessage());
            }
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
        if (placeholderAPIEnabled && player != null && cachedSetPlaceholdersMethod != null) {
            try {
                return (String) cachedSetPlaceholdersMethod.invoke(null, player, text);
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