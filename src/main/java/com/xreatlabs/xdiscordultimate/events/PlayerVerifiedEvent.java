package com.xreatlabs.xdiscordultimate.events;

import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerVerifiedEvent {

    private final UUID playerUuid;
    private final String playerName;
    private final String discordId;
    private final String discordUsername;
    private final long timestamp;

    public PlayerVerifiedEvent(UUID playerUuid, String playerName, String discordId, String discordUsername) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.discordId = discordId;
        this.discordUsername = discordUsername;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getDiscordId() {
        return discordId;
    }

    public String getDiscordUsername() {
        return discordUsername;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PlayerVerifiedEvent{" +
            "player=" + playerName +
            ", discord=" + discordUsername +
            ", time=" + timestamp +
            '}';
    }
}
