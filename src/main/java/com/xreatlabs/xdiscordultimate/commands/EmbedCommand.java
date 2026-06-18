package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.awt.Color;
import java.util.Arrays;

public class EmbedCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;

    public EmbedCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xdiscord.embed")) {
            plugin.getMessageManager().sendMessage(sender, "errors.no-permission");
            return true;
        }

        if (args.length < 3) {
            plugin.getMessageManager().sendError(sender, "Usage: /embed <channel> <title> <description>");
            return true;
        }

        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            plugin.getMessageManager().sendError(sender, "Discord bot is not connected!");
            return true;
        }

        String channelIdOrName = args[0];
        String title = args[1];
        String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        TextChannel channel = plugin.getDiscordManager().getTextChannelById(channelIdOrName);
        if (channel == null && plugin.getDiscordManager().getMainGuild() != null) {
            channel = plugin.getDiscordManager().getMainGuild()
                .getTextChannelsByName(channelIdOrName, true)
                .stream()
                .findFirst()
                .orElse(null);
        }

        if (channel == null) {
            plugin.getMessageManager().sendError(sender, "Channel not found: " + channelIdOrName);
            return true;
        }

        final TextChannel targetChannel = channel;

        String colorStr = plugin.getConfig().getString("messages.embed-color", "#5865F2");
        Color color;
        try {
            color = Color.decode(colorStr);
        } catch (NumberFormatException e) {
            color = Color.decode("#5865F2");
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(title)
            .setDescription(description.replace("&nl;", "\n"))
            .setColor(color)
            .setFooter("Sent by " + sender.getName(), null);

        targetChannel.sendMessageEmbeds(embed.build()).queue(
            success -> plugin.getMessageManager().sendSuccess(sender, "Embed sent to #" + targetChannel.getName()),
            error -> plugin.getMessageManager().sendError(sender, "Failed to send embed: " + error.getMessage())
        );

        return true;
    }
}
