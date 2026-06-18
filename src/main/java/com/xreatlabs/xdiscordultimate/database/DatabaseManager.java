package com.xreatlabs.xdiscordultimate.database;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {

    private final XDiscordUltimate plugin;
    private HikariDataSource dataSource;
    private final String type;
    private static final int CURRENT_SCHEMA_VERSION = 1;
    private static final String SCHEMA_VERSION_KEY = "schema_version";

    public DatabaseManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("database.type", "sqlite");
    }
    
    public void initialize() throws SQLException {
        HikariConfig config = new HikariConfig();
        
        switch (type.toLowerCase()) {
            case "mysql":
                setupMySQL(config);
                break;
            case "postgresql":
                setupPostgreSQL(config);
                break;
            case "sqlite":
            default:
                setupSQLite(config);
                break;
        }
        
        // Common pool settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(600000);
        config.setConnectionTimeout(30000);
        config.setLeakDetectionThreshold(60000);

        // Connection validation
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(3000);

        dataSource = new HikariDataSource(config);

        // Create schema version table first
        createSchemaVersionTable();

        // Create tables
        createTables();

        // Run migrations if needed
        runMigrations();

        plugin.getLogger().info("Database initialized successfully using " + type);
    }
    
    private void setupSQLite(HikariConfig config) {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        String fileName = plugin.getConfig().getString("database.sqlite.file", "data.db");
        File dbFile = new File(dataFolder, fileName);
        
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
    }
    
    private void setupMySQL(HikariConfig config) {
        ConfigurationSection mysql = plugin.getConfig().getConfigurationSection("database.mysql");
        
        String host = mysql.getString("host", "localhost");
        int port = mysql.getInt("port", 3306);
        String database = mysql.getString("database", "xdiscord");
        String username = mysql.getString("username", "root");
        String password = mysql.getString("password", "");
        boolean ssl = mysql.getBoolean("ssl", false);
        
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database + 
                         "?useSSL=" + ssl + "&autoReconnect=true");
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // MySQL specific settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
    }
    
    private void setupPostgreSQL(HikariConfig config) {
        ConfigurationSection postgres = plugin.getConfig().getConfigurationSection("database.postgresql");

        String host = postgres.getString("host", "localhost");
        int port = postgres.getInt("port", 5432);
        String database = postgres.getString("database", "xdiscord");
        String username = postgres.getString("username", "postgres");
        String password = postgres.getString("password", "");

        config.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
    }

    private void createSchemaVersionTable() throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = "CREATE TABLE IF NOT EXISTS schema_versions (" +
                "version_key VARCHAR(64) PRIMARY KEY," +
                "version_value INT NOT NULL DEFAULT 0," +
                "applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            stmt.execute(sql);

            stmt.execute("INSERT OR IGNORE INTO schema_versions (version_key, version_value) " +
                "VALUES ('" + SCHEMA_VERSION_KEY + "', 0)");
        }
    }

    private int getSchemaVersion() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT version_value FROM schema_versions WHERE version_key = '" + SCHEMA_VERSION_KEY + "'")) {
            if (rs.next()) {
                return rs.getInt("version_value");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get schema version", e);
        }
        return 0;
    }

    private void updateSchemaVersion(int version) throws SQLException {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE schema_versions SET version_value = " + version +
                ", applied_at = CURRENT_TIMESTAMP WHERE version_key = '" + SCHEMA_VERSION_KEY + "'");
            plugin.getLogger().info("Database schema updated to version " + version);
        }
    }

    private void runMigrations() throws SQLException {
        int currentVersion = getSchemaVersion();

        if (currentVersion >= CURRENT_SCHEMA_VERSION) {
            plugin.getLogger().info("Database schema is up to date (version " + currentVersion + ")");
            return;
        }

        plugin.getLogger().info("Running database migrations from version " + currentVersion + " to " + CURRENT_SCHEMA_VERSION);

        for (int version = currentVersion + 1; version <= CURRENT_SCHEMA_VERSION; version++) {
            runMigration(version);
        }
    }

    private void runMigration(int version) throws SQLException {
        plugin.getLogger().info("Running migration to version " + version);

        try (Connection conn = getConnection()) {
            List<String> statements = getMigrationStatements(version);

            if (statements.isEmpty()) {
                updateSchemaVersion(version);
                return;
            }

            for (String sql : statements) {
                if (sql == null || sql.trim().isEmpty() || sql.trim().startsWith("--")) {
                    continue;
                }
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sql);
                }
            }

            updateSchemaVersion(version);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to run migration to version " + version, e);
            throw e;
        }
    }

    private List<String> getMigrationStatements(int version) {
        switch (version) {
            case 1:
                return Collections.emptyList();
            default:
                plugin.getLogger().warning("No migration defined for version " + version);
                return Collections.emptyList();
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            // Verified users table
            String verifiedUsers = "CREATE TABLE IF NOT EXISTS verified_users (" +
                "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                "discord_id VARCHAR(20) NOT NULL UNIQUE," +
                "verified_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "minecraft_name VARCHAR(16)," +
                "discord_name VARCHAR(32)" +
                ")";
            
            // Verification codes table
            String verificationCodes = "CREATE TABLE IF NOT EXISTS verification_codes (" +
                "discord_id VARCHAR(20) PRIMARY KEY," +
                "code VARCHAR(10) NOT NULL UNIQUE," +
                "username VARCHAR(16)," +
                "created_at BIGINT NOT NULL," +
                "expires_at BIGINT NOT NULL" +
                ")";
            
            // Support tickets table
            String tickets = "CREATE TABLE IF NOT EXISTS tickets (" +
                "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                "minecraft_uuid VARCHAR(36) NOT NULL," +
                "discord_channel_id VARCHAR(20)," +
                "status VARCHAR(20) DEFAULT 'OPEN'," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "closed_at TIMESTAMP NULL," +
                "subject TEXT," +
                "FOREIGN KEY (minecraft_uuid) REFERENCES verified_users(minecraft_uuid)" +
                ")";
            
            String ticketMessages = "CREATE TABLE IF NOT EXISTS ticket_messages (" +
                "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                "ticket_id INTEGER NOT NULL," +
                "sender_uuid VARCHAR(36)," +
                "sender_type VARCHAR(10)," +
                "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (ticket_id) REFERENCES tickets(id)" +
                ")";
            
            String moderationLogs = "CREATE TABLE IF NOT EXISTS moderation_logs (" +
                "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                "action_type VARCHAR(20) NOT NULL," +
                "moderator_uuid VARCHAR(36)," +
                "reason TEXT," +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "expires_at TIMESTAMP NULL," +
                "active BOOLEAN DEFAULT TRUE" +
                ")";
            
            // Player statistics table
            String playerStats = "CREATE TABLE IF NOT EXISTS player_stats (" +
                "minecraft_uuid VARCHAR(36) PRIMARY KEY," +
                "joins INTEGER DEFAULT 0," +
                "messages_sent INTEGER DEFAULT 0," +
                "deaths INTEGER DEFAULT 0," +
                "playtime_minutes INTEGER DEFAULT 0," +
                "last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "first_join TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ")";
            
            // Discord activity rewards table
            String activityRewards = "CREATE TABLE IF NOT EXISTS activity_rewards (" +
                "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                "discord_id VARCHAR(20) NOT NULL," +
                "reward_type VARCHAR(50)," +
                "reward_data TEXT," +
                "claimed BOOLEAN DEFAULT FALSE," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "claimed_at TIMESTAMP NULL" +
                ")";
            
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(verifiedUsers);
                stmt.execute(verificationCodes);
                stmt.execute(tickets);
                stmt.execute(ticketMessages);
                stmt.execute(moderationLogs);
                stmt.execute(playerStats);
                stmt.execute(activityRewards);

                stmt.execute("CREATE INDEX IF NOT EXISTS idx_discord_id ON verified_users(discord_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_verification_code ON verification_codes(code)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_verification_expires ON verification_codes(expires_at)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ticket_status ON tickets(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_moderator ON moderation_logs(moderator_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_active ON moderation_logs(active)");
            }
        }
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database not initialized!");
        }
        return dataSource.getConnection();
    }
    
    public <T> CompletableFuture<T> executeAsync(DatabaseOperation<T> operation) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return operation.execute();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Database operation failed", e);
                throw new RuntimeException(e);
            }
        });
    }
    
    public CompletableFuture<Boolean> linkAccount(UUID minecraftUuid, String discordId,
                                                  String minecraftName, String discordName) {
        return executeAsync(() -> {
            String sql;
            
            switch (type.toLowerCase()) {
                case "sqlite":
                    sql = "INSERT OR REPLACE INTO verified_users (minecraft_uuid, discord_id, minecraft_name, discord_name) " +
                         "VALUES (?, ?, ?, ?)";
                    break;
                case "mysql":
                    sql = "INSERT INTO verified_users (minecraft_uuid, discord_id, minecraft_name, discord_name) " +
                         "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                         "discord_id = VALUES(discord_id), discord_name = VALUES(discord_name), verified_at = CURRENT_TIMESTAMP";
                    break;
                case "postgresql":
                default:
                    sql = "INSERT INTO verified_users (minecraft_uuid, discord_id, minecraft_name, discord_name) " +
                         "VALUES (?, ?, ?, ?) ON CONFLICT(minecraft_uuid) DO UPDATE SET " +
                         "discord_id = EXCLUDED.discord_id, discord_name = EXCLUDED.discord_name, verified_at = CURRENT_TIMESTAMP";
                    break;
            }
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, minecraftUuid.toString());
                stmt.setString(2, discordId);
                stmt.setString(3, minecraftName);
                stmt.setString(4, discordName);
                
                return stmt.executeUpdate() > 0;
            }
        });
    }
    
    public CompletableFuture<String> getDiscordId(UUID minecraftUuid) {
        return executeAsync(() -> {
            String sql = "SELECT discord_id FROM verified_users WHERE minecraft_uuid = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, minecraftUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("discord_id");
                    }
                }
            }
            return null;
        });
    }
    
    public CompletableFuture<UUID> getMinecraftUuid(String discordId) {
        return executeAsync(() -> {
            String sql = "SELECT minecraft_uuid FROM verified_users WHERE discord_id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, discordId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return UUID.fromString(rs.getString("minecraft_uuid"));
                    }
                }
            }
            return null;
        });
    }
    
    /**
     * Get Minecraft UUID from Discord ID (synchronous version for use in async contexts)
     */
    public UUID getMinecraftUuidSync(String discordId) {
        String sql = "SELECT minecraft_uuid FROM verified_users WHERE discord_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("minecraft_uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Database error in getMinecraftUuidSync: " + e.getMessage());
        }
        return null;
    }

    /**
     * Get Discord ID from Minecraft UUID (synchronous version for use in async contexts)
     */
    public String getDiscordIdSync(UUID minecraftUuid) {
        String sql = "SELECT discord_id FROM verified_users WHERE minecraft_uuid = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, minecraftUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("discord_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Database error in getDiscordIdSync: " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if a Discord user is linked to a Minecraft account (synchronous version)
     */
    public boolean isDiscordLinkedSync(String discordId) {
        return getMinecraftUuidSync(discordId) != null;
    }
    
    /**
     * Get Minecraft UUID from Discord ID (for economy module)
     */
    public CompletableFuture<UUID> getMinecraftUUID(String discordId) {
        return getMinecraftUuid(discordId);
    }
    
    /**
     * Check if a Discord user is linked to a Minecraft account
     */
    public CompletableFuture<Boolean> isDiscordLinked(String discordId) {
        return executeAsync(() -> {
            String sql = "SELECT 1 FROM verified_users WHERE discord_id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, discordId);

                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    /**
     * Fetch the linked account info (minecraft name, discord name, verified_at) for a Discord user.
     * Returns null if the Discord user is not linked.
     */
    public LinkedAccountInfo getLinkedAccountInfoSync(String discordId) {
        String sql = "SELECT minecraft_uuid, minecraft_name, discord_name, verified_at FROM verified_users WHERE discord_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, discordId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String uuidStr = rs.getString("minecraft_uuid");
                    UUID minecraftUuid = null;
                    try {
                        if (uuidStr != null) minecraftUuid = UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException ignored) {
                    }
                    return new LinkedAccountInfo(
                        discordId,
                        rs.getString("discord_name"),
                        rs.getString("minecraft_name"),
                        minecraftUuid,
                        rs.getTimestamp("verified_at")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Database error in getLinkedAccountInfoSync: " + e.getMessage());
        }
        return null;
    }

    /**
     * Immutable snapshot of a linked Discord/Minecraft account, used for verification embeds.
     */
    public static class LinkedAccountInfo {
        private final String discordId;
        private final String discordName;
        private final String minecraftName;
        private final UUID minecraftUuid;
        private final Timestamp linkedAt;

        public LinkedAccountInfo(String discordId, String discordName, String minecraftName,
                                 UUID minecraftUuid, Timestamp linkedAt) {
            this.discordId = discordId;
            this.discordName = discordName;
            this.minecraftName = minecraftName;
            this.minecraftUuid = minecraftUuid;
            this.linkedAt = linkedAt;
        }

        public String getDiscordId() { return discordId; }
        public String getDiscordName() { return discordName; }
        public String getMinecraftName() { return minecraftName; }
        public UUID getMinecraftUuid() { return minecraftUuid; }
        public Timestamp getLinkedAt() { return linkedAt; }
    }
    
    /**
     * Create a support ticket
     */
    public CompletableFuture<Integer> createTicket(UUID playerUuid, String subject) {
        return executeAsync(() -> {
            String sql = "INSERT INTO tickets (minecraft_uuid, subject) VALUES (?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, subject);
                
                stmt.executeUpdate();
                
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return -1;
        });
    }
    
    /**
     * Log a moderation action
     */
    public CompletableFuture<Void> logModerationAction(String actionType, UUID targetUuid, 
                                                       UUID moderatorUuid, String reason, 
                                                       Timestamp expiresAt) {
        return executeAsync(() -> {
            String sql = "INSERT INTO moderation_logs (action_type, target_uuid, moderator_uuid, reason, expires_at) " +
                        "VALUES (?, ?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, actionType);
                stmt.setString(2, targetUuid.toString());
                stmt.setString(3, moderatorUuid != null ? moderatorUuid.toString() : null);
                stmt.setString(4, reason);
                stmt.setTimestamp(5, expiresAt);
                
                stmt.executeUpdate();
            }
            return null;
        });
    }
    
    /**
     * Update player statistics
     */
    public CompletableFuture<Void> updatePlayerStats(UUID playerUuid, String statType, int increment) {
        return executeAsync(() -> {
            String sql;
            
            switch (type.toLowerCase()) {
                case "sqlite":
                    sql = "INSERT OR REPLACE INTO player_stats (minecraft_uuid, " + statType + ") " +
                         "VALUES (?, COALESCE((SELECT " + statType + " FROM player_stats WHERE minecraft_uuid = ?), 0) + ?)";
                    break;
                case "mysql":
                    sql = "INSERT INTO player_stats (minecraft_uuid, " + statType + ") VALUES (?, ?) " +
                         "ON DUPLICATE KEY UPDATE " + statType + " = " + statType + " + VALUES(" + statType + ")";
                    break;
                case "postgresql":
                default:
                    sql = "INSERT INTO player_stats (minecraft_uuid, " + statType + ") VALUES (?, ?) " +
                         "ON CONFLICT(minecraft_uuid) DO UPDATE SET " + statType + " = player_stats." + statType + " + EXCLUDED." + statType;
                    break;
            }
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                
                if (type.equalsIgnoreCase("sqlite")) {
                    stmt.setString(2, playerUuid.toString());
                    stmt.setInt(3, increment);
                } else {
                    stmt.setInt(2, increment);
                }
                
                stmt.executeUpdate();
            }
            return null;
        });
    }
    
    /**
     * Get Minecraft name from Discord ID
     */
    public CompletableFuture<String> getMinecraftName(String discordId) {
        return executeAsync(() -> {
            String sql = "SELECT minecraft_name FROM verified_users WHERE discord_id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, discordId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("minecraft_name");
                    }
                }
            }
            return null;
        });
    }

    /**
     * Update the stored Discord display name for a linked Discord account
     */
    public CompletableFuture<Boolean> updateDiscordName(String discordId, String newDiscordName) {
        return executeAsync(() -> {
            String sql = "UPDATE verified_users SET discord_name = ? WHERE discord_id = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, newDiscordName);
                stmt.setString(2, discordId);

                return stmt.executeUpdate() > 0;
            }
        });
    }
    
    /**
     * Create a player report
     */
    public CompletableFuture<Integer> createReport(UUID reporterUuid, UUID targetUuid, String reason) {
        return executeAsync(() -> {
            String sql = "INSERT INTO moderation_logs (action_type, target_uuid, moderator_uuid, reason) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                stmt.setString(1, "REPORT");
                stmt.setString(2, targetUuid.toString());
                stmt.setString(3, reporterUuid.toString());
                stmt.setString(4, reason);
                
                stmt.executeUpdate();
                
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return -1;
        });
    }
    
    /**
     * Update ticket with Discord channel ID
     */
    public CompletableFuture<Boolean> updateTicketChannel(int ticketId, String channelId) {
        return executeAsync(() -> {
            String sql = "UPDATE tickets SET discord_channel_id = ? WHERE id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, channelId);
                stmt.setInt(2, ticketId);
                
                return stmt.executeUpdate() > 0;
            }
        });
    }
    
    /**
     * Close a ticket
     */
    public CompletableFuture<Boolean> closeTicket(int ticketId) {
        return executeAsync(() -> {
            String sql = "UPDATE tickets SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP WHERE id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, ticketId);
                
                return stmt.executeUpdate() > 0;
            }
        });
    }
    
    /**
     * Add message to ticket
     */
    public CompletableFuture<Boolean> addTicketMessage(int ticketId, UUID senderUuid, String senderType, String message) {
        return executeAsync(() -> {
            String sql = "INSERT INTO ticket_messages (ticket_id, sender_uuid, sender_type, message) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, ticketId);
                stmt.setString(2, senderUuid != null ? senderUuid.toString() : null);
                stmt.setString(3, senderType);
                stmt.setString(4, message);
                
                return stmt.executeUpdate() > 0;
            }
        });
    }
    
    /**
     * Get open tickets count for a player
     */
    public CompletableFuture<Integer> getOpenTicketsCount(UUID playerUuid) {
        return executeAsync(() -> {
            String sql = "SELECT COUNT(*) FROM tickets WHERE minecraft_uuid = ? AND status = 'OPEN'";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            return 0;
        });
    }
    
    /**
     * Check if player has recent reports (cooldown)
     */
    public CompletableFuture<Boolean> hasRecentReport(UUID reporterUuid, int cooldownMinutes) {
        return executeAsync(() -> {
            String sql = "SELECT 1 FROM moderation_logs WHERE moderator_uuid = ? AND action_type = 'REPORT' " +
                        "AND timestamp > datetime('now', '-" + cooldownMinutes + " minutes')";
            
            if (!type.equals("sqlite")) {
                sql = "SELECT 1 FROM moderation_logs WHERE moderator_uuid = ? AND action_type = 'REPORT' " +
                     "AND timestamp > NOW() - INTERVAL " + cooldownMinutes + " MINUTE";
            }
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, reporterUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }
    
    /**
     * Unlink (delete) a verified account by Minecraft UUID.
     * Returns true if a row was deleted.
     */
    public CompletableFuture<Boolean> unlinkAccount(UUID minecraftUuid) {
        return executeAsync(() -> {
            String sql = "DELETE FROM verified_users WHERE minecraft_uuid = ?";
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, minecraftUuid.toString());
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Store verification code for Discord user
     */
    public void storeVerificationCode(String discordId, String code, String username, int expiryMinutes) {
        String sql;
        switch (type.toLowerCase()) {
            case "mysql":
                sql = "INSERT INTO verification_codes (discord_id, code, username, created_at, expires_at) " +
                      "VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE " +
                      "code = VALUES(code), username = VALUES(username), created_at = VALUES(created_at), expires_at = VALUES(expires_at)";
                break;
            case "postgresql":
                sql = "INSERT INTO verification_codes (discord_id, code, username, created_at, expires_at) " +
                      "VALUES (?, ?, ?, ?, ?) ON CONFLICT(discord_id) DO UPDATE SET " +
                      "code = EXCLUDED.code, username = EXCLUDED.username, created_at = EXCLUDED.created_at, expires_at = EXCLUDED.expires_at";
                break;
            case "sqlite":
            default:
                sql = "INSERT OR REPLACE INTO verification_codes (discord_id, code, username, created_at, expires_at) VALUES (?, ?, ?, ?, ?)";
                break;
        }
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            long now = System.currentTimeMillis();
            long expires = now + (expiryMinutes * 60L * 1000L);
            
            stmt.setString(1, discordId);
            stmt.setString(2, code);
            stmt.setString(3, username);
            stmt.setLong(4, now);
            stmt.setLong(5, expires);
            
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to store verification code: " + e.getMessage());
        }
    }
    
    /**
     * Get verification code info
     */
    public VerificationCode getVerificationCode(String code) {
        String sql = "SELECT * FROM verification_codes WHERE code = ? AND expires_at > ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, code);
            stmt.setLong(2, System.currentTimeMillis());
            
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new VerificationCode(
                    rs.getString("discord_id"),
                    rs.getString("code"),
                    rs.getString("username"),
                    rs.getLong("created_at"),
                    rs.getLong("expires_at")
                );
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get verification code: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Remove used verification code
     */
    public void removeVerificationCode(String code) {
        String sql = "DELETE FROM verification_codes WHERE code = ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setString(1, code);
            stmt.executeUpdate();
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to remove verification code: " + e.getMessage());
        }
    }
    
    /**
     * Clean expired verification codes
     */
    public void cleanExpiredVerificationCodes() {
        String sql = "DELETE FROM verification_codes WHERE expires_at < ?";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, System.currentTimeMillis());
            int deleted = stmt.executeUpdate();
            
            if (deleted > 0) {
                plugin.getLogger().info("Cleaned " + deleted + " expired verification codes");
            }
            
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clean expired verification codes: " + e.getMessage());
        }
    }
    
    /**
     * Inner class for verification code data
     */
    public static class VerificationCode {
        private final String discordId;
        private final String code;
        private final String username;
        private final long createdAt;
        private final long expiresAt;
        
        public VerificationCode(String discordId, String code, String username, long createdAt, long expiresAt) {
            this.discordId = discordId;
            this.code = code;
            this.username = username;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }
        
        public String getDiscordId() { return discordId; }
        public String getCode() { return code; }
        public String getUsername() { return username; }
        public long getCreatedAt() { return createdAt; }
        public long getExpiresAt() { return expiresAt; }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    /**
     * Close the database connection
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }
    
    /**
     * Functional interface for database operations
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }
}