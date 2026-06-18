package com.xreatlabs.xdiscordultimate.utils;

import org.bukkit.Bukkit;
import org.bukkit.Sound;

public class VersionUtils {

    private static final int MAJOR_VERSION;
    private static final int MINOR_VERSION;
    private static final String VERSION_STRING;
    private static final int MIN_SUPPORTED = 16;
    private static final int MAX_SUPPORTED = 21;

    static {
        String version = Bukkit.getBukkitVersion();
        VERSION_STRING = version;

        String[] parts = version.split("-")[0].split("\\.");
        MAJOR_VERSION = Integer.parseInt(parts[0]);
        MINOR_VERSION = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
    }

    public static int getMajorVersion() {
        return MAJOR_VERSION;
    }

    public static int getMinorVersion() {
        return MINOR_VERSION;
    }

    public static String getVersionString() {
        return VERSION_STRING;
    }

    public static boolean isVersion(int major, int minor) {
        if (MAJOR_VERSION > major) return true;
        if (MAJOR_VERSION == major && MINOR_VERSION >= minor) return true;
        return false;
    }

    public static boolean is1_17OrNewer() {
        return isVersion(1, 17);
    }

    public static boolean is1_18OrNewer() {
        return isVersion(1, 18);
    }

    public static boolean is1_19OrNewer() {
        return isVersion(1, 19);
    }

    public static boolean is1_20OrNewer() {
        return isVersion(1, 20);
    }

    public static boolean is1_21OrNewer() {
        return isVersion(1, 21);
    }

    public static Sound getSound(String soundName) {
        try {
            return Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
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

    public static Sound getNotificationSound() {
        Sound sound = getSound("ENTITY_EXPERIENCE_ORB_PICKUP");
        if (sound == null) {
            sound = getSound("ENTITY_PLAYER_LEVELUP");
        }
        return sound;
    }

    public static boolean supportsComponents() {
        return isVersion(1, 16);
    }

    public static boolean supportsRGBColors() {
        return isVersion(1, 16);
    }

    public static String getSupportedVersions() {
        return "1.16.5 - 1.21.x";
    }

    public static boolean isSupported() {
        return MAJOR_VERSION == 1 && MINOR_VERSION >= MIN_SUPPORTED && MINOR_VERSION <= MAX_SUPPORTED;
    }
}
