package com.xreatlabs.xdiscordultimate.modules.welcomedm;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.modules.DependsOn;
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
@DependsOn({"verification"})
public class WelcomeDMModule extends Module implements Listener {

    private boolean sendToFirstJoinOnly;
    private boolean sendToVerifiedOnly;
    private boolean sendToAllPlayers;
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
        info("Welcome DM module enabled (first-join-only=" + sendToFirstJoinOnly
            + ", verified-only=" + sendToVerifiedOnly
            + ", send-to-all=" + sendToAllPlayers + ")");
    }

    @Override
    protected void onDisable() {
        sentPlayers.clear();
    }

    private void loadConfig() {
        ConfigurationSection config = getConfig();

        if (config == null) {
            sendToFirstJoinOnly = true;
            sendToVerifiedOnly = true;
            sendToAllPlayers = false;
            useEmbed = true;
            plainMessage = "Welcome, %player%! Your Discord account is linked.";
            embedTitle = "Welcome to %server_name%";
            embedDescription = "Hey **%player%**, welcome! Your accounts are now linked.";
            embedColor = "#55FFFF";
            embedFooter = "";
            embedThumbnail = "%player_head%";
            embedImage = "";
            embedFields = List.of(
                "Server IP|play.example.com|true",
                "Website|example.com|true",
                "Discord|discord.gg/example|true",
                "Need help?|Use /support in-game|false"
            );
            return;
        }

        sendToFirstJoinOnly = config.getBoolean("first-join-only", true);
        sendToVerifiedOnly = config.getBoolean("verified-only", true);
        sendToAllPlayers = config.getBoolean("send-to-all-players", false);
        useEmbed = config.getBoolean("use-embed", true);
        plainMessage = config.getString("plain-message", "Welcome, %player%!");

        ConfigurationSection embedConfig = config.getConfigurationSection("embed");
        if (embedConfig != null) {
            embedTitle = embedConfig.getString("title", "Welcome to %server_name%");
            embedDescription = embedConfig.getString("description", "Hey **%player%**, welcome!");
            embedColor = embedConfig.getString("color", "#55FFFF");
            embedFooter = embedConfig.getString("footer", "");
            embedThumbnail = embedConfig.getString("thumbnail", "%player_head%");
            embedImage = embedConfig.getString("image", "");
            embedFields = embedConfig.getStringList("fields");
        } else {
            embedTitle = "Welcome to %server_name%";
            embedDescription = "Hey **%player%**, welcome!";
            embedColor = "#55FFFF";
            embedFooter = "";
            embedThumbnail = "%player_head%";
            embedImage = "";
            embedFields = List.of();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (sendToAllPlayers) {
            info("Send-to-all-players is enabled - attempting welcome DM for " + player.getName()
                + " (hasPlayedBefore=" + player.hasPlayedBefore() + ")");
            attemptSend(player, true);
            return;
        }

        if (sendToFirstJoinOnly && player.hasPlayedBefore()) {
            info("Skipping welcome DM for " + player.getName() + " - first-join-only is enabled and player has played before");
            return;
        }

        attemptSend(player, false);
    }

    private void attemptSend(Player player, boolean force) {
        if (sentPlayers.contains(player.getUniqueId())) {
            debug("Already sent welcome DM to " + player.getName() + " this session");
            return;
        }

        if (plugin.getDiscordManager() == null
            || plugin.getDiscordManager().getJDA() == null
            || !plugin.getDiscordManager().isReady()) {
            info("Skipping welcome DM for " + player.getName() + " - JDA not ready yet");
            return;
        }

        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(discordId -> {
                if (discordId == null || discordId.isEmpty()) {
                    if (sendToVerifiedOnly) {
                        info("Skipping welcome DM for " + player.getName()
                            + " - player is not linked to Discord (verified-only=true)");
                    } else {
                        info("Skipping welcome DM for " + player.getName()
                            + " - no Discord ID found in database (set verified-only=false will not help if no link exists)");
                    }
                    return;
                }
                sendWelcomeDM(player, discordId, force);
            })
            .exceptionally(t -> {
                warning("Failed to look up Discord ID for " + player.getName() + ": " + t.getMessage());
                return null;
            });
    }

    private void sendWelcomeDM(Player player, String discordId, boolean force) {
        plugin.getDiscordManager().getJDA().retrieveUserById(discordId).queue(
            user -> {
                if (user == null) {
                    warning("Could not find Discord user with ID: " + discordId
                        + " (player: " + player.getName() + ")");
                    return;
                }

                sendDMToUser(user, player, force);
                sentPlayers.add(player.getUniqueId());
            },
            error -> {
                warning("Failed to retrieve Discord user " + discordId
                    + " for player " + player.getName() + ": " + error.getMessage());
            }
        );
    }

    private void sendDMToUser(User user, Player player, boolean force) {
        user.openPrivateChannel().queue(
            channel -> {
                if (useEmbed) {
                    channel.sendMessageEmbeds(buildEmbed(player, user).build()).queue(
                        success -> {
                            info("Sent welcome DM to " + player.getName() + " (Discord: " + user.getName() + ")");
                            plugin.getServer().getScheduler().runTask(plugin, () -> {
                                if (player.isOnline()) {
                                    player.sendMessage(ColorUtils.parse(
                                        "<gradient:#55FFFF:#5555FF>✉</gradient> &7A welcome message has been sent to your Discord DMs!"));
                                }
                            });
                        },
                        error -> {
                            String msg = error.getMessage();
                            if (msg != null && msg.contains("Cannot send messages to this user")) {
                                info("Cannot DM " + user.getName() + " (player: " + player.getName()
                                    + ") - recipient has DMs disabled");
                            } else {
                                warning("Failed to send welcome DM to " + user.getName()
                                    + " (player: " + player.getName() + "): " + msg);
                            }
                        }
                    );
                } else {
                    String message = replacePlaceholders(plainMessage, player, user);
                    channel.sendMessage(message).queue(
                        success -> info("Sent welcome DM (plain) to " + player.getName()
                            + " (Discord: " + user.getName() + ")"),
                        error -> warning("Failed to send welcome DM: " + error.getMessage())
                    );
                }
            },
            error -> {
                warning("Failed to open DM channel with " + user.getName()
                    + " (player: " + player.getName() + "): " + error.getMessage());
            }
        );
    }

    private EmbedBuilder buildEmbed(Player player, User user) {
        EmbedBuilder embed = new EmbedBuilder();

        embed.setTitle(replacePlaceholders(embedTitle, player, user));
        embed.setDescription(replacePlaceholders(embedDescription, player, user));

        try {
            embed.setColor(Color.decode(embedColor));
        } catch (NumberFormatException e) {
            embed.setColor(new Color(85, 255, 255));
        }

        String thumbnail = replacePlaceholders(embedThumbnail, player, user);
        if (!thumbnail.isEmpty()) {
            embed.setThumbnail(thumbnail);
        }

        String image = replacePlaceholders(embedImage, player, user);
        if (!image.isEmpty()) {
            embed.setImage(image);
        }

        for (String field : embedFields) {
            String[] parts = field.split("\\|");
            if (parts.length >= 2) {
                String name = replacePlaceholders(parts[0], player, user);
                String value = replacePlaceholders(parts[1], player, user);
                boolean inline = parts.length > 2 && Boolean.parseBoolean(parts[2]);
                embed.addField(name, value, inline);
            }
        }

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

        if (plugin.isPlaceholderAPIEnabled()) {
            result = plugin.parsePlaceholders(result, player);
        }

        return result;
    }

    /**
     * Manually send a welcome DM to a player (for testing or admin use).
     * Bypasses first-join-only and verified-only checks, but still requires
     * the player to be linked to a Discord account.
     */
    public CompletableFuture<Boolean> sendManualWelcomeDM(Player player) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (plugin.getDiscordManager() == null
            || plugin.getDiscordManager().getJDA() == null
            || !plugin.getDiscordManager().isReady()) {
            warning("Cannot send manual welcome DM - Discord bot not ready");
            future.complete(false);
            return future;
        }

        info("Manual welcome DM triggered for " + player.getName());

        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(discordId -> {
                if (discordId == null || discordId.isEmpty()) {
                    info("Cannot send manual welcome DM to " + player.getName()
                        + " - player is not linked to a Discord account");
                    future.complete(false);
                    return;
                }
                plugin.getDiscordManager().getJDA().retrieveUserById(discordId).queue(
                    user -> {
                        if (user == null) {
                            warning("Cannot send manual welcome DM - Discord user not found: " + discordId);
                            future.complete(false);
                            return;
                        }
                        sendDMToUser(user, player, true);
                        future.complete(true);
                    },
                    error -> {
                        warning("Failed to retrieve Discord user for manual welcome DM: " + error.getMessage());
                        future.complete(false);
                    }
                );
            })
            .exceptionally(t -> {
                warning("Failed manual welcome DM for " + player.getName() + ": " + t.getMessage());
                future.complete(false);
                return null;
            });

        return future;
    }
}
