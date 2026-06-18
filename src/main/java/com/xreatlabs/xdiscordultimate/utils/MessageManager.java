package com.xreatlabs.xdiscordultimate.utils;

import com.xreatlabs.xdiscordultimate.XDiscordUltimate;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class MessageManager {

    private static final String DEFAULT_LANGUAGE = "en_US";

    public static final Map<String, String> AVAILABLE_LOCALES = Map.ofEntries(
        Map.entry("en_US", "English"),
        Map.entry("de_DE", "Deutsch"),
        Map.entry("fr_FR", "Français"),
        Map.entry("es_ES", "Español"),
        Map.entry("it_IT", "Italiano"),
        Map.entry("pt_BR", "Português (Brasil)"),
        Map.entry("ru_RU", "Русский"),
        Map.entry("zh_CN", "简体中文"),
        Map.entry("ja_JP", "日本語"),
        Map.entry("ko_KR", "한국어"),
        Map.entry("ar_SA", "العربية"),
        Map.entry("tr_TR", "Türkçe"),
        Map.entry("pl_PL", "Polski"),
        Map.entry("nl_NL", "Nederlands"),
        Map.entry("sv_SE", "Svenska"),
        Map.entry("cs_CZ", "Čeština"),
        Map.entry("vi_VN", "Tiếng Việt"),
        Map.entry("th_TH", "ไทย"),
        Map.entry("id_ID", "Bahasa Indonesia"),
        Map.entry("hi_IN", "हिन्दी")
    );

    private static final String LANG_FOLDER = "lang";
    private static final String MESSAGES_PREFIX = "messages_";
    private static final String PLAYER_LANG_FILE = "player_languages.yml";

    private final XDiscordUltimate plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix;
    private String defaultLocale;
    private final Map<UUID, String> playerLocales = new HashMap<>();
    private final Map<String, FileConfiguration> localeConfigs = new ConcurrentHashMap<>();

    public MessageManager(XDiscordUltimate plugin) {
        this.plugin = plugin;
        loadPlayerLanguages();
        loadMessages();
        String rawPrefix = messagesConfig.getString("general.prefix",
            "<gradient:#55FFFF:#5555FF>XDiscord</gradient> &8» &r");
        this.prefix = ColorUtils.parse(rawPrefix);
        plugin.getLogger().info("MessageManager initialized. Default: " + defaultLocale + ", Available locales: " + AVAILABLE_LOCALES.size());
    }

    private void loadMessages() {
        File langFolder = getLangFolder();
        if (!langFolder.exists() && !langFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create language directory: " + langFolder.getPath());
        }

        this.defaultLocale = plugin.getConfig().getString("general.default-language", DEFAULT_LANGUAGE);
        String language = defaultLocale;
        String fileName = MESSAGES_PREFIX + language + ".yml";
        messagesFile = new File(langFolder, fileName);

        if (!messagesFile.exists()) {
            String resourcePath = findBundledLanguageResource(language);
            if (resourcePath != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                plugin.getLogger().severe("Could not copy bundled language file; language system is broken.");
                // Fallback to en_US
                this.defaultLocale = DEFAULT_LANGUAGE;
                fileName = MESSAGES_PREFIX + DEFAULT_LANGUAGE + ".yml";
                messagesFile = new File(langFolder, fileName);
                if (!messagesFile.exists()) {
                    plugin.saveResource(LANG_FOLDER + "/" + fileName, false);
                }
            }
        }

        messagesConfig = loadLocaleFile(language);

        InputStream defConfigStream = plugin.getResource(LANG_FOLDER + "/" + MESSAGES_PREFIX + DEFAULT_LANGUAGE + ".yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            messagesConfig.setDefaults(defConfig);
        }

        // Validate the config has content
        if (messagesConfig.getKeys(false).isEmpty()) {
            plugin.getLogger().warning("Messages file for " + language + " is empty! Falling back to " + DEFAULT_LANGUAGE);
            this.defaultLocale = DEFAULT_LANGUAGE;
            messagesFile = new File(langFolder, MESSAGES_PREFIX + DEFAULT_LANGUAGE + ".yml");
            messagesConfig = loadLocaleFile(DEFAULT_LANGUAGE);
        }

        extractAllLocales();
    }

    private void extractAllLocales() {
        File langFolder = getLangFolder();
        for (String locale : AVAILABLE_LOCALES.keySet()) {
            if (locale.equals(defaultLocale)) continue;
            String fileName = MESSAGES_PREFIX + locale + ".yml";
            File localeFile = new File(langFolder, fileName);
            if (!localeFile.exists()) {
                String resourcePath = LANG_FOLDER + "/" + fileName;
                if (plugin.getResource(resourcePath) != null) {
                    plugin.saveResource(resourcePath, false);
                }
            }
        }
    }

    private FileConfiguration loadLocaleFile(String locale) {
        File langFolder = getLangFolder();
        String fileName = MESSAGES_PREFIX + locale + ".yml";
        File localeFile = new File(langFolder, fileName);

        if (!localeFile.exists()) {
            String resourcePath = findBundledLanguageResource(locale);
            if (resourcePath != null) {
                plugin.saveResource(resourcePath, false);
                localeFile = new File(langFolder, fileName);
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(localeFile);
        localeConfigs.put(locale, config);

        InputStream defConfigStream = plugin.getResource(LANG_FOLDER + "/" + MESSAGES_PREFIX + DEFAULT_LANGUAGE + ".yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
            config.setDefaults(defConfig);
        }

        return config;
    }

    private String findBundledLanguageResource(String language) {
        String fileName = MESSAGES_PREFIX + language + ".yml";
        String resourcePath = LANG_FOLDER + "/" + fileName;
        if (plugin.getResource(resourcePath) != null) {
            return resourcePath;
        }

        String defaultResourcePath = LANG_FOLDER + "/" + MESSAGES_PREFIX + DEFAULT_LANGUAGE + ".yml";
        if (plugin.getResource(defaultResourcePath) != null) {
            if (!language.equals(DEFAULT_LANGUAGE)) {
                plugin.getLogger().info("Language file '" + language + "' not found in jar. Using default: " + DEFAULT_LANGUAGE);
            }
            return defaultResourcePath;
        }

        String legacyResourcePath = LANG_FOLDER + "/messages.yml";
        if (plugin.getResource(legacyResourcePath) != null) {
            return legacyResourcePath;
        }

        plugin.getLogger().severe("No bundled language file found in jar. Plugin language system is broken.");
        return null;
    }

    public File getLangFolder() {
        return new File(plugin.getDataFolder(), LANG_FOLDER);
    }

    public void reload() {
        loadMessages();
        String rawPrefix = messagesConfig.getString("general.prefix",
            "<gradient:#55FFFF:#5555FF>XDiscord</gradient> &8» &r");
        this.prefix = ColorUtils.parse(rawPrefix);
    }

    public void reloadLocale(String locale) {
        if (!AVAILABLE_LOCALES.containsKey(locale)) {
            plugin.getLogger().warning("Cannot reload unknown locale: " + locale);
            return;
        }

        FileConfiguration config = loadLocaleFile(locale);
        if (locale.equals(defaultLocale)) {
            messagesConfig = config;
            String rawPrefix = messagesConfig.getString("general.prefix",
                "<gradient:#55FFFF:#5555FF>XDiscord</gradient> &8» &r");
            this.prefix = ColorUtils.parse(rawPrefix);
        }
    }

    public void setDefaultLocale(String locale) {
        if (!AVAILABLE_LOCALES.containsKey(locale)) {
            throw new IllegalArgumentException("Unknown locale: " + locale);
        }
        this.defaultLocale = locale;
        plugin.getConfig().set("general.default-language", locale);
        plugin.saveConfig();
        reload();
    }

    public String getDefaultLocale() {
        return defaultLocale;
    }

    public Map<String, String> getAvailableLocales() {
        return AVAILABLE_LOCALES;
    }

    private void loadPlayerLanguages() {
        File playerLangFile = new File(plugin.getDataFolder(), PLAYER_LANG_FILE);
        if (!playerLangFile.exists()) {
            try {
                plugin.saveResource(PLAYER_LANG_FILE, false);
            } catch (Exception ignored) {
                // File not in JAR, create empty file
                try {
                    playerLangFile.createNewFile();
                    plugin.getLogger().info("Created new player_languages.yml file");
                } catch (Exception e) {
                    plugin.getLogger().warning("Could not create player_languages.yml: " + e.getMessage());
                }
            }
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(playerLangFile);
        ConfigurationSection section = config.getConfigurationSection("players");
        if (section != null) {
            for (String uuid : section.getKeys(false)) {
                String locale = section.getString(uuid);
                if (AVAILABLE_LOCALES.containsKey(locale)) {
                    try {
                        playerLocales.put(UUID.fromString(uuid), locale);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in player_languages.yml: " + uuid);
                    }
                }
            }
        }
    }

    private void savePlayerLanguages() {
        File playerLangFile = new File(plugin.getDataFolder(), PLAYER_LANG_FILE);

        // Ensure parent directory exists
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        FileConfiguration config = new YamlConfiguration();

        ConfigurationSection section = config.createSection("players");
        for (Map.Entry<UUID, String> entry : playerLocales.entrySet()) {
            section.set(entry.getKey().toString(), entry.getValue());
        }

        try {
            config.save(playerLangFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player languages file", e);
        }
    }

    public void setPlayerLocale(UUID playerUuid, String locale) {
        if (!AVAILABLE_LOCALES.containsKey(locale)) {
            throw new IllegalArgumentException("Unknown locale: " + locale);
        }

        if (locale.equals(defaultLocale)) {
            playerLocales.remove(playerUuid);
        } else {
            playerLocales.put(playerUuid, locale);
        }
        savePlayerLanguages();
    }

    public String getPlayerLocale(UUID playerUuid) {
        String locale = playerLocales.get(playerUuid);
        if (locale == null || !AVAILABLE_LOCALES.containsKey(locale)) {
            return defaultLocale;
        }
        return locale;
    }

    private FileConfiguration getConfigForPlayer(UUID playerUuid) {
        String locale = getPlayerLocale(playerUuid);
        FileConfiguration cached = localeConfigs.get(locale);
        if (cached != null) {
            return cached;
        }
        // loadLocaleFile caches the loaded config in localeConfigs itself. Do NOT use
        // computeIfAbsent here: its mapping function would self-mutate the map during
        // computation and throw ConcurrentModificationException on first locale access.
        return loadLocaleFile(locale);
    }

    private String getMessageRaw(UUID playerUuid, String path) {
        FileConfiguration config = getConfigForPlayer(playerUuid);
        String message = config.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Missing message: " + path + " for locale: " + getPlayerLocale(playerUuid));
            return "&cMissing message: " + path;
        }
        return message;
    }

    public String getLocaleString(UUID playerUuid, String path) {
        return getMessageRaw(playerUuid, path);
    }

    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Missing message: " + path);
            return colorize("&cMissing message: " + path);
        }
        return colorize(message);
    }

    public String getMessage(UUID playerUuid, String path) {
        return colorize(getMessageRaw(playerUuid, path));
    }

    public String getMessage(String path, Map<String, String> placeholders) {
        String message = getMessage(path);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    public String getMessage(UUID playerUuid, String path, Map<String, String> placeholders) {
        String message = getMessage(playerUuid, path);

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace(entry.getKey(), entry.getValue());
        }

        return message;
    }

    public String getMessage(String path, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put(placeholder, value);
        return getMessage(path, placeholders);
    }

    public String getMessage(UUID playerUuid, String path, String placeholder, String value) {
        return getMessage(playerUuid, path, Map.of(placeholder, value));
    }

    public void sendMessage(CommandSender sender, String path) {
        sender.sendMessage(prefix + getMessage(path));
    }

    public void sendMessage(Player player, String path) {
        player.sendMessage(prefix + getMessage(player.getUniqueId(), path));
    }

    public void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        sender.sendMessage(prefix + getMessage(path, placeholders));
    }

    public void sendMessage(Player player, String path, Map<String, String> placeholders) {
        player.sendMessage(prefix + getMessage(player.getUniqueId(), path, placeholders));
    }

    public void sendMessage(CommandSender sender, String path, String placeholder, String value) {
        sender.sendMessage(prefix + getMessage(path, placeholder, value));
    }

    public void sendMessage(Player player, String path, String placeholder, String value) {
        player.sendMessage(prefix + getMessage(player.getUniqueId(), path, placeholder, value));
    }

    public void sendRawMessage(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize(message));
    }

    public void sendMessageNoPrefix(CommandSender sender, String path) {
        sender.sendMessage(getMessage(path));
    }

    public void sendMessageNoPrefix(Player player, String path) {
        player.sendMessage(getMessage(player.getUniqueId(), path));
    }

    public void sendMessageList(CommandSender sender, String path) {
        List<String> messages = messagesConfig.getStringList(path);
        for (String message : messages) {
            sender.sendMessage(colorize(message));
        }
    }

    public void sendMessageList(Player player, String path) {
        FileConfiguration config = getConfigForPlayer(player.getUniqueId());
        List<String> messages = config.getStringList(path);
        for (String message : messages) {
            player.sendMessage(colorize(message));
        }
    }

    public void sendError(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize("&c" + message));
    }

    public void sendSuccess(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize("&a" + message));
    }

    public void sendWarning(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize("&e" + message));
    }

    public void sendInfo(CommandSender sender, String message) {
        sender.sendMessage(prefix + colorize("&b" + message));
    }

    // Player-overloaded versions with locale support
    public void sendError(Player player, String path) {
        player.sendMessage(prefix + colorize("&c" + getMessage(player.getUniqueId(), path)));
    }

    public void sendSuccess(Player player, String path) {
        player.sendMessage(prefix + colorize("&a" + getMessage(player.getUniqueId(), path)));
    }

    public void sendWarning(Player player, String path) {
        player.sendMessage(prefix + colorize("&e" + getMessage(player.getUniqueId(), path)));
    }

    public void sendInfo(Player player, String path) {
        player.sendMessage(prefix + colorize("&b" + getMessage(player.getUniqueId(), path)));
    }

    // For messages with placeholders
    public void sendError(Player player, String path, Map<String, String> placeholders) {
        String message = getMessage(player.getUniqueId(), path, placeholders);
        player.sendMessage(prefix + colorize("&c" + message));
    }

    public void sendSuccess(Player player, String path, Map<String, String> placeholders) {
        String message = getMessage(player.getUniqueId(), path, placeholders);
        player.sendMessage(prefix + colorize("&a" + message));
    }

    public void sendWarning(Player player, String path, Map<String, String> placeholders) {
        String message = getMessage(player.getUniqueId(), path, placeholders);
        player.sendMessage(prefix + colorize("&e" + message));
    }

    public void sendInfo(Player player, String path, Map<String, String> placeholders) {
        String message = getMessage(player.getUniqueId(), path, placeholders);
        player.sendMessage(prefix + colorize("&b" + message));
    }

    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            player.getClass().getMethod("sendTitle", String.class, String.class, int.class, int.class, int.class)
                .invoke(player, colorize(title), colorize(subtitle), fadeIn, stay, fadeOut);
        } catch (Exception e) {
            try {
                player.getClass().getMethod("sendTitle", String.class, String.class)
                    .invoke(player, colorize(title), colorize(subtitle));
            } catch (Exception ex) {
                plugin.getLogger().warning("Could not send title to player: " + ex.getMessage());
            }
        }
    }

    public void sendActionBar(Player player, String message) {
        try {
            Class<?> chatMessageTypeClass = Class.forName("net.md_5.bungee.api.ChatMessageType");
            Object actionBar = chatMessageTypeClass.getField("ACTION_BAR").get(null);
            Class<?> baseComponentClass = Class.forName("net.md_5.bungee.api.chat.BaseComponent");
            Class<?> textComponentClass = Class.forName("net.md_5.bungee.api.chat.TextComponent");

            Object textComponent = textComponentClass.getConstructor(String.class).newInstance(colorize(message));
            Object[] components = (Object[]) java.lang.reflect.Array.newInstance(baseComponentClass, 1);
            components[0] = textComponent;

            Class<?> arrayType = java.lang.reflect.Array.newInstance(baseComponentClass, 0).getClass();
            player.spigot().getClass().getMethod("sendMessage", chatMessageTypeClass, arrayType)
                .invoke(player.spigot(), actionBar, components);
        } catch (Exception e) {
            player.sendMessage(colorize(message));
        }
    }

    public void broadcast(String path) {
        String message = prefix + getMessage(path);
        plugin.getServer().broadcastMessage(message);
    }

    public void broadcast(String path, Map<String, String> placeholders) {
        String message = prefix + getMessage(path, placeholders);
        plugin.getServer().broadcastMessage(message);
    }

    public void broadcastPermission(String permission, String path) {
        String message = prefix + getMessage(path);
        plugin.getServer().broadcast(message, permission);
    }

    public String colorize(String message) {
        return ColorUtils.parse(message);
    }

    public String stripColors(String message) {
        return ChatColor.stripColor(message);
    }

    public String getPrefix() {
        return prefix;
    }

    public String formatDiscordToMinecraft(String username, String message) {
        return formatChatMessage("messages.discord-to-minecraft",
            "&7[&9Discord&7] &b%user%&7: &f%message%",
            "%user%", username, "%message%", message);
    }

    public String formatMinecraftToDiscord(String playerName, String message) {
        return formatChatMessage("messages.minecraft-to-discord",
            "**%player%**: %message%",
            "%player%", playerName, "%message%", message);
    }

    private String formatChatMessage(String configPath, String defaultFormat, String... placeholders) {
        String format = plugin.getConfig().getString(configPath, defaultFormat);

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 < placeholders.length) {
                format = format.replace(placeholders[i], placeholders[i + 1]);
            }
        }

        return configPath.contains("discord-to-minecraft") ? colorize(format) : format;
    }

    public String getNoPermissionMessage() {
        return getMessage("errors.no-permission");
    }

    public String getPlayerNotFoundMessage(String playerName) {
        return getMessage("errors.player-not-found", "%player%", playerName);
    }

    public String getInvalidUsageMessage(String usage) {
        return getMessage("errors.invalid-usage", "%usage%", usage);
    }

    public boolean hasMessage(String path) {
        return messagesConfig.contains(path);
    }

    public void setMessage(String path, String message) {
        messagesConfig.set(path, message);
        saveMessages();
    }

    private void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save messages file: " + messagesFile.getName(), e);
        }
    }
}
