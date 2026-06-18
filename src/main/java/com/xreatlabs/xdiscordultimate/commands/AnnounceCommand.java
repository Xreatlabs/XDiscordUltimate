package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.announcements.AnnouncementModule;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AnnounceCommand implements CommandExecutor {

    private final XDiscordUltimate plugin;

    public AnnounceCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xdiscord.announce")) {
            plugin.getMessageManager().sendMessage(sender, "errors.no-permission");
            return true;
        }

        if (!plugin.getConfigManager().isFeatureEnabled("announcements")) {
            plugin.getMessageManager().sendMessage(sender, "errors.feature-disabled");
            return true;
        }

        AnnouncementModule announcementModule = plugin.getModuleManager().getModule(AnnouncementModule.class);
        if (announcementModule == null || !announcementModule.isEnabled()) {
            plugin.getMessageManager().sendError(sender, "Announcement module is not available!");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "test":
                handleTest(sender, announcementModule);
                break;
            case "list":
                if (sender instanceof Player) {
                    announcementModule.listAnnouncements((Player) sender);
                } else {
                    sender.sendMessage("Use /announcement list in Discord to view configured announcements.");
                }
                break;
            case "help":
                sendHelp(sender, label);
                break;
            default:
                String message = String.join(" ", args);
                if (sender instanceof Player) {
                    announcementModule.makeAnnouncement((Player) sender, "Announcement", message);
                } else {
                    announcementModule.makeAnnouncement(null, "Announcement", message);
                }
                plugin.getMessageManager().sendMessage(sender, "announcement.sent");
                break;
        }
        return true;
    }

    private void handleTest(CommandSender sender, AnnouncementModule module) {
        sender.sendMessage(ColorUtils.parse("<gradient:#55FF55:#00AA00>Sending a test announcement...</gradient>"));
        String testMessage = "This is a test announcement from XDiscordUltimate. If you can see this in Minecraft chat and the configured Discord channel, the announcement module is working correctly!";
        if (sender instanceof Player) {
            module.makeAnnouncement((Player) sender, "Test Announcement", testMessage);
        } else {
            module.makeAnnouncement(null, "Test Announcement", testMessage);
        }
        sender.sendMessage(ColorUtils.parse("&7Check both the in-game chat and the configured Discord channel."));
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.parse("<gradient:#55FFFF:#5555FF>━━━ Announce Commands ━━━</gradient>"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " <message> &8- &7Broadcast an announcement"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " test &8- &7Send a test announcement (MC + Discord)"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " list &8- &7List configured scheduled announcements"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " help &8- &7Show this help"));
    }
}
