package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.utils.MessageManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

public class DiscordConsoleCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;
    private final MessageManager messageManager;

    public DiscordConsoleCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Security check - require permission
        if (!sender.hasPermission("xdiscord.console")) {
            messageManager.sendError(sender, "You don't have permission to use this command!");
            return true;
        }
        
        if (args.length == 0) {
            messageManager.sendError(sender, "Usage: /dconsole <command>");
            return true;
        }

        if (sender instanceof ConsoleCommandSender) {
            String cmd = String.join(" ", args);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            return true;
        }

        // Create a new appender for this specific command execution
        final AbstractAppender appender = new AbstractAppender("DConsole-" + sender.getName() + "-" + System.currentTimeMillis(), null,
                PatternLayout.createDefaultLayout(), false, null) {
            @Override
            public void append(LogEvent event) {
                final String message = event.getMessage().getFormattedMessage();
                // We are in a logging thread, so we need to schedule the message sending
                // back on the main server thread.
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(message);
                });
            }
        };
        appender.start();

        final Logger rootLogger = (Logger) LogManager.getRootLogger();
        rootLogger.addAppender(appender);

        String cmd = String.join(" ", args);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

        // Schedule a task to remove the appender after a timeout
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            rootLogger.removeAppender(appender);
            appender.stop();
        }, 100L); // 5 seconds (5 * 20 ticks)

        return true;
    }
}
