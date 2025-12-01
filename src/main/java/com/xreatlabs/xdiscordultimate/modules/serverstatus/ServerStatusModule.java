package com.xreatlabs.xdiscordultimate.modules.serverstatus;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.stream.Collectors;

public class ServerStatusModule extends Module {

    private BukkitTask updateTask;
    private String statusMessageId;
    private String statusChannelId;
    private int updateInterval;

    public ServerStatusModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Server Status";
    }

    @Override
    public String getDescription() {
        return "Auto-updating server status embed in Discord";
    }

    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Server status module enabled");
    }

    @Override
    protected void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
        info("Server status module disabled");
    }

    @Override
    public void onDiscordReady() {
        startStatusUpdater();
    }

    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        if (config != null) {
            statusChannelId = config.getString("channel-id", "");
            updateInterval = config.getInt("update-interval", 60);
            statusMessageId = config.getString("message-id", "");
        } else {
            statusChannelId = "";
            updateInterval = 60;
            statusMessageId = "";
        }
    }

    private void startStatusUpdater() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateStatusEmbed();
        }, 20L, updateInterval * 20L);

        info("Status updater started (interval: " + updateInterval + "s)");
    }

    private void updateStatusEmbed() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }

        if (statusChannelId == null || statusChannelId.isEmpty()) {
            return;
        }

        TextChannel channel = plugin.getDiscordManager().getTextChannelById(statusChannelId);
        if (channel == null) {
            debug("Status channel not found: " + statusChannelId);
            return;
        }

        EmbedBuilder embed = buildStatusEmbed();

        if (statusMessageId != null && !statusMessageId.isEmpty()) {
            channel.retrieveMessageById(statusMessageId).queue(
                message -> message.editMessageEmbeds(embed.build()).queue(),
                error -> createNewStatusMessage(channel, embed)
            );
        } else {
            createNewStatusMessage(channel, embed);
        }
    }

    private void createNewStatusMessage(TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build()).queue(message -> {
            statusMessageId = message.getId();
            plugin.getConfig().set("features.server-status.message-id", statusMessageId);
            plugin.saveConfig();
            debug("Created new status message: " + statusMessageId);
        });
    }

    private EmbedBuilder buildStatusEmbed() {
        int onlinePlayers = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = getTPS();
        long usedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024;
        long maxMemory = Runtime.getRuntime().maxMemory() / 1024 / 1024;
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;

        String uptimeStr = formatUptime(uptime);
        String playerList = getPlayerList();

        Color statusColor = tps >= 18 ? new Color(0, 255, 0) : tps >= 15 ? new Color(255, 255, 0) : new Color(255, 0, 0);

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle(":video_game: Server Status")
            .setColor(statusColor)
            .addField(":busts_in_silhouette: Players", onlinePlayers + "/" + maxPlayers, true)
            .addField(":zap: TPS", String.format("%.1f", tps), true)
            .addField(":floppy_disk: Memory", usedMemory + "/" + maxMemory + " MB", true)
            .addField(":clock1: Uptime", uptimeStr, true)
            .addField(":globe_with_meridians: Version", Bukkit.getVersion().split("-")[0], true)
            .addField(":signal_strength: Status", tps >= 18 ? "Excellent" : tps >= 15 ? "Good" : "Lag", true)
            .setTimestamp(Instant.now())
            .setFooter("Last updated");

        if (!playerList.isEmpty()) {
            embed.addField(":video_game: Online Players", playerList, false);
        }

        return embed;
    }

    private String getPlayerList() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return "*No players online*";
        }

        String players = Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .limit(20)
            .collect(Collectors.joining(", "));

        if (Bukkit.getOnlinePlayers().size() > 20) {
            players += " +" + (Bukkit.getOnlinePlayers().size() - 20) + " more";
        }

        return "`" + players + "`";
    }

    private String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return days + "d " + hours + "h " + minutes + "m";
        } else if (hours > 0) {
            return hours + "h " + minutes + "m";
        } else {
            return minutes + "m";
        }
    }

    private double getTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return Math.min(20.0, tps[0]);
        } catch (Exception e) {
            return 20.0;
        }
    }

    public void forceUpdate() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::updateStatusEmbed);
    }
}
