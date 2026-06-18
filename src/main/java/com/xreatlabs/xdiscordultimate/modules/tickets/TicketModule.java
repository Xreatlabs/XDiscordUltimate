package com.xreatlabs.xdiscordultimate.modules.tickets;
import net.dv8tion.jda.api.entities.emoji.Emoji;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.modules.DependsOn;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@DependsOn({"verification"})
public class TicketModule extends Module {

    public enum TicketPriority {
        LOW("LOW", new Color(0x4CAF50)),
        MEDIUM("MEDIUM", new Color(0xFFC107)),
        HIGH("HIGH", new Color(0xFF9800)),
        URGENT("URGENT", new Color(0xE53935));

        public final String label;
        public final Color color;

        TicketPriority(String label, Color color) {
            this.label = label;
            this.color = color;
        }

        public static TicketPriority fromString(String value) {
            if (value == null) return MEDIUM;
            switch (value.toUpperCase().trim()) {
                case "LOW": return LOW;
                case "HIGH": return HIGH;
                case "URGENT":
                case "CRITICAL": return URGENT;
                case "MEDIUM":
                default: return MEDIUM;
            }
        }
    }

    private TicketListener listener;
    private String ticketCategoryName;
    private String ticketChannelName;
    private String transcriptChannelName;
    private String supportRoleName;
    private int maxOpenTickets;
    private boolean autoCloseInactive;
    private int inactiveHours;
    private boolean autoCloseWarningEnabled;
    private int autoCloseWarningMinutes;

    // Active tickets
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
    private final Map<String, String> channelToTicket = new ConcurrentHashMap<>();
    private final Map<String, Integer> userTicketCount = new ConcurrentHashMap<>();
    
    public TicketModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Tickets";
    }
    
    @Override
    public String getDescription() {
        return "Support ticket system with Discord integration";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Ticket module enabled - waiting for Discord...");
    }
    
    @Override
    public void onDiscordReady() {
        // Register Discord listener
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            listener = new TicketListener();
            plugin.getDiscordManager().getJDA().addEventListener(listener);
            
            // Create ticket panel if needed
            createTicketPanel();
            
            // Start auto-close task
            if (autoCloseInactive) {
                startAutoCloseTask();
            }
            info("Ticket system connected to Discord");
        }
    }

    @Override
    public List<SlashCommandData> getSlashCommands() {
        return Collections.singletonList(
            Commands.slash("ticket", "Support ticket management")
                .addSubcommands(
                    new SubcommandData("create", "Create a new support ticket")
                        .addOption(OptionType.STRING, "subject", "Ticket subject", false)
                        .addOption(OptionType.STRING, "priority", "Priority: LOW, MEDIUM, HIGH, URGENT", false),
                    new SubcommandData("close", "Close the current ticket channel"),
                    new SubcommandData("info", "Show your open tickets and active ticket count"),
                    new SubcommandData("priority", "Change the priority of the current ticket")
                        .addOption(OptionType.STRING, "level", "New priority: LOW, MEDIUM, HIGH, URGENT", true)
                )
                .addSubcommandGroups(
                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData("edit", "Edit the current ticket")
                        .addSubcommands(
                            new SubcommandData("title", "Edit the ticket title/subject")
                                .addOption(OptionType.STRING, "title", "New ticket title", true),
                            new SubcommandData("description", "Set or update the ticket description")
                                .addOption(OptionType.STRING, "description", "New description text", true),
                            new SubcommandData("priority", "Edit the ticket priority")
                                .addOption(OptionType.STRING, "level", "New priority: LOW, MEDIUM, HIGH, URGENT", true),
                            new SubcommandData("addbutton", "Add a custom action button to the ticket")
                                .addOption(OptionType.STRING, "label", "Button label", true)
                                .addOption(OptionType.STRING, "style", "Button style: PRIMARY, SECONDARY, SUCCESS, DANGER, LINK", true)
                                .addOption(OptionType.STRING, "target", "URL (for LINK) or custom id", false),
                            new SubcommandData("removebutton", "Remove a button by label")
                                .addOption(OptionType.STRING, "label", "Exact label of the button to remove", true)
                        )
                )
        );
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        if (!"ticket".equals(event.getName())) {
            return false;
        }

        String subcommand = event.getSubcommandName();
        Member member = event.getMember();
        if (member == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Guild Only",
                    "This command can only be used in a guild.").build())
                .setEphemeral(true).queue();
            return true;
        }

        // Subcommand group: /ticket edit <title|description|priority|addbutton|removebutton>
        if ("edit".equals(event.getSubcommandGroup())) {
            handleSlashTicketEdit(event, member, subcommand);
            return true;
        }

        if ("close".equals(subcommand)) {
            handleSlashCloseTicket(event, member);
            return true;
        }

        if ("info".equals(subcommand)) {
            handleSlashTicketInfo(event, member);
            return true;
        }

        if ("priority".equals(subcommand)) {
            handleSlashTicketPriority(event, member);
            return true;
        }

        handleSlashCreateTicket(event, member);
        return true;
    }

    private void handleSlashTicketEdit(SlashCommandInteractionEvent event, Member member, String editType) {
        Ticket ticket = resolveTicketFromEvent(event);
        if (ticket == null) return;
        if (!checkEditPermission(event, member, ticket)) return;

        switch (editType == null ? "" : editType.toLowerCase()) {
            case "title":
                handleEditTitle(event, member, ticket);
                break;
            case "description":
                handleEditDescription(event, member, ticket);
                break;
            case "priority":
                handleEditPriority(event, member, ticket);
                break;
            case "addbutton":
                handleEditAddButton(event, member, ticket);
                break;
            case "removebutton":
                handleEditRemoveButton(event, member, ticket);
                break;
            default:
                event.replyEmbeds(EmbedUtils.errorEmbed("Unknown Edit",
                        "Unknown ticket edit operation.").build())
                    .setEphemeral(true).queue();
        }
    }

    private void handleEditTitle(SlashCommandInteractionEvent event, Member member, Ticket ticket) {
        String newTitle = event.getOption("title").getAsString().trim();
        if (newTitle.isEmpty() || newTitle.length() > 100) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Invalid Title",
                    "Title must be between 1 and 100 characters.").build())
                .setEphemeral(true).queue();
            return;
        }
        String oldTitle = ticket.subject;
        ticket.subject = newTitle;
        persistSubjectChange(ticket, newTitle);
        refreshWelcomeMessage(ticket);
        updateChannelTopic(ticket);
        event.replyEmbeds(EmbedUtils.successEmbed("Title Updated",
                "Ticket title changed from **" + oldTitle + "** to **" + newTitle + "**.").build())
            .queue();
        info("Ticket #" + ticket.id + " title changed from '" + oldTitle + "' to '" + newTitle
            + "' by " + member.getEffectiveName());
        logTicketAction(ticket.id, "title-edited", member.getEffectiveName());
    }

    private void handleEditDescription(SlashCommandInteractionEvent event, Member member, Ticket ticket) {
        String newDescription = event.getOption("description").getAsString().trim();
        if (newDescription.isEmpty() || newDescription.length() > 1000) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Invalid Description",
                    "Description must be between 1 and 1000 characters.").build())
                .setEphemeral(true).queue();
            return;
        }
        ticket.description = newDescription;
        refreshWelcomeMessage(ticket);
        event.replyEmbeds(EmbedUtils.successEmbed("Description Updated",
                "Ticket description has been updated.").build()).queue();
        info("Ticket #" + ticket.id + " description updated by " + member.getEffectiveName());
        logTicketAction(ticket.id, "description-edited", member.getEffectiveName());
    }

    private void handleEditPriority(SlashCommandInteractionEvent event, Member member, Ticket ticket) {
        String levelStr = event.getOption("level").getAsString();
        TicketPriority newPriority = TicketPriority.fromString(levelStr);
        TicketPriority oldPriority = ticket.priority != null ? ticket.priority : TicketPriority.MEDIUM;
        ticket.priority = newPriority;
        refreshWelcomeMessage(ticket);
        updateChannelTopic(ticket);

        EmbedBuilder reply = EmbedUtils.successEmbed("Priority Updated",
                "Ticket #" + ticket.id + " priority changed from " + oldPriority.label + " to " + newPriority.label + ".")
            .setColor(newPriority.color);
        event.replyEmbeds(reply.build()).queue();

        TextChannel channel = event.getGuild().getTextChannelById(ticket.channelId);
        if (channel != null) {
            EmbedBuilder broadcast = EmbedUtils.infoEmbed("Priority Updated",
                    "Ticket #" + ticket.id + " is now " + newPriority.label)
                .setColor(newPriority.color);
            channel.sendMessageEmbeds(broadcast.build()).queue();
        }
        info("Ticket #" + ticket.id + " priority changed from " + oldPriority.label
            + " to " + newPriority.label + " by " + member.getEffectiveName());
        logTicketAction(ticket.id, "priority-edited", member.getEffectiveName());
    }

    private void handleEditAddButton(SlashCommandInteractionEvent event, Member member, Ticket ticket) {
        String label = event.getOption("label").getAsString().trim();
        String styleStr = event.getOption("style").getAsString().trim().toUpperCase();
        String target = event.getOption("target") != null ? event.getOption("target").getAsString() : null;

        if (label.isEmpty() || label.length() > 80) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Invalid Label",
                    "Button label must be between 1 and 80 characters.").build())
                .setEphemeral(true).queue();
            return;
        }
        // Reject duplicate labels so removebutton stays unambiguous
        if (ticket.welcomeButtons.stream().anyMatch(b -> label.equals(b.getLabel()))) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Duplicate Button",
                    "A button with that label already exists.").build())
                .setEphemeral(true).queue();
            return;
        }

        Button button;
        try {
            switch (styleStr) {
                case "LINK":
                    if (target == null || target.isEmpty()) {
                        event.replyEmbeds(EmbedUtils.errorEmbed("Missing URL",
                                "LINK style requires a URL target.").build())
                            .setEphemeral(true).queue();
                        return;
                    }
                    if (!target.startsWith("http://") && !target.startsWith("https://")) {
                        event.replyEmbeds(EmbedUtils.errorEmbed("Invalid URL",
                                "Link buttons require an http(s) URL.").build())
                            .setEphemeral(true).queue();
                        return;
                    }
                    button = Button.link(target, label);
                    break;
                case "PRIMARY":
                    button = Button.primary(sanitizeCustomId(target, label), label);
                    break;
                case "SECONDARY":
                    button = Button.secondary(sanitizeCustomId(target, label), label);
                    break;
                case "SUCCESS":
                    button = Button.success(sanitizeCustomId(target, label), label);
                    break;
                case "DANGER":
                    button = Button.danger(sanitizeCustomId(target, label), label);
                    break;
                default:
                    event.replyEmbeds(EmbedUtils.errorEmbed("Invalid Style",
                            "Style must be PRIMARY, SECONDARY, SUCCESS, DANGER, or LINK.").build())
                        .setEphemeral(true).queue();
                    return;
            }
        } catch (IllegalArgumentException e) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Invalid Button",
                    "Could not create button: " + e.getMessage()).build())
                .setEphemeral(true).queue();
            return;
        }

        // Discord caps a single action row at 5 buttons
        if (ticket.welcomeButtons.size() >= 5) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Button Limit Reached",
                    "A ticket can have at most 5 buttons. Remove one first.").build())
                .setEphemeral(true).queue();
            return;
        }

        ticket.welcomeButtons.add(button);
        refreshWelcomeMessage(ticket);
        event.replyEmbeds(EmbedUtils.successEmbed("Button Added",
                "Button **" + label + "** (" + styleStr + ") added to the ticket.").build()).queue();
        info("Ticket #" + ticket.id + " button added '" + label + "' (" + styleStr + ") by "
            + member.getEffectiveName());
        logTicketAction(ticket.id, "button-added", member.getEffectiveName());
    }

    private void handleEditRemoveButton(SlashCommandInteractionEvent event, Member member, Ticket ticket) {
        String label = event.getOption("label").getAsString().trim();
        boolean removed = ticket.welcomeButtons.removeIf(b -> label.equals(b.getLabel()));
        if (!removed) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Button Not Found",
                    "No button with label **" + label + "** exists on this ticket.").build())
                .setEphemeral(true).queue();
            return;
        }
        refreshWelcomeMessage(ticket);
        event.replyEmbeds(EmbedUtils.successEmbed("Button Removed",
                "Button **" + label + "** removed from the ticket.").build()).queue();
        info("Ticket #" + ticket.id + " button removed '" + label + "' by " + member.getEffectiveName());
        logTicketAction(ticket.id, "button-removed", member.getEffectiveName());
    }

    private String sanitizeCustomId(String target, String label) {
        if (target != null && !target.isEmpty()) {
            String trimmed = target.length() > 100 ? target.substring(0, 100) : target;
            return trimmed.replaceAll("[^a-zA-Z0-9_:-]", "_");
        }
        return ("ticket_" + ticketSafeId() + "_" + label)
            .replaceAll("[^a-zA-Z0-9_:-]", "_")
            .substring(0, Math.min(100, label.length() + 16));
    }

    // Instance helper used by sanitizeCustomId - returns a stable prefix
    private String ticketSafeId() {
        return String.valueOf(System.currentTimeMillis());
    }

    private void updateChannelTopic(Ticket ticket) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) return;
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return;
        TextChannel channel = guild.getTextChannelById(ticket.channelId);
        if (channel == null) return;
        TicketPriority priority = ticket.priority != null ? ticket.priority : TicketPriority.MEDIUM;
        String topic = "Support ticket | MC: " + ticket.minecraftName
            + " | Priority: " + priority.name()
            + " | Subject: " + ticket.subject;
        channel.getManager().setTopic(topic).queue(
            success -> debug("Updated channel topic for ticket #" + ticket.id),
            error -> warning("Failed to update topic for ticket #" + ticket.id + ": " + error.getMessage())
        );
    }

    private void handleSlashTicketInfo(SlashCommandInteractionEvent event, Member member) {
        event.deferReply(true).queue();
        plugin.getDatabaseManager().getMinecraftUuid(member.getId()).thenAccept(uuid -> {
            if (uuid == null) {
                event.getHook().sendMessageEmbeds(EmbedUtils.infoEmbed("Ticket Info",
                    "You are not verified, so you have no tickets.").build()).queue();
                return;
            }
            plugin.getDatabaseManager().getOpenTicketsCount(uuid).thenAccept(count -> {
                int active = (int) activeTickets.values().stream()
                    .filter(t -> member.getId().equals(t.userId)).count();
                StringBuilder myTickets = new StringBuilder();
                for (Ticket t : activeTickets.values()) {
                    if (!member.getId().equals(t.userId)) continue;
                    myTickets.append("• #").append(t.id)
                        .append(" [").append(t.priority != null ? t.priority.label : "MEDIUM").append("]")
                        .append(" - ").append(t.subject).append("\n");
                }
                EmbedBuilder embed = EmbedUtils.infoEmbed("Ticket Information",
                    "Active server tickets: **" + activeTickets.size() + "**\n"
                        + "Your open tickets: **" + count + "**")
                    .addField("Your Active Channels", myTickets.length() == 0
                        ? "None currently open." : myTickets.toString(), false);
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            });
        });
    }

    private void handleSlashTicketPriority(SlashCommandInteractionEvent event, Member member) {
        String channelId = event.getChannel().getId();
        String ticketId = channelToTicket.get(channelId);
        if (ticketId == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Not a Ticket Channel",
                    "This command can only be used inside an active ticket channel.").build())
                .setEphemeral(true).queue();
            return;
        }
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Ticket Not Found",
                    "This ticket is no longer active.").build())
                .setEphemeral(true).queue();
            return;
        }
        boolean isSupport = member.hasPermission(Permission.MANAGE_CHANNEL)
            || member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase(supportRoleName));
        if (!member.getId().equals(ticket.userId) && !isSupport) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                    "Only the ticket creator or support staff may change this ticket's priority.").build())
                .setEphemeral(true).queue();
            return;
        }
        String levelStr = event.getOption("level").getAsString();
        TicketPriority priority = TicketPriority.fromString(levelStr);
        ticket.priority = priority;

        EmbedBuilder reply = EmbedUtils.successEmbed("Priority Updated",
                "Ticket #" + ticket.id + " priority is now " + priority.label + ".")
            .setColor(priority.color);
        event.replyEmbeds(reply.build()).queue();
        info("Ticket #" + ticket.id + " priority changed to " + priority.label + " by " + member.getEffectiveName());

        TextChannel channel = event.getGuild().getTextChannelById(channelId);
        if (channel != null) {
            EmbedBuilder embed = EmbedUtils.infoEmbed("Priority Updated",
                "Ticket #" + ticket.id + " is now " + priority.label)
                .setColor(priority.color);
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"ticket".equals(command)) {
            return false;
        }

        Member member = event.getMember();
        if (member == null) {
            event.getMessage().reply("This command can only be used in a guild.").queue();
            return true;
        }

        String trimmed = args.trim();
        String subcommand = trimmed.isEmpty() ? "create" : trimmed.split(" ", 2)[0].toLowerCase();
        String subArgs = trimmed.isEmpty() ? "" : (trimmed.split(" ", 2).length > 1 ? trimmed.split(" ", 2)[1] : "");

        if ("close".equals(subcommand)) {
            handleTextCloseTicket(event, member);
            return true;
        }

        if ("create".equals(subcommand)) {
            handleTextCreateTicket(event, member, subArgs.trim());
            return true;
        }

        event.getMessage().reply("Usage: `!ticket create [subject]` or `!ticket close`").queue();
        return true;
    }

    private void handleTextCreateTicket(MessageReceivedEvent event, Member member, String subject) {
        String userId = member.getId();
        int currentTickets = userTicketCount.getOrDefault(userId, 0);
        if (currentTickets >= maxOpenTickets) {
            event.getMessage().reply("You have reached the maximum number of open tickets (" + maxOpenTickets + ")").queue();
            return;
        }

        final String ticketSubject = subject.isEmpty() ? "General Support" : subject;

        plugin.getDatabaseManager().getMinecraftName(userId)
            .thenAccept(minecraftName -> {
                if (minecraftName == null) {
                    event.getMessage().reply("You must verify your Minecraft account first! Use `/verify` in-game.").queue();
                    return;
                }
                event.getMessage().reply("Creating your ticket...").queue();
                createDiscordTicket(userId, minecraftName, ticketSubject);
            })
            .exceptionally(throwable -> {
                event.getMessage().reply("An error occurred while checking your verification status.").queue();
                return null;
            });
    }

    private void handleTextCloseTicket(MessageReceivedEvent event, Member member) {
        String channelId = event.getChannel().getId();
        String ticketId = channelToTicket.get(channelId);

        if (ticketId == null) {
            event.getMessage().reply("This is not a valid ticket channel!").queue();
            return;
        }

        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            event.getMessage().reply("Ticket not found.").queue();
            return;
        }

        boolean canClose = member.getId().equals(ticket.userId) ||
            member.hasPermission(Permission.MANAGE_CHANNEL) ||
            member.getRoles().stream().anyMatch(role -> role.getName().equalsIgnoreCase(supportRoleName));

        if (!canClose) {
            event.getMessage().reply("You don't have permission to close this ticket!").queue();
            return;
        }

        event.getMessage().reply("Closing ticket...").queue();
        closeTicket(ticketId, member.getEffectiveName());
    }

    private void handleSlashCreateTicket(SlashCommandInteractionEvent event, Member member) {
        String userId = member.getId();
        int currentTickets = userTicketCount.getOrDefault(userId, 0);
        if (currentTickets >= maxOpenTickets) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Limit Reached",
                    "You have reached the maximum number of open tickets (" + maxOpenTickets + ").").build())
                .setEphemeral(true).queue();
            return;
        }

        final TicketPriority priority = event.getOption("priority") != null
            ? TicketPriority.fromString(event.getOption("priority").getAsString())
            : TicketPriority.MEDIUM;

        plugin.getDatabaseManager().getMinecraftName(userId)
            .thenAccept(minecraftName -> {
                if (minecraftName == null) {
                    event.replyEmbeds(EmbedUtils.warningEmbed("Verification Required",
                            "You must verify your Minecraft account first. Use `/verify` in-game.").build())
                        .setEphemeral(true).queue();
                    return;
                }

                String subject = event.getOption("subject") != null
                    ? event.getOption("subject").getAsString()
                    : "General Support";
                event.replyEmbeds(EmbedUtils.infoEmbed("Creating Ticket",
                        "Your support ticket is being created...").build())
                    .setEphemeral(true).queue();
                createDiscordTicket(userId, minecraftName, subject, priority);
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Error checking verification status: " + throwable.getMessage());
                event.replyEmbeds(EmbedUtils.errorEmbed("Verification Check Failed",
                        "An error occurred while checking your verification status.").build())
                    .setEphemeral(true).queue();
                return null;
            });
    }

    private void handleSlashCloseTicket(SlashCommandInteractionEvent event, Member member) {
        String channelId = event.getChannel().getId();
        String ticketId = channelToTicket.get(channelId);

        if (ticketId == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Not a Ticket Channel",
                    "This command can only be used inside an active ticket channel.").build())
                .setEphemeral(true).queue();
            return;
        }

        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Ticket Not Found",
                    "This ticket is no longer active.").build())
                .setEphemeral(true).queue();
            return;
        }

        boolean canClose = member.getId().equals(ticket.userId) ||
            member.hasPermission(Permission.MANAGE_CHANNEL) ||
            member.getRoles().stream().anyMatch(role -> role.getName().equalsIgnoreCase(supportRoleName));

        if (!canClose) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                    "Only the ticket creator or support staff may close this ticket.").build())
                .setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.infoEmbed("Closing Ticket",
                "This ticket will be closed shortly.").build()).queue();
        closeTicket(ticketId, member.getEffectiveName());
    }
    
    @Override
    protected void onDisable() {
        // Unregister Discord listener
        if (listener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(listener);
        }
        
        // Save active tickets
        saveActiveTickets();
        
        info("Ticket module disabled");
    }
    
    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();

        if (config != null) {
            ticketCategoryName = config.getString("ticket-category", "Support Tickets");
            ticketChannelName = config.getString("ticket-panel-channel", config.getString("transcript-channel", "ticket-logs"));
            transcriptChannelName = config.getString("transcript-channel", "ticket-logs");
            supportRoleName = config.getString("support-role", "Support");
            maxOpenTickets = config.getInt("max-open-tickets", 3);
            autoCloseInactive = config.getInt("auto-close-hours", 48) > 0;
            inactiveHours = config.getInt("auto-close-hours", 48);
            autoCloseWarningEnabled = config.getBoolean("auto-close-warning-enabled", true);
            // Default: warn 1 hour before close
            autoCloseWarningMinutes = config.getInt("auto-close-warning-minutes", 60);
        } else {
            warning("Config section not found, using defaults");
            ticketCategoryName = "Support Tickets";
            ticketChannelName = "ticket-logs";
            transcriptChannelName = "ticket-logs";
            supportRoleName = "Support";
            maxOpenTickets = 3;
            autoCloseInactive = true;
            inactiveHours = 48;
            autoCloseWarningEnabled = true;
            autoCloseWarningMinutes = 60;
        }

        debug("Ticket category: " + ticketCategoryName);
    }
    
    /**
     * Create a new ticket
     */
    public void createTicket(Player player, String subject) {
        createTicket(player, subject, TicketPriority.MEDIUM);
    }

    public void createTicket(Player player, String subject, TicketPriority priority) {
        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(userId -> {
                if (userId == null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageManager().sendError(player, "errors.discord-not-linked"));
                    return;
                }

                // Check ticket limit
                int currentTickets = userTicketCount.getOrDefault(userId, 0);
                if (currentTickets >= maxOpenTickets) {
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%max%", String.valueOf(maxOpenTickets));
                    Bukkit.getScheduler().runTask(plugin, () ->
                        plugin.getMessageManager().sendError(player, "errors.max-tickets", placeholders));
                    return;
                }

                // Create ticket in Discord
                createDiscordTicket(userId, player.getName(), subject, priority);

                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendSuccess(player, "success.ticket-created"));
            })
            .exceptionally(throwable -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendError(player, "verification.verification-failed"));
                plugin.getLogger().warning("Failed to create ticket for " + player.getName() + ": " + throwable.getMessage());
                return null;
            });
    }
    
    private void createTicketPanel() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return;
        
        // Try global tickets channel first
        String channelId = plugin.getConfig().getString("discord.channels.tickets", "");
        TextChannel panelChannel = null;
        
        if (channelId != null && !channelId.isEmpty() && channelId.matches("\\d+")) {
            panelChannel = plugin.getDiscordManager().getTextChannelById(channelId);
        }
        
        if (panelChannel == null) {
            panelChannel = guild.getTextChannelsByName(ticketChannelName, true)
                .stream().findFirst().orElse(null);
        }
        
        if (panelChannel == null) {
            // Create the channel
            Category category = getOrCreateCategory(guild);
            panelChannel = guild.createTextChannel(ticketChannelName)
                .setParent(category)
                .setTopic("Create a support ticket")
                .complete();
        }
        
        // Make panelChannel final for lambda
        final TextChannel finalPanelChannel = panelChannel;
        
        // Clear old messages
        finalPanelChannel.getHistory().retrievePast(10).queue(messages -> {
            for (Message msg : messages) {
                if (msg.getAuthor().equals(plugin.getDiscordManager().getJDA().getSelfUser())) {
                    msg.delete().queue();
                }
            }

            // Send new modern panel
            EmbedBuilder embed = new EmbedBuilder()
                .setColor(EmbedUtils.BRAND_PRIMARY)
                .setTitle("Support Tickets")
                .setDescription(
                    "Create a ticket for help from staff.\n\n" +
                    "• Max " + maxOpenTickets + " open tickets per user\n" +
                    "• Tickets close after " + inactiveHours + " hours of inactivity\n" +
                    "• Be respectful to support staff")
                .addField("Priorities", "LOW · MEDIUM · HIGH · URGENT", false)
                .setFooter("XDiscordUltimate Tickets • Click below to create a ticket", null)
                .setTimestamp(Instant.now());

            finalPanelChannel.sendMessageEmbeds(embed.build())
                .setActionRow(
                    Button.primary("create_ticket", "Create Ticket")
                        .withEmoji(Emoji.fromUnicode("🎫"))
                )
                .queue();
        });
    }
    
    private void createDiscordTicket(String userId, String minecraftName, String subject) {
        createDiscordTicket(userId, minecraftName, subject, TicketPriority.MEDIUM);
    }

    private void createDiscordTicket(String userId, String minecraftName, String subject, TicketPriority priority) {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return;

        Member member = guild.getMemberById(userId);
        if (member == null) return;

        final TicketPriority finalPriority = priority != null ? priority : TicketPriority.MEDIUM;

        // Get Minecraft UUID for database operations
        plugin.getDatabaseManager().getMinecraftUuid(userId)
            .thenAccept(minecraftUuid -> {
                if (minecraftUuid == null) {
                    // User not properly verified
                    return;
                }

                // Check if user already has too many open tickets
                plugin.getDatabaseManager().getOpenTicketsCount(minecraftUuid)
                    .thenAccept(openCount -> {
                        if (openCount >= maxOpenTickets) {
                            return; // Already handled in button interaction
                        }

                        // Create ticket in database first
                        plugin.getDatabaseManager().createTicket(minecraftUuid, subject)
                            .thenAccept(dbTicketId -> {
                                if (dbTicketId <= 0) {
                                    plugin.getLogger().severe("Failed to create ticket in database");
                                    return;
                                }
                                
                                // Create Discord channel
                                createDiscordChannel(guild, member, userId, minecraftName, subject, dbTicketId, finalPriority);
                            });
                    });
            });
    }

    private void createDiscordChannel(Guild guild, Member member, String userId, String minecraftName,
                                       String subject, int dbTicketId, TicketPriority priority) {
        TicketPriority finalPriority = priority != null ? priority : TicketPriority.MEDIUM;

        // Create ticket channel
        Category category = getOrCreateCategory(guild);
        TextChannel ticketChannel = guild.createTextChannel("ticket-" + dbTicketId)
            .setParent(category)
            .setTopic("Support ticket for " + member.getEffectiveName() + " | MC: " + minecraftName
                + " | Priority: " + finalPriority.name())
            .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
            .addPermissionOverride(member, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
            .complete();

        // Add support role permissions
        Role supportRole = guild.getRolesByName(supportRoleName, true).stream().findFirst().orElse(null);
        if (supportRole != null) {
            ticketChannel.upsertPermissionOverride(supportRole)
                .setAllowed(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_MANAGE)
                .queue();
        }

        // Update database with channel ID
        plugin.getDatabaseManager().updateTicketChannel(dbTicketId, ticketChannel.getId());

        // Create ticket object
        Ticket ticket = new Ticket(String.valueOf(dbTicketId), userId, minecraftName, subject, ticketChannel.getId());
        ticket.dbId = dbTicketId;
        ticket.priority = finalPriority;
        activeTickets.put(String.valueOf(dbTicketId), ticket);
        channelToTicket.put(ticketChannel.getId(), String.valueOf(dbTicketId));
        userTicketCount.merge(userId, 1, Integer::sum);

        // Player avatar thumbnail if we can resolve UUID (best-effort)
        String avatarUrl = "https://minotar.net/helm/" + minecraftName + "/64.png";

        // Send welcome message - use priority color and avatar
        EmbedBuilder welcomeEmbed = buildWelcomeEmbed(ticket);
        ticket.welcomeButtons.add(Button.danger("close_ticket", "Close Ticket")
            .withEmoji(Emoji.fromUnicode("🔒")));
        ticket.welcomeButtons.add(Button.secondary("claim_ticket", "Claim Ticket")
            .withEmoji(Emoji.fromUnicode("✋")));

        ticketChannel.sendMessageEmbeds(welcomeEmbed.build())
            .setActionRow(new ArrayList<>(ticket.welcomeButtons))
            .queue(sentMessage -> {
                ticket.welcomeMessageId = sentMessage.getId();
                debug("Captured welcome message id " + ticket.welcomeMessageId + " for ticket #" + ticket.id);
            });

        // Notify support role
        if (supportRole != null) {
            ticketChannel.sendMessage(supportRole.getAsMention() + " - New ticket created! (" + finalPriority.label + ")").queue();
        }

        // Log ticket creation
        logTicketAction(String.valueOf(dbTicketId), "created", member.getEffectiveName());

        // Notify player in-game if online
        plugin.getDatabaseManager().getMinecraftUuid(userId)
            .thenAccept(uuid -> {
                if (uuid != null) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(plugin.getMessageManager().colorize("<gradient:#55FF55:#00AA00>✅ Your support ticket #" + dbTicketId + " has been created!</gradient>"));
                        player.sendMessage(plugin.getMessageManager().colorize("&eCheck Discord for updates on your ticket."));
                    }
                }
            });
    }
    
    private class TicketListener extends ListenerAdapter {
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            String buttonId = event.getComponentId();
            Member member = event.getMember();
            
            if (member == null) return;
            
            switch (buttonId) {
                case "create_ticket":
                    handleCreateTicket(event);
                    break;
                case "close_ticket":
                    handleCloseTicket(event);
                    break;
                case "claim_ticket":
                    handleClaimTicket(event);
                    break;
            }
        }
        
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            
            String channelId = event.getChannel().getId();
            String ticketId = channelToTicket.get(channelId);
            
            if (ticketId != null) {
                Ticket ticket = activeTickets.get(ticketId);
                if (ticket != null) {
                    ticket.lastActivity = System.currentTimeMillis();
                    
                    // Log message to transcript
                    ticket.addMessage(event.getAuthor().getName(), event.getMessage().getContentRaw());
                }
            }
        }
    }
    
    private void handleCreateTicket(ButtonInteractionEvent event) {
        Member member = event.getMember();
        String userId = member.getId();

        // Check ticket limit
        int currentTickets = userTicketCount.getOrDefault(userId, 0);
        if (currentTickets >= maxOpenTickets) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Limit Reached",
                    "You have reached the maximum number of open tickets (" + maxOpenTickets + ").").build())
                .setEphemeral(true).queue();
            return;
        }

        // Check if verified and get Minecraft name
        plugin.getDatabaseManager().getMinecraftName(userId)
            .thenAccept(minecraftName -> {
                if (minecraftName == null) {
                    event.replyEmbeds(EmbedUtils.warningEmbed("Verification Required",
                            "You must verify your Minecraft account first. Use `/verify` in-game.").build())
                        .setEphemeral(true).queue();
                    return;
                }

                event.replyEmbeds(EmbedUtils.infoEmbed("Creating Ticket",
                        "Your support ticket is being created...").build())
                    .setEphemeral(true).queue();
                createDiscordTicket(userId, minecraftName, "General Support");
            })
            .exceptionally(throwable -> {
                plugin.getLogger().severe("Error checking verification status: " + throwable.getMessage());
                event.replyEmbeds(EmbedUtils.errorEmbed("Verification Check Failed",
                        "An error occurred while checking your verification status.").build())
                    .setEphemeral(true).queue();
                return null;
            });
    }

    private void handleCloseTicket(ButtonInteractionEvent event) {
        String channelId = event.getChannel().getId();
        String ticketId = channelToTicket.get(channelId);

        if (ticketId == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Not a Ticket Channel",
                    "This button can only be used inside an active ticket channel.").build())
                .setEphemeral(true).queue();
            return;
        }

        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) return;

        // Check permissions
        Member member = event.getMember();
        boolean canClose = member.getId().equals(ticket.userId) ||
            member.hasPermission(Permission.MANAGE_CHANNEL) ||
            member.getRoles().stream().anyMatch(role -> role.getName().equalsIgnoreCase(supportRoleName));

        if (!canClose) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                    "Only the ticket creator or support staff may close this ticket.").build())
                .setEphemeral(true).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.infoEmbed("Closing Ticket",
                "This ticket will be closed shortly.").build()).queue();
        closeTicket(ticketId, member.getEffectiveName());
    }

    private void handleClaimTicket(ButtonInteractionEvent event) {
        Member member = event.getMember();

        // Check if member has support role
        boolean isSupport = member.getRoles().stream()
            .anyMatch(role -> role.getName().equalsIgnoreCase(supportRoleName));

        if (!isSupport) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                    "Only support staff can claim tickets.").build())
                .setEphemeral(true).queue();
            return;
        }

        String channelId = event.getChannel().getId();
        String ticketId = channelToTicket.get(channelId);

        if (ticketId == null) return;

        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) return;

        if (ticket.claimedBy != null) {
            event.replyEmbeds(EmbedUtils.warningEmbed("Already Claimed",
                    "This ticket is already claimed by " + ticket.claimedBy + ".").build())
                .setEphemeral(true).queue();
            return;
        }

        ticket.claimedBy = member.getEffectiveName();
        ticket.claimedById = member.getId();

        event.replyEmbeds(EmbedUtils.successEmbed("Ticket Claimed",
                "You have claimed this ticket.").build()).queue();

        // Update channel topic
        TextChannel channel = event.getGuild().getTextChannelById(channelId);
        if (channel != null) {
            String baseTopic = channel.getTopic() == null ? "" : channel.getTopic();
            channel.getManager().setTopic(baseTopic + " | Claimed by " + member.getEffectiveName()).queue();
        }

        logTicketAction(ticketId, "claimed", member.getEffectiveName());
    }
    
    private void closeTicket(String ticketId, String closedBy) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) return;

        channelToTicket.remove(ticket.channelId);
        userTicketCount.merge(ticket.userId, -1, Integer::sum);

        // Close ticket in database
        if (ticket.dbId > 0) {
            plugin.getDatabaseManager().closeTicket(ticket.dbId);
        }

        Guild guild = plugin.getDiscordManager().getMainGuild();
        TextChannel channel = guild != null ? guild.getTextChannelById(ticket.channelId) : null;

        // Generate transcript
        String transcript = ticket.generateTranscript();

        // Send transcript as a file attachment to the transcript channel
        sendTranscriptAttachment(ticket, transcript, closedBy);

        if (channel != null) {
            // Send closing message with a muted, dedicated color (not the red error color)
            EmbedBuilder closeEmbed = new EmbedBuilder()
                .setColor(EmbedUtils.BRAND_SECONDARY)
                .setTitle("Ticket Closed")
                .setDescription("This ticket has been closed by **" + closedBy + "**.")
                .addField("Ticket ID", ticket.id, true)
                .addField("Priority", ticket.priority != null ? ticket.priority.label : "MEDIUM", true)
                .addField("Duration", formatDuration(System.currentTimeMillis() - ticket.createdAt), true)
                .setFooter("XDiscordUltimate Tickets", null)
                .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(closeEmbed.build()).queue();

            // Save transcript to disk as well (for archival)
            saveTranscript(ticket, transcript);

            // Delete channel after delay
            channel.delete().queueAfter(10, TimeUnit.SECONDS);
        }

        // Notify player in-game if online
        plugin.getDatabaseManager().getMinecraftUuid(ticket.userId)
            .thenAccept(uuid -> {
                if (uuid != null) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline()) {
                        player.sendMessage(plugin.getMessageManager().colorize("<gradient:#FF5555:#AA0000>🔒 Your support ticket #" + ticket.id + " has been closed by " + closedBy + "</gradient>"));
                        if (ticket.claimedBy != null) {
                            player.sendMessage(plugin.getMessageManager().colorize("&7Handled by: &f" + ticket.claimedBy));
                        }
                    }
                }
            });

        logTicketAction(ticketId, "closed", closedBy);
    }

    /**
     * Send the transcript as a .txt file attachment to the configured transcript channel.
     */
    private void sendTranscriptAttachment(Ticket ticket, String transcript, String closedBy) {
        if (plugin.getDiscordManager() == null
            || !plugin.getDiscordManager().isReady()
            || plugin.getDiscordManager().getMainGuild() == null) {
            return;
        }

        Guild guild = plugin.getDiscordManager().getMainGuild();
        TextChannel transcriptChannel = guild.getTextChannelsByName(transcriptChannelName, true)
            .stream().findFirst().orElse(null);

        if (transcriptChannel == null) {
            debug("Transcript channel '" + transcriptChannelName + "' not found - skipping attachment upload");
            return;
        }

        // Write transcript to a temporary file for upload
        File tempFile = null;
        try {
            File transcriptsDir = new File(plugin.getDataFolder(), "transcripts");
            if (!transcriptsDir.exists() && !transcriptsDir.mkdirs()) {
                warning("Failed to create transcripts directory for upload");
                return;
            }
            String fileName = "ticket_" + ticket.id + "_" + System.currentTimeMillis() + ".txt";
            tempFile = new File(transcriptsDir, fileName);
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(transcript);
            }

            EmbedBuilder summary = new EmbedBuilder()
                .setColor(EmbedUtils.BRAND_PRIMARY)
                .setTitle("Ticket #" + ticket.id + " Transcript")
                .setDescription("Closed by: **" + closedBy + "**")
                .addField("Subject", ticket.subject, false)
                .addField("Minecraft", ticket.minecraftName, true)
                .addField("Priority", ticket.priority != null ? ticket.priority.label : "MEDIUM", true)
                .addField("Claimed By", ticket.claimedBy != null ? ticket.claimedBy : "Nobody", true)
                .setFooter("XDiscordUltimate Tickets", null)
                .setTimestamp(Instant.now());

            transcriptChannel.sendMessageEmbeds(summary.build())
                .addFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(tempFile))
                .queue(
                    success -> info("Uploaded transcript attachment for ticket #" + ticket.id),
                    error -> warning("Failed to upload transcript for ticket #" + ticket.id + ": " + error.getMessage())
                );
        } catch (Exception e) {
            warning("Failed to send transcript attachment for ticket #" + ticket.id + ": " + e.getMessage());
        }
    }

    private void startAutoCloseTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long inactiveMillis = inactiveHours * 60 * 60 * 1000L;
            long warningMillis = (long) autoCloseWarningMinutes * 60 * 1000L;
            long currentTime = System.currentTimeMillis();

            for (Ticket ticket : activeTickets.values()) {
                long inactiveFor = currentTime - ticket.lastActivity;
                if (inactiveFor > inactiveMillis) {
                    final String tid = ticket.id;
                    Bukkit.getScheduler().runTask(plugin, () ->
                        closeTicket(tid, "System (Inactivity)"));
                } else if (autoCloseWarningEnabled
                    && !ticket.autoCloseWarningSent
                    && inactiveFor > (inactiveMillis - warningMillis)) {
                    ticket.autoCloseWarningSent = true;
                    sendAutoCloseWarning(ticket, inactiveHours, autoCloseWarningMinutes);
                }
            }
        }, 20L * 60 * 30, 20L * 60 * 30); // Check every 30 minutes
    }

    private void sendAutoCloseWarning(Ticket ticket, int inactiveHours, int warningMinutes) {
        if (plugin.getDiscordManager() == null
            || !plugin.getDiscordManager().isReady()
            || plugin.getDiscordManager().getMainGuild() == null) {
            return;
        }
        TextChannel channel = plugin.getDiscordManager().getMainGuild()
            .getTextChannelById(ticket.channelId);
        if (channel == null) return;

        EmbedBuilder embed = EmbedUtils.warningEmbed("Auto-close Warning",
            "This ticket will be automatically closed in approximately **" + warningMinutes
                + " minute(s)** due to inactivity.")
            .addField("Ticket", "#" + ticket.id, true)
            .addField("Inactive Limit", inactiveHours + " hours", true)
            .setFooter("Reply in this channel to reset the inactivity timer", null);

        channel.sendMessageEmbeds(embed.build()).queue();
        info("Sent auto-close warning for ticket #" + ticket.id);
    }
    
    private Category getOrCreateCategory(Guild guild) {
        return guild.getCategoriesByName(ticketCategoryName, true).stream()
            .findFirst()
            .orElseGet(() -> guild.createCategory(ticketCategoryName).complete());
    }
    
    private String generateTicketId() {
        return String.format("%04d", new Random().nextInt(10000));
    }
    
    private void logTicketAction(String ticketId, String action, String user) {
        info("Ticket #" + ticketId + " " + action + " by " + user);
    }
    
    private void saveTranscript(Ticket ticket, String transcript) {
        try {
            // Create transcripts directory if it doesn't exist
            File transcriptsDir = new File(plugin.getDataFolder(), "transcripts");
            if (!transcriptsDir.exists()) {
                transcriptsDir.mkdirs();
            }
            
            // Save transcript to file
            String fileName = "ticket_" + ticket.id + "_" + System.currentTimeMillis() + ".txt";
            File transcriptFile = new File(transcriptsDir, fileName);
            
            try (java.io.FileWriter writer = new java.io.FileWriter(transcriptFile)) {
                writer.write(transcript);
            }
            
            debug("Saved transcript for ticket #" + ticket.id + " to " + fileName);
            
            // Also save messages to database
            if (ticket.dbId > 0) {
                for (TicketMessage msg : ticket.messages) {
                    plugin.getDatabaseManager().addTicketMessage(
                        ticket.dbId,
                        null, // We don't have UUID for Discord messages
                        "DISCORD",
                        msg.author + ": " + msg.content
                    );
                }
            }
            
        } catch (Exception e) {
            error("Failed to save transcript for ticket #" + ticket.id, e);
        }
    }
    
    private void saveActiveTickets() {
        // Active tickets live in Discord channels (already closed when channel is deleted)
        // and the tickets table; nothing additional to persist here.
    }
    
    private String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        return String.format("%d hours, %d minutes", hours, minutes);
    }

    /**
     * Build the welcome embed for a ticket. Used for initial send and in-place edits.
     */
    private EmbedBuilder buildWelcomeEmbed(Ticket ticket) {
        TicketPriority priority = ticket.priority != null ? ticket.priority : TicketPriority.MEDIUM;
        String avatarUrl = "https://minotar.net/helm/" + ticket.minecraftName + "/64.png";

        StringBuilder description = new StringBuilder();
        description.append("Welcome <@").append(ticket.userId).append(">!\n\n");
        description.append("**Subject:** ").append(ticket.subject).append("\n");
        description.append("**Minecraft:** ").append(ticket.minecraftName).append("\n");
        description.append("**Priority:** ").append(priority.label).append("\n");
        if (ticket.description != null && !ticket.description.isEmpty()) {
            description.append("\n**Description:**\n").append(ticket.description).append("\n");
        }
        description.append("\nA support team member will be with you shortly.\n");
        description.append("Please describe your issue in detail.");

        String title = priority.label;
        return new EmbedBuilder()
            .setColor(priority.color)
            .setTitle("Ticket #" + ticket.id + " — " + title)
            .setThumbnail(avatarUrl)
            .setDescription(description.toString())
            .addField("Ticket Information",
                "Created: <t:" + (ticket.createdAt / 1000) + ":R>\n" +
                "Status: Open\n" +
                "Priority: " + priority.label, false)
            .setFooter("XDiscordUltimate Tickets", null)
            .setTimestamp(Instant.now());
    }

    /**
     * Re-render and in-place edit the welcome message for a ticket.
     */
    private void refreshWelcomeMessage(Ticket ticket) {
        if (ticket.welcomeMessageId == null) {
            debug("Cannot refresh welcome message for ticket #" + ticket.id + " - no message id captured yet");
            return;
        }
        Guild guild = plugin.getDiscordManager() != null ? plugin.getDiscordManager().getMainGuild() : null;
        if (guild == null) return;
        TextChannel channel = guild.getTextChannelById(ticket.channelId);
        if (channel == null) return;

        try {
            channel.editMessageEmbedsById(ticket.welcomeMessageId, buildWelcomeEmbed(ticket).build())
                .setActionRow(new ArrayList<>(ticket.welcomeButtons))
                .queue(
                    success -> debug("Refreshed welcome embed for ticket #" + ticket.id),
                    error -> warning("Failed to refresh welcome embed for ticket #" + ticket.id
                        + ": " + error.getMessage())
                );
        } catch (Exception e) {
            warning("Exception while refreshing welcome embed for ticket #" + ticket.id + ": " + e.getMessage());
        }
    }

    /**
     * Resolve a ticket from a slash command event's channel.
     * Returns null and sends an ephemeral error embed if the channel is not a ticket channel.
     */
    private Ticket resolveTicketFromEvent(SlashCommandInteractionEvent event) {
        String channelId = event.getChannel().getId();
        String ticketId = channelToTicket.get(channelId);
        if (ticketId == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Not a Ticket Channel",
                    "This command can only be used inside an active ticket channel.").build())
                .setEphemeral(true).queue();
            return null;
        }
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Ticket Not Found",
                    "This ticket is no longer active.").build())
                .setEphemeral(true).queue();
            return null;
        }
        return ticket;
    }

    /**
     * Permission gate for ticket edits. Returns true if the member may edit the ticket.
     * Sends an ephemeral error embed and returns false otherwise.
     */
    private boolean checkEditPermission(SlashCommandInteractionEvent event, Member member, Ticket ticket) {
        if (member == null) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                    "This command can only be used in a guild.").build())
                .setEphemeral(true).queue();
            return false;
        }
        boolean isCreator = member.getId().equals(ticket.userId);
        boolean isSupport = member.hasPermission(Permission.MANAGE_CHANNEL)
            || member.getRoles().stream().anyMatch(r -> r.getName().equalsIgnoreCase(supportRoleName));
        if (!isCreator && !isSupport) {
            event.replyEmbeds(EmbedUtils.errorEmbed("Permission Denied",
                    "Only the ticket creator or support staff may edit this ticket.").build())
                .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    /**
     * Persist subject changes to the database when supported.
     */
    private void persistSubjectChange(Ticket ticket, String newSubject) {
        if (ticket.dbId <= 0) return;
        plugin.getDatabaseManager().executeAsync(() -> {
            String sql = "UPDATE tickets SET subject = ? WHERE id = ?";
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, newSubject);
                stmt.setInt(2, ticket.dbId);
                stmt.executeUpdate();
            }
            return null;
        });
    }
    
    /**
     * Ticket data class
     */
    private static class Ticket {
        final String id;
        final String userId;
        final String minecraftName;
        String subject;
        String description;
        final String channelId;
        String welcomeMessageId;
        final List<Button> welcomeButtons;
        final long createdAt;
        long lastActivity;
        String claimedBy;
        String claimedById;
        TicketPriority priority;
        boolean autoCloseWarningSent;
        final List<TicketMessage> messages;
        int dbId; // Database ticket ID

        Ticket(String id, String userId, String minecraftName, String subject, String channelId) {
            this.id = id;
            this.userId = userId;
            this.minecraftName = minecraftName;
            this.subject = subject;
            this.description = "";
            this.channelId = channelId;
            this.welcomeMessageId = null;
            this.welcomeButtons = new ArrayList<>();
            this.createdAt = System.currentTimeMillis();
            this.lastActivity = createdAt;
            this.priority = TicketPriority.MEDIUM;
            this.autoCloseWarningSent = false;
            this.messages = new ArrayList<>();
            this.dbId = -1;
        }

        void addMessage(String author, String content) {
            messages.add(new TicketMessage(author, content, System.currentTimeMillis()));
        }

        String generateTranscript() {
            StringBuilder transcript = new StringBuilder();
            transcript.append("Ticket #").append(id).append(" - ").append(subject).append("\n");
            transcript.append("User: ").append(minecraftName).append("\n");
            transcript.append("Priority: ").append(priority != null ? priority.label : "MEDIUM").append("\n");
            transcript.append("Created: ").append(new Date(createdAt)).append("\n");
            transcript.append("Claimed by: ").append(claimedBy != null ? claimedBy : "None").append("\n");
            transcript.append("\n--- Messages ---\n\n");

            for (TicketMessage msg : messages) {
                transcript.append("[").append(new Date(msg.timestamp)).append("] ");
                transcript.append(msg.author).append(": ").append(msg.content).append("\n");
            }

            return transcript.toString();
        }
    }
    
    private static class TicketMessage {
        final String author;
        final String content;
        final long timestamp;
        
        TicketMessage(String author, String content, long timestamp) {
            this.author = author;
            this.content = content;
            this.timestamp = timestamp;
        }
    }
}