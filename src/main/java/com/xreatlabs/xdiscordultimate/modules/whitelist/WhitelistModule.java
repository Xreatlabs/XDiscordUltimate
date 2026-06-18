package com.xreatlabs.xdiscordultimate.modules.whitelist;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class WhitelistModule extends Module implements CommandExecutor {

    private static final String BUTTON_ACCEPT_PREFIX = "wl_accept:";
    private static final String BUTTON_DECLINE_PREFIX = "wl_decline:";

    private final Map<String, WhitelistRequest> requests = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger();

    private WhitelistListener listener;
    private String whitelistChannelName;
    private String supportRoleName;
    private boolean autoWhitelist;
    private boolean dmOnAccept;
    private boolean dmOnDecline;

    public WhitelistModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Whitelist";
    }

    @Override
    public String getDescription() {
        return "Allow players to request whitelist access via Discord";
    }

    @Override
    protected void onEnable() {
        loadConfiguration();

        PluginCommand cmd = plugin.getCommand("whitelist");
        if (cmd != null) {
            cmd.setExecutor(this);
        }

        info("Whitelist module enabled");
    }

    @Override
    public void onDiscordReady() {
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            listener = new WhitelistListener();
            plugin.getDiscordManager().getJDA().addEventListener(listener);
            info("Whitelist module connected to Discord");
        }
    }

    @Override
    public List<SlashCommandData> getSlashCommands() {
        return Collections.singletonList(
            Commands.slash("whitelist", "Request whitelist access")
                .addOption(OptionType.STRING, "username", "Minecraft username to whitelist", true)
        );
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        if (!"whitelist".equals(event.getName())) {
            return false;
        }

        Member member = event.getMember();
        if (member == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Guild Only",
                    "This command can only be used in a guild.").build())
                .setEphemeral(true).queue();
            return true;
        }

        String username = event.getOption("username").getAsString().trim();
        String error = validateUsername(username);
        if (error != null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Invalid Username", error).build())
                .setEphemeral(true).queue();
            return true;
        }

        submitRequest(member.getId(), member.getEffectiveName(), username);
        event.replyEmbeds(EmbedUtils.successEmbed("Request Submitted",
                "Your whitelist request for **" + username + "** has been sent to staff.").build())
            .setEphemeral(true).queue();
        return true;
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"whitelist".equals(command)) {
            return false;
        }

        Member member = event.getMember();
        if (member == null) {
            event.getMessage().reply("This command can only be used in a guild.").queue();
            return true;
        }

        String username = args.trim();
        if (username.isEmpty()) {
            event.getMessage().reply("Usage: `!whitelist <username>`").queue();
            return true;
        }

        String error = validateUsername(username);
        if (error != null) {
            event.getMessage().replyEmbeds(
                EmbedUtils.errorEmbed("Invalid Username", error).build()).queue();
            return true;
        }

        submitRequest(member.getId(), member.getEffectiveName(), username);
        event.getMessage().replyEmbeds(
            EmbedUtils.successEmbed("Request Submitted",
                "Your whitelist request for **" + username + "** has been sent to staff.").build())
            .queue();
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().colorize("&eUsage: &f/whitelist <username>"));
            }
            return true;
        }

        String username = args[0].trim();
        String error = validateUsername(username);
        if (error != null) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().colorize("&c" + error));
            }
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;
        if (player == null) {
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().colorize("&cOnly players can request whitelist access."));
            }
            return true;
        }

        final String requesterName = player.getName();
        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(discordId -> {
                final String requesterDiscordId = discordId;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    submitRequest(requesterDiscordId, requesterName, username);
                    player.sendMessage(plugin.getMessageManager().colorize(
                        "&aWhitelist request submitted for &f" + username + "&a. Check Discord for updates."));
                });
            })
            .exceptionally(t -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendError(player, "errors.whitelist-submission-failed"));
                warning("Failed to resolve Discord id for " + requesterName + ": " + t.getMessage());
                return null;
            });

        return true;
    }

    private String validateUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "Username cannot be empty.";
        }
        if (username.length() < 3 || username.length() > 16) {
            return "Username must be between 3 and 16 characters.";
        }
        if (!username.matches("[A-Za-z0-9_]+")) {
            return "Username may only contain letters, numbers, and underscores.";
        }
        return null;
    }

    private void submitRequest(String requesterDiscordId, String requesterName, String username) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            warning("Discord not ready; cannot submit whitelist request");
            return;
        }

        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) {
            warning("Main guild not found; cannot submit whitelist request");
            return;
        }

        TextChannel channel = resolveChannel(guild);
        if (channel == null) {
            warning("Whitelist channel '" + whitelistChannelName + "' not found");
            return;
        }

        String requestId = String.valueOf(requestCounter.incrementAndGet());
        WhitelistRequest request = new WhitelistRequest(
            requestId, username, requesterName, requesterDiscordId);
        requests.put(requestId, request);

        EmbedBuilder embed = buildRequestEmbed(request);
        Button accept = Button.success(BUTTON_ACCEPT_PREFIX + requestId, "Accept");
        Button decline = Button.danger(BUTTON_DECLINE_PREFIX + requestId, "Decline");

        channel.sendMessageEmbeds(embed.build())
            .setActionRow(accept, decline)
            .queue(sent -> {
                request.messageId = sent.getId();
                request.channelId = sent.getChannel().getId();
                info("Whitelist request #" + requestId + " for " + username + " posted by " + requesterName);
            }, error -> warning("Failed to post whitelist request: " + error.getMessage()));
    }

    private TextChannel resolveChannel(Guild guild) {
        String channelId = plugin.getConfig().getString("discord.channels.whitelist", "");
        if (channelId != null && !channelId.isEmpty() && channelId.matches("\\d+")) {
            TextChannel channel = plugin.getDiscordManager().getTextChannelById(channelId);
            if (channel != null) {
                return channel;
            }
        }
        return guild.getTextChannelsByName(whitelistChannelName, true)
            .stream().findFirst().orElse(null);
    }

    private EmbedBuilder buildRequestEmbed(WhitelistRequest request) {
        Color color;
        String status;
        switch (request.status) {
            case ACCEPTED:
                color = EmbedUtils.SUCCESS;
                status = "Accepted";
                break;
            case DECLINED:
                color = EmbedUtils.ERROR;
                status = "Declined";
                break;
            default:
                color = EmbedUtils.BRAND_PRIMARY;
                status = "Pending";
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(color)
            .setTitle("Whitelist Request")
            .addField("Minecraft Username", request.username, true)
            .addField("Requested By", request.requesterName, true)
            .addField("Status", status, true)
            .setFooter("XDiscordUltimate • Request #" + request.id, null)
            .setTimestamp(Instant.now());

        if (request.handledBy != null) {
            embed.addField("Handled By", request.handledBy, true);
        }
        return embed;
    }

    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        if (config != null) {
            whitelistChannelName = config.getString("whitelist-channel", "whitelist");
            supportRoleName = config.getString("support-role", "Support");
            autoWhitelist = config.getBoolean("auto-whitelist", true);
            dmOnAccept = config.getBoolean("dm-on-accept", true);
            dmOnDecline = config.getBoolean("dm-on-decline", true);
        } else {
            whitelistChannelName = "whitelist";
            supportRoleName = "Support";
            autoWhitelist = true;
            dmOnAccept = true;
            dmOnDecline = true;
        }
    }

    private boolean isAuthorized(Member member) {
        if (member == null) {
            return false;
        }
        if (member.hasPermission(Permission.MANAGE_CHANNEL)) {
            return true;
        }
        if (supportRoleName == null || supportRoleName.isEmpty()) {
            return false;
        }
        for (Role role : member.getRoles()) {
            if (supportRoleName.equalsIgnoreCase(role.getName())) {
                return true;
            }
        }
        return false;
    }

    private void handleAccept(ButtonInteractionEvent event, String requestId) {
        WhitelistRequest request = requests.get(requestId);
        if (request == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Not Found",
                    "This whitelist request is no longer active.").build())
                .setEphemeral(true).queue();
            return;
        }
        if (request.status != RequestStatus.PENDING) {
            event.replyEmbeds(EmbedUtils.warningEmbed("Already Handled",
                    "This request has already been " + request.status.name().toLowerCase() + ".").build())
                .setEphemeral(true).queue();
            return;
        }

        request.status = RequestStatus.ACCEPTED;
        request.handledBy = event.getMember().getEffectiveName();

        if (autoWhitelist) {
            Bukkit.getScheduler().runTask(plugin, () ->
                Bukkit.getServer().dispatchCommand(
                    Bukkit.getConsoleSender(), "whitelist add " + request.username));
        }

        updateRequestMessage(request);
        event.replyEmbeds(EmbedUtils.successEmbed("Request Accepted",
                "Whitelist request for **" + request.username + "** has been accepted.").build())
            .queue();

        if (dmOnAccept) {
            sendDm(request.requesterDiscordId,
                EmbedUtils.successEmbed("Whitelist Accepted",
                    "Your whitelist request has been accepted! Welcome!").build());
        }

        info("Whitelist request #" + requestId + " accepted by " + request.handledBy);
    }

    private void handleDecline(ButtonInteractionEvent event, String requestId) {
        WhitelistRequest request = requests.get(requestId);
        if (request == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Not Found",
                    "This whitelist request is no longer active.").build())
                .setEphemeral(true).queue();
            return;
        }
        if (request.status != RequestStatus.PENDING) {
            event.replyEmbeds(EmbedUtils.warningEmbed("Already Handled",
                    "This request has already been " + request.status.name().toLowerCase() + ".").build())
                .setEphemeral(true).queue();
            return;
        }

        request.status = RequestStatus.DECLINED;
        request.handledBy = event.getMember().getEffectiveName();

        updateRequestMessage(request);
        event.replyEmbeds(EmbedUtils.successEmbed("Request Declined",
                "Whitelist request for **" + request.username + "** has been declined.").build())
            .queue();

        if (dmOnDecline) {
            sendDm(request.requesterDiscordId,
                EmbedUtils.errorEmbed("Whitelist Declined",
                    "Your whitelist request has been declined.").build());
        }

        info("Whitelist request #" + requestId + " declined by " + request.handledBy);
    }

    private void updateRequestMessage(WhitelistRequest request) {
        if (request.channelId == null || request.messageId == null) {
            return;
        }
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) {
            return;
        }
        TextChannel channel = guild.getTextChannelById(request.channelId);
        if (channel == null) {
            return;
        }
        channel.editMessageEmbedsById(request.messageId, buildRequestEmbed(request).build())
            .setComponents()
            .queue(
                success -> debug("Updated whitelist request #" + request.id),
                error -> warning("Failed to update whitelist request #" + request.id
                    + ": " + error.getMessage())
            );
    }

    private void sendDm(String discordId, net.dv8tion.jda.api.entities.MessageEmbed embed) {
        if (discordId == null || discordId.isEmpty()) {
            debug("No linked Discord id; skipping whitelist DM");
            return;
        }
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        plugin.getDiscordManager().getJDA().retrieveUserById(discordId).queue(
            user -> user.openPrivateChannel().queue(
                ch -> ch.sendMessageEmbeds(embed).queue(
                    ok -> debug("Sent whitelist DM to " + user.getName()),
                    err -> warning("Failed to send whitelist DM: " + err.getMessage())),
                err -> warning("Failed to open DM channel: " + err.getMessage())),
            err -> debug("Could not resolve Discord user " + discordId + " for DM"));
    }

    @Override
    protected void onDisable() {
        if (listener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(listener);
        }
        info("Whitelist module disabled");
    }

    private class WhitelistListener extends ListenerAdapter {
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            String buttonId = event.getComponentId();
            Member member = event.getMember();
            if (member == null) {
                return;
            }

            if (buttonId.startsWith(BUTTON_ACCEPT_PREFIX)) {
                if (!isAuthorized(member)) {
                    event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                            "Only staff with the Support role or Manage Channels permission can use this.").build())
                        .setEphemeral(true).queue();
                    return;
                }
                handleAccept(event, buttonId.substring(BUTTON_ACCEPT_PREFIX.length()));
            } else if (buttonId.startsWith(BUTTON_DECLINE_PREFIX)) {
                if (!isAuthorized(member)) {
                    event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                            "Only staff with the Support role or Manage Channels permission can use this.").build())
                        .setEphemeral(true).queue();
                    return;
                }
                handleDecline(event, buttonId.substring(BUTTON_DECLINE_PREFIX.length()));
            }
        }
    }

    private enum RequestStatus {
        PENDING,
        ACCEPTED,
        DECLINED
    }

    private static class WhitelistRequest {
        final String id;
        final String username;
        final String requesterName;
        final String requesterDiscordId;
        volatile String messageId;
        volatile String channelId;
        volatile RequestStatus status = RequestStatus.PENDING;
        volatile String handledBy;

        WhitelistRequest(String id, String username, String requesterName, String requesterDiscordId) {
            this.id = id;
            this.username = username;
            this.requesterName = requesterName;
            this.requesterDiscordId = requesterDiscordId;
        }
    }
}
