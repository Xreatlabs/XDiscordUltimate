package com.xreatlabs.xdiscordultimate.modules;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;

public abstract class Module {

    protected final XDiscordUltimate plugin;
    private boolean enabled = false;

    public Module(XDiscordUltimate plugin) {
        this.plugin = plugin;
    }

    public abstract String getName();

    public abstract String getDescription();

    public void enable() {
        if (enabled) {
            return;
        }

        try {
            onEnable();
            enabled = true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enable module " + getName(), e);
        }
    }

    public void disable() {
        if (!enabled) {
            return;
        }

        try {
            onDisable();
            enabled = false;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to disable module " + getName(), e);
        }
    }

    protected abstract void onEnable();

    protected abstract void onDisable();

    public void onDiscordReady() {
    }

    public List<SlashCommandData> getSlashCommands() {
        return Collections.emptyList();
    }

    public boolean handleSlashCommand(SlashCommandInteractionEvent event) {
        return false;
    }

    public boolean handleTextCommand(MessageReceivedEvent event, String command, String args) {
        return false;
    }

    public void reload() {
        if (enabled) {
            disable();
            enable();
        }
    }

    public void reloadConfig() {
    }

    public boolean isEnabled() {
        return enabled;
    }

    protected ConfigurationSection getConfig() {
        String configPath = getName().toLowerCase().replace(" ", "-");
        return plugin.getConfigManager().getFeatureConfig(configPath);
    }

    protected boolean isSubFeatureEnabled(String subFeature) {
        String configPath = getName().toLowerCase().replace(" ", "-");
        return plugin.getConfigManager().isSubFeatureEnabled(configPath, subFeature);
    }

    protected void debug(String message) {
        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[" + getName() + "] " + message);
        }
    }

    protected void info(String message) {
        plugin.getLogger().info("[" + getName() + "] " + message);
    }

    protected void warning(String message) {
        plugin.getLogger().warning("[" + getName() + "] " + message);
    }

    protected void error(String message) {
        plugin.getLogger().severe("[" + getName() + "] " + message);
    }

    protected void error(String message, Throwable throwable) {
        plugin.getLogger().log(Level.SEVERE, "[" + getName() + "] " + message, throwable);
    }

    protected String getCommandFromMessage(String message) {
        if (message.startsWith("!")) {
            return message.substring(1);
        } else if (message.startsWith("/")) {
            return message.substring(1);
        }
        return null;
    }

    protected boolean isCommand(String message) {
        return message.startsWith("!") || message.startsWith("/");
    }
}