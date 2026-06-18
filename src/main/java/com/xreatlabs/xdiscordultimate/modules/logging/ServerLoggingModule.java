package com.xreatlabs.xdiscordultimate.modules.logging;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class ServerLoggingModule extends Module implements Listener {
    
    private String logsChannelName;
    private String consoleChannelName;
    private boolean enableServerLogs;
    private boolean enableConsoleOutput;
    private boolean enableCommandLogging;
    private boolean enableErrorLogging;
    private boolean enableWarningLogging;
    private boolean enableInfoLogging;
    
    private CustomLogHandler logHandler;
    private final ConcurrentLinkedQueue<LogRecord> logQueue = new ConcurrentLinkedQueue<>();
    private BukkitRunnable logProcessor;
    
    // Log levels to track
    private final List<Level> trackedLevels = new ArrayList<>();
    
    public ServerLoggingModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Server Logging";
    }
    
    @Override
    public String getDescription() {
        return "Logs server events, console output, and commands to Discord";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        
        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Setup log handler if console output is enabled
        if (enableConsoleOutput) {
            setupLogHandler();
        }
        
        // Start log processor
        startLogProcessor();
        
        info("Server logging module enabled");
    }
    
    @Override
    protected void onDisable() {
        // Remove log handler
        if (logHandler != null) {
            Bukkit.getLogger().removeHandler(logHandler);
        }
        
        // Stop log processor
        if (logProcessor != null) {
            logProcessor.cancel();
        }
        
        // Unregister events
        ServerCommandEvent.getHandlerList().unregister(this);
        
        info("Server logging module disabled");
    }
    
    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        
        if (config != null) {
            logsChannelName = config.getString("logs-channel-id", "");
            enableServerLogs = config.getBoolean("log-server-events", true);
            enableConsoleOutput = false;
            enableCommandLogging = config.getBoolean("log-commands", true);
            enableErrorLogging = true;
            enableWarningLogging = true;
            enableInfoLogging = false;
        } else {
            warning("Config section not found, using defaults");
            logsChannelName = "";
            enableServerLogs = true;
            enableConsoleOutput = false;
            enableCommandLogging = true;
            enableErrorLogging = true;
            enableWarningLogging = true;
            enableInfoLogging = false;
        }
        
        // Fallback to global channel config
        if (logsChannelName == null || logsChannelName.isEmpty()) {
            logsChannelName = plugin.getConfig().getString("discord.channels.logs", "");
        }
        consoleChannelName = plugin.getConfig().getString("discord.channels.console", "");
        
        // Setup tracked levels
        trackedLevels.clear();
        if (enableErrorLogging) trackedLevels.add(Level.SEVERE);
        if (enableWarningLogging) trackedLevels.add(Level.WARNING);
        if (enableInfoLogging) trackedLevels.add(Level.INFO);
        
        info("Logs channel: " + (logsChannelName.isEmpty() ? "(not set)" : logsChannelName));
    }
    
    private void setupLogHandler() {
        logHandler = new CustomLogHandler();
        logHandler.setLevel(Level.ALL);
        
        // Add to root logger
        Bukkit.getLogger().addHandler(logHandler);
    }
    
    private void startLogProcessor() {
        logProcessor = new BukkitRunnable() {
            @Override
            public void run() {
                processLogQueue();
            }
        };
        
        // Process logs every 5 seconds
        logProcessor.runTaskTimerAsynchronously(plugin, 100L, 100L);
    }
    
    private void processLogQueue() {
        if (logQueue.isEmpty()) return;
        
        TextChannel consoleChannel = getConsoleChannel();
        if (consoleChannel == null) return;
        
        StringBuilder logBatch = new StringBuilder();
        int count = 0;
        
        while (!logQueue.isEmpty() && count < 10) { // Process up to 10 logs at once
            LogRecord record = logQueue.poll();
            if (record != null) {
                String formattedLog = formatLogRecord(record);
                if (logBatch.length() + formattedLog.length() > 1900) { // Discord limit
                    break;
                }
                logBatch.append(formattedLog).append("\n");
                count++;
            }
        }
        
        if (logBatch.length() > 0) {
            sendLogBatch(consoleChannel, logBatch.toString());
        }
    }
    
    private String formatLogRecord(LogRecord record) {
        String level = record.getLevel().getName();
        String message = record.getMessage();
        String logger = record.getLoggerName();
        
        // Truncate long messages
        if (message.length() > 200) {
            message = message.substring(0, 197) + "...";
        }
        
        return String.format("[%s] %s: %s", level, logger, message);
    }
    
    private void sendLogBatch(TextChannel channel, String logs) {
        EmbedBuilder embed = EmbedUtils.infoEmbed("Console Logs", "```\n" + logs + "```")
            .setFooter("Server Console", null);

        channel.sendMessageEmbeds(embed.build()).queue(
            success -> debug("Sent log batch to Discord"),
            error -> warning("Failed to send logs to Discord: " + error.getMessage())
        );
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!enableCommandLogging) return;
        
        String command = event.getCommand();
        
        // Filter sensitive commands
        if (isSensitiveCommand(command)) {
            command = "[REDACTED COMMAND]";
        }
        
        logServerEvent("Console Command", "Command executed: `" + command + "`", Color.CYAN);
    }
    
    private boolean isSensitiveCommand(String command) {
        String lowerCommand = command.toLowerCase();
        return lowerCommand.contains("password") || 
               lowerCommand.contains("token") || 
               lowerCommand.contains("key") ||
               lowerCommand.startsWith("op ") ||
               lowerCommand.startsWith("deop ");
    }
    
    /**
     * Log a server event to Discord
     */
    public void logServerEvent(String title, String description, Color color) {
        if (!enableServerLogs) return;

        TextChannel logsChannel = getLogsChannel();
        if (logsChannel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color != null ? color : EmbedUtils.INFO)
            .setTitle(title)
            .setDescription(description)
            .setTimestamp(Instant.now())
            .setFooter("Server Event", null);

        logsChannel.sendMessageEmbeds(embed.build()).queue(
            success -> debug("Logged server event: " + title),
            error -> warning("Failed to log server event: " + error.getMessage())
        );
    }
    
    /**
     * Log server startup
     */
    public void logServerStartup() {
        logServerEvent("Server Started",
            "Server started.\n" +
            "Version: " + Bukkit.getVersion() + "\n" +
            "Players: 0/" + Bukkit.getMaxPlayers(),
            Color.GREEN);
    }
    
    /**
     * Log server shutdown
     */
    public void logServerShutdown() {
        logServerEvent("Server Stopping", 
            "Server is shutting down...\n" +
            "Online players: " + Bukkit.getOnlinePlayers().size(),
            Color.RED);
    }
    
    /**
     * Log plugin events
     */
    public void logPluginEvent(String pluginName, String action, boolean success) {
        Color color = success ? Color.GREEN : Color.RED;
        String status = success ? "✅" : "❌";
        
        logServerEvent("Plugin " + action, 
            status + " Plugin: **" + pluginName + "**\n" +
            "Action: " + action,
            color);
    }
    
    /**
     * Log performance warnings
     */
    public void logPerformanceWarning(String metric, String value, String threshold) {
        logServerEvent("Performance Warning", 
            "⚠️ **" + metric + "** is concerning!\n" +
            "Current: " + value + "\n" +
            "Threshold: " + threshold,
            Color.ORANGE);
    }
    
    private TextChannel getLogsChannel() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return null;
        }
        
        if (logsChannelName == null || logsChannelName.isEmpty()) {
            return null;
        }
        
        // Try by ID first (if numeric)
        if (logsChannelName.matches("\\d+")) {
            TextChannel channel = plugin.getDiscordManager().getTextChannelById(logsChannelName);
            if (channel != null) {
                return channel;
            }
        }
        
        // Fall back to name lookup
        net.dv8tion.jda.api.entities.Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return null;
        
        return guild.getTextChannelsByName(logsChannelName, true)
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    private TextChannel getConsoleChannel() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return null;
        }
        
        if (consoleChannelName == null || consoleChannelName.isEmpty()) {
            return null;
        }
        
        // Try by ID first (if numeric)
        if (consoleChannelName.matches("\\d+")) {
            TextChannel channel = plugin.getDiscordManager().getTextChannelById(consoleChannelName);
            if (channel != null) {
                return channel;
            }
        }
        
        // Fall back to name lookup
        net.dv8tion.jda.api.entities.Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return null;
        
        return guild.getTextChannelsByName(consoleChannelName, true)
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Custom log handler to capture console output
     */
    private class CustomLogHandler extends Handler {
        @Override
        public void publish(LogRecord record) {
            if (!enableConsoleOutput) return;
            
            // Filter by level
            if (!trackedLevels.contains(record.getLevel())) {
                return;
            }
            
            // Filter out our own messages to prevent loops
            if (record.getLoggerName().contains("XDiscordUltimate")) {
                return;
            }
            
            // Add to queue for processing
            logQueue.offer(record);
        }
        
        @Override
        public void flush() {
            // Not needed for our implementation
        }
        
        @Override
        public void close() throws SecurityException {
            // Not needed for our implementation
        }
    }
}