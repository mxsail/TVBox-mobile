package com.github.tvbox.osc.player.util;

import android.content.SharedPreferences;

public class Prefers {

    public static SharedPreferences getPrefers() {
        return PlayerApp.getPrefs();
    }

    public static String getString(String key) {
        return getString(key, "");
    }

    public static String getString(String key, String defaultValue) {
        try {
            SharedPreferences p = getPrefers();
            return p != null ? p.getString(key, defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static int getInt(String key) {
        return getInt(key, 0);
    }

    public static int getInt(String key, int defaultValue) {
        try {
            SharedPreferences p = getPrefers();
            return p != null ? p.getInt(key, defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long getLong(String key) {
        return getLong(key, 0L);
    }

    public static long getLong(String key, long defaultValue) {
        try {
            SharedPreferences p = getPrefers();
            return p != null ? p.getLong(key, defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key) {
        return getBoolean(key, false);
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        try {
            SharedPreferences p = getPrefers();
            return p != null ? p.getBoolean(key, defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static float getFloat(String key) {
        return getFloat(key, 0f);
    }

    public static float getFloat(String key, float defaultValue) {
        try {
            SharedPreferences p = getPrefers();
            return p != null ? p.getFloat(key, defaultValue) : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static void put(String key, String value) {
        try {
            SharedPreferences p = getPrefers();
            if (p != null) p.edit().putString(key, value).apply();
        } catch (Exception ignored) {
        }
    }

    public static void put(String key, int value) {
        try {
            SharedPreferences p = getPrefers();
            if (p != null) p.edit().putInt(key, value).apply();
        } catch (Exception ignored) {
        }
    }

    public static void put(String key, long value) {
        try {
            SharedPreferences p = getPrefers();
            if (p != null) p.edit().putLong(key, value).apply();
        } catch (Exception ignored) {
        }
    }

    public static void put(String key, boolean value) {
        try {
            SharedPreferences p = getPrefers();
            if (p != null) p.edit().putBoolean(key, value).apply();
        } catch (Exception ignored) {
        }
    }

    public static void put(String key, float value) {
        try {
            SharedPreferences p = getPrefers();
            if (p != null) p.edit().putFloat(key, value).apply();
        } catch (Exception ignored) {
        }
    }
}
