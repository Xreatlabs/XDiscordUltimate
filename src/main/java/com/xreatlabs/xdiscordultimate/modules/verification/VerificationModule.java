package com.xreatlabs.xdiscordultimate.modules.verification;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.database.DatabaseManager;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VerificationModule extends Module {
    
    private final Map<String, PendingVerification> pendingVerifications;
    private final Map<UUID, Long> verificationCooldowns;
    private final Map<String, UUID> verificationCodes; // code -> minecraft UUID
    private VerificationListener verificationListener;
    
    // Verification settings
    private int kickAfterMinutes;
    private boolean whitelistMode;
    private String verifiedRole;
    private String verifiedGroup;
    private int codeLength;
    private int codeExpiryMinutes;
    
    public VerificationModule(XDiscordUltimate plugin) {
        super(plugin);
        this.pendingVerifications = new ConcurrentHashMap<>();
        this.verificationCooldowns = new ConcurrentHashMap<>();
        this.verificationCodes = new ConcurrentHashMap<>();
    }
    
    @Override
    public String getName() {
        return "Verification";
    }
    
    @Override
    public String getDescription() {
        return "Simple code-based Discord account verification system";
    }
    
    @Override
    protected void onEnable() {
        loadConfiguration();
        
        // Start verification check task
        if (kickAfterMinutes > 0) {
            startVerificationCheckTask();
        }
        
        // Start code cleanup task
        startCodeCleanupTask();
        
        info("Verification module enabled - waiting for Discord...");
    }
    
    @Override
    public void onDiscordReady() {
        // Note: Slash command /verify is handled centrally by DiscordListener
        // We only register listener for message-based verification (!verify)
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            verificationListener = new VerificationListener();
            plugin.getDiscordManager().getJDA().addEventListener(verificationListener);
            info("Verification connected to Discord (message commands)");
        }
    }
    
    @Override
    protected void onDisable() {
        if (verificationListener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(verificationListener);
        }
        
        pendingVerifications.clear();
        verificationCooldowns.clear();
        verificationCodes.clear();
    }
    
    private void loadConfiguration() {
        kickAfterMinutes = getConfig().getInt("kick-after-minutes", 30);
        whitelistMode = getConfig().getBoolean("whitelist-mode", false);
        verifiedRole = getConfig().getString("verified-role", "Verified");
        verifiedGroup = getConfig().getString("verified-group", "verified");
        codeLength = getConfig().getInt("code-length", 6);
        codeExpiryMinutes = getConfig().getInt("code-expiry-minutes", 5);
    }
    
    // Note: /verify slash command is registered in DiscordListener.java to avoid duplication
    
    /**
     * Generate a verification code for Discord user
     */
    public String generateVerificationCode(String discordId, String discordName) {
        // Generate random code
        String code = generateRandomCode();
        
        // Check if user already has a pending verification
        pendingVerifications.values().removeIf(pv -> pv.discordId.equals(discordId));
        
        // Create new pending verification
        PendingVerification pending = new PendingVerification(discordId, discordName, code);
        pendingVerifications.put(code, pending);
        
        // Store in database
        plugin.getDatabaseManager().storeVerificationCode(discordId, code, discordName);
        
        return code;
    }
    
    /**
     * Process verification from Minecraft
     */
    public void processMinecraftVerification(Player player, String code) {
        UUID uuid = player.getUniqueId();
        
        // Check cooldown
        if (verificationCooldowns.containsKey(uuid)) {
            long cooldownEnd = verificationCooldowns.get(uuid);
            if (System.currentTimeMillis() < cooldownEnd) {
                long remainingSeconds = (cooldownEnd - System.currentTimeMillis()) / 1000;
                plugin.getMessageManager().sendError(player,
                    "Please wait " + remainingSeconds + " seconds before trying again.");
                return;
            }
        }
        
        // Check if player is already verified
        if (plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendError(player, "You are already verified!");
            return;
        }
        
        // First check in-memory pending verifications
        PendingVerification pending = pendingVerifications.get(code.toUpperCase());
        
        // If not found in memory, check database
        if (pending == null) {
            DatabaseManager.VerificationCode dbCode = plugin.getDatabaseManager().getVerificationCode(code.toUpperCase());
            if (dbCode != null && !dbCode.isExpired()) {
                // Create pending verification from database data
                pending = new PendingVerification(dbCode.getDiscordId(), dbCode.getUsername(), dbCode.getCode());
                pendingVerifications.put(code.toUpperCase(), pending);
            }
        }
        
        if (pending == null) {
            plugin.getMessageManager().sendError(player, "Invalid verification code!");
            return;
        }
        
        // Check if code is expired
        if (System.currentTimeMillis() - pending.timestamp > codeExpiryMinutes * 60 * 1000) {
            pendingVerifications.remove(code.toUpperCase());
            plugin.getDatabaseManager().removeVerificationCode(code.toUpperCase());
            plugin.getMessageManager().sendError(player, "This verification code has expired!");
            return;
        }
        
        // Set cooldown
        verificationCooldowns.put(uuid, System.currentTimeMillis() + 30000); // 30 second cooldown
        
        // Complete verification
        completeVerification(player, pending);
    }
    
    /**
     * Complete the verification process
     */
    private void completeVerification(Player player, PendingVerification pending) {
        // Remove from pending
        pendingVerifications.remove(pending.code);
        verificationCodes.remove(pending.code);
        
        // Remove from database
        plugin.getDatabaseManager().removeVerificationCode(pending.code);
        
        // Save to database
        plugin.getDatabaseManager().linkAccount(
            player.getUniqueId(),
            pending.discordId,
            player.getName(),
            pending.discordName
        ).thenAccept(success -> {
            if (success) {
                // Run on main thread
                Bukkit.getScheduler().runTask(plugin, () -> {
                    plugin.getMessageManager().sendMessage(player, "verification.success");
                    player.sendMessage(plugin.getMessageManager().colorize("<gradient:#55FF55:#00AA00>Your account has been linked to Discord user</gradient> &e" + pending.discordName + " &a!"));
                    
                    // Add to verified group if LuckPerms is available
                    if (plugin.getLuckPerms() != null && !verifiedGroup.isEmpty()) {
                        addToLuckPermsGroup(player, verifiedGroup);
                    }
                    
                    // Add Discord role
                    addDiscordRole(pending.discordId);
                    
                    // Sync Discord nickname to Minecraft name
                    syncDiscordNickname(pending.discordId, player.getName());
                    
                    // Notify in Discord
                    notifyDiscordVerification(pending.discordId, player.getName());
                });
                
                info("Player " + player.getName() + " verified with Discord user " + pending.discordName);
            } else {
                plugin.getMessageManager().sendError(player, "Failed to save verification. Please try again.");
                error("Failed to save verification to database");
            }
        });
    }
    
    /**
     * Handle player join event
     */
    public void handlePlayerJoin(Player player) {
        if (whitelistMode && !plugin.getAdminUtils().isVerified(player) && 
            !plugin.getAdminUtils().canBypassVerification(player)) {
            
            // Schedule kick if not verified
            if (kickAfterMinutes > 0) {
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && !plugin.getAdminUtils().isVerified(player)) {
                            player.kickPlayer(plugin.getMessageManager().getMessage(
                                "verification.kick-message", "%minutes%", String.valueOf(kickAfterMinutes)));
                        }
                    }
                }.runTaskLater(plugin, 20L * 60 * kickAfterMinutes);
            }
            
            // Send verification reminder
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && !plugin.getAdminUtils().isVerified(player)) {
                        plugin.getMessageManager().sendMessage(player, "verification.required");
                        player.sendMessage(plugin.getMessageManager().colorize("&e1. &7Use &b/verify &7in Discord to get a verification code"));
                        player.sendMessage(plugin.getMessageManager().colorize("&e2. &7Use &b/verify <code> &7in Minecraft to complete verification"));
                    }
                }
            }.runTaskLater(plugin, 60L); // 3 second delay
        }
    }
    
    /**
     * Check if a player is verified
     */
    public boolean isPlayerVerified(Player player) {
        return plugin.getAdminUtils().isVerified(player);
    }
    
    /**
     * Check if a Discord user is verified
     */
    public boolean isDiscordUserVerified(String discordId) {
        return plugin.getDatabaseManager().isDiscordLinked(discordId).join();
    }
    
    /**
     * Generate random verification code
     */
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        Random random = ThreadLocalRandom.current();
        
        for (int i = 0; i < codeLength; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        // Ensure code is unique
        String finalCode = code.toString();
        if (pendingVerifications.containsKey(finalCode)) {
            return generateRandomCode();
        }
        
        return finalCode;
    }
    
    /**
     * Add player to LuckPerms group
     */
    private void addToLuckPermsGroup(Player player, String group) {
        if (!plugin.isLuckPermsEnabled()) {
            debug("LuckPerms not available, skipping group assignment");
            return;
        }
        
        try {
            Object luckPerms = plugin.getLuckPerms();
            if (luckPerms == null) return;
            
            // Use reflection to interact with LuckPerms
            Class<?> luckPermsClass = luckPerms.getClass();
            java.lang.reflect.Method getUserManager = luckPermsClass.getMethod("getUserManager");
            Object userManager = getUserManager.invoke(luckPerms);
            
            // Get user
            java.lang.reflect.Method getUser = userManager.getClass().getMethod("getUser", UUID.class);
            Object user = getUser.invoke(userManager, player.getUniqueId());
            
            if (user != null) {
                // Get user data
                java.lang.reflect.Method getData = user.getClass().getMethod("data");
                Object userData = getData.invoke(user);
                
                // Create node
                Class<?> nodeClass = Class.forName("net.luckperms.api.node.Node");
                java.lang.reflect.Method nodeBuilder = nodeClass.getMethod("builder", String.class);
                Object builder = nodeBuilder.invoke(null, "group." + group);
                java.lang.reflect.Method build = builder.getClass().getMethod("build");
                Object node = build.invoke(builder);
                
                // Add node
                java.lang.reflect.Method add = userData.getClass().getMethod("add", nodeClass);
                add.invoke(userData, node);
                
                // Save user
                java.lang.reflect.Method saveUser = userManager.getClass().getMethod("saveUser", user.getClass());
                saveUser.invoke(userManager, user);
                
                debug("Added player " + player.getName() + " to LuckPerms group: " + group);
            }
        } catch (Exception e) {
            error("Failed to add player to LuckPerms group", e);
        }
    }
    
    /**
     * Sync Discord nickname to Minecraft name
     */
    private void syncDiscordNickname(String discordId, String minecraftName) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        
        try {
            Guild guild = plugin.getDiscordManager().getMainGuild();
            if (guild == null) return;
            
            Member member = guild.getMemberById(discordId);
            if (member != null) {
                // Don't change nickname if it's the server owner
                if (member.isOwner()) {
                    debug("Cannot change nickname of server owner");
                    return;
                }
                
                guild.modifyNickname(member, minecraftName).queue(
                    success -> debug("Changed Discord nickname to " + minecraftName),
                    error -> debug("Failed to change nickname: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            debug("Failed to sync Discord nickname: " + e.getMessage());
        }
    }
    
    /**
     * Add Discord verified role
     */
    private void addDiscordRole(String discordId) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        
        try {
            Guild guild = plugin.getDiscordManager().getMainGuild();
            if (guild == null) {
                error("Main guild not found");
                return;
            }
            
            Member member = guild.getMemberById(discordId);
            
            if (member != null) {
                // First try to get role by ID if it looks like an ID
                Role role = null;
                if (verifiedRole.matches("\\d+")) {
                    role = guild.getRoleById(verifiedRole);
                }
                
                // Fall back to name lookup
                if (role == null) {
                    role = guild.getRolesByName(verifiedRole, true).stream().findFirst().orElse(null);
                }
                
                if (role != null) {
                    guild.addRoleToMember(member, role).queue(
                        success -> debug("Added verified role to Discord user " + discordId),
                        error -> error("Failed to add verified role: " + error.getMessage())
                    );
                } else {
                    error("Verified role not found: " + verifiedRole);
                }
            }
        } catch (Exception e) {
            error("Failed to add Discord role", e);
        }
    }
    
    /**
     * Notify Discord user of successful verification
     */
    private void notifyDiscordVerification(String discordId, String minecraftName) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        
        try {
            User user = plugin.getDiscordManager().getJDA().getUserById(discordId);
            if (user != null) {
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("✅ Verification Successful!")
                    .setDescription("Your Discord account has been linked to Minecraft account **" + minecraftName + "**")
                    .setTimestamp(Instant.now());
                
                user.openPrivateChannel().queue(channel -> 
                    channel.sendMessageEmbeds(embed.build()).queue(
                        success -> debug("Sent verification success DM to " + user.getName()),
                        error -> debug("Failed to send DM: " + error.getMessage())
                    )
                );
            } else {
                warning("Could not find user with ID " + discordId + " to send verification DM.");
            }
        } catch (Exception e) {
            debug("Failed to notify Discord user: " + e.getMessage());
        }
    }
    
    /**
     * Start task to check for unverified players
     */
    private void startVerificationCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!whitelistMode) return;
                
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!plugin.getAdminUtils().isVerified(player) && 
                        !plugin.getAdminUtils().canBypassVerification(player)) {
                        
                        plugin.getMessageManager().sendWarning(player, 
                            "You must verify your Discord account to continue playing!");
                        player.sendMessage(plugin.getMessageManager().colorize("&e1. &7Use &b/verify &7in Discord to get a code"));
                        player.sendMessage(plugin.getMessageManager().colorize("&e2. &7Use &b/verify <code> &7in Minecraft to complete"));
                    }
                }
            }
        }.runTaskTimer(plugin, 20L * 60 * 5, 20L * 60 * 5); // Every 5 minutes
    }
    
    /**
     * Start task to clean up expired codes
     */
    private void startCodeCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                long expiryTime = codeExpiryMinutes * 60 * 1000;
                
                pendingVerifications.entrySet().removeIf(entry ->
                    now - entry.getValue().timestamp > expiryTime
                );
                
                verificationCooldowns.entrySet().removeIf(entry ->
                    now > entry.getValue()
                );
                
                // Also clean database
                plugin.getDatabaseManager().cleanExpiredVerificationCodes();
            }
        }.runTaskTimer(plugin, 20L * 60, 20L * 60); // Every minute
    }
    
    /**
     * Discord listener for verification commands (message-based only)
     * Note: Slash command /verify is handled centrally by DiscordListener
     */
    private class VerificationListener extends ListenerAdapter {
        // Slash command handling removed - DiscordListener handles /verify centrally
        // This prevents duplicate responses to the same slash command
        
        @Override
        public void onMessageReceived(MessageReceivedEvent event) {
            if (event.getAuthor().isBot()) return;
            
            String message = event.getMessage().getContentRaw();
            if (message.equalsIgnoreCase("!verify") || message.equalsIgnoreCase("/verify")) {
                String userId = event.getAuthor().getId();
                String userName = event.getAuthor().getName();
                
                // Check if already verified
                if (isDiscordUserVerified(userId)) {
                    event.getMessage().reply("❌ Your Discord account is already linked to a Minecraft account!").queue();
                    return;
                }
                
                // Generate verification code
                String code = generateVerificationCode(userId, userName);
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.BLUE)
                    .setTitle("🔐 Minecraft Account Verification")
                    .setDescription("To link your Discord account with your Minecraft account, follow these steps:")
                    .addField("Step 1", "Join the Minecraft server", false)
                    .addField("Step 2", "Use the following command in Minecraft:\n`/verify " + code + "`", false)
                    .addField("Your Code", "**`" + code + "`**", true)
                    .addField("Expires In", codeExpiryMinutes + " minutes", true)
                    .setFooter("This code is unique to you and can only be used once", null)
                    .setTimestamp(Instant.now());
                
                event.getMessage().replyEmbeds(embed.build()).queue();
                
                info("Generated verification code for Discord user " + userName + " (" + userId + ")");
            }
        }
    }
    
    /**
     * Pending verification data
     */
    private static class PendingVerification {
        final String discordId;
        final String discordName;
        final String code;
        final long timestamp;
        
        PendingVerification(String discordId, String discordName, String code) {
            this.discordId = discordId;
            this.discordName = discordName;
            this.code = code;
            this.timestamp = System.currentTimeMillis();
        }
    }
}