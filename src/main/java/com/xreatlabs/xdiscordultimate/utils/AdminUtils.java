package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class AdminUtils {
    
    private final XDiscordUltimate plugin;
    private final List<String> adminIDs;
    
    public AdminUtils(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.adminIDs = plugin.getConfig().getStringList("adminIDs");
    }
    
    /**
     * Check if a Discord user is a trusted admin
     * @param discordUserId The Discord user ID
     * @return true if the user is an admin
     */
    public boolean isTrustedAdmin(String discordUserId) {
        if (discordUserId == null || discordUserId.isEmpty()) {
            return false;
        }
        
        boolean isInList = adminIDs.contains(discordUserId);
        
        if (plugin.getConfig().getBoolean("general.debug", false)) {
            plugin.getLogger().info("Checking admin status for Discord ID: " + discordUserId + " - Result: " + isInList);
        }
        
        return isInList;
    }
    
    /**
     * Check if a Discord user is an admin by Discord ID
     * @param discordId The Discord user ID
     * @return true if the user is an admin
     */
    public boolean isAdminByDiscordId(String discordId) {
        return isTrustedAdmin(discordId);
    }
    
    /**
     * Check if a Discord user has the required role for server control
     * @param discordUserId The Discord user ID
     * @return true if the user has the required role
     */
    public boolean hasControlRole(String discordUserId) {
        if (!plugin.getConfig().getBoolean("features.server-control.require-role", true)) {
            return true; // Role not required
        }
        
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return false;
        }
        
        try {
            String requiredRole = plugin.getConfig().getString("features.server-control.control-role", "Server Admin");
            User user = plugin.getDiscordManager().getJDA().getUserById(discordUserId);
            
            if (user == null) {
                return false;
            }
            
            Member member = plugin.getDiscordManager().getMainGuild().getMember(user);
            if (member == null) {
                return false;
            }
            
            for (Role role : member.getRoles()) {
                if (role.getName().equalsIgnoreCase(requiredRole)) {
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error checking control role for user " + discordUserId, e);
        }
        
        return false;
    }
    
    /**
     * Check if a Discord user can execute server control commands
     * @param discordUserId The Discord user ID
     * @return true if the user can execute commands
     */
    public boolean canExecuteServerControl(String discordUserId) {
        // Allow if user is a trusted admin OR has the required role
        return isTrustedAdmin(discordUserId) || hasControlRole(discordUserId);
    }
    
    /**
     * Check if a player has admin permissions
     * @param player The player to check
     * @return true if the player has admin permissions
     */
    public boolean isAdmin(Player player) {
        return player.hasPermission("xdiscord.admin") || player.isOp();
    }
    
    /**
     * Get Discord user ID from a Minecraft player
     * @param player The Minecraft player
     * @return Discord user ID or null if not linked
     */
    public String getDiscordId(Player player) {
        return getDiscordId(player.getUniqueId());
    }
    
    /**
     * Get Discord user ID from a Minecraft UUID
     * @param uuid The Minecraft UUID
     * @return Discord user ID or null if not linked
     */
    public String getDiscordId(UUID uuid) {
        try {
            // Get from database
            return plugin.getDatabaseManager().getDiscordId(uuid).get();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting Discord ID for UUID " + uuid, e);
            return null;
        }
    }
    
    /**
     * Get Minecraft UUID from Discord user ID
     * @param discordId The Discord user ID
     * @return Minecraft UUID or null if not linked
     */
    public UUID getMinecraftUUID(String discordId) {
        try {
            // Get from database
            return plugin.getDatabaseManager().getMinecraftUuid(discordId).get();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error getting UUID for Discord ID " + discordId, e);
            return null;
        }
    }
    
    /**
     * Check if a player is verified (linked with Discord)
     * @param player The player to check
     * @return true if the player is verified
     */
    public boolean isVerified(Player player) {
        return getDiscordId(player) != null;
    }
    
    /**
     * Check if a player can bypass verification requirement
     * @param player The player to check
     * @return true if the player can bypass verification
     */
    public boolean canBypassVerification(Player player) {
        return player.hasPermission("xdiscord.bypass.verification");
    }
    
    /**
     * Reload admin IDs from config
     */
    public void reload() {
        adminIDs.clear();
        adminIDs.addAll(plugin.getConfig().getStringList("adminIDs"));
    }
    
    /**
     * Add an admin ID
     * @param discordId The Discord ID to add
     */
    public void addAdmin(String discordId) {
        if (!adminIDs.contains(discordId)) {
            adminIDs.add(discordId);
            plugin.getConfig().set("adminIDs", adminIDs);
            plugin.saveConfig();
        }
    }
    
    /**
     * Remove an admin ID
     * @param discordId The Discord ID to remove
     */
    public void removeAdmin(String discordId) {
        if (adminIDs.remove(discordId)) {
            plugin.getConfig().set("adminIDs", adminIDs);
            plugin.saveConfig();
        }
    }
    
    /**
     * Get list of admin IDs
     * @return List of admin Discord IDs
     */
    public List<String> getAdminIDs() {
        return adminIDs;
    }
}