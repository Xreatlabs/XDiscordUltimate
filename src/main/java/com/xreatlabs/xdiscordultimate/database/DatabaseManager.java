package com.xreatlabs.xdiscordultimate.database;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DatabaseManager {
    
    private final XDiscordUltimate plugin;
    private HikariDataSource dataSource;
    private final String type;
    
    public DatabaseManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("database.type", "sqlite");
    }
    
    /**
     * Initialize the database connection
     */
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
        
        dataSource = new HikariDataSource(config);
        
        // Create tables
        createTables();
        
        plugin.getLogger().info("Database initialized successfully using " + type);
    }
    
    /**
     * Setup SQLite configuration
     */
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
    
    /**
     * Setup MySQL configuration
     */
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
    
    /**
     * Setup PostgreSQL configuration
     */
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
    
    /**
     * Create database tables
     */
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
            
            // Ticket messages table
            String ticketMessages = "CREATE TABLE IF NOT EXISTS ticket_messages (" +
                "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                "ticket_id INTEGER NOT NULL," +
                "sender_uuid VARCHAR(36)," +
                "sender_type VARCHAR(10)," + // PLAYER or STAFF
                "message TEXT," +
                "sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY (ticket_id) REFERENCES tickets(id)" +
                ")";
            
            // Moderation logs table
            String moderationLogs = "CREATE TABLE IF NOT EXISTS moderation_logs (" +
                "id INTEGER PRIMARY KEY " + (type.equals("sqlite") ? "AUTOINCREMENT" : "AUTO_INCREMENT") + "," +
                "action_type VARCHAR(20) NOT NULL," + // BAN, KICK, WARN, MUTE
                "target_uuid VARCHAR(36) NOT NULL," +
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
            
            // Execute table creation
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(verifiedUsers);
                stmt.execute(verificationCodes);
                stmt.execute(tickets);
                stmt.execute(ticketMessages);
                stmt.execute(moderationLogs);
                stmt.execute(playerStats);
                stmt.execute(activityRewards);
                
                // Create indexes for better performance
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_discord_id ON verified_users(discord_id)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_verification_code ON verification_codes(code)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_verification_expires ON verification_codes(expires_at)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_ticket_status ON tickets(status)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_target ON moderation_logs(target_uuid)");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_mod_active ON moderation_logs(active)");
            }
        }
    }
    
    /**
     * Get a database connection
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not available");
        }
        
        Connection connection = dataSource.getConnection();
        if (connection == null) {
            throw new SQLException("Failed to get database connection from pool");
        }
        
        return connection;
    }

    /**
     * Execute a database operation with proper error handling
     */
    public <T> T executeOperation(DatabaseOperation<T> operation) {
        try (Connection conn = getConnection()) {
            return operation.execute();
        } catch (SQLException e) {
            plugin.getLogger().severe("Database operation failed: " + e.getMessage());
            if (plugin.getConfigManager().isDebugEnabled()) {
                e.printStackTrace();
            }
            return null;
        }
    }

    /**
     * Check database connection health
     */
    public boolean isHealthy() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed() && conn.isValid(5);
        } catch (SQLException e) {
            plugin.getLogger().warning("Database health check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Close the database connection
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                plugin.getLogger().info("Database connection closed.");
            } catch (Exception e) {
                plugin.getLogger().severe("Error closing database connection: " + e.getMessage());
            }
        }
    }
    
    /**
     * Link a Discord account with Minecraft
     */
    public CompletableFuture<Boolean> linkAccount(UUID minecraftUuid, String discordId, 
                                                    String minecraftName, String discordName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO verified_users (minecraft_uuid, discord_id, minecraft_name, discord_name) " +
                        "VALUES (?, ?, ?, ?) ON CONFLICT(minecraft_uuid) DO UPDATE SET " +
                        "discord_id = ?, discord_name = ?, verified_at = CURRENT_TIMESTAMP";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, minecraftUuid.toString());
                stmt.setString(2, discordId);
                stmt.setString(3, minecraftName);
                stmt.setString(4, discordName);
                
                if (type.equals("sqlite")) {
                    stmt.setString(5, discordId);
                    stmt.setString(6, discordName);
                } else {
                    stmt.setString(5, discordId);
                    stmt.setString(6, discordName);
                }
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to link account: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get Discord ID from Minecraft UUID
     */
    public CompletableFuture<String> getDiscordId(UUID minecraftUuid) {
        return CompletableFuture.supplyAsync(() -> {
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
                plugin.getLogger().severe("Failed to get Discord ID: " + e.getMessage());
            }
            
            return null;
        });
    }
    
    /**
     * Get Minecraft UUID from Discord ID
     */
    public CompletableFuture<UUID> getMinecraftUuid(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
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
                plugin.getLogger().severe("Failed to get Minecraft UUID: " + e.getMessage());
            }
            
            return null;
        });
    }
    
    /**
     * Check if a Discord user is linked to a Minecraft account
     */
    public CompletableFuture<Boolean> isDiscordLinked(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM verified_users WHERE discord_id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, discordId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check Discord link: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Create a support ticket
     */
    public CompletableFuture<Integer> createTicket(UUID playerUuid, String subject) {
        return CompletableFuture.supplyAsync(() -> {
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
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create ticket: " + e.getMessage());
            }
            
            return -1;
        });
    }
    
    /**
     * Log moderation action
     */
    public CompletableFuture<Void> logModerationAction(String actionType, UUID targetUuid, 
                                                         UUID moderatorUuid, String reason, 
                                                         Timestamp expiresAt) {
        return CompletableFuture.supplyAsync(() -> {
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
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log moderation action: " + e.getMessage());
            }
            
            return null;
        });
    }
    
    /**
     * Update player statistics
     */
    public CompletableFuture<Void> updatePlayerStats(UUID playerUuid, String statType, int increment) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO player_stats (minecraft_uuid, " + statType + ") VALUES (?, ?) " +
                        "ON CONFLICT(minecraft_uuid) DO UPDATE SET " + statType + " = " + statType + " + ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                
                if (type.equals("sqlite")) {
                    stmt.setString(2, playerUuid.toString());
                    stmt.setInt(3, increment);
                } else {
                    stmt.setInt(2, increment);
                    stmt.setInt(3, increment);
                }
                
                stmt.executeUpdate();
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update player stats: " + e.getMessage());
            }
            
            return null;
        });
    }
    
    /**
     * Get Minecraft name from Discord ID
     */
    public CompletableFuture<String> getMinecraftName(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT minecraft_name FROM verified_users WHERE discord_id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, discordId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("minecraft_name");
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get Minecraft name: " + e.getMessage());
            }
            
            return null;
        });
    }
    
    /**
     * Create a player report
     */
    public CompletableFuture<Integer> createReport(UUID reporterUuid, UUID targetUuid, String reason) {
        return CompletableFuture.supplyAsync(() -> {
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
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to create report: " + e.getMessage());
            }
            
            return -1;
        });
    }
    
    /**
     * Update ticket with Discord channel ID
     */
    public CompletableFuture<Boolean> updateTicketChannel(int ticketId, String channelId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE tickets SET discord_channel_id = ? WHERE id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, channelId);
                stmt.setInt(2, ticketId);
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to update ticket channel: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Close a ticket
     */
    public CompletableFuture<Boolean> closeTicket(int ticketId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE tickets SET status = 'CLOSED', closed_at = CURRENT_TIMESTAMP WHERE id = ?";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, ticketId);
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to close ticket: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Add message to ticket
     */
    public CompletableFuture<Boolean> addTicketMessage(int ticketId, UUID senderUuid, String senderType, String message) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "INSERT INTO ticket_messages (ticket_id, sender_uuid, sender_type, message) VALUES (?, ?, ?, ?)";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setInt(1, ticketId);
                stmt.setString(2, senderUuid != null ? senderUuid.toString() : null);
                stmt.setString(3, senderType);
                stmt.setString(4, message);
                
                return stmt.executeUpdate() > 0;
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to add ticket message: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get open tickets count for a player
     */
    public CompletableFuture<Integer> getOpenTicketsCount(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM tickets WHERE minecraft_uuid = ? AND status = 'OPEN'";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, playerUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get open tickets count: " + e.getMessage());
            }
            
            return 0;
        });
    }
    
    /**
     * Check if player has recent reports (cooldown)
     */
    public CompletableFuture<Boolean> hasRecentReport(UUID reporterUuid, int cooldownMinutes) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM moderation_logs WHERE moderator_uuid = ? AND action_type = 'REPORT' " +
                        "AND timestamp > datetime('now', '-" + cooldownMinutes + " minutes')";
            
            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                
                stmt.setString(1, reporterUuid.toString());
                
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
                
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to check recent reports: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Store verification code for Discord user
     */
    public void storeVerificationCode(String discordId, String code, String username) {
        String sql = "INSERT OR REPLACE INTO verification_codes (discord_id, code, username, created_at, expires_at) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            long now = System.currentTimeMillis();
            long expires = now + (10 * 60 * 1000); // 10 minutes
            
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
     * Functional interface for database operations
     */
    @FunctionalInterface
    public interface DatabaseOperation<T> {
        T execute() throws SQLException;
    }
}