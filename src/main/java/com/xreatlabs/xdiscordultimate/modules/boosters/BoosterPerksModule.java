package com.xreatlabs.xdiscordultimate.modules.boosters;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class BoosterPerksModule extends Module {

    private BukkitTask syncTask;
    private Set<String> boosters = new HashSet<>();
    private String boosterPermissionGroup;
    private List<String> boosterCommands;
    private List<String> removeCommands;
    private int syncInterval;
    
    // Custom booster role detection
    private String boosterRoleId;
    private List<String> additionalBoosterRoleIds;
    private boolean useNativeBoosterDetection;

    public BoosterPerksModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Booster Perks";
    }

    @Override
    public String getDescription() {
        return "Give perks to Discord server boosters";
    }

    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Booster perks module enabled");
    }

    @Override
    protected void onDisable() {
        if (syncTask != null) {
            syncTask.cancel();
            syncTask = null;
        }
        info("Booster perks module disabled");
    }

    @Override
    public void onDiscordReady() {
        startBoosterSync();
    }

    private void loadConfiguration() {
        org.bukkit.configuration.ConfigurationSection config = getConfig();
        if (config != null) {
            boosterPermissionGroup = config.getString("permission-group", "booster");
            boosterCommands = config.getStringList("reward-commands");
            removeCommands = config.getStringList("remove-commands");
            syncInterval = config.getInt("sync-interval", 300);
            
            // Role-based detection settings
            boosterRoleId = config.getString("booster-role-id", "");
            additionalBoosterRoleIds = config.getStringList("additional-booster-roles");
            useNativeBoosterDetection = config.getBoolean("use-native-detection", true);
            
            // Log configuration
            if (!boosterRoleId.isEmpty()) {
                info("Custom booster role ID configured: " + boosterRoleId);
            }
            if (!additionalBoosterRoleIds.isEmpty()) {
                info("Additional booster roles: " + additionalBoosterRoleIds.size());
            }
        } else {
            boosterPermissionGroup = "booster";
            boosterCommands = new ArrayList<>();
            removeCommands = new ArrayList<>();
            syncInterval = 300;
            boosterRoleId = "";
            additionalBoosterRoleIds = new ArrayList<>();
            useNativeBoosterDetection = true;
        }
    }

    private void startBoosterSync() {
        if (syncTask != null) {
            syncTask.cancel();
        }

        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            syncBoosters();
        }, 100L, syncInterval * 20L);

        info("Booster sync started (interval: " + syncInterval + "s)");
    }

    private void syncBoosters() {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return;
        }

        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) {
            return;
        }

        Set<String> currentBoosters = new HashSet<>();
        
        // Collect all booster role IDs to check
        Set<String> boosterRoleIds = new HashSet<>();
        if (!boosterRoleId.isEmpty()) {
            boosterRoleIds.add(boosterRoleId);
        }
        boosterRoleIds.addAll(additionalBoosterRoleIds);

        // Find members who are boosters
        guild.loadMembers().onSuccess(members -> {
            for (Member member : members) {
                boolean isBooster = false;
                
                // Method 1: Native Discord booster detection (member.isBoosting())
                if (useNativeBoosterDetection && member.isBoosting()) {
                    isBooster = true;
                }
                
                // Method 2: Check for booster role IDs
                if (!isBooster && !boosterRoleIds.isEmpty()) {
                    for (Role role : member.getRoles()) {
                        if (boosterRoleIds.contains(role.getId())) {
                            isBooster = true;
                            break;
                        }
                    }
                }
                
                if (isBooster) {
                    String discordId = member.getId();
                    currentBoosters.add(discordId);

                    // Check if this is a NEW booster (wasn't in our list before)
                    if (!boosters.contains(discordId)) {
                        // This runs inside an async Bukkit task, so a sync DB lookup is safe.
                        UUID minecraftUuid = plugin.getDatabaseManager().getMinecraftUuidSync(discordId);
                        if (minecraftUuid != null) {
                            onBoosterAdded(minecraftUuid.toString(), member.getEffectiveName());
                        }
                    }
                }
            }

            // Check for removed boosters
            for (String oldBooster : boosters) {
                if (!currentBoosters.contains(oldBooster)) {
                    // This runs inside an async Bukkit task, so a sync DB lookup is safe.
                    UUID minecraftUuid = plugin.getDatabaseManager().getMinecraftUuidSync(oldBooster);
                    if (minecraftUuid != null) {
                        onBoosterRemoved(minecraftUuid.toString());
                    }
                }
            }

            boosters = currentBoosters;
            debug("Synced " + boosters.size() + " boosters (roles checked: " + boosterRoleIds.size() + ")");
        }).onError(error -> {
            warning("Failed to sync boosters: " + error.getMessage());
        });
    }
    
    /**
     * Check if a member has any booster role
     */
    public boolean hasBoosterRole(Member member) {
        if (member == null) return false;
        
        // Check native boosting status
        if (useNativeBoosterDetection && member.isBoosting()) {
            return true;
        }
        
        // Check custom role IDs
        Set<String> boosterRoleIds = new HashSet<>();
        if (!boosterRoleId.isEmpty()) {
            boosterRoleIds.add(boosterRoleId);
        }
        boosterRoleIds.addAll(additionalBoosterRoleIds);
        
        for (Role role : member.getRoles()) {
            if (boosterRoleIds.contains(role.getId())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Manually trigger booster sync for a specific player
     */
    public void checkAndSyncPlayer(Player player) {
        plugin.getDatabaseManager().getDiscordId(player.getUniqueId())
            .thenAccept(discordId -> {
                if (discordId == null) return;

                Guild guild = plugin.getDiscordManager().getMainGuild();
                if (guild == null) return;

                guild.retrieveMemberById(discordId).queue(member -> {
                    if (member != null && hasBoosterRole(member)) {
                        if (!boosters.contains(discordId)) {
                            boosters.add(discordId);
                            onBoosterAdded(player.getUniqueId().toString(), member.getEffectiveName());
                        }
                    }
                }, error -> {
                    debug("Could not check booster status for " + player.getName());
                });
            })
            .exceptionally(throwable -> {
                warning("Failed to check booster status for " + player.getName() + ": " + throwable.getMessage());
                return null;
            });
    }

    private void onBoosterAdded(String uuid, String discordName) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isLuckPermsEnabled() && !boosterPermissionGroup.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + uuid + " parent add " + boosterPermissionGroup);
                info("Added booster perks to " + uuid);
            }

            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null) {
                for (String cmd : boosterCommands) {
                    String command = cmd
                        .replace("%player%", player.getName())
                        .replace("%uuid%", uuid);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                player.sendMessage(plugin.getMessageManager().colorize("<gradient:#FF55FF:#AA00AA>Thank you for boosting!</gradient> &fYou received booster perks!"));
            }
        });
    }

    private void onBoosterRemoved(String uuid) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            // Remove permission group
            if (plugin.isLuckPermsEnabled() && !boosterPermissionGroup.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "lp user " + uuid + " parent remove " + boosterPermissionGroup);
                info("Removed booster perks from " + uuid);
            }
            
            // Run remove commands
            Player player = Bukkit.getPlayer(UUID.fromString(uuid));
            if (player != null && !removeCommands.isEmpty()) {
                for (String cmd : removeCommands) {
                    String command = cmd
                        .replace("%player%", player.getName())
                        .replace("%uuid%", uuid);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                player.sendMessage(plugin.getMessageManager().colorize("&7Your Discord booster perks have expired."));
            }
        });
    }

    public boolean isBooster(Player player) {
        // Synchronous lookup: this is called from sync contexts (PlaceholderAPI) that must
        // return a value immediately. The database lookup is bounded and does not use .join().
        String discordId = plugin.getDatabaseManager().getDiscordIdSync(player.getUniqueId());
        return discordId != null && boosters.contains(discordId);
    }

    public boolean isBooster(String discordId) {
        return boosters.contains(discordId);
    }

    public int getBoosterCount() {
        return boosters.size();
    }
}
