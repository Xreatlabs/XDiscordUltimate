package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class WebhookManager {
    
    private final XDiscordUltimate plugin;
    private final OkHttpClient httpClient;
    
    private static final Pattern WEBHOOK_PATTERN = Pattern.compile(
        "https://discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+"
    );
    
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // Rate limiting
    private long lastMessageTime = 0;
    private static final long MIN_MESSAGE_INTERVAL_MS = 50; // 20 messages per second max
    
    public WebhookManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }
    
    /**
     * Validate a webhook URL format
     */
    public boolean isValidWebhookUrl(String url) {
        return url != null && WEBHOOK_PATTERN.matcher(url).matches();
    }
    
    /**
     * Send a message via Discord webhook with custom username and avatar
     */
    public CompletableFuture<Boolean> sendMessage(String webhookUrl, String username, String avatarUrl, String content) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Rate limiting
                long now = System.currentTimeMillis();
                long timeSinceLastMessage = now - lastMessageTime;
                if (timeSinceLastMessage < MIN_MESSAGE_INTERVAL_MS) {
                    Thread.sleep(MIN_MESSAGE_INTERVAL_MS - timeSinceLastMessage);
                }
                lastMessageTime = System.currentTimeMillis();
                
                // Build JSON payload
                JsonObject json = new JsonObject();
                json.addProperty("username", sanitizeUsername(username));
                json.addProperty("content", content);
                
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    json.addProperty("avatar_url", avatarUrl);
                    plugin.getLogger().info("[Webhook] Sending with avatar: " + avatarUrl);
                } else {
                    plugin.getLogger().warning("[Webhook] No avatar URL provided!");
                }
                
                // Create request
                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
                
                // Execute request
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return true;
                    } else {
                        plugin.getLogger().warning("Webhook failed: " + response.code() + " - " + response.message());
                        return false;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException e) {
                plugin.getLogger().warning("Webhook error: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Send a message with embed via webhook
     */
    public CompletableFuture<Boolean> sendEmbed(String webhookUrl, String username, String avatarUrl, 
                                                 String title, String description, int color) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Rate limiting
                long now = System.currentTimeMillis();
                long timeSinceLastMessage = now - lastMessageTime;
                if (timeSinceLastMessage < MIN_MESSAGE_INTERVAL_MS) {
                    Thread.sleep(MIN_MESSAGE_INTERVAL_MS - timeSinceLastMessage);
                }
                lastMessageTime = System.currentTimeMillis();
                
                // Build embed
                JsonObject embed = new JsonObject();
                if (title != null) embed.addProperty("title", title);
                if (description != null) embed.addProperty("description", description);
                embed.addProperty("color", color);
                
                // Build JSON payload
                JsonObject json = new JsonObject();
                json.addProperty("username", sanitizeUsername(username));
                
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    json.addProperty("avatar_url", avatarUrl);
                }
                
                com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
                embeds.add(embed);
                json.add("embeds", embeds);
                
                // Create request
                RequestBody body = RequestBody.create(json.toString(), JSON);
                Request request = new Request.Builder()
                    .url(webhookUrl)
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();
                
                // Execute request
                try (Response response = httpClient.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        return true;
                    } else {
                        plugin.getLogger().warning("Webhook embed failed: " + response.code() + " - " + response.message());
                        return false;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (IOException e) {
                plugin.getLogger().warning("Webhook embed error: " + e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Get player avatar URL from various skin providers
     */
    public static String getPlayerAvatarUrl(UUID uuid, String provider) {
        if (uuid == null) {
            return "https://crafthead.net/avatar/Steve/128";
        }
        
        String uuidStr = uuid.toString().replace("-", "");
        String safeProvider = (provider != null) ? provider.toLowerCase() : "crafthead";
        
        switch (safeProvider) {
            case "minotar":
                return "https://minotar.net/helm/" + uuidStr + "/128";
            case "crafatar":
                return "https://crafatar.com/avatars/" + uuid.toString() + "?overlay&size=128";
            case "visage":
                return "https://visage.surgeplay.com/face/128/" + uuidStr;
            case "mc-heads":
                return "https://mc-heads.net/avatar/" + uuidStr + "/128";
            case "crafthead":
            default:
                // crafthead.net is reliable and fast
                return "https://crafthead.net/avatar/" + uuidStr + "/128";
        }
    }
    
    /**
     * Get player avatar URL using player name (for Minotar)
     */
    public static String getPlayerAvatarUrlByName(String playerName, String provider) {
        if (playerName == null) return null;
        
        switch (provider.toLowerCase()) {
            case "minotar":
                return "https://minotar.net/helm/" + playerName + "/128";
            default:
                return null; // Other providers need UUID
        }
    }
    
    /**
     * Sanitize username for Discord webhook (2-80 chars, no @everyone/@here)
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "Player";
        }
        
        // Remove mentions
        username = username.replace("@everyone", "everyone")
                          .replace("@here", "here");
        
        // Discord username length limits
        if (username.length() < 2) {
            username = username + "_";
        } else if (username.length() > 80) {
            username = username.substring(0, 80);
        }
        
        return username;
    }
    
    /**
     * Test webhook connectivity
     */
    public CompletableFuture<Boolean> testWebhook(String webhookUrl) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Request request = new Request.Builder()
                    .url(webhookUrl)
                    .get()
                    .build();
                
                try (Response response = httpClient.newCall(request).execute()) {
                    return response.isSuccessful();
                }
            } catch (IOException e) {
                return false;
            }
        });
    }
    
    /**
     * Shutdown the HTTP client
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
