package com.xreatlabs.xdiscordultimate.modules.stats;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerStatsModule extends Module implements Listener {

    public PlayerStatsModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Player Stats";
    }

    @Override
    public String getDescription() {
        return "Track and display player statistics";
    }

    @Override
    protected void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        info("Player stats module enabled");
    }

    @Override
    protected void onDisable() {
        PlayerDeathEvent.getHandlerList().unregister(this);
        info("Player stats module disabled");
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && killer != victim) {
            debug(killer.getName() + " killed " + victim.getName());
        }
    }

    public PlayerStats getStats(OfflinePlayer player) {
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        int deaths = player.getStatistic(Statistic.DEATHS);
        int mobKills = player.getStatistic(Statistic.MOB_KILLS);
        long playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long playtimeMinutes = playtimeTicks / 20 / 60;

        return new PlayerStats(player.getName(), kills, deaths, mobKills, playtimeMinutes);
    }

    public PlayerStats getStats(String playerName) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (player == null || !player.hasPlayedBefore()) {
            return null;
        }
        return getStats(player);
    }

    public PlayerStats getStats(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        if (player == null || !player.hasPlayedBefore()) {
            return null;
        }
        return getStats(player);
    }

    public EmbedBuilder buildStatsEmbed(PlayerStats stats) {
        if (stats == null) {
            return new EmbedBuilder()
                .setTitle("Player Not Found")
                .setDescription("Could not find stats for this player.")
                .setColor(Color.RED);
        }

        // Calculate actual K/D ratio
        double kdRatio = stats.deaths > 0 ? (double) stats.kills / stats.deaths : stats.kills;
        String kdFormatted = new DecimalFormat("0.00").format(kdRatio);

        return new EmbedBuilder()
            .setTitle("📊 " + stats.playerName + "'s Stats")
            .setColor(new Color(0, 170, 255))
            .addField("⚔️ Kills", String.valueOf(stats.kills), true)
            .addField("💀 Deaths", String.valueOf(stats.deaths), true)
            .addField("📈 K/D Ratio", kdFormatted, true)
            .addField("🐾 Mob Kills", String.valueOf(stats.mobKills), true)
            .addField("⏱️ Playtime", formatPlaytime(stats.playtimeMinutes), true)
            .setThumbnail("https://crafatar.com/avatars/" + Bukkit.getOfflinePlayer(stats.playerName).getUniqueId() + "?overlay")
            .setTimestamp(Instant.now());
    }

    public String formatStatsMessage(PlayerStats stats) {
        if (stats == null) {
            return ColorUtils.parse("<gradient:#FF5555:#AA0000>Player not found!</gradient>");
        }

        // Calculate actual K/D ratio
        double kdRatio = stats.deaths > 0 ? (double) stats.kills / stats.deaths : stats.kills;
        String kdFormatted = new DecimalFormat("0.00").format(kdRatio);

        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append(ColorUtils.parse("<gradient:#FFAA00:#FF5555>━━━ " + stats.playerName + "'s Stats ━━━</gradient>")).append("\n");
        sb.append(ColorUtils.parse("&8▸ <gradient:#FF5555:#FFAA00>Kills</gradient>&8: &f" + stats.kills)).append("\n");
        sb.append(ColorUtils.parse("&8▸ <gradient:#AAAAAA:#555555>Deaths</gradient>&8: &f" + stats.deaths)).append("\n");
        sb.append(ColorUtils.parse("&8▸ <gradient:#55FFFF:#5555FF>K/D Ratio</gradient>&8: &f" + kdFormatted)).append("\n");
        sb.append(ColorUtils.parse("&8▸ <gradient:#55FF55:#00AA00>Mob Kills</gradient>&8: &f" + stats.mobKills)).append("\n");
        sb.append(ColorUtils.parse("&8▸ <gradient:#AA00AA:#FF55FF>Playtime</gradient>&8: &f" + formatPlaytime(stats.playtimeMinutes))).append("\n");
        
        return sb.toString();
    }

    private String formatPlaytime(long minutes) {
        long hours = minutes / 60;
        long days = hours / 24;
        hours = hours % 24;
        long mins = minutes % 60;

        if (days > 0) {
            return days + "d " + hours + "h " + mins + "m";
        } else if (hours > 0) {
            return hours + "h " + mins + "m";
        } else {
            return mins + "m";
        }
    }

    public static class PlayerStats {
        public final String playerName;
        public final int kills;
        public final int deaths;
        public final int mobKills;
        public final long playtimeMinutes;

        public PlayerStats(String playerName, int kills, int deaths, int mobKills, long playtimeMinutes) {
            this.playerName = playerName;
            this.kills = kills;
            this.deaths = deaths;
            this.mobKills = mobKills;
            this.playtimeMinutes = playtimeMinutes;
        }
    }
}
