package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.tickets.TicketModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SupportCommand implements CommandExecutor, TabCompleter {
    
    private final XDiscordUltimate plugin;
    
    public SupportCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            plugin.getMessageManager().sendMessage(sender, "general.player-only");
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if tickets feature is enabled
        if (!plugin.getConfigManager().isFeatureEnabled("tickets")) {
            plugin.getMessageManager().sendMessage(player, "errors.feature-disabled");
            return true;
        }
        
        // Check if player is verified
        if (!plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendMessage(player, "errors.discord-not-linked");
            return true;
        }
        
        // Check if player provided a message
        if (args.length == 0) {
            plugin.getMessageManager().sendMessage(player, "support.no-message");
            plugin.getMessageManager().sendInfo(player, "Usage: /support <your message>");
            return true;
        }
        
        // Get the ticket module
        TicketModule ticketModule = plugin.getModuleManager().getModule(TicketModule.class);
        if (ticketModule == null || !ticketModule.isEnabled()) {
            plugin.getMessageManager().sendError(player, "Ticket module is not available!");
            return true;
        }
        
        // Check if player has permission
        if (!player.hasPermission("xdiscord.support")) {
            plugin.getMessageManager().sendMessage(player, "errors.no-permission");
            return true;
        }
        
        // Check if player has too many open tickets
        plugin.getDatabaseManager().getOpenTicketsCount(player.getUniqueId())
            .thenAccept(openCount -> {
                int maxTickets = plugin.getConfig().getInt("features.tickets.max-open-tickets", 3);
                if (openCount >= maxTickets) {
                    plugin.getMessageManager().sendError(player,
                        "You have reached the maximum number of open tickets (" + maxTickets + ")");
                    plugin.getMessageManager().sendRawMessage(player, "&7Please wait for current tickets to be resolved.");
                    return;
                }
                
                // Combine args into message
                String message = String.join(" ", args);
                
                // Create ticket
                ticketModule.createTicket(player, message);
                
                // Send confirmation
                plugin.getMessageManager().sendRawMessage(player, "<gradient:#55FF55:#55FFFF>✅ Support ticket created!</gradient>");
                plugin.getMessageManager().sendRawMessage(player, "&7Your ticket will be handled by our support team.");
                plugin.getMessageManager().sendRawMessage(player, "&7Check Discord for updates on your ticket.");
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Error checking open tickets count: " + throwable.getMessage());
                plugin.getMessageManager().sendError(player, "An error occurred while creating your ticket.");
                return null;
            });
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            // Suggest common support topics
            completions.addAll(Arrays.asList(
                "I need help with",
                "Bug report:",
                "Question about",
                "Problem with",
                "Request for",
                "Cannot access",
                "Lost items",
                "Griefing report"
            ));
        }
        
        return completions;
    }
}