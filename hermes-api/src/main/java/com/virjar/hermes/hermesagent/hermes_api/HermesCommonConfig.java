package com.virjar.hermes.hermesagent.hermes_api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Map;

/**
 * Created by virjar on 2018/9/27.<br>
 * hermes同一个的配置模块，该模块所属apk内部，每个apk享有一份独立配置
 */

public class HermesCommonConfig {
    private static final String hermesConfigFileName = "hermes_config_file";
    private static SharedPreferences sharedPreferences = null;

    static {
        sharedPreferences = SharedObject.context.getSharedPreferences(hermesConfigFileName, Context.MODE_PRIVATE);
    }

    public static String getString(String key) {
        return sharedPreferences.getString(key, null);
    }

    public static int getInt(String key) {
        return sharedPreferences.getInt(key, 0);
    }

    public static long getLong(String key) {
        return sharedPreferences.getLong(key, 0L);
    }

    public static boolean getBoolean(String key) {
        return sharedPreferences.getBoolean(key, false);
    }

    public static void putString(String key, String value) {
        sharedPreferences.edit().putString(key, value).apply();
    }

    public static void putInt(String key, int value) {
        sharedPreferences.edit().putInt(key, value).apply();
    }

    public static void putLong(String key, long value) {
        sharedPreferences.edit().putLong(key, value).apply();
    }

    public static void putBoolean(String key, boolean value) {
        sharedPreferences.edit().putBoolean(key, value).apply();
    }

    public static void putAll(Map<String, ?> data) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (Map.Entry<String, ?> entry : data.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Integer) {
                editor.putInt(entry.getKey(), (Integer) value);
            } else if (value instanceof Boolean) {
                editor.putBoolean(entry.getKey(), (Boolean) value);
            } else if (value instanceof Long) {
                editor.putLong(entry.getKey(), (Long) value);
            } else if (value instanceof CharSequence) {
                editor.putString(entry.getKey(), value.toString());
            } else {
                Log.w("weijia", "the value " + value.getClass().getName() + " can not save info hermes" +
                        "common config ,only support int、string、long、boolean");
            }
        }
        editor.apply();
    }
}
