package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class DiscordCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;

    public DiscordCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String inviteUrl = plugin.getConfig().getString("discord.invite-url", "");
        String guildName = plugin.getConfig().getString("discord.guild-name", "Discord Server");

        if (inviteUrl == null || inviteUrl.isEmpty() || !inviteUrl.startsWith("http")) {
            sender.sendMessage(ColorUtils.parse("<gradient:#FF5555:#FFAA00>Discord invite not configured!</gradient> &7Ask an admin to set &ediscord.invite-url&7 in config.yml."));
            return true;
        }

        sender.sendMessage(ColorUtils.parse("<gradient:#55FFFF:#5555FF>━━━ Discord ━━━</gradient>"));
        sender.sendMessage(ColorUtils.parse("&7Join our Discord: &f" + guildName));
        sender.sendMessage(ColorUtils.parse("&e" + inviteUrl));
        return true;
    }
}
