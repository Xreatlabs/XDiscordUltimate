package com.xreatlabs.xdiscordultimate.discord;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.announcements.AnnouncementModule;
import com.xreatlabs.xdiscordultimate.modules.stats.PlayerStatsModule;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
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
import org.bukkit.entity.Player;

import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;

public class DiscordListener implements EventListener {

    private final XDiscordUltimate plugin;
    private final SlashCommandManager slashCommandManager;

    public DiscordListener(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.slashCommandManager = new SlashCommandManager(plugin);
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

        plugin.getDiscordManager().updateActivity();

        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            plugin.getDiscordManager().logConfiguredChannels();
        }, 40L);
    }

    public void registerSlashCommands() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) {
            plugin.getLogger().warning("Cannot register slash commands: main guild is not set.");
            return;
        }
        slashCommandManager.registerCommands(guild);
    }

    private void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        if (!event.isFromGuild()) {
            handleDirectMessage(event);
            return;
        }

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("Discord message from " + event.getAuthor().getAsTag() +
                " in #" + event.getChannel().getName() + ": " + event.getMessage().getContentDisplay());
        }

        String message = event.getMessage().getContentDisplay().trim();
        String prefix = plugin.getConfig().getString("discord.text-command-prefix", "!");
        if (message.startsWith(prefix)) {
            handleBotCommand(event, prefix);
        }
    }

    private void handleDirectMessage(MessageReceivedEvent event) {
        User author = event.getAuthor();
        String message = event.getMessage().getContentDisplay().trim();

        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Received DM from " + author.getAsTag() + ": " + message);
        }

        if (message.matches("\\d{6}")) {
            com.xreatlabs.xdiscordultimate.database.DatabaseManager.VerificationCode verificationCode = plugin.getDatabaseManager().getVerificationCode(message);
            if (verificationCode != null && !verificationCode.isExpired()) {
                event.getChannel().sendMessage("Code accepted. Use `/verify " + message + "` in-game.").queue();
            } else {
                event.getChannel().sendMessage("Invalid or expired verification code.").queue();
            }
        } else if (message.toLowerCase().startsWith("help")) {
            EmbedBuilder embed = EmbedUtils.infoEmbed("XDiscordUltimate",
                "`/verify` — Link your Discord account\n" +
                "`/support <msg>` — Create a support ticket\n" +
                "`!help` — Bot commands");

            event.getChannel().sendMessageEmbeds(embed.build()).queue();
        }
    }

    private void handleBotCommand(MessageReceivedEvent event, String prefix) {
        String message = event.getMessage().getContentDisplay().trim();
        String withoutPrefix = message.substring(prefix.length()).trim();
        int spaceIndex = withoutPrefix.indexOf(' ');
        String command = (spaceIndex == -1 ? withoutPrefix : withoutPrefix.substring(0, spaceIndex)).toLowerCase();
        String args = spaceIndex == -1 ? "" : withoutPrefix.substring(spaceIndex + 1).trim();

        if (handleCoreTextCommand(event, command, args)) return;
        if (slashCommandManager.dispatchText(event, command, args)) return;

        event.getMessage().reply("Unknown command. Use `" + prefix + "help` to see available commands.").queue();
    }

    private boolean handleCoreTextCommand(MessageReceivedEvent event, String command, String args) {
        switch (command) {
            case "help":
                event.getChannel().sendMessageEmbeds(buildHelpEmbed(event.getJDA()).build()).queue();
                return true;
            case "status":
                event.getChannel().sendMessageEmbeds(buildStatusEmbed().build()).queue();
                return true;
            case "players":
            case "online":
            case "list":
                event.getChannel().sendMessageEmbeds(buildPlayerListEmbed().build()).queue();
                return true;
            case "ping":
                event.getChannel().sendMessageEmbeds(buildPingEmbed(event.getJDA().getGatewayPing()).build()).queue();
                return true;
            case "tps":
                event.getChannel().sendMessageEmbeds(buildTpsEmbed(getTPS()).build()).queue();
                return true;
            case "stats":
                handleTextStats(event, args);
                return true;
            case "announce":
                handleTextAnnounce(event, args);
                return true;
            case "info":
                event.getChannel().sendMessageEmbeds(buildInfoEmbed(event.getJDA()).build()).queue();
                return true;
            case "server":
                if (args.trim().isEmpty()) {
                    event.getChannel().sendMessageEmbeds(buildServerInfoEmbed().build()).queue();
                    return true;
                }
                return false;
            default:
                return false;
        }
    }

    private void handleTextStats(MessageReceivedEvent event, String args) {
        String playerName = args.trim().isEmpty() ? null : args.trim();
        if (playerName == null) {
            String discordId = event.getAuthor().getId();
            plugin.getDatabaseManager().getMinecraftName(discordId).thenAccept(name -> {
                if (name == null) {
                    event.getMessage().reply("Please specify a player name or link your account with `/verify`").queue();
                    return;
                }
                MessageEmbed embed = buildStatsEmbed(name);
                if (embed != null) {
                    event.getChannel().sendMessageEmbeds(embed).queue();
                }
            });
        } else {
            MessageEmbed embed = buildStatsEmbed(playerName);
            if (embed != null) {
                event.getChannel().sendMessageEmbeds(embed).queue();
            }
        }
    }

    private void handleTextAnnounce(MessageReceivedEvent event, String args) {
        if (args.trim().isEmpty()) {
            event.getMessage().reply("Usage: `!announce <message>`").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null || !canAnnounce(member)) {
            event.getMessage().reply("You don't have permission to send announcements!").queue();
            return;
        }

        AnnouncementModule announcementModule = plugin.getModuleManager().getModule(AnnouncementModule.class);
        if (announcementModule == null || !announcementModule.isEnabled()) {
            event.getMessage().reply("Announcement module is not enabled!").queue();
            return;
        }

        announcementModule.makeAnnouncement(null, "Announcement", args.trim());
        event.getMessage().reply("Announcement sent to all players!").queue();
    }

    private boolean canAnnounce(Member member) {
        List<String> adminIds = plugin.getConfig().getStringList("adminIDs");
        if (adminIds.contains(member.getId())) return true;

        List<String> allowedRoles = plugin.getConfig().getStringList("announcements.allowed-roles");
        List<String> announceRoles = (allowedRoles == null || allowedRoles.isEmpty())
            ? Arrays.asList("admin", "moderator", "staff") : allowedRoles;

        return member.getRoles().stream().anyMatch(role -> {
            String roleName = role.getName().toLowerCase();
            String roleId = role.getId();
            return announceRoles.stream().anyMatch(allowed ->
                allowed.equalsIgnoreCase(roleName) || allowed.equals(roleId));
        });
    }

    private void onGuildMemberJoin(GuildMemberJoinEvent event) {
        Member member = event.getMember();
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Discord member joined: " + member.getEffectiveName());
        }

        TextChannel welcomeChannel = plugin.getDiscordManager().getWelcomeChannel();
        if (welcomeChannel != null) {
            sendWelcomeMessage(welcomeChannel, member);
        }
    }

    private void sendWelcomeMessage(TextChannel channel, Member member) {
        User user = member.getUser();

        EmbedBuilder embed = EmbedUtils.successEmbed("Welcome!",
            user.getAsMention() + " joined " + channel.getGuild().getName() + ".\n\n" +
            "• Verify your Minecraft account: `/verify` in-game\n" +
            "• Read the rules in <#YOUR_RULES_CHANNEL_ID>")
            .setThumbnail(user.getAvatarUrl())
            .setFooter("Member #" + channel.getGuild().getMemberCount(), null);

        channel.sendMessageEmbeds(embed.build())
            .setActionRow(net.dv8tion.jda.api.interactions.components.buttons.Button.primary("verify_button", "Get Verified")
                .withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")))
            .queue();
    }

    private void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        User user = event.getUser();
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Discord member left: " + user.getAsTag());
        }

        TextChannel logsChannel = plugin.getDiscordManager().getLogsChannel();
        if (logsChannel != null) {
            EmbedBuilder embed = EmbedUtils.errorEmbed("Member Left",
                user.getAsTag() + " has left the server")
                .setThumbnail(user.getAvatarUrl());

            logsChannel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    private void onSlashCommand(SlashCommandInteractionEvent event) {
        if (event.isAcknowledged()) {
            return;
        }

        if (slashCommandManager.dispatch(event)) {
            return;
        }

        switch (event.getName()) {
            case "help":
                event.replyEmbeds(buildHelpEmbed(event.getJDA()).build()).queue();
                break;
            case "status":
                event.replyEmbeds(buildStatusEmbed().build()).queue();
                break;
            case "players":
            case "online":
                event.replyEmbeds(buildPlayerListEmbed().build()).queue();
                break;
            case "ping":
                event.replyEmbeds(buildPingEmbed(event.getJDA().getGatewayPing()).build()).queue();
                break;
            case "tps":
                event.replyEmbeds(buildTpsEmbed(getTPS()).build()).queue();
                break;
            case "stats":
                handleSlashStats(event);
                break;
            case "announce":
                handleSlashAnnounce(event);
                break;
            case "info":
                event.replyEmbeds(buildInfoEmbed(event.getJDA()).build()).queue();
                break;
            default:
                event.reply("Unknown command! Use `/help` to see available commands.").setEphemeral(true).queue();
                break;
        }
    }

    private void handleSlashStats(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String playerName = event.getOption("player") != null ?
            event.getOption("player").getAsString() : null;

        if (playerName == null) {
            String discordId = event.getUser().getId();
            plugin.getDatabaseManager().getMinecraftName(discordId).thenAccept(name -> {
                if (name == null) {
                    event.getHook().editOriginal("Please specify a player name or link your account with `/verify`").queue();
                    return;
                }
                MessageEmbed embed = buildStatsEmbed(name);
                if (embed != null) {
                    event.getHook().editOriginalEmbeds(embed).queue();
                }
            });
        } else {
            MessageEmbed embed = buildStatsEmbed(playerName);
            if (embed != null) {
                event.getHook().editOriginalEmbeds(embed).queue();
            }
        }
    }

    private MessageEmbed buildStatsEmbed(String playerName) {
        PlayerStatsModule statsModule = plugin.getModuleManager().getModule(PlayerStatsModule.class);
        if (statsModule == null || !statsModule.isEnabled()) {
            return EmbedUtils.errorEmbed("Stats Unavailable", "Stats module is not enabled!").build();
        }

        PlayerStatsModule.PlayerStats stats = statsModule.getStats(playerName);
        return statsModule.buildStatsEmbed(stats).build();
    }

    private void handleSlashAnnounce(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !canAnnounce(member)) {
            event.reply("You don't have permission to send announcements!").setEphemeral(true).queue();
            return;
        }

        String message = event.getOption("message").getAsString();
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : "Announcement";

        AnnouncementModule announcementModule = plugin.getModuleManager().getModule(AnnouncementModule.class);
        if (announcementModule == null || !announcementModule.isEnabled()) {
            event.reply("Announcement module is not enabled!").setEphemeral(true).queue();
            return;
        }

        announcementModule.makeAnnouncement(null, title, message);
        event.reply("Announcement sent to all players!\n**Title:** " + title + "\n**Message:** " + message)
            .setEphemeral(true)
            .queue();
    }

    private EmbedBuilder buildHelpEmbed(JDA jda) {
        return buildHelpEmbed(jda.getSelfUser().getAvatarUrl());
    }

    private EmbedBuilder buildHelpEmbed(String avatarUrl) {
        return new EmbedBuilder()
            .setColor(EmbedUtils.BRAND_PRIMARY)
            .setAuthor("XDiscordUltimate", null, avatarUrl)
            .setTitle("Command Reference")
            .setDescription("**All available commands for the bot**")
            .addField("General Commands",
                "`/help` - Show this help message\n" +
                "`/status` - Show server status\n" +
                "`/players` - List online players\n" +
                "`/ping` - Check bot latency\n" +
                "`/tps` - Show server TPS", false)
            .addField("Account Linking",
                "`/verify` - Get code to link accounts\n" +
                "`/stats [player]` - View player stats", false)
            .addField("Leaderboards & Economy",
                "`/leaderboard` - View the server leaderboard\n" +
                "`/balance [player]` - Check player balance\n" +
                "`/baltop` - Richest players\n" +
                "`/pay <player> <amount>` - Send money", false)
            .addField("Support & Announcements",
                "`/ticket create <subject>` - Open a support ticket\n" +
                "`/announcement list` - View scheduled announcements\n" +
                "`/voicechannels status` - Voice channel status", false)
            .addField("Admin Commands",
                "`/console <cmd>` - Execute server command\n" +
                "`/server <subcommand>` - Server management\n" +
                "`/announce <msg>` - Send announcement\n" +
                "`/economy <action> <player> <amount>` - Economy admin", false)
            .setThumbnail(avatarUrl)
            .setFooter("XDiscordUltimate • Premium Discord Integration", null)
            .setTimestamp(Instant.now());
    }

    private EmbedBuilder buildStatusEmbed() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemory = runtime.maxMemory() / 1024 / 1024;

        String tpsStatus = tps >= 19 ? "🟢" : tps >= 15 ? "🟡" : "🔴";
        String memStatus = (usedMemory * 100 / maxMemory) < 80 ? "🟢" : "🟡";

        int barLength = 10;
        int filled = (int) ((double) onlinePlayers / maxPlayers * barLength);
        String playerBar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barLength - filled));

        return new EmbedBuilder()
            .setColor(EmbedUtils.SUCCESS)
            .setAuthor("Server Status", null, "https://crafatar.com/avatars/c06f89064c8a49119c29ea1dbd1aab82?overlay")
            .setTitle(Bukkit.getServer().getName())
            .setDescription("**Server is online and running smoothly!**")
            .addField("Players", "```\n" + playerBar + " " + onlinePlayers + "/" + maxPlayers + "\n```", false)
            .addField("Performance",
                tpsStatus + " **TPS:** " + String.format("%.1f", tps) + "/20\n" +
                memStatus + " **RAM:** " + usedMemory + "MB / " + maxMemory + "MB", true)
            .addField("Worlds",
                "**Loaded:** " + Bukkit.getWorlds().size() + "\n" +
                "**Chunks:** " + Bukkit.getWorlds().stream().mapToInt(w -> w.getLoadedChunks().length).sum(), true)
            .setFooter("Last Updated", null)
            .setTimestamp(Instant.now());
    }

    private EmbedBuilder buildPlayerListEmbed() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();

        if (players.isEmpty()) {
            return new EmbedBuilder()
                .setColor(EmbedUtils.WARNING)
                .setTitle("Online Players")
                .setDescription("```\nNo players online\n```")
                .setFooter("Check back later!", null)
                .setTimestamp(Instant.now());
        }

        StringBuilder playerList = new StringBuilder();
        for (Player player : players) {
            playerList.append("• **").append(player.getName()).append("**")
                .append(" - ").append(player.getWorld().getName())
                .append("\n");
        }

        return new EmbedBuilder()
            .setColor(EmbedUtils.BRAND_PRIMARY)
            .setTitle("Online Players (" + players.size() + "/" + Bukkit.getMaxPlayers() + ")")
            .setDescription(playerList.toString())
            .setFooter("Click a name to view their stats!", null)
            .setTimestamp(Instant.now());
    }

    private EmbedBuilder buildPingEmbed(long gatewayPing) {
        return new EmbedBuilder()
            .setColor(gatewayPing < 100 ? EmbedUtils.SUCCESS : gatewayPing < 200 ? EmbedUtils.WARNING : EmbedUtils.ERROR)
            .setTitle("Pong!")
            .addField("Gateway Ping", gatewayPing + "ms", true)
            .addField("Status", gatewayPing < 100 ? "🟢 Excellent" : gatewayPing < 200 ? "🟡 Good" : "🔴 High", true)
            .setTimestamp(Instant.now());
    }

    private EmbedBuilder buildTpsEmbed(double tps) {
        String status = tps >= 19 ? "🟢 Excellent" : tps >= 15 ? "🟡 Good" : "🔴 Low";
        Color color = tps >= 19 ? EmbedUtils.SUCCESS : tps >= 15 ? EmbedUtils.WARNING : EmbedUtils.ERROR;

        return new EmbedBuilder()
            .setColor(color)
            .setTitle("Server Performance")
            .addField("TPS", String.format("%.2f / 20.00", tps), true)
            .addField("Status", status, true)
            .addField("MSPT", String.format("%.2fms", 1000.0 / tps), true)
            .setTimestamp(Instant.now());
    }

    private EmbedBuilder buildInfoEmbed(JDA jda) {
        return new EmbedBuilder()
            .setColor(EmbedUtils.BRAND_PRIMARY)
            .setAuthor("XDiscordUltimate", null, jda.getSelfUser().getAvatarUrl())
            .setTitle("Plugin Information")
            .setDescription("**Premium Discord ↔ Minecraft Integration**")
            .addField("Version", plugin.getDescription().getVersion(), true)
            .addField("Author", "XreaLabs", true)
            .addField("Platform", Bukkit.getVersion().split("-")[0], true)
            .addField("Features",
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
    }

    private EmbedBuilder buildServerInfoEmbed() {
        String version = Bukkit.getServer().getVersion();
        String bukkitVersion = Bukkit.getBukkitVersion();
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        int pluginCount = Bukkit.getPluginManager().getPlugins().length;

        return new EmbedBuilder()
            .setColor(EmbedUtils.BRAND_SECONDARY)
            .setAuthor("Server Information", null, null)
            .setTitle("🏠 " + Bukkit.getServer().getName())
            .setDescription("**Detailed server information**")
            .addField("Server Details",
                "**Version:** " + bukkitVersion + "\n" +
                "**Software:** " + version.split("-")[0] + "\n" +
                "**Plugins:** " + pluginCount + " loaded", true)
            .addField("Capacity",
                "**Online:** " + onlinePlayers + "\n" +
                "**Max:** " + maxPlayers + "\n" +
                "**Slots:** " + (maxPlayers - onlinePlayers) + " free", true)
            .addField("Worlds",
                Bukkit.getWorlds().stream()
                    .map(w -> "• " + w.getName() + " (" + w.getPlayers().size() + ")")
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No worlds"), false)
            .setFooter("XDiscordUltimate", null)
            .setTimestamp(Instant.now());
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
}
