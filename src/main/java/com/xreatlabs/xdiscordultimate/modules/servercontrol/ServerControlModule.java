package com.xreatlabs.xdiscordultimate.modules.servercontrol;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.List;

public class ServerControlModule extends Module {
    
    private ServerControlListener listener;
    
    private List<String> allowedCommands;
    private String controlChannelName;
    
    public ServerControlModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Server Control";
    }
    
    @Override
    public String getDescription() {
        return "Control server from Discord with admin protection";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Server control module enabled - waiting for Discord...");
    }
    
    @Override
    public void onDiscordReady() {
        // Register Discord listener
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            listener = new ServerControlListener();
            plugin.getDiscordManager().getJDA().addEventListener(listener);
            info("Server control connected to Discord");
        }
    }
    
    @Override
    protected void onDisable() {
        // Unregister Discord listener
        if (listener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(listener);
        }
        
        info("Server control module disabled");
    }
    
    private void loadConfiguration() {
        controlChannelName = plugin.getConfigManager().getChannelName("server-control");
        if (controlChannelName == null || controlChannelName.isEmpty()) {
            controlChannelName = "bot-console";
        }
        
        // Load allowed commands
        allowedCommands = Arrays.asList("restart", "stop", "kick", "tps", "list");
    }
    
    private class ServerControlListener extends ListenerAdapter {
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            // Ignore bot messages
            if (event.getAuthor().isBot()) return;
            
            // Check if message is in control channel
            if (!event.getChannel().getName().equalsIgnoreCase(controlChannelName)) {
                return;
            }
            
            // Check if user has permission
            String userId = event.getAuthor().getId();
            if (!plugin.getAdminUtils().canExecuteServerControl(userId)) {
                return;
            }
            
            String message = event.getMessage().getContentRaw();
            
            // Check for command prefix (! or /)
            String commandStr = getCommandFromMessage(message);
            if (commandStr == null) {
                return;
            }
            
            String command = commandStr.toLowerCase();
            String[] parts = command.split(" ");
            String baseCommand = parts[0];
            
            // Check if command is allowed
            if (!isCommandAllowed(baseCommand)) {
                event.getChannel().sendMessage("❌ Command not allowed: " + baseCommand).queue();
                return;
            }
            
            // Execute command
            executeCommand(event, baseCommand, parts);
        }
    }
    
    private boolean isCommandAllowed(String command) {
        return allowedCommands.contains(command) && 
               plugin.getConfig().getBoolean("features.server-control.allowed-commands." + command, false);
    }
    
    private void executeCommand(MessageReceivedEvent event, String command, String[] args) {
        switch (command) {
            case "restart":
                handleRestart(event);
                break;
            case "stop":
                handleStop(event);
                break;
            case "kick":
                handleKick(event, args);
                break;
            case "tps":
                handleTPS(event);
                break;
            case "list":
                handleList(event);
                break;
        }
    }
    
    private void handleRestart(MessageReceivedEvent event) {
        event.getChannel().sendMessage("🔄 Server restart initiated by " + event.getAuthor().getAsMention()).queue();
        
        // Broadcast restart message
        java.util.Map<String, String> placeholders = new java.util.HashMap<>();
        placeholders.put("%time%", "30");
        plugin.getMessageManager().broadcast("server-control.restart-scheduled", placeholders);
        
        // Schedule restart
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
        }, 20L * 30); // 30 seconds
    }
    
    private void handleStop(MessageReceivedEvent event) {
        event.getChannel().sendMessage("🛑 Server shutdown initiated by " + event.getAuthor().getAsMention()).queue();
        
        plugin.getMessageManager().broadcast("server-control.stopping");
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.shutdown();
        }, 20L * 5); // 5 seconds
    }
    
    private void handleKick(MessageReceivedEvent event, String[] args) {
        if (args.length < 2) {
            event.getChannel().sendMessage("❌ Usage: !kick <player> [reason] or /kick <player> [reason]").queue();
            return;
        }
        
        String playerName = args[1];
        String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Kicked by Discord admin";
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            org.bukkit.entity.Player player = Bukkit.getPlayer(playerName);
            if (player != null) {
                player.kickPlayer(reason);
                event.getChannel().sendMessage("✅ Kicked player: " + playerName).queue();
            } else {
                event.getChannel().sendMessage("❌ Player not found: " + playerName).queue();
            }
        });
    }
    
    private void handleTPS(MessageReceivedEvent event) {
        double tps = getTPS();
        String status = tps >= 19 ? "🟢" : tps >= 15 ? "🟡" : "🔴";
        event.getChannel().sendMessage(status + " Current TPS: " + String.format("%.2f", tps)).queue();
    }
    
    private void handleList(MessageReceivedEvent event) {
        java.util.Collection<? extends org.bukkit.entity.Player> players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            event.getChannel().sendMessage("📊 No players online").queue();
            return;
        }
        
        StringBuilder list = new StringBuilder("📊 Online players (" + players.size() + "):\n```\n");
        for (org.bukkit.entity.Player player : players) {
            list.append("• ").append(player.getName()).append("\n");
        }
        list.append("```");
        
        event.getChannel().sendMessage(list.toString()).queue();
    }
    
    private double getTPS() {
        try {
            Object server = plugin.getServer().getClass().getMethod("getServer").invoke(plugin.getServer());
            double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return tps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }
    
    protected String getCommandFromMessage(String message) {
        if (message.startsWith("!")) {
            return message.substring(1);
        } else if (message.startsWith("/")) {
            return message.substring(1);
        }
        return null;
    }
}