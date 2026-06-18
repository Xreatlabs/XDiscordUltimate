package com.xreatlabs.xdiscordultimate.modules.adminalerts;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AdminAlertsModule extends Module implements Listener {
    
    private String alertsChannelName;
    private boolean alertSuspiciousCommands;
    private boolean alertOppedPlayers;
    private boolean alertResourceUsage;
    private boolean alertErrorLogs;
    private boolean alertBlockMonitoring;
    private boolean alertMultipleLogins;
    
    // Monitored blocks
    private Set<Material> monitoredBlocks;
    private List<String> suspiciousCommands;
    
    // Resource monitoring
    private double tpsThreshold;
    private double memoryThreshold;
    
    // Multiple login tracking
    private final Map<String, Set<String>> ipToPlayers = new ConcurrentHashMap<>();
    private final Map<String, String> playerToIp = new ConcurrentHashMap<>();
    
    // Colors
    private final Color alertColor = new Color(255, 0, 0);
    private final Color warningColor = new Color(255, 165, 0);
    private final Color infoColor = new Color(0, 123, 255);
    
    public AdminAlertsModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Admin Alerts";
    }
    
    @Override
    public String getDescription() {
        return "Sends important server alerts and monitoring information to administrators";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        
        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Start monitoring tasks
        if (alertResourceUsage) {
            startResourceMonitoring();
        }
        
        info("Admin alerts module enabled");
    }
    
    @Override
    protected void onDisable() {
        // Unregister events
        PlayerJoinEvent.getHandlerList().unregister(this);
        PlayerCommandPreprocessEvent.getHandlerList().unregister(this);
        BlockBreakEvent.getHandlerList().unregister(this);
        BlockPlaceEvent.getHandlerList().unregister(this);
        ServerCommandEvent.getHandlerList().unregister(this);
        
        info("Admin alerts module disabled");
    }
    
    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        
        if (config != null) {
            alertsChannelName = config.getString("alert-channel", "admin-alerts");
            tpsThreshold = config.getDouble("tps-threshold", 15.0);
            memoryThreshold = config.getDouble("ram-threshold", 90.0);
            alertResourceUsage = config.getBoolean("dm-alerts", true);
        } else {
            warning("Config section not found, using defaults");
            alertsChannelName = "admin-alerts";
            tpsThreshold = 15.0;
            memoryThreshold = 90.0;
            alertResourceUsage = true;
        }
        
        // Default settings
        alertSuspiciousCommands = true;
        alertOppedPlayers = true;
        alertErrorLogs = true;
        alertBlockMonitoring = true;
        alertMultipleLogins = true;
        
        // Default monitored blocks
        monitoredBlocks = new HashSet<>();
        monitoredBlocks.add(Material.TNT);
        monitoredBlocks.add(Material.BEDROCK);
        monitoredBlocks.add(Material.COMMAND_BLOCK);
        monitoredBlocks.add(Material.BARRIER);
        
        // Default suspicious commands
        suspiciousCommands = Arrays.asList(
            "op", "deop", "stop", "reload", "restart",
            "ban", "ban-ip", "pardon", "whitelist",
            "gamemode", "gamerule", "difficulty"
        );
        
        debug("Alert channel: " + alertsChannelName);
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress().getAddress().getHostAddress();
        
        // Check for OP status
        if (alertOppedPlayers && player.isOp()) {
            sendAlert("OP Player Joined", 
                "**" + player.getName() + "** joined with OP permissions!", 
                alertColor);
        }
        
        // Track multiple logins
        if (alertMultipleLogins) {
            String previousIp = playerToIp.put(player.getName(), ip);
            
            // Check if IP changed
            if (previousIp != null && !previousIp.equals(ip)) {
                sendAlert("IP Change Detected", 
                    "**" + player.getName() + "** logged in from a different IP!\n" +
                    "Previous: `" + previousIp + "`\n" +
                    "Current: `" + ip + "`", 
                    warningColor);
            }
            
            // Track players per IP
            ipToPlayers.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(player.getName());
            
            // Check for multiple accounts
            Set<String> playersOnIp = ipToPlayers.get(ip);
            if (playersOnIp.size() > 1) {
                sendAlert("Multiple Accounts Detected", 
                    "IP `" + ip + "` has multiple accounts:\n" +
                    playersOnIp.stream().map(name -> "• " + name).collect(Collectors.joining("\n")), 
                    warningColor);
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!alertSuspiciousCommands || event.isCancelled()) return;
        
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1); // Remove leading /
        String baseCommand = command.split(" ")[0].toLowerCase();
        
        // Check if command is suspicious
        if (suspiciousCommands.contains(baseCommand)) {
            sendAlert("Suspicious Command", 
                "**" + player.getName() + "** executed: `/" + command + "`\n" +
                "Location: " + formatLocation(player.getLocation()), 
                alertColor);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!alertSuspiciousCommands) return;
        
        String command = event.getCommand();
        String baseCommand = command.split(" ")[0].toLowerCase();
        
        // Check if command is suspicious
        if (suspiciousCommands.contains(baseCommand)) {
            sendAlert("Console Command", 
                "Console executed: `" + command + "`", 
                warningColor);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!alertBlockMonitoring || event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        if (monitoredBlocks.contains(blockType)) {
            sendAlert("Monitored Block Broken", 
                "**" + player.getName() + "** broke **" + blockType.name() + "**\n" +
                "Location: " + formatLocation(event.getBlock().getLocation()), 
                warningColor);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!alertBlockMonitoring || event.isCancelled()) return;
        
        Player player = event.getPlayer();
        Material blockType = event.getBlock().getType();
        
        if (monitoredBlocks.contains(blockType)) {
            sendAlert("Monitored Block Placed", 
                "**" + player.getName() + "** placed **" + blockType.name() + "**\n" +
                "Location: " + formatLocation(event.getBlock().getLocation()), 
                warningColor);
        }
    }
    
    private void startResourceMonitoring() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check TPS
                double tps = getTPS();
                if (tps < tpsThreshold) {
                    sendAlert("Low TPS Warning", 
                        "Server TPS has dropped to **" + String.format("%.2f", tps) + "**\n" +
                        "Players online: " + Bukkit.getOnlinePlayers().size(), 
                        alertColor);
                }
                
                // Check memory usage
                Runtime runtime = Runtime.getRuntime();
                long maxMemory = runtime.maxMemory() / 1024 / 1024;
                long totalMemory = runtime.totalMemory() / 1024 / 1024;
                long freeMemory = runtime.freeMemory() / 1024 / 1024;
                long usedMemory = totalMemory - freeMemory;
                double memoryPercent = (double) usedMemory / maxMemory * 100;
                
                if (memoryPercent > memoryThreshold) {
                    sendAlert("High Memory Usage", 
                        "Memory usage: **" + String.format("%.1f", memoryPercent) + "%**\n" +
                        "Used: " + usedMemory + "MB / " + maxMemory + "MB", 
                        alertColor);
                }
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60 * 5); // Check every 5 minutes
    }
    
    /**
     * Send an error log alert
     */
    public void sendErrorAlert(String error, String details) {
        if (!alertErrorLogs) return;
        
        sendAlert("Error Detected", 
            "**Error:** " + error + "\n" +
            "**Details:** " + details, 
            alertColor);
    }
    
    /**
     * Send a custom alert
     */
    public void sendCustomAlert(String title, String message, Color color) {
        sendAlert(title, message, color);
    }
    
    private void sendAlert(String title, String message, Color color) {
        TextChannel channel = getAlertsChannel();
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color)
            .setTitle(title)
            .setDescription(message)
            .setTimestamp(Instant.now())
            .setFooter("Admin Alert System", null);

        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    private TextChannel getAlertsChannel() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return null;
        }
        
        if (alertsChannelName == null || alertsChannelName.isEmpty()) {
            return null;
        }
        
        // Try by ID first (if numeric)
        if (alertsChannelName.matches("\\d+")) {
            TextChannel channel = plugin.getDiscordManager().getTextChannelById(alertsChannelName);
            if (channel != null) {
                return channel;
            }
        }
        
        net.dv8tion.jda.api.entities.Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return null;
        
        return guild.getTextChannelsByName(alertsChannelName, true)
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    private String formatLocation(Location loc) {
        return String.format("%s: %d, %d, %d",
            loc.getWorld().getName(),
            loc.getBlockX(),
            loc.getBlockY(),
            loc.getBlockZ()
        );
    }
    
    private double getTPS() {
        try {
            // Use reflection to get TPS from Minecraft server
            Object minecraftServer = Bukkit.getServer().getClass()
                .getMethod("getServer").invoke(Bukkit.getServer());
            
            double[] recentTps = (double[]) minecraftServer.getClass()
                .getField("recentTps").get(minecraftServer);
            
            return recentTps[0]; // 1 minute average
        } catch (Exception e) {
            // Fallback method - estimate based on tick timing
            return 20.0; // Assume normal TPS if we can't get actual value
        }
    }
}