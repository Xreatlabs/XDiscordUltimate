package com.xreatlabs.xdiscordultimate.modules.economy;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.awt.*;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class EconomyBridgeModule extends Module {

    private Economy economy;
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
            warning("Vault economy not found! Economy Bridge requires Vault + an economy plugin.");
            return;
        }
        
        loadConfiguration();
        info("Economy Bridge enabled - Connected to: " + economy.getName());
    }

    @Override
    protected void onDisable() {
        if (listener != null && plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            plugin.getDiscordManager().getJDA().removeEventListener(listener);
        }
    }

    @Override
    public void onDiscordReady() {
        if (economy == null) return;
        
        if (plugin.getDiscordManager() != null && plugin.getDiscordManager().isReady()) {
            listener = new EconomyListener();
            plugin.getDiscordManager().getJDA().addEventListener(listener);
            
            // Register slash commands
            registerSlashCommands();
            
            info("Economy Bridge connected to Discord");
        }
    }

    private boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadConfiguration() {
        currencySymbol = getConfig().getString("currency-symbol", "$");
        currencyName = getConfig().getString("currency-name", "coins");
        topPlayersCount = getConfig().getInt("top-players", 10);
        allowTransfers = getConfig().getBoolean("allow-transfers", true);
        minTransfer = getConfig().getDouble("min-transfer", 1.0);
        maxTransfer = getConfig().getDouble("max-transfer", 1000000.0);
        transferTax = getConfig().getDouble("transfer-tax", 0.0); // 0 = no tax, 0.05 = 5% tax
    }

    private void registerSlashCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        
        // /balance command
        commands.add(Commands.slash("balance", "Check your or another player's balance")
            .addOption(OptionType.STRING, "player", "Player name (optional)", false));
        
        // /baltop command
        commands.add(Commands.slash("baltop", "Show the richest players"));
        
        // /pay command
        if (allowTransfers) {
            commands.add(Commands.slash("pay", "Send money to another player")
                .addOption(OptionType.STRING, "player", "Player to send money to", true)
                .addOption(OptionType.NUMBER, "amount", "Amount to send", true));
        }
        
        // /economy command (admin)
        commands.add(Commands.slash("economy", "Economy admin commands")
            .addOption(OptionType.STRING, "action", "give/take/set", true)
            .addOption(OptionType.STRING, "player", "Player name", true)
            .addOption(OptionType.NUMBER, "amount", "Amount", true));

        plugin.getDiscordManager().getMainGuild().updateCommands()
            .addCommands(commands)
            .queue(
                success -> info("Registered " + commands.size() + " economy slash commands"),
                error -> warning("Failed to register economy commands: " + error.getMessage())
            );
    }

    private class EconomyListener extends ListenerAdapter {
        @Override
        public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
            if (event.isAcknowledged()) return;
            
            String command = event.getName();
            
            switch (command) {
                case "balance":
                    handleBalanceCommand(event);
                    break;
                case "baltop":
                    handleBaltopCommand(event);
                    break;
                case "pay":
                    handlePayCommand(event);
                    break;
                case "economy":
                    handleEconomyAdminCommand(event);
                    break;
            }
        }
    }

    private void handleBalanceCommand(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        
        String playerName = event.getOption("player") != null ? 
            event.getOption("player").getAsString() : null;
        
        // If no player specified, try to get linked account
        if (playerName == null) {
            String discordId = event.getUser().getId();
            plugin.getDatabaseManager().getMinecraftUUID(discordId).thenAccept(uuid -> {
                if (uuid == null) {
                    event.getHook().editOriginal("❌ You need to link your account first! Use `/verify` to link, or specify a player name.").queue();
                    return;
                }
                
                OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
                sendBalanceEmbed(event, player);
            });
        } else {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
            if (!player.hasPlayedBefore()) {
                event.getHook().editOriginal("❌ Player **" + playerName + "** not found!").queue();
                return;
            }
            sendBalanceEmbed(event, player);
        }
    }

    private void sendBalanceEmbed(SlashCommandInteractionEvent event, OfflinePlayer player) {
        double balance = economy.getBalance(player);
        DecimalFormat df = new DecimalFormat("#,##0.00");
        
        String avatarUrl = "https://crafthead.net/avatar/" + player.getUniqueId().toString().replace("-", "") + "/128";
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("💰 Balance")
            .setColor(new Color(255, 215, 0)) // Gold
            .setThumbnail(avatarUrl)
            .addField("Player", player.getName(), true)
            .addField("Balance", currencySymbol + df.format(balance) + " " + currencyName, true)
            .setTimestamp(Instant.now());
        
        // Add rank if we can calculate it
        int rank = getPlayerRank(player);
        if (rank > 0) {
            embed.addField("Rank", "#" + rank, true);
        }
        
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private int getPlayerRank(OfflinePlayer targetPlayer) {
        List<OfflinePlayer> sorted = Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(OfflinePlayer::hasPlayedBefore)
            .sorted((a, b) -> Double.compare(economy.getBalance(b), economy.getBalance(a)))
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
            .sorted((a, b) -> Double.compare(economy.getBalance(b), economy.getBalance(a)))
            .limit(topPlayersCount)
            .collect(Collectors.toList());
        
        if (topPlayers.isEmpty()) {
            event.getHook().editOriginal("No player data available yet!").queue();
            return;
        }
        
        DecimalFormat df = new DecimalFormat("#,##0.00");
        StringBuilder description = new StringBuilder();
        description.append("```\n");
        
        String[] medals = {"🥇", "🥈", "🥉"};
        
        for (int i = 0; i < topPlayers.size(); i++) {
            OfflinePlayer player = topPlayers.get(i);
            String rank = i < 3 ? medals[i] : String.format("%2d.", i + 1);
            String name = truncate(player.getName(), 16);
            double balance = economy.getBalance(player);
            
            description.append(String.format("%-3s %-16s %s%s\n", 
                rank, name, currencySymbol, df.format(balance)));
        }
        
        description.append("```");
        
        // Calculate total economy
        double totalEconomy = Arrays.stream(Bukkit.getOfflinePlayers())
            .filter(OfflinePlayer::hasPlayedBefore)
            .mapToDouble(economy::getBalance)
            .sum();
        
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("🏆 Richest Players")
            .setDescription(description.toString())
            .setColor(new Color(255, 215, 0))
            .addField("💵 Total Economy", currencySymbol + df.format(totalEconomy) + " " + currencyName, false)
            .setTimestamp(Instant.now());
        
        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handlePayCommand(SlashCommandInteractionEvent event) {
        if (!allowTransfers) {
            event.reply("❌ Money transfers are disabled on this server.").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        String targetName = event.getOption("player").getAsString();
        double amount = event.getOption("amount").getAsDouble();
        
        // Validate amount
        if (amount < minTransfer) {
            event.getHook().editOriginal("❌ Minimum transfer amount is " + currencySymbol + minTransfer).queue();
            return;
        }
        if (amount > maxTransfer) {
            event.getHook().editOriginal("❌ Maximum transfer amount is " + currencySymbol + maxTransfer).queue();
            return;
        }
        
        String discordId = event.getUser().getId();
        
        plugin.getDatabaseManager().getMinecraftUUID(discordId).thenAccept(senderUuid -> {
            if (senderUuid == null) {
                event.getHook().editOriginal("❌ You need to link your account first! Use `/verify`").queue();
                return;
            }
            
            OfflinePlayer sender = Bukkit.getOfflinePlayer(senderUuid);
            OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
            
            if (!target.hasPlayedBefore()) {
                event.getHook().editOriginal("❌ Player **" + targetName + "** not found!").queue();
                return;
            }
            
            if (sender.getUniqueId().equals(target.getUniqueId())) {
                event.getHook().editOriginal("❌ You can't send money to yourself!").queue();
                return;
            }
            
            double senderBalance = economy.getBalance(sender);
            double totalCost = amount + (amount * transferTax);
            
            if (senderBalance < totalCost) {
                event.getHook().editOriginal("❌ Insufficient funds! You need " + currencySymbol + 
                    String.format("%.2f", totalCost) + " (including " + (transferTax * 100) + "% tax)").queue();
                return;
            }
            
            // Perform transfer on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                economy.withdrawPlayer(sender, totalCost);
                economy.depositPlayer(target, amount);
                
                DecimalFormat df = new DecimalFormat("#,##0.00");
                
                EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("💸 Transfer Successful")
                    .setColor(Color.GREEN)
                    .addField("From", sender.getName(), true)
                    .addField("To", target.getName(), true)
                    .addField("Amount", currencySymbol + df.format(amount), true);
                
                if (transferTax > 0) {
                    embed.addField("Tax (" + (transferTax * 100) + "%)", currencySymbol + df.format(amount * transferTax), true);
                }
                
                embed.addField("Your New Balance", currencySymbol + df.format(economy.getBalance(sender)), false)
                    .setTimestamp(Instant.now());
                
                event.getHook().editOriginalEmbeds(embed.build()).queue();
                
                // Notify target if online
                if (target.isOnline()) {
                    Player targetPlayer = target.getPlayer();
                    if (targetPlayer != null) {
                        targetPlayer.sendMessage(ColorUtils.parse(
                            "<gradient:#55FF55:#00AA00>💰 You received " + currencySymbol + df.format(amount) + 
                            " from " + sender.getName() + "!</gradient>"
                        ));
                    }
                }
                
                info(sender.getName() + " sent " + amount + " to " + target.getName());
            });
        });
    }

    private void handleEconomyAdminCommand(SlashCommandInteractionEvent event) {
        // Check if user is admin
        String userId = event.getUser().getId();
        if (!plugin.getAdminUtils().isTrustedAdmin(userId)) {
            event.reply("❌ You don't have permission to use this command!").setEphemeral(true).queue();
            return;
        }
        
        event.deferReply().queue();
        
        String action = event.getOption("action").getAsString().toLowerCase();
        String playerName = event.getOption("player").getAsString();
        double amount = event.getOption("amount").getAsDouble();
        
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        if (!player.hasPlayedBefore()) {
            event.getHook().editOriginal("❌ Player **" + playerName + "** not found!").queue();
            return;
        }
        
        DecimalFormat df = new DecimalFormat("#,##0.00");
        String result;
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (action) {
                case "give":
                    economy.depositPlayer(player, amount);
                    sendAdminResult(event, "✅ Gave " + currencySymbol + df.format(amount) + " to " + player.getName() +
                        "\nNew balance: " + currencySymbol + df.format(economy.getBalance(player)));
                    break;
                case "take":
                    economy.withdrawPlayer(player, amount);
                    sendAdminResult(event, "✅ Took " + currencySymbol + df.format(amount) + " from " + player.getName() +
                        "\nNew balance: " + currencySymbol + df.format(economy.getBalance(player)));
                    break;
                case "set":
                    double current = economy.getBalance(player);
                    economy.withdrawPlayer(player, current);
                    economy.depositPlayer(player, amount);
                    sendAdminResult(event, "✅ Set " + player.getName() + "'s balance to " + currencySymbol + df.format(amount));
                    break;
                default:
                    event.getHook().editOriginal("❌ Invalid action! Use: give, take, or set").queue();
            }
        });
    }

    private void sendAdminResult(SlashCommandInteractionEvent event, String message) {
        EmbedBuilder embed = new EmbedBuilder()
            .setTitle("💼 Economy Admin")
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

    public Economy getEconomy() {
        return economy;
    }

    public boolean isEconomyAvailable() {
        return economy != null;
    }
}
