package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XDiscordCommand implements CommandExecutor, TabCompleter {
    
    private final XDiscordUltimate plugin;
    
    public XDiscordCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xdiscord.admin")) {
            plugin.getMessageManager().sendMessage(sender, "errors.no-permission");
            return true;
        }
        
        if (args.length == 0) {
            showHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "reload":
                handleReload(sender);
                break;
                
            case "status":
                handleStatus(sender);
                break;
                
            case "help":
                if (args.length > 1) {
                    showCommandHelp(sender, args[1]);
                } else {
                    showHelp(sender);
                }
                break;
                
            case "modules":
                handleModules(sender);
                break;
                
            case "debug":
                handleDebug(sender, args);
                break;
                
            case "admin":
                handleAdmin(sender, args);
                break;
                
            default:
                plugin.getMessageManager().sendError(sender, "Unknown subcommand: " + subCommand);
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void handleReload(CommandSender sender) {
        plugin.getMessageManager().sendInfo(sender, "Reloading configuration...");
        
        try {
            plugin.reload();
            plugin.getMessageManager().sendMessage(sender, "general.reload-success");
        } catch (Exception e) {
            plugin.getMessageManager().sendMessage(sender, "general.reload-failed");
            e.printStackTrace();
        }
    }
    
    private void handleStatus(CommandSender sender) {
        sender.sendMessage(color("<gradient:#55FFFF:#5555FF>━━━ XDiscordUltimate Status ━━━</gradient>"));
        sender.sendMessage("");
        
        // Plugin version
        sender.sendMessage(color("&8▸ <gradient:#55FFFF:#5555FF>Version</gradient>&8: &f" + plugin.getDescription().getVersion()));
        
        // Discord connection status
        boolean discordConnected = plugin.getDiscordManager() != null &&
                                 plugin.getDiscordManager().getJDA() != null &&
                                 plugin.getDiscordManager().getJDA().getStatus().name().equals("CONNECTED");
        sender.sendMessage(color("&8▸ <gradient:#5555FF:#7289DA>Discord</gradient>&8: " + (discordConnected ? "&a● Connected" : "&c● Disconnected")));
        
        // Database status
        boolean databaseConnected = plugin.getDatabaseManager() != null;
        sender.sendMessage(color("&8▸ <gradient:#55FF55:#00AA00>Database</gradient>&8: " + (databaseConnected ? "&a● Connected" : "&c● Disconnected")));
        
        // Module status
        Map<String, Module> modules = plugin.getModuleManager().getModules();
        long enabledModules = modules.values().stream().filter(Module::isEnabled).count();
        sender.sendMessage(color("&8▸ <gradient:#FFAA00:#FF5555>Modules</gradient>&8: &e" + enabledModules + "&7/&f" + modules.size() + " &7enabled"));
        
        // Memory usage
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;
        sender.sendMessage(color("&8▸ <gradient:#AA00AA:#FF55FF>Memory</gradient>&8: &e" + usedMemory + "MB &7/ &f" + maxMemory + "MB"));
        
        // Player stats
        int onlinePlayers = plugin.getServer().getOnlinePlayers().size();
        sender.sendMessage(color("&8▸ <gradient:#55FF55:#55FFFF>Players</gradient>&8: &e" + onlinePlayers + " &7online"));
        sender.sendMessage("");
    }
    
    private void handleModules(CommandSender sender) {
        sender.sendMessage(color("<gradient:#55FFFF:#5555FF>━━━ XDiscordUltimate Modules ━━━</gradient>"));
        sender.sendMessage("");
        
        Map<String, Module> modules = plugin.getModuleManager().getModules();
        
        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            Module module = entry.getValue();
            
            String status = module.isEnabled() ? "&a●" : "&c●";
            sender.sendMessage(color(status + " <gradient:#55FFFF:#5555FF>" + module.getName() + "</gradient>"));
            sender.sendMessage(color("  &8" + module.getDescription()));
        }
        sender.sendMessage("");
    }
    
    private void handleDebug(CommandSender sender, String[] args) {
        if (args.length < 2) {
            boolean debugEnabled = plugin.getConfigManager().isDebugEnabled();
            sender.sendMessage(color("&8▸ <gradient:#FFAA00:#FF5555>Debug</gradient>&8: " + 
                             (debugEnabled ? "&a● Enabled" : "&c● Disabled")));
            return;
        }
        
        String action = args[1].toLowerCase();
        
        if (action.equals("on") || action.equals("enable")) {
            plugin.getConfigManager().set("general.debug", true);
            plugin.getMessageManager().sendSuccess(sender, "Debug mode enabled");
        } else if (action.equals("off") || action.equals("disable")) {
            plugin.getConfigManager().set("general.debug", false);
            plugin.getMessageManager().sendSuccess(sender, "Debug mode disabled");
        } else {
            plugin.getMessageManager().sendError(sender, "Usage: /xdiscord debug <on|off>");
        }
    }
    
    private void handleAdmin(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getMessageManager().sendError(sender, "Usage: /xdiscord admin <add|remove> <discord-id>");
            return;
        }
        
        String action = args[1].toLowerCase();
        String discordId = args[2];
        
        // Validate Discord ID format
        if (!discordId.matches("\\d{17,20}")) {
            plugin.getMessageManager().sendError(sender, "Invalid Discord ID format!");
            return;
        }
        
        if (action.equals("add")) {
            plugin.getAdminUtils().addAdmin(discordId);
            plugin.getMessageManager().sendSuccess(sender, "Added Discord ID " + discordId + " as admin");
        } else if (action.equals("remove")) {
            plugin.getAdminUtils().removeAdmin(discordId);
            plugin.getMessageManager().sendSuccess(sender, "Removed Discord ID " + discordId + " from admins");
        } else {
            plugin.getMessageManager().sendError(sender, "Usage: /xdiscord admin <add|remove> <discord-id>");
        }
    }
    
    private void showHelp(CommandSender sender) {
        plugin.getMessageManager().sendMessageList(sender, "help.header");
        
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord reload &8- &7Reload configuration"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord status &8- &7Show plugin status"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord modules &8- &7List all modules"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord debug <on|off> &8- &7Toggle debug"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord admin <add|remove> <id> &8- &7Manage admins"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord help <cmd> &8- &7Command help"));
        
        plugin.getMessageManager().sendMessageList(sender, "help.footer");
    }
    
    private void showCommandHelp(CommandSender sender, String command) {
        // Show detailed help for specific commands
        switch (command.toLowerCase()) {
            case "verify":
                sender.sendMessage(color("<gradient:#55FFFF:#5555FF>/verify</gradient> &8- &7" + 
                    plugin.getMessageManager().getMessage("commands.verify")));
                sender.sendMessage(color("&8  Links your Minecraft account with Discord."));
                break;
                
            case "support":
                sender.sendMessage(color("<gradient:#55FFFF:#5555FF>/support <message></gradient> &8- &7" + 
                    plugin.getMessageManager().getMessage("commands.support")));
                sender.sendMessage(color("&8  Creates a support ticket for staff."));
                break;
                
            case "embed":
                sender.sendMessage(color("<gradient:#55FFFF:#5555FF>/embed <channel> <title> <desc></gradient>"));
                sender.sendMessage(color("&8  Sends embed to Discord. Requires: &exdiscord.embed"));
                break;
                
            case "announce":
                sender.sendMessage(color("<gradient:#55FFFF:#5555FF>/announce <message></gradient>"));
                sender.sendMessage(color("&8  Broadcasts to Discord & in-game. Requires: &exdiscord.announce"));
                break;
                
            default:
                plugin.getMessageManager().sendError(sender, "No help available for: " + command);
                break;
        }
    }
    
    private String color(String message) {
        return ColorUtils.parse(message);
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("xdiscord.admin")) {
            return new ArrayList<>();
        }
        
        List<String> completions = new ArrayList<>();
        
        if (args.length == 1) {
            completions.addAll(Arrays.asList("reload", "status", "modules", "debug", "admin", "help"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "debug":
                    completions.addAll(Arrays.asList("on", "off", "enable", "disable"));
                    break;
                case "admin":
                    completions.addAll(Arrays.asList("add", "remove"));
                    break;
                case "help":
                    completions.addAll(Arrays.asList("verify", "support", "embed", "announce", 
                                                    "discordconsole", "report"));
                    break;
            }
        }
        
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}