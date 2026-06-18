package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class WebhookManager {

    private final XDiscordUltimate plugin;
    private final OkHttpClient httpClient;
    private final ScheduledExecutorService scheduler;

    private static final Pattern WEBHOOK_PATTERN = Pattern.compile(
        "https://discord(?:app)?\\.com/api/webhooks/\\d+/[\\w-]+"
    );

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // Rate limiting: per-webhook minimum spacing to avoid 429s and thread blocking
    private final ConcurrentHashMap<String, Long> lastSendTimePerWebhook = new ConcurrentHashMap<>();
    private static final long MIN_MESSAGE_INTERVAL_MS = 250; // 4 messages per second per webhook max

    public WebhookManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "XDiscordUltimate-Webhook");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Validate a webhook URL format
     */
    public boolean isValidWebhookUrl(String url) {
        return url != null && WEBHOOK_PATTERN.matcher(url).matches();
    }

    /**
     * Extract webhook ID from a Discord webhook URL.
     * @param url The webhook URL
     * @return The webhook ID or null if invalid
     */
    public static String extractWebhookId(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        try {
            // Pattern: https://discord.com/api/webhooks/ID/TOKEN
            String[] parts = url.split("/");
            if (parts.length >= 6 && parts[4].equals("webhooks")) {
                String id = parts[5];
                // Verify it's a valid numeric ID
                if (id.matches("\\d+")) {
                    return id;
                }
            }
        } catch (Exception e) {
            // Invalid format
        }

        return null;
    }

    /**
     * Send a message via Discord webhook with custom username and avatar.
     * The send is scheduled so the caller thread is never blocked by rate limiting.
     */
    public CompletableFuture<Boolean> sendMessage(String webhookUrl, String username, String avatarUrl, String content) {
        JsonObject json = new JsonObject();
        json.addProperty("username", sanitizeUsername(username));
        json.addProperty("content", content);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.addProperty("avatar_url", avatarUrl);
        }

        return scheduleSend(webhookUrl, json);
    }

    /**
     * Send a message with embed via webhook.
     * The send is scheduled so the caller thread is never blocked by rate limiting.
     */
    public CompletableFuture<Boolean> sendEmbed(String webhookUrl, String username, String avatarUrl,
                                                 String title, String description, int color) {
        JsonObject embed = new JsonObject();
        if (title != null) embed.addProperty("title", title);
        if (description != null) embed.addProperty("description", description);
        embed.addProperty("color", color);

        com.google.gson.JsonArray embeds = new com.google.gson.JsonArray();
        embeds.add(embed);

        JsonObject json = new JsonObject();
        json.addProperty("username", sanitizeUsername(username));
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            json.addProperty("avatar_url", avatarUrl);
        }
        json.add("embeds", embeds);

        return scheduleSend(webhookUrl, json);
    }

    /**
     * Schedule a webhook send, enforcing a per-webhook rate limit without blocking.
     */
    private CompletableFuture<Boolean> scheduleSend(String webhookUrl, JsonObject json) {
        long now = System.currentTimeMillis();
        long lastSend = lastSendTimePerWebhook.getOrDefault(webhookUrl, 0L);
        long delay = Math.max(0, MIN_MESSAGE_INTERVAL_MS - (now - lastSend));

        // Record the scheduled send time to maintain spacing between consecutive calls
        lastSendTimePerWebhook.put(webhookUrl, now + delay);

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        scheduler.schedule(() -> executeSend(webhookUrl, json, future), delay, TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Execute the HTTP request on the scheduler thread.
     */
    private void executeSend(String webhookUrl, JsonObject json, CompletableFuture<Boolean> future) {
        RequestBody body = RequestBody.create(json.toString(), JSON);
        Request request = new Request.Builder()
            .url(webhookUrl)
            .post(body)
            .addHeader("Content-Type", "application/json")
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                future.complete(true);
            } else {
                plugin.getLogger().warning("Webhook failed: " + response.code() + " - " + response.message());
                future.complete(false);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Webhook error: " + e.getMessage());
            future.complete(false);
        }
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
                return "https://crafthead.net/avatar/" + uuidStr + "/128";
        }
    }

    public static String getPlayerAvatarUrl(UUID uuid, String playerName, String provider) {
        if (playerName != null && !playerName.isEmpty()) {
            return "https://crafthead.net/avatar/" + playerName + "/128";
        }
        return getPlayerAvatarUrl(uuid, provider);
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
     * Shutdown the HTTP client and scheduler
     */
    public void shutdown() {
        scheduler.shutdown();
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
}
