package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.utils.MessageManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LanguageCommand implements CommandExecutor, TabCompleter {

    private final XDiscordUltimate plugin;
    private final MessageManager messageManager;

    public LanguageCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.messageManager = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.getLogger().info("LanguageCommand called by " + sender.getName() + " with args: " + java.util.Arrays.toString(args));

        try {
            if (!(sender instanceof Player)) {
                return handleConsole(sender, args);
            }

            Player player = (Player) sender;

            if (!player.hasPermission("xdiscord.language")) {
                plugin.getLogger().info(player.getName() + " denied access (no permission)");
                messageManager.sendMessage(player, "errors.no-permission");
                player.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                plugin.getLogger().info(player.getName() + " requested language menu");
                showLanguageMenu(player);
                return true;
            }

            String locale = args[0];
            if (!messageManager.getAvailableLocales().containsKey(locale)) {
                plugin.getLogger().info(player.getName() + " provided invalid locale: " + locale);
                messageManager.sendMessage(player, "errors.invalid-usage", "%usage%", "/language [locale]");
                player.sendMessage(" ");
                player.sendMessage(messageManager.colorize("&7Available languages:"));
                for (Map.Entry<String, String> entry : messageManager.getAvailableLocales().entrySet()) {
                    player.sendMessage(messageManager.colorize("  &e" + entry.getKey() + " &7- " + entry.getValue()));
                }
                return true;
            }

            messageManager.setPlayerLocale(player.getUniqueId(), locale);
            plugin.getLogger().info(player.getName() + " changed language to " + locale);
            player.sendMessage(messageManager.colorize("<gradient:#55FF55:#55FFFF>Language Changed!</gradient> &7Now using &e" + locale + " &7(" + messageManager.getAvailableLocales().get(locale) + ")"));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "LanguageCommand error", e);
            sender.sendMessage("§cAn error occurred while changing language. Check console.");
            return true;
        }
    }

    private boolean handleConsole(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(messageManager.colorize("<gradient:#55FFFF:#5555FF>━━━━ Language Settings (Console) ━━━━</gradient>"));
            sender.sendMessage(messageManager.colorize("  &7Server Default: &e" + messageManager.getDefaultLocale()));
            sender.sendMessage(" ");
            sender.sendMessage(messageManager.colorize("  &7Available:"));
            for (Map.Entry<String, String> entry : messageManager.getAvailableLocales().entrySet()) {
                String marker = entry.getKey().equals(messageManager.getDefaultLocale()) ? "&a✓" : "  ";
                sender.sendMessage(messageManager.colorize("  " + marker + " &e" + entry.getKey() + " &7- " + entry.getValue()));
            }
            sender.sendMessage(" ");
            sender.sendMessage(messageManager.colorize("  &7Use &e/language <locale> &7to change server default"));
            return true;
        }

        String locale = args[0];
        if (!messageManager.getAvailableLocales().containsKey(locale)) {
            sender.sendMessage(messageManager.colorize("&cUnknown locale: &e" + locale));
            return true;
        }

        messageManager.setDefaultLocale(locale);
        sender.sendMessage(messageManager.colorize("<gradient:#55FF55:#55FFFF>Server Language Changed!</gradient> &7Now: &e" + locale));
        return true;
    }

    private void showLanguageMenu(Player player) {
        String currentLocale = messageManager.getPlayerLocale(player.getUniqueId());
        String currentName = messageManager.getAvailableLocales().get(currentLocale);

        player.sendMessage(messageManager.colorize("<gradient:#55FFFF:#5555FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        player.sendMessage(messageManager.colorize("       <gradient:#55FFFF:#5555FF>&lLanguage Settings</gradient>"));
        player.sendMessage(messageManager.colorize("<gradient:#55FFFF:#5555FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
        player.sendMessage(" ");
        player.sendMessage(messageManager.colorize("  &7Current Language: &e" + currentLocale + " &7(" + currentName + ")"));
        player.sendMessage(" ");
        player.sendMessage(messageManager.colorize("  &7Available Languages:"));

        for (Map.Entry<String, String> entry : messageManager.getAvailableLocales().entrySet()) {
            String indicator = entry.getKey().equals(currentLocale) ? "&a✓" : "  ";
            player.sendMessage(messageManager.colorize("  " + indicator + " &e" + entry.getKey() + " &7- " + entry.getValue()));
        }

        player.sendMessage(" ");
        player.sendMessage(messageManager.colorize("  &7Use &e/language <locale> &7to change"));
        player.sendMessage(messageManager.colorize("<gradient:#55FFFF:#5555FF>━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━</gradient>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return messageManager.getAvailableLocales().keySet().stream()
                .filter(locale -> locale.toLowerCase().startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
