package com.xreatlabs.xdiscordultimate.modules.leaderboard;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LeaderboardModule extends Module {

    private String channelId;
    private String messageId;
    private int updateInterval;
    private int topPlayersCount;
    private String customThumbnail;
    private LeaderboardType currentType;
    private BukkitTask updateTask;
    private LeaderboardListener listener;

    // Cache for player stats
    private final Map<UUID, PlayerLeaderboardStats> statsCache = new ConcurrentHashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 60000; // 1 minute cache

    public enum LeaderboardType {
        KILLS("⚔️ Top Kills", Color.RED, "lb_kills"),
        DEATHS("💀 Top Deaths", Color.GRAY, "lb_deaths"),
        KD_RATIO("📈 Top K/D Ratio", Color.CYAN, "lb_kd"),
        PLAYTIME("⏱️ Top Playtime", Color.MAGENTA, "lb_playtime"),
        MOB_KILLS("🐾 Top Mob Kills", Color.GREEN, "lb_mobs"),
        BLOCKS_MINED("⛏️ Top Blocks Mined", new Color(0x8B4513), "lb_blocks_mined"),
        BLOCKS_PLACED("🧱 Top Blocks Placed", new Color(0x20B2AA), "lb_blocks_placed"),
        DISTANCE_TRAVELED("🚶 Top Distance Traveled", new Color(0x9370DB), "lb_distance");

        public final String title;
        public final Color color;
        public final String buttonId;

        LeaderboardType(String title, Color color, String buttonId) {
            this.title = title;
            this.color = color;
            this.buttonId = buttonId;
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

    @Override
    public List<SlashCommandData> getSlashCommands() {
        return Collections.singletonList(
            Commands.slash("leaderboard", "View the server leaderboard")
                .addSubcommands(
                    new SubcommandData("kills", "Top player kills"),
                    new SubcommandData("deaths", "Top player deaths"),
                    new SubcommandData("kd", "Top K/D ratio"),
                    new SubcommandData("playtime", "Top playtime"),
                    new SubcommandData("mobs", "Top mob kills"),
                    new SubcommandData("blocks-mined", "Top blocks mined"),
                    new SubcommandData("blocks-placed", "Top blocks placed"),
                    new SubcommandData("distance", "Top distance traveled")
                )
        );
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        if (!"leaderboard".equals(event.getName())) {
            return false;
        }

        String subcommand = event.getSubcommandName();
        LeaderboardType type = resolveType(subcommand);
        currentType = type;

        event.replyEmbeds(buildLeaderboardEmbed(type).build())
            .setComponents(getLeaderboardActionRows().toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]))
            .queue();
        return true;
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"leaderboard".equals(command)) {
            return false;
        }

        String subcommand = args.trim().isEmpty() ? "kills" : args.trim().toLowerCase();
        currentType = resolveType(subcommand);

        event.getChannel().sendMessageEmbeds(buildLeaderboardEmbed(currentType).build())
            .setComponents(getLeaderboardActionRows().toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]))
            .queue();
        return true;
    }

    private LeaderboardType resolveType(String subcommand) {
        if (subcommand == null) {
            return LeaderboardType.KILLS;
        }
        switch (subcommand) {
            case "deaths": return LeaderboardType.DEATHS;
            case "kd": return LeaderboardType.KD_RATIO;
            case "playtime": return LeaderboardType.PLAYTIME;
            case "mobs": return LeaderboardType.MOB_KILLS;
            case "blocks-mined":
            case "blocks_mined": return LeaderboardType.BLOCKS_MINED;
            case "blocks-placed":
            case "blocks_placed": return LeaderboardType.BLOCKS_PLACED;
            case "distance": return LeaderboardType.DISTANCE_TRAVELED;
            case "kills":
            default: return LeaderboardType.KILLS;
        }
    }

    private List<Button> getLeaderboardButtons() {
        return Arrays.asList(
            Button.danger("lb_kills", "Kills").withEmoji(Emoji.fromUnicode("⚔️")),
            Button.secondary("lb_deaths", "Deaths").withEmoji(Emoji.fromUnicode("💀")),
            Button.primary("lb_kd", "K/D").withEmoji(Emoji.fromUnicode("📈")),
            Button.success("lb_playtime", "Time").withEmoji(Emoji.fromUnicode("⏱️")),
            Button.secondary("lb_mobs", "Mobs").withEmoji(Emoji.fromUnicode("🐾")),
            Button.secondary("lb_blocks_mined", "Mined").withEmoji(Emoji.fromUnicode("⛏️")),
            Button.secondary("lb_blocks_placed", "Placed").withEmoji(Emoji.fromUnicode("🧱")),
            Button.secondary("lb_distance", "Distance").withEmoji(Emoji.fromUnicode("🚶")),
            Button.primary("lb_refresh", "Refresh").withEmoji(Emoji.fromUnicode("🔄"))
        );
    }

    /**
     * Split the flat button list into ActionRows (max 5 buttons per row).
     */
    private List<ActionRow> getLeaderboardActionRows() {
        List<Button> buttons = getLeaderboardButtons();
        List<ActionRow> rows = new ArrayList<>();
        final int chunkSize = 5;
        for (int i = 0; i < buttons.size(); i += chunkSize) {
            rows.add(ActionRow.of(buttons.subList(i, Math.min(i + chunkSize, buttons.size()))));
        }
        return rows;
    }

    private void loadConfiguration() {
        channelId = getConfig().getString("channel-id", "");
        messageId = getConfig().getString("message-id", "");
        updateInterval = getConfig().getInt("update-interval", 300); // 5 minutes default
        topPlayersCount = getConfig().getInt("top-players", 10);
        customThumbnail = getConfig().getString("custom-thumbnail", "");

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

        List<ActionRow> rows = getLeaderboardActionRows();

        if (messageId != null && !messageId.isEmpty()) {
            // Update existing message
            channel.retrieveMessageById(messageId).queue(
                message -> {
                    message.editMessageEmbeds(embed.build())
                        .setComponents(rows.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]))
                        .queue(
                            success -> debug("Leaderboard updated"),
                            error -> {
                                warning("Failed to update leaderboard, creating new: " + error.getMessage());
                                createNewLeaderboardMessage(channel, embed, rows);
                            }
                        );
                },
                error -> {
                    debug("Leaderboard message not found, creating new");
                    createNewLeaderboardMessage(channel, embed, rows);
                }
            );
        } else {
            createNewLeaderboardMessage(channel, embed, rows);
        }
    }

    private void createNewLeaderboardMessage(TextChannel channel, EmbedBuilder embed, List<ActionRow> rows) {
        channel.sendMessageEmbeds(embed.build())
            .setComponents(rows.toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]))
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
            .setFooter("Updates every " + updateInterval + "s • Tap a button below to switch category or refresh")
            .setTimestamp(Instant.now());

        // Custom thumbnail (icon) from config, else use top player's avatar
        if (customThumbnail != null && !customThumbnail.isEmpty()) {
            embed.setThumbnail(customThumbnail);
        } else if (!stats.isEmpty()) {
            embed.setThumbnail(stats.get(0).avatarUrl());
        }

        if (stats.isEmpty()) {
            embed.setDescription("No player data available yet.\nPlayers need to join the server to appear on the leaderboard.");
            return embed;
        }

        StringBuilder description = new StringBuilder();
        String[] medals = {"🥇", "🥈", "🥉"};
        DecimalFormat df = new DecimalFormat("0.00");

        // Mobile-friendly: shorter rows, no monospace block (renders better on small screens)
        for (int i = 0; i < stats.size(); i++) {
            PlayerLeaderboardStats player = stats.get(i);
            String rank = i < 3 ? medals[i] : ("`#" + (i + 1) + "`");
            String value = formatStatValue(player, type, df);
            // Trim long names so values stay visible on mobile
            String name = truncate(player.name, 14);
            description.append(rank).append(" **")
                .append(name).append("** — `")
                .append(value).append("`\n");
        }

        embed.setDescription(description.toString());

        // Top player gets a dedicated thumbnail showcase
        if (!stats.isEmpty()) {
            PlayerLeaderboardStats top = stats.get(0);
            embed.addField("🏆 Leader", "**" + truncate(top.name, 16)
                + "** with `" + formatStatValue(top, type, df) + "`", false);
        }
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
            case BLOCKS_MINED:
                return formatBigNumber(player.blocksMined);
            case BLOCKS_PLACED:
                return formatBigNumber(player.blocksPlaced);
            case DISTANCE_TRAVELED:
                return formatDistance(player.distanceTraveledMeters);
            default:
                return "N/A";
        }
    }

    private String formatBigNumber(long value) {
        if (value >= 1_000_000) {
            return String.format("%.1fM", value / 1_000_000.0);
        } else if (value >= 1_000) {
            return String.format("%.1fK", value / 1_000.0);
        }
        return String.valueOf(value);
    }

    private String formatDistance(long meters) {
        if (meters >= 1000) {
            double km = meters / 1000.0;
            return String.format("%.1f km", km);
        }
        return meters + " m";
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
            case BLOCKS_MINED:
                comparator = Comparator.comparingLong(s -> -s.blocksMined);
                break;
            case BLOCKS_PLACED:
                comparator = Comparator.comparingLong(s -> -s.blocksPlaced);
                break;
            case DISTANCE_TRAVELED:
                comparator = Comparator.comparingLong(s -> -s.distanceTraveledMeters);
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

                    long blocksMined = 0L;
                    long blocksPlaced = 0L;
                    long distanceMeters = 0L;

                    try {
                        blocksMined = player.getStatistic(Statistic.MINE_BLOCK);
                    } catch (NoSuchMethodError | NoSuchFieldError | IllegalStateException ignored) {
                        // Older Bukkit API may not have this overload
                    }
                    // Statistic.PLACE_BLOCK not available in all Bukkit versions
                    blocksPlaced = 0;
                    try {
                        // Distance is stored in centimeters in Bukkit
                        distanceMeters = player.getStatistic(Statistic.WALK_ONE_CM)
                            + player.getStatistic(Statistic.SPRINT_ONE_CM)
                            + player.getStatistic(Statistic.CROUCH_ONE_CM)
                            + player.getStatistic(Statistic.SWIM_ONE_CM)
                            + player.getStatistic(Statistic.FALL_ONE_CM)
                            + player.getStatistic(Statistic.BOAT_ONE_CM)
                            + player.getStatistic(Statistic.HORSE_ONE_CM)
                            + player.getStatistic(Statistic.AVIATE_ONE_CM)
                            + player.getStatistic(Statistic.MINECART_ONE_CM)
                            + player.getStatistic(Statistic.PIG_ONE_CM)
                            + player.getStatistic(Statistic.STRIDER_ONE_CM)
                            + player.getStatistic(Statistic.CLIMB_ONE_CM);
                        distanceMeters /= 100;
                    } catch (NoSuchMethodError | NoSuchFieldError | IllegalStateException | NoClassDefFoundError ignored) {
                    }

                    statsCache.put(player.getUniqueId(), new PlayerLeaderboardStats(
                        player.getName(),
                        player.getUniqueId(),
                        kills,
                        deaths,
                        kdRatio,
                        mobKills,
                        playtimeMinutes,
                        blocksMined,
                        blocksPlaced,
                        distanceMeters
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

            // Refresh forces a cache bust + re-render of the current type
            if ("lb_refresh".equals(buttonId)) {
                lastCacheUpdate = 0; // force cache refresh
                EmbedBuilder embed = buildLeaderboardEmbed(currentType);
                event.editMessageEmbeds(embed.build())
                    .setComponents(getLeaderboardActionRows().toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]))
                    .queue();
                debug("Leaderboard refreshed by " + event.getUser().getName());
                return;
            }

            LeaderboardType newType = null;
            switch (buttonId) {
                case "lb_kills": newType = LeaderboardType.KILLS; break;
                case "lb_deaths": newType = LeaderboardType.DEATHS; break;
                case "lb_kd": newType = LeaderboardType.KD_RATIO; break;
                case "lb_playtime": newType = LeaderboardType.PLAYTIME; break;
                case "lb_mobs": newType = LeaderboardType.MOB_KILLS; break;
                case "lb_blocks_mined": newType = LeaderboardType.BLOCKS_MINED; break;
                case "lb_blocks_placed": newType = LeaderboardType.BLOCKS_PLACED; break;
                case "lb_distance": newType = LeaderboardType.DISTANCE_TRAVELED; break;
            }

            if (newType != null) {
                currentType = newType;

                EmbedBuilder embed = buildLeaderboardEmbed(currentType);

                event.editMessageEmbeds(embed.build())
                    .setComponents(getLeaderboardActionRows().toArray(new net.dv8tion.jda.api.interactions.components.LayoutComponent[0]))
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
        public final long blocksMined;
        public final long blocksPlaced;
        public final long distanceTraveledMeters;

        public PlayerLeaderboardStats(String name, UUID uuid, int kills, int deaths,
                                       double kdRatio, int mobKills, long playtimeMinutes) {
            this(name, uuid, kills, deaths, kdRatio, mobKills, playtimeMinutes, 0L, 0L, 0L);
        }

        public PlayerLeaderboardStats(String name, UUID uuid, int kills, int deaths,
                                       double kdRatio, int mobKills, long playtimeMinutes,
                                       long blocksMined, long blocksPlaced, long distanceTraveledMeters) {
            this.name = name;
            this.uuid = uuid;
            this.kills = kills;
            this.deaths = deaths;
            this.kdRatio = kdRatio;
            this.mobKills = mobKills;
            this.playtimeMinutes = playtimeMinutes;
            this.blocksMined = blocksMined;
            this.blocksPlaced = blocksPlaced;
            this.distanceTraveledMeters = distanceTraveledMeters;
        }

        public String avatarUrl() {
            return "https://minotar.net/helm/" + (uuid != null ? uuid : name) + "/32.png";
        }
    }
}
