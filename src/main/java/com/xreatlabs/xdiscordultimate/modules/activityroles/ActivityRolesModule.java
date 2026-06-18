package com.xreatlabs.xdiscordultimate.modules.activityroles;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class ActivityRolesModule extends Module implements Listener {

    private final List<ActivityTier> tiers = new ArrayList<>();
    private int syncInterval;
    private boolean removeOldRoles;
    private boolean announcePromotion;
    private BukkitTask syncTask;
    private final Map<UUID, String> lastTierCache = new ConcurrentHashMap<>();

    public ActivityRolesModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Activity Roles";
    }

    @Override
    public String getDescription() {
        return "Auto-assign Discord roles based on playtime tiers";
    }

    @Override
    protected void onEnable() {
        loadConfiguration();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        info("Activity roles module enabled with " + tiers.size() + " tiers");
    }

    @Override
    protected void onDisable() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        PlayerJoinEvent.getHandlerList().unregister(this);
        PlayerQuitEvent.getHandlerList().unregister(this);
        tiers.clear();
        lastTierCache.clear();
    }

    @Override
    public void onDiscordReady() {
        startSyncTask();
        info("Activity roles connected to Discord");

        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::syncAllPlayers, 100L);
    }

    private void loadConfiguration() {
        tiers.clear();

        syncInterval = getConfig().getInt("sync-interval", 300);
        removeOldRoles = getConfig().getBoolean("remove-old-roles", true);
        announcePromotion = getConfig().getBoolean("announce-promotion", true);

        if (getConfig().contains("tiers")) {
            for (String tierName : getConfig().getConfigurationSection("tiers").getKeys(false)) {
                String path = "tiers." + tierName;
                long minHours = getConfig().getLong(path + ".min-hours", 0);
                String roleId = getConfig().getString(path + ".role-id", "");
                String displayName = getConfig().getString(path + ".display-name", tierName);
                String color = getConfig().getString(path + ".color", "&7");

                if (!roleId.isEmpty()) {
                    tiers.add(new ActivityTier(tierName, displayName, minHours, roleId, color));
                }
            }
        }

        if (tiers.isEmpty()) {
            info("No tiers configured, using defaults (you need to set role IDs in config)");
            tiers.add(new ActivityTier("newcomer", "🌱 Newcomer", 0, "", "&a"));
            tiers.add(new ActivityTier("regular", "⭐ Regular", 10, "", "&e"));
            tiers.add(new ActivityTier("veteran", "💎 Veteran", 50, "", "&b"));
            tiers.add(new ActivityTier("elite", "👑 Elite", 100, "", "&6"));
            tiers.add(new ActivityTier("legend", "🏆 Legend", 500, "", "&d"));
        }

        tiers.sort((a, b) -> Long.compare(b.minHours, a.minHours));
    }

    private void startSyncTask() {
        if (syncTask != null) {
            syncTask.cancel();
        }
        
        syncTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            this::syncAllPlayers,
            syncInterval * 20L,
            syncInterval * 20L
        );
        
        info("Activity roles sync started (interval: " + syncInterval + "s)");
    }

    private void syncAllPlayers() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) {
            warning("Cannot sync activity roles: guild not found");
            return;
        }

        int synced = 0;
        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.hasPlayedBefore()) {
                syncPlayerRole(player, guild);
                synced++;
            }
        }
        
        debug("Synced activity roles for " + synced + " players");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            Guild guild = plugin.getDiscordManager().getMainGuild();
            if (guild != null) {
                syncPlayerRole(event.getPlayer(), guild);
            }
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Guild guild = plugin.getDiscordManager().getMainGuild();
            if (guild != null) {
                syncPlayerRole(event.getPlayer(), guild);
            }
        });
    }

    private void syncPlayerRole(OfflinePlayer player, Guild guild) {
        String discordId = plugin.getDatabaseManager().getDiscordIdSync(player.getUniqueId());
        if (discordId == null) {
            return;
        }

        Member member = guild.getMemberById(discordId);
        if (member == null) {
            return;
        }

        long playtimeMinutes;
        try {
            long playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            playtimeMinutes = playtimeTicks / 20 / 60;
        } catch (Exception e) {
            debug("Could not get playtime for " + player.getName());
            return;
        }

        long playtimeHours = playtimeMinutes / 60;

        ActivityTier newTier = null;
        for (ActivityTier tier : tiers) {
            if (playtimeHours >= tier.minHours && !tier.roleId.isEmpty()) {
                newTier = tier;
                break;
            }
        }

        if (newTier == null) {
            return;
        }

        Role newRole = guild.getRoleById(newTier.roleId);
        if (newRole == null) {
            warning("Role not found for tier " + newTier.name + ": " + newTier.roleId);
            return;
        }

        if (member.getRoles().contains(newRole)) {
            return;
        }

        if (removeOldRoles) {
            for (ActivityTier tier : tiers) {
                if (!tier.roleId.isEmpty() && !tier.roleId.equals(newTier.roleId)) {
                    Role oldRole = guild.getRoleById(tier.roleId);
                    if (oldRole != null && member.getRoles().contains(oldRole)) {
                        guild.removeRoleFromMember(member, oldRole).queue(
                            success -> debug("Removed old role " + oldRole.getName() + " from " + member.getEffectiveName()),
                            error -> warning("Failed to remove role: " + error.getMessage())
                        );
                    }
                }
            }
        }

        final ActivityTier finalNewTier = newTier;
        final long finalPlaytimeHours = playtimeHours;
        guild.addRoleToMember(member, newRole).queue(
            success -> {
                info("Assigned role " + newRole.getName() + " to " + member.getEffectiveName() +
                     " (" + finalPlaytimeHours + " hours playtime)");

                String lastTier = lastTierCache.get(player.getUniqueId());
                if (lastTier != null && !lastTier.equals(finalNewTier.name)) {
                    onPlayerPromotion(player, finalNewTier);
                }
                lastTierCache.put(player.getUniqueId(), finalNewTier.name);
            },
            error -> warning("Failed to assign role " + newRole.getName() + ": " + error.getMessage())
        );
    }

    private void onPlayerPromotion(OfflinePlayer player, ActivityTier newTier) {
        if (!announcePromotion) return;

        if (player.isOnline()) {
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(ColorUtils.parse(
                    "<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"
                ));
                onlinePlayer.sendMessage(ColorUtils.parse(
                    "&6&l⬆ &eYou have been promoted to " + newTier.color + newTier.displayName + "&e!"
                ));
                onlinePlayer.sendMessage(ColorUtils.parse(
                    "&7Keep playing to unlock even more rewards!"
                ));
                onlinePlayer.sendMessage(ColorUtils.parse(
                    "<gradient:#FFD700:#FFA500>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"
                ));
            }
        }

        if (plugin.getModuleManager().getModule("chat-bridge") != null) {
            debug("Player " + player.getName() + " promoted to " + newTier.displayName);
        }
    }

    public ActivityTier getPlayerTier(OfflinePlayer player) {
        long playtimeMinutes;
        try {
            long playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            playtimeMinutes = playtimeTicks / 20 / 60;
        } catch (Exception e) {
            return null;
        }

        long playtimeHours = playtimeMinutes / 60;

        for (ActivityTier tier : tiers) {
            if (playtimeHours >= tier.minHours) {
                return tier;
            }
        }
        return null;
    }

    public List<ActivityTier> getTiers() {
        return Collections.unmodifiableList(tiers);
    }

    public static class ActivityTier {
        public final String name;
        public final String displayName;
        public final long minHours;
        public final String roleId;
        public final String color;

        public ActivityTier(String name, String displayName, long minHours, String roleId, String color) {
            this.name = name;
            this.displayName = displayName;
            this.minHours = minHours;
            this.roleId = roleId;
            this.color = color;
        }
    }
}
