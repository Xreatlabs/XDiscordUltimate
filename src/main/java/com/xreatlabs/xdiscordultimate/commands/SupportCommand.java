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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

        if (!plugin.getConfigManager().isFeatureEnabled("tickets")) {
            plugin.getMessageManager().sendMessage(player, "errors.feature-disabled");
            return true;
        }

        if (!plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendMessage(player, "errors.discord-not-linked");
            return true;
        }

        if (args.length == 0) {
            plugin.getMessageManager().sendMessage(player, "support.no-message");
            plugin.getMessageManager().sendInfo(player, "support.usage");
            return true;
        }

        TicketModule ticketModule = plugin.getModuleManager().getModule(TicketModule.class);
        if (ticketModule == null || !ticketModule.isEnabled()) {
            plugin.getMessageManager().sendError(player, "errors.module-not-available");
            return true;
        }

        if (!player.hasPermission("xdiscord.support")) {
            plugin.getMessageManager().sendMessage(player, "errors.no-permission");
            return true;
        }

        plugin.getDatabaseManager().getOpenTicketsCount(player.getUniqueId())
            .thenAccept(openCount -> {
                int maxTickets = plugin.getConfig().getInt("features.tickets.max-open-tickets", 3);
                if (openCount >= maxTickets) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%max%", String.valueOf(maxTickets));
                    plugin.getMessageManager().sendError(player, "errors.max-tickets", placeholders);
                    plugin.getMessageManager().sendRawMessage(player, "&7Please wait for current tickets to be resolved.");
                    return;
                }

                String message = String.join(" ", args);

                ticketModule.createTicket(player, message);

                plugin.getMessageManager().sendSuccess(player, "success.ticket-created");
                plugin.getMessageManager().sendRawMessage(player, "&7Check Discord for updates on your ticket.");
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Error checking open tickets count: " + throwable.getMessage());
                plugin.getMessageManager().sendError(player, "errors.ticket-create-failed");
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