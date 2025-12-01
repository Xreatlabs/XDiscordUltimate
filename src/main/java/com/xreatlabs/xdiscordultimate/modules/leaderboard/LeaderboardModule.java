package com.xreatlabs.xdiscordultimate.modules.leaderboard;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LeaderboardModule extends Module {

    private String channelId;
    private String messageId;
    private int updateInterval;
    private int topPlayersCount;
    private LeaderboardType currentType;
    private BukkitTask updateTask;
    private LeaderboardListener listener;
    
    // Cache for player stats
    private final Map<UUID, PlayerLeaderboardStats> statsCache = new ConcurrentHashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 60000; // 1 minute cache

    public enum LeaderboardType {
        KILLS("⚔️ Top Kills", Color.RED),
        DEATHS("💀 Top Deaths", Color.GRAY),
        KD_RATIO("📈 Top K/D Ratio", Color.CYAN),
        PLAYTIME("⏱️ Top Playtime", Color.MAGENTA),
        MOB_KILLS("🐾 Top Mob Kills", Color.GREEN);

        public final String title;
        public final Color color;

        LeaderboardType(String title, Color color) {
            this.title = title;
            this.color = color;
        }
    }

    public LeaderboardModule(XDiscordUltimate plugin) {
        super(plugin);
        this.currentType = LeaderboardType.KILLS;
    }

    @Override
    public String getName() {
        return "Leaderboard";
    }

    @Override
    public String getDescription() {
        return "Auto-updating Discord leaderboard with interactive buttons";
    }

    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Leaderboard module enabled");
    }

    @Override
    protected void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (listener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(listener);
        }
        statsCache.clear();
    }

    @Override
    public void onDiscordReady() {
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            listener = new LeaderboardListener();
            plugin.getDiscordManager().getJDA().addEventListener(listener);
            
            // Start update task
            startUpdateTask();
            
            // Initial update
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::updateLeaderboard, 60L);
            
            info("Leaderboard connected to Discord");
        }
    }

    private void loadConfiguration() {
        channelId = getConfig().getString("channel-id", "");
        messageId = getConfig().getString("message-id", "");
        updateInterval = getConfig().getInt("update-interval", 300); // 5 minutes default
        topPlayersCount = getConfig().getInt("top-players", 10);
        
        String typeStr = getConfig().getString("default-type", "KILLS");
        try {
            currentType = LeaderboardType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            currentType = LeaderboardType.KILLS;
        }
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        
        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, 
            this::updateLeaderboard, 
            updateInterval * 20L, 
            updateInterval * 20L
        );
        
        info("Leaderboard auto-update started (interval: " + updateInterval + "s)");
    }

    public void updateLeaderboard() {
        if (channelId == null || channelId.isEmpty()) {
            debug("Leaderboard channel not configured");
            return;
        }

        TextChannel channel = plugin.getDiscordManager().getTextChannelById(channelId);
        if (channel == null) {
            warning("Leaderboard channel not found: " + channelId);
            return;
        }

        EmbedBuilder embed = buildLeaderboardEmbed(currentType);
        
        // Add buttons for switching categories
        List<Button> buttons = Arrays.asList(
            Button.danger("lb_kills", "Kills").withEmoji(Emoji.fromUnicode("⚔️")),
            Button.secondary("lb_deaths", "Deaths").withEmoji(Emoji.fromUnicode("💀")),
            Button.primary("lb_kd", "K/D").withEmoji(Emoji.fromUnicode("📈")),
            Button.success("lb_playtime", "Playtime").withEmoji(Emoji.fromUnicode("⏱️")),
            Button.success("lb_mobs", "Mobs").withEmoji(Emoji.fromUnicode("🐾"))
        );

        if (messageId != null && !messageId.isEmpty()) {
            // Update existing message
            channel.retrieveMessageById(messageId).queue(
                message -> {
                    message.editMessageEmbeds(embed.build())
                        .setActionRow(buttons)
                        .queue(
                            success -> debug("Leaderboard updated"),
                            error -> {
                                warning("Failed to update leaderboard, creating new: " + error.getMessage());
                                createNewLeaderboardMessage(channel, embed, buttons);
                            }
                        );
                },
                error -> {
                    debug("Leaderboard message not found, creating new");
                    createNewLeaderboardMessage(channel, embed, buttons);
                }
            );
        } else {
            createNewLeaderboardMessage(channel, embed, buttons);
        }
    }

    private void createNewLeaderboardMessage(TextChannel channel, EmbedBuilder embed, List<Button> buttons) {
        channel.sendMessageEmbeds(embed.build())
            .setActionRow(buttons)
            .queue(
                message -> {
                    messageId = message.getId();
                    // Save message ID to config
                    getConfig().set("message-id", messageId);
                    plugin.saveConfig();
                    info("Created new leaderboard message: " + messageId);
                },
                error -> warning("Failed to create leaderboard: " + error.getMessage())
            );
    }

    private EmbedBuilder buildLeaderboardEmbed(LeaderboardType type) {
        List<PlayerLeaderboardStats> stats = getTopPlayers(type, topPlayersCount);
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(type.title)
            .setColor(type.color)
            .setFooter("Updates every " + updateInterval + " seconds • Click buttons to change category")
            .setTimestamp(Instant.now());

        if (stats.isEmpty()) {
            embed.setDescription("No player data available yet.\nPlayers need to join the server to appear on the leaderboard.");
            return embed;
        }

        StringBuilder description = new StringBuilder();
        description.append("```\n");
        
        String[] medals = {"🥇", "🥈", "🥉"};
        DecimalFormat df = new DecimalFormat("0.00");
        
        for (int i = 0; i < stats.size(); i++) {
            PlayerLeaderboardStats player = stats.get(i);
            String rank = i < 3 ? medals[i] : String.format("%2d.", i + 1);
            String value = formatStatValue(player, type, df);
            
            description.append(String.format("%-3s %-16s %s\n", 
                rank, 
                truncate(player.name, 16), 
                value
            ));
        }
        
        description.append("```");
        embed.setDescription(description.toString());

        // Add server stats
        int totalPlayers = statsCache.size();
        long totalKills = statsCache.values().stream().mapToLong(s -> s.kills).sum();
        long totalPlaytime = statsCache.values().stream().mapToLong(s -> s.playtimeMinutes).sum();
        
        embed.addField("📊 Server Stats", 
            "**Players:** " + totalPlayers + "\n" +
            "**Total Kills:** " + totalKills + "\n" +
            "**Total Playtime:** " + formatPlaytime(totalPlaytime), 
            false);

        return embed;
    }

    private String formatStatValue(PlayerLeaderboardStats player, LeaderboardType type, DecimalFormat df) {
        switch (type) {
            case KILLS:
                return String.valueOf(player.kills);
            case DEATHS:
                return String.valueOf(player.deaths);
            case KD_RATIO:
                return df.format(player.kdRatio);
            case PLAYTIME:
                return formatPlaytime(player.playtimeMinutes);
            case MOB_KILLS:
                return String.valueOf(player.mobKills);
            default:
                return "N/A";
        }
    }

    private String truncate(String str, int maxLen) {
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 1) + "…";
    }

    private String formatPlaytime(long minutes) {
        long hours = minutes / 60;
        long days = hours / 24;
        hours = hours % 24;
        
        if (days > 0) {
            return days + "d " + hours + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else {
            return minutes + "m";
        }
    }

    private List<PlayerLeaderboardStats> getTopPlayers(LeaderboardType type, int limit) {
        updateStatsCache();
        
        Comparator<PlayerLeaderboardStats> comparator;
        switch (type) {
            case KILLS:
                comparator = Comparator.comparingInt(s -> -s.kills);
                break;
            case DEATHS:
                comparator = Comparator.comparingInt(s -> -s.deaths);
                break;
            case KD_RATIO:
                comparator = Comparator.comparingDouble(s -> -s.kdRatio);
                break;
            case PLAYTIME:
                comparator = Comparator.comparingLong(s -> -s.playtimeMinutes);
                break;
            case MOB_KILLS:
                comparator = Comparator.comparingInt(s -> -s.mobKills);
                break;
            default:
                comparator = Comparator.comparingInt(s -> -s.kills);
        }

        return statsCache.values().stream()
            .sorted(comparator)
            .limit(limit)
            .collect(Collectors.toList());
    }

    private void updateStatsCache() {
        long now = System.currentTimeMillis();
        if (now - lastCacheUpdate < CACHE_DURATION && !statsCache.isEmpty()) {
            return; // Use cached data
        }

        statsCache.clear();
        
        // Get all players who have played before
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.hasPlayedBefore() && player.getName() != null) {
                try {
                    int kills = player.getStatistic(Statistic.PLAYER_KILLS);
                    int deaths = player.getStatistic(Statistic.DEATHS);
                    int mobKills = player.getStatistic(Statistic.MOB_KILLS);
                    long playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    long playtimeMinutes = playtimeTicks / 20 / 60;
                    
                    double kdRatio = deaths > 0 ? (double) kills / deaths : kills;
                    
                    statsCache.put(player.getUniqueId(), new PlayerLeaderboardStats(
                        player.getName(),
                        player.getUniqueId(),
                        kills,
                        deaths,
                        kdRatio,
                        mobKills,
                        playtimeMinutes
                    ));
                } catch (Exception e) {
                    debug("Failed to get stats for " + player.getName() + ": " + e.getMessage());
                }
            }
        }
        
        lastCacheUpdate = now;
        debug("Stats cache updated: " + statsCache.size() + " players");
    }

    private class LeaderboardListener extends ListenerAdapter {
        @Override
        public void onButtonInteraction(ButtonInteractionEvent event) {
            String buttonId = event.getComponentId();
            
            if (!buttonId.startsWith("lb_")) return;
            
            LeaderboardType newType = null;
            switch (buttonId) {
                case "lb_kills":
                    newType = LeaderboardType.KILLS;
                    break;
                case "lb_deaths":
                    newType = LeaderboardType.DEATHS;
                    break;
                case "lb_kd":
                    newType = LeaderboardType.KD_RATIO;
                    break;
                case "lb_playtime":
                    newType = LeaderboardType.PLAYTIME;
                    break;
                case "lb_mobs":
                    newType = LeaderboardType.MOB_KILLS;
                    break;
            }
            
            if (newType != null) {
                currentType = newType;
                
                // Update the embed immediately
                EmbedBuilder embed = buildLeaderboardEmbed(currentType);
                
                List<Button> buttons = Arrays.asList(
                    Button.danger("lb_kills", "Kills").withEmoji(Emoji.fromUnicode("⚔️")),
                    Button.secondary("lb_deaths", "Deaths").withEmoji(Emoji.fromUnicode("💀")),
                    Button.primary("lb_kd", "K/D").withEmoji(Emoji.fromUnicode("📈")),
                    Button.success("lb_playtime", "Playtime").withEmoji(Emoji.fromUnicode("⏱️")),
                    Button.success("lb_mobs", "Mobs").withEmoji(Emoji.fromUnicode("🐾"))
                );
                
                event.editMessageEmbeds(embed.build())
                    .setActionRow(buttons)
                    .queue();
                
                debug("Leaderboard switched to: " + newType.title);
            }
        }
    }

    public static class PlayerLeaderboardStats {
        public final String name;
        public final UUID uuid;
        public final int kills;
        public final int deaths;
        public final double kdRatio;
        public final int mobKills;
        public final long playtimeMinutes;

        public PlayerLeaderboardStats(String name, UUID uuid, int kills, int deaths, 
                                       double kdRatio, int mobKills, long playtimeMinutes) {
            this.name = name;
            this.uuid = uuid;
            this.kills = kills;
            this.deaths = deaths;
            this.kdRatio = kdRatio;
            this.mobKills = mobKills;
            this.playtimeMinutes = playtimeMinutes;
        }
    }
}
