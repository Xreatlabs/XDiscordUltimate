package com.xreatlabs.xdiscordultimate.modules.botconsole;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.awt.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class BotConsoleModule extends Module {
    
    private ConsoleListener consoleListener;
    private ConsoleAppender consoleAppender;
    private String consoleChannelName;
    private boolean enableConsoleInput;
    private boolean enableLogStreaming;
    private int maxLogLines;
    
    // Console output management
    private final BlockingQueue<String> consoleQueue = new LinkedBlockingQueue<>();
    private final List<String> consoleHistory = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService consoleExecutor;
    
    // Command execution
    private final Map<String, ConsoleSession> activeSessions = new ConcurrentHashMap<>();
    private final Pattern ANSI_PATTERN = Pattern.compile("\u001B\\[[;\\d]*m");
    
    public BotConsoleModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Bot Console";
    }
    
    @Override
    public String getDescription() {
        return "Remote console access and log streaming through Discord";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Bot Console module enabled - waiting for Discord...");
    }
    
    @Override
    public void onDiscordReady() {
        // Register Discord listener
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            consoleListener = new ConsoleListener();
            plugin.getDiscordManager().getJDA().addEventListener(consoleListener);
            
            // Start console streaming
            if (enableLogStreaming) {
                startConsoleStreaming();
            }
            
            // Setup log appender
            setupLogAppender();
            
            info("Bot Console connected to Discord");
        }
    }
    
    @Override
    protected void onDisable() {
        // Unregister Discord listener
        if (consoleListener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(consoleListener);
        }
        
        // Stop console streaming
        if (consoleExecutor != null) {
            consoleExecutor.shutdown();
        }
        
        // Remove log appender
        if (consoleAppender != null) {
            Logger rootLogger = (Logger) LogManager.getRootLogger();
            rootLogger.removeAppender(consoleAppender);
        }
        
        info("Bot console module disabled");
    }
    
    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        
        if (config != null) {
            consoleChannelName = config.getString("console-channel", "bot-console");
            enableConsoleInput = !config.getBoolean("allow-dm", false); // inverted - if allow-dm is false, console input is enabled
            enableLogStreaming = true;
            maxLogLines = 10;
        } else {
            warning("Config section not found, using defaults");
            consoleChannelName = "bot-console";
            enableConsoleInput = true;
            enableLogStreaming = true;
            maxLogLines = 10;
        }
        
        debug("Console channel: " + consoleChannelName);
    }
    
    @Override
    public List<SlashCommandData> getSlashCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        
        commands.add(Commands.slash("console", "Execute Minecraft console commands")
            .addOption(OptionType.STRING, "command", "The Minecraft command to execute (without /)", true));
        
        commands.add(Commands.slash("server", "Server management and information")
            .addSubcommands(
                new SubcommandData("logs", "Show recent console logs"),
                new SubcommandData("tps", "Show server TPS"),
                new SubcommandData("memory", "Show memory usage"),
                new SubcommandData("players", "List online players"),
                new SubcommandData("plugins", "List server plugins"),
                new SubcommandData("restart", "Restart the server"),
                new SubcommandData("stop", "Stop the server"),
                new SubcommandData("save", "Save all worlds"),
                new SubcommandData("op", "Give operator status")
                    .addOption(OptionType.STRING, "player", "Player name", true),
                new SubcommandData("deop", "Remove operator status")
                    .addOption(OptionType.STRING, "player", "Player name", true),
                new SubcommandData("ban", "Ban a player")
                    .addOption(OptionType.STRING, "player", "Player name", true)
                    .addOption(OptionType.STRING, "reason", "Ban reason", false),
                new SubcommandData("pardon", "Unban a player")
                    .addOption(OptionType.STRING, "player", "Player name", true),
                new SubcommandData("kick", "Kick a player")
                    .addOption(OptionType.STRING, "player", "Player name", true)
                    .addOption(OptionType.STRING, "reason", "Kick reason", false),
                new SubcommandData("whitelist", "Manage whitelist")
                    .addOption(OptionType.STRING, "action", "add/remove/on/off", true)
                    .addOption(OptionType.STRING, "player", "Player name (for add/remove)", false),
                new SubcommandData("reload", "Reload server configuration")
            ));
        
        commands.add(Commands.slash("game", "Game management commands")
            .addSubcommands(
                new SubcommandData("gamemode", "Change gamemode")
                    .addOption(OptionType.STRING, "mode", "Gamemode (survival/creative/adventure/spectator)", true)
                    .addOption(OptionType.STRING, "player", "Player name", false),
                new SubcommandData("difficulty", "Set difficulty")
                    .addOption(OptionType.STRING, "level", "Difficulty level", true),
                new SubcommandData("time", "Manage time")
                    .addOption(OptionType.STRING, "action", "set/add", true)
                    .addOption(OptionType.INTEGER, "value", "Time value", true),
                new SubcommandData("weather", "Set weather")
                    .addOption(OptionType.STRING, "type", "clear/rain/thunder", true)
                    .addOption(OptionType.INTEGER, "duration", "Duration in seconds", false),
                new SubcommandData("give", "Give items to player")
                    .addOption(OptionType.STRING, "player", "Player name", true)
                    .addOption(OptionType.STRING, "item", "Item ID", true)
                    .addOption(OptionType.INTEGER, "amount", "Amount", false),
                new SubcommandData("tp", "Teleport players")
                    .addOption(OptionType.STRING, "player", "Player to teleport", true)
                    .addOption(OptionType.STRING, "target", "Target player or coordinates", true)
            ));
        
        commands.add(Commands.slash("session", "Manage console session")
            .addSubcommands(
                new SubcommandData("start", "Start logging session"),
                new SubcommandData("stop", "Stop logging session and get transcript")
            ));
        
        return commands;
    }
    
    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        ConsoleListener handler = consoleListener != null ? consoleListener : new ConsoleListener();
        handler.handleSlashCommand(event);
        return event.isAcknowledged();
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"console".equals(command) && !"server".equals(command) &&
            !"game".equals(command) && !"session".equals(command)) {
            return false;
        }

        String userId = event.getAuthor().getId();
        if (!canUseConsole(userId)) {
            event.getMessage().reply("You don't have permission to use console commands!\n" +
                "Only trusted administrators can access the console.").queue();
            return true;
        }

        if (!plugin.getAdminUtils().getAdminIDs().contains(userId)) {
            event.getMessage().reply("Your admin privileges have been revoked!").queue();
            return true;
        }

        switch (command) {
            case "console":
                if (args.trim().isEmpty()) {
                    event.getMessage().reply("Usage: `!console <command>`").queue();
                } else {
                    executeConsoleCommand(event, args.trim());
                }
                return true;
            case "server":
                handleServerTextCommand(event, args.trim());
                return true;
            case "game":
                handleGameTextCommand(event, args.trim());
                return true;
            case "session":
                handleSessionCommand(event, args.trim());
                return true;
            default:
                return false;
        }
    }

    private boolean canUseConsole(String userId) {
        if (!plugin.getAdminUtils().isTrustedAdmin(userId)) {
            return false;
        }

        if (plugin.getConfigManager().isFeatureEnabled("verification")) {
            VerificationModule verificationModule = plugin.getModuleManager().getModule(VerificationModule.class);
            if (verificationModule != null && verificationModule.isEnabled()) {
                if (!verificationModule.isDiscordUserVerified(userId)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void handleServerTextCommand(MessageReceivedEvent event, String args) {
        if (args.trim().isEmpty()) {
            event.getMessage().reply("Usage: `!server <logs|tps|memory|players|plugins|restart|stop|save>`").queue();
            return;
        }

        String[] parts = args.trim().split(" ", 2);
        String subcommand = parts[0].toLowerCase();

        switch (subcommand) {
            case "logs":
                sendRecentLogs(event);
                break;
            case "tps":
                sendTpsInfo(event);
                break;
            case "memory":
                sendMemoryInfo(event);
                break;
            case "players":
                sendPlayerList(event);
                break;
            case "plugins":
                sendPluginList(event);
                break;
            case "restart":
                executeConsoleCommand(event, "restart");
                break;
            case "stop":
                executeConsoleCommand(event, "stop");
                break;
            case "save":
                executeConsoleCommand(event, "save-all");
                break;
            default:
                event.getMessage().reply("Unknown server subcommand.").queue();
        }
    }

    private void handleGameTextCommand(MessageReceivedEvent event, String args) {
        if (args.trim().isEmpty()) {
            event.getMessage().reply("Usage: `!game <gamemode|difficulty|time|weather|give|tp> ...`").queue();
            return;
        }

        String[] parts = args.trim().split(" ", 2);
        String subcommand = parts[0].toLowerCase();
        String subArgs = parts.length > 1 ? parts[1] : "";

        String command;
        switch (subcommand) {
            case "gamemode":
                command = "gamemode " + subArgs;
                break;
            case "difficulty":
                command = "difficulty " + subArgs;
                break;
            case "time":
                command = "time " + subArgs;
                break;
            case "weather":
                command = "weather " + subArgs;
                break;
            case "give":
                command = "give " + subArgs;
                break;
            case "tp":
                command = "tp " + subArgs;
                break;
            default:
                event.getMessage().reply("Unknown game subcommand.").queue();
                return;
        }

        executeConsoleCommand(event, command);
    }
    
    private void setupLogAppender() {
        // Create custom appender
        consoleAppender = new ConsoleAppender();
        consoleAppender.start();
        
        // Add to root logger
        Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addAppender(consoleAppender);
    }
    
    private void startConsoleStreaming() {
        consoleExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("XDiscordUltimate-ConsoleStream");
            thread.setDaemon(true);
            return thread;
        });
        
        // Stream console output every 2 seconds
        consoleExecutor.scheduleAtFixedRate(() -> {
            try {
                List<String> lines = new ArrayList<>();
                consoleQueue.drainTo(lines, maxLogLines);
                
                if (!lines.isEmpty()) {
                    sendConsoleOutput(lines);
                }
            } catch (Exception e) {
                error("Error streaming console output", e);
            }
        }, 2, 2, TimeUnit.SECONDS);
    }
    
    private void sendConsoleOutput(List<String> lines) {
        TextChannel channel = getConsoleChannel();
        if (channel == null) return;
        
        // Format output
        StringBuilder output = new StringBuilder("```ansi\n");
        for (String line : lines) {
            // Convert Minecraft color codes to ANSI
            String ansiLine = convertToAnsi(line);
            output.append(ansiLine).append("\n");
        }
        output.append("```");
        
        // Send if not too long
        if (output.length() <= 2000) {
            channel.sendMessage(output.toString()).queue();
        } else {
            // Split into multiple messages
            String content = output.toString();
            while (content.length() > 0) {
                int splitIndex = Math.min(content.length(), 1990);
                if (splitIndex < content.length()) {
                    // Find last newline before limit
                    int lastNewline = content.lastIndexOf('\n', splitIndex);
                    if (lastNewline > 0) {
                        splitIndex = lastNewline;
                    }
                }
                
                String part = content.substring(0, splitIndex);
                if (!part.endsWith("```")) {
                    part += "\n```";
                }
                if (!part.startsWith("```")) {
                    part = "```ansi\n" + part;
                }
                
                channel.sendMessage(part).queue();
                content = content.substring(splitIndex);
            }
        }
    }
    
    private String convertToAnsi(String text) {
        // Remove existing ANSI codes
        text = ANSI_PATTERN.matcher(text).replaceAll("");
        
        // Convert Minecraft color codes to ANSI
        text = text.replace("§0", "\u001B[30m")    // Black
                  .replace("§1", "\u001B[34m")     // Dark Blue
                  .replace("§2", "\u001B[32m")     // Dark Green
                  .replace("§3", "\u001B[36m")     // Dark Aqua
                  .replace("§4", "\u001B[31m")     // Dark Red
                  .replace("§5", "\u001B[35m")     // Dark Purple
                  .replace("§6", "\u001B[33m")     // Gold
                  .replace("§7", "\u001B[37m")     // Gray
                  .replace("§8", "\u001B[90m")     // Dark Gray
                  .replace("§9", "\u001B[94m")     // Blue
                  .replace("§a", "\u001B[92m")     // Green
                  .replace("§b", "\u001B[96m")     // Aqua
                  .replace("§c", "\u001B[91m")     // Red
                  .replace("§d", "\u001B[95m")     // Light Purple
                  .replace("§e", "\u001B[93m")     // Yellow
                  .replace("§f", "\u001B[97m")     // White
                  .replace("§r", "\u001B[0m");     // Reset
        
        return text;
    }
    
    private class ConsoleListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            if (!event.getChannel().getName().equalsIgnoreCase(consoleChannelName)) return;
            if (!enableConsoleInput) return;
            
            // Check permissions - Discord user must be in adminIDs list
            String userId = event.getAuthor().getId();
            if (!plugin.getAdminUtils().isTrustedAdmin(userId)) {
                event.getMessage().reply("❌ You don't have permission to use console commands!\n" +
                    "Only trusted administrators can access the console.").queue();
                
                // Log unauthorized access attempt
                plugin.getLogger().warning("Unauthorized console access attempt by Discord user: " +
                    event.getAuthor().getName() + " (" + userId + ")");
                return;
            }
            
            // Check if verification is required
            if (plugin.getConfigManager().isFeatureEnabled("verification")) {
                VerificationModule verificationModule = plugin.getModuleManager().getModule(VerificationModule.class);
                if (verificationModule != null && verificationModule.isEnabled()) {
                    if (!verificationModule.isDiscordUserVerified(userId)) {
                        event.getMessage().reply("❌ You must verify your account before using console commands!\n" +
                            "Use `/verify` here in Discord to get a code, then use `/verify <code>` in-game.").queue();
                        return;
                    }
                }
            }
            
            // Additional security check - ensure user is still in admin list
            if (!plugin.getAdminUtils().getAdminIDs().contains(userId)) {
                event.getMessage().reply("❌ Your admin privileges have been revoked!").queue();
                plugin.getLogger().warning("Former admin attempted console access: " +
                    event.getAuthor().getName() + " (" + userId + ")");
                return;
            }
            
            String message = event.getMessage().getContentRaw();
            
            // Check for command prefix
            String command = getCommandFromMessage(message);
            if (command != null) {
                // Handle special commands
                handleSpecialCommand(event, command);
                return;
            }
            
            // Execute as console command if no prefix
            executeConsoleCommand(event, message);
        }
        
        public void handleSlashCommand(SlashCommandInteractionEvent event) {
            if (event.isAcknowledged()) {
                return;
            }
            
            String commandName = event.getName();
            String subcommandName = event.getSubcommandName();
            
            if (!commandName.equals("console") && !commandName.equals("server") && 
                !commandName.equals("game") && !commandName.equals("session")) {
                return;
            }
            
            String userId = event.getUser().getId();
            if (!plugin.getAdminUtils().isTrustedAdmin(userId)) {
                event.reply("❌ You don't have permission to use console commands!\n" +
                    "Only trusted administrators can access the console.")
                    .setEphemeral(true)
                    .queue();
                plugin.getLogger().warning("Unauthorized console command attempt by Discord user: " +
                    event.getUser().getName() + " (" + userId + ")");
                return;
            }
            
            if (plugin.getConfigManager().isFeatureEnabled("verification")) {
                VerificationModule verificationModule = plugin.getModuleManager().getModule(VerificationModule.class);
                if (verificationModule != null && verificationModule.isEnabled()) {
                    if (!verificationModule.isDiscordUserVerified(userId)) {
                        event.reply("❌ You must verify your account before using console commands!\n" +
                            "Use `/verify` here in Discord to get a code, then use `/verify <code>` in-game.")
                            .setEphemeral(true)
                            .queue();
                        return;
                    }
                }
            }
            
            if (!plugin.getAdminUtils().getAdminIDs().contains(userId)) {
                event.reply("❌ Your admin privileges have been revoked!")
                    .setEphemeral(true)
                    .queue();
                plugin.getLogger().warning("Former admin attempted console command access: " +
                    event.getUser().getName() + " (" + userId + ")");
                return;
            }
            
            switch (commandName) {
                case "console":
                    if (subcommandName == null) {
                        handleDirectConsoleCommand(event);
                    } else {
                        handleConsoleSlashCommand(event, subcommandName);
                    }
                    break;
                case "server":
                    handleServerSlashCommand(event, subcommandName);
                    break;
                case "game":
                    handleGameSlashCommand(event, subcommandName);
                    break;
                case "session":
                    handleSessionSlashCommand(event, subcommandName);
                    break;
            }
        }

        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            handleSlashCommand(event);
        }
        
        /**
         * Handle direct console command execution: /console <command>
         */
        private void handleDirectConsoleCommand(SlashCommandInteractionEvent event) {
            String command = event.getOption("command").getAsString();
            
            // Validate command
            if (command == null || command.trim().isEmpty()) {
                event.reply("❌ Please provide a command to execute!")
                    .setEphemeral(true)
                    .queue();
                return;
            }
            
            // Clean the command (remove leading slash if present)
            command = command.startsWith("/") ? command.substring(1) : command;
            
            // Log the command execution attempt
            info("Admin " + event.getUser().getName() + " executing console command: " + command);
            
            // Defer reply since command execution might take time
            event.deferReply().setEphemeral(true).queue();
            
            // Make variables effectively final for lambda
            final String finalCommand = command;
            final String userId = event.getUser().getId();
            
            // Execute the command on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    // Capture console output
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    PrintStream oldOut = System.out;
                    PrintStream oldErr = System.err;
                    System.setOut(ps);
                    System.setErr(ps);
                    
                    // Execute the command
                    boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                    
                    // Restore original System.out and System.err
                    System.out.flush();
                    System.err.flush();
                    System.setOut(oldOut);
                    System.setErr(oldErr);
                    
                    // Get the captured output
                    String output = baos.toString().trim();
                    
                    // Prepare response
                    String response;
                    if (output.isEmpty()) {
                        response = "✅ Done (no output)";
                    } else {
                        response = "✅ Done:\n```\n" +
                                  (output.length() > 1800 ? output.substring(0, 1800) + "..." : output) +
                                  "\n```";
                    }
                    
                    // Send response
                    event.getHook().editOriginal(response).queue();
                    
                    // Add to session if active
                    ConsoleSession session = activeSessions.get(userId);
                    if (session != null) {
                        session.addCommand(finalCommand);
                    }
                    
                    // Log successful execution
                    plugin.getLogger().info("Console command executed by " + event.getUser().getName() +
                                          " (" + userId + "): " + finalCommand + " - Success: " + success);
                    
                } catch (Exception e) {
                    // Handle execution errors
                    String errorResponse = "❌ **Error executing command:**\n```\n" +
                                         e.getMessage() + "\n```";
                    event.getHook().editOriginal(errorResponse).queue();
                    
                    // Log the error
                    error("Error executing console command '" + finalCommand + "' by " + event.getUser().getName(), e);
                }
            });
        }
        
        private void handleConsoleSlashCommand(SlashCommandInteractionEvent event, String subcommand) {
            switch (subcommand) {
                case "execute":
                    String command = event.getOption("command").getAsString();
                    event.deferReply().queue();
                    executeConsoleCommandSlash(event, command);
                    break;
                case "logs":
                    sendRecentLogsSlash(event);
                    break;
                case "tps":
                    sendTpsInfoSlash(event);
                    break;
                case "memory":
                    sendMemoryInfoSlash(event);
                    break;
                case "players":
                    sendPlayerListSlash(event);
                    break;
                case "plugins":
                    sendPluginListSlash(event);
                    break;
                case "clear":
                    clearConsoleChannelSlash(event);
                    break;
                case "help":
                    sendHelpMessageSlash(event);
                    break;
            }
        }
        
        private void handleServerSlashCommand(SlashCommandInteractionEvent event, String subcommand) {
            switch (subcommand) {
                case "logs":
                    sendRecentLogsSlash(event);
                    break;
                case "tps":
                    sendTpsInfoSlash(event);
                    break;
                case "memory":
                    sendMemoryInfoSlash(event);
                    break;
                case "players":
                    sendPlayerListSlash(event);
                    break;
                case "plugins":
                    sendPluginListSlash(event);
                    break;
                case "restart":
                    event.deferReply().queue();
                    executeConsoleCommandSlash(event, "restart");
                    break;
                case "stop":
                    event.deferReply().queue();
                    executeConsoleCommandSlash(event, "stop");
                    break;
                case "save":
                    event.deferReply().queue();
                    executeConsoleCommandSlash(event, "save-all");
                    break;
            }
        }
        
        private void handleGameSlashCommand(SlashCommandInteractionEvent event, String subcommand) {
            String command = "";
            switch (subcommand) {
                case "gamemode":
                    command = "gamemode " + event.getOption("mode").getAsString();
                    if (event.getOption("player") != null) {
                        command += " " + event.getOption("player").getAsString();
                    }
                    break;
                case "difficulty":
                    command = "difficulty " + event.getOption("level").getAsString();
                    break;
                case "time":
                    command = "time " + event.getOption("action").getAsString() + " " + event.getOption("value").getAsLong();
                    break;
                case "weather":
                    command = "weather " + event.getOption("type").getAsString();
                    if (event.getOption("duration") != null) {
                        command += " " + event.getOption("duration").getAsLong();
                    }
                    break;
                case "give":
                    command = "give " + event.getOption("player").getAsString() + " " + event.getOption("item").getAsString();
                    if (event.getOption("amount") != null) {
                        command += " " + event.getOption("amount").getAsLong();
                    }
                    break;
                case "tp":
                    command = "tp " + event.getOption("player").getAsString() + " " + event.getOption("target").getAsString();
                    break;
            }
            
            event.deferReply().queue();
            executeConsoleCommandSlash(event, command);
        }
        
        private void handleSessionSlashCommand(SlashCommandInteractionEvent event, String subcommand) {
            String userId = event.getUser().getId();
            
            if (subcommand.equals("start")) {
                if (activeSessions.containsKey(userId)) {
                    event.reply("You already have an active session!").setEphemeral(true).queue();
                    return;
                }
                
                ConsoleSession session = new ConsoleSession(userId, event.getUser().getName());
                activeSessions.put(userId, session);
                event.reply("Console session started. All your commands will be logged.").queue();
                
            } else if (subcommand.equals("stop")) {
                ConsoleSession session = activeSessions.remove(userId);
                if (session == null) {
                    event.reply("You don't have an active session!").setEphemeral(true).queue();
                    return;
                }
                
                // Save session log
                String sessionLog = session.generateLog();
                event.reply("Session ended. " + session.commands.size() + " commands executed.")
                    .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(
                        sessionLog.getBytes(), 
                        "console_session_" + System.currentTimeMillis() + ".txt"
                    ))
                    .queue();
            }
        }
    }
    
    private void handleSpecialCommand(MessageReceivedEvent event, String command) {
        String[] parts = command.split(" ", 2);
        String baseCommand = parts[0].toLowerCase();
        
        switch (baseCommand) {
            case "logs":
                sendRecentLogs(event);
                break;
            case "tps":
                sendTpsInfo(event);
                break;
            case "memory":
                sendMemoryInfo(event);
                break;
            case "players":
                sendPlayerList(event);
                break;
            case "plugins":
                sendPluginList(event);
                break;
            case "clear":
                clearConsoleChannel(event);
                break;
            case "session":
                if (parts.length > 1) {
                    handleSessionCommand(event, parts[1]);
                } else {
                    event.getMessage().reply("Usage: !session <start|stop> or /session <start|stop>").queue();
                }
                break;
            case "help":
                sendHelpMessage(event);
                break;
            // Minecraft server commands
            case "op":
            case "deop":
            case "ban":
            case "pardon":
            case "ban-ip":
            case "pardon-ip":
            case "kick":
            case "whitelist":
            case "gamemode":
            case "gamerule":
            case "difficulty":
            case "time":
            case "weather":
            case "give":
            case "tp":
            case "teleport":
            case "stop":
            case "restart":
            case "reload":
            case "save-all":
            case "save-on":
            case "save-off":
                // Execute as Minecraft command
                String fullCommand = baseCommand;
                if (parts.length > 1) {
                    fullCommand += " " + parts[1];
                }
                executeConsoleCommand(event, fullCommand);
                break;
            default:
                event.getMessage().reply("Unknown command. Use `!help` or `/help` to see available commands.").queue();
        }
    }
    
    private void executeConsoleCommand(MessageReceivedEvent event, String command) {
        // Add to session if active
        String userId = event.getAuthor().getId();
        ConsoleSession session = activeSessions.get(userId);
        if (session != null) {
            session.addCommand(command);
        }

        // Execute command
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Capture output
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                PrintStream old = System.out;
                System.setOut(ps);

                // Execute
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                // Restore original output
                System.out.flush();
                System.setOut(old);

                // Send reaction
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue();

                // Send output
                String output = baos.toString();
                if (!output.isEmpty()) {
                    event.getMessage().reply("```\n" + output + "\n```").queue();
                }

                // Log the command
                info("Console command executed by " + event.getAuthor().getName() + ": " + command);

            } catch (Exception e) {
                event.getMessage().reply("❌ Error executing command: " + e.getMessage()).queue();
                error("Error executing console command", e);
            }
        });
    }

    private void executeConsoleCommandSlash(SlashCommandInteractionEvent event, String command) {
        // Add to session if active
        String userId = event.getUser().getId();
        ConsoleSession session = activeSessions.get(userId);
        if (session != null) {
            session.addCommand(command);
        }

        // Execute command
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                // Capture output
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos);
                PrintStream old = System.out;
                System.setOut(ps);

                // Execute
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

                // Restore original output
                System.out.flush();
                System.setOut(old);

                // Send response
                String output = baos.toString();
                if (output.isEmpty()) {
                    event.getHook().editOriginal("✅ Done").queue();
                } else {
                    event.getHook().editOriginal("✅ Done\n```\n" + output + "\n```").queue();
                }

                // Log the command
                info("Console command executed by " + event.getUser().getName() + ": " + command);

            } catch (Exception e) {
                event.getHook().editOriginal("❌ Error executing command: " + e.getMessage()).queue();
                error("Error executing console command", e);
            }
        });
    }
    
    private void sendRecentLogs(MessageReceivedEvent event) {
        if (consoleHistory.isEmpty()) {
            event.getMessage().reply("No recent logs available.").queue();
            return;
        }
        
        List<String> recent = consoleHistory.subList(
            Math.max(0, consoleHistory.size() - 20),
            consoleHistory.size()
        );
        
        StringBuilder logs = new StringBuilder("**Recent Console Logs:**\n```ansi\n");
        for (String line : recent) {
            logs.append(convertToAnsi(line)).append("\n");
        }
        logs.append("```");
        
        event.getMessage().reply(logs.toString()).queue();
    }
    
    private void sendRecentLogsSlash(SlashCommandInteractionEvent event) {
        if (consoleHistory.isEmpty()) {
            event.reply("No recent logs available.").queue();
            return;
        }
        
        List<String> recent = consoleHistory.subList(
            Math.max(0, consoleHistory.size() - 20),
            consoleHistory.size()
        );
        
        StringBuilder logs = new StringBuilder("**Recent Console Logs:**\n```ansi\n");
        for (String line : recent) {
            logs.append(convertToAnsi(line)).append("\n");
        }
        logs.append("```");
        
        event.reply(logs.toString()).queue();
    }
    
    private void sendTpsInfo(MessageReceivedEvent event) {
        double tps = getTPS();
        String status = tps >= 19 ? "🟢" : tps >= 15 ? "🟡" : "🔴";
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(tps >= 19 ? Color.GREEN : tps >= 15 ? Color.YELLOW : Color.RED)
            .setTitle("Server Performance")
            .addField("TPS", status + " " + String.format("%.2f", tps) + "/20.0", true)
            .addField("MSPT", String.format("%.2f", 1000.0 / tps) + "ms", true)
            .setTimestamp(Instant.now());
        
        event.getMessage().replyEmbeds(embed.build()).queue();
    }
    
    private void sendTpsInfoSlash(SlashCommandInteractionEvent event) {
        double tps = getTPS();
        String status = tps >= 19 ? "🟢" : tps >= 15 ? "🟡" : "🔴";
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(tps >= 19 ? Color.GREEN : tps >= 15 ? Color.YELLOW : Color.RED)
            .setTitle("Server Performance")
            .addField("TPS", status + " " + String.format("%.2f", tps) + "/20.0", true)
            .addField("MSPT", String.format("%.2f", 1000.0 / tps) + "ms", true)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendMemoryInfo(MessageReceivedEvent event) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        double usagePercent = (double) usedMemory / maxMemory * 100;
        String status = usagePercent < 60 ? "🟢" : usagePercent < 80 ? "🟡" : "🔴";
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(usagePercent < 60 ? Color.GREEN : usagePercent < 80 ? Color.YELLOW : Color.RED)
            .setTitle("Memory Usage")
            .addField("Used", status + " " + usedMemory + " MB (" + String.format("%.1f", usagePercent) + "%)", true)
            .addField("Allocated", totalMemory + " MB", true)
            .addField("Maximum", maxMemory + " MB", true)
            .setTimestamp(Instant.now());
        
        event.getMessage().replyEmbeds(embed.build()).queue();
    }
    
    private void sendMemoryInfoSlash(SlashCommandInteractionEvent event) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        
        double usagePercent = (double) usedMemory / maxMemory * 100;
        String status = usagePercent < 60 ? "🟢" : usagePercent < 80 ? "🟡" : "🔴";
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(usagePercent < 60 ? Color.GREEN : usagePercent < 80 ? Color.YELLOW : Color.RED)
            .setTitle("Memory Usage")
            .addField("Used", status + " " + usedMemory + " MB (" + String.format("%.1f", usagePercent) + "%)", true)
            .addField("Allocated", totalMemory + " MB", true)
            .addField("Maximum", maxMemory + " MB", true)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendPlayerList(MessageReceivedEvent event) {
        Collection<? extends org.bukkit.entity.Player> players = Bukkit.getOnlinePlayers();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Online Players")
            .setDescription("**" + players.size() + "/" + Bukkit.getMaxPlayers() + " players online**")
            .setTimestamp(Instant.now());
        
        if (!players.isEmpty()) {
            String playerList = players.stream()
                .map(p -> "• " + p.getName() + " (" + p.getWorld().getName() + ")")
                .collect(Collectors.joining("\n"));
            
            embed.addField("Players", playerList, false);
        }
        
        event.getMessage().replyEmbeds(embed.build()).queue();
    }
    
    private void sendPlayerListSlash(SlashCommandInteractionEvent event) {
        Collection<? extends org.bukkit.entity.Player> players = Bukkit.getOnlinePlayers();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Online Players")
            .setDescription("**" + players.size() + "/" + Bukkit.getMaxPlayers() + " players online**")
            .setTimestamp(Instant.now());
        
        if (!players.isEmpty()) {
            String playerList = players.stream()
                .map(p -> "• " + p.getName() + " (" + p.getWorld().getName() + ")")
                .collect(Collectors.joining("\n"));
            
            embed.addField("Players", playerList, false);
        }
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendPluginList(MessageReceivedEvent event) {
        org.bukkit.plugin.Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        
        Map<Boolean, List<org.bukkit.plugin.Plugin>> grouped = Arrays.stream(plugins)
            .collect(Collectors.partitioningBy(org.bukkit.plugin.Plugin::isEnabled));
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.CYAN)
            .setTitle("Plugin List")
            .setDescription("**" + plugins.length + " plugins loaded**")
            .setTimestamp(Instant.now());
        
        if (!grouped.get(true).isEmpty()) {
            String enabled = grouped.get(true).stream()
                .map(p -> "• " + p.getName() + " v" + p.getDescription().getVersion())
                .collect(Collectors.joining("\n"));
            embed.addField("Enabled (" + grouped.get(true).size() + ")", enabled, false);
        }
        
        if (!grouped.get(false).isEmpty()) {
            String disabled = grouped.get(false).stream()
                .map(p -> "• " + p.getName() + " v" + p.getDescription().getVersion())
                .collect(Collectors.joining("\n"));
            embed.addField("Disabled (" + grouped.get(false).size() + ")", disabled, false);
        }
        
        event.getMessage().replyEmbeds(embed.build()).queue();
    }
    
    private void sendPluginListSlash(SlashCommandInteractionEvent event) {
        org.bukkit.plugin.Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        
        Map<Boolean, List<org.bukkit.plugin.Plugin>> grouped = Arrays.stream(plugins)
            .collect(Collectors.partitioningBy(org.bukkit.plugin.Plugin::isEnabled));
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.CYAN)
            .setTitle("Plugin List")
            .setDescription("**" + plugins.length + " plugins loaded**")
            .setTimestamp(Instant.now());
        
        if (!grouped.get(true).isEmpty()) {
            String enabled = grouped.get(true).stream()
                .map(p -> "• " + p.getName() + " v" + p.getDescription().getVersion())
                .collect(Collectors.joining("\n"));
            embed.addField("Enabled (" + grouped.get(true).size() + ")", enabled, false);
        }
        
        if (!grouped.get(false).isEmpty()) {
            String disabled = grouped.get(false).stream()
                .map(p -> "• " + p.getName() + " v" + p.getDescription().getVersion())
                .collect(Collectors.joining("\n"));
            embed.addField("Disabled (" + grouped.get(false).size() + ")", disabled, false);
        }
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void clearConsoleChannel(MessageReceivedEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        
        channel.getHistory().retrievePast(100).queue(messages -> {
            messages.forEach(msg -> msg.delete().queue());
            event.getMessage().reply("Console channel cleared!").queue(reply -> 
                reply.delete().queueAfter(5, TimeUnit.SECONDS));
        });
    }
    
    private void clearConsoleChannelSlash(SlashCommandInteractionEvent event) {
        TextChannel channel = event.getChannel().asTextChannel();
        
        event.reply("Clearing console channel...").setEphemeral(true).queue();
        
        channel.getHistory().retrievePast(100).queue(messages -> {
            messages.forEach(msg -> msg.delete().queue());
        });
    }
    
    private void handleSessionCommand(MessageReceivedEvent event, String action) {
        String userId = event.getAuthor().getId();
        
        if (action.equalsIgnoreCase("start")) {
            if (activeSessions.containsKey(userId)) {
                event.getMessage().reply("You already have an active session!").queue();
                return;
            }
            
            ConsoleSession session = new ConsoleSession(userId, event.getAuthor().getName());
            activeSessions.put(userId, session);
            event.getMessage().reply("Console session started. All your commands will be logged.").queue();
            
        } else if (action.equalsIgnoreCase("stop")) {
            ConsoleSession session = activeSessions.remove(userId);
            if (session == null) {
                event.getMessage().reply("You don't have an active session!").queue();
                return;
            }
            
            // Save session log
            String sessionLog = session.generateLog();
            event.getMessage().reply("Session ended. " + session.commands.size() + " commands executed.")
                .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(
                    sessionLog.getBytes(), 
                    "console_session_" + System.currentTimeMillis() + ".txt"
                ))
                .queue();
        }
    }
    
    private TextChannel getConsoleChannel() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return null;
        }
        
        return plugin.getDiscordManager().getMainGuild()
            .getTextChannelsByName(consoleChannelName, true)
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    private double getTPS() {
        try {
            Object minecraftServer = Bukkit.getServer().getClass()
                .getMethod("getServer").invoke(Bukkit.getServer());
            
            double[] recentTps = (double[]) minecraftServer.getClass()
                .getField("recentTps").get(minecraftServer);
            
            return recentTps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }
    
    private void sendHelpMessage(MessageReceivedEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("📚 Bot Console Commands")
            .setDescription("All commands support both `!` and `/` prefixes")
            .addField("🔧 Utility Commands",
                "`logs` - Show recent console logs\n" +
                "`tps` - Show server TPS\n" +
                "`memory` - Show memory usage\n" +
                "`players` - List online players\n" +
                "`plugins` - List server plugins\n" +
                "`clear` - Clear console channel\n" +
                "`session <start|stop>` - Start/stop command logging session",
                false)
            .addField("👑 Server Management (Owner Only)",
                "`op <player>` - Give operator status\n" +
                "`deop <player>` - Remove operator status\n" +
                "`ban <player> [reason]` - Ban a player\n" +
                "`pardon <player>` - Unban a player\n" +
                "`ban-ip <ip>` - Ban an IP address\n" +
                "`pardon-ip <ip>` - Unban an IP address\n" +
                "`kick <player> [reason]` - Kick a player\n" +
                "`whitelist <add|remove|on|off> [player]` - Manage whitelist",
                false)
            .addField("🎮 Game Commands",
                "`gamemode <mode> [player]` - Change gamemode\n" +
                "`gamerule <rule> [value]` - Set game rules\n" +
                "`difficulty <level>` - Set difficulty\n" +
                "`time <set|add> <value>` - Manage time\n" +
                "`weather <clear|rain|thunder> [duration]` - Set weather\n" +
                "`give <player> <item> [amount]` - Give items\n" +
                "`tp <player> <target>` - Teleport players",
                false)
            .addField("⚙️ Server Control",
                "`stop` - Stop the server\n" +
                "`restart` - Restart the server\n" +
                "`reload` - Reload server configuration\n" +
                "`save-all` - Force save all worlds\n" +
                "`save-on` - Enable auto-saving\n" +
                "`save-off` - Disable auto-saving",
                false)
            .addField("💡 Direct Commands",
                "You can also type any Minecraft command directly without a prefix to execute it as console.",
                false)
            .addField("🔷 Slash Commands",
                "Use Discord slash commands for better experience:\n" +
                "`/console` - Console utilities\n" +
                "`/server` - Server management\n" +
                "`/game` - Game commands\n" +
                "`/session` - Session management",
                false)
            .setFooter("Only users in adminIDs can use these commands", null)
            .setTimestamp(Instant.now());
        
        event.getMessage().replyEmbeds(embed.build()).queue();
    }
    
    private void sendHelpMessageSlash(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("Console Commands")
            .setDescription("Execute server commands from Discord.")
            .addField("`/console <command>`",
                "Run any Minecraft console command.\n" +
                "Examples: `/console list`, `/console ban Player`, `/console give Player diamond 64`",
                false)
            .addField("`/server`",
                "`logs`, `tps`, `memory`, `players`, `plugins`, `restart`, `stop`, `save`",
                false)
            .addField("`/game`",
                "`gamemode`, `difficulty`, `time`, `weather`, `give`, `tp`",
                false)
            .addField("`/session`",
                "`start`, `stop`",
                false)
            .setFooter("XDiscordUltimate • Admin only", null)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    /**
     * Custom log appender
     */
    private class ConsoleAppender extends AbstractAppender {
        protected ConsoleAppender() {
            super("XDiscordConsoleAppender", null, PatternLayout.createDefaultLayout(), true, Property.EMPTY_ARRAY);
        }
        
        @Override
        public void append(LogEvent event) {
            if (!enableLogStreaming) return;
            
            String message = event.getMessage().getFormattedMessage();
            Level level = event.getLevel();
            
            // Format message with level
            String formatted = String.format("[%s] %s", level.name(), message);
            
            // Add to queue
            consoleQueue.offer(formatted);
            
            // Add to history
            consoleHistory.add(formatted);
            if (consoleHistory.size() > 1000) {
                consoleHistory.remove(0);
            }
        }
    }
    
    /**
     * Console session tracking
     */
    private static class ConsoleSession {
        final String userId;
        final String userName;
        final long startTime;
        final List<CommandEntry> commands;
        
        ConsoleSession(String userId, String userName) {
            this.userId = userId;
            this.userName = userName;
            this.startTime = System.currentTimeMillis();
            this.commands = new ArrayList<>();
        }
        
        void addCommand(String command) {
            commands.add(new CommandEntry(command, System.currentTimeMillis()));
        }
        
        String generateLog() {
            StringBuilder log = new StringBuilder();
            log.append("Console Session Log\n");
            log.append("User: ").append(userName).append(" (").append(userId).append(")\n");
            log.append("Start Time: ").append(new Date(startTime)).append("\n");
            log.append("Duration: ").append(formatDuration(System.currentTimeMillis() - startTime)).append("\n");
            log.append("Commands Executed: ").append(commands.size()).append("\n");
            log.append("\n--- Command History ---\n\n");
            
            for (CommandEntry entry : commands) {
                log.append("[").append(new Date(entry.timestamp)).append("] ");
                log.append(entry.command).append("\n");
            }
            
            return log.toString();
        }
        
        private String formatDuration(long millis) {
            long seconds = millis / 1000;
            long minutes = seconds / 60;
            long hours = minutes / 60;
            
            return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
        }
    }
    
    private static class CommandEntry {
        final String command;
        final long timestamp;
        
        CommandEntry(String command, long timestamp) {
            this.command = command;
            this.timestamp = timestamp;
        }
    }
}