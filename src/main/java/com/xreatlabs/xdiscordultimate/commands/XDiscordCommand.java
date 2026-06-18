package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
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
            case "setup":
                handleSetup(sender);
                break;

            case "reload":
                handleReload(sender);
                break;

            case "status":
            case "info":
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

            case "export":
                handleExport(sender, args);
                break;

            case "unlink":
                handleUnlink(sender, args);
                break;

            default:
                plugin.getMessageManager().sendError(sender, "Unknown subcommand: " + subCommand);
                showHelp(sender);
                break;
        }
        
        return true;
    }
    
    private void handleSetup(CommandSender sender) {
        new XDiscordSetupCommand(plugin).onCommand(sender, null, "setup", new String[0]);
    }

    private void handleReload(CommandSender sender) {
        plugin.getMessageManager().sendInfo(sender, "Reloading configuration...");
        
        try {
            plugin.reload();
            plugin.getMessageManager().sendMessage(sender, "general.reload-success");
        } catch (Exception e) {
            plugin.getMessageManager().sendMessage(sender, "general.reload-failed");
            plugin.getLogger().log(Level.SEVERE, "Failed to reload configuration", e);
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

    private void handleUnlink(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendError(sender, "Usage: /xdiscord unlink <player>");
            return;
        }
        if (!plugin.getConfigManager().isFeatureEnabled("verification")) {
            plugin.getMessageManager().sendMessage(sender, "errors.feature-disabled");
            return;
        }
        com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule module =
            plugin.getModuleManager().getModule(com.xreatlabs.xdiscordultimate.modules.verification.VerificationModule.class);
        if (module == null) {
            plugin.getMessageManager().sendMessage(sender, "errors.module-not-available");
            return;
        }

        String targetName = args[1];
        org.bukkit.OfflinePlayer target = plugin.getServer().getOfflinePlayer(targetName);
        boolean removed = module.unlink(target.getUniqueId());

        if (removed) {
            plugin.getMessageManager().sendSuccess(sender, "Unlinked " + targetName + " from Discord");
        } else {
            plugin.getMessageManager().sendError(sender, targetName + " is not linked to Discord (or unlink failed)");
        }
    }

    private void handleExport(CommandSender sender, String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase() : "all";

        sender.sendMessage(color("<gradient:#55FFFF:#5555FF>━━━ Exporting Data ━━━</gradient>"));
        sender.sendMessage(color("&7Gathering data..."));

        // Run export asynchronously to avoid blocking
        CompletableFuture.runAsync(() -> {
            try {
                File exportDir = new File(plugin.getDataFolder(), "exports");
                if (!exportDir.exists() && !exportDir.mkdirs()) {
                    plugin.getMessageManager().sendError(sender, "Failed to create export directory");
                    return;
                }

                String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
                    .withZone(ZoneId.systemDefault()).format(Instant.now());

                List<String> exportedFiles = new ArrayList<>();

                if (type.equals("all") || type.equals("verified")) {
                    File verifiedFile = exportVerifiedUsers(exportDir, timestamp);
                    if (verifiedFile != null) exportedFiles.add("verified users");
                }

                if (type.equals("all") || type.equals("stats")) {
                    File statsFile = exportPlayerStats(exportDir, timestamp);
                    if (statsFile != null) exportedFiles.add("player stats");
                }

                if (type.equals("all") || type.equals("tickets")) {
                    File ticketsFile = exportTickets(exportDir, timestamp);
                    if (ticketsFile != null) exportedFiles.add("tickets");
                }

                if (exportedFiles.isEmpty()) {
                    plugin.getMessageManager().sendError(sender, "No data was exported");
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage(color("<gradient:#55FF55:#00AA00>━━━ Export Complete ━━━</gradient>"));
                        sender.sendMessage(color("&a✓ Exported: " + String.join(", ", exportedFiles)));
                        sender.sendMessage(color("&7Files saved to: &fplugins/XDiscordUltimate/exports/"));
                    });
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Export failed", e);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendError(sender, "Export failed: " + e.getMessage()));
            }
        });
    }

    private File exportVerifiedUsers(File exportDir, String timestamp) {
        try {
            List<Map<String, Object>> verifiedUsers = new ArrayList<>();

            String sql = "SELECT * FROM verified_users ORDER BY verified_at DESC";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    Map<String, Object> user = new LinkedHashMap<>();
                    user.put("minecraft_uuid", rs.getString("minecraft_uuid"));
                    user.put("minecraft_name", rs.getString("minecraft_name"));
                    user.put("discord_id", rs.getString("discord_id"));
                    user.put("discord_name", rs.getString("discord_name"));
                    user.put("verified_at", rs.getString("verified_at"));
                    verifiedUsers.add(user);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("export_type", "verified_users");
            data.put("exported_at", DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault()).format(Instant.now()));
            data.put("total_count", verifiedUsers.size());
            data.put("users", verifiedUsers);

            File file = new File(exportDir, "verified_users_" + timestamp + ".json");
            writeJsonFile(file, data);
            return file;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to export verified users", e);
            return null;
        }
    }

    private File exportPlayerStats(File exportDir, String timestamp) {
        try {
            List<Map<String, Object>> playerStats = new ArrayList<>();

            String sql = "SELECT * FROM player_stats ORDER BY playtime_minutes DESC";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("minecraft_uuid", rs.getString("minecraft_uuid"));
                    stats.put("joins", rs.getInt("joins"));
                    stats.put("messages_sent", rs.getInt("messages_sent"));
                    stats.put("deaths", rs.getInt("deaths"));
                    stats.put("playtime_minutes", rs.getInt("playtime_minutes"));
                    stats.put("last_seen", rs.getString("last_seen"));
                    stats.put("first_join", rs.getString("first_join"));
                    playerStats.add(stats);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("export_type", "player_stats");
            data.put("exported_at", DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault()).format(Instant.now()));
            data.put("total_count", playerStats.size());
            data.put("stats", playerStats);

            File file = new File(exportDir, "player_stats_" + timestamp + ".json");
            writeJsonFile(file, data);
            return file;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to export player stats", e);
            return null;
        }
    }

    private File exportTickets(File exportDir, String timestamp) {
        try {
            List<Map<String, Object>> tickets = new ArrayList<>();

            String sql = "SELECT * FROM tickets ORDER BY created_at DESC";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {

                while (rs.next()) {
                    Map<String, Object> ticket = new LinkedHashMap<>();
                    ticket.put("id", rs.getInt("id"));
                    ticket.put("minecraft_uuid", rs.getString("minecraft_uuid"));
                    ticket.put("discord_channel_id", rs.getString("discord_channel_id"));
                    ticket.put("status", rs.getString("status"));
                    ticket.put("subject", rs.getString("subject"));
                    ticket.put("created_at", rs.getString("created_at"));
                    ticket.put("closed_at", rs.getString("closed_at"));
                    tickets.add(ticket);
                }
            }

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("export_type", "tickets");
            data.put("exported_at", DateTimeFormatter.ISO_INSTANT.withZone(ZoneId.systemDefault()).format(Instant.now()));
            data.put("total_count", tickets.size());
            data.put("tickets", tickets);

            File file = new File(exportDir, "tickets_" + timestamp + ".json");
            writeJsonFile(file, data);
            return file;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to export tickets", e);
            return null;
        }
    }

    private void writeJsonFile(File file, Map<String, Object> data) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("{\n");
            boolean first = true;
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                if (!first) writer.write(",\n");
                first = false;

                writer.write("  \"" + entry.getKey() + "\": ");
                Object value = entry.getValue();

                if (value instanceof String) {
                    writer.write("\"" + escapeJson((String) value) + "\"");
                } else if (value instanceof Number || value instanceof Boolean) {
                    writer.write(String.valueOf(value));
                } else if (value instanceof List) {
                    writer.write(jsonArrayToString((List<?>) value));
                } else {
                    writer.write("\"" + escapeJson(String.valueOf(value)) + "\"");
                }
            }
            writer.write("\n}\n");
        }
    }

    private String jsonArrayToString(List<?> list) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",\n");
            Object item = list.get(i);
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                sb.append("    {\n");
                boolean first = true;
                for (Map.Entry<String, Object> entry : map.entrySet()) {
                    if (!first) sb.append(",\n");
                    first = false;
                    sb.append("      \"" + entry.getKey() + "\": ");
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        sb.append("\"" + escapeJson((String) value) + "\"");
                    } else if (value instanceof Number || value instanceof Boolean) {
                        sb.append(String.valueOf(value));
                    } else {
                        sb.append("\"" + escapeJson(String.valueOf(value)) + "\"");
                    }
                }
                sb.append("\n    }");
            }
        }
        sb.append("\n  ]");
        return sb.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
    
    private void showHelp(CommandSender sender) {
        plugin.getMessageManager().sendMessageList(sender, "help.header");

        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord setup &8- &7Interactive setup wizard"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord reload &8- &7Reload configuration"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord status &8- &7Show plugin status"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord modules &8- &7List all modules"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord debug <on|off> &8- &7Toggle debug"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord admin <add|remove> <id> &8- &7Manage admins"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord export [type] &8- &7Export data to JSON"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord unlink <player> &8- &7Unlink a player from Discord"));
        sender.sendMessage(color("  <gradient:#55FF55:#55FFFF>▸</gradient> &e/xdiscord help <cmd> &8- &7Command help"));
        sender.sendMessage("");
        sender.sendMessage(color("&6&lOther Plugin Commands:"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/verify &8- &7Link your Minecraft account to Discord"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/support <message> &8- &7Create a support ticket"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/report <player> <reason> &8- &7Report a player to staff"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/stats &8- &7View your player statistics"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/discord &8- &7Get the Discord server invite link"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/embed <channel> <title> <desc> &8- &7Send an embed to Discord"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/announce <message> &8- &7Broadcast a server announcement"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/discordconsole <cmd> &8- &7Execute server commands via Discord"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/welcomedm &8- &7View your welcome DM message"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/ip &8- &7Get the server IP address"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/whitelist &8- &7Manage server whitelist"));
        sender.sendMessage(color("  <gradient:#FFAA00:#FF5555>▸</gradient> &e/language &8- &7Change your language preference"));

        plugin.getMessageManager().sendMessageList(sender, "help.footer");
    }
    
    private void showCommandHelp(CommandSender sender, String command) {
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
                sender.sendMessage(color("&8  Sends an embed to Discord. Requires: &exdiscord.embed"));
                break;

            case "discord":
                sender.sendMessage(color("<gradient:#55FFFF:#5555FF>/discord</gradient> &8- &7" +
                    plugin.getMessageManager().getMessage("commands.discord")));
                sender.sendMessage(color("&8  Shows the Discord server invite link."));
                break;

            case "report":
                sender.sendMessage(color("<gradient:#55FFFF:#5555FF>/report <player> <reason></gradient> &8- &7" +
                    plugin.getMessageManager().getMessage("commands.report")));
                sender.sendMessage(color("&8  Reports a player to Discord moderators."));
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
            completions.addAll(Arrays.asList("setup", "reload", "status", "modules", "debug", "admin", "export", "unlink", "help"));
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "debug":
                    completions.addAll(Arrays.asList("on", "off", "enable", "disable"));
                    break;
                case "admin":
                    completions.addAll(Arrays.asList("add", "remove"));
                    break;
                case "export":
                    completions.addAll(Arrays.asList("all", "verified", "stats", "tickets"));
                    break;
                case "unlink":
                    plugin.getServer().getOnlinePlayers().forEach(p -> completions.add(p.getName()));
                    break;
                case "help":
                    completions.addAll(Arrays.asList("verify", "support", "embed", "announce",
                                                    "discordconsole", "report", "discord"));
                    break;
            }
        }
        
        return completions.stream()
            .filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase()))
            .collect(Collectors.toList());
    }
}