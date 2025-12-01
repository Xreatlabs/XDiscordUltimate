package com.xreatlabs.xdiscordultimate.utils;

import org.bukkit.Bukkit;
import org.bukkit.Sound;

/**
 * Utility class for handling version-specific functionality.
 * Supports Minecraft versions 1.16.5 - 1.21.x
 */
public class VersionUtils {
    
    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;
    private static final String VERSION_STRING;
    
    static {
        String version = Bukkit.getBukkitVersion();
        VERSION_STRING = version;
        
        // Parse version like "1.20.4-R0.1-SNAPSHOT" or "1.16.5-R0.1-SNAPSHOT"
        String[] parts = version.split("-")[0].split("\\.");
        MAJOR_VERSION = Integer.parseInt(parts[0]);
        MINOR_VERSION = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }
    
    /**
     * Get the major version number (e.g., 1 for 1.20.4)
     */
    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }
    
    /**
     * Get the minor version number (e.g., 20 for 1.20.4)
     */
    public static int getMinorVersion() {
        return MINOR_VERSION;
    }
    
    /**
     * Get the full version string
     */
    public static String getVersionString() {
        return VERSION_STRING;
    }
    
    /**
     * Check if running on a specific version or newer
     */
    public static boolean isVersion(int major, int minor) {
        if (MAJOR_VERSION > major) return true;
        if (MAJOR_VERSION == major && MINOR_VERSION >= minor) return true;
        return false;
    }
    
    /**
     * Check if running on 1.17 or newer (requires Java 16)
     */
    public static boolean is1_17OrNewer() {
        return isVersion(1, 17);
    }
    
    /**
     * Check if running on 1.18 or newer (requires Java 17)
     */
    public static boolean is1_18OrNewer() {
        return isVersion(1, 18);
    }
    
    /**
     * Check if running on 1.19 or newer
     */
    public static boolean is1_19OrNewer() {
        return isVersion(1, 19);
    }
    
    /**
     * Check if running on 1.20 or newer
     */
    public static boolean is1_20OrNewer() {
        return isVersion(1, 20);
    }
    
    /**
     * Check if running on 1.21 or newer
     */
    public static boolean is1_21OrNewer() {
        return isVersion(1, 21);
    }
    
    /**
     * Get a sound that works across versions.
     * Some sounds have different names in different versions.
     */
    public static Sound getSound(String soundName) {
        try {
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            // Try alternative sound names for older versions
            switch (soundName) {
                case "BLOCK_NOTE_BLOCK_PLING":
                    try {
                        return Sound.valueOf("NOTE_PLING");
                    } catch (IllegalArgumentException ignored) {}
                    break;
                case "ENTITY_EXPERIENCE_ORB_PICKUP":
                    try {
                        return Sound.valueOf("ORB_PICKUP");
                    } catch (IllegalArgumentException ignored) {}
                    break;
            }
            return null;
        }
    }
    
    /**
     * Get notification sound that works on all versions
     */
    public static Sound getNotificationSound() {
        Sound sound = getSound("ENTITY_EXPERIENCE_ORB_PICKUP");
        if (sound == null) {
            sound = getSound("ENTITY_PLAYER_LEVELUP");
        }
        return sound;
    }
    
    /**
     * Check if the server supports modern components (1.16+)
     */
    public static boolean supportsComponents() {
        return isVersion(1, 16);
    }
    
    /**
     * Check if the server supports RGB colors in chat (1.16+)
     */
    public static boolean supportsRGBColors() {
        return isVersion(1, 16);
    }
    
    /**
     * Get a human-readable version range string
     */
    public static String getSupportedVersions() {
        return "1.16.5 - 1.21.x";
    }
    
    /**
     * Check if current server version is supported
     */
    public static boolean isSupported() {
        return MAJOR_VERSION == 1 && MINOR_VERSION >= 16 && MINOR_VERSION <= 21;
    }
}
