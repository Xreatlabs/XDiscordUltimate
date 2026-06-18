package com.xreatlabs.xdiscordultimate.hooks;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class PlaceholderAPIExpansion extends PlaceholderExpansion {

    private final XDiscordUltimate plugin;
    private static final long TIMEOUT_SECONDS = 1L;

    public PlaceholderAPIExpansion(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "xdiscord";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().isEmpty() ? "XreaLabs" : plugin.getDescription().getAuthors().get(0);
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        switch (params.toLowerCase()) {
            case "bot_status":
                return getBotStatus();
            case "guild_name":
                return getGuildName();
            case "guild_members":
                return getGuildMemberCount();
            case "guild_online":
                return getGuildOnlineCount();
        }

        if (player == null) {
            return "";
        }

        UUID uuid = player.getUniqueId();

        switch (params.toLowerCase()) {
            case "verified":
                return isVerified(uuid) ? "true" : "false";
            case "linked":
                return isVerified(uuid) ? "Yes" : "No";
            case "discord_name":
                return getDiscordName(uuid);
            case "discord_id":
                return getDiscordId(uuid);
            case "discord_tag":
                return getDiscordTag(uuid);
            case "discord_avatar":
                return getDiscordAvatar(uuid);
            case "has_nitro":
                return hasNitro(uuid) ? "true" : "false";
            case "is_booster":
                return isBooster(uuid) ? "true" : "false";
            default:
                return null;
        }
    }

    private boolean isVerified(UUID uuid) {
        try {
            CompletableFuture<String> future = plugin.getDatabaseManager().getDiscordId(uuid);
            String discordId = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return discordId != null && !discordId.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String getDiscordId(UUID uuid) {
        try {
            CompletableFuture<String> future = plugin.getDatabaseManager().getDiscordId(uuid);
            String discordId = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return discordId != null ? discordId : "N/A";
        } catch (Exception e) {
            return "N/A";
        }
    }

    private String getDiscordName(UUID uuid) {
        try {
            String discordId = getDiscordId(uuid);
            if (discordId.equals("N/A")) {
                return "Not Linked";
            }

            JDA jda = plugin.getDiscordManager().getJDA();
            if (jda == null) return "Not Linked";

            return jda.retrieveUserById(discordId)
                .map(user -> user.getName())
                .complete();
        } catch (Exception e) {
            return "Not Linked";
        }
    }

    private String getDiscordTag(UUID uuid) {
        try {
            String discordId = getDiscordId(uuid);
            if (discordId.equals("N/A")) {
                return "Not Linked";
            }

            JDA jda = plugin.getDiscordManager().getJDA();
            if (jda == null) return "Not Linked";

            return jda.retrieveUserById(discordId)
                .map(user -> user.getName() + (user.getDiscriminator().equals("0") ? "" : "#" + user.getDiscriminator()))
                .complete();
        } catch (Exception e) {
            return "Not Linked";
        }
    }

    private String getDiscordAvatar(UUID uuid) {
        try {
            String discordId = getDiscordId(uuid);
            if (discordId.equals("N/A")) {
                return "";
            }

            JDA jda = plugin.getDiscordManager().getJDA();
            if (jda == null) return "";

            return jda.retrieveUserById(discordId)
                .map(user -> user.getEffectiveAvatarUrl())
                .complete();
        } catch (Exception e) {
            return "";
        }
    }

    private boolean hasNitro(UUID uuid) {
        try {
            String discordId = getDiscordId(uuid);
            if (discordId.equals("N/A")) {
                return false;
            }

            JDA jda = plugin.getDiscordManager().getJDA();
            if (jda == null) return false;

            Guild guild = plugin.getDiscordManager().getMainGuild();
            if (guild == null) return false;

            Member member = guild.retrieveMemberById(discordId).complete();
            return member != null && member.isBoosting();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBooster(UUID uuid) {
        return hasNitro(uuid);
    }

    private String getBotStatus() {
        JDA jda = plugin.getDiscordManager().getJDA();
        if (jda == null) return "Offline";
        return jda.getStatus().name().equals("CONNECTED") ? "Online" : "Offline";
    }

    private String getGuildName() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        return guild != null ? guild.getName() : "N/A";
    }

    private String getGuildMemberCount() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        return guild != null ? String.valueOf(guild.getMemberCount()) : "0";
    }

    private String getGuildOnlineCount() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return "0";

        try {
            return String.valueOf(guild.getMembers().stream()
                .filter(m -> m.getOnlineStatus().name().equals("ONLINE") ||
                            m.getOnlineStatus().name().equals("IDLE") ||
                            m.getOnlineStatus().name().equals("DO_NOT_DISTURB"))
                .count());
        } catch (Exception e) {
            return "0";
        }
    }
}
