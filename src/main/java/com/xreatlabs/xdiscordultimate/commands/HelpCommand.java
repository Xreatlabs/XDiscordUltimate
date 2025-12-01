package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import com.xreatlabs.xdiscordultimate.utils.HelpGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class HelpCommand implements CommandExecutor, TabCompleter {
    
    private final XDiscordUltimate plugin;
    
    public HelpCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    private HelpGUI getHelpGUI() {
        return plugin.getHelpGUI();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            // Console help - show text-based help
            showConsoleHelp(sender);
            return true;
        }
        
        Player player = (Player) sender;
        
        // Check if args specify a category
        if (args.length > 0) {
            String category = args[0].toLowerCase();
            switch (category) {
                case "discord":
                case "verification":
                    getHelpGUI().openDiscordHelp(player);
                    break;
                case "report":
                case "moderation":
                    getHelpGUI().openModerationHelp(player);
                    break;
                case "support":
                case "ticket":
                case "tickets":
                    getHelpGUI().openSupportHelp(player);
                    break;
                case "admin":
                case "console":
                    if (plugin.getAdminUtils().isAdmin(player)) {
                        getHelpGUI().openAdminHelp(player);
                    } else {
                        plugin.getMessageManager().sendError(player, "You don't have permission to view admin commands!");
                    }
                    break;
                case "voice":
                case "tts":
                    getHelpGUI().openVoiceHelp(player);
                    break;
                default:
                    getHelpGUI().openMainHelp(player);
                    break;
            }
        } else {
            // Open main help GUI
            getHelpGUI().openMainHelp(player);
        }
        
        return true;
    }
    
    private void showConsoleHelp(CommandSender sender) {
        sender.sendMessage(color("<gradient:#55FFFF:#5555FF>━━━ XDiscordUltimate Help ━━━</gradient>"));
        sender.sendMessage(color("&7This plugin integrates Discord with Minecraft!"));
        sender.sendMessage("");
        sender.sendMessage(color("<gradient:#55FF55:#55FFFF>Main Commands:</gradient>"));
        sender.sendMessage(color("&8▸ &a/verify <code> &8- &7Link your Discord account"));
        sender.sendMessage(color("&8▸ &a/report <player> <reason> &8- &7Report a player"));
        sender.sendMessage(color("&8▸ &a/support <message> &8- &7Create a support ticket"));
        sender.sendMessage(color("&8▸ &a/discord &8- &7Get Discord server invite"));
        sender.sendMessage("");
        sender.sendMessage(color("<gradient:#FF5555:#FFAA00>Admin Commands:</gradient>"));
        sender.sendMessage(color("&8▸ &e/xdiscord reload &8- &7Reload configuration"));
        sender.sendMessage(color("&8▸ &e/xdiscord info &8- &7Show plugin information"));
        sender.sendMessage(color("&8▸ &e/dconsole &8- &7Toggle Discord console"));
        sender.sendMessage("");
        sender.sendMessage(color("&7For detailed help, use &b/help &7in-game!"));
    }
    
    private String color(String message) {
        return ColorUtils.parse(message);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> categories = Arrays.asList(
                "discord", "verification", "report", "moderation", 
                "support", "ticket", "voice", "tts"
            );
            
            // Add admin category if player has permission
            if (sender instanceof Player && plugin.getAdminUtils().isAdmin((Player) sender)) {
                categories = new ArrayList<>(categories);
                categories.addAll(Arrays.asList("admin", "console"));
            }
            
            return categories.stream()
                .filter(cat -> cat.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        
        return new ArrayList<>();
    }
}