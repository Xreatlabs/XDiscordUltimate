package com.xreatlabs.xdiscordultimate.commands;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.welcomedm.WelcomeDMModule;
import com.xreatlabs.xdiscordultimate.utils.ColorUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WelcomeDMCommand implements CommandExecutor, TabCompleter {

    private final XDiscordUltimate plugin;

    public WelcomeDMCommand(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("xdiscord.admin")) {
            plugin.getMessageManager().sendMessage(sender, "errors.no-permission");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "test":
                handleTest(sender, args);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "help":
                sendHelp(sender, label);
                break;
            default:
                plugin.getMessageManager().sendError(sender, "Unknown subcommand: " + sub);
                sendHelp(sender, label);
                break;
        }
        return true;
    }

    private void handleTest(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getMessageManager().sendError(sender, "Usage: /welcomedm test <player>");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.getMessageManager().sendError(sender, "Player not found or not online: " + args[1]);
            return;
        }

        WelcomeDMModule module = plugin.getModuleManager().getModule(WelcomeDMModule.class);
        if (module == null || !module.isEnabled()) {
            plugin.getMessageManager().sendError(sender, "Welcome DM module is not enabled");
            return;
        }

        plugin.getMessageManager().sendInfo(sender, "Sending test welcome DM to " + target.getName() + "...");
        module.sendManualWelcomeDM(target)
            .thenAccept(success -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (success) {
                    sender.sendMessage(ColorUtils.parse(
                        "<gradient:#55FF55:#00AA00>✓ Test welcome DM dispatched for "
                            + target.getName() + "</gradient>"));
                    sender.sendMessage(ColorUtils.parse(
                        "&7Check the console for delivery status logs."));
                } else {
                    plugin.getMessageManager().sendError(sender,
                        "Failed to send welcome DM to " + target.getName()
                            + ". Check console for details (player may not be linked, or has DMs disabled).");
                }
            }));
    }

    private void handleReload(CommandSender sender) {
        plugin.getMessageManager().sendInfo(sender, "Reloading Welcome DM module...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            WelcomeDMModule module = plugin.getModuleManager().getModule(WelcomeDMModule.class);
            if (module != null) {
                module.reload();
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendSuccess(sender, "Welcome DM module reloaded"));
            } else {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getMessageManager().sendError(sender, "Welcome DM module not loaded"));
            }
        });
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ColorUtils.parse("<gradient:#55FFFF:#5555FF>━━━ Welcome DM Commands ━━━</gradient>"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " test <player> &8- &7Send a test welcome DM"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " reload &8- &7Reload the module config"));
        sender.sendMessage(ColorUtils.parse("  &e/" + label + " help &8- &7Show this help"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("xdiscord.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(Arrays.asList("test", "reload", "help"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
            .filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase()))
            .collect(Collectors.toList());
    }
}
