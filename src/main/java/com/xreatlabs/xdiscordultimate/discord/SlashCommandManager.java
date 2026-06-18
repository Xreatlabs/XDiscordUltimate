package com.xreatlabs.xdiscordultimate.discord;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import com.xreatlabs.xdiscordultimate.modules.Module;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class SlashCommandManager {

    private final XDiscordUltimate plugin;
    private final Map<String, SlashCommandData> registeredCommands;

    public SlashCommandManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.registeredCommands = new LinkedHashMap<>();
    }

    public void registerCommands(Guild targetGuild) {
        registeredCommands.clear();

        List<SlashCommandData> coreCommands = getCoreCommands();
        for (SlashCommandData command : coreCommands) {
            putCommand(command);
        }

        for (Module module : plugin.getModuleManager().getModules().values()) {
            if (!module.isEnabled()) {
                continue;
            }
            for (SlashCommandData command : module.getSlashCommands()) {
                putCommand(command);
            }
        }

        List<SlashCommandData> commands = new ArrayList<>(registeredCommands.values());
        if (commands.isEmpty()) {
            plugin.getLogger().info("No slash commands to register.");
            return;
        }

        if (targetGuild != null) {
            targetGuild.updateCommands()
                .addCommands(commands)
                .queue(
                    success -> plugin.getLogger().info("Registered " + commands.size() + " slash commands to " + targetGuild.getName()),
                    error -> plugin.getLogger().log(Level.SEVERE, "Failed to register guild slash commands", error)
                );
        } else if (plugin.getDiscordManager().getJDA() != null) {
            plugin.getDiscordManager().getJDA().updateCommands()
                .addCommands(commands)
                .queue(
                    success -> plugin.getLogger().info("Registered " + commands.size() + " global slash commands"),
                    error -> plugin.getLogger().log(Level.SEVERE, "Failed to register global slash commands", error)
                );
        }
    }

    public List<String> getRegisteredCommandNames() {
        return new ArrayList<>(registeredCommands.keySet());
    }

    public boolean dispatch(SlashCommandInteractionEvent event) {
        if (event.isAcknowledged()) {
            return false;
        }

        for (Module module : plugin.getModuleManager().getModules().values()) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                if (module.handleSlashCommand(event)) {
                    return true;
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE,
                    "Module '" + module.getName() + "' failed while handling /" + event.getName()
                        + ": " + t.getClass().getSimpleName() + " - " + t.getMessage(), t);
                try {
                    if (!event.isAcknowledged()) {
                        event.reply("Internal error while running that command. A developer has been notified.")
                            .setEphemeral(true).queue();
                    } else {
                        event.getHook().sendMessage("Internal error while running that command.").queue();
                    }
                } catch (Throwable ignored) {
                }
                return true;
            }
        }

        return false;
    }

    public boolean dispatchText(MessageReceivedEvent event, String command, String args) {
        for (Module module : plugin.getModuleManager().getModules().values()) {
            if (!module.isEnabled()) {
                continue;
            }
            if (module.handleTextCommand(event, command, args)) {
                return true;
            }
        }

        return false;
    }

    private List<SlashCommandData> getCoreCommands() {
        List<SlashCommandData> commands = new ArrayList<>();
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("help", "Show all bot commands and features"));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("status", "Show real-time server status with TPS and player count"));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("players", "List all online players with their worlds"));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("ping", "Check bot latency and response time"));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("tps", "Show server performance (TPS/MSPT)"));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("stats", "View player statistics and achievements")
            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "player", "Player name to lookup", false));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("announce", "Broadcast an announcement to all players")
            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "message", "The announcement message", true)
            .addOption(net.dv8tion.jda.api.interactions.commands.OptionType.STRING, "title", "Optional title for the announcement", false));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("online", "Quick view of who's online"));
        commands.add(net.dv8tion.jda.api.interactions.commands.build.Commands.slash("info", "Show plugin info and version"));
        return commands;
    }

    private void putCommand(SlashCommandData command) {
        if (command == null) {
            return;
        }
        String name = command.getName();
        if (registeredCommands.containsKey(name)) {
            plugin.getLogger().warning("Duplicate slash command '" + name + "' skipped during consolidation.");
            return;
        }
        registeredCommands.put(name, command);
    }
}
