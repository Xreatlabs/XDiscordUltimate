package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.stats.PlayerStatsModule;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StatsCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;

    public StatsCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        PlayerStatsModule statsModule = plugin.getModuleManager().getModule(PlayerStatsModule.class);
        
        if (statsModule == null || !statsModule.isEnabled()) {
            sender.sendMessage(ColorUtils.parse("<gradient:#FF5555:#AA0000>Error!</gradient> &7Stats module is not enabled!"));
            return true;
        }

        String targetName;
        
        if (args.length > 0) {
            targetName = args[0];
        } else if (sender instanceof Player) {
            targetName = sender.getName();
        } else {
            sender.sendMessage(ColorUtils.parse("<gradient:#FF5555:#FFAA00>Usage:</gradient> &e/stats <player>"));
            return true;
        }

        PlayerStatsModule.PlayerStats stats = statsModule.getStats(targetName);
        String message = statsModule.formatStatsMessage(stats);
        sender.sendMessage(message);

        return true;
    }
}
