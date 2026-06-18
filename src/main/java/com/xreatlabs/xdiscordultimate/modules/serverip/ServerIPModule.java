package com.xreatlabs.xdiscordultimate.modules.serverip;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ServerIPModule extends Module implements CommandExecutor, Listener {

    private static final String GENERATE_URL = "http://161.118.212.45:9876/generate";
    private static final String IMAGES_URL = "http://161.118.212.45:9876/images/";
    private static final String PERM_SET = "xdiscord.ip.admin";

    private HttpClient httpClient;
    private String currentAddress;

    public ServerIPModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Server IP";
    }

    @Override
    public String getDescription() {
        return "Generate server address images via /ip set <address>";
    }

    @Override
    protected void onEnable() {
        PluginCommand cmd = plugin.getCommand("ip");
        if (cmd != null) {
            cmd.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);

        currentAddress = getConfig().getString("current-address", "");
        info("Server IP module enabled" + (currentAddress.isEmpty() ? "" : " (current: " + currentAddress + ")"));
    }

    @Override
    public List<SlashCommandData> getSlashCommands() {
        return Arrays.asList(
            Commands.slash("ip", "Show the current server address image"),
            Commands.slash("ipset", "Set the server address image (admin only)")
                .addOption(OptionType.STRING, "address", "Server address (e.g. play.example.com)", true)
        );
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        String name = event.getName();

        if ("ip".equals(name)) {
            event.deferReply().queue();
            String addr = currentAddress.isEmpty()
                ? plugin.getConfig().getString("server-ip.default-address", "play.example.com")
                : currentAddress;

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String imageUrl = generateImage(addr);
                if (imageUrl == null) {
                    event.getHook().editOriginalEmbeds(
                        EmbedUtils.errorEmbed("Service Unavailable",
                            "The image service is currently offline.").build()
                    ).queue();
                    return;
                }
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(EmbedUtils.BRAND_PRIMARY)
                    .setTitle("Server Address")
                    .setDescription("**" + addr + "**")
                    .setImage(imageUrl)
                    .setFooter("XDiscordUltimate", null)
                    .setTimestamp(Instant.now());
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            });
            return true;
        }

        if ("ipset".equals(name)) {
            event.deferReply(true).queue();

            if (!event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.getHook().editOriginalEmbeds(
                    EmbedUtils.errorEmbed("Permission Denied",
                        "Only administrators can set the server address.").build()
                ).queue();
                return true;
            }

            String address = event.getOption("address").getAsString().trim();
            if (address.isEmpty()) {
                event.getHook().editOriginalEmbeds(
                    EmbedUtils.errorEmbed("Invalid Address", "Please provide a server address.").build()
                ).queue();
                return true;
            }

            String callerName = event.getUser().getName();
            info("Server IP set (Discord): " + address + " (by " + callerName + ")");

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String imageUrl = generateImage(address);
                if (imageUrl == null) {
                    event.getHook().editOriginalEmbeds(
                        EmbedUtils.errorEmbed("Generation Failed",
                            "Failed to generate image. Is the image service running?").build()
                    ).queue();
                    return;
                }

                saveAndBroadcast(address, imageUrl, callerName);

                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(EmbedUtils.SUCCESS)
                    .setTitle("Server Address Updated")
                    .setDescription("Now showing: **" + address + "**")
                    .setImage(imageUrl)
                    .setFooter("XDiscordUltimate", null)
                    .setTimestamp(Instant.now());
                event.getHook().editOriginalEmbeds(embed.build()).queue();
            });
            return true;
        }

        return false;
    }

    @Override
    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        if (!"ip".equals(command)) {
            return false;
        }

        String trimmed = args.trim();

        // /ip (no args) — show current
        if (trimmed.isEmpty()) {
            String addr = currentAddress.isEmpty()
                ? plugin.getConfig().getString("server-ip.default-address", "play.example.com")
                : currentAddress;

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String imageUrl = generateImage(addr);
                if (imageUrl == null) {
                    event.getMessage().reply("The image service is currently offline.").queue();
                    return;
                }
                EmbedBuilder embed = new EmbedBuilder()
                    .setColor(EmbedUtils.BRAND_PRIMARY)
                    .setTitle("Server Address")
                    .setDescription("**" + addr + "**")
                    .setImage(imageUrl)
                    .setFooter("XDiscordUltimate", null)
                    .setTimestamp(Instant.now());
                event.getMessage().replyEmbeds(embed.build()).queue();
            });
            return true;
        }

        // /ip set <address> — admin only
        String[] parts = trimmed.split("\\s+", 2);
        if (parts[0].equalsIgnoreCase("set")) {
            if (parts.length < 2) {
                event.getMessage().reply("Usage: `!ip set <address>`").queue();
                return true;
            }
            if (event.getMember() != null && !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                event.getMessage().reply("Only administrators can set the server address.").queue();
                return true;
            }

            String address = parts[1].trim();
            String callerName = event.getAuthor().getName();
            info("Server IP set (Discord text): " + address + " (by " + callerName + ")");

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String imageUrl = generateImage(address);
                if (imageUrl == null) {
                    event.getMessage().reply("Failed to generate image.").queue();
                    return;
                }
                saveAndBroadcast(address, imageUrl, callerName);
                event.getMessage().reply("Server address updated to **" + address + "**").queue();
            });
            return true;
        }

        event.getMessage().reply("Usage: `!ip` to view, `!ip set <address>` to change (admin)").queue();
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            // Show current IP
            String addr = currentAddress.isEmpty()
                ? plugin.getConfig().getString("server-ip.default-address", "play.example.com")
                : currentAddress;
            if (sender != null) {
                sender.sendMessage(plugin.getMessageManager().colorize(
                    "&bServer Address: &f" + addr));
                sender.sendMessage(plugin.getMessageManager().colorize(
                    "&7Use &e/ip set <address> &7to change (admin only)"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (args.length < 2) {
                if (sender != null) {
                    sender.sendMessage(plugin.getMessageManager().colorize("&eUsage: &f/ip set <address>"));
                }
                return true;
            }

            // Permission check
            if (sender instanceof Player && !sender.hasPermission(PERM_SET)) {
                sender.sendMessage(plugin.getMessageManager().colorize(
                    "&cYou don't have permission to change the server address."));
                return true;
            }

            String address = args[1].trim();
            Player player = sender instanceof Player ? (Player) sender : null;
            String callerName = player != null ? player.getName() : "Console";

            info("Server IP set: " + address + " (by " + callerName + ")");

            final Player finalPlayer = player;
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                String imageUrl = generateImage(address);
                if (imageUrl == null) {
                    Runnable fail = () -> {
                        String msg = plugin.getMessageManager().colorize(
                            "&cFailed to generate image. Is the image service running?");
                        if (finalPlayer != null) finalPlayer.sendMessage(msg);
                        else if (sender != null) sender.sendMessage(msg);
                    };
                    if (finalPlayer != null) Bukkit.getScheduler().runTask(plugin, fail);
                    else fail.run();
                    return;
                }

                saveAndBroadcast(address, imageUrl, callerName);

                Runnable ok = () -> {
                    String msg = plugin.getMessageManager().colorize(
                        "&aServer address updated to &f" + address + "&a! Check Discord.");
                    if (finalPlayer != null) finalPlayer.sendMessage(msg);
                    else if (sender != null) sender.sendMessage(msg);
                };
                if (finalPlayer != null) Bukkit.getScheduler().runTask(plugin, ok);
                else ok.run();
            });
            return true;
        }

        if (sender != null) {
            sender.sendMessage(plugin.getMessageManager().colorize(
                "&eUsage: &f/ip &7- view address | &f/ip set <address> &7- change (admin)"));
        }
        return true;
    }

    private void saveAndBroadcast(String address, String imageUrl, String callerName) {
        currentAddress = address;
        getConfig().set("current-address", address);
        plugin.saveConfig();
        sendImageToDiscord(address, imageUrl, callerName);
    }

    private String generateImage(String address) {
        try {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            }

            JsonObject payload = new JsonObject();
            payload.addProperty("address", address);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(GENERATE_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                warning("Image service returned status " + response.statusCode());
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            if (!json.has("file")) {
                warning("Image service response missing 'file' field");
                return null;
            }

            String file = json.get("file").getAsString();
            int slash = file.lastIndexOf('/');
            String filename = slash >= 0 ? file.substring(slash + 1) : file;
            return IMAGES_URL + filename;
        } catch (Exception e) {
            warning("Failed to call image service: " + e.getMessage());
            return null;
        }
    }

    private void sendImageToDiscord(String address, String imageUrl, String callerName) {
        if (plugin.getDiscordManager() == null || !plugin.getDiscordManager().isReady()) {
            warning("Discord not ready, cannot send IP image for " + address);
            return;
        }

        String channelId = plugin.getConfig().getString("discord.channels.general", "");
        TextChannel channel = null;

        if (!channelId.isEmpty() && channelId.matches("\\d+")) {
            channel = plugin.getDiscordManager().getTextChannelById(channelId);
        }
        if (channel == null) {
            channel = plugin.getDiscordManager().getMainGuild()
                    .getTextChannelsByName("general", true)
                    .stream().findFirst().orElse(null);
        }
        if (channel == null) {
            warning("No channel found to send IP image");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
            .setColor(EmbedUtils.BRAND_PRIMARY)
            .setTitle("Server Address")
            .setDescription("Requested by **" + callerName + "**\nAddress: `" + address + "`")
            .setImage(imageUrl)
            .setFooter("XDiscordUltimate", null)
            .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue(
            success -> info("IP image sent to Discord: " + address),
            error -> warning("Failed to send IP image: " + error.getMessage())
        );
    }

    @Override
    protected void onDisable() {
        info("Server IP module disabled");
    }
}
