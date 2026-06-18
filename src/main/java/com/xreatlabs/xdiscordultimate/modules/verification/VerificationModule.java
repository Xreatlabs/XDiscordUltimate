package com.xreatlabs.xdiscordultimate.modules.verification;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.database.DatabaseManager;
import com.xreatlabs.xdiscordultimate.events.PlayerUnlinkedEvent;
import com.xreatlabs.xdiscordultimate.events.PlayerVerifiedEvent;
import com.xreatlabs.xdiscordultimate.events.XDiscordEventBus;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class VerificationModule extends Module {
    
    private final Map<String, PendingVerification> pendingVerifications;
    private final Map<UUID, Long> verificationCooldowns;
    private final Map<UUID, Integer> failedAttempts;
    private VerificationListener verificationListener;
    
    // Verification settings
    private int kickAfterMinutes;
    private boolean whitelistMode;
    private String verifiedRole;
    private String verifiedGroup;
    private int codeLength;
    private int codeExpiryMinutes;
    private boolean syncNickname;
    private boolean oneTimeOnly;
    private int verifyCooldownSeconds;
    private int failedAttemptCooldownSeconds;
    private int maxVerifyAttempts;
    private int verifyLockoutSeconds;

    private static final java.security.SecureRandom SECURE_RANDOM = new java.security.SecureRandom();
    
    public VerificationModule(XDiscordUltimate plugin) {
        super(plugin);
        this.pendingVerifications = new ConcurrentHashMap<>();
        this.verificationCooldowns = new ConcurrentHashMap<>();
        this.failedAttempts = new ConcurrentHashMap<>();
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
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            verificationListener = new VerificationListener();
            plugin.getDiscordManager().getJDA().addEventListener(verificationListener);
            info("Verification connected to Discord");
        }
    }

    @Override
    public List<SlashCommandData> getSlashCommands() {
        return Collections.singletonList(Commands.slash("verify", "Link your Discord account to Minecraft"));
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        if (!"verify".equals(event.getName())) {
            return false;
        }

        event.deferReply(true).queue();
        String discordId = event.getUser().getId();

        plugin.getDatabaseManager().isDiscordLinked(discordId).thenAccept(isLinked -> {
            if (isLinked && oneTimeOnly) {
                DatabaseManager.LinkedAccountInfo info = getLinkedAccountInfo(discordId);
                EmbedBuilder embed = info != null
                    ? buildAlreadyLinkedEmbed(info, event.getUser())
                    : EmbedUtils.successEmbed("Account Already Linked",
                        "Your Discord account is already linked to a Minecraft account.");
                event.getHook().editOriginalEmbeds(embed.build()).queue();
                return;
            }

            String code = generateVerificationCode(discordId, event.getUser().getName());
            event.getHook().editOriginalEmbeds(EmbedUtils.verificationEmbed(code, codeExpiryMinutes).build()).queue();
        });
        return true;
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"verify".equals(command)) {
            return false;
        }

        String discordId = event.getAuthor().getId();
        plugin.getDatabaseManager().isDiscordLinked(discordId).thenAccept(isLinked -> {
            if (isLinked && oneTimeOnly) {
                DatabaseManager.LinkedAccountInfo info = getLinkedAccountInfo(discordId);
                EmbedBuilder embed = info != null
                    ? buildAlreadyLinkedEmbed(info, event.getAuthor())
                    : EmbedUtils.successEmbed("Account Already Linked",
                        "Your Discord account is already linked to a Minecraft account.");
                event.getMessage().replyEmbeds(embed.build()).queue();
                return;
            }

            String code = generateVerificationCode(discordId, event.getAuthor().getName());
            event.getMessage().replyEmbeds(EmbedUtils.verificationEmbed(code, codeExpiryMinutes).build()).queue();
        });
        return true;
    }
    
    @Override
    protected void onDisable() {
        if (verificationListener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(verificationListener);
        }

        pendingVerifications.clear();
        verificationCooldowns.clear();
        failedAttempts.clear();

        // Clean up orphaned pending verification codes from database
        plugin.getDatabaseManager().cleanExpiredVerificationCodes();
        info("Cleaned up orphaned verification codes on disable");
    }
    
    private void loadConfiguration() {
        // Defaults aligned with the values shipped in config.yml (features.verification)
        kickAfterMinutes = getConfig().getInt("kick-after-minutes", 0);
        whitelistMode = getConfig().getBoolean("whitelist-mode", false);
        verifiedRole = getConfig().getString("verified-role", "Verified");
        verifiedGroup = getConfig().getString("verified-group", "verified");
        codeLength = getConfig().getInt("code-length", 6);
        codeExpiryMinutes = getConfig().getInt("code-expiry-minutes", 5);
        syncNickname = getConfig().getBoolean("sync-nickname", true);
        oneTimeOnly = getConfig().getBoolean("one-time-only", false);

        verifyCooldownSeconds = getConfig().getInt("verify-cooldown-seconds", 30);
        failedAttemptCooldownSeconds = getConfig().getInt("failed-attempt-cooldown-seconds", 5);
        maxVerifyAttempts = getConfig().getInt("max-verify-attempts", 5);
        verifyLockoutSeconds = getConfig().getInt("verify-lockout-seconds", 300);
    }

    /**
     * Fetch the linked account info (MC name, Discord name, link timestamp) for a Discord user.
     * Returns null when the Discord user is not linked.
     */
    public DatabaseManager.LinkedAccountInfo getLinkedAccountInfo(String discordId) {
        return plugin.getDatabaseManager().getLinkedAccountInfoSync(discordId);
    }

    /**
     * Build the "Account Already Linked" success embed shown when a verified user re-runs /verify.
     */
    private EmbedBuilder buildAlreadyLinkedEmbed(DatabaseManager.LinkedAccountInfo info, User user) {
        String minecraftName = info.getMinecraftName() != null ? info.getMinecraftName() : "Unknown";
        String discordDisplay = info.getDiscordName() != null ? info.getDiscordName()
            : (user != null ? user.getName() : "Unknown");

        String avatarUrl = info.getMinecraftUuid() != null
            ? EmbedUtils.getPlayerAvatarUrl(info.getMinecraftUuid())
            : EmbedUtils.getPlayerAvatarUrlByUsername(minecraftName);

        String linkedSince = formatLinkedDate(info.getLinkedAt());

        EmbedBuilder embed = EmbedUtils.successEmbed("Account Already Linked",
                "Your Discord account is already linked to a Minecraft account.");
        embed.setThumbnail(avatarUrl);
        embed.addField("Minecraft Account", minecraftName, true);
        embed.addField("Discord", discordDisplay, true);
        embed.addField("Linked Since", linkedSince, false);
        embed.setFooter("XDiscordUltimate", null);
        return embed;
    }

    private String formatLinkedDate(Timestamp timestamp) {
        if (timestamp == null) return "Unknown";
        try {
            long epochSeconds = timestamp.getTime() / 1000L;
            return "<t:" + epochSeconds + ":F>";
        } catch (Exception ignored) {
            return timestamp.toLocalDateTime().atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }
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
        plugin.getDatabaseManager().storeVerificationCode(discordId, code, discordName, codeExpiryMinutes);
        
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
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("%seconds%", String.valueOf(remainingSeconds));
                plugin.getMessageManager().sendError(player, "errors.cooldown", placeholders);
                return;
            }
        }

        // Check if player is already verified (only when one-time-only mode is on)
        if (oneTimeOnly && plugin.getAdminUtils().isVerified(player)) {
            plugin.getMessageManager().sendMessage(player, "verification.already-verified");
            return;
        }

        // First check in-memory pending verifications
        PendingVerification pending = pendingVerifications.get(code.toUpperCase());

        // If not found in memory, check database
        if (pending == null) {
            DatabaseManager.VerificationCode dbCode = plugin.getDatabaseManager().getVerificationCode(code.toUpperCase());
            if (dbCode != null && !dbCode.isExpired()) {
                // Create pending verification from database data, preserving the ORIGINAL creation timestamp
                pending = new PendingVerification(dbCode.getDiscordId(), dbCode.getUsername(), dbCode.getCode(), dbCode.getCreatedAt());
                pendingVerifications.put(code.toUpperCase(), pending);
            }
        }

        if (pending == null) {
            registerFailedAttempt(player);
            plugin.getMessageManager().sendError(player, "verification.code-invalid");
            return;
        }
        
        // Check if code is expired (uses the original creation timestamp)
        if (System.currentTimeMillis() - pending.timestamp > codeExpiryMinutes * 60_000L) {
            pendingVerifications.remove(code.toUpperCase());
            plugin.getDatabaseManager().removeVerificationCode(code.toUpperCase());
            registerFailedAttempt(player);
            plugin.getMessageManager().sendError(player, "verification.code-expired");
            return;
        }
        
        // Success: clear failed attempts and apply the normal cooldown
        failedAttempts.remove(uuid);
        verificationCooldowns.put(uuid, System.currentTimeMillis() + verifyCooldownSeconds * 1000L);

        // Complete verification
        completeVerification(player, pending);
    }

    /**
     * Track a failed verification attempt. Applies a short cooldown per failed
     * attempt and a longer lockout once the configured max is reached.
     */
    private void registerFailedAttempt(Player player) {
        UUID uuid = player.getUniqueId();
        int attempts = failedAttempts.merge(uuid, 1, Integer::sum);
        if (attempts >= maxVerifyAttempts) {
            failedAttempts.remove(uuid);
            verificationCooldowns.put(uuid, System.currentTimeMillis() + verifyLockoutSeconds * 1000L);
            Map<String, String> lockoutPlaceholders = new HashMap<>();
            lockoutPlaceholders.put("%seconds%", String.valueOf(verifyLockoutSeconds));
            plugin.getMessageManager().sendError(player, "verification.locked-out", lockoutPlaceholders);
        } else {
            verificationCooldowns.put(uuid, System.currentTimeMillis() + failedAttemptCooldownSeconds * 1000L);
        }
    }
    
    /**
     * Complete the verification process
     */
    private void completeVerification(Player player, PendingVerification pending) {
        // Remove from pending
        pendingVerifications.remove(pending.code);
        
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

                    // Sync Discord nickname to Minecraft name (also updates DB)
                    syncDiscordNickname(pending.discordId, player.getName());

                    // In-game feedback about nickname sync
                    if (syncNickname) {
                        player.sendMessage(plugin.getMessageManager().colorize(
                            "<gradient:#55FF55:#00AA00>✓</gradient> &7Your Discord nickname has been updated to match your Minecraft name"));
                    }

                    // Notify in Discord
                    notifyDiscordVerification(pending.discordId, player.getName());

                    // Emit PlayerVerifiedEvent for other modules to listen to
                    XDiscordEventBus.publish(new PlayerVerifiedEvent(
                        player.getUniqueId(),
                        player.getName(),
                        pending.discordId,
                        pending.discordName
                    ));
                });
                
                info("Player " + player.getName() + " verified with Discord user " + pending.discordName);
            } else {
                plugin.getMessageManager().sendError(player, "verification.verification-failed");
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
        // This is called from JDA callbacks (already async), so a sync DB lookup is safe.
        return plugin.getDatabaseManager().isDiscordLinkedSync(discordId);
    }
    
    /**
     * Generate random verification code
     */
    private String generateRandomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        
        for (int i = 0; i < codeLength; i++) {
            code.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        
        // Ensure code is unique
        String finalCode = code.toString();
        if (pendingVerifications.containsKey(finalCode)) {
            return generateRandomCode();
        }
        
        return finalCode;
    }
    
    /**
     * Add player to LuckPerms group using the API directly
     */
    private void addToLuckPermsGroup(Player player, String group) {
        if (!plugin.isLuckPermsEnabled()) {
            debug("LuckPerms not available, skipping group assignment");
            return;
        }

        try {
            Object luckPerms = plugin.getLuckPerms();
            if (luckPerms == null) {
                debug("LuckPerms reference is null, skipping group assignment");
                return;
            }

            // Use the LuckPerms API directly (compileOnly dependency)
            net.luckperms.api.LuckPerms api = (net.luckperms.api.LuckPerms) luckPerms;
            net.luckperms.api.model.user.UserManager userManager = api.getUserManager();
            net.luckperms.api.model.user.User user = userManager.loadUser(player.getUniqueId()).join();

            if (user != null) {
                // Build the inheritance node for the group
                net.luckperms.api.node.Node node = net.luckperms.api.node.Node.builder("group." + group).build();

                // Add node to user data
                user.data().add(node);

                // Save the user
                userManager.saveUser(user);

                debug("Added player " + player.getName() + " to LuckPerms group: " + group);
            }
        } catch (Exception e) {
            error("Failed to add player to LuckPerms group: " + e.getMessage(), e);
            debug("LuckPerms group assignment failed, but verification will continue");
        }
    }
    
    /**
     * Sync Discord nickname to Minecraft name
     */
    private void syncDiscordNickname(String discordId, String minecraftName) {
        if (!syncNickname) {
            debug("Nickname sync disabled in config");
            return;
        }
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            info("Skipping nickname sync - Discord bot not ready");
            return;
        }

        try {
            Guild guild = plugin.getDiscordManager().getMainGuild();
            if (guild == null) {
                warning("Cannot sync nickname - main guild not found");
                return;
            }

            Member member = guild.getMemberById(discordId);
            if (member == null) {
                info("Cannot sync nickname - member not in guild: " + discordId);
                return;
            }

            if (member.isOwner()) {
                info("Skipping nickname sync for server owner (cannot change owner nickname)");
                return;
            }

            // Check bot permission before attempting
            if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.NICKNAME_MANAGE)) {
                warning("Cannot sync nickname for " + minecraftName
                    + " - bot lacks Manage Nicknames permission");
                return;
            }

            // Skip if nickname is already correct
            if (minecraftName.equals(member.getNickname())) {
                debug("Nickname already matches for " + minecraftName);
                return;
            }

            guild.modifyNickname(member, minecraftName).queue(
                success -> {
                    info("Updated Discord nickname for " + discordId + " to '" + minecraftName + "'");
                    plugin.getDatabaseManager().updateDiscordName(discordId, minecraftName)
                        .thenAccept(updated -> {
                            if (updated) {
                                debug("Persisted updated Discord name to database for " + discordId);
                            } else {
                                warning("Database row not found when updating Discord name for " + discordId);
                            }
                        });
                },
                error -> {
                    String msg = error.getMessage();
                    warning("Failed to sync Discord nickname for " + minecraftName
                        + " (" + discordId + "): " + msg);
                    if (msg != null && msg.toLowerCase().contains("permission")) {
                        warning("Bot does not have permission to manage this member's nickname"
                            + " (member may outrank the bot)");
                    }
                }
            );
        } catch (Exception e) {
            warning("Failed to sync Discord nickname for " + minecraftName + ": " + e.getMessage());
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
                EmbedBuilder embed = EmbedUtils.successEmbed("Verification Successful",
                    "Your Discord account has been linked to Minecraft account **" + minecraftName + "**");

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
     * Unlink a Minecraft account from Discord: removes the LuckPerms group,
     * the Discord verified role, the database row, and notifies the Discord user.
     * Works for both online players and offline (admin) unlinks.
     * Returns true if a link existed and was removed.
     */
    public boolean unlink(UUID minecraftUuid) {
        if (minecraftUuid == null) return false;

        // Look up the linked Discord id (best-effort)
        String discordId = null;
        try {
            discordId = plugin.getDatabaseManager().getDiscordId(minecraftUuid).get();
        } catch (Exception e) {
            error("Failed to look up Discord id for unlink: " + e.getMessage());
        }

        // Remove LuckPerms group
        if (plugin.getLuckPerms() != null && !verifiedGroup.isEmpty()) {
            removeFromLuckPermsGroup(minecraftUuid, verifiedGroup);
        }

        // Remove Discord verified role
        if (discordId != null) {
            removeDiscordRole(discordId);
        }

        // Clear any in-memory state for this account
        final String finalDiscordId = discordId;
        if (finalDiscordId != null) {
            pendingVerifications.entrySet().removeIf(e -> e.getValue().discordId.equals(finalDiscordId));
        }
        failedAttempts.remove(minecraftUuid);
        verificationCooldowns.remove(minecraftUuid);

        // Delete the link from the database
        boolean removed = false;
        try {
            removed = plugin.getDatabaseManager().unlinkAccount(minecraftUuid).get();
        } catch (Exception e) {
            error("Failed to unlink account from database: " + e.getMessage());
        }

        // Notify the Discord user and fire an event so other modules can react
        if (discordId != null) {
            notifyDiscordUnlink(discordId);
            XDiscordEventBus.publish(new PlayerUnlinkedEvent(minecraftUuid, discordId));
        }

        info("Unlinked Minecraft UUID " + minecraftUuid + " (discordId=" + discordId + ", removed=" + removed + ")");
        return removed;
    }

    /**
     * Remove a player from a LuckPerms group (offline-safe via UUID).
     */
    private void removeFromLuckPermsGroup(UUID playerUuid, String group) {
        if (!plugin.isLuckPermsEnabled()) {
            debug("LuckPerms not available, skipping group removal");
            return;
        }
        try {
            Object luckPerms = plugin.getLuckPerms();
            if (luckPerms == null) {
                debug("LuckPerms reference is null, skipping group removal");
                return;
            }

            net.luckperms.api.LuckPerms api = (net.luckperms.api.LuckPerms) luckPerms;
            net.luckperms.api.model.user.UserManager userManager = api.getUserManager();
            net.luckperms.api.model.user.User user = userManager.loadUser(playerUuid).join();

            if (user != null) {
                net.luckperms.api.node.Node node = net.luckperms.api.node.Node.builder("group." + group).build();
                user.data().remove(node);
                userManager.saveUser(user);
                debug("Removed player " + playerUuid + " from LuckPerms group: " + group);
            }
        } catch (Exception e) {
            error("Failed to remove player from LuckPerms group: " + e.getMessage(), e);
        }
    }

    /**
     * Remove the Discord verified role from a user.
     */
    private void removeDiscordRole(String discordId) {
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
            if (member == null) {
                return;
            }

            Role role = null;
            if (verifiedRole.matches("\\d+")) {
                role = guild.getRoleById(verifiedRole);
            }
            if (role == null) {
                role = guild.getRolesByName(verifiedRole, true).stream().findFirst().orElse(null);
            }

            if (role != null) {
                guild.removeRoleFromMember(member, role).queue(
                    success -> debug("Removed verified role from Discord user " + discordId),
                    error -> error("Failed to remove verified role: " + error.getMessage())
                );
            }
        } catch (Exception e) {
            error("Failed to remove Discord role", e);
        }
    }

    /**
     * Notify a Discord user that their account was unlinked.
     */
    private void notifyDiscordUnlink(String discordId) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }
        try {
            User user = plugin.getDiscordManager().getJDA().getUserById(discordId);
            if (user != null) {
                EmbedBuilder embed = EmbedUtils.errorEmbed("Account Unlinked",
                    "Your Discord account has been unlinked from your Minecraft account. " +
                    "Run `/verify` again to link a new account.");
                user.openPrivateChannel().queue(channel ->
                    channel.sendMessageEmbeds(embed.build()).queue(
                        success -> debug("Sent unlink DM to " + user.getName()),
                        error -> debug("Failed to send unlink DM: " + error.getMessage())
                    )
                );
            }
        } catch (Exception e) {
            debug("Failed to notify Discord user of unlink: " + e.getMessage());
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
                    if (oneTimeOnly) {
                        DatabaseManager.LinkedAccountInfo info = getLinkedAccountInfo(userId);
                        EmbedBuilder embed = info != null
                            ? buildAlreadyLinkedEmbed(info, event.getAuthor())
                            : EmbedUtils.successEmbed("Account Already Linked",
                                "Your Discord account is already linked to a Minecraft account.");
                        event.getMessage().replyEmbeds(embed.build()).queue();
                    } else {
                        event.getMessage().replyEmbeds(EmbedUtils.successEmbed("Account Already Linked",
                            "Your Discord account is already linked to a Minecraft account.").build()).queue();
                    }
                    return;
                }

                // Generate verification code
                String code = generateVerificationCode(userId, userName);

                EmbedBuilder embed = EmbedUtils.verificationEmbed(code, codeExpiryMinutes);

                event.getMessage().replyEmbeds(embed.build()).queue();

                info("Generated verification code for Discord user " + userName + " (" + userId + ")");
            }
        }

        /**
         * Keep the stored Discord name fresh when a linked member changes their nickname.
         */
        @Override
        public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event) {
            handleMemberNameChange(event.getMember());
        }

        private void handleMemberNameChange(Member member) {
            if (member == null || member.getUser().isBot()) return;
            String discordId = member.getId();
            if (!isDiscordUserVerified(discordId)) return; // only linked members

            String newName = member.getNickname() != null
                ? member.getNickname()
                : member.getUser().getName();

            plugin.getDatabaseManager().updateDiscordName(discordId, newName).thenAccept(updated -> {
                if (updated) {
                    debug("Re-synced stored Discord name for " + discordId + " -> " + newName);
                }
            });
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
            this(discordId, discordName, code, System.currentTimeMillis());
        }

        PendingVerification(String discordId, String discordName, String code, long timestamp) {
            this.discordId = discordId;
            this.discordName = discordName;
            this.code = code;
            this.timestamp = timestamp;
        }
    }
}