package com.xreatlabs.xdiscordultimate.modules.economy;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.awt.Color;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class EconomyBridgeModule extends Module {

    private static final String ECONOMY_CLASS = "net.milkbowl.vault.economy.Economy";

    private final Object lock = new Object();

    private volatile Object economyProvider;
    private volatile Method methodGetBalance;
    private volatile Method methodWithdraw;
    private volatile Method methodDeposit;
    private volatile Method methodGetName;

    private EconomyListener listener;
    private String currencySymbol;
    private String currencyName;
    private int topPlayersCount;
    private boolean allowTransfers;
    private double minTransfer;
    private double maxTransfer;
    private double transferTax;

    public EconomyBridgeModule(XDiscordUltimate plugin) {
        super(plugin);
    }

    @Override
    public String getName() {
        return "Economy Bridge";
    }

    @Override
    public String getDescription() {
        return "Discord commands for Vault economy - balance, pay, baltop";
    }

    @Override
    protected void onEnable() {
        if (!setupEconomy()) {
            warning("Vault economy not found - Economy Bridge will remain dormant until an economy plugin is available.");
            return;
        }

        loadConfiguration();
        info("Economy Bridge enabled - Connected to: " + providerName());
    }

    @Override
    protected void onDisable() {
        if (listener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(listener);
        }
    }

    @Override
    public void onDiscordReady() {
        if (!isEconomyAvailable()) return;

        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            listener = new EconomyListener();
            plugin.getDiscordManager().getJDA().addEventListener(listener);
            info("Economy Bridge connected to Discord");
        }
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        try {
            Class<?> economyClass = Class.forName(ECONOMY_CLASS);
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(economyClass);
            if (rsp == null) {
                return false;
            }
            Object provider = rsp.getProvider();
            if (provider == null) {
                return false;
            }
            synchronized (lock) {
                economyProvider = provider;
                methodGetBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
                methodWithdraw = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
                methodDeposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
                methodGetName = economyClass.getMethod("getName");
            }
            return true;
        } catch (ClassNotFoundException e) {
            warning("Vault Economy class not present on classpath - cannot enable Economy Bridge.");
            return false;
        } catch (NoSuchMethodException e) {
            warning("Vault Economy API signature mismatch - cannot enable Economy Bridge: " + e.getMessage());
            return false;
        }
    }

    private void loadConfiguration() {
        currencySymbol = getConfig().getString("currency-symbol", "$");
        currencyName = getConfig().getString("currency-name", "coins");
        topPlayersCount = getConfig().getInt("top-players", 10);
        allowTransfers = getConfig().getBoolean("allow-transfers", true);
        minTransfer = getConfig().getDouble("min-transfer", 1.0);
        maxTransfer = getConfig().getDouble("max-transfer", 1000000.0);
        transferTax = getConfig().getDouble("transfer-tax", 0.0);
    }

    @Override
    public List<SlashCommandData> getSlashCommands() {
        if (!isEconomyAvailable()) {
            return java.util.Collections.emptyList();
        }

        List<SlashCommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("balance", "Check your or another player's balance")
            .addOption(OptionType.STRING, "player", "Player name (optional)", false));

        commands.add(Commands.slash("baltop", "Show the richest players"));

        if (allowTransfers) {
            commands.add(Commands.slash("pay", "Send money to another player")
                .addOption(OptionType.STRING, "player", "Player to send money to", true)
                .addOption(OptionType.NUMBER, "amount", "Amount to send", true));
        }

        commands.add(Commands.slash("economy", "Economy admin commands")
            .addOption(OptionType.STRING, "action", "give/take/set", true)
            .addOption(OptionType.STRING, "player", "Player name", true)
            .addOption(OptionType.NUMBER, "amount", "Amount", true));

        return commands;
    }

    @Override
    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        if (!isEconomyAvailable()) {
            return false;
        }
        switch (event.getName()) {
            case "balance":
                handleBalanceCommand(event);
                return true;
            case "baltop":
                handleBaltopCommand(event);
                return true;
            case "pay":
                handlePayCommand(event);
                return true;
            case "economy":
                handleEconomyAdminCommand(event);
                return true;
            default:
                return false;
        }
    }

    private class EconomyListener extends ListenerAdapter {
        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (event.isAcknowledged()) return;
            handleSlashCommand(event);
        }
    }

    private void handleBalanceCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String playerName = event.getOption("player") != null
            ? event.getOption("player").getAsString()
            : null;

        if (playerName == null) {
            String discordId = event.getUser().getId();
            plugin.getDatabaseManager().getMinecraftUUID(discordId).thenAccept(uuid -> {
                if (uuid == null) {
                    event.getHook().editOriginal("You need to link your account first! Use `/verify` to link, or specify a player name.").queue();
                    return;
                }
                sendBalanceEmbed(event, Bukkit.getOfflinePlayer(uuid));
            });
        } else {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            if (!player.hasPlayedBefore()) {
                event.getHook().editOriginal("Player **" + playerName + "** not found!").queue();
                return;
            }
            sendBalanceEmbed(event, player);
        }
    }

    private void sendBalanceEmbed(SlashCommandInteractionEvent event, OfflinePlayer player) {
        double balance = getBalance(player);
        DecimalFormat df = new DecimalFormat("#,##0.00");

        String avatarUrl = "https://crafthead.net/avatar/" + player.getUniqueId().toString().replace("-", "") + "/128";

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Balance")
            .setColor(new Color(255, 215, 0))
            .setThumbnail(avatarUrl)
            .addField("Player", player.getName(), true)
            .addField("Balance", currencySymbol + df.format(balance) + " " + currencyName, true)
            .setTimestamp(Instant.now());

        int rank = getPlayerRank(player);
        if (rank > 0) {
            embed.addField("Rank", "#" + rank, true);
        }

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private int getPlayerRank(OfflinePlayer targetPlayer) {
        List<OfflinePlayer> sorted = Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(OfflinePlayer::hasPlayedBefore)
            .sorted((a, b) -> Double.compare(getBalance(b), getBalance(a)))
            .collect(Collectors.toList());

        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).getUniqueId().equals(targetPlayer.getUniqueId())) {
                return i + 1;
            }
        }
        return -1;
    }

    private void handleBaltopCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        List<OfflinePlayer> topPlayers = Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(OfflinePlayer::hasPlayedBefore)
            .filter(p -> p.getName() != null)
            .sorted((a, b) -> Double.compare(getBalance(b), getBalance(a)))
            .limit(topPlayersCount)
            .collect(Collectors.toList());

        if (topPlayers.isEmpty()) {
            event.getHook().editOriginal("No player data available yet!").queue();
            return;
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");
        StringBuilder description = new StringBuilder();
        description.append("```\n");

        String[] medals = {"1.", "2.", "3."};

        for (int i = 0; i < topPlayers.size(); i++) {
            OfflinePlayer player = topPlayers.get(i);
            String rank = i < 3 ? medals[i] : String.format("%2d.", i + 1);
            String name = truncate(player.getName(), 16);
            double balance = getBalance(player);

            description.append(String.format("%-3s %-16s %s%s%n",
                rank, name, currencySymbol, df.format(balance)));
        }

        description.append("```");

        double totalEconomy = Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(OfflinePlayer::hasPlayedBefore)
            .mapToDouble(this::getBalance)
            .sum();

        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Richest Players")
            .setDescription(description.toString())
            .setColor(new Color(255, 215, 0))
            .addField("Total Economy", currencySymbol + df.format(totalEconomy) + " " + currencyName, false)
            .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handlePayCommand(SlashCommandInteractionEvent event) {
        if (!allowTransfers) {
            event.reply("Money transfers are disabled on this server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        String targetName = event.getOption("player").getAsString();
        double amount = event.getOption("amount").getAsDouble();

        if (amount < minTransfer) {
            event.getHook().editOriginal("Minimum transfer amount is " + currencySymbol + minTransfer).queue();
            return;
        }
        if (amount > maxTransfer) {
            event.getHook().editOriginal("Maximum transfer amount is " + currencySymbol + maxTransfer).queue();
            return;
        }

        String discordId = event.getUser().getId();

        plugin.getDatabaseManager().getMinecraftUUID(discordId).thenAccept(senderUuid -> {
            if (senderUuid == null) {
                event.getHook().editOriginal("You need to link your account first! Use `/verify`").queue();
                return;
            }

            OfflinePlayer sender = Bukkit.getOfflinePlayer(senderUuid);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

            if (!target.hasPlayedBefore()) {
                event.getHook().editOriginal("Player **" + targetName + "** not found!").queue();
                return;
            }

            if (sender.getUniqueId().equals(target.getUniqueId())) {
                event.getHook().editOriginal("You can't send money to yourself!").queue();
                return;
            }

            double senderBalance = getBalance(sender);
            double totalCost = amount + (amount * transferTax);

            if (senderBalance < totalCost) {
                event.getHook().editOriginal("Insufficient funds! You need " + currencySymbol
                    + String.format("%.2f", totalCost) + " (including " + (transferTax * 100) + "% tax)").queue();
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                withdraw(sender, totalCost);
                deposit(target, amount);

                DecimalFormat df = new DecimalFormat("#,##0.00");

                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Transfer Successful")
                    .setColor(Color.GREEN)
                    .addField("From", sender.getName(), true)
                    .addField("To", target.getName(), true)
                    .addField("Amount", currencySymbol + df.format(amount), true);

                if (transferTax > 0) {
                    embed.addField("Tax (" + (transferTax * 100) + "%)", currencySymbol + df.format(amount * transferTax), true);
                }

                embed.addField("Your New Balance", currencySymbol + df.format(getBalance(sender)), false)
                    .setTimestamp(Instant.now());

                event.getHook().editOriginalEmbeds(embed.build()).queue();

                if (target.isOnline()) {
                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(ColorUtils.parse(
                            "<gradient:#55FF55:#00AA00> You received " + currencySymbol + df.format(amount)
                                + " from " + sender.getName() + "!</gradient>"
                        ));
                    }
                }

                info(sender.getName() + " sent " + amount + " to " + target.getName());
            });
        });
    }

    private void handleEconomyAdminCommand(SlashCommandInteractionEvent event) {
        String userId = event.getUser().getId();
        if (!plugin.getAdminUtils().isTrustedAdmin(userId)) {
            event.reply("You don't have permission to use this command!").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        String action = event.getOption("action").getAsString().toLowerCase();
        String playerName = event.getOption("player").getAsString();
        double amount = event.getOption("amount").getAsDouble();

        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (!player.hasPlayedBefore()) {
            event.getHook().editOriginal("Player **" + playerName + "** not found!").queue();
            return;
        }

        DecimalFormat df = new DecimalFormat("#,##0.00");

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (action) {
                case "give":
                    deposit(player, amount);
                    sendAdminResult(event, "Gave " + currencySymbol + df.format(amount) + " to " + player.getName()
                        + "\nNew balance: " + currencySymbol + df.format(getBalance(player)));
                    break;
                case "take":
                    withdraw(player, amount);
                    sendAdminResult(event, "Took " + currencySymbol + df.format(amount) + " from " + player.getName()
                        + "\nNew balance: " + currencySymbol + df.format(getBalance(player)));
                    break;
                case "set":
                    double current = getBalance(player);
                    withdraw(player, current);
                    deposit(player, amount);
                    sendAdminResult(event, "Set " + player.getName() + "'s balance to " + currencySymbol + df.format(amount));
                    break;
                default:
                    event.getHook().editOriginal("Invalid action! Use: give, take, or set").queue();
            }
        });
    }

    private void sendAdminResult(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("Economy Admin")
            .setDescription(message)
            .setColor(Color.GREEN)
            .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private String truncate(String str, int maxLen) {
        if (str == null) return "Unknown";
        if (str.length() <= maxLen) return str;
        return str.substring(0, maxLen - 1) + "…";
    }

    private double getBalance(OfflinePlayer player) {
        Method m;
        Object provider;
        synchronized (lock) {
            if (methodGetBalance == null || economyProvider == null) return 0.0;
            m = methodGetBalance;
            provider = economyProvider;
        }
        try {
            Object result = m.invoke(provider, player);
            return result instanceof Number ? ((Number) result).doubleValue() : 0.0;
        } catch (Exception e) {
            warning("Failed to query balance for " + player.getName() + ": " + e.getMessage());
            return 0.0;
        }
    }

    private void deposit(OfflinePlayer player, double amount) {
        invoke(methodDeposit, player, amount);
    }

    private void withdraw(OfflinePlayer player, double amount) {
        invoke(methodWithdraw, player, amount);
    }

    private void invoke(Method method, OfflinePlayer player, double amount) {
        Method m;
        Object provider;
        synchronized (lock) {
            if (method == null || economyProvider == null) return;
            m = method;
            provider = economyProvider;
        }
        try {
            m.invoke(provider, player, amount);
        } catch (Exception e) {
            warning("Vault economy operation failed: " + e.getMessage());
        }
    }

    private String providerName() {
        Method m;
        Object provider;
        synchronized (lock) {
            if (methodGetName == null || economyProvider == null) return "unknown";
            m = methodGetName;
            provider = economyProvider;
        }
        try {
            Object result = m.invoke(provider);
            return result == null ? "unknown" : result.toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    public boolean isEconomyAvailable() {
        synchronized (lock) {
            return economyProvider != null && methodGetBalance != null;
        }
    }
}
