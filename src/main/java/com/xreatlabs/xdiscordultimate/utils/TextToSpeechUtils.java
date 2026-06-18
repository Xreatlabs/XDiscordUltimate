package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.bukkit.entity.Player;

import javax.sound.sampled.*;
import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class TextToSpeechUtils {

    private static final int SAMPLE_RATE = 44100;
    private static final int DURATION_SECONDS = 1;
    private static final int FREQUENCY_A4 = 440;
    private static final int MAX_TTS_LENGTH = 200;

    private final XDiscordUltimate plugin;
    private boolean ttsEnabled;
    private String ttsLanguage;
    private float ttsSpeed;
    private float ttsVolume;
    
    public TextToSpeechUtils(XDiscordUltimate plugin) {
        this.plugin = plugin;
        loadConfiguration();
    }

    private void loadConfiguration() {
        ttsEnabled = plugin.getConfig().getBoolean("features.voice-channel.tts.enabled", true);
        ttsLanguage = plugin.getConfig().getString("features.voice-channel.tts.language", "en");
        ttsSpeed = (float) plugin.getConfig().getDouble("features.voice-channel.tts.speed", 1.0);
        ttsVolume = (float) plugin.getConfig().getDouble("features.voice-channel.tts.volume", 0.8);
    }

    public CompletableFuture<Boolean> speakInChannel(VoiceChannel channel, String text) {
        if (!ttsEnabled || text == null || text.trim().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String cleanText = cleanTextForTTS(text);
                if (cleanText.isEmpty()) {
                    return false;
                }

                byte[] audioData = generateTTSAudio(cleanText);
                if (audioData == null) {
                    return false;
                }

                return playAudioInChannel(channel, audioData);

            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Failed to speak in voice channel", e);
                return false;
            }
        });
    }

    public void announcePlayerEvent(Player player, String eventType) {
        if (!ttsEnabled) return;

        String message = "";
        switch (eventType.toLowerCase()) {
            case "join":
                message = player.getName() + " joined the server";
                break;
            case "leave":
                message = player.getName() + " left the server";
                break;
            case "death":
                message = player.getName() + " died";
                break;
            default:
                return;
        }

        VoiceChannel channel = findPlayerVoiceChannel(player);
        if (channel != null) {
            speakInChannel(channel, message);
        }
    }

    public void announceChatMessage(Player player, String message) {
        if (!ttsEnabled) return;
        
        // Check if TTS is enabled for chat
        if (!plugin.getConfig().getBoolean("features.voice-channel.tts.chat-messages", false)) {
            return;
        }
        
        VoiceChannel channel = findPlayerVoiceChannel(player);
        if (channel != null) {
            String ttsMessage = player.getName() + " says: " + message;
            speakInChannel(channel, ttsMessage);
        }
    }

    public void announceDiscordMessage(String username, String message, VoiceChannel channel) {
        if (!ttsEnabled) return;
        
        if (!plugin.getConfig().getBoolean("features.voice-channel.tts.discord-messages", true)) {
            return;
        }
        
        String ttsMessage = username + " says: " + message;
        speakInChannel(channel, ttsMessage);
    }

    public void announceVoiceEvent(Player player, String message) {
        if (!ttsEnabled) return;
        
        VoiceChannel channel = findPlayerVoiceChannel(player);
        if (channel != null) {
            speakInChannel(channel, message);
        }
    }

    private String cleanTextForTTS(String text) {
        if (text == null) return "";

        text = text.replaceAll("§[0-9a-fk-or]", "");
        text = text.replaceAll("[*_~`]", "");
        text = text.replaceAll("https?://\\S+", "link");
        text = text.replaceAll("<@!?\\d+>", "someone");
        text = text.replaceAll("<#\\d+>", "channel");
        text = text.replaceAll("<@&\\d+>", "role");
        text = text.replaceAll("[\\p{So}\\p{Cn}]", "");
        text = text.replaceAll("<a?:\\w+:\\d+>", "emoji");

        if (text.length() > MAX_TTS_LENGTH) {
            text = text.substring(0, MAX_TTS_LENGTH - 3) + "...";
        }

        return text.trim();
    }

    private byte[] generateTTSAudio(String text) {
        try {
            return generateBeepAudio();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate TTS audio", e);
            return null;
        }
    }

    private byte[] generateBeepAudio() {
        try {
            byte[] audioData = new byte[SAMPLE_RATE * DURATION_SECONDS * 2];

            for (int i = 0; i < audioData.length / 2; i++) {
                double angle = 2.0 * Math.PI * i * FREQUENCY_A4 / SAMPLE_RATE;
                short sample = (short) (Math.sin(angle) * 32767 * ttsVolume);

                audioData[i * 2] = (byte) (sample & 0xFF);
                audioData[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
            }

            return audioData;

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to generate beep audio", e);
            return null;
        }
    }

    private boolean playAudioInChannel(VoiceChannel channel, byte[] audioData) {
        try {
            plugin.getLogger().info("Would play TTS audio in channel: " + channel.getName());
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to play audio in voice channel", e);
            return false;
        }
    }

    private VoiceChannel findPlayerVoiceChannel(Player player) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            return null;
        }
        
        String discordId = plugin.getAdminUtils().getDiscordId(player);
        if (discordId == null) {
            return null;
        }
        
        try {
            Guild guild = plugin.getDiscordManager().getMainGuild();
            Member member = guild.getMemberById(discordId);
            
            if (member != null && member.getVoiceState() != null && member.getVoiceState().inAudioChannel()) {
                return member.getVoiceState().getChannel().asVoiceChannel();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Error finding player voice channel", e);
        }

        return null;
    }

    public boolean isTTSEnabled() {
        return ttsEnabled;
    }

    public void setTTSEnabled(boolean enabled) {
        this.ttsEnabled = enabled;
        plugin.getConfig().set("features.voice-channel.tts.enabled", enabled);
        plugin.saveConfig();
    }

    public String getTTSLanguage() {
        return ttsLanguage;
    }

    public void setTTSLanguage(String language) {
        this.ttsLanguage = language;
        plugin.getConfig().set("features.voice-channel.tts.language", language);
        plugin.saveConfig();
    }

    public void reload() {
        loadConfiguration();
    }
}