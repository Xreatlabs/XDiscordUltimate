package com.xreatlabs.xdiscordultimate.modules.voicechannel;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class VoiceChannelModule extends Module {

    private String categoryId;
    private String statusChannelId;
    private String statusChannelFormat;
    private String hubChannelId;
    private String dynamicChannelPrefix;
    private int maxDynamicChannels;
    private int userLimitPerChannel;
    private int updateInterval;
    private boolean enableDynamicChannels;
    private boolean enableStatusChannel;

    private BukkitTask updateTask;
    private VoiceListener voiceListener;
    private final Set<String> dynamicChannelIds = ConcurrentHashMap.newKeySet();

    public VoiceChannelModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Voice Channels";
    }

    @Override
    public String getDescription() {
        return "Dynamic voice channels and player count status channel";
    }

    @Override
    protected void onEnable() {
        loadConfiguration();
        info("Voice channel module enabled");
    }

    @Override
    protected void onDisable() {
        if (updateTask != null) {
            updateTask.cancel();
        }
        if (voiceListener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(voiceListener);
        }
        
        // Clean up dynamic channels
        cleanupDynamicChannels();
    }

    @Override
    public void onDiscordReady() {
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            voiceListener = new VoiceListener();
            plugin.getDiscordManager().getJDA().addEventListener(voiceListener);
            
            startUpdateTask();
            
            // Initial update
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, this::updateStatusChannel, 60L);
            
            info("Voice channel module connected to Discord");
        }
    }

    private void loadConfiguration() {
        categoryId = getConfig().getString("category-id", "");
        statusChannelId = getConfig().getString("status-channel-id", "");
        statusChannelFormat = getConfig().getString("status-channel-format", "📊 Players: %online%/%max%");
        hubChannelId = getConfig().getString("hub-channel-id", "");
        dynamicChannelPrefix = getConfig().getString("dynamic-channel-prefix", "🎮 Gaming Room");
        maxDynamicChannels = getConfig().getInt("max-dynamic-channels", 10);
        userLimitPerChannel = getConfig().getInt("user-limit", 0); // 0 = unlimited
        updateInterval = getConfig().getInt("update-interval", 60);
        enableDynamicChannels = getConfig().getBoolean("enable-dynamic-channels", true);
        enableStatusChannel = getConfig().getBoolean("enable-status-channel", true);
    }

    private void startUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
        }

        updateTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
            this::updateStatusChannel,
            updateInterval * 20L,
            updateInterval * 20L
        );

        info("Voice channel update task started (interval: " + updateInterval + "s)");
    }

    private void updateStatusChannel() {
        if (!enableStatusChannel || statusChannelId == null || statusChannelId.isEmpty()) {
            return;
        }

        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return;

        VoiceChannel channel = guild.getVoiceChannelById(statusChannelId);
        if (channel == null) {
            debug("Status voice channel not found: " + statusChannelId);
            return;
        }

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        
        // Calculate TPS
        double tps = getTPS();
        String tpsStr = String.format("%.1f", tps);
        
        // Format the channel name
        String newName = statusChannelFormat
            .replace("%online%", String.valueOf(online))
            .replace("%max%", String.valueOf(max))
            .replace("%tps%", tpsStr);

        // Only update if name changed (to avoid rate limits)
        if (!channel.getName().equals(newName)) {
            channel.getManager().setName(newName).queue(
                success -> debug("Updated status channel: " + newName),
                error -> warning("Failed to update status channel: " + error.getMessage())
            );
        }
    }

    private double getTPS() {
        try {
            Object server = Bukkit.getServer().getClass().getMethod("getServer").invoke(Bukkit.getServer());
            double[] tps = (double[]) server.getClass().getField("recentTps").get(server);
            return Math.min(20.0, tps[0]);
        } catch (Exception e) {
            return 20.0;
        }
    }

    private class VoiceListener extends ListenerAdapter {
        @Override
        public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
            if (!enableDynamicChannels) return;

            VoiceChannel joinedChannel = (VoiceChannel) event.getChannelJoined();
            VoiceChannel leftChannel = (VoiceChannel) event.getChannelLeft();

            // Handle joining hub channel
            if (joinedChannel != null && joinedChannel.getId().equals(hubChannelId)) {
                createDynamicChannel(event.getGuild(), event.getMember());
            }

            // Handle leaving dynamic channel
            if (leftChannel != null && dynamicChannelIds.contains(leftChannel.getId())) {
                checkAndDeleteEmptyChannel(leftChannel);
            }

            // Handle joining dynamic channel - check if we need more rooms
            if (joinedChannel != null && dynamicChannelIds.contains(joinedChannel.getId())) {
                ensureEmptyChannelAvailable(event.getGuild());
            }
        }
    }

    private void createDynamicChannel(Guild guild, net.dv8tion.jda.api.entities.Member member) {
        if (dynamicChannelIds.size() >= maxDynamicChannels) {
            debug("Max dynamic channels reached");
            // Move user to first available channel
            VoiceChannel available = findAvailableDynamicChannel(guild);
            if (available != null) {
                guild.moveVoiceMember(member, available).queue();
            }
            return;
        }

        Category category = null;
        if (categoryId != null && !categoryId.isEmpty()) {
            category = guild.getCategoryById(categoryId);
        }

        int roomNumber = dynamicChannelIds.size() + 1;
        String channelName = dynamicChannelPrefix + " #" + roomNumber;

        var action = guild.createVoiceChannel(channelName);
        if (category != null) {
            action = action.setParent(category);
        }
        if (userLimitPerChannel > 0) {
            action = action.setUserlimit(userLimitPerChannel);
        }

        action.queue(
            channel -> {
                dynamicChannelIds.add(channel.getId());
                // Move the member to the new channel
                guild.moveVoiceMember(member, channel).queue(
                    success -> debug("Created dynamic channel and moved " + member.getEffectiveName()),
                    error -> debug("Created channel but couldn't move member: " + error.getMessage())
                );
                info("Created dynamic voice channel: " + channelName);
            },
            error -> warning("Failed to create dynamic channel: " + error.getMessage())
        );
    }

    private VoiceChannel findAvailableDynamicChannel(Guild guild) {
        for (String channelId : dynamicChannelIds) {
            VoiceChannel channel = guild.getVoiceChannelById(channelId);
            if (channel != null) {
                if (userLimitPerChannel <= 0 || channel.getMembers().size() < userLimitPerChannel) {
                    return channel;
                }
            }
        }
        return null;
    }

    private void checkAndDeleteEmptyChannel(VoiceChannel channel) {
        if (channel.getMembers().isEmpty()) {
            // Keep at least one empty channel if we have multiple
            long emptyChannels = dynamicChannelIds.stream()
                .map(id -> channel.getGuild().getVoiceChannelById(id))
                .filter(c -> c != null && c.getMembers().isEmpty())
                .count();

            if (emptyChannels > 1) {
                dynamicChannelIds.remove(channel.getId());
                channel.delete().queue(
                    success -> info("Deleted empty dynamic channel: " + channel.getName()),
                    error -> warning("Failed to delete channel: " + error.getMessage())
                );
            }
        }
    }

    private void ensureEmptyChannelAvailable(Guild guild) {
        if (dynamicChannelIds.size() >= maxDynamicChannels) return;

        boolean hasEmpty = dynamicChannelIds.stream()
            .map(id -> guild.getVoiceChannelById(id))
            .anyMatch(c -> c != null && c.getMembers().isEmpty());

        if (!hasEmpty) {
            // Create a new empty channel
            Category category = null;
            if (categoryId != null && !categoryId.isEmpty()) {
                category = guild.getCategoryById(categoryId);
            }

            int roomNumber = dynamicChannelIds.size() + 1;
            String channelName = dynamicChannelPrefix + " #" + roomNumber;

            var action = guild.createVoiceChannel(channelName);
            if (category != null) {
                action = action.setParent(category);
            }
            if (userLimitPerChannel > 0) {
                action = action.setUserlimit(userLimitPerChannel);
            }

            action.queue(
                channel -> {
                    dynamicChannelIds.add(channel.getId());
                    info("Created additional dynamic channel: " + channelName);
                },
                error -> warning("Failed to create additional channel: " + error.getMessage())
            );
        }
    }

    private void cleanupDynamicChannels() {
        Guild guild = plugin.getDiscordManager().getMainGuild();
        if (guild == null) return;

        for (String channelId : dynamicChannelIds) {
            VoiceChannel channel = guild.getVoiceChannelById(channelId);
            if (channel != null && channel.getMembers().isEmpty()) {
                channel.delete().queue(
                    success -> debug("Cleaned up dynamic channel: " + channel.getName()),
                    error -> debug("Failed to clean up channel: " + error.getMessage())
                );
            }
        }
        dynamicChannelIds.clear();
    }

    public Set<String> getDynamicChannelIds() {
        return Collections.unmodifiableSet(dynamicChannelIds);
    }
}
