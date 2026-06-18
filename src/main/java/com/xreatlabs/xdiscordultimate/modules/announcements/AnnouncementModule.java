package com.xreatlabs.xdiscordultimate.modules.announcements;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AnnouncementModule extends Module implements Listener {
    
    // Announcement types
    private final Map<String, Announcement> announcements = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> scheduledAnnouncements = new ConcurrentHashMap<>();
    private final Set<UUID> mutedPlayers = ConcurrentHashMap.newKeySet();
    
    // Configuration
    private String announcementChannel;
    private boolean enableAutoAnnouncements;
    private boolean enableJoinAnnouncements;
    private boolean enableDiscordSync;
    private String announcementPrefix;
    private Sound announcementSound;
    
    public AnnouncementModule(XDiscordUltimate plugin) {
        super(plugin);
    }
    
    @Override
    public String getName() {
        return "Announcements";
    }
    
    @Override
    public String getDescription() {
        return "Comprehensive announcement broadcasting system with scheduling";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        loadAnnouncements();
        
        // Register events
        Bukkit.getPluginManager().registerEvents(this, plugin);
        
        // Start auto announcements
        if (enableAutoAnnouncements) {
            startAutoAnnouncements();
        }
        
        info("Announcements module enabled with " + announcements.size() + " announcements");
    }
    
    @Override
    protected void onDisable() {
        // Cancel all scheduled tasks
        scheduledAnnouncements.values().forEach(BukkitTask::cancel);
        scheduledAnnouncements.clear();
        
        // Save muted players
        saveMutedPlayers();
        
        info("Announcements module disabled");
    }

    @Override
    public List<SlashCommandData> getSlashCommands() {
        return Collections.singletonList(
            Commands.slash("announcement", "Server announcements")
                .addSubcommands(
                    new SubcommandData("list", "View scheduled announcements"),
                    new SubcommandData("send", "Send a custom announcement")
                        .addOption(OptionType.STRING, "message", "Announcement message", true)
                        .addOption(OptionType.STRING, "title", "Announcement title", false),
                    new SubcommandData("test", "Send a test announcement to verify the module is working")
                )
        );
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        if (!"announcement".equals(event.getName())) {
            return false;
        }

        String subcommand = event.getSubcommandName();
        if ("list".equals(subcommand)) {
            listAnnouncementsSlash(event);
            return true;
        }

        if ("send".equals(subcommand)) {
            sendAnnouncementSlash(event);
            return true;
        }

        if ("test".equals(subcommand)) {
            handleTestSlash(event);
            return true;
        }

        return false;
    }

    private void handleTestSlash(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !canSendAnnouncements(member)) {
            event.reply("You don't have permission to send test announcements!").setEphemeral(true).queue();
            return;
        }

        event.reply("📣 Sending test announcement...").setEphemeral(true).queue();
        String testMessage = "This is a **test announcement** from XDiscordUltimate. "
            + "If you can see this in Minecraft chat and the configured Discord channel, "
            + "the announcement module is working correctly!";
        makeAnnouncement(null, "Test Announcement", testMessage);
        info("Test announcement dispatched by " + event.getUser().getName());
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"announcement".equals(command)) {
            return false;
        }

        String trimmed = args.trim();
        String subcommand = trimmed.isEmpty() ? "list" : trimmed.split(" ", 2)[0].toLowerCase();
        String subArgs = trimmed.isEmpty() ? "" : (trimmed.split(" ", 2).length > 1 ? trimmed.split(" ", 2)[1] : "");

        if ("list".equals(subcommand)) {
            event.getChannel().sendMessageEmbeds(buildAnnouncementsListEmbed().build()).queue();
            return true;
        }

        if ("send".equals(subcommand)) {
            sendAnnouncementText(event, subArgs.trim());
            return true;
        }

        event.getMessage().reply("Usage: `!announcement list` or `!announcement send <message>`").queue();
        return true;
    }

    private void sendAnnouncementText(MessageReceivedEvent event, String args) {
        if (args.trim().isEmpty()) {
            event.getMessage().reply("Usage: `!announcement send <message>`").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null || !canSendAnnouncements(member)) {
            event.getMessage().reply("You don't have permission to send announcements!").queue();
            return;
        }

        makeAnnouncement(null, "Announcement", args.trim());
        event.getMessage().reply("Announcement sent to all players!").queue();
    }

    private boolean canSendAnnouncements(Member member) {
        java.util.List<String> adminIds = plugin.getConfig().getStringList("adminIDs");
        if (adminIds.contains(member.getId())) {
            return true;
        }

        java.util.List<String> allowedRoles = plugin.getConfig().getStringList("announcements.allowed-roles");
        final java.util.List<String> announceRoles = (allowedRoles == null || allowedRoles.isEmpty())
            ? java.util.Arrays.asList("admin", "moderator", "staff")
            : allowedRoles;

        return member.getRoles().stream().anyMatch(role -> {
            String roleName = role.getName().toLowerCase();
            String roleId = role.getId();
            return announceRoles.stream().anyMatch(allowed ->
                allowed.equalsIgnoreCase(roleName) || allowed.equals(roleId));
        });
    }

    private void listAnnouncementsSlash(SlashCommandInteractionEvent event) {
        event.replyEmbeds(buildAnnouncementsListEmbed().build()).setEphemeral(true).queue();
    }

    private EmbedBuilder buildAnnouncementsListEmbed() {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("📢 Scheduled Announcements")
            .setColor(Color.YELLOW)
            .setFooter("XDiscordUltimate • " + announcements.size() + " announcement(s) configured", null)
            .setTimestamp(Instant.now());

        if (announcements.isEmpty()) {
            embed.setDescription("No scheduled announcements configured.\nUse `/announce test` to test the module.");
            return embed;
        }

        for (Announcement announcement : announcements.values()) {
            String status = announcement.enabled ? "✅" : "❌";
            String destinations = (announcement.sendToMinecraft ? "MC " : "")
                + (announcement.sendToDiscord ? "Discord" : "");
            if (destinations.isEmpty()) destinations = "None";

            String fieldName = status + " " + announcement.id + " — "
                + (announcement.title.isEmpty() ? "(no title)" : announcement.title);
            String fieldValue = "Interval: `" + announcement.interval + "s` • "
                + "Targets: `" + destinations + "` • "
                + "Messages: `" + announcement.messages.size() + "`";
            embed.addField(fieldName, fieldValue, false);
        }

        return embed;
    }

    private void sendAnnouncementSlash(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !canSendAnnouncements(member)) {
            event.reply("You don't have permission to send announcements!").setEphemeral(true).queue();
            return;
        }

        String message = event.getOption("message").getAsString();
        String title = event.getOption("title") != null ? event.getOption("title").getAsString() : "Announcement";
        makeAnnouncement(null, title, message);
        event.reply("Announcement sent!\n**Title:** " + title + "\n**Message:** " + message)
            .setEphemeral(true)
            .queue();
    }
    
    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        
        if (config != null) {
            announcementChannel = config.getString("default-channel", "announcements");
            enableAutoAnnouncements = true;
            enableJoinAnnouncements = true;
            enableDiscordSync = config.getBoolean("send-to-discord", false);
            announcementPrefix = ColorUtils.parse("<gradient:#FFD700:#FFA500>✦ Announcement</gradient> &8» &r");
        } else {
            warning("Config section not found, using defaults");
            announcementChannel = "announcements";
            enableAutoAnnouncements = true;
            enableJoinAnnouncements = true;
            enableDiscordSync = false;
            announcementPrefix = ColorUtils.parse("<gradient:#FFD700:#FFA500>✦ Announcement</gradient> &8» &r");
        }
        
        announcementSound = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        debug("Announcement channel: " + announcementChannel);
    }
    
    private void loadAnnouncements() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        
        // First try to load from "scheduled" section (new format)
        ConfigurationSection section = config != null ? config.getConfigurationSection("scheduled") : null;
        
        // Fallback to "announcements" section (old format)
        if (section == null && config != null) {
            section = config.getConfigurationSection("announcements");
        }
        
        if (section == null) {
            info("No announcements configured.");
            return;
        }
        
        for (String key : section.getKeys(false)) {
            ConfigurationSection announcementSection = section.getConfigurationSection(key);
            if (announcementSection == null) continue;
            
            Announcement announcement = new Announcement(
                key,
                announcementSection.getString("title", ""),
                announcementSection.getStringList("messages"),
                announcementSection.getInt("interval", 300),
                announcementSection.getBoolean("enabled", true),
                announcementSection.getString("permission", null),
                announcementSection.getBoolean("minecraft", true),  // Send to Minecraft players
                announcementSection.getBoolean("discord", true),    // Send to Discord
                announcementSection.getString("discord-color", "#FFD700")
            );
            
            announcements.put(key, announcement);
            debug("Loaded announcement: " + key + " (MC: " + announcement.sendToMinecraft + ", Discord: " + announcement.sendToDiscord + ")");
        }
    }
    
    private void startAutoAnnouncements() {
        for (Announcement announcement : announcements.values()) {
            if (!announcement.enabled || announcement.interval <= 0) continue;
            
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    broadcastAnnouncement(announcement);
                }
            }.runTaskTimer(plugin, 20L * announcement.interval, 20L * announcement.interval);
            
            scheduledAnnouncements.put(announcement.id, task);
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enableJoinAnnouncements) return;
        
        Player player = event.getPlayer();
        
        // Send welcome announcement after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    Announcement welcome = announcements.get("welcome");
                    if (welcome != null && welcome.enabled) {
                        sendAnnouncementToPlayer(player, welcome);
                    }
                }
            }
        }.runTaskLater(plugin, 60L); // 3 seconds delay
    }
    
    /**
     * Make a custom announcement (broadcasts to both Minecraft and Discord)
     */
    public void makeAnnouncement(Player sender, String title, String message) {
        makeAnnouncement(sender, title, message, true, true);
    }
    
    /**
     * Make a custom announcement with control over destinations
     * @param sender The player sending the announcement (can be null)
     * @param title The announcement title
     * @param message The announcement message (use \\n for new lines)
     * @param toMinecraft Send to Minecraft players
     * @param toDiscord Send to Discord channel
     */
    public void makeAnnouncement(Player sender, String title, String message, boolean toMinecraft, boolean toDiscord) {
        List<String> messages = Arrays.asList(message.split("\\\\n"));
        
        Announcement customAnnouncement = new Announcement(
            "custom",
            title,
            messages,
            0,
            true,
            null,
            toMinecraft,
            toDiscord && enableDiscordSync,
            "#FF6347"
        );
        
        broadcastAnnouncement(customAnnouncement);
        
        if (sender != null) {
            String destinations = "";
            if (toMinecraft && toDiscord) destinations = "Minecraft & Discord";
            else if (toMinecraft) destinations = "Minecraft only";
            else if (toDiscord) destinations = "Discord only";
            plugin.getMessageManager().sendSuccess(sender, "Announcement sent to " + destinations + "!");
        }
    }
    
    /**
     * Broadcast a scheduled announcement
     */
    public void broadcastScheduledAnnouncement(String announcementId) {
        Announcement announcement = announcements.get(announcementId);
        if (announcement == null) {
            warning("Announcement not found: " + announcementId);
            return;
        }
        
        broadcastAnnouncement(announcement);
    }
    
    private void broadcastAnnouncement(Announcement announcement) {
        // Send to Minecraft (if enabled for this announcement)
        if (announcement.sendToMinecraft) {
            sendMinecraftAnnouncement(announcement);
        }

        // Send to Discord (if enabled for this announcement)
        if (enableDiscordSync && announcement.sendToDiscord) {
            sendDiscordAnnouncement(announcement);
        }

        info("Delivered announcement '" + announcement.id + "'"
            + " (MC=" + (announcement.sendToMinecraft)
            + ", Discord=" + (enableDiscordSync && announcement.sendToDiscord) + ")");
    }
    
    private void sendMinecraftAnnouncement(Announcement announcement) {
        // Build the message with simple formatting (no complex gradients for compatibility)
        List<String> formattedMessages = new ArrayList<>();

        // Calculate title width for centering
        String titleText = announcement.title.isEmpty() ? "ANNOUNCEMENT" : announcement.title;
        int titleWidth = titleText.length();
        int totalWidth = 40; // Reduced from 46 for better chat fit
        int padding = (totalWidth - titleWidth - 4) / 2; // 4 for "  * " and "  *"

        // Top border
        formattedMessages.add(ColorUtils.parse("&8&l╔════════════════════════════════════╗"));

        // Title line with centered text and star icons
        if (!announcement.title.isEmpty()) {
            StringBuilder titleLine = new StringBuilder();
            titleLine.append("&8&l║ &6&l⭐ ");
            titleLine.append(titleText);
            titleLine.append(" &6&l⭐ &8&l║");
            formattedMessages.add(ColorUtils.parse(titleLine.toString()));
        }

        // Divider line
        formattedMessages.add(ColorUtils.parse("&8&l╠════════════════════════════════════╣"));
        formattedMessages.add("");

        // Add messages (simple & color codes for compatibility)
        for (String message : announcement.messages) {
            // Wrap message with borders
            String wrapped = wrapMessageInBorder(message, totalWidth);
            formattedMessages.add(ColorUtils.parse(wrapped));
            formattedMessages.add("");
        }

        // Bottom border
        formattedMessages.add(ColorUtils.parse("&8&l╚════════════════════════════════════╝"));

        // Send to players
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Check if player has muted announcements
            if (mutedPlayers.contains(player.getUniqueId())) {
                continue;
            }

            // Check permission
            if (announcement.permission != null && !player.hasPermission(announcement.permission)) {
                continue;
            }

            // Send messages
            for (String line : formattedMessages) {
                player.sendMessage(line);
            }

            // Play sound
            if (announcementSound != null) {
                player.playSound(player.getLocation(), announcementSound, 1.0f, 1.0f);
            }
        }
    }

    /**
     * Wrap a message in a bordered box format
     * Note: Returns raw color codes - caller must parse the result
     */
    private String wrapMessageInBorder(String message, int totalWidth) {
        StringBuilder sb = new StringBuilder();
        sb.append("&8&l║ &e");
        sb.append(message);
        // Calculate remaining space (strip color codes to get actual visible length)
        String strippedMessage = ChatColor.stripColor(message);
        int remaining = totalWidth - 5 - strippedMessage.length();
        sb.append("&8&l");
        for (int i = 0; i < Math.max(0, remaining); i++) sb.append(" ");
        sb.append("║");
        return sb.toString();
    }
    
    private void sendAnnouncementToPlayer(Player player, Announcement announcement) {
        if (mutedPlayers.contains(player.getUniqueId())) {
            return;
        }
        
        if (announcement.permission != null && !player.hasPermission(announcement.permission)) {
            return;
        }
        
        // Build the message
        player.sendMessage(ColorUtils.parse("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        if (!announcement.title.isEmpty()) {
            player.sendMessage(announcementPrefix + ColorUtils.parse("&e" + announcement.title));
            player.sendMessage("");
        }
        
        for (String message : announcement.messages) {
            player.sendMessage(ColorUtils.parse(message));
        }
        
        player.sendMessage(ColorUtils.parse("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        
        if (announcementSound != null) {
            player.playSound(player.getLocation(), announcementSound, 1.0f, 1.0f);
        }
    }
    
    private void sendDiscordAnnouncement(Announcement announcement) {
        TextChannel channel = getAnnouncementChannel();
        if (channel == null) return;

        Color embedColor;
        try {
            embedColor = Color.decode(announcement.discordColor);
        } catch (NumberFormatException e) {
            embedColor = Color.YELLOW;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(embedColor)
            .setTitle("📢 " + (announcement.title.isEmpty() ? "Server Announcement" : announcement.title))
            .setDescription(String.join("\n", announcement.messages)
                .replaceAll("&[0-9a-fk-or]", "")) // Remove color codes
            .setTimestamp(Instant.now())
            .setFooter("Server Announcement", plugin.getServer().getServerIcon() != null ?
                "attachment://server-icon.png" : null);

        // Use a LinkedHashSet to store unique URLs while preserving insertion order.
        Set<String> urls = new LinkedHashSet<>();
        for (String message : announcement.messages) {
            // The extractUrl method should handle stripping color codes and finding a valid URL.
            String url = extractUrl(message, null);
            if (url != null) {
                urls.add(url);
            }
        }

        List<Button> buttons = new ArrayList<>();
        if (!urls.isEmpty()) {
            for (String url : urls) {
                if (buttons.size() >= 5) break; // Discord allows a maximum of 5 buttons per action row.

                String label = "Open Link"; // Default label
                Emoji emoji = null;

                // Determine button label and emoji based on URL content.
                if (url.contains("discord.gg/")) {
                    label = "Join Discord";
                    emoji = Emoji.fromUnicode("💬");
                } else if (url.contains("store")) {
                    label = "Visit Store";
                    emoji = Emoji.fromUnicode("🛒");
                } else if (url.contains("vote")) {
                    label = "Vote Now";
                    emoji = Emoji.fromUnicode("🗳️");
                } else if (url.contains("forum")) {
                    label = "Visit Forum";
                    emoji = Emoji.fromUnicode("🌐");
                }

                try {
                    Button button = Button.link(url, label);
                    if (emoji != null) {
                        button = button.withEmoji(emoji);
                    }
                    buttons.add(button);
                } catch (IllegalArgumentException e) {
                    warning("Invalid URL for button: " + url + " - " + e.getMessage());
                }
            }
        }

        if (buttons.isEmpty()) {
            channel.sendMessageEmbeds(embed.build()).queue();
        } else {
            channel.sendMessageEmbeds(embed.build())
                .setActionRow(buttons) // No need for subList as we already limited the size
                .queue();
        }
    }

    private String extractUrl(String message, String contains) {
        // Strip color codes to prevent invalid characters in URLs
        String cleanMessage = message.replaceAll("&[0-9a-fk-or]", "");
        String[] words = cleanMessage.split(" ");

        for (String word : words) {
            // Check if the word is a full URL
            if (word.startsWith("http://") || word.startsWith("https://")) {
                if (contains == null || word.contains(contains)) {
                    return word;
                }
            }
            // Check if the word looks like a domain/URL path
            else if (word.contains(".") && (contains == null || word.contains(contains))) {
                // Basic regex to match domain-like strings (e.g., example.com, store.example.com/path)
                if (word.matches("([\\w-]+\\.)+[\\w-]+(/[\\w-./?%&=]*)?")) {
                    return "https://" + word;
                }
            }
        }
        return null;
    }
    
    private TextChannel getAnnouncementChannel() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return null;
        }
        
        return plugin.getDiscordManager().getMainGuild()
            .getTextChannelsByName(announcementChannel, true)
            .stream()
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Toggle announcement mute for a player
     */
    public void toggleAnnouncementMute(Player player) {
        UUID uuid = player.getUniqueId();

        if (mutedPlayers.contains(uuid)) {
            mutedPlayers.remove(uuid);
            plugin.getMessageManager().sendSuccess(player, "success.announcements-unmuted");
        } else {
            mutedPlayers.add(uuid);
            plugin.getMessageManager().sendSuccess(player, "success.announcements-muted");
        }
    }
    
    /**
     * List all announcements
     */
    public void listAnnouncements(Player player) {
        player.sendMessage(ColorUtils.parse("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        player.sendMessage(ColorUtils.parse("&e&lAvailable Announcements:"));
        player.sendMessage("");
        
        for (Announcement announcement : announcements.values()) {
            String status = announcement.enabled ? "&a✓" : "&c✗";
            String interval = announcement.interval > 0 ? 
                " &7(every " + announcement.interval + "s)" : "";
            
            // Show destinations
            String destinations = "";
            if (announcement.sendToMinecraft && announcement.sendToDiscord) {
                destinations = " &b[MC+DC]";
            } else if (announcement.sendToMinecraft) {
                destinations = " &a[MC]";
            } else if (announcement.sendToDiscord) {
                destinations = " &9[DC]";
            }
            
            player.sendMessage(ColorUtils.parse(status + " &f" + announcement.id + interval + destinations));
            
            if (!announcement.title.isEmpty()) {
                player.sendMessage(ColorUtils.parse("  &7" + announcement.title));
            }
        }
        
        player.sendMessage("");
        player.sendMessage(ColorUtils.parse("&7[MC] = Minecraft only, [DC] = Discord only, [MC+DC] = Both"));
        player.sendMessage(ColorUtils.parse("<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
    }
    
    /**
     * Enable or disable an announcement
     */
    public void toggleAnnouncement(String announcementId, boolean enable) {
        Announcement announcement = announcements.get(announcementId);
        if (announcement == null) return;
        
        announcement.enabled = enable;
        
        // Update scheduled task
        if (enable && announcement.interval > 0 && enableAutoAnnouncements) {
            // Cancel existing task if any
            BukkitTask existingTask = scheduledAnnouncements.remove(announcementId);
            if (existingTask != null) {
                existingTask.cancel();
            }
            
            // Create new task
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    broadcastAnnouncement(announcement);
                }
            }.runTaskTimer(plugin, 20L * announcement.interval, 20L * announcement.interval);
            
            scheduledAnnouncements.put(announcementId, task);
        } else {
            // Cancel task
            BukkitTask task = scheduledAnnouncements.remove(announcementId);
            if (task != null) {
                task.cancel();
            }
        }
    }
    
    private void saveMutedPlayers() {
        List<String> mutedList = mutedPlayers.stream()
            .map(UUID::toString)
            .collect(Collectors.toList());

        getConfig().set("muted-players", mutedList);
        plugin.saveConfig();
    }
    
    /**
     * Announcement data class
     */
    private static class Announcement {
        final String id;
        final String title;
        final List<String> messages;
        final int interval;
        boolean enabled;
        final String permission;
        final boolean sendToMinecraft;
        final boolean sendToDiscord;
        final String discordColor;
        
        Announcement(String id, String title, List<String> messages, int interval, 
                    boolean enabled, String permission, boolean sendToMinecraft, 
                    boolean sendToDiscord, String discordColor) {
            this.id = id;
            this.title = title;
            this.messages = new ArrayList<>(messages);
            this.interval = interval;
            this.enabled = enabled;
            this.permission = permission;
            this.sendToMinecraft = sendToMinecraft;
            this.sendToDiscord = sendToDiscord;
            this.discordColor = discordColor;
        }
    }
}