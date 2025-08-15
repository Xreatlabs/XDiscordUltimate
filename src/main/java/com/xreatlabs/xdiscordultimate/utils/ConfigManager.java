package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class ConfigManager {
    
    private final XDiscordUltimate plugin;
    private final Map<String, FileConfiguration> configs;
    private final Map<String, File> configFiles;
    
    public ConfigManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        this.configs = new HashMap<>();
        this.configFiles = new HashMap<>();
        
        // Load main config
        loadConfig("config.yml");
        
        // Load additional configs
        loadConfig("messages.yml");
    }
    
    /**
     * Load a configuration file
     */
    private void loadConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        
        // Look for defaults in the jar
        InputStream defConfigStream = plugin.getResource(fileName);
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defConfigStream));
            config.setDefaults(defConfig);
        }
        
        configs.put(fileName, config);
        configFiles.put(fileName, file);
    }
    
    /**
     * Get a configuration by name
     */
    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }
    
    /**
     * Save a configuration
     */
    public void saveConfig(String name) {
        FileConfiguration config = configs.get(name);
        File file = configFiles.get(name);
        
        if (config != null && file != null) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save config " + name, e);
            }
        }
    }
    
    /**
     * Reload all configurations
     */
    public void reload() {
        for (String fileName : configs.keySet()) {
            File file = configFiles.get(fileName);
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            InputStream defConfigStream = plugin.getResource(fileName);
            if (defConfigStream != null) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream));
                config.setDefaults(defConfig);
            }
            
            configs.put(fileName, config);
        }
    }
    
    /**
     * Check if a feature is enabled
     */
    public boolean isFeatureEnabled(String feature) {
        return plugin.getConfig().getBoolean("features." + feature + ".enabled", false);
    }
    
    /**
     * Get feature configuration section
     */
    public ConfigurationSection getFeatureConfig(String feature) {
        return plugin.getConfig().getConfigurationSection("features." + feature);
    }
    
    /**
     * Get a string with color codes translated
     */
    public String getColoredString(String path) {
        String value = plugin.getConfig().getString(path, "");
        return value.replace('&', '§');
    }
    
    /**
     * Get a string with placeholders replaced
     */
    public String getString(String path, Map<String, String> placeholders) {
        String value = plugin.getConfig().getString(path, "");
        
        if (placeholders != null && !placeholders.isEmpty() && value != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    value = value.replace(entry.getKey(), entry.getValue());
                }
            }
        }
        
        return value != null ? value.replace('&', '§') : "";
    }
    
    /**
     * Get admin IDs
     */
    public List<String> getAdminIDs() {
        return plugin.getConfig().getStringList("adminIDs");
    }
    
    /**
     * Get Discord bot token
     */
    public String getBotToken() {
        return plugin.getConfig().getString("discord.bot-token", "");
    }
    
    /**
     * Get Discord guild ID
     */
    public String getGuildId() {
        return plugin.getConfig().getString("discord.guild-id", "");
    }
    
    /**
     * Get database type
     */
    public String getDatabaseType() {
        return plugin.getConfig().getString("database.type", "sqlite");
    }
    
    /**
     * Get database configuration section
     */
    public ConfigurationSection getDatabaseConfig() {
        return plugin.getConfig().getConfigurationSection("database");
    }
    
    /**
     * Check if debug mode is enabled
     */
    public boolean isDebugEnabled() {
        return plugin.getConfig().getBoolean("general.debug", false);
    }
    
    /**
     * Get language
     */
    public String getLanguage() {
        return plugin.getConfig().getString("general.language", "en_US");
    }
    
    /**
     * Get message format
     */
    public String getMessageFormat(String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }
    
    /**
     * Get all keys in a section
     */
    public Set<String> getKeys(String path, boolean deep) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section != null) {
            return section.getKeys(deep);
        }
        return null;
    }
    
    /**
     * Set a value in config
     */
    public void set(String path, Object value) {
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
    }
    
    /**
     * Create default config if not exists
     */
    public void createDefaultConfig(String fileName, String resourcePath) {
        File file = new File(plugin.getDataFolder(), fileName);
        
        if (!file.exists()) {
            plugin.saveResource(resourcePath != null ? resourcePath : fileName, false);
        }
    }
    
    /**
     * Get webhook URL for a specific feature
     */
    public String getWebhookUrl(String feature) {
        if (feature == null) {
            return "";
        }
        
        String path = "features." + feature + ".webhook-url";
        String url = plugin.getConfig().getString(path);
        
        if (url == null || url.isEmpty()) {
            // Fall back to default webhook
            url = plugin.getConfig().getString("features.webhooks.default-webhook", "");
        }
        
        return url != null ? url : "";
    }
    
    /**
     * Get channel name for a specific feature
     */
    public String getChannelName(String feature) {
        if (feature == null) {
            return "";
        }
        
        String path = "features." + feature + ".channel";
        String channelName = plugin.getConfig().getString(path);
        
        if (channelName == null || channelName.isEmpty()) {
            // Try alternative paths
            path = "features." + feature + "." + feature + "-channel";
            channelName = plugin.getConfig().getString(path);
        }
        
        return channelName != null ? channelName : "";
    }
    
    /**
     * Get role name for a specific feature
     */
    public String getRoleName(String feature) {
        if (feature == null) {
            return "";
        }
        
        String path = "features." + feature + ".role";
        String roleName = plugin.getConfig().getString(path);
        
        if (roleName == null || roleName.isEmpty()) {
            // Try alternative paths
            path = "features." + feature + "." + feature + "-role";
            roleName = plugin.getConfig().getString(path);
        }
        
        return roleName != null ? roleName : "";
    }
    
    /**
     * Get timeout/cooldown for a feature
     */
    public int getTimeout(String feature, String type) {
        String path = "features." + feature + "." + type;
        return plugin.getConfig().getInt(path, 60);
    }
    
    /**
     * Check if a sub-feature is enabled
     */
    public boolean isSubFeatureEnabled(String feature, String subFeature) {
        String path = "features." + feature + "." + subFeature;
        return plugin.getConfig().getBoolean(path, false);
    }
}