package com.xreatlabs.xdiscordultimate.events;

import java.util.UUID;

/**
 * Fired when a Minecraft account is unlinked from Discord (self-unlink or admin unlink).
 * Other modules can listen via {@link XDiscordEventBus} to revoke perks, clean up, etc.
 */
public class PlayerUnlinkedEvent {

    private final UUID playerUuid;
    private final String discordId;
    private final long timestamp;

    public PlayerUnlinkedEvent(UUID playerUuid, String discordId) {
        this.playerUuid = playerUuid;
        this.discordId = discordId;
        this.timestamp = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getDiscordId() {
        return discordId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "PlayerUnlinkedEvent{" +
            "playerUuid=" + playerUuid +
            ", discordId=" + discordId +
            ", time=" + timestamp +
            '}';
    }
}
