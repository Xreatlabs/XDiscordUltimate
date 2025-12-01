package com.xreatlabs.xdiscordultimate.modules.welcomedm;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.awt.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Sends a configurable welcome DM to first-time players who are linked to Discord
 */
public class WelcomeDMModule extends Module implements Listener {
    
    private boolean sendToFirstJoinOnly;
    private boolean sendToVerifiedOnly;
    private boolean useEmbed;
    private String plainMessage;
    private String embedTitle;
    private String embedDescription;
    private String embedColor;
    private String embedFooter;
    private String embedThumbnail;
    private String embedImage;
    private List<String> embedFields;
    private Set<UUID> sentPlayers;
    
    public WelcomeDMModule(XDiscordUltimate plugin) {
        super(plugin);
        this.sentPlayers = new HashSet<>();
    }
    
    @Override
    public String getName() {
        return "Welcome DM";
    }
    
    @Override
    public String getDescription() {
        return "Sends welcome DM to new players on Discord";
    }
    
    @Override
    protected void onEnable() {
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        info("Welcome DM module enabled");
    }
    
    @Override
    protected void onDisable() {
        sentPlayers.clear();
    }
    
    private void loadConfig() {
        ConfigurationSection config = getConfig();
        
        if (config == null) {
            // Set defaults
            sendToFirstJoinOnly = true;
            sendToVerifiedOnly = true;
            useEmbed = true;
            plainMessage = "Welcome to our server, %player%! Thanks for linking your Discord account.";
            embedTitle = "Welcome to %server_name%!";
            embedDescription = "Hey **%player%**! Welcome to our Minecraft server!\n\nThanks for linking your Discord account. Here's some useful info:";
            embedColor = "#55FFFF";
            embedFooter = "Sent via XDiscordUltimate";
            embedThumbnail = "%player_head%";
            embedImage = "";
            embedFields = List.of(
                "Server IP|play.example.com|true",
                "Website|https://example.com|true",
                "Discord|discord.gg/example|true",
                "Need Help?|Create a ticket with /support in-game or use the ticket system in Discord!|false"
            );
            return;
        }
        
        sendToFirstJoinOnly = config.getBoolean("first-join-only", true);
        sendToVerifiedOnly = config.getBoolean("verified-only", true);
        useEmbed = config.getBoolean("use-embed", true);
        plainMessage = config.getString("plain-message", "Welcome to our server, %player%!");
        
        ConfigurationSection embedConfig = config.getConfigurationSection("embed");
        if (embedConfig != null) {
            embedTitle = embedConfig.getString("title", "Welcome to %server_name%!");
            embedDescription = embedConfig.getString("description", "Hey **%player%**! Welcome to our server!");
            embedColor = embedConfig.getString("color", "#55FFFF");
            embedFooter = embedConfig.getString("footer", "Sent via XDiscordUltimate");
            embedThumbnail = embedConfig.getString("thumbnail", "%player_head%");
            embedImage = embedConfig.getString("image", "");
            embedFields = embedConfig.getStringList("fields");
        } else {
            embedTitle = "Welcome to %server_name%!";
            embedDescription = "Hey **%player%**! Welcome to our server!";
            embedColor = "#55FFFF";
            embedFooter = "Sent via XDiscordUltimate";
            embedThumbnail = "%player_head%";
            embedImage = "";
            embedFields = List.of();
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // Check if first join only
        if (sendToFirstJoinOnly && player.hasPlayedBefore()) {
            debug("Player " + player.getName() + " has played before, skipping DM");
            return;
        }
        
        // Prevent duplicate DMs in same session
        if (sentPlayers.contains(player.getUniqueId())) {
            return;
        }
        
        // Check if verified only - if so, check if they're linked
        if (sendToVerifiedOnly) {
            checkAndSendDM(player);
        } else {
            // Send to all players - but we need Discord ID
            checkAndSendDM(player);
        }
    }
    
    private void checkAndSendDM(Player player) {
        // Get Discord ID from database
        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(discordId -> {
                if (discordId != null && !discordId.isEmpty()) {
                    sendWelcomeDM(player, discordId);
                } else {
                    debug("Player " + player.getName() + " is not linked to Discord");
                }
            });
    }
    
    private void sendWelcomeDM(Player player, String discordId) {
        if (plugin.getDiscordManager().getJDA() == null) {
            warning("Cannot send welcome DM - Discord bot not connected");
            return;
        }
        
        plugin.getDiscordManager().getJDA().retrieveUserById(discordId).queue(
            user -> {
                if (user == null) {
                    warning("Could not find Discord user with ID: " + discordId);
                    return;
                }
                
                sendDMToUser(user, player);
                sentPlayers.add(player.getUniqueId());
            },
            error -> {
                warning("Failed to retrieve Discord user: " + error.getMessage());
            }
        );
    }
    
    private void sendDMToUser(User user, Player player) {
        user.openPrivateChannel().queue(
            channel -> {
                if (useEmbed) {
                    channel.sendMessageEmbeds(buildEmbed(player, user).build()).queue(
                        success -> {
                            info("Sent welcome DM to " + player.getName() + " (" + user.getName() + ")");
                            // Optionally notify player in-game
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    player.sendMessage(ColorUtils.parse(
                                        "<gradient:#55FFFF:#5555FF>✉</gradient> &7A welcome message has been sent to your Discord DMs!"));
                                }
                            });
                        },
                        error -> {
                            if (error.getMessage().contains("Cannot send messages to this user")) {
                                debug("User " + user.getName() + " has DMs disabled");
                            } else {
                                warning("Failed to send DM to " + user.getName() + ": " + error.getMessage());
                            }
                        }
                    );
                } else {
                    String message = replacePlaceholders(plainMessage, player, user);
                    channel.sendMessage(message).queue(
                        success -> info("Sent welcome DM to " + player.getName()),
                        error -> warning("Failed to send DM: " + error.getMessage())
                    );
                }
            },
            error -> {
                warning("Failed to open DM channel with " + user.getName() + ": " + error.getMessage());
            }
        );
    }
    
    private EmbedBuilder buildEmbed(Player player, User user) {
        EmbedBuilder embed = new EmbedBuilder();
        
        // Title
        embed.setTitle(replacePlaceholders(embedTitle, player, user));
        
        // Description
        embed.setDescription(replacePlaceholders(embedDescription, player, user));
        
        // Color
        try {
            embed.setColor(Color.decode(embedColor));
        } catch (NumberFormatException e) {
            embed.setColor(new Color(85, 255, 255));
        }
        
        // Thumbnail (player head)
        String thumbnail = replacePlaceholders(embedThumbnail, player, user);
        if (!thumbnail.isEmpty()) {
            embed.setThumbnail(thumbnail);
        }
        
        // Image
        String image = replacePlaceholders(embedImage, player, user);
        if (!image.isEmpty()) {
            embed.setImage(image);
        }
        
        // Fields
        for (String field : embedFields) {
            String[] parts = field.split("\\|");
            if (parts.length >= 2) {
                String name = replacePlaceholders(parts[0], player, user);
                String value = replacePlaceholders(parts[1], player, user);
                boolean inline = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                embed.addField(name, value, inline);
            }
        }
        
        // Footer
        embed.setFooter(replacePlaceholders(embedFooter, player, user));
        embed.setTimestamp(Instant.now());
        
        return embed;
    }
    
    private String replacePlaceholders(String text, Player player, User user) {
        if (text == null) return "";
        
        String serverName = plugin.getServer().getName();
        if (serverName.equals("Unknown Server")) {
            serverName = plugin.getServer().getMotd();
            if (serverName.length() > 32) {
                serverName = serverName.substring(0, 32);
            }
        }
        
        String result = text
            .replace("%player%", player.getName())
            .replace("%player_name%", player.getName())
            .replace("%player_uuid%", player.getUniqueId().toString())
            .replace("%player_head%", "https://crafatar.com/avatars/" + player.getUniqueId() + "?overlay=true")
            .replace("%player_body%", "https://crafatar.com/renders/body/" + player.getUniqueId() + "?overlay=true")
            .replace("%discord_name%", user.getName())
            .replace("%discord_id%", user.getId())
            .replace("%discord_mention%", user.getAsMention())
            .replace("%discord_avatar%", user.getEffectiveAvatarUrl())
            .replace("%server_name%", serverName)
            .replace("%server_ip%", plugin.getServer().getIp().isEmpty() ? "localhost" : plugin.getServer().getIp())
            .replace("%server_port%", String.valueOf(plugin.getServer().getPort()))
            .replace("%online_players%", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
            .replace("%max_players%", String.valueOf(plugin.getServer().getMaxPlayers()));
        
        // Parse PlaceholderAPI if available
        if (plugin.isPlaceholderAPIEnabled()) {
            result = plugin.parsePlaceholders(result, player);
        }
        
        return result;
    }
    
    /**
     * Manually send a welcome DM to a player (for testing or admin use)
     */
    public CompletableFuture<Boolean> sendManualWelcomeDM(Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        
        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(discordId -> {
                if (discordId != null && !discordId.isEmpty()) {
                    plugin.getDiscordManager().getJDA().retrieveUserById(discordId).queue(
                        user -> {
                            sendDMToUser(user, player);
                            future.complete(true);
                        },
                        error -> {
                            future.complete(false);
                        }
                    );
                } else {
                    future.complete(false);
                }
            });
        
        return future;
    }
}
