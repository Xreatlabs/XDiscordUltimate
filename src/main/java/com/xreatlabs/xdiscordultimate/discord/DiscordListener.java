package com.xreatlabs.xdiscordultimate.discord;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.database.DatabaseManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;

import java.awt.*;
import java.time.Instant;

public class DiscordListener implements EventListener {
    
    private final XDiscordUltimate plugin;
    
    public DiscordListener(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof ReadyEvent) {
            onReady((ReadyEvent) event);
        } else if (event instanceof MessageReceivedEvent) {
            onMessageReceived((MessageReceivedEvent) event);
        } else if (event instanceof GuildMemberJoinEvent) {
            onGuildMemberJoin((GuildMemberJoinEvent) event);
        } else if (event instanceof GuildMemberRemoveEvent) {
            onGuildMemberRemove((GuildMemberRemoveEvent) event);
        } else if (event instanceof SlashCommandInteractionEvent) {
            onSlashCommand((SlashCommandInteractionEvent) event);
        }
    }
    
    private void onReady(ReadyEvent event) {
        plugin.getLogger().info("Discord bot is ready!");
        
        if (!event.getJDA().getGuilds().isEmpty()) {
            plugin.getLogger().info("Connected to guild: " + event.getJDA().getGuilds().get(0).getName());
        } else {
            plugin.getLogger().warning("Bot is not connected to any guilds! Check your guild-id in config.");
        }
        
        plugin.getLogger().info("Bot user: " + event.getJDA().getSelfUser().getAsTag());
        
        // Update bot activity
        plugin.getDiscordManager().updateActivity();
        
        // Register slash commands
        registerSlashCommands(event);
        
        // Log configured channels (helpful for debugging)
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getDiscordManager().logConfiguredChannels();
        }, 40L); // 2 seconds after ready
        
        // Note: onDiscordReady is called from XDiscordUltimate after Discord initialization
    }
    
    private void registerSlashCommands(ReadyEvent event) {
        try {
            // Build all commands list for bulk registration
            java.util.List<net.dv8tion.jda.api.interactions.commands.build.SlashCommandData> commands = new java.util.ArrayList<>();
            
            // Basic bot commands
            commands.add(Commands.slash("help", "Show all bot commands and features"));
            commands.add(Commands.slash("status", "Show real-time server status with TPS and player count"));
            commands.add(Commands.slash("players", "List all online players with their worlds"));
            commands.add(Commands.slash("server", "Show detailed server information"));
            commands.add(Commands.slash("ping", "Check bot latency and response time"));
            commands.add(Commands.slash("tps", "Show server performance (TPS/MSPT)"));
            
            // Verification command
            commands.add(Commands.slash("verify", "Link your Discord account to Minecraft"));
            
            // Stats command
            commands.add(Commands.slash("stats", "View player statistics and achievements")
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", "Player name to lookup", false));
            
            // Console command (admin only)
            commands.add(Commands.slash("console", "Execute a command on the server (Admin only)")
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "command", "The command to execute", true));
            
            // Announce command
            commands.add(Commands.slash("announce", "Broadcast an announcement to all players")
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "message", "The announcement message", true)
                .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "title", "Optional title for the announcement", false));
            
            // Online command (alias for players)
            commands.add(Commands.slash("online", "Quick view of who's online"));
            
            // Info command
            commands.add(Commands.slash("info", "Show plugin info and version"));
            
            // Register GLOBALLY (works everywhere, takes up to 1 hour to propagate)
            event.getJDA().updateCommands()
                .addCommands(commands)
                .queue(
                    success -> plugin.getLogger().info("Registered " + commands.size() + " global slash commands (may take up to 1 hour to appear)"),
                    error -> plugin.getLogger().warning("Failed to register global commands: " + error.getMessage())
                );
            
            // Get guild ID from config and register commands directly
            String guildId = plugin.getConfigManager().getGuildId();
            if (guildId != null) {
                guildId = guildId.trim().replace("\"", "").replace("'", "");
            }
            
            // Try to find guild for instant command registration
            Guild guild = null;
            
            // Method 1: Use configured guild ID
            if (guildId != null && !guildId.isEmpty() && !guildId.equals("YOUR_GUILD_ID")) {
                guild = event.getJDA().getGuildById(guildId);
            }
            
            // Method 2: If bot is only in one guild, use that
            if (guild == null && event.getJDA().getGuilds().size() == 1) {
                guild = event.getJDA().getGuilds().get(0);
                plugin.getLogger().info("Auto-detected guild for commands: " + guild.getName());
            }
            
            // Method 3: Try first available guild
            if (guild == null && !event.getJDA().getGuilds().isEmpty()) {
                guild = event.getJDA().getGuilds().get(0);
                plugin.getLogger().info("Using first available guild: " + guild.getName() + " (ID: " + guild.getId() + ")");
            }
            
            // Register to guild for instant availability
            if (guild != null) {
                final Guild targetGuild = guild;
                targetGuild.updateCommands()
                    .addCommands(commands)
                    .queue(
                        success -> plugin.getLogger().info("Registered " + commands.size() + " guild slash commands to " + targetGuild.getName() + " (available instantly!)"),
                        error -> plugin.getLogger().severe("Failed to register guild commands: " + error.getMessage())
                    );
            } else {
                plugin.getLogger().warning("No guilds available for instant command registration.");
                plugin.getLogger().warning("Global commands will be available within 1 hour.");
                plugin.getLogger().info("Make sure your bot is invited to a Discord server!");
            }
                
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register slash commands: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages
        if (event.getAuthor().isBot()) return;
        
        // Handle DMs for support/verification
        if (!event.isFromGuild()) {
            handleDirectMessage(event);
            return;
        }
        
        // Log message for debugging
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Discord message from " + event.getAuthor().getAsTag() +
                " in #" + event.getChannel().getName() + ": " + event.getMessage().getContentDisplay());
        }
        
        // Handle bot commands
        String message = event.getMessage().getContentDisplay().trim();
        if (message.startsWith("!") || message.startsWith("/")) {
            handleBotCommand(event);
        }
    }
    
    private void handleDirectMessage(MessageReceivedEvent event) {
        User author = event.getAuthor();
        String message = event.getMessage().getContentDisplay().trim();
        
        plugin.getLogger().info("Received DM from " + author.getAsTag() + ": " + message);
        
        // Handle verification codes
        if (message.matches("\\d{6}")) {
            // This looks like a verification code
            com.xreatlabs.xdiscordultimate.database.DatabaseManager.VerificationCode verificationCode = plugin.getDatabaseManager().getVerificationCode(message);
            if (verificationCode != null && !verificationCode.isExpired()) {
                // Valid verification code
                event.getChannel().sendMessage("✅ Verification code accepted! You can now use `/verify " + message + "` in-game.").queue();
            } else {
                event.getChannel().sendMessage("❌ Invalid or expired verification code.").queue();
            }
        } else if (message.toLowerCase().startsWith("help")) {
            // Send help message
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.BLUE)
                .setTitle("XDiscordUltimate Help")
                .setDescription("Available commands:")
                .addField("Verification", "Use `/verify` in-game to link your accounts", false)
                .addField("Support", "Use `/support <message>` in-game to create a ticket", false)
                .addField("Commands", "Use `!help` in the server for bot commands", false)
                .setTimestamp(Instant.now());
            
            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        }
    }
    
    private void handleBotCommand(MessageReceivedEvent event) {
        String message = event.getMessage().getContentDisplay().trim();
        String command = message.substring(1).toLowerCase();
        String[] args = command.split(" ");
        
        switch (args[0]) {
            case "help":
                sendHelpMessage(event);
                break;
            case "status":
                sendStatusMessage(event);
                break;
            case "players":
            case "list":
                sendPlayerList(event);
                break;
            case "server":
                sendServerInfo(event);
                break;
            default:
                // Unknown command - let modules handle it
                break;
        }
    }
    
    private void sendHelpMessage(MessageReceivedEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.BLUE)
            .setTitle("🤖 Bot Commands")
            .setDescription("Available Discord bot commands:")
            .addField("!help", "Show this help message", false)
            .addField("!status", "Show server status", false)
            .addField("!players", "List online players", false)
            .addField("!server", "Show server information", false)
            .setTimestamp(Instant.now())
            .setFooter("XDiscordUltimate", null);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void sendStatusMessage(MessageReceivedEvent event) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        String version = Bukkit.getServer().getVersion();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("🖥️ Server Status")
            .addField("Status", "🟢 Online", true)
            .addField("Players", onlinePlayers + "/" + maxPlayers, true)
            .addField("Version", version, true)
            .setTimestamp(Instant.now())
            .setFooter("Last updated", null);
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void sendPlayerList(MessageReceivedEvent event) {
        java.util.Collection<? extends org.bukkit.entity.Player> players = Bukkit.getOnlinePlayers();
        
        if (players.isEmpty()) {
            event.getChannel().sendMessage("No players are currently online.").queue();
            return;
        }
        
        StringBuilder playerList = new StringBuilder();
        for (org.bukkit.entity.Player player : players) {
            if (playerList.length() > 0) {
                playerList.append(", ");
            }
            playerList.append(player.getName());
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.CYAN)
            .setTitle("👥 Online Players (" + players.size() + ")")
            .setDescription(playerList.toString())
            .setTimestamp(Instant.now());
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void sendServerInfo(MessageReceivedEvent event) {
        String serverName = Bukkit.getServer().getName();
        String version = Bukkit.getServer().getVersion();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.ORANGE)
            .setTitle("ℹ️ Server Information")
            .addField("Server Name", serverName, true)
            .addField("Version", version, true)
            .addField("Players", onlinePlayers + "/" + maxPlayers, true)
            .setTimestamp(Instant.now());
        
        event.getChannel().sendMessageEmbeds(embed.build()).queue();
    }
    
    private void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        plugin.getLogger().info("Discord member joined: " + member.getEffectiveName());
        
        // Send welcome message if configured
        TextChannel welcomeChannel = plugin.getDiscordManager().getWelcomeChannel();
        if (welcomeChannel != null) {
            sendWelcomeMessage(welcomeChannel, member);
        }
    }
    
    private void sendWelcomeMessage(TextChannel channel, Member member) {
        User user = member.getUser();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(Color.GREEN)
            .setTitle("🎉 Welcome to the server!")
            .setDescription("Welcome " + user.getAsMention() + " to our Discord server!")
            .addField("Getting Started",
                "• Link your Minecraft account with `/verify` in-game\n" + 
                "• Check out our rules and information\n" + 
                "• Say hello in the chat!", false)
            .setThumbnail(user.getAvatarUrl())
            .setTimestamp(Instant.now())
            .setFooter("Member #" + channel.getGuild().getMemberCount(), null);
        
        channel.sendMessageEmbeds(embed.build()).queue();
    }
    
    private void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        plugin.getLogger().info("Discord member left: " + user.getAsTag());
        
        // Log to logs channel if configured
        TextChannel logsChannel = plugin.getDiscordManager().getLogsChannel();
        if (logsChannel != null) {
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("📤 Member Left")
                .setDescription(user.getAsTag() + " has left the server")
                .setThumbnail(user.getAvatarUrl())
                .setTimestamp(Instant.now());
            
            logsChannel.sendMessageEmbeds(embed.build()).queue();
        }
    }
    
    private void onSlashCommand(SlashCommandInteractionEvent event) {
        String command = event.getName();
        
        switch (command) {
            case "help":
                sendSlashHelpMessage(event);
                break;
            case "status":
                sendSlashStatusMessage(event);
                break;
            case "players":
                sendSlashPlayerList(event);
                break;
            case "server":
                sendSlashServerInfo(event);
                break;
            case "ping":
                handlePingCommand(event);
                break;
            case "tps":
                handleTpsCommand(event);
                break;
            case "verify":
                handleVerifyCommand(event);
                break;
            case "stats":
                handleStatsCommand(event);
                break;
            case "console":
                handleConsoleCommand(event);
                break;
            case "announce":
                handleAnnounceCommand(event);
                break;
            case "online":
                sendSlashPlayerList(event);  // Alias for /players
                break;
            case "info":
                handleInfoCommand(event);
                break;
            default:
                event.reply("❌ Unknown command! Use `/help` to see available commands.").setEphemeral(true).queue();
                break;
        }
    }
    
    private void handleInfoCommand(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(85, 255, 255))
            .setAuthor("XDiscordUltimate", null, event.getJDA().getSelfUser().getAvatarUrl())
            .setTitle("ℹ️ Plugin Information")
            .setDescription("**Premium Discord ↔ Minecraft Integration**\n\u200B")
            .addField("📋 Version", plugin.getDescription().getVersion(), true)
            .addField("👤 Author", "XreaLabs", true)
            .addField("☕ Platform", Bukkit.getVersion().split("-")[0], true)
            .addField("🔌 Features",
                "• Two-way chat bridge\n" +
                "• Account verification\n" +
                "• Player statistics\n" +
                "• Server status embeds\n" +
                "• Admin console access\n" +
                "• Moderation tools\n" +
                "• Ticket system\n" +
                "• Announcements", false)
            .setFooter("Use /help for all commands", null)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendSlashHelpMessage(SlashCommandInteractionEvent event) {
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(85, 255, 255))
            .setAuthor("XDiscordUltimate", null, event.getJDA().getSelfUser().getAvatarUrl())
            .setTitle("📚 Command Reference")
            .setDescription("**All available commands for the bot**\n\u200B")
            .addField("🎮 General Commands", 
                "`/help` - Show this help message\n" +
                "`/status` - Show server status\n" +
                "`/players` - List online players\n" +
                "`/server` - Show server info\n" +
                "`/ping` - Check bot latency\n" +
                "`/tps` - Show server TPS", false)
            .addField("🔗 Account Linking", 
                "`/verify` - Get code to link accounts\n" +
                "`/stats [player]` - View player stats", false)
            .addField("🛡️ Admin Commands", 
                "`/console <cmd>` - Execute server command\n" +
                "`/announce <msg>` - Send announcement", false)
            .setThumbnail(event.getJDA().getSelfUser().getAvatarUrl())
            .setFooter("XDiscordUltimate • Premium Discord Integration", null)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendSlashStatusMessage(SlashCommandInteractionEvent event) {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        
        String tpsStatus = tps >= 19 ? "🟢" : tps >= 15 ? "🟡" : "🔴";
        String memStatus = (usedMemory * 100 / maxMemory) < 80 ? "🟢" : "🟡";
        
        // Create progress bar for players
        int barLength = 10;
        int filled = (int) ((double) onlinePlayers / maxPlayers * barLength);
        String playerBar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barLength - filled));
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(87, 242, 135))
            .setAuthor("Server Status", null, "https://crafatar.com/avatars/c06f89064c8a49119c29ea1dbd1aab82?overlay")
            .setTitle("🖥️ " + Bukkit.getServer().getName())
            .setDescription("**Server is online and running smoothly!**\n\u200B")
            .addField("👥 Players", "```\n" + playerBar + " " + onlinePlayers + "/" + maxPlayers + "\n```", false)
            .addField("📊 Performance", 
                tpsStatus + " **TPS:** " + String.format("%.1f", tps) + "/20\n" +
                memStatus + " **RAM:** " + usedMemory + "MB / " + maxMemory + "MB", true)
            .addField("🌍 Worlds", 
                "**Loaded:** " + Bukkit.getWorlds().size() + "\n" +
                "**Chunks:** " + Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum(), true)
            .setFooter("Last Updated", null)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendSlashPlayerList(SlashCommandInteractionEvent event) {
        java.util.Collection<? extends org.bukkit.entity.Player> players = Bukkit.getOnlinePlayers();
        
        if (players.isEmpty()) {
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(254, 231, 92))
                .setTitle("👥 Online Players")
                .setDescription("```\nNo players online\n```")
                .setFooter("Check back later!", null)
                .setTimestamp(Instant.now());
            event.replyEmbeds(embed.build()).queue();
            return;
        }
        
        StringBuilder playerList = new StringBuilder();
        for (org.bukkit.entity.Player player : players) {
            playerList.append("• **").append(player.getName()).append("**");
            playerList.append(" - ").append(player.getWorld().getName());
            playerList.append("\n");
        }
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(85, 255, 255))
            .setTitle("👥 Online Players (" + players.size() + "/" + Bukkit.getMaxPlayers() + ")")
            .setDescription(playerList.toString())
            .setFooter("Click a name to view their stats!", null)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void sendSlashServerInfo(SlashCommandInteractionEvent event) {
        String version = Bukkit.getServer().getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        int pluginCount = Bukkit.getPluginManager().getPlugins().length;
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(new Color(255, 170, 0))
            .setAuthor("Server Information", null, null)
            .setTitle("🏠 " + Bukkit.getServer().getName())
            .setDescription("**Detailed server information**\n\u200B")
            .addField("📋 Server Details", 
                "**Version:** " + bukkitVersion + "\n" +
                "**Software:** " + version.split("-")[0] + "\n" +
                "**Plugins:** " + pluginCount + " loaded", true)
            .addField("👥 Capacity", 
                "**Online:** " + onlinePlayers + "\n" +
                "**Max:** " + maxPlayers + "\n" +
                "**Slots:** " + (maxPlayers - onlinePlayers) + " free", true)
            .addField("🌍 Worlds", 
                Bukkit.getWorlds().stream()
                    .map(w -> "• " + w.getName() + " (" + w.getPlayers().size() + ")")
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No worlds"), false)
            .setFooter("XDiscordUltimate", null)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleVerifyCommand(SlashCommandInteractionEvent event) {
        // Defer reply immediately to prevent other listeners from responding
        event.deferReply(true).queue();
        
        // Check if user is already verified
        String discordId = event.getUser().getId();
        
        plugin.getDatabaseManager().isDiscordLinked(discordId).thenAccept(isLinked -> {
            if (isLinked) {
                event.getHook().editOriginal("✅ Your Discord account is already linked to a Minecraft account!").queue();
                return;
            }
            
            // Generate a verification code
            String code = String.format("%06d", new java.util.Random().nextInt(999999));
            plugin.getDatabaseManager().storeVerificationCode(discordId, code, event.getUser().getName());
            
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("🔗 Account Verification")
                .setDescription("Your verification code is: **" + code + "**")
                .addField("Instructions", "1. Join the Minecraft server\n2. Run `/verify " + code + "`\n3. Your accounts will be linked!", false)
                .addField("⏰ Expires", "This code expires in 10 minutes", false)
                .setTimestamp(Instant.now());
            
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        });
    }
    
    private void handleStatsCommand(SlashCommandInteractionEvent event) {
        // Defer reply immediately for async operations
        event.deferReply().queue();
        
        String playerName = event.getOption("player") != null ? 
            event.getOption("player").getAsString() : null;
        
        // If no player specified, try to get linked Minecraft account
        if (playerName == null) {
            String discordId = event.getUser().getId();
            plugin.getDatabaseManager().getMinecraftName(discordId).thenAccept(name -> {
                if (name == null) {
                    event.getHook().editOriginal("Please specify a player name or link your account with `/verify`").queue();
                    return;
                }
                sendStatsEmbed(event, name);
            });
        } else {
            sendStatsEmbed(event, playerName);
        }
    }
    
    private void sendStatsEmbed(SlashCommandInteractionEvent event, String playerName) {
        com.xreatlabs.xdiscordultimate.modules.stats.PlayerStatsModule statsModule = 
            plugin.getModuleManager().getModule(com.xreatlabs.xdiscordultimate.modules.stats.PlayerStatsModule.class);
        
        if (statsModule == null || !statsModule.isEnabled()) {
            event.getHook().editOriginal("Stats module is not enabled!").queue();
            return;
        }
        
        com.xreatlabs.xdiscordultimate.modules.stats.PlayerStatsModule.PlayerStats stats = 
            statsModule.getStats(playerName);
        
        EmbedBuilder embed = statsModule.buildStatsEmbed(stats);
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleConsoleCommand(SlashCommandInteractionEvent event) {
        if (!plugin.getConfig().getBoolean("discord-console.enabled", true)) {
            event.reply("The console command is disabled.").setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null) {
            event.reply("This command can only be used in a guild.").setEphemeral(true).queue();
            return;
        }

        java.util.List<String> adminIds = plugin.getConfig().getStringList("adminIDs");
        boolean isAdmin = adminIds.contains(member.getId());

        java.util.List<String> allowedRoles = plugin.getConfig().getStringList("discord-console.allowed-roles");
        boolean hasRole = member.getRoles().stream().anyMatch(role -> allowedRoles.contains(role.getName()) || allowedRoles.contains(role.getId()));

        if (!isAdmin && !hasRole) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        String command = event.getOption("command").getAsString();
        event.deferReply().setEphemeral(true).queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            final java.util.List<String> capturedLogs = new java.util.ArrayList<>();
            org.apache.logging.log4j.core.appender.AbstractAppender tempAppender = new org.apache.logging.log4j.core.appender.AbstractAppender("TempDiscordListenerAppender", null, org.apache.logging.log4j.core.layout.PatternLayout.createDefaultLayout(), false, null) {
                @Override
                public void append(org.apache.logging.log4j.core.LogEvent event) {
                    capturedLogs.add(event.getMessage().getFormattedMessage());
                }
            };
            tempAppender.start();

            org.apache.logging.log4j.core.Logger rootLogger = (org.apache.logging.log4j.core.Logger) org.apache.logging.log4j.LogManager.getRootLogger();
            rootLogger.addAppender(tempAppender);

            try {
                // Execute
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            } finally {
                rootLogger.removeAppender(tempAppender);
                tempAppender.stop();
            }

            // Send output
            String output = String.join("\n", capturedLogs);
            if (output.isEmpty()) {
                event.getHook().editOriginal("✅ Command executed successfully! (No output)").queue();
            } else {
                String finalOutput = "✅ Command executed successfully!\n```\n" + org.bukkit.ChatColor.stripColor(output) + "\n```";
                if (finalOutput.length() > 2000) {
                    finalOutput = finalOutput.substring(0, 1990) + "...```";
                }
                event.getHook().editOriginal(finalOutput).queue();
            }
        });
    }
    
    private void handlePingCommand(SlashCommandInteractionEvent event) {
        long gatewayPing = event.getJDA().getGatewayPing();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(gatewayPing < 100 ? Color.GREEN : gatewayPing < 200 ? Color.YELLOW : Color.RED)
            .setTitle("🏓 Pong!")
            .addField("Gateway Ping", gatewayPing + "ms", true)
            .addField("Status", gatewayPing < 100 ? "🟢 Excellent" : gatewayPing < 200 ? "🟡 Good" : "🔴 High", true)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private void handleTpsCommand(SlashCommandInteractionEvent event) {
        double tps = getTPS();
        String status = tps >= 19 ? "🟢 Excellent" : tps >= 15 ? "🟡 Good" : "🔴 Low";
        Color color = tps >= 19 ? Color.GREEN : tps >= 15 ? Color.YELLOW : Color.RED;
        
        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color)
            .setTitle("📊 Server Performance")
            .addField("TPS", String.format("%.2f / 20.00", tps), true)
            .addField("Status", status, true)
            .addField("MSPT", String.format("%.2fms", 1000.0 / tps), true)
            .setTimestamp(Instant.now());
        
        event.replyEmbeds(embed.build()).queue();
    }
    
    private double getTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return tps[0];
        } catch (Exception e) {
            return 20.0;
        }
    }
    
    private void handleAnnounceCommand(SlashCommandInteractionEvent event) {
        // Check permissions
        Member member = event.getMember();
        if (member == null) {
            event.reply("This command can only be used in a guild.").setEphemeral(true).queue();
            return;
        }
        
        java.util.List<String> adminIds = plugin.getConfig().getStringList("adminIDs");
        boolean isAdmin = adminIds.contains(member.getId());
        boolean hasRole = member.getRoles().stream()
            .anyMatch(role -> role.getName().equalsIgnoreCase("Admin") || 
                             role.getName().equalsIgnoreCase("Moderator") ||
                             role.getName().equalsIgnoreCase("Staff"));
        
        if (!isAdmin && !hasRole) {
            event.reply("❌ You don't have permission to send announcements!").setEphemeral(true).queue();
            return;
        }
        
        String message = event.getOption("message").getAsString();
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : "Announcement";
        
        // Get announcement module
        com.xreatlabs.xdiscordultimate.modules.announcements.AnnouncementModule announcementModule = 
            plugin.getModuleManager().getModule(com.xreatlabs.xdiscordultimate.modules.announcements.AnnouncementModule.class);
        
        if (announcementModule == null || !announcementModule.isEnabled()) {
            event.reply("❌ Announcement module is not enabled!").setEphemeral(true).queue();
            return;
        }
        
        // Send announcement
        announcementModule.makeAnnouncement(null, title, message);
        
        event.reply("✅ Announcement sent to all players!\n**Title:** " + title + "\n**Message:** " + message)
            .setEphemeral(true)
            .queue();
    }
}