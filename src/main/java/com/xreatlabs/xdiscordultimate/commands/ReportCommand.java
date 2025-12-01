package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.moderation.ModerationModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReportCommand implements CommandExecutor, TabCompleter {
    
    private final XDiscordUltimate plugin;
    
    public ReportCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "general.player-only");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if player has permission
        if (!player.hasPermission("xdiscord.report")) {
            plugin.getMessageManager().sendMessage(player, "errors.no-permission");
            return true;
        }
        
        // Check if player is verified
        if (!plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendError(player, "You must verify your Discord account to report players!");
            player.sendMessage(plugin.getMessageManager().colorize("&eUse &b/verify &ein Discord to get a verification code."));
            return true;
        }
        
        // Check if moderation feature is enabled
        if (!plugin.getConfigManager().isFeatureEnabled("moderation")) {
            plugin.getMessageManager().sendMessage(player, "errors.feature-disabled");
            return true;
        }
        
        // Check arguments
        if (args.length < 2) {
            plugin.getMessageManager().sendError(player, "Usage: /report <player> <reason>");
            return true;
        }
        
        String targetName = args[0];
        Player target = Bukkit.getPlayer(targetName);
        
        if (target == null) {
            plugin.getMessageManager().sendMessage(player, "errors.player-not-found", "%player%", targetName);
            return true;
        }
        
        // Check if reporting self
        if (target.equals(player)) {
            plugin.getMessageManager().sendMessage(player, "report.self-report");
            return true;
        }
        
        // Check if target is admin (optional protection)
        if (plugin.getAdminUtils().isAdmin(target)) {
            plugin.getMessageManager().sendError(player, "You cannot report server administrators!");
            return true;
        }
        
        // Get the moderation module
        ModerationModule moderationModule = plugin.getModuleManager().getModule(ModerationModule.class);
        if (moderationModule == null || !moderationModule.isEnabled()) {
            plugin.getMessageManager().sendError(player, "Moderation module is not available!");
            return true;
        }
        
        // Combine args for reason
        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        
        // Check for report cooldown
        int cooldownMinutes = plugin.getConfig().getInt("features.moderation.report-cooldown-minutes", 5);
        plugin.getDatabaseManager().hasRecentReport(player.getUniqueId(), cooldownMinutes)
            .thenAccept(hasRecent -> {
                if (hasRecent) {
                    plugin.getMessageManager().sendMessage(player, "report.cooldown");
                    return;
                }
                
                // Create the report
                createReport(player, target, reason);
            });
        
        return true;
    }
    
    /**
     * Create a report and send it to Discord
     */
    private void createReport(Player reporter, Player target, String reason) {
        plugin.getDatabaseManager().createReport(reporter.getUniqueId(), target.getUniqueId(), reason)
            .thenAccept(reportId -> {
                if (reportId > 0) {
                    // Send success message to reporter
                    java.util.Map<String, String> placeholders = new java.util.HashMap<>();
                    placeholders.put("%player%", target.getName());
                    placeholders.put("%reason%", reason);
                    placeholders.put("%id%", String.valueOf(reportId));
                    plugin.getMessageManager().sendMessage(reporter, "report.success", placeholders);
                    
                    // Send to Discord
                    sendReportToDiscord(reportId, reporter, target, reason);
                    
                    // Log the report
                    plugin.getLogger().info("Player " + reporter.getName() + " reported " + target.getName() + " for: " + reason);
                } else {
                    plugin.getMessageManager().sendError(reporter, "Failed to create report. Please try again.");
                }
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Error creating report: " + throwable.getMessage());
                plugin.getMessageManager().sendError(reporter, "An error occurred while creating the report.");
                return null;
            });
    }
    
    /**
     * Send report to Discord moderation channel
     */
    private void sendReportToDiscord(int reportId, Player reporter, Player target, String reason) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        
        // Try global moderation channel first
        String channelId = plugin.getConfig().getString("discord.channels.moderation", "");
        TextChannel channel = null;
        
        // Try by ID first
        if (channelId != null && !channelId.isEmpty() && channelId.matches("\\d+")) {
            channel = plugin.getDiscordManager().getTextChannelById(channelId);
        }
        
        // Fall back to module config
        if (channel == null) {
            String channelName = plugin.getConfig().getString("features.moderation.log-channel", "mod-logs");
            if (channelName != null && !channelName.isEmpty()) {
                // Try by ID if numeric
                if (channelName.matches("\\d+")) {
                    channel = plugin.getDiscordManager().getTextChannelById(channelName);
                }
                // Fall back to name lookup
                if (channel == null && plugin.getDiscordManager().getMainGuild() != null) {
                    channel = plugin.getDiscordManager().getMainGuild()
                        .getTextChannelsByName(channelName, true)
                        .stream()
                        .findFirst()
                        .orElse(null);
                }
            }
        }
        
        if (channel == null) {
            plugin.getLogger().warning("Moderation log channel not found! Configure discord.channels.moderation in config.yml");
            return;
        }
        
        // Get Discord IDs for additional info
        String reporterDiscordId = plugin.getAdminUtils().getDiscordId(reporter);
        String targetDiscordId = plugin.getAdminUtils().getDiscordId(target);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle("🚨 Player Report #" + reportId)
            .addField("Reporter",
                reporter.getName() + (reporterDiscordId != null ? " (<@" + reporterDiscordId + ">)" : ""),
                true)
            .addField("Reported Player",
                target.getName() + (targetDiscordId != null ? " (<@" + targetDiscordId + ">)" : ""),
                true)
            .addField("Server",
                target.getWorld().getName() + " (" + target.getLocation().getBlockX() + ", " +
                target.getLocation().getBlockY() + ", " + target.getLocation().getBlockZ() + ")",
                true)
            .addField("Reason", reason, false)
            .addField("Report ID", String.valueOf(reportId), true)
            .addField("Status", "🔍 Under Review", true)
            .setFooter("XDiscordUltimate Report System", null)
            .setTimestamp(Instant.now());
        
        channel.sendMessageEmbeds(embed.build()).queue(
            success -> plugin.getLogger().info("Report #" + reportId + " sent to Discord"),
            error -> plugin.getLogger().warning("Failed to send report to Discord: " + error.getMessage())
        );
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Suggest online player names
            completions.addAll(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> !name.equals(sender.getName())) // Exclude self
                .collect(Collectors.toList()));
        } else if (args.length == 2) {
            // Suggest common report reasons
            completions.addAll(Arrays.asList(
                "Cheating", "Griefing", "Spamming", "Harassment", 
                "Inappropriate", "Bug abuse", "Advertising"
            ));
        }
        
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}